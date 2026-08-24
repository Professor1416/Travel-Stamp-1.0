package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.poster.PhotoStampLayout
import com.example.ui.theme.ForestPine
import com.example.ui.theme.OchreGold
import com.example.ui.theme.SandCanvasLight
import com.example.ui.theme.Terracotta

/**
 * Minimal collectible Travel Stamp badge for Photo + Stamp posters.
 * Uses the official symbol-only logo (R.drawable.travel_stamp_symbol),
 * "TRAVEL STAMP" brand label, and the collectible stamp sequence number (#XXX).
 *
 * Dedicated to Photo + Stamp editions only: does not display repeated journey
 * destination/location/date information, leaving that to the clean footer below the photo.
 */
@Composable
fun PhotoStampBadgeView(
    stampCode: String,
    stampNumber: Long,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    inkColorHex: String = "#1E3A2F"
) {
    val sequenceText = PhotoStampLayout.formatStampSequence(stampCode, stampNumber)
    val inkColor = parseInkColor(inkColorHex, ForestPine)

    Surface(
        shape = CircleShape,
        color = SandCanvasLight.copy(alpha = 0.96f),
        border = BorderStroke(1.2.dp, OchreGold.copy(alpha = 0.75f)),
        modifier = modifier
            .size(size)
            .testTag("photo_stamp_badge_view"),
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = size * PhotoStampLayout.BADGE_PADDING_TOP_RATIO,
                    bottom = size * 0.06f
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Symbol-only logo artwork (Travel Stamp Emblem)
            Image(
                painter = painterResource(id = R.drawable.travel_stamp_symbol),
                contentDescription = "Travel Stamp Symbol Emblem",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size * PhotoStampLayout.BADGE_LOGO_DIAMETER_RATIO)
                    .testTag("photo_stamp_badge_logo")
            )

            Spacer(modifier = Modifier.height(size * 0.015f))

            // 2. TRAVEL STAMP Brand Label
            Text(
                text = "TRAVEL STAMP",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * PhotoStampLayout.BADGE_BRAND_TEXT_SIZE_RATIO).sp,
                letterSpacing = 1.1.sp,
                color = inkColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.testTag("photo_stamp_badge_brand")
            )

            Spacer(modifier = Modifier.height(size * 0.008f))

            // 3. Collectible Sequence Number (#XXX)
            Text(
                text = sequenceText,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * PhotoStampLayout.BADGE_SERIAL_TEXT_SIZE_RATIO).sp,
                letterSpacing = 0.8.sp,
                color = Terracotta,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.testTag("photo_stamp_badge_serial")
            )
        }
    }
}
