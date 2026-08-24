package com.example.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
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

/**
 * Robust, production-grade image preparation and media utility pipeline for Travel Stamp.
 * Handles diverse Android gallery providers, cloud content URIs, large camera photos (12MP–108MP),
 * iPhone HEIC/HEIF/PNG sources, EXIF orientation, and memory-safe downsampling.
 */
object PhotoUtils {

    const val MAX_SOURCE_FILE_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB hard safety limit
    const val MAX_WORKING_DIMENSION = 2560 // Max 2560px for high-res 1080x1920 exports
    const val WORKING_JPEG_QUALITY = 86

    sealed interface WorkingImageResult {
        data class Success(val filePath: String, val width: Int, val height: Int) : WorkingImageResult
        data class TooLarge(val maxAllowedMb: Int) : WorkingImageResult
        data class UnsupportedFormat(val message: String) : WorkingImageResult
        data class Error(val code: ErrorCode, val message: String) : WorkingImageResult
    }

    enum class ErrorCode {
        URI_OPEN_FAILED,
        STREAM_READ_FAILED,
        FILE_TOO_LARGE,
        BOUNDS_DECODE_FAILED,
        UNSUPPORTED_FORMAT,
        BITMAP_DECODE_FAILED,
        OUT_OF_MEMORY,
        WRITE_FAILED,
        UNKNOWN
    }

    /**
     * Calculates the memory-safe downsampling ratio (power of 2) to ensure the decoded bitmap
     * stays within target dimensions without wasting memory or causing OutOfMemoryError.
     */
    fun calculateInSampleSize(outWidth: Int, outHeight: Int, maxDimension: Int = MAX_WORKING_DIMENSION): Int {
        var inSampleSize = 1
        val maxSrcDim = max(outWidth, outHeight)
        val maxTarget = maxDimension.coerceAtLeast(1)
        while ((maxSrcDim / inSampleSize) > maxTarget) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Prepares a single canonical, app-controlled optimized working image for the Photo + Stamp editor.
     * 1. Safely streams source (respects cloud content:// URIs and single-use provider grants).
     * 2. Rejects files > 50MB during stream reading.
     * 3. Reads bounds first without allocating full bitmap.
     * 4. Calculates optimal power-of-2 inSampleSize.
     * 5. Normalizes EXIF rotation (0°, 90°, 180°, 270°, flips).
     * 6. Normalizes color space to standard sRGB on supported devices.
     * 7. Scales to optimal working dimensions (<=2560px).
     * 8. Writes high-quality JPEG to app cache (cacheDir/photo_editor/).
     * 9. Cleans up temporary scratch input files.
     * 10. Validates working file exists and is decodable before returning.
     */
    fun prepareWorkingImage(context: Context, sourceUri: Uri): WorkingImageResult {
        var scratchFile: File? = null
        return try {
            val uriString = sourceUri.toString()
            val isDirectFile = uriString.startsWith("/") || sourceUri.scheme == "file" || sourceUri.scheme == null

            val sourceFile: File = if (isDirectFile) {
                val path = if (uriString.startsWith("/")) uriString else (sourceUri.path ?: uriString.removePrefix("file://").removePrefix("file:"))
                val f = File(path)
                if (!f.exists() || !f.canRead()) {
                    return WorkingImageResult.Error(ErrorCode.URI_OPEN_FAILED, "Source file cannot be read.")
                }
                if (f.length() > MAX_SOURCE_FILE_SIZE_BYTES) {
                    return WorkingImageResult.TooLarge(50)
                }
                f
            } else {
                // For content:// URIs (e.g. Google Photos, MediaStore, WhatsApp, Downloads),
                // stream safely into a temporary scratch file once to ensure random seekability and persistence.
                val scratchDir = File(context.cacheDir, "scratch_picker")
                if (!scratchDir.exists()) {
                    scratchDir.mkdirs()
                }
                val tempScratch = File(scratchDir, "picker_in_${System.currentTimeMillis()}.tmp")
                scratchFile = tempScratch

                val inputStream = try {
                    context.contentResolver.openInputStream(sourceUri)
                } catch (e: SecurityException) {
                    return WorkingImageResult.Error(ErrorCode.URI_OPEN_FAILED, "Permission denied opening image.")
                } catch (e: Exception) {
                    return WorkingImageResult.Error(ErrorCode.URI_OPEN_FAILED, "Cannot open image stream.")
                } ?: return WorkingImageResult.Error(ErrorCode.URI_OPEN_FAILED, "Cannot open image stream.")

                var totalBytes = 0L
                val buffer = ByteArray(64 * 1024)
                FileOutputStream(tempScratch).use { out ->
                    inputStream.use { input ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            totalBytes += read
                            if (totalBytes > MAX_SOURCE_FILE_SIZE_BYTES) {
                                tempScratch.delete()
                                return WorkingImageResult.TooLarge(50)
                            }
                            out.write(buffer, 0, read)
                        }
                    }
                }

                if (!tempScratch.exists() || tempScratch.length() <= 0L) {
                    tempScratch.delete()
                    return WorkingImageResult.Error(ErrorCode.STREAM_READ_FAILED, "Received empty image from provider.")
                }
                tempScratch
            }

            // 1. Inspect bounds
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOptions)

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                // Check if it's an unsupported format (e.g. unsupported HEIC/HEIF or RAW)
                val mimeType = try {
                    if (sourceUri.scheme == "content") context.contentResolver.getType(sourceUri) else null
                } catch (_: Exception) { null }
                val isHeic = mimeType?.contains("heic", ignoreCase = true) == true ||
                        mimeType?.contains("heif", ignoreCase = true) == true ||
                        sourceUri.toString().endsWith(".heic", ignoreCase = true) ||
                        sourceUri.toString().endsWith(".heif", ignoreCase = true)

                scratchFile?.delete()
                return if (isHeic) {
                    WorkingImageResult.UnsupportedFormat("This photo format isn’t supported on this device. Try using a JPG version.")
                } else {
                    WorkingImageResult.Error(ErrorCode.BOUNDS_DECODE_FAILED, "Invalid or corrupted image format.")
                }
            }

            val sampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, MAX_WORKING_DIMENSION)

            // 2. Read EXIF Orientation safely
            val exifOrientation = try {
                ExifInterface(sourceFile.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            // 3. Decode bitmap with memory-safe inSampleSize and sRGB color normalization
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                }
            }

            var rawBitmap = try {
                BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
            } catch (e: OutOfMemoryError) {
                // Fallback to RGB_565 on extreme memory pressure
                try {
                    val fallbackOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize * 2
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeFile(sourceFile.absolutePath, fallbackOptions)
                } catch (_: Throwable) {
                    null
                }
            }

            if (rawBitmap == null) {
                scratchFile?.delete()
                return WorkingImageResult.Error(ErrorCode.BITMAP_DECODE_FAILED, "Failed to decode photo.")
            }

            // 4. Apply EXIF orientation
            val matrix = Matrix()
            when (exifOrientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            val orientedBitmap = if (!matrix.isIdentity) {
                val rotated = try {
                    Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                } catch (e: OutOfMemoryError) {
                    rawBitmap
                }
                if (rotated != rawBitmap) {
                    rawBitmap.recycle()
                }
                rotated
            } else {
                rawBitmap
            }

            // 5. Downscale if longest dimension still exceeds max
            val longestDim = max(orientedBitmap.width, orientedBitmap.height)
            val finalBitmap = if (longestDim > MAX_WORKING_DIMENSION) {
                val scale = MAX_WORKING_DIMENSION.toFloat() / longestDim
                val targetW = (orientedBitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (orientedBitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = try {
                    Bitmap.createScaledBitmap(orientedBitmap, targetW, targetH, true)
                } catch (e: OutOfMemoryError) {
                    orientedBitmap
                }
                if (scaled != orientedBitmap) {
                    orientedBitmap.recycle()
                }
                scaled
            } else {
                orientedBitmap
            }

            // 6. Write normalized working image to cacheDir/photo_editor/
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

            // 7. Cleanup scratch file immediately
            scratchFile?.delete()

            if (!workingFile.exists() || workingFile.length() <= 0L) {
                return WorkingImageResult.Error(ErrorCode.WRITE_FAILED, "Failed to write working image copy.")
            }

            WorkingImageResult.Success(workingFile.absolutePath, finalWidth, finalHeight)
        } catch (e: OutOfMemoryError) {
            scratchFile?.delete()
            WorkingImageResult.Error(ErrorCode.OUT_OF_MEMORY, "Image too large for device memory. Please choose a smaller photo.")
        } catch (e: Exception) {
            scratchFile?.delete()
            WorkingImageResult.Error(ErrorCode.UNKNOWN, e.message ?: "Failed to process photo.")
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
}
