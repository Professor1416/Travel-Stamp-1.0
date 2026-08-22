package com.example.ui.poster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import com.example.data.model.TravelStamp
import com.example.data.model.Trip
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * High-performance, memory-safe bitmap renderer for 1080x1920 (9:16) Travel Stamp posters.
 * Pure presentation logic: strictly read-only, never alters Trip, Stamp, or database records.
 */
object PosterRenderer {

    const val POSTER_WIDTH = 1080
    const val POSTER_HEIGHT = 1920

    fun render(
        context: Context,
        trip: Trip,
        stamp: TravelStamp,
        config: PosterRenderConfig
    ): Bitmap {
        val bitmap = try {
            Bitmap.createBitmap(POSTER_WIDTH, POSTER_HEIGHT, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            Bitmap.createBitmap(POSTER_WIDTH, POSTER_HEIGHT, Bitmap.Config.RGB_565)
        }

        val canvas = Canvas(bitmap)

        when (config.template) {
            PosterTemplate.PHOTO_STAMP -> {
                renderTemplateA(context, canvas, trip, stamp, config)
            }
            PosterTemplate.PASSPORT_STAMP -> {
                renderTemplateB(canvas, trip, stamp)
            }
        }

        return bitmap
    }

    // ==========================================
    // TEMPLATE A: PHOTO + STAMP (SOCIAL HERO)
    // ==========================================
    private fun renderTemplateA(
        context: Context,
        canvas: Canvas,
        trip: Trip,
        stamp: TravelStamp,
        config: PosterRenderConfig
    ) {
        val width = POSTER_WIDTH.toFloat()
        val height = POSTER_HEIGHT.toFloat()

        // 1. Render Photo or Branded Fallback
        var photoDrawn = false
        if (!config.photoUri.isNullOrBlank()) {
            val decodedPhoto = loadSafeOrientedBitmap(
                context = context,
                uriString = config.photoUri,
                reqWidth = POSTER_WIDTH,
                reqHeight = POSTER_HEIGHT
            )
            if (decodedPhoto != null) {
                drawTransformedPhoto(canvas, decodedPhoto, width, height, config.panX, config.panY, config.zoom)
                try {
                    decodedPhoto.recycle()
                } catch (_: Exception) {}
                photoDrawn = true
            }
        }

        if (!photoDrawn) {
            drawBrandedFallbackBackground(canvas, width, height, stamp)
        }

        // 2. Gradient Overlays for High Legibility & Contrast
        drawTemplateAGradients(canvas, width, height)

        // 3. Top Header Branding
        val topHeaderPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.WHITE
            alpha = 210
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.22f
        }
        canvas.drawText("TRAVEL STAMP • EXPEDITION POSTER", width / 2f, 120f, topHeaderPaint)

        // 4. Overlaid Travel Stamp Seal
        val stampCenterY = height * 0.46f
        val stampRadius = 250f

        // Soft backdrop badge circle for stamp visibility on diverse photos
        val badgePaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#F5EBE1")
            alpha = 245
            style = Paint.Style.FILL
            setShadowLayer(30f, 0f, 10f, AndroidColor.argb(120, 0, 0, 0))
        }
        canvas.drawCircle(width / 2f, stampCenterY, stampRadius + 24f, badgePaint)

        val badgeBorderPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#B07D46")
            alpha = 180
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(width / 2f, stampCenterY, stampRadius + 20f, badgeBorderPaint)

        val inkColorInt = parseColor(stamp.inkColorHex, AndroidColor.parseColor("#1E3A2F"))
        drawSealToCanvas(
            canvas = canvas,
            centerX = width / 2f,
            centerY = stampCenterY,
            radius = stampRadius,
            inkColor = inkColorInt,
            stamp = stamp
        )

        // 5. Bottom Metadata Typography
        val contentStartY = height * 0.69f
        val maxTextWidth = width - 180f

        // Trip Title (Max 2 lines)
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.WHITE
            textSize = 54f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val titleLines = wrapAndLimitText(stamp.title, titlePaint, maxTextWidth, 2)
        var currentY = contentStartY
        for (line in titleLines) {
            canvas.drawText(line, width / 2f, currentY, titlePaint)
            currentY += 64f
        }

        currentY += 8f

        // Destination (Max 2 lines)
        val destPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#FFAB91") // Warm accent for dark photo overlay
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        val destText = (if (stamp.destination.isNotBlank()) stamp.destination else trip.destination).uppercase().replace(",", " •")
        val destLines = wrapAndLimitText(destText, destPaint, maxTextWidth, 2)
        for (line in destLines) {
            canvas.drawText(line, width / 2f, currentY, destPaint)
            currentY += 46f
        }

        currentY += 24f

        // Date & Stamp Code Badge Box
        val dateText = "📅 ${stamp.dateText.uppercase()}   •   STAMP ${stamp.stampCode}"
        val infoPillPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.WHITE
            alpha = 40
            style = Paint.Style.FILL
        }
        val pillBorderPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.WHITE
            alpha = 90
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val pillTextPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.06f
        }
        val textWidth = pillTextPaint.measureText(dateText)
        val pillWidth = min(textWidth + 80f, maxTextWidth)
        val pillRect = RectF(
            width / 2f - pillWidth / 2f,
            currentY - 34f,
            width / 2f + pillWidth / 2f,
            currentY + 20f
        )
        canvas.drawRoundRect(pillRect, 27f, 27f, infoPillPaint)
        canvas.drawRoundRect(pillRect, 27f, 27f, pillBorderPaint)
        canvas.drawText(dateText, width / 2f, currentY, pillTextPaint)

        // 6. Bottom subtle branding
        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.WHITE
            alpha = 150
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.16f
        }
        canvas.drawText("TRAVEL STAMP 🏔️ • OFFICIAL DIGITAL PASSPORT", width / 2f, height - 70f, footerPaint)
    }

    private fun drawTemplateAGradients(canvas: Canvas, width: Float, height: Float) {
        // Top subtle shadow for header readability
        val topShader = LinearGradient(
            0f, 0f, 0f, 280f,
            intArrayOf(AndroidColor.argb(190, 0, 0, 0), AndroidColor.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        val topPaint = Paint().apply {
            isAntiAlias = true
            shader = topShader
        }
        canvas.drawRect(0f, 0f, width, 280f, topPaint)

        // Bottom deep gradient for metadata readability
        val bottomShader = LinearGradient(
            0f, height * 0.48f, 0f, height,
            intArrayOf(
                AndroidColor.TRANSPARENT,
                AndroidColor.argb(160, 10, 16, 14),
                AndroidColor.argb(240, 10, 16, 14)
            ),
            floatArrayOf(0f, 0.35f, 1.0f),
            Shader.TileMode.CLAMP
        )
        val bottomPaint = Paint().apply {
            isAntiAlias = true
            shader = bottomShader
        }
        canvas.drawRect(0f, height * 0.48f, width, height, bottomPaint)
    }

    // ==========================================
    // TEMPLATE B: PASSPORT / STAMP FOCUSED
    // ==========================================
    private fun renderTemplateB(
        canvas: Canvas,
        trip: Trip,
        stamp: TravelStamp
    ) {
        val width = POSTER_WIDTH.toFloat()
        val height = POSTER_HEIGHT.toFloat()

        val bgColorInt = AndroidColor.parseColor("#F5EBE1") // Authentic Sand Canvas parchment
        val inkColorInt = parseColor(stamp.inkColorHex, AndroidColor.parseColor("#1E3A2F"))
        val accentGold = AndroidColor.parseColor("#B07D46")
        val innerBorderColor = AndroidColor.parseColor("#E5D5C5")

        // 1. Parchment Background
        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = bgColorInt
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        // Subtle parchment vignette
        val radialShader = RadialGradient(
            width / 2f, height / 2f, height * 0.65f,
            intArrayOf(AndroidColor.TRANSPARENT, AndroidColor.argb(28, 110, 80, 50)),
            null,
            Shader.TileMode.CLAMP
        )
        val vignettePaint = Paint().apply {
            isAntiAlias = true
            shader = radialShader
        }
        canvas.drawRect(0f, 0f, width, height, vignettePaint)

        // 2. Decorative Passport Borders
        val outerMargin = 48f
        val borderPaint = Paint().apply {
            isAntiAlias = true
            color = accentGold
            alpha = 140
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRoundRect(
            RectF(outerMargin, outerMargin, width - outerMargin, height - outerMargin),
            40f, 40f, borderPaint
        )

        val innerMargin = 64f
        val innerDashedPaint = Paint().apply {
            isAntiAlias = true
            color = innerBorderColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)
        }
        canvas.drawRoundRect(
            RectF(innerMargin, innerMargin, width - innerMargin, height - innerMargin),
            32f, 32f, innerDashedPaint
        )

        // 3. Top Passport Memorandum Header
        val headerSubPaint = Paint().apply {
            isAntiAlias = true
            color = accentGold
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.28f
        }
        canvas.drawText("PASSPORT OF THE OPEN TRAIL", width / 2f, 150f, headerSubPaint)

        val headerMainPaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            alpha = 220
            textSize = 36f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
        }
        canvas.drawText("OFFICIAL EXPEDITION MEMORANDUM", width / 2f, 205f, headerMainPaint)

        val linePaint = Paint().apply {
            isAntiAlias = true
            color = accentGold
            alpha = 160
            strokeWidth = 2.5f
        }
        canvas.drawLine(180f, 235f, width - 180f, 235f, linePaint)

        // 4. Large Official Stamp as the Hero
        val stampCenterY = height * 0.44f
        val stampRadius = 310f

        drawSealToCanvas(
            canvas = canvas,
            centerX = width / 2f,
            centerY = stampCenterY,
            radius = stampRadius,
            inkColor = inkColorInt,
            stamp = stamp
        )

        // 5. Trip Metadata Typography
        val contentStartY = height * 0.69f
        val maxTextWidth = width - 200f

        // Trip Name (Max 2 lines)
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            textSize = 58f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val titleLines = wrapAndLimitText(stamp.title, titlePaint, maxTextWidth, 2)
        var currentY = contentStartY
        for (line in titleLines) {
            canvas.drawText(line, width / 2f, currentY, titlePaint)
            currentY += 68f
        }

        currentY += 12f

        // Destination (Max 2 lines)
        val destPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#C85A32") // Terracotta
            textSize = 36f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.1f
        }
        val destText = (if (stamp.destination.isNotBlank()) stamp.destination else trip.destination).uppercase().replace(",", " •")
        val destLines = wrapAndLimitText(destText, destPaint, maxTextWidth, 2)
        for (line in destLines) {
            canvas.drawText(line, width / 2f, currentY, destPaint)
            currentY += 48f
        }

        currentY += 28f

        // Journey Date Box
        val datePaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            alpha = 210
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        canvas.drawText("━◆ DATE OF EXPEDITION: ${stamp.dateText.uppercase()} ◆━", width / 2f, currentY, datePaint)

        currentY += 48f

        // Stamp Code & Serial Box
        val serialBoxPaint = Paint().apply {
            isAntiAlias = true
            color = accentGold
            alpha = 140
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val serialBgPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.WHITE
            alpha = 180
            style = Paint.Style.FILL
        }
        val serialTextPaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val serialLabel = "AUTHENTICATED TRAVEL STAMP: ${stamp.stampCode}"
        val sWidth = min(serialTextPaint.measureText(serialLabel) + 60f, maxTextWidth)
        val sRect = RectF(
            width / 2f - sWidth / 2f,
            currentY - 34f,
            width / 2f + sWidth / 2f,
            currentY + 18f
        )
        canvas.drawRoundRect(sRect, 10f, 10f, serialBgPaint)
        canvas.drawRoundRect(sRect, 10f, 10f, serialBoxPaint)
        canvas.drawText(serialLabel, width / 2f, currentY, serialTextPaint)

        // 6. Bottom Branding
        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            alpha = 160
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.16f
        }
        canvas.drawText("TRAVEL STAMP 🏔️ • OFFICIAL EXPEDITION LOG", width / 2f, height - 80f, footerPaint)
    }

    // ==========================================
    // BRANDED FALLBACK BACKGROUND
    // ==========================================
    private fun drawBrandedFallbackBackground(
        canvas: Canvas,
        width: Float,
        height: Float,
        stamp: TravelStamp
    ) {
        val inkColor = parseColor(stamp.inkColorHex, AndroidColor.parseColor("#1E3A2F"))

        // Rich dusk forest / sand canvas gradient
        val bgShader = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(
                AndroidColor.parseColor("#1B332B"),
                AndroidColor.parseColor("#14241F"),
                AndroidColor.parseColor("#0C1512")
            ),
            null,
            Shader.TileMode.CLAMP
        )
        val bgPaint = Paint().apply {
            isAntiAlias = true
            shader = bgShader
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        // Subtle topographic contour rings watermark
        val contourPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.WHITE
            alpha = 14
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val centerX = width / 2f
        val centerY = height * 0.44f
        for (r in listOf(400f, 520f, 650f, 780f, 920f)) {
            canvas.drawCircle(centerX, centerY, r, contourPaint)
        }

        // Subtle Compass Crosshair lines
        val crossPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.WHITE
            alpha = 18
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(20f, 14f), 0f)
        }
        canvas.drawLine(centerX, 200f, centerX, height - 300f, crossPaint)
        canvas.drawLine(100f, centerY, width - 100f, centerY, crossPaint)
    }

    // ==========================================
    // AUTHENTIC TRAVEL STAMP SEAL DRAWING
    // ==========================================
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
            alpha = 110
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
            alpha = 190
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
        }
        canvas.drawCircle(centerX, centerY, radius - 36f, dashedPaint)

        // Fine hairline
        val hairlinePaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            alpha = 110
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawCircle(centerX, centerY, radius - 48f, hairlinePaint)

        // Inner Stamp Motif Emoji
        val motifEmoji = when (stamp.stampStyle) {
            "COMPASS" -> "🧭"
            "PINE" -> "🌲"
            "EXPEDITION" -> "⚜️"
            else -> "🏔️"
        }
        val iconPaint = Paint().apply {
            isAntiAlias = true
            textSize = radius * 0.20f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(motifEmoji, centerX, centerY - radius * 0.44f, iconPaint)

        // Stamp Title
        val sealTitlePaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            textSize = radius * 0.15f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.06f
        }
        val sealTitle = truncateTextSingleLine(stamp.title.uppercase(), sealTitlePaint, radius * 1.5f)
        canvas.drawText(sealTitle, centerX, centerY - radius * 0.20f, sealTitlePaint)

        // Destination
        if (stamp.destination.isNotBlank()) {
            val sealDestPaint = Paint().apply {
                isAntiAlias = true
                color = inkColor
                alpha = 220
                textSize = radius * 0.10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.10f
            }
            val sealDest = truncateTextSingleLine(stamp.destination.uppercase().replace(",", " •"), sealDestPaint, radius * 1.4f)
            canvas.drawText(sealDest, centerX, centerY - radius * 0.05f, sealDestPaint)
        }

        // Date row
        val sealDatePaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            textSize = radius * 0.10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        canvas.drawText("━◆ ${stamp.dateText.uppercase()} ◆━", centerX, centerY + radius * 0.14f, sealDatePaint)

        // Label: TRAVEL STAMP
        val stampLabelPaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            alpha = 210
            textSize = radius * 0.085f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.20f
        }
        canvas.drawText("TRAVEL STAMP", centerX, centerY + radius * 0.32f, stampLabelPaint)

        // Serial box e.g. #001
        val boxWidth = radius * 0.65f
        val boxHeight = radius * 0.18f
        val boxRect = RectF(
            centerX - boxWidth / 2f,
            centerY + radius * 0.44f,
            centerX + boxWidth / 2f,
            centerY + radius * 0.44f + boxHeight
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
            textSize = radius * 0.12f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(stamp.stampCode, centerX, centerY + radius * 0.44f + boxHeight * 0.72f, codePaint)
    }

    // ==========================================
    // MEMORY-SAFE PHOTO DECODING & TRANSFORMS
    // ==========================================
    private fun loadSafeOrientedBitmap(
        context: Context,
        uriString: String,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            val openInput: () -> InputStream? = {
                if (uri.scheme == "file" || uri.scheme == null) {
                    val file = File(uri.path ?: uriString)
                    if (file.exists() && file.canRead()) FileInputStream(file) else null
                } else {
                    context.contentResolver.openInputStream(uri)
                }
            }

            // 1. Inspect dimensions without loading pixels
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            openInput()?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            val outWidth = options.outWidth
            val outHeight = options.outHeight
            if (outWidth <= 0 || outHeight <= 0) return null

            // 2. Calculate safe power-of-2 sample size
            var sampleSize = 1
            if (outHeight > reqHeight || outWidth > reqWidth) {
                val halfHeight = outHeight / 2
                val halfWidth = outWidth / 2
                while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
                    sampleSize *= 2
                }
            }

            // 3. Decode scaled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val rawBitmap = openInput()?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

            // 4. Handle EXIF orientation
            val orientation = getExifOrientation(context, uri, uriString)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotated != rawBitmap) {
                    rawBitmap.recycle()
                }
                rotated
            } else {
                rawBitmap
            }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun getExifOrientation(context: Context, uri: Uri, uriString: String): Int {
        return try {
            if (uri.scheme == "file" || uri.scheme == null) {
                val file = File(uri.path ?: uriString)
                if (file.exists()) {
                    ExifInterface(file.absolutePath).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } else ExifInterface.ORIENTATION_NORMAL
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            }
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * Center-crop / fill transformation with pan and zoom.
     * Prevents image distortion regardless of source aspect ratio.
     */
    private fun drawTransformedPhoto(
        canvas: Canvas,
        bitmap: Bitmap,
        targetWidth: Float,
        targetHeight: Float,
        panX: Float,
        panY: Float,
        zoom: Float
    ) {
        val srcWidth = bitmap.width.toFloat()
        val srcHeight = bitmap.height.toFloat()
        val clampedZoom = zoom.coerceIn(1.0f, 3.5f)

        // Center-crop base scale to fill target 9:16 rectangle
        val scaleX = targetWidth / srcWidth
        val scaleY = targetHeight / srcHeight
        val baseScale = max(scaleX, scaleY)
        val finalScale = baseScale * clampedZoom

        val scaledW = srcWidth * finalScale
        val scaledH = srcHeight * finalScale

        // Compute translation so photo is centered by default + user pan
        val maxPanX = max(0f, (scaledW - targetWidth) / 2f)
        val maxPanY = max(0f, (scaledH - targetHeight) / 2f)
        val clampedPanX = (panX * targetWidth).coerceIn(-maxPanX, maxPanX)
        val clampedPanY = (panY * targetHeight).coerceIn(-maxPanY, maxPanY)

        val tx = (targetWidth - scaledW) / 2f + clampedPanX
        val ty = (targetHeight - scaledH) / 2f + clampedPanY

        val matrix = Matrix().apply {
            postScale(finalScale, finalScale)
            postTranslate(tx, ty)
        }

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    // ==========================================
    // TEXT WRAPPING & UNICODE SAFETY
    // ==========================================
    fun wrapAndLimitText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val words = trimmed.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(candidate) <= maxWidth) {
                currentLine = candidate
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                    if (lines.size >= maxLines) break
                }
                currentLine = word
            }
        }

        if (lines.size < maxLines && currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        // If the last line still overflows or there are more words left, truncate with ellipsis
        if (lines.isNotEmpty()) {
            val lastIdx = lines.lastIndex
            lines[lastIdx] = truncateTextSingleLine(lines[lastIdx], paint, maxWidth)
        }

        return lines
    }

    fun truncateTextSingleLine(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }

    private fun parseColor(hex: String, fallback: Int): Int {
        return try {
            val cleanHex = if (hex.startsWith("#")) hex else "#$hex"
            AndroidColor.parseColor(cleanHex)
        } catch (_: Exception) {
            fallback
        }
    }
}
