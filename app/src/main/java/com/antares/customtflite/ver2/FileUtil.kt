package com.antares.customtflite.ver2

import android.content.Context
import android.net.Uri
import java.io.File

object FileUtil {
    fun fromUri(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = File(context.cacheDir, "temp_output_video.mp4")

        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input?.copyTo(output)
            }
        }

        return tempFile
    }
}