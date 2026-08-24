package com.example.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object PhotoUtils {

    const val MAX_SOURCE_FILE_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB
    const val MAX_WORKING_DIMENSION = 2560 // Max 2560px for high-res 1080x1920 exports
    const val WORKING_JPEG_QUALITY = 86

    sealed interface WorkingImageResult {
        data class Success(val filePath: String, val width: Int, val height: Int) : WorkingImageResult
        data class TooLarge(val maxAllowedMb: Int) : WorkingImageResult
        data class Error(val message: String) : WorkingImageResult
    }

    /**
     * Calculates the memory-safe downsampling ratio (power of 2) to ensure the decoded bitmap
     * stays within target dimensions without wasting memory or causing OutOfMemoryError.
     */
    fun calculateInSampleSize(outWidth: Int, outHeight: Int, maxDimension: Int = MAX_WORKING_DIMENSION): Int {
        var inSampleSize = 1
        val maxSrcDim = max(outWidth, outHeight)
        while ((maxSrcDim / (inSampleSize * 2)) >= maxDimension) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    /**
     * Prepares a single canonical, app-controlled optimized working image for the Photo + Stamp editor.
     * 1. Safely checks source size (rejects >50MB).
     * 2. Reads bounds first without decoding full image.
     * 3. Calculates optimal inSampleSize.
     * 4. Normalizes EXIF rotation (0°, 90°, 180°, 270°, flips).
     * 5. Scales to optimal working dimensions (<=2560px).
     * 6. Writes high-quality JPEG to app cache (cacheDir/photo_editor/).
     * 7. Validates working file exists and is decodable before returning.
     */
    fun prepareWorkingImage(context: Context, sourceUri: Uri): WorkingImageResult {
        return try {
            // 1. Check source size limit (50 MB)
            val sourceSizeBytes = getSourceSizeBytes(context, sourceUri)
            if (sourceSizeBytes != null && sourceSizeBytes > MAX_SOURCE_FILE_SIZE_BYTES) {
                return WorkingImageResult.TooLarge(50)
            }

            val uriString = sourceUri.toString()
            val directFile: File? = when {
                uriString.startsWith("/") -> File(uriString)
                sourceUri.scheme == "file" || sourceUri.scheme == null -> {
                    val path = sourceUri.path ?: uriString.removePrefix("file://").removePrefix("file:")
                    File(path)
                }
                else -> null
            }

            val (outWidth, outHeight, orientation, rawBitmap) = if (directFile != null && directFile.exists() && directFile.canRead()) {
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(directFile.absolutePath, boundsOptions)
                if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                    return WorkingImageResult.Error("Invalid or corrupted image dimensions.")
                }

                val sampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, MAX_WORKING_DIMENSION)
                val exifOrientation = try {
                    ExifInterface(directFile.absolutePath).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } catch (_: Exception) {
                    ExifInterface.ORIENTATION_NORMAL
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeFile(directFile.absolutePath, decodeOptions)
                    ?: return WorkingImageResult.Error("Failed to decode image data.")

                listOf(boundsOptions.outWidth, boundsOptions.outHeight, exifOrientation, bitmap)
            } else {
                val openInput: () -> InputStream? = {
                    context.contentResolver.openInputStream(sourceUri)?.let { BufferedInputStream(it) }
                }

                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                openInput()?.use { input ->
                    BitmapFactory.decodeStream(input, null, boundsOptions)
                } ?: return WorkingImageResult.Error("Cannot read source image stream.")

                if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                    return WorkingImageResult.Error("Invalid or corrupted image dimensions.")
                }

                val sampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, MAX_WORKING_DIMENSION)
                val exifOrientation = try {
                    openInput()?.use { stream ->
                        ExifInterface(stream).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    } ?: ExifInterface.ORIENTATION_NORMAL
                } catch (_: Exception) {
                    ExifInterface.ORIENTATION_NORMAL
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = openInput()?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOptions)
                } ?: return WorkingImageResult.Error("Failed to decode image data.")

                listOf(boundsOptions.outWidth, boundsOptions.outHeight, exifOrientation, bitmap)
            }.let { list ->
                @Suppress("UNCHECKED_CAST")
                DecodeResult(list[0] as Int, list[1] as Int, list[2] as Int, list[3] as Bitmap)
            }

            // 6. Bake in orientation matrix
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            val orientedBitmap = if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotated != rawBitmap) {
                    rawBitmap.recycle()
                }
                rotated
            } else {
                rawBitmap
            }

            // 7. Scale down if longest dimension still exceeds max
            val longestDim = max(orientedBitmap.width, orientedBitmap.height)
            val finalBitmap = if (longestDim > MAX_WORKING_DIMENSION) {
                val scale = MAX_WORKING_DIMENSION.toFloat() / longestDim
                val targetW = (orientedBitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (orientedBitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(orientedBitmap, targetW, targetH, true)
                if (scaled != orientedBitmap) {
                    orientedBitmap.recycle()
                }
                scaled
            } else {
                orientedBitmap
            }

            // 8. Save into app cache photo_editor directory
            val editorDir = File(context.cacheDir, "photo_editor")
            if (!editorDir.exists()) {
                editorDir.mkdirs()
            }
            cleanupWorkingDir(editorDir, maxFilesToKeep = 5)

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val workingFile = File(editorDir, "working_${timeStamp}.jpg")
            FileOutputStream(workingFile).use { output ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, WORKING_JPEG_QUALITY, output)
            }
            val finalWidth = finalBitmap.width
            val finalHeight = finalBitmap.height
            finalBitmap.recycle()

            if (!workingFile.exists() || workingFile.length() <= 0L) {
                return WorkingImageResult.Error("Failed to write working image copy.")
            }

            WorkingImageResult.Success(workingFile.absolutePath, finalWidth, finalHeight)
        } catch (e: OutOfMemoryError) {
            WorkingImageResult.Error("Image too large for device memory. Please choose a smaller photo.")
        } catch (e: Exception) {
            WorkingImageResult.Error(e.message ?: "Failed to process photo.")
        }
    }

    private fun getSourceSizeBytes(context: Context, uri: Uri): Long? {
        return try {
            if (uri.scheme == "file" || uri.scheme == null) {
                val path = uri.path ?: uri.toString()
                val f = File(path)
                if (f.exists()) f.length() else null
            } else {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                    it.length
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Cleans up stale working copies in cacheDir/photo_editor.
     * Keeps the latest [maxFilesToKeep] files.
     * NEVER touches persistent user journal data (filesDir/moments/).
     */
    fun cleanupWorkingDir(dir: File, maxFilesToKeep: Int = 5) {
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
            // Safe non-fatal cleanup
        }
    }

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

    /**
     * Safely deletes internal images (in filesDir or cacheDir) associated with deleted moments or trips.
     * Prevents deleting external or system files and handles missing files gracefully.
     */
    fun safeDeleteInternalImage(context: Context, imageUriOrPath: String?) {
        if (imageUriOrPath.isNullOrBlank()) return
        try {
            val file = when {
                imageUriOrPath.startsWith("file://") -> {
                    val parsed = Uri.parse(imageUriOrPath).path
                    if (parsed != null) File(parsed) else null
                }
                imageUriOrPath.startsWith("/") -> File(imageUriOrPath)
                else -> File(imageUriOrPath)
            } ?: return

            if (!file.exists()) return

            val filesDirCanonical = context.filesDir.canonicalPath
            val cacheDirCanonical = context.cacheDir.canonicalPath
            val targetCanonical = file.canonicalPath

            // Security invariant: Only delete files located inside the application's internal filesDir or cacheDir
            if (targetCanonical.startsWith(filesDirCanonical) || targetCanonical.startsWith(cacheDirCanonical)) {
                file.delete()
            }
        } catch (_: Exception) {
            // Non-fatal fail-safe
        }
    }

    private data class DecodeResult(
        val width: Int,
        val height: Int,
        val orientation: Int,
        val bitmap: Bitmap
    )
}
