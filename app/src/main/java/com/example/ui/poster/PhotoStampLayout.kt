package com.example.ui.poster

import com.example.data.model.TravelStamp
import com.example.data.model.Trip
import kotlin.math.max
import kotlin.math.min

/**
 * Single Canonical Geometry and Layout Model for the Photo + Stamp edition.
 * Shared directly by Compose Live Preview and Bitmap Canvas Renderers to ensure
 * 100% mathematical WYSIWYG parity across all devices, densities, and aspect ratios.
 */
object PhotoStampLayout {

    // Base proportional constants
    const val BASE_STAMP_WIDTH_RATIO = 0.44f // Stamp diameter is 44% of poster width (radius = 22%)
    const val SEAL_INNER_SCALE = 0.92f // Inner seal diameter relative to backdrop circle
    const val TOP_HEADER_Y_RATIO = 0.0625f // Top header text centered at 6.25% height (120px / 1920px)

    // Internal Photo Stamp Collectible Badge Proportions (relative to canonical stamp diameter)
    const val BADGE_PADDING_TOP_RATIO = 0.08f // Top padding ratio within badge circle
    const val BADGE_LOGO_DIAMETER_RATIO = 0.52f // Symbol-only logo width/height relative to badge diameter
    const val BADGE_BRAND_CENTER_Y_RATIO = 0.72f // "TRAVEL STAMP" vertical center relative to badge diameter
    const val BADGE_BRAND_TEXT_SIZE_RATIO = 0.070f // "TRAVEL STAMP" font size relative to badge diameter
    const val BADGE_SERIAL_CENTER_Y_RATIO = 0.85f // "#XXX" vertical center relative to badge diameter
    const val BADGE_SERIAL_TEXT_SIZE_RATIO = 0.090f // "#XXX" font size relative to badge diameter

    // Margin constants in normalized coordinates
    const val HORIZONTAL_MARGIN_NORM = 0.035f // ~38px on 1080px width
    const val TOP_MARGIN_NORM = 0.075f // Safe clearance below header
    const val SAFE_CLEARANCE_NORM = 0.025f // Strictly safe clearance above destination/title footer

    /**
     * Proportional vertical start position of the bottom metadata footer
     * for each supported stamp format.
     */
    fun getFooterStartYRatio(format: StampEditionFormat): Float {
        return when (format) {
            StampEditionFormat.SQUARE -> 0.73f
            StampEditionFormat.PORTRAIT -> 0.76f
            StampEditionFormat.STORY -> 0.79f
        }
    }

    /**
     * Radius of the outer stamp backdrop in pixels for a given poster width and discrete stamp scale.
     */
    fun getStampRadiusPx(widthPx: Float, stampSize: StampSize): Float {
        val baseRadius = widthPx * (BASE_STAMP_WIDTH_RATIO / 2f)
        return baseRadius * stampSize.scale
    }

    /**
     * Diameter of the outer stamp backdrop in pixels.
     */
    fun getStampDiameterPx(widthPx: Float, stampSize: StampSize): Float {
        return getStampRadiusPx(widthPx, stampSize) * 2f
    }

    /**
     * Radius of the inner seal ink in pixels.
     */
    fun getSealRadiusPx(widthPx: Float, stampSize: StampSize): Float {
        return getStampRadiusPx(widthPx, stampSize) * SEAL_INNER_SCALE
    }

    /**
     * Normalized radius of the stamp along X and Y axes relative to poster width and height.
     */
    data class NormalizedStampRadius(val radiusNormX: Float, val radiusNormY: Float)

    fun getNormalizedStampRadius(format: StampEditionFormat, stampSize: StampSize): NormalizedStampRadius {
        val radiusNormX = (BASE_STAMP_WIDTH_RATIO / 2f) * stampSize.scale
        val radiusNormY = radiusNormX * format.aspectRatio
        return NormalizedStampRadius(radiusNormX, radiusNormY)
    }

    /**
     * Boundary constraints for the normalized center of the stamp.
     * Guarantees the stamp never clips edges, never overlaps top header,
     * and never overlaps bottom metadata title and badges.
     */
    data class StampNormalizedBounds(
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float
    )

    fun getStampNormalizedBounds(
        format: StampEditionFormat,
        stampSize: StampSize
    ): StampNormalizedBounds {
        val (radX, radY) = getNormalizedStampRadius(format, stampSize)
        val footerStartY = getFooterStartYRatio(format)

        val minX = radX + HORIZONTAL_MARGIN_NORM
        val maxX = 1.0f - radX - HORIZONTAL_MARGIN_NORM
        val minY = radY + TOP_MARGIN_NORM
        val maxY = footerStartY - radY - SAFE_CLEARANCE_NORM

        val safeMinX = minX.coerceAtMost(0.49f)
        val safeMaxX = max(safeMinX, maxX)
        val safeMinY = minY.coerceAtMost(0.40f)
        val safeMaxY = max(safeMinY, maxY)

        return StampNormalizedBounds(
            minX = safeMinX,
            maxX = safeMaxX,
            minY = safeMinY,
            maxY = safeMaxY
        )
    }

    /**
     * Clamps user drag position (normalized 0..1) to strictly safe bounds
     * respecting the stamp radius, format aspect ratio, and metadata zone.
     */
    fun clampStampPosition(
        normX: Float,
        normY: Float,
        format: StampEditionFormat,
        stampSize: StampSize
    ): Pair<Float, Float> {
        val bounds = getStampNormalizedBounds(format, stampSize)
        val clampedX = normX.coerceIn(bounds.minX, bounds.maxX)
        val clampedY = normY.coerceIn(bounds.minY, bounds.maxY)
        return Pair(clampedX, clampedY)
    }

    /**
     * Mathematical model for positioning and cropping user photos with Pan and Zoom.
     * Evaluated identically in Bitmap Canvas and Compose Live Preview.
     */
    data class PhotoTransformGeometry(
        val targetWidth: Float,
        val targetHeight: Float,
        val scaledWidth: Float,
        val scaledHeight: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val clampedPanX: Float,
        val clampedPanY: Float,
        val finalScale: Float
    )

    fun calculatePhotoTransform(
        srcWidth: Float,
        srcHeight: Float,
        targetWidth: Float,
        targetHeight: Float,
        panX: Float,
        panY: Float,
        zoom: Float
    ): PhotoTransformGeometry {
        val clampedZoom = zoom.coerceIn(1.0f, 3.5f)
        val scaleX = targetWidth / max(1f, srcWidth)
        val scaleY = targetHeight / max(1f, srcHeight)
        val baseScale = max(scaleX, scaleY)
        val finalScale = baseScale * clampedZoom

        val scaledW = srcWidth * finalScale
        val scaledH = srcHeight * finalScale

        val maxPanX = max(0f, (scaledW - targetWidth) / 2f)
        val maxPanY = max(0f, (scaledH - targetHeight) / 2f)

        val clampedPanX = (panX * targetWidth).coerceIn(-maxPanX, maxPanX)
        val clampedPanY = (panY * targetHeight).coerceIn(-maxPanY, maxPanY)

        val left = (targetWidth - scaledW) / 2f + clampedPanX
        val top = (targetHeight - scaledH) / 2f + clampedPanY

        return PhotoTransformGeometry(
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            scaledWidth = scaledW,
            scaledHeight = scaledH,
            left = left,
            top = top,
            right = left + scaledW,
            bottom = top + scaledH,
            clampedPanX = clampedPanX,
            clampedPanY = clampedPanY,
            finalScale = finalScale
        )
    }

    /**
     * Formats the collectible stamp sequence number with standard # prefix
     * matching the app's established numbering conventions.
     */
    fun formatStampSequence(stampCode: String, stampNumber: Long): String {
        val trimmed = stampCode.trim()
        return when {
            trimmed.startsWith("#") -> trimmed
            trimmed.startsWith("TS-", ignoreCase = true) -> "#" + trimmed.substring(3)
            trimmed.isNotBlank() -> "#$trimmed"
            else -> "#%03d".format(stampNumber)
        }
    }
}
