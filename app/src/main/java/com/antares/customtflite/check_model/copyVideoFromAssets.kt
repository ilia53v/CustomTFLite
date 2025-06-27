package com.antares.customtflite.check_model

import android.content.Context
import java.io.File
import java.io.FileOutputStream


fun copyVideoFromAssets(context: Context, assetFileName: String): String {
    val file = File(context.cacheDir, assetFileName)
    if (!file.exists()) {
        context.assets.open(assetFileName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
    return file.absolutePath
}
