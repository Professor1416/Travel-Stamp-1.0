package com.example.ui.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoUtils {
    fun createCameraTempUri(context: Context): Uri {
        val photoDir = File(context.cacheDir, "photos")
        if (!photoDir.exists()) {
            photoDir.mkdirs()
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(photoDir, "JPEG_${timeStamp}_temp.jpg")
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun copyUriToPermanentStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val destinationDir = File(context.filesDir, "moments")
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val targetFile = File(destinationDir, "moment_${timeStamp}.jpg")

            context.contentResolver.openInputStream(sourceUri)?.use { input: InputStream ->
                FileOutputStream(targetFile).use { output: FileOutputStream ->
                    input.copyTo(output)
                }
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to string of sourceUri if copying failed
            sourceUri.toString()
        }
    }
}
