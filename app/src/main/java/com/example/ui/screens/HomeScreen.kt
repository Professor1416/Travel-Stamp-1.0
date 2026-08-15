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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.ui.components.CompactStampBadge
import com.example.ui.components.EmptyStateView
import com.example.ui.components.SectionHeader
import com.example.ui.components.Spacing
import com.example.ui.components.TravelPrimaryButton
import com.example.ui.components.TripCardTicket
import com.example.ui.theme.ForestPine
import com.example.ui.theme.OchreGold
import com.example.ui.theme.Terracotta
import com.example.ui.viewmodel.TravelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TravelViewModel,
    onCreateTripClick: () -> Unit,
    onTripClick: (Long) -> Unit,
    onCollectionClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStampClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTrips by viewModel.activeTrips.collectAsStateWithLifecycle()
    val completedTrips by viewModel.completedTrips.collectAsStateWithLifecycle()
    val stamps by viewModel.stamps.collectAsStateWithLifecycle()
    val completedTripsCount by viewModel.completedTripsCount.collectAsStateWithLifecycle()
    val totalMomentsCount by viewModel.totalMomentsCount.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
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
                        .padding(top = Spacing.xs, bottom = Spacing.xs),
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

            // 3. Collection Summary & Preview (Max 3 stamps preview)
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

                            Text(
                                text = "${stamps.size} ${if (stamps.size == 1) "Stamp" else "Stamps"}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Terracotta
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        if (stamps.isEmpty()) {
                            // Warm empty state
                            EmptyStateView(
                                emoji = "🧭",
                                title = "Your passport is still empty",
                                subtitle = "Start your first journey and earn your first official Travel Stamp."
                            )
                        } else {
                            // Stamp preview (Max 3 stamps for clean balanced layout)
                            val displayStamps = stamps.take(3)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(displayStamps) { stamp ->
                                    CompactStampBadge(
                                        stamp = stamp,
                                        modifier = Modifier.clickable {
                                            onStampClick(stamp.tripId)
                                        }
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
                                .padding(vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (stamps.size > 3) "View Full Collection (${stamps.size}) →" else "Open Passport Book →",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Open Collection",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 4. Current Expeditions (Active Trips)
            if (activeTrips.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = "Current Expeditions",
                            emoji = "🧭",
                            trailingText = "${activeTrips.size} Active",
                            modifier = Modifier.padding(bottom = Spacing.md)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            activeTrips.forEach { trip ->
                                TripCardTicket(
                                    trip = trip,
                                    onClick = { onTripClick(trip.id) }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Recent Completed Expeditions Preview (if any)
            if (completedTrips.isNotEmpty() && activeTrips.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = "Recent Journeys",
                            emoji = "🏅",
                            trailingText = "View All",
                            onTrailingClick = onCollectionClick,
                            modifier = Modifier.padding(bottom = Spacing.md)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            completedTrips.take(2).forEach { trip ->
                                TripCardTicket(
                                    trip = trip,
                                    onClick = { onTripClick(trip.id) }
                                )
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
