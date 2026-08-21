package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TravelStamp
import com.example.ui.theme.ForestPine
import com.example.ui.theme.OchreGold
import com.example.ui.theme.SandCanvasLight
import com.example.ui.theme.Terracotta
import kotlin.math.cos
import kotlin.math.sin

/**
 * Passport Stamp Grid Card designed specifically for grid-based collection layouts.
 * Features authentic tactile passport booklet aesthetic with parchment styling,
 * hand-stamped seal with subtle natural rotation, serial badge, and certified metadata.
 */
@Composable
fun PassportStampGridCard(
    stamp: TravelStamp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inkColor = remember(stamp.inkColorHex) {
        parseInkColor(stamp.inkColorHex, ForestPine)
    }

    // Subtle deterministic rotation (-2.0 to +2.0 deg) for authentic hand-stamped passport feel
    val naturalRotation = remember(stamp.stampNumber) {
        ((stamp.stampNumber % 5) - 2) * 1.0f
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("passport_stamp_grid_card_${stamp.stampCode}")
            .semantics {
                contentDescription = "Official Travel Stamp ${stamp.stampCode} for ${stamp.title}, ${stamp.destination}, dated ${stamp.dateText}"
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = SandCanvasLight
        ),
        border = BorderStroke(1.2.dp, OchreGold.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Card Header: Stamp Serial & Expedition Marker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Serial Code Badge (#001)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(inkColor.copy(alpha = 0.12f))
                        .border(1.dp, inkColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stamp.stampCode,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = inkColor,
                        letterSpacing = 0.5.sp
                    )
                }

                // Official Seal Status
                Text(
                    text = "CERTIFIED",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestPine.copy(alpha = 0.6f),
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Central Passport Stamp Seal (Circular with scalloped rings & motif)
            MiniPassportStampSeal(
                stamp = stamp,
                inkColor = inkColor,
                rotation = naturalRotation,
                size = 112.dp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Divider: Subtle Perforated Line
            PerforatedDivider(
                color = OchreGold.copy(alpha = 0.35f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Fort / Expedition Title
            Text(
                text = stamp.title,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = ForestPine,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 5. Destination & Location
            if (stamp.destination.isNotBlank()) {
                Text(
                    text = stamp.destination,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Terracotta,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(3.dp))
            }

            // 6. Expedition Date
            Text(
                text = "📅 ${stamp.dateText}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 7. Bottom Badges: Moments & Companions
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.7f))
                    .border(0.6.dp, OchreGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "✨ ${stamp.momentsCount} ${if (stamp.momentsCount == 1) "Moment" else "Moments"}",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestPine
                )
            }
        }
    }
}

/**
 * Stylized circular passport stamp seal rendered specifically for grid cards.
 */
@Composable
fun MiniPassportStampSeal(
    stamp: TravelStamp,
    inkColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    rotation: Float = 0f
) {
    Box(
        modifier = modifier
            .size(size)
            .rotate(rotation),
        contentAlignment = Alignment.Center
    ) {
        // Stamp Canvas Drawing (outer scalloped ring + inner dashed ring)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawMiniStampBorder(inkColor = inkColor)
        }

        // Inner Content of Seal
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxSize(),
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
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(1.dp))

            Text(
                text = stamp.title.uppercase(),
                color = inkColor,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                letterSpacing = 0.3.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(1.dp))

            Text(
                text = "TRAVEL STAMP",
                color = inkColor.copy(alpha = 0.8f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun DrawScope.drawMiniStampBorder(inkColor: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = (size.minDimension / 2f) - 2.5f
    val innerRadius = outerRadius - 7f

    // 1. Scalloped serrated perimeter
    val teeth = 36
    val teethPath = Path()
    for (i in 0 until teeth) {
        val angle = (i.toFloat() / teeth.toFloat()) * (2f * Math.PI.toFloat())
        val nextAngle = ((i + 1).toFloat() / teeth.toFloat()) * (2f * Math.PI.toFloat())
        val midAngle = (angle + nextAngle) / 2f

        val rOuter = outerRadius
        val rInner = outerRadius - 2.2f

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
        style = Stroke(width = 1.6f)
    )

    // 2. Solid inner ring
    drawCircle(
        color = inkColor,
        radius = outerRadius - 2.5f,
        center = center,
        style = Stroke(width = 2.0f)
    )

    // 3. Dashed certification ring
    drawCircle(
        color = inkColor.copy(alpha = 0.65f),
        radius = innerRadius,
        center = center,
        style = Stroke(
            width = 1.0f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
        )
    )
}

/**
 * Grid Row layout helper for embedding 2-column stamp grids smoothly inside a LazyColumn.
 */
@Composable
fun PassportStampGridRow(
    stamp1: TravelStamp,
    stamp2: TravelStamp?,
    onStampClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("passport_stamp_grid_row"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        PassportStampGridCard(
            stamp = stamp1,
            onClick = { onStampClick(stamp1.tripId) },
            modifier = Modifier.weight(1f)
        )

        if (stamp2 != null) {
            PassportStampGridCard(
                stamp = stamp2,
                onClick = { onStampClick(stamp2.tripId) },
                modifier = Modifier.weight(1f)
            )
        } else {
            // Invisible placeholder to keep the left column size stable
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Standalone LazyVerticalGrid for displaying collected passport stamps.
 */
@Composable
fun PassportStampGrid(
    stamps: List<TravelStamp>,
    onStampClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.screenHorizontal),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.md),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Spacing.md)
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .testTag("passport_stamp_grid_container"),
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement
    ) {
        items(stamps, key = { it.id }) { stamp ->
            PassportStampGridCard(
                stamp = stamp,
                onClick = { onStampClick(stamp.tripId) }
            )
        }
    }
}
