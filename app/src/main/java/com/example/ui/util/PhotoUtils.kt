package com.example.ui.util

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoUtils {

    /**
     * Creates a safe temporary image destination inside application cache
     * and returns a FileProvider content:// URI granted to the camera app.
     */
    fun createCameraTempUri(context: Context): Uri {
        val photoDir = File(context.cacheDir, "photos")
        if (!photoDir.exists()) {
            photoDir.mkdirs()
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(photoDir, "JPEG_${timeStamp}_temp.jpg")
        if (!file.exists()) {
            file.createNewFile()
        }

        val authority = "${context.packageName}.fileprovider"
        return try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (_: Exception) {
            // Fallback for Robolectric or test environments
            Uri.fromFile(file)
        }
    }

    /**
     * Copies captured/picked image from sourceUri to stable internal storage (filesDir/moments/).
     * Validates that the file has non-zero bytes, removes GPS EXIF metadata for privacy,
     * cleans up any temporary camera cache file, and returns the absolute local path.
     */
    fun copyUriToPermanentStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val destinationDir = File(context.filesDir, "moments")
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val targetFile = File(destinationDir, "moment_${timeStamp}.jpg")

            var bytesCopied = 0L
            val inputStream: InputStream? = if (sourceUri.scheme == "file" || sourceUri.scheme == null) {
                val path = sourceUri.path
                if (path != null && File(path).exists()) FileInputStream(File(path)) else null
            } else {
                context.contentResolver.openInputStream(sourceUri)
            }

            inputStream?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    bytesCopied = input.copyTo(output)
                }
            }

            // Reject empty or zero-byte files safely
            if (bytesCopied <= 0L || !targetFile.exists() || targetFile.length() <= 0L) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                return null
            }

            // Privacy: Strip GPS EXIF coordinates while preserving image orientation
            try {
                val exif = ExifInterface(targetFile.absolutePath)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null)
                exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
                exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null)
                exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
                exif.saveAttributes()
            } catch (_: Exception) {
                // Non-fatal if EXIF stripping fails
            }

            // Clean up temporary camera file if the source was from cache/photos
            cleanUpTempFile(context, sourceUri.toString())

            targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Safely deletes temporary camera cache files when cancelled, failed, or after copying.
     */
    fun cleanUpTempFile(context: Context, uriString: String?) {
        if (uriString.isNullOrBlank()) return
        try {
            val photoDir = File(context.cacheDir, "photos")
            if (photoDir.exists()) {
                val files = photoDir.listFiles() ?: return
                for (file in files) {
                    if (file.name.endsWith("_temp.jpg")) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {
            // Ignored safe cleanup
        }
    }
}
