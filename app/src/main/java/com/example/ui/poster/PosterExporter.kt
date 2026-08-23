package com.example.ui.poster

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.TravelStamp
import com.example.data.model.Trip
import java.io.File
import java.io.FileOutputStream

/**
 * Handles exporting 9:16 Travel Stamp posters to Gallery via MediaStore and generating secure
 * content:// FileProvider URIs for the Android Sharesheet.
 */
object PosterExporter {

    fun createPosterBitmap(
        context: Context,
        trip: Trip,
        stamp: TravelStamp,
        config: PosterRenderConfig
    ): Bitmap {
        return PosterRenderer.render(context, trip, stamp, config)
    }

    /**
     * Saves the rendered poster bitmap to the user's Gallery via Android MediaStore.
     */
    fun savePosterToGallery(
        context: Context,
        bitmap: Bitmap,
        stamp: TravelStamp,
        format: StampEditionFormat = StampEditionFormat.PORTRAIT
    ): Boolean {
        return try {
            val safeName = stamp.title.replace(Regex("[^a-zA-Z0-9]"), "_").take(24)
            val safeCode = stamp.stampCode.replace("#", "")
            val formatSuffix = format.name.lowercase()
            val timeStamp = System.currentTimeMillis()
            val filename = "travel_stamp_${safeCode}_${formatSuffix}_${safeName}_${timeStamp}.png"

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TravelStamp")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return false

            resolver.openOutputStream(imageUri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            } ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Generates a temporary shareable PNG in the app cache directory and returns a content:// FileProvider URI.
     * Automatically cleans up older poster exports in cache to prevent unbounded growth.
     */
    fun getShareablePosterUri(
        context: Context,
        bitmap: Bitmap,
        stamp: TravelStamp,
        format: StampEditionFormat = StampEditionFormat.PORTRAIT
    ): Uri? {
        return try {
            val posterDir = File(context.cacheDir, "posters")
            if (!posterDir.exists()) {
                posterDir.mkdirs()
            }

            // Cleanup older cached posters to conserve disk space
            cleanupPosterCache(posterDir, maxFilesToKeep = 3)

            val safeName = stamp.title.replace(Regex("[^a-zA-Z0-9]"), "_").take(24)
            val safeCode = stamp.stampCode.replace("#", "")
            val formatSuffix = format.name.lowercase()
            val timeStamp = System.currentTimeMillis()
            val file = File(posterDir, "TravelStamp_Poster_${safeCode}_${formatSuffix}_${safeName}_${timeStamp}.png")

            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }

            val authority = "${context.packageName}.fileprovider"
            try {
                FileProvider.getUriForFile(context, authority, file)
            } catch (_: Exception) {
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun cleanupPosterCache(dir: File, maxFilesToKeep: Int) {
        try {
            val files = dir.listFiles() ?: return
            if (files.size > maxFilesToKeep) {
                val sorted = files.sortedBy { it.lastModified() }
                val toDeleteCount = files.size - maxFilesToKeep
                for (i in 0 until toDeleteCount) {
                    sorted[i].delete()
                }
            }
        } catch (_: Exception) {
            // Non-fatal safe cleanup
        }
    }
}
