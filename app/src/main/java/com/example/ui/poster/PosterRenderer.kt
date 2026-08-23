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
 * High-performance, memory-safe bitmap renderer for Travel Stamp editions across
 * Square (1080x1080), Portrait (1080x1440), and Story (1080x1920) formats.
 * Pure presentation logic: strictly read-only, never alters Trip, Stamp, or database records.
 */
object PosterRenderer {

    const val POSTER_WIDTH = 1080
    const val POSTER_HEIGHT = 1920

    data class FittedTextLayout(
        val lines: List<String>,
        val paint: Paint,
        val lineHeight: Float,
        val totalHeight: Float
    )

    fun render(
        context: Context,
        trip: Trip,
        stamp: TravelStamp,
        config: PosterRenderConfig
    ): Bitmap {
        val targetWidth = config.format.width
        val targetHeight = config.format.height

        val bitmap = try {
            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
        }

        val canvas = Canvas(bitmap)

        when (config.template) {
            PosterTemplate.PHOTO_STAMP -> {
                renderTemplateA(context, canvas, trip, stamp, config, targetWidth, targetHeight)
            }
            PosterTemplate.PASSPORT_STAMP -> {
                renderTemplateB(canvas, trip, stamp, config.format, targetWidth, targetHeight)
            }
        }

        return bitmap
    }

    // ==========================================
    // TEMPLATE B: RESPONSIVE PASSPORT STAMP (PASS B)
    // ==========================================
    private fun renderTemplateB(
        canvas: Canvas,
        trip: Trip,
        stamp: TravelStamp,
        format: StampEditionFormat,
        widthPx: Int,
        heightPx: Int
    ) {
        val width = widthPx.toFloat()
        val height = heightPx.toFloat()

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

        // 2. Responsive Geometry Parameters derived from Format
        val outerMargin: Float
        val innerMargin: Float
        val outerStroke: Float
        val innerStroke: Float
        val headerSubY: Float
        val headerSubSize: Float
        val headerMainY: Float
        val headerMainSize: Float
        val dividerY: Float
        val dividerMargin: Float
        val stampCenterY: Float
        val stampRadius: Float
        val contentStartY: Float
        val titleMaxSize: Float
        val titleMinSize: Float
        val destMaxSize: Float
        val destMinSize: Float
        val dateSize: Float
        val serialSize: Float
        val footerY: Float
        val footerSize: Float

        when (format) {
            StampEditionFormat.SQUARE -> {
                // 1080 x 1080
                outerMargin = 34f
                innerMargin = 48f
                outerStroke = 4f
                innerStroke = 2.5f
                headerSubY = 88f
                headerSubSize = 18f
                headerMainY = 126f
                headerMainSize = 25f
                dividerY = 146f
                dividerMargin = 220f
                stampCenterY = 415f
                stampRadius = 215f
                contentStartY = 680f
                titleMaxSize = 42f
                titleMinSize = 28f
                destMaxSize = 28f
                destMinSize = 19f
                dateSize = 22f
                serialSize = 21f
                footerY = height - 58f
                footerSize = 17f
            }
            StampEditionFormat.PORTRAIT -> {
                // 1080 x 1440 (Classic 3:4 Passport Certificate)
                outerMargin = 42f
                innerMargin = 58f
                outerStroke = 5f
                innerStroke = 3f
                headerSubY = 118f
                headerSubSize = 22f
                headerMainY = 165f
                headerMainSize = 32f
                dividerY = 192f
                dividerMargin = 190f
                stampCenterY = 545f
                stampRadius = 275f
                contentStartY = 885f
                titleMaxSize = 52f
                titleMinSize = 34f
                destMaxSize = 34f
                destMinSize = 22f
                dateSize = 26f
                serialSize = 25f
                footerY = height - 74f
                footerSize = 20f
            }
            StampEditionFormat.STORY -> {
                // 1080 x 1920 (9:16 Story)
                outerMargin = 48f
                innerMargin = 64f
                outerStroke = 5f
                innerStroke = 3f
                headerSubY = 165f
                headerSubSize = 24f
                headerMainY = 222f
                headerMainSize = 36f
                dividerY = 252f
                dividerMargin = 180f
                stampCenterY = 780f
                stampRadius = 325f
                contentStartY = 1180f
                titleMaxSize = 58f
                titleMinSize = 36f
                destMaxSize = 36f
                destMinSize = 24f
                dateSize = 28f
                serialSize = 28f
                footerY = height - 85f
                footerSize = 22f
            }
        }

        // 3. Decorative Passport Borders
        val borderPaint = Paint().apply {
            isAntiAlias = true
            color = accentGold
            alpha = 140
            style = Paint.Style.STROKE
            strokeWidth = outerStroke
        }
        canvas.drawRoundRect(
            RectF(outerMargin, outerMargin, width - outerMargin, height - outerMargin),
            36f, 36f, borderPaint
        )

        val innerDashedPaint = Paint().apply {
            isAntiAlias = true
            color = innerBorderColor
            style = Paint.Style.STROKE
            strokeWidth = innerStroke
            pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)
        }
        canvas.drawRoundRect(
            RectF(innerMargin, innerMargin, width - innerMargin, height - innerMargin),
            28f, 28f, innerDashedPaint
        )

        // 4. Top Passport Memorandum Header
        val headerSubPaint = Paint().apply {
            isAntiAlias = true
            color = accentGold
            textSize = headerSubSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.28f
        }
        canvas.drawText("PASSPORT OF THE OPEN TRAIL", width / 2f, headerSubY, headerSubPaint)

        val headerMainPaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            alpha = 220
            textSize = headerMainSize
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
        }
        canvas.drawText("OFFICIAL EXPEDITION MEMORANDUM", width / 2f, headerMainY, headerMainPaint)

        val linePaint = Paint().apply {
            isAntiAlias = true
            color = accentGold
            alpha = 160
            strokeWidth = 2.5f
        }
        canvas.drawLine(dividerMargin, dividerY, width - dividerMargin, dividerY, linePaint)

        // 5. Hero Canonical Official Stamp Seal
        drawSealToCanvas(
            canvas = canvas,
            centerX = width / 2f,
            centerY = stampCenterY,
            radius = stampRadius,
            inkColor = inkColorInt,
            stamp = stamp
        )

        // 6. Responsive Bottom Metadata Typography (No Truncation Bug)
        val maxTextWidth = width - (innerMargin * 2f + 60f)

        // A. Trip Title (Max 2 lines, fitted smoothly)
        val baseTitlePaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val titleLayout = fitResponsiveText(
            text = stamp.title,
            basePaint = baseTitlePaint,
            maxWidth = maxTextWidth,
            maxLines = 2,
            maxTextSize = titleMaxSize,
            minTextSize = titleMinSize,
            lineSpacingMultiplier = 1.22f
        )

        var currentY = contentStartY
        for (line in titleLayout.lines) {
            canvas.drawText(line, width / 2f, currentY, titleLayout.paint)
            currentY += titleLayout.lineHeight
        }

        currentY += when (format) {
            StampEditionFormat.SQUARE -> 6f
            StampEditionFormat.PORTRAIT -> 10f
            StampEditionFormat.STORY -> 14f
        }

        // B. Destination & Location (Max 2 lines, fitted smoothly)
        val destRaw = (if (stamp.destination.isNotBlank()) stamp.destination else trip.destination)
            .uppercase()
            .replace(",", " •")
        val baseDestPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#C85A32") // Terracotta
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        val destLayout = fitResponsiveText(
            text = destRaw,
            basePaint = baseDestPaint,
            maxWidth = maxTextWidth,
            maxLines = 2,
            maxTextSize = destMaxSize,
            minTextSize = destMinSize,
            lineSpacingMultiplier = 1.25f
        )

        for (line in destLayout.lines) {
            canvas.drawText(line, width / 2f, currentY, destLayout.paint)
            currentY += destLayout.lineHeight
        }

        currentY += when (format) {
            StampEditionFormat.SQUARE -> 14f
            StampEditionFormat.PORTRAIT -> 22f
            StampEditionFormat.STORY -> 28f
        }

        // C. Journey Date Line
        val datePaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            alpha = 210
            textSize = dateSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.10f
        }
        val dateLabel = "━◆ DATE OF EXPEDITION: ${stamp.dateText.uppercase()} ◆━"
        canvas.drawText(dateLabel, width / 2f, currentY, datePaint)

        currentY += when (format) {
            StampEditionFormat.SQUARE -> 34f
            StampEditionFormat.PORTRAIT -> 44f
            StampEditionFormat.STORY -> 48f
        }

        // D. Stamp Code & Serial Box
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
            alpha = 190
            style = Paint.Style.FILL
        }
        val serialTextPaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            textSize = serialSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val serialLabel = "AUTHENTICATED TRAVEL STAMP: ${stamp.stampCode}"
        val sWidth = min(serialTextPaint.measureText(serialLabel) + 50f, maxTextWidth)
        val sHalfH = serialSize * 0.9f
        val sRect = RectF(
            width / 2f - sWidth / 2f,
            currentY - sHalfH,
            width / 2f + sWidth / 2f,
            currentY + sHalfH * 0.6f
        )
        canvas.drawRoundRect(sRect, 10f, 10f, serialBgPaint)
        canvas.drawRoundRect(sRect, 10f, 10f, serialBoxPaint)
        canvas.drawText(serialLabel, width / 2f, currentY, serialTextPaint)

        // 7. Bottom Branding / Watermark
        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = inkColorInt
            alpha = 160
            textSize = footerSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.16f
        }
        canvas.drawText("TRAVEL STAMP 🏔️ • OFFICIAL EXPEDITION LOG", width / 2f, footerY, footerPaint)
    }

    // ==========================================
    // TEMPLATE A: PHOTO + STAMP (SOCIAL HERO)
    // ==========================================
    private fun renderTemplateA(
        context: Context,
        canvas: Canvas,
        trip: Trip,
        stamp: TravelStamp,
        config: PosterRenderConfig,
        widthPx: Int,
        heightPx: Int
    ) {
        val width = widthPx.toFloat()
        val height = heightPx.toFloat()

        // 1. Render Photo or Branded Fallback
        var photoDrawn = false
        if (!config.photoUri.isNullOrBlank()) {
            val decodedPhoto = loadSafeOrientedBitmap(
                context = context,
                uriString = config.photoUri,
                reqWidth = widthPx,
                reqHeight = heightPx
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

        // 4. Overlaid Travel Stamp Seal (Respects user drag position and discrete StampSize)
        val baseStampRadius = min(width * 0.22f, height * 0.155f)
        val stampRadius = baseStampRadius * config.stampSize.scale

        val minX = stampRadius + 36f
        val maxX = width - stampRadius - 36f
        val minY = stampRadius + 140f
        val maxY = height - stampRadius - 260f

        val stampCenterX = (config.stampPositionX * width).coerceIn(minX, maxX)
        val stampCenterY = (config.stampPositionY * height).coerceIn(minY, maxY)

        // Soft backdrop badge circle for stamp visibility on diverse photos
        val badgePaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#F5EBE1")
            alpha = 245
            style = Paint.Style.FILL
            setShadowLayer(30f, 0f, 10f, AndroidColor.argb(120, 0, 0, 0))
        }
        canvas.drawCircle(stampCenterX, stampCenterY, stampRadius + 20f, badgePaint)

        val badgeBorderPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#B07D46")
            alpha = 180
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(stampCenterX, stampCenterY, stampRadius + 16f, badgeBorderPaint)

        val inkColorInt = parseColor(stamp.inkColorHex, AndroidColor.parseColor("#1E3A2F"))
        drawSealToCanvas(
            canvas = canvas,
            centerX = stampCenterX,
            centerY = stampCenterY,
            radius = stampRadius,
            inkColor = inkColorInt,
            stamp = stamp
        )

        // 5. Bottom Metadata Typography
        val contentStartY = height * 0.70f
        val maxTextWidth = width - 180f

        val baseTitlePaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.WHITE
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val titleLayout = fitResponsiveText(
            text = stamp.title,
            basePaint = baseTitlePaint,
            maxWidth = maxTextWidth,
            maxLines = 2,
            maxTextSize = 54f,
            minTextSize = 34f
        )
        var currentY = contentStartY
        for (line in titleLayout.lines) {
            canvas.drawText(line, width / 2f, currentY, titleLayout.paint)
            currentY += titleLayout.lineHeight
        }

        currentY += 8f

        val destText = (if (stamp.destination.isNotBlank()) stamp.destination else trip.destination)
            .uppercase()
            .replace(",", " •")
        val baseDestPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#FFAB91")
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        val destLayout = fitResponsiveText(
            text = destText,
            basePaint = baseDestPaint,
            maxWidth = maxTextWidth,
            maxLines = 2,
            maxTextSize = 34f,
            minTextSize = 22f
        )
        for (line in destLayout.lines) {
            canvas.drawText(line, width / 2f, currentY, destLayout.paint)
            currentY += destLayout.lineHeight
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
    // BRANDED FALLBACK BACKGROUND
    // ==========================================
    private fun drawBrandedFallbackBackground(
        canvas: Canvas,
        width: Float,
        height: Float,
        stamp: TravelStamp
    ) {
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
            textSize = radius * 0.17f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(motifEmoji, centerX, centerY - radius * 0.44f, iconPaint)

        // Stamp Title: fitted dynamically (1 to 3 lines) so multi-word and long titles fit without truncation or ellipsis
        val sealTitlePaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.04f
        }
        val titleText = stamp.title.uppercase()
        val titleLayout = fitResponsiveText(
            text = titleText,
            basePaint = sealTitlePaint,
            maxWidth = radius * 1.52f,
            maxLines = 3,
            maxTextSize = radius * 0.135f,
            minTextSize = radius * 0.080f,
            lineSpacingMultiplier = 1.15f
        )
        val titleStartY = when (titleLayout.lines.size) {
            1 -> centerY - radius * 0.22f
            2 -> centerY - radius * 0.26f
            else -> centerY - radius * 0.30f
        }
        var curY = titleStartY
        for (line in titleLayout.lines) {
            canvas.drawText(line, centerX, curY, titleLayout.paint)
            curY += titleLayout.lineHeight
        }

        // Destination / Location (Fitted dynamically across 1 to 2 lines)
        if (stamp.destination.isNotBlank()) {
            val sealDestPaint = Paint().apply {
                isAntiAlias = true
                color = inkColor
                alpha = 220
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.06f
            }
            val destText = stamp.destination.uppercase().replace(",", " •")
            val destLayout = fitResponsiveText(
                text = destText,
                basePaint = sealDestPaint,
                maxWidth = radius * 1.46f,
                maxLines = 2,
                maxTextSize = radius * 0.090f,
                minTextSize = radius * 0.060f,
                lineSpacingMultiplier = 1.15f
            )
            val destStartY = when (destLayout.lines.size) {
                1 -> centerY - radius * 0.04f
                else -> centerY - radius * 0.08f
            }
            var dY = destStartY
            for (line in destLayout.lines) {
                canvas.drawText(line, centerX, dY, destLayout.paint)
                dY += destLayout.lineHeight
            }
        }

        // Date row
        val sealDatePaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            textSize = radius * 0.088f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.06f
        }
        canvas.drawText("━◆ ${stamp.dateText.uppercase()} ◆━", centerX, centerY + radius * 0.15f, sealDatePaint)

        // Label: TRAVEL STAMP
        val stampLabelPaint = Paint().apply {
            isAntiAlias = true
            color = inkColor
            alpha = 210
            textSize = radius * 0.080f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.16f
        }
        canvas.drawText("TRAVEL STAMP", centerX, centerY + radius * 0.31f, stampLabelPaint)

        // Serial box e.g. #001
        val boxWidth = radius * 0.60f
        val boxHeight = radius * 0.16f
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
            textSize = radius * 0.105f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(stamp.stampCode, centerX, centerY + radius * 0.42f + boxHeight * 0.72f, codePaint)
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

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            openInput()?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            val outWidth = options.outWidth
            val outHeight = options.outHeight
            if (outWidth <= 0 || outHeight <= 0) return null

            var sampleSize = 1
            if (outHeight > reqHeight || outWidth > reqWidth) {
                val halfHeight = outHeight / 2
                val halfWidth = outWidth / 2
                while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val rawBitmap = openInput()?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

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

        val scaleX = targetWidth / srcWidth
        val scaleY = targetHeight / srcHeight
        val baseScale = max(scaleX, scaleY)
        val finalScale = baseScale * clampedZoom

        val scaledW = srcWidth * finalScale
        val scaledH = srcHeight * finalScale

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
    // RESPONSIVE TEXT WRAPPING & AUTO-FITTING
    // ==========================================

    /**
     * Dynamically finds the optimal font size between maxTextSize and minTextSize
     * so that multi-line text wraps nicely into maxLines without clipping or truncation.
     */
    fun fitResponsiveText(
        text: String,
        basePaint: Paint,
        maxWidth: Float,
        maxLines: Int,
        maxTextSize: Float,
        minTextSize: Float,
        lineSpacingMultiplier: Float = 1.25f
    ): FittedTextLayout {
        val workingPaint = Paint(basePaint)
        var currentSize = maxTextSize
        val step = 1.5f

        while (currentSize >= minTextSize) {
            workingPaint.textSize = currentSize
            val lines = wrapTextToLines(text, workingPaint, maxWidth)
            if (lines.size <= maxLines) {
                val lineHeight = currentSize * lineSpacingMultiplier
                val totalHeight = lines.size * lineHeight
                return FittedTextLayout(
                    lines = lines,
                    paint = workingPaint,
                    lineHeight = lineHeight,
                    totalHeight = totalHeight
                )
            }
            currentSize -= step
        }

        // At minimum text size, wrap and limit with graceful ellipsis fallback
        workingPaint.textSize = minTextSize
        val limitedLines = wrapAndLimitText(text, workingPaint, maxWidth, maxLines)
        val lineHeight = minTextSize * lineSpacingMultiplier
        val totalHeight = limitedLines.size * lineHeight

        return FittedTextLayout(
            lines = limitedLines,
            paint = workingPaint,
            lineHeight = lineHeight,
            totalHeight = totalHeight
        )
    }

    /**
     * Dynamically fits a single line of text by scaling down text size within maxWidth.
     */
    fun fitSingleLineText(
        text: String,
        paint: Paint,
        maxWidth: Float,
        maxTextSize: Float,
        minTextSize: Float
    ): String {
        var currentSize = maxTextSize
        val step = 1.0f
        while (currentSize >= minTextSize) {
            paint.textSize = currentSize
            if (paint.measureText(text) <= maxWidth) {
                return text
            }
            currentSize -= step
        }
        paint.textSize = minTextSize
        return truncateTextSingleLine(text, paint, maxWidth)
    }

    fun wrapTextToLines(text: String, paint: Paint, maxWidth: Float): List<String> {
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
                    currentLine = ""
                }
                // If single word itself exceeds maxWidth on its own
                if (paint.measureText(word) > maxWidth) {
                    var remaining = word
                    while (remaining.isNotEmpty()) {
                        var takeChars = remaining.length
                        while (takeChars > 1 && paint.measureText(remaining.substring(0, takeChars)) > maxWidth) {
                            takeChars--
                        }
                        lines.add(remaining.substring(0, takeChars))
                        remaining = remaining.substring(takeChars)
                    }
                } else {
                    currentLine = word
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
    }

    fun wrapAndLimitText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        val allLines = wrapTextToLines(text, paint, maxWidth)
        if (allLines.isEmpty()) return emptyList()
        if (allLines.size <= maxLines) return allLines

        val result = allLines.take(maxLines).toMutableList()
        val lastIdx = result.lastIndex
        result[lastIdx] = truncateTextSingleLine(result[lastIdx], paint, maxWidth)
        return result
    }

    fun truncateTextSingleLine(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return if (truncated.isNotEmpty()) "$truncated…" else text.take(1)
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
