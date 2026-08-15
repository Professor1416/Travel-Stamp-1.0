package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.TravelStamp
import com.example.ui.theme.ForestPine
import com.example.ui.theme.OchreGold
import com.example.ui.theme.SandCanvasLight
import com.example.ui.theme.Terracotta
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
 * Reusable Travel Stamp Card Composable for Detail screen, Image export & sharing.
 * Elegant passport memorandum styling with parchment background and official seal.
 */
@Composable
fun TravelStampCard(
    stamp: TravelStamp,
    modifier: Modifier = Modifier,
    photoUri: String? = null,
    rotation: Float = -1.5f,
    elevation: Dp = 4.dp
) {
    val context = LocalContext.current
    val inkColor = parseInkColor(stamp.inkColorHex, ForestPine)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("travel_stamp_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = SandCanvasLight
        ),
        border = BorderStroke(1.5.dp, OchreGold.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Passport Memorandum Header
            Text(
                text = "PASSPORT MEMORANDUM",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = ForestPine.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(1.dp)
                    .background(OchreGold.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Optional Inset Snapshot Photo (tastefully framed)
            if (!photoUri.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.2.dp, OchreGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.5f))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Expedition Snapshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Large Authentic Stamp Seal
            TravelStampView(
                stamp = stamp,
                size = 230.dp,
                rotation = rotation,
                modifier = Modifier.testTag("travel_stamp_view")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Trip Title & Destination
            Text(
                text = stamp.title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = inkColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (stamp.destination.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stamp.destination.replace(",", " •"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Terracotta,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Expedition Stats Row
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📅 ${stamp.dateText}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ForestPine
                )
                Text(
                    text = "•",
                    fontSize = 11.sp,
                    color = OchreGold
                )
                Text(
                    text = "👥 ${stamp.peopleCount} Explorers",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ForestPine
                )
                Text(
                    text = "•",
                    fontSize = 11.sp,
                    color = OchreGold
                )
                Text(
                    text = "✨ ${stamp.momentsCount} Moments",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ForestPine
                )
            }

            // Reflection Note Quotation
            if (!stamp.reflectionNote.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.75f),
                    border = BorderStroke(0.8.dp, OchreGold.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "“${stamp.reflectionNote.trim()}”",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = ForestPine,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "OFFICIALLY LOGGED • CERTIFIED JOURNEY",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = ForestPine.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * High-fidelity, authentic circular/scalloped Travel Stamp rendering.
 */
@Composable
fun TravelStampView(
    stamp: TravelStamp,
    modifier: Modifier = Modifier,
    size: Dp = 230.dp,
    rotation: Float = -1.5f
) {
    val inkColor = parseInkColor(stamp.inkColorHex, ForestPine)

    Box(
        modifier = modifier
            .size(size)
            .rotate(rotation),
        contentAlignment = Alignment.Center
    ) {
        // Stamp Canvas Drawing (Concentric rings, scalloped serrated edges)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawStampBorder(inkColor = inkColor, style = stamp.stampStyle)
        }

        // Inner Content Layout
        Column(
            modifier = Modifier
                .padding(22.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Visual travel emblem
            val emblemEmoji = when (stamp.stampStyle) {
                "COMPASS" -> "🧭"
                "PINE" -> "🌲"
                "EXPEDITION" -> "⚜️"
                else -> "🏔️"
            }
            Text(
                text = emblemEmoji,
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
                fontSize = 15.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (stamp.destination.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stamp.destination.uppercase().replace(",", " •"),
                    color = inkColor.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Date Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "━◆ ",
                    color = inkColor.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
                Text(
                    text = stamp.dateText.uppercase(),
                    color = inkColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = " ◆━",
                    color = inkColor.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Stamp Badge Title & Sequential ID
            Text(
                text = "TRAVEL STAMP",
                color = inkColor.copy(alpha = 0.85f),
                fontSize = 8.5.sp,
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
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text(
                    text = stamp.stampCode,
                    color = inkColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

private fun DrawScope.drawStampBorder(inkColor: Color, style: String) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = (size.minDimension / 2f) - 4f
    val innerRadius = outerRadius - 12f

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
        radius = outerRadius - 3.5f,
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
        radius = innerRadius - 5f,
        center = center,
        style = Stroke(width = 0.8f)
    )
}

/**
 * Reusable Collection Stamp Item for Passport Collection view.
 * Compact, lightweight, responsive item.
 */
@Composable
fun CollectionStampItem(
    stamp: TravelStamp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val inkColor = parseInkColor(stamp.inkColorHex, ForestPine)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("collection_stamp_item_${stamp.stampCode}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = SandCanvasLight
        ),
        border = BorderStroke(1.dp, OchreGold.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact Circular Stamp Badge
            CompactStampBadge(
                stamp = stamp,
                modifier = Modifier.size(86.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stamp.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = ForestPine,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Serial badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(inkColor.copy(alpha = 0.12f))
                            .border(1.dp, inkColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stamp.stampCode,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = inkColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stamp.destination,
                    style = MaterialTheme.typography.bodySmall,
                    color = Terracotta,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "📅 ${stamp.dateText}   •   ✨ ${stamp.momentsCount} Moments",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!stamp.reflectionNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "“${stamp.reflectionNote.trim()}”",
                        fontSize = 10.5.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Compact Stamp Badge for Passport collection grids/lists.
 */
@Composable
fun CompactStampBadge(
    stamp: TravelStamp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val inkColor = parseInkColor(stamp.inkColorHex, ForestPine)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.65f))
            .border(2.dp, inkColor.copy(alpha = 0.7f), CircleShape)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val emblemEmoji = when (stamp.stampStyle) {
                "COMPASS" -> "🧭"
                "PINE" -> "🌲"
                "EXPEDITION" -> "⚜️"
                else -> "🏔️"
            }
            Text(
                text = emblemEmoji,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = stamp.title,
                color = inkColor,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = stamp.stampCode,
                color = inkColor.copy(alpha = 0.9f),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }
    }
}
