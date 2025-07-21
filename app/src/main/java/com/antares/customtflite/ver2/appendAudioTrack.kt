package com.antares.customtflite.ver2

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

fun appendAudioTrack(context: Context, inputUri: Uri, outputFile: File) {
    val tempFile = File(outputFile.absolutePath + ".tmp")
    if (!tempFile.exists()) return

    val extractor = MediaExtractor()
    extractor.setDataSource(context, inputUri, null)

    val finalMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val videoExtractor = MediaExtractor()
    videoExtractor.setDataSource(tempFile.absolutePath)

    var videoTrack = -1
    for (i in 0 until videoExtractor.trackCount) {
        val format = videoExtractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME)
        if (mime?.startsWith("video/") == true) {
            videoExtractor.selectTrack(i)
            videoTrack = finalMuxer.addTrack(format)
            break
        }
    }

    var audioTrack = -1
    for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME)
        if (mime?.startsWith("audio/") == true) {
            extractor.selectTrack(i)
            audioTrack = finalMuxer.addTrack(format)
            break
        }
    }

    finalMuxer.start()

    fun copySamples(extractor: MediaExtractor, muxer: MediaMuxer, track: Int) {
        val buffer = ByteBuffer.allocate(1 * 1024 * 1024) // 1MB буфер
        val info = MediaCodec.BufferInfo()

        while (true) {
            info.offset = 0
            info.size = extractor.readSampleData(buffer, 0)
            if (info.size < 0) break

            info.presentationTimeUs = extractor.sampleTime

            // Переводим SAMPLE_FLAG_SYNC в BUFFER_FLAG_KEY_FRAME
            info.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0)
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            else
                0

            muxer.writeSampleData(track, buffer, info)
            extractor.advance()
        }
    }

    if (videoTrack >= 0) copySamples(videoExtractor, finalMuxer, videoTrack)
    if (audioTrack >= 0) copySamples(extractor, finalMuxer, audioTrack)

    finalMuxer.stop()
    finalMuxer.release()
    extractor.release()
    videoExtractor.release()

    tempFile.delete()
}