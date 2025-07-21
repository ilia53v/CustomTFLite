package com.antares.customtflite.ver2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.antares.customtflite.ver2.segmentor.YoloV8Segmentor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

suspend fun saveVideoWithInferenceCanvasOptimized(
    context: Context,
    inputUri: Uri,
    outputFile: File,
    yolo: YoloV8Segmentor,
    drawer: YoloContourDrawer,
    onProgress: (Int) -> Unit = {}
) = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever().apply {
        setDataSource(context, inputUri)
    }

    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
    val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloat()?.toInt()
        ?: 30
    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0

    Log.d("VideoSave", "Starting video processing")
    Log.d("VideoSave", "Width=$width Height=$height Duration=$durationMs FrameRate=$frameRate Rotation=$rotation")

    val totalFrames = (durationMs / 1000f * frameRate).toInt()
    val timeStepUs = 1_000_000L / frameRate

    val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }

    val videoEncoder = MediaCodec.createEncoderByType("video/avc")
    videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    val inputSurface = videoEncoder.createInputSurface()
    videoEncoder.start()
    Log.d("VideoSave", "Encoder started")

    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    if (rotation != 0) muxer.setOrientationHint(rotation)

    var videoTrackIndex = -1
    var audioTrackIndex = -1
    var muxerStarted = false

    val bufferInfo = MediaCodec.BufferInfo()
    var presentationTimeUs = 0L

    // --- AUDIO EXTRACTION ---
    val extractor = MediaExtractor()
    extractor.setDataSource(context, inputUri, null)
    for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("audio/")) {
            extractor.selectTrack(i)
            audioTrackIndex = muxer.addTrack(format)
            Log.d("VideoSave", "Audio track added: $audioTrackIndex")
            break
        }
    }

    // --- VIDEO PROCESSING LOOP ---
    for (i in 0 until totalFrames) {
        val frameTimeUs = i * timeStepUs
        val frameBitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
        val (_, _, _, overlayBitmap) = yolo.runInference(frameBitmap)

        val canvas = inputSurface.lockCanvas(null)
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(overlayBitmap, null, Rect(0, 0, width, height), null)
        inputSurface.unlockCanvasAndPost(canvas)

        // Read output from encoder
        while (true) {
            val outputIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 0)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) error("Format changed after muxer started")
                    val newFormat = videoEncoder.outputFormat
                    videoTrackIndex = muxer.addTrack(newFormat)
                    if (audioTrackIndex != -1 && videoTrackIndex != -1 && !muxerStarted) {
                        muxer.start()
                        muxerStarted = true
                        Log.d("VideoSave", "Video track added: $videoTrackIndex")
                        Log.d("VideoSave", "Muxer start with both tracks")
                    }
                }
                outputIndex >= 0 -> {
                    val encodedData = videoEncoder.getOutputBuffer(outputIndex) ?: continue
                    if (bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        bufferInfo.presentationTimeUs = presentationTimeUs
                        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        Log.d("VideoSave", "Video frame written: $presentationTimeUs")
                        presentationTimeUs += timeStepUs
                    }
                    videoEncoder.releaseOutputBuffer(outputIndex, false)
                }
            }
        }

        onProgress(((i + 1) * 100) / totalFrames)
    }

    // --- FINALIZE VIDEO STREAM ---
    videoEncoder.signalEndOfInputStream()
    Log.d("VideoSave", "Signaled end of video stream")
    while (true) {
        val outputIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 0)
        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
        else if (outputIndex >= 0) {
            val encodedData = videoEncoder.getOutputBuffer(outputIndex) ?: continue
            if (bufferInfo.size > 0 && muxerStarted) {
                encodedData.position(bufferInfo.offset)
                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                Log.d("VideoSave", "Final video frame written")
            }
            videoEncoder.releaseOutputBuffer(outputIndex, false)
        }
    }
    videoEncoder.stop()
    videoEncoder.release()
    Log.d("VideoSave", "Encoder released")

    // --- WRITE AUDIO ---
    if (audioTrackIndex != -1 && muxerStarted) {
        val buffer = ByteBuffer.allocate(1 * 1024 * 1024)
        val audioInfo = MediaCodec.BufferInfo()

        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            audioInfo.offset = 0
            audioInfo.size = sampleSize
            audioInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
            audioInfo.presentationTimeUs = extractor.sampleTime

            muxer.writeSampleData(audioTrackIndex, buffer, audioInfo)
            Log.d("VideoSave", "Audio sample written at ${audioInfo.presentationTimeUs}")
            extractor.advance()
        }
    }

    extractor.release()
    Log.d("VideoSave", "Audio extractor released")

    // --- CLOSE MUXER ---
    if (muxerStarted) {
        muxer.stop()
        muxer.release()
        Log.d("VideoSave", "Muxer stopped")
    }

    retriever.release()
    Log.d("VideoSave", "All resources released, file saved: ${outputFile.absolutePath}")
}
