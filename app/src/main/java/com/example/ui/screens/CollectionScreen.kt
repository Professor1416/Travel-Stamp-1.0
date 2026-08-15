package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CollectionStampItem
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PassportSummaryCard
import com.example.ui.components.Spacing
import com.example.ui.components.TripCardTicket
import com.example.ui.viewmodel.TravelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    viewModel: TravelViewModel,
    onNavigateBack: () -> Unit,
    onTripClick: (Long) -> Unit,
    onStampClick: (Long) -> Unit,
    onCreateTripClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allTrips by viewModel.allTrips.collectAsStateWithLifecycle()
    val stamps by viewModel.stamps.collectAsStateWithLifecycle()
    val completedTripsCount by viewModel.completedTripsCount.collectAsStateWithLifecycle()
    val totalMomentsCount by viewModel.totalMomentsCount.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Stamps Collection (${stamps.size})", "All Journeys (${allTrips.size})")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MY TRAVEL PASSPORT",
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("collection_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // 1. Passport Summary Banner (Unified Design System)
            item {
                PassportSummaryCard(
                    stampsCount = stamps.size,
                    journeysCount = allTrips.size,
                    momentsCount = totalMomentsCount
                )
            }

            // 2. Tab Row
            item {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            height = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    divider = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.4.sp
                                )
                            }
                        )
                    }
                }
            }

            // 3. Tab Contents
            if (selectedTabIndex == 0) {
                // TAB 0: STAMPS COLLECTION
                if (stamps.isEmpty()) {
                    item {
                        EmptyStateView(
                            emoji = "🛂",
                            title = "No stamps earned yet",
                            subtitle = "Finish an expedition to earn your official Travel Stamp with a permanent certification number.",
                            actionText = "Start a Journey",
                            onActionClick = onCreateTripClick
                        )
                    }
                } else {
                    items(stamps, key = { it.id }) { stamp ->
                        CollectionStampItem(
                            stamp = stamp,
                            onClick = { onStampClick(stamp.tripId) }
                        )
                    }
                }
            } else {
                // TAB 1: ALL JOURNEYS
                if (allTrips.isEmpty()) {
                    item {
                        EmptyStateView(
                            emoji = "🎒",
                            title = "No journeys recorded",
                            subtitle = "Plan and log your adventures with notes, moments, checklists, and collectible stamps.",
                            actionText = "Create New Trip",
                            onActionClick = onCreateTripClick
                        )
                    }
                } else {
                    items(allTrips, key = { it.id }) { trip ->
                        TripCardTicket(
                            trip = trip,
                            onClick = { onTripClick(trip.id) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.xxl))
            }
        }
    }
}
