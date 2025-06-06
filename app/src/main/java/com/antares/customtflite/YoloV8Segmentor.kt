package com.antares.customtflite

import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.MappedByteBuffer

class YoloV8Segmentor(private val context: Context) {

    companion object {
        private const val TAG = "YoloV8Segmentor"
        private const val MODEL_NAME = "best.tflite"
    }

    private var interpreter: Interpreter? = null
    private var inputWidth = 0
    private var inputHeight = 0
    private var gpuEnabled = false

    init {
        initializeInterpreter()
    }

    private fun initializeInterpreter() {
        try {
            val model = loadModelFile(context, MODEL_NAME)
            val options = Interpreter.Options()

            if (isGpuSupported()) {
                try {
                    val gpuDelegate = GpuDelegate()
                    options.addDelegate(gpuDelegate)
                    gpuEnabled = true
                    Log.i(TAG, "GPU delegate applied successfully.")
                } catch (e: Exception) {
                    Log.e(TAG, "GPU delegate failed: ${e.message}. Falling back to CPU.")
                    gpuEnabled = false
                }
            }

            interpreter = Interpreter(model, options)
            if(interpreter == null){
                Log.e("YoloV8Segmentor", "Failed to load model")
            }
            val inputTensor: Tensor = interpreter!!.getInputTensor(0)
            inputHeight = inputTensor.shape()[1]
            inputWidth = inputTensor.shape()[2]

            Log.i(TAG, "Interpreter initialized with input size: $inputWidth x $inputHeight")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize interpreter: ${e.message}")
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }


    private fun isGpuSupported(): Boolean {
        return try {
            GpuDelegate().close()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun runSegmentation(bitmap: Bitmap, width: Int, height: Int): List<Contour> {
        if (interpreter == null) throw IllegalStateException("Interpreter == null")
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val inputImage = TensorImage.fromBitmap(resizedBitmap)

        val outputShape = interpreter?.getOutputTensor(0)?.shape()
        if (outputShape == null) {
            Log.e(TAG, "Output tensor shape is null.")
            return emptyList()
        }

        val outputBuffer = TensorBuffer.createFixedSize(outputShape, interpreter!!.getOutputTensor(0).dataType())
        try {
            interpreter?.run(inputImage.buffer, outputBuffer.buffer)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}")
            return emptyList()
        }

        return processSegmentationOutput(outputBuffer)
    }

    private fun processSegmentationOutput(buffer: TensorBuffer): List<Contour> {
        val outputArray = buffer.floatArray
        if (outputArray.isEmpty()) {
            Log.w(TAG, "Empty output from model")
            return emptyList()
        }

        val contours = mutableListOf<Contour>()

        // TODO: адаптировать под структуру сегментной маски YOLOv8 (зависит от модели)
        // Пример: если это бинарная маска 1xHxW
        val maskSize = inputWidth * inputHeight
        if (outputArray.size < maskSize) {
            Log.w(TAG, "Output size too small for mask")
            return emptyList()
        }

        val threshold = 0.5f
        val pathPoints = mutableListOf<Pair<Float, Float>>()

        for (y in 0 until inputHeight) {
            for (x in 0 until inputWidth) {
                val index = y * inputWidth + x
                if (outputArray[index] > threshold) {
                    pathPoints.add(Pair(x.toFloat(), y.toFloat()))
                }
            }
        }

        if (pathPoints.isNotEmpty()) {
            contours.add(Contour(pathPoints))
        }

        return contours
    }
}