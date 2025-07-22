package com.antares.customtflite.ver2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
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
import com.antares.customtflite.data.YoloObject
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
    Log.d("VideoSave", "Starting video processing")

    val retriever = MediaMetadataRetriever().apply { setDataSource(context, inputUri) }

    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
    val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloat()?.toInt() ?: 30
    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0

    Log.d("VideoSave", "Width=$width Height=$height Duration=$durationMs FrameRate=$frameRate Rotation=$rotation")

    val totalFrames = (durationMs / 1000f * frameRate).toInt()
    val timeStepUs = 1_000_000L / frameRate

    val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }

    val encoder = MediaCodec.createEncoderByType("video/avc")
    encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    val inputSurface = encoder.createInputSurface()
    encoder.start()
    Log.d("VideoSave", "Encoder started")

    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    if (rotation != 0) muxer.setOrientationHint(rotation)

    val bufferInfo = MediaCodec.BufferInfo()
    var videoTrackIndex = -1
    var audioTrackIndex = -1
    var muxerStarted = false
    var presentationTimeUs = 0L

    val extractor = MediaExtractor()
    extractor.setDataSource(context, inputUri, null)

    // Найдём аудиотрек
    for (i in 0 until extractor.trackCount) {
        val formatTrack = extractor.getTrackFormat(i)
        val mime = formatTrack.getString(MediaFormat.KEY_MIME)
        if (mime?.startsWith("audio/") == true) {
            extractor.selectTrack(i)
            audioTrackIndex = muxer.addTrack(formatTrack)
            Log.d("VideoSave", "Audio track added: $audioTrackIndex")
            break
        }
    }

    // Сохраняем кадры
    for (i in 0 until totalFrames) {
        val frameTimeUs = i * timeStepUs
        val frameBitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: continue

        val (contours, _, objects, _) = yolo.runInference(frameBitmap)
        val bboxList = objects.map { Triple(it.topLeft, it.bottomRight, it.confidence) }
        val annotated = drawer.drawDetections(bboxList, contours, frameBitmap)

        val canvas = inputSurface.lockCanvas(null)
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(annotated, null, Rect(0, 0, width, height), null)
        inputSurface.unlockCanvasAndPost(canvas)

        // Считывание буфера
        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) break
            if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                videoTrackIndex = muxer.addTrack(newFormat)
                if (audioTrackIndex != -1 && !muxerStarted) {
                    muxer.start()
                    muxerStarted = true
                    Log.d("VideoSave", "Muxer start with both tracks")
                }
                Log.d("VideoSave", "Video track added: $videoTrackIndex")
            } else if (encoderStatus >= 0) {
                val encodedData = encoder.getOutputBuffer(encoderStatus) ?: continue
                if (bufferInfo.size > 0 && muxerStarted) {
                    bufferInfo.presentationTimeUs = presentationTimeUs
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    Log.d("VideoSave", "Video frame written: $presentationTimeUs")
                }
                encoder.releaseOutputBuffer(encoderStatus, false)
                presentationTimeUs += timeStepUs
            }
        }

        onProgress((i + 1) * 100 / totalFrames)
    }

    Log.d("VideoSave", "Signaled end of video stream")
    encoder.signalEndOfInputStream()
    encoder.stop()
    encoder.release()
    Log.d("VideoSave", "Encoder released")

    // Копируем аудио
    if (audioTrackIndex != -1) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val sampleSize = extractor.readSampleData(ByteBuffer.allocate(1024 * 1024), 0)
            if (sampleSize < 0) break

            info.offset = 0
            info.size = sampleSize
            info.presentationTimeUs = extractor.sampleTime
            info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME

            val buffer = ByteBuffer.allocate(sampleSize)
            extractor.readSampleData(buffer, 0)
            muxer.writeSampleData(audioTrackIndex, buffer, info)
            Log.d("VideoSave", "Audio sample written at ${info.presentationTimeUs}")

            extractor.advance()
        }
        extractor.release()
        Log.d("VideoSave", "Audio extractor released")
    }

    if (muxerStarted) {
        muxer.stop()
        Log.d("VideoSave", "Muxer stopped")
    }
    muxer.release()
    retriever.release()
    Log.d("VideoSave", "All resources released, file saved: ${outputFile.absolutePath}")
}