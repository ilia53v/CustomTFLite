package com.antares.customtflite.canvas_ver3.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

class MediaPipePoseLandmarker(context: Context) {

    private val landmarker: PoseLandmarker

    init {
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("pose_landmarker_full.task")
                    .setDelegate(Delegate.GPU) // Используем GPU для ускорения
                    .build()
            )
            .setRunningMode(RunningMode.VIDEO) // Для видео
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinPoseDetectionConfidence(0.5f)
            .setNumPoses(2) // Обработка до 5 человек
            .build()

        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detectPoseLandmarksMultiple(bitmap: Bitmap, timestampMs: Long): List<List<PointF>> {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = landmarker.detectForVideo(mpImage, timestampMs)
        return result.landmarks().map { person ->
            person.map { PointF(it.x(), it.y()) }
        }
    }

    fun detectPoseLandmarks(bitmap: Bitmap): List<PointF>? {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = landmarker.detect(mpImage)
        val landmarksList = result.landmarks()
        return if (landmarksList.isNotEmpty())
            landmarksList[0].map { PointF(it.x(), it.y()) }
        else null
    }
}