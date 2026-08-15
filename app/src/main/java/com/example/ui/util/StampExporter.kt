package com.example.ui.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.TravelStamp
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-resolution, standalone image exporter for Travel Stamps.
 * Renders authentic passport-style artwork offline to a clean Bitmap and handles Gallery saving and System Sharing.
 */
object StampExporter {

    private fun parseColor(hex: String, fallback: Int = AndroidColor.parseColor("#1E3A2F")): Int {
        return try {
            val cleanHex = if (hex.startsWith("#")) hex else "#$hex"
            AndroidColor.parseColor(cleanHex)
        } catch (_: Exception) {
            fallback
        }
    }

    /**
     * Renders a clean 1080x1350 passport-card bitmap suitable for high quality sharing and phone gallery.
     */
    fun createStampBitmap(
        context: Context,
        stamp: TravelStamp,
        photoUri: String? = null
    ): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val inkColorInt = parseColor(stamp.inkColorHex, AndroidColor.parseColor("#1E3A2F"))
        val bgColorInt = AndroidColor.parseColor("#F5EBE1") // Authentic warm Sand Canvas parchment
        val innerBorderColor = AndroidColor.parseColor("#E5D5C5")
        val accentGold = AndroidColor.parseColor("#B07D46")

        // 1. Draw Background Parchment
        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = bgColorInt
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Draw Outer & Inner Decorative Borders
        val borderPaint = Paint().apply {
            isAntiAlias = true
            color = accentGold
            alpha = 130
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val outerMargin = 40f
        canvas.drawRoundRect(
            RectF(outerMargin, outerMargin, width - outerMargin, height - outerMargin),
            32f, 32f, borderPaint
        )

        val innerMargin = 52f
        val innerDashedPaint = Paint().apply {
            isAntiAlias = true
            color = innerBorderColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
        canvas.drawRoundRect(
            RectF(innerMargin, innerMargin, width - innerMargin, height - innerMargin),
            24f, 24f, innerDashedPaint
        )

        // 3. Top Header: PASSPORT MEMORANDUM
        val headerPaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            alpha = 180
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.25f
        }
        canvas.drawText("PASSPORT MEMORANDUM • OFFICIAL EXPEDITION LOG", width / 2f, 110f, headerPaint)

        val linePaint = Paint().apply {
            isAntiAlias = true
            color = accentGold
            alpha = 140
            strokeWidth = 2f
        }
        canvas.drawLine(140f, 132f, width - 140f, 132f, linePaint)

        // 4. Optional Photo Inset (or default decorative space)
        var stampCenterY = 560f
        var stampRadius = 260f

        if (!photoUri.isNullOrBlank()) {
            val photoBitmap = loadScaledBitmap(context, photoUri, 360, 260)
            if (photoBitmap != null) {
                val photoRect = RectF(width / 2f - 180f, 160f, width / 2f + 180f, 400f)
                val photoBorderPaint = Paint().apply {
                    isAntiAlias = true
                    color = innerBorderColor
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                val photoFillPaint = Paint().apply { isAntiAlias = true }
                
                // Draw rounded photo card
                canvas.save()
                val clipPath = Path().apply {
                    addRoundRect(photoRect, 18f, 18f, Path.Direction.CW)
                }
                canvas.clipPath(clipPath)
                val srcRect = Rect(0, 0, photoBitmap.width, photoBitmap.height)
                canvas.drawBitmap(photoBitmap, srcRect, photoRect, photoFillPaint)
                canvas.restore()
                canvas.drawRoundRect(photoRect, 18f, 18f, photoBorderPaint)
                try {
                    photoBitmap.recycle()
                } catch (_: Exception) {}

                stampCenterY = 700f
                stampRadius = 240f
            }
        }

        // 5. Draw Stamp Seal on Canvas
        drawSealToCanvas(
            canvas = canvas,
            centerX = width / 2f,
            centerY = stampCenterY,
            radius = stampRadius,
            inkColor = inkColorInt,
            stamp = stamp
        )

        // 6. Draw Trip Information & Details below Stamp
        val bottomSectionY = if (!photoUri.isNullOrBlank()) 1010f else 930f

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            textSize = 44f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val safeTitle = truncateText(stamp.title, titlePaint, width - 200f)
        canvas.drawText(safeTitle, width / 2f, bottomSectionY, titlePaint)

        val destPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#C85A32")
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        val safeDest = truncateText(stamp.destination.uppercase().replace(",", " •"), destPaint, width - 240f)
        canvas.drawText(safeDest, width / 2f, bottomSectionY + 46f, destPaint)

        val subStatsPaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            alpha = 180
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.05f
        }
        val statsText = "📅 ${stamp.dateText}   •   👥 ${stamp.peopleCount} EXPLORERS   •   ✨ ${stamp.momentsCount} MOMENTS"
        canvas.drawText(statsText, width / 2f, bottomSectionY + 92f, subStatsPaint)

        // Reflection Note if present
        if (!stamp.reflectionNote.isNullOrBlank()) {
            val reflectionPaint = Paint().apply {
                isAntiAlias = true
                color = inkColorInt
                alpha = 200
                textSize = 22f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                textAlign = Paint.Align.CENTER
            }
            val quote = "“${stamp.reflectionNote.trim()}”"
            val safeQuote = truncateText(quote, reflectionPaint, width - 260f)
            canvas.drawText(safeQuote, width / 2f, bottomSectionY + 140f, reflectionPaint)
        }

        // 7. Bottom Branding
        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            alpha = 140
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        canvas.drawText("TRAVEL STAMP 🏔️ • OFFICIAL DIGITAL PASSPORT", width / 2f, height - 76f, footerPaint)

        return bitmap
    }

    private fun drawSealToCanvas(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        inkColor: Int,
        stamp: TravelStamp
    ) {
        // Scalloped outer teeth
        val teethCount = 48
        val teethPath = Path()
        val rOuter = radius
        val rInner = radius - 10f

        for (i in 0 until teethCount) {
            val angle = (i.toFloat() / teethCount.toFloat()) * (2f * Math.PI.toFloat())
            val nextAngle = ((i + 1).toFloat() / teethCount.toFloat()) * (2f * Math.PI.toFloat())
            val midAngle = (angle + nextAngle) / 2f

            val x1 = centerX + rInner * cos(angle)
            val y1 = centerY + rInner * sin(angle)
            val xMid = centerX + rOuter * cos(midAngle)
            val yMid = centerY + rOuter * sin(midAngle)

            if (i == 0) teethPath.moveTo(x1, y1)
            teethPath.lineTo(xMid, yMid)
        }
        teethPath.close()

        val teethPaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            alpha = 90
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawPath(teethPath, teethPaint)

        // Outer solid circle
        val outerSolidPaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(centerX, centerY, radius - 12f, outerSolidPaint)

        // Inner dashed ring
        val dashedPaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            alpha = 180
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
        }
        canvas.drawCircle(centerX, centerY, radius - 36f, dashedPaint)

        // Fine hairline
        val hairlinePaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            alpha = 100
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawCircle(centerX, centerY, radius - 48f, hairlinePaint)

        // Inner Stamp Content
        val iconPaint = Paint().apply {
            isAntiAlias = true
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("🏔️", centerX, centerY - radius * 0.44f, iconPaint)

        // Destination / Title
        val sealTitlePaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            textSize = 34f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        val sealTitle = truncateText(stamp.title.uppercase(), sealTitlePaint, radius * 1.5f)
        canvas.drawText(sealTitle, centerX, centerY - radius * 0.20f, sealTitlePaint)

        // Region / Destination
        if (stamp.destination.isNotBlank()) {
            val sealDestPaint = Paint().apply {
                isAntiAlias = true
                color = inkColor
                alpha = 220
                textSize = 20f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.12f
            }
            val sealDest = truncateText(stamp.destination.uppercase().replace(",", " •"), sealDestPaint, radius * 1.4f)
            canvas.drawText(sealDest, centerX, centerY - radius * 0.05f, sealDestPaint)
        }

        // Date row
        val sealDatePaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.1f
        }
        canvas.drawText("━◆ ${stamp.dateText.uppercase()} ◆━", centerX, centerY + radius * 0.14f, sealDatePaint)

        // Label: TRAVEL STAMP
        val stampLabelPaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            alpha = 200
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.22f
        }
        canvas.drawText("TRAVEL STAMP", centerX, centerY + radius * 0.32f, stampLabelPaint)

        // Serial box e.g. #001
        val boxWidth = 140f
        val boxHeight = 44f
        val boxRect = RectF(
            centerX - boxWidth / 2f,
            centerY + radius * 0.42f,
            centerX + boxWidth / 2f,
            centerY + radius * 0.42f + boxHeight
        )
        val boxBorderPaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(boxRect, 8f, 8f, boxBorderPaint)

        val codePaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(stamp.stampCode, centerX, centerY + radius * 0.42f + 32f, codePaint)
    }

    private fun loadScaledBitmap(context: Context, uriString: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            var input: InputStream? = if (uri.scheme == "file" || uri.scheme == null) {
                val file = File(uri.path ?: uriString)
                if (file.exists()) file.inputStream() else null
            } else {
                context.contentResolver.openInputStream(uri)
            }

            if (input == null) return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(input, null, options)
            input.close()

            // Calculate inSampleSize
            var sampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            input = if (uri.scheme == "file" || uri.scheme == null) {
                File(uri.path ?: uriString).inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
            val result = BitmapFactory.decodeStream(input, null, decodeOptions)
            input?.close()
            result
        } catch (_: Exception) {
            null
        }
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }

    /**
     * Saves the rendered stamp bitmap to the user's Gallery via modern Android MediaStore.
     */
    fun saveToGallery(context: Context, bitmap: Bitmap, stamp: TravelStamp): Boolean {
        return try {
            val safeName = stamp.title.replace(Regex("[^a-zA-Z0-9]"), "_").take(30)
            val safeCode = stamp.stampCode.replace("#", "")
            val filename = "TravelStamp_${safeCode}_${safeName}.png"

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
     */
    fun getShareableUri(context: Context, bitmap: Bitmap, stamp: TravelStamp): Uri? {
        return try {
            val stampsDir = File(context.cacheDir, "stamps")
            if (!stampsDir.exists()) {
                stampsDir.mkdirs()
            }
            val safeName = stamp.title.replace(Regex("[^a-zA-Z0-9]"), "_").take(30)
            val safeCode = stamp.stampCode.replace("#", "")
            val file = File(stampsDir, "TravelStamp_${safeCode}_${safeName}.png")

            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }

            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
