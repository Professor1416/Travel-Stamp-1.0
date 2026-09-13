package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.TravelStamp
import com.example.ui.theme.ForestPine
import com.example.ui.theme.OchreGold
import com.example.ui.theme.SandCanvasLight
import com.example.ui.theme.Terracotta

const val PASSPORT_PAGE_SIZE = 4

/**
 * Transforms sorted travel stamps into pages of size [PASSPORT_PAGE_SIZE].
 * Pure, side-effect free, sorting ascending by stampNumber.
 */
fun buildPassportPages(
    stamps: List<TravelStamp>
): List<List<TravelStamp>> {
    return stamps
        .sortedBy { it.stampNumber }
        .chunked(PASSPORT_PAGE_SIZE)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PassportBook(
    stamps: List<TravelStamp>,
    targetStampId: Long?,
    onStampClick: (TravelStamp) -> Unit,
    onCreateJourney: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedStamps = remember(stamps) { stamps.sortedBy { it.stampNumber } }
    val pages = remember(sortedStamps) { buildPassportPages(sortedStamps) }

    if (pages.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyPassportPage(
                onCreateJourney = onCreateJourney,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            )
        }
        return;
    }

    // Determine initial target page index safely
    val initialPage = remember(targetStampId, pages) {
        if (targetStampId != null) {
            val targetIndex = sortedStamps.indexOfFirst { it.tripId == targetStampId || it.id == targetStampId }
            if (targetIndex >= 0) {
                targetIndex / PASSPORT_PAGE_SIZE
            } else 0
        } else 0
    }

    val pagerState = rememberPagerState(
        initialPage = if (initialPage in pages.indices) initialPage else 0
    ) { pages.size }

    // Direct scroll to target page upon stamp arrival
    LaunchedEffect(targetStampId, pages) {
        if (targetStampId != null) {
            val targetIndex = sortedStamps.indexOfFirst { it.tripId == targetStampId || it.id == targetStampId }
            if (targetIndex >= 0) {
                val targetPage = targetIndex / PASSPORT_PAGE_SIZE
                if (targetPage in pages.indices && pagerState.currentPage != targetPage) {
                    pagerState.scrollToPage(targetPage)
                }
            }
        }
    }

    // Safety constraint: clamp page position if dataset shrinks
    LaunchedEffect(pages.size) {
        if (pages.isNotEmpty() && pagerState.currentPage >= pages.size) {
            val clampedPage = pages.size - 1
            pagerState.scrollToPage(clampedPage)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .testTag("passport_book_pager"),
            beyondViewportPageCount = 1
        ) { pageIndex ->
            val pageStamps = pages[pageIndex]
            
            // Premium layout page transition effects
            val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
            val scale = (1f - (pageOffset.coerceIn(-1f, 1f) * 0.03f).let { if (it < 0) -it else it }).coerceIn(0.95f, 1f)
            val alpha = (1f - (pageOffset.coerceIn(-1f, 1f) * 0.35f).let { if (it < 0) -it else it }).coerceIn(0.65f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                PassportPage(
                    pageStamps = pageStamps,
                    pageNumber = pageIndex + 1,
                    totalPages = pages.size,
                    onStampClick = onStampClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .testTag("passport_page_$pageIndex")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Accessible page progress helper underneath the pager
        PassportPageIndicator(
            currentPage = pagerState.currentPage + 1,
            totalPages = pages.size,
            modifier = Modifier.testTag("passport_pager_indicator")
        )
    }
}

@Composable
fun PassportPage(
    pageStamps: List<TravelStamp>,
    pageNumber: Int,
    totalPages: Int,
    onStampClick: (TravelStamp) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .background(SandCanvasLight, RoundedCornerShape(20.dp))
            .border(BorderStroke(1.2.dp, OchreGold.copy(alpha = 0.35f)), RoundedCornerShape(20.dp))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        
        // Calculate responsive stamp scale safely
        val cellSpacing = 12.dp
        val horizontalMargin = 8.dp
        val calculatedWidth = (availableWidth - (horizontalMargin * 2) - cellSpacing) / 2
        val stampSize = calculatedWidth.coerceIn(90.dp, 135.dp)

        // Faint central watermark behind the stamps
        Image(
            painter = painterResource(id = R.drawable.travel_stamp_master),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer { alpha = 0.04f }
                .align(Alignment.Center)
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant microcopy page header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "TRAVEL PASSPORT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp
                    ),
                    color = ForestPine.copy(alpha = 0.6f)
                )
                Text(
                    text = "OFFICIAL RECORD",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 8.sp,
                        letterSpacing = 1.2.sp
                    ),
                    color = OchreGold.copy(alpha = 0.7f)
                )
            }

            // Balanced 2x2 stamp grid columns and rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalMargin),
                verticalArrangement = Arrangement.spacedBy(cellSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(cellSpacing, Alignment.CenterHorizontally)
                ) {
                    PassportStampSlot(
                        stamp = pageStamps.getOrNull(0),
                        rotation = -2.0f,
                        stampSize = stampSize,
                        onStampClick = onStampClick,
                        modifier = Modifier.weight(1f)
                    )
                    PassportStampSlot(
                        stamp = pageStamps.getOrNull(1),
                        rotation = 1.5f,
                        stampSize = stampSize,
                        onStampClick = onStampClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(cellSpacing, Alignment.CenterHorizontally)
                ) {
                    PassportStampSlot(
                        stamp = pageStamps.getOrNull(2),
                        rotation = 1.0f,
                        stampSize = stampSize,
                        onStampClick = onStampClick,
                        modifier = Modifier.weight(1f)
                    )
                    PassportStampSlot(
                        stamp = pageStamps.getOrNull(3),
                        rotation = -1.5f,
                        stampSize = stampSize,
                        onStampClick = onStampClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Subtle page number text
            Text(
                text = "PAGE %02d OF %02d".format(pageNumber, totalPages),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = ForestPine.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
fun PassportStampSlot(
    stamp: TravelStamp?,
    rotation: Float,
    stampSize: Dp,
    onStampClick: (TravelStamp) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics(mergeDescendants = true) {
                if (stamp != null) {
                    contentDescription = "Official Stamp #${stamp.stampNumber} for ${stamp.destination}. Dated ${stamp.dateText}. Double tap to open journey logs."
                } else {
                    contentDescription = "Empty passport stamp slot"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (stamp != null) {
            Box(
                modifier = Modifier
                    .size(stampSize)
                    .clickable(
                        onClick = { onStampClick(stamp) },
                        role = Role.Button
                    )
                    .testTag("passport_stamp_slot_${stamp.stampCode}"),
                contentAlignment = Alignment.Center
            ) {
                TravelStampView(
                    stamp = stamp,
                    size = stampSize,
                    rotation = rotation
                )
            }
        } else {
            // Unused slot: subtle placement boundary guide, naturally incomplete
            Box(
                modifier = Modifier
                    .size(stampSize * 0.82f)
                    .border(
                        width = 1.dp,
                        color = OchreGold.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(percent = 50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "★",
                    color = OchreGold.copy(alpha = 0.15f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun EmptyPassportPage(
    onCreateJourney: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(SandCanvasLight, RoundedCornerShape(20.dp))
            .border(BorderStroke(1.2.dp, OchreGold.copy(alpha = 0.35f)), RoundedCornerShape(20.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.travel_stamp_master),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(110.dp)
                    .graphicsLayer { alpha = 0.12f }
            )

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Your passport is waiting.",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                ),
                color = ForestPine,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("empty_passport_title")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Complete your first journey to earn your first Travel Stamp.",
                style = MaterialTheme.typography.bodyMedium,
                color = ForestPine.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("empty_passport_body")
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onCreateJourney,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Terracotta,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier.testTag("empty_passport_cta")
            ) {
                Text(
                    text = "Plan Journey",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun PassportPageIndicator(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = "PAGE %d OF %d".format(currentPage, totalPages),
        style = MaterialTheme.typography.labelMedium.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        ),
        color = ForestPine.copy(alpha = 0.6f),
        modifier = modifier
    )
}
