package com.antares.customtflite.ver3

/*
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.Log
import android.util.Size
import androidx.compose.ui.graphics.Canvas
import com.antares.customtflite.ver2.YoloContourDrawer
import com.antares.customtflite.ver2.segmentor.YoloV8Segmentor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class InferencePipeline(
    private val segmentor: YoloV8Segmentor,
    private val contourDrawer: YoloContourDrawer,
    private val confidenceThreshold: Float,
    private val minAreaAbs: Float
) {
    private val _overlayBitmapState = MutableStateFlow<Bitmap?>(null)
    val overlayBitmapState: StateFlow<Bitmap?> = _overlayBitmapState

    private val inferenceLock = Mutex()

    suspend fun processFrame(frame: Bitmap) {
        if (!inferenceLock.tryLock()) return

        try {
            // 1. Запускаем инференс
            val (contours, _, objects) = segmentor.runInference(frame, 0.15f)

            // 2. Преобразуем объекты в Triple<PointF, PointF, Float>
            val bboxes = objects.map {
                Triple(it.topLeft, it.bottomRight, it.confidence)
            }

            // 3. Отрисовка на встроенном Bitmap
            contourDrawer.drawOverlay(
                bboxes = bboxes,
                contours = contours,
                confidenceThreshold = confidenceThreshold,
                minAreaAbs = minAreaAbs
            )

            // 4. Обновление внешнего StateFlow
            _overlayBitmapState.value = contourDrawer.getOverlayBitmap()

        } catch (e: Exception) {
            Log.e("InferencePipeline", "Ошибка инференса: ${e.message}", e)
        } finally {
            inferenceLock.unlock()
        }
    }
}
*/
