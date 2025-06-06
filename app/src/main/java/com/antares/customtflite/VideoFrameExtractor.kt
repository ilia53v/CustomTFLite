package com.antares.customtflite

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/*
class VideoFrameExtractor(
    private val context: Context,
    private val videoUri: Uri,
    private val segmentor: YoloV8Segmentor,
    private val onContoursReady: (List<Contour>) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        scope.launch {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, videoUri)

                val durationMs =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                        ?: 0L

                val frameIntervalMs = 100L // каждые 100 мс (10 fps)
                var timeMs = 0L

                while (timeMs < durationMs) {
                    val bitmap =
                        retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                            ?: break

                    val contours = segmentor.runSegmentation(bitmap)
                    withContext(Dispatchers.Main) {
                        onContoursReady(contours)
                    }

                    timeMs += frameIntervalMs
                    delay(frameIntervalMs)
                }

                retriever.release()
            } catch (e: Exception) {
                Log.e("VideoFrameExtractor", "Failed to extract frames", e)
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}*/


class VideoFrameExtractor(
    private val context: Context,
    private val segmentor: YoloV8Segmentor,
    private val onContoursReady: (List<Contour>) -> Unit
) {
    companion object {
        private const val TAG = "VideoFrameExtractor"
    }

    private var retriever: MediaMetadataRetriever? = null

    suspend fun extractFramesAndSegment(videoUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                retriever = MediaMetadataRetriever()
                retriever?.setDataSource(context, videoUri)

                val durationStr = retriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 0L

                // Интервал выборки кадров — например, каждые 500 мс
                val intervalMs = 500L

                for (timeMs in 0..durationMs step intervalMs) {
                    val frame: Bitmap? = retriever?.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                    frame?.let {
                        val contours = segmentor.runSegmentation(it)
                        onContoursReady(contours)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract frames or segment video", e)
            } finally {
                retriever?.release()
                retriever = null
            }
        }
    }
}