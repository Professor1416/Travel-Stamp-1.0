package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TravelStamp
import com.example.ui.theme.SandCanvasLight
import kotlin.math.cos
import kotlin.math.sin

/**
 * Parses hex color string safely or returns fallback.
 */
fun parseInkColor(hex: String, fallback: Color = Color(0xFF1E3A2F)): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        val colorInt = cleanHex.toLong(16)
        if (cleanHex.length == 6) {
            Color(colorInt or 0x00000000FF000000L)
        } else if (cleanHex.length == 8) {
            Color(colorInt)
        } else {
            fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

/**
 * High-fidelity, authentic Travel Stamp rendering.
 */
@Composable
fun TravelStampView(
    stamp: TravelStamp,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    rotation: Float = -2f
) {
    val inkColor = parseInkColor(stamp.inkColorHex, MaterialTheme.colorScheme.primary)

    Box(
        modifier = modifier
            .size(size)
            .rotate(rotation),
        contentAlignment = Alignment.Center
    ) {
        // Stamp Canvas Drawing (Concentric rings, serrated edges, mountain emblem)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawStampBorder(inkColor = inkColor, style = stamp.stampStyle)
        }

        // Inner Content Layout
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Visual travel illustration icon
            Text(
                text = "🏔️",
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Destination / Title
            Text(
                text = stamp.title.uppercase(),
                color = inkColor,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            if (stamp.destination.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stamp.destination.uppercase().replace(",", " •"),
                    color = inkColor.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Date Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "━◆ ",
                    color = inkColor.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
                Text(
                    text = stamp.dateText.uppercase(),
                    color = inkColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = " ◆━",
                    color = inkColor.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Stamp Badge Title & Sequential ID
            Text(
                text = "TRAVEL STAMP",
                color = inkColor.copy(alpha = 0.85f),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Stamp Serial / Code (e.g. #001)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, inkColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stamp.stampCode,
                    color = inkColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

private fun DrawScope.drawStampBorder(inkColor: Color, style: String) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = (size.minDimension / 2f) - 6f
    val innerRadius = outerRadius - 14f

    // Outer scalloped / serrated ring
    val teeth = 48
    val teethPath = Path()
    for (i in 0 until teeth) {
        val angle = (i.toFloat() / teeth.toFloat()) * (2f * Math.PI.toFloat())
        val nextAngle = ((i + 1).toFloat() / teeth.toFloat()) * (2f * Math.PI.toFloat())
        val midAngle = (angle + nextAngle) / 2f

        val rOuter = outerRadius
        val rInner = outerRadius - 3.5f

        val x1 = center.x + rInner * cos(angle)
        val y1 = center.y + rInner * sin(angle)
        val xMid = center.x + rOuter * cos(midAngle)
        val yMid = center.y + rOuter * sin(midAngle)

        if (i == 0) teethPath.moveTo(x1, y1)
        teethPath.lineTo(xMid, yMid)
    }
    teethPath.close()

    drawPath(
        path = teethPath,
        color = inkColor.copy(alpha = 0.35f),
        style = Stroke(width = 2.5f)
    )

    // Outer solid ring
    drawCircle(
        color = inkColor,
        radius = outerRadius - 4f,
        center = center,
        style = Stroke(width = 3.5f)
    )

    // Inner dashed ring
    drawCircle(
        color = inkColor.copy(alpha = 0.7f),
        radius = innerRadius,
        center = center,
        style = Stroke(
            width = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )
    )

    // Innermost fine hairline ring
    drawCircle(
        color = inkColor.copy(alpha = 0.4f),
        radius = innerRadius - 6f,
        center = center,
        style = Stroke(width = 0.8f)
    )
}

/**
 * Compact Stamp Badge for Passport collection grids.
 */
@Composable
fun CompactStampBadge(
    stamp: TravelStamp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val inkColor = parseInkColor(stamp.inkColorHex, MaterialTheme.colorScheme.primary)

    Box(
        modifier = modifier
            .size(116.dp)
            .clip(CircleShape)
            .background(SandCanvasLight.copy(alpha = 0.75f))
            .border(2.dp, inkColor.copy(alpha = 0.7f), CircleShape)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🏔️",
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stamp.title,
                color = inkColor,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 12.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stamp.dateText,
                color = inkColor.copy(alpha = 0.8f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
