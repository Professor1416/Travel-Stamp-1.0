package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Trip
import com.example.data.util.DateUtils
import com.example.ui.components.CompactStampBadge
import com.example.ui.components.EmptyStateView
import com.example.ui.components.MoreStampsIndicator
import com.example.ui.components.SectionHeader
import com.example.ui.components.Spacing
import com.example.ui.components.TravelPrimaryButton
import com.example.ui.components.TripCardTicket
import com.example.ui.theme.ForestPine
import com.example.ui.theme.OchreGold
import com.example.ui.theme.Terracotta
import com.example.ui.viewmodel.TravelViewModel

private const val HOME_COLLECTION_PREVIEW_LIMIT = 4
private const val RECENT_COMPLETED_PREVIEW_LIMIT = 3

@Composable
fun HomeScreen(
    viewModel: TravelViewModel,
    onCreateTripClick: () -> Unit,
    onTripClick: (Long) -> Unit,
    onCollectionClick: () -> Unit,
    onStampClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTrips by viewModel.activeTrips.collectAsStateWithLifecycle()
    val completedTrips by viewModel.completedTrips.collectAsStateWithLifecycle()
    val stamps by viewModel.stamps.collectAsStateWithLifecycle()
    val completedTripsCount by viewModel.completedTripsCount.collectAsStateWithLifecycle()
    val totalMomentsCount by viewModel.totalMomentsCount.collectAsStateWithLifecycle()

    // Repository-level authoritative sorting
    val sortedActiveTrips = activeTrips
    val sortedCompletedTrips = completedTrips

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            // 1. App Identity & Tagline
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md, bottom = Spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Vintage Mountain Stamp Emblem
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(ForestPine, Color(0xFF13221B))
                                )
                            )
                            .border(2.dp, OchreGold.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🏔️",
                            fontSize = 32.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    Text(
                        text = "TRAVEL STAMP",
                        style = MaterialTheme.typography.headlineLarge,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        text = "“Your journeys. Your memories. Your collection.”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Spacing.lg)
                    )
                }
            }

            // 2. Primary Action CTA: CREATE TRIP
            item {
                TravelPrimaryButton(
                    text = "CREATE TRIP",
                    icon = Icons.Default.Add,
                    onClick = onCreateTripClick,
                    testTag = "create_trip_button"
                )
            }

            // 3. Collection Summary & Preview (Compact, Scalable, Fixed Height)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collection_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.cardPadding)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🛂", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = "MY PASSPORT & COLLECTION",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            val stampCountText = when (stamps.size) {
                                1 -> "1 Stamp"
                                else -> "${stamps.size} Stamps"
                            }
                            Text(
                                text = stampCountText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Terracotta,
                                modifier = Modifier.testTag("collection_stamps_count_text")
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        if (stamps.isEmpty()) {
                            // Warm, compact empty state
                            EmptyStateView(
                                emoji = "🧭",
                                title = "Your passport is waiting",
                                subtitle = "Complete your first journey to earn a Travel Stamp.",
                                actionText = "START YOUR FIRST JOURNEY",
                                onActionClick = onCreateTripClick
                            )
                        } else {
                            // Scalable Stamp preview (Max HOME_COLLECTION_PREVIEW_LIMIT + More indicator)
                            val displayStamps = stamps.take(HOME_COLLECTION_PREVIEW_LIMIT)
                            val remainingCount = stamps.size - displayStamps.size

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_stamp_preview_row")
                            ) {
                                items(displayStamps, key = { it.id }) { stamp ->
                                    CompactStampBadge(
                                        stamp = stamp,
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clickable {
                                                onStampClick(stamp.tripId)
                                            }
                                    )
                                }

                                if (remainingCount > 0) {
                                    item(key = "more_stamps_indicator") {
                                        MoreStampsIndicator(
                                            remainingCount = remainingCount,
                                            onClick = onCollectionClick
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.md))

                            // View Full Collection Navigation link
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onCollectionClick() }
                                    .padding(vertical = Spacing.xs)
                                    .testTag("view_full_collection_button"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (stamps.size > 1) "VIEW FULL COLLECTION (${stamps.size}) →" else "VIEW FULL COLLECTION →",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Open Collection",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Current Expeditions (Active / Upcoming Trips)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = "Current Expeditions",
                        emoji = "🧭",
                        trailingText = if (sortedActiveTrips.isNotEmpty()) "${sortedActiveTrips.size} Active" else null,
                        modifier = Modifier.padding(bottom = Spacing.md)
                    )

                    if (sortedActiveTrips.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("no_active_expeditions_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.lg),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "🧭", fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Text(
                                    text = "No upcoming expeditions",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(Spacing.xxs))
                                Text(
                                    text = "Plan your next journey to organize gear, checklists & moments.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            sortedActiveTrips.forEach { trip ->
                                TripCardTicket(
                                    trip = trip,
                                    onClick = { onTripClick(trip.id) }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Recent Completed Journeys (Strictly sorted by trip date, latest 2-3 preview)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = "Recent Journeys",
                        emoji = "🏅",
                        trailingText = if (sortedCompletedTrips.isNotEmpty()) "View All" else null,
                        onTrailingClick = onCollectionClick,
                        modifier = Modifier.padding(bottom = Spacing.md)
                    )

                    if (sortedCompletedTrips.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("no_recent_journeys_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.lg),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "🏅", fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Text(
                                    text = "Your journey collection starts here",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(Spacing.xxs))
                                Text(
                                    text = "Complete an expedition to issue your first official Travel Stamp.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            val displayCompleted = sortedCompletedTrips.take(RECENT_COMPLETED_PREVIEW_LIMIT)
                            displayCompleted.forEach { trip ->
                                TripCardTicket(
                                    trip = trip,
                                    onClick = { onTripClick(trip.id) }
                                )
                            }

                            if (sortedCompletedTrips.size > RECENT_COMPLETED_PREVIEW_LIMIT) {
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onCollectionClick() }
                                        .padding(vertical = Spacing.sm, horizontal = Spacing.xs)
                                        .testTag("view_all_journeys_button"),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "VIEW ALL JOURNEYS (${sortedCompletedTrips.size}) →",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Spacing
            item {
                Spacer(modifier = Modifier.height(Spacing.lg))
            }
        }
    }
}
