package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TravelStamp
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.data.util.DateUtils
import com.example.ui.components.CollectionStampItem
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PassportSummaryCard
import com.example.ui.components.Spacing
import com.example.ui.components.TripCardTicket
import com.example.ui.theme.ForestPine
import com.example.ui.theme.OchreGold
import com.example.ui.theme.Terracotta
import com.example.ui.viewmodel.TravelViewModel
import java.time.LocalDate

enum class StampSortOption(val displayName: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    STAMP_NUMBER_DESC("Stamp Number: High → Low"),
    STAMP_NUMBER_ASC("Stamp Number: Low → High"),
    MOST_MOMENTS("Most Moments"),
    LEAST_MOMENTS("Least Moments"),
    NAME_ASC("Name: A → Z"),
    NAME_DESC("Name: Z → A")
}

enum class JourneySortOption(val displayName: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    UPCOMING_FIRST("Upcoming First"),
    COMPLETED_FIRST("Completed First"),
    MOST_MOMENTS("Most Moments"),
    LEAST_MOMENTS("Least Moments"),
    NAME_ASC("Name: A → Z"),
    NAME_DESC("Name: Z → A")
}

enum class StatusFilter(val displayName: String) {
    ALL("All"),
    UPCOMING("Upcoming"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed")
}

enum class MomentsFilter(val displayName: String) {
    ALL("All"),
    HAS_MOMENTS("Has Moments"),
    NO_MOMENTS("No Moments")
}

enum class DatePeriodFilter(val displayName: String) {
    ALL_TIME("All Time"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year")
}

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
    val totalMomentsCount by viewModel.totalMomentsCount.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current

    // State preserved across configuration changes and navigation
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    
    // Sort states
    var stampSortOption by rememberSaveable { mutableStateOf(StampSortOption.NEWEST_FIRST) }
    var journeySortOption by rememberSaveable { mutableStateOf(JourneySortOption.NEWEST_FIRST) }

    // Filter states
    var journeyStatusFilter by rememberSaveable { mutableStateOf(StatusFilter.ALL) }
    var momentsFilter by rememberSaveable { mutableStateOf(MomentsFilter.ALL) }
    var datePeriodFilter by rememberSaveable { mutableStateOf(DatePeriodFilter.ALL_TIME) }

    // Bottom sheet visibility states
    var showSortSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val sortSheetState = rememberModalBottomSheetState()
    val filterSheetState = rememberModalBottomSheetState()

    val today = remember { DateUtils.getTodayLocalDate() }
    val stampsMap = remember(stamps) { stamps.associateBy { it.tripId } }

    // Number of active filters for badge
    val activeFiltersCount by remember(selectedTabIndex, journeyStatusFilter, momentsFilter, datePeriodFilter) {
        derivedStateOf {
            var count = 0
            if (selectedTabIndex == 1 && journeyStatusFilter != StatusFilter.ALL) count++
            if (momentsFilter != MomentsFilter.ALL) count++
            if (datePeriodFilter != DatePeriodFilter.ALL_TIME) count++
            count
        }
    }

    // Filtered & sorted stamps
    val filteredStamps by remember(stamps, searchQuery, stampSortOption, momentsFilter, datePeriodFilter, today) {
        derivedStateOf {
            val query = searchQuery.trim().lowercase()
            val filtered = stamps.filter { stamp ->
                val matchesMoments = when (momentsFilter) {
                    MomentsFilter.ALL -> true
                    MomentsFilter.HAS_MOMENTS -> stamp.momentsCount > 0
                    MomentsFilter.NO_MOMENTS -> stamp.momentsCount == 0
                }
                val matchesPeriod = matchesDatePeriod(stamp.dateText, datePeriodFilter, today)
                val matchesSearch = matchesStampSearch(stamp, query)
                matchesMoments && matchesPeriod && matchesSearch
            }

            when (stampSortOption) {
                StampSortOption.NEWEST_FIRST -> filtered.sortedWith(compareByDescending<TravelStamp> { getStampEpoch(it) }.thenByDescending { it.stampNumber })
                StampSortOption.OLDEST_FIRST -> filtered.sortedWith(compareBy<TravelStamp> { getStampEpoch(it) }.thenBy { it.stampNumber })
                StampSortOption.STAMP_NUMBER_DESC -> filtered.sortedByDescending { it.stampNumber }
                StampSortOption.STAMP_NUMBER_ASC -> filtered.sortedBy { it.stampNumber }
                StampSortOption.MOST_MOMENTS -> filtered.sortedWith(compareByDescending<TravelStamp> { it.momentsCount }.thenByDescending { it.stampNumber })
                StampSortOption.LEAST_MOMENTS -> filtered.sortedWith(compareBy<TravelStamp> { it.momentsCount }.thenBy { it.stampNumber })
                StampSortOption.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
                StampSortOption.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            }
        }
    }

    // Filtered & sorted journeys
    val filteredTrips by remember(allTrips, stampsMap, searchQuery, journeySortOption, journeyStatusFilter, momentsFilter, datePeriodFilter, today) {
        derivedStateOf {
            val query = searchQuery.trim().lowercase()
            val filtered = allTrips.filter { trip ->
                val matchesStatus = when (journeyStatusFilter) {
                    StatusFilter.ALL -> true
                    StatusFilter.UPCOMING -> trip.status == TripStatus.UPCOMING
                    StatusFilter.IN_PROGRESS -> trip.status == TripStatus.IN_PROGRESS
                    StatusFilter.COMPLETED -> trip.status == TripStatus.COMPLETED
                }
                val momentsCount = stampsMap[trip.id]?.momentsCount ?: 0
                val matchesMoments = when (momentsFilter) {
                    MomentsFilter.ALL -> true
                    MomentsFilter.HAS_MOMENTS -> momentsCount > 0
                    MomentsFilter.NO_MOMENTS -> momentsCount == 0
                }
                val matchesPeriod = matchesDatePeriod(trip.date, datePeriodFilter, today)
                val matchesSearch = matchesTripSearch(trip, stampsMap[trip.id], query)

                matchesStatus && matchesMoments && matchesPeriod && matchesSearch
            }

            when (journeySortOption) {
                JourneySortOption.NEWEST_FIRST -> filtered.sortedWith(compareByDescending<Trip> { getTripEpoch(it) }.thenByDescending { it.id })
                JourneySortOption.OLDEST_FIRST -> filtered.sortedWith(compareBy<Trip> { getTripEpoch(it) }.thenBy { it.id })
                JourneySortOption.UPCOMING_FIRST -> filtered.sortedWith(
                    compareBy<Trip> {
                        when (it.status) {
                            TripStatus.UPCOMING -> 0
                            TripStatus.IN_PROGRESS -> 1
                            TripStatus.COMPLETED -> 2
                        }
                    }.thenByDescending { getTripEpoch(it) }
                )
                JourneySortOption.COMPLETED_FIRST -> filtered.sortedWith(
                    compareBy<Trip> {
                        when (it.status) {
                            TripStatus.COMPLETED -> 0
                            TripStatus.IN_PROGRESS -> 1
                            TripStatus.UPCOMING -> 2
                        }
                    }.thenByDescending { getTripEpoch(it) }
                )
                JourneySortOption.MOST_MOMENTS -> filtered.sortedWith(
                    compareByDescending<Trip> { stampsMap[it.id]?.momentsCount ?: 0 }.thenByDescending { getTripEpoch(it) }
                )
                JourneySortOption.LEAST_MOMENTS -> filtered.sortedWith(
                    compareBy<Trip> { stampsMap[it.id]?.momentsCount ?: 0 }.thenBy { getTripEpoch(it) }
                )
                JourneySortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                JourneySortOption.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            }
        }
    }

    val isFilterActive = activeFiltersCount > 0 || searchQuery.isNotBlank()

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
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 1. Passport Summary Banner
            item {
                PassportSummaryCard(
                    stampsCount = stamps.size,
                    journeysCount = allTrips.size,
                    momentsCount = totalMomentsCount
                )
            }

            // 2. Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("passport_search_input"),
                    placeholder = {
                        Text(
                            text = "Search stamps or journeys...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (searchQuery.isNotEmpty()) Terracotta else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.testTag("passport_clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        focusedBorderColor = Terracotta,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )
            }

            // 3. Compact Sort & Filter Action Controls Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sort Button
                    Surface(
                        onClick = { showSortSheet = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("passport_sort_button"),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val currentSortLabel = if (selectedTabIndex == 0) {
                                stampSortOption.displayName
                            } else {
                                journeySortOption.displayName
                            }
                            Text(
                                text = "Sort: $currentSortLabel",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Filter Button with Badge
                    Surface(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("passport_filter_button"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (activeFiltersCount > 0) {
                            ForestPine.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (activeFiltersCount > 0) OchreGold else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Filter",
                                modifier = Modifier.size(16.dp),
                                tint = if (activeFiltersCount > 0) OchreGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (activeFiltersCount > 0) "Filter ($activeFiltersCount)" else "Filter",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeFiltersCount > 0) OchreGold else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Quick Clear button if filters or search are active
                    if (isFilterActive) {
                        Surface(
                            onClick = {
                                searchQuery = ""
                                journeyStatusFilter = StatusFilter.ALL
                                momentsFilter = MomentsFilter.ALL
                                datePeriodFilter = DatePeriodFilter.ALL_TIME
                            },
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("passport_clear_all_filters_button"),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = Spacing.sm),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // 4. Tab Row with dynamic filtered counts
            item {
                val stampsTabTitle = if (searchQuery.isNotBlank() || momentsFilter != MomentsFilter.ALL || datePeriodFilter != DatePeriodFilter.ALL_TIME) {
                    "Stamps (${filteredStamps.size}/${stamps.size})"
                } else {
                    "Stamps (${stamps.size})"
                }

                val journeysTabTitle = if (searchQuery.isNotBlank() || journeyStatusFilter != StatusFilter.ALL || momentsFilter != MomentsFilter.ALL || datePeriodFilter != DatePeriodFilter.ALL_TIME) {
                    "Journeys (${filteredTrips.size}/${allTrips.size})"
                } else {
                    "Journeys (${allTrips.size})"
                }

                val tabs = listOf(stampsTabTitle, journeysTabTitle)

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
                                    letterSpacing = 0.4.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("collection_tab_$index")
                        )
                    }
                }
            }

            // 5. Tab Contents
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
                } else if (filteredStamps.isEmpty()) {
                    item {
                        EmptyStateView(
                            emoji = "🔎",
                            title = "No stamps found",
                            subtitle = "No travel stamps match your current search query or active filter settings.",
                            actionText = "Clear Search & Filters",
                            onActionClick = {
                                searchQuery = ""
                                momentsFilter = MomentsFilter.ALL
                                datePeriodFilter = DatePeriodFilter.ALL_TIME
                            }
                        )
                    }
                } else {
                    items(filteredStamps, key = { it.id }) { stamp ->
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
                } else if (filteredTrips.isEmpty()) {
                    item {
                        EmptyStateView(
                            emoji = "🔎",
                            title = "No journeys found",
                            subtitle = "No travel journeys match your current search query or active filter settings.",
                            actionText = "Clear Search & Filters",
                            onActionClick = {
                                searchQuery = ""
                                journeyStatusFilter = StatusFilter.ALL
                                momentsFilter = MomentsFilter.ALL
                                datePeriodFilter = DatePeriodFilter.ALL_TIME
                            }
                        )
                    }
                } else {
                    items(filteredTrips, key = { it.id }) { trip ->
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

    // Sort Bottom Sheet
    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            sheetState = sortSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenHorizontal)
                    .padding(bottom = Spacing.xxl)
            ) {
                Text(
                    text = if (selectedTabIndex == 0) "SORT STAMPS COLLECTION" else "SORT ALL JOURNEYS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(Spacing.md))

                if (selectedTabIndex == 0) {
                    StampSortOption.entries.forEach { option ->
                        SortOptionRow(
                            label = option.displayName,
                            isSelected = stampSortOption == option,
                            onClick = {
                                stampSortOption = option
                                showSortSheet = false
                            },
                            testTag = "sort_option_${option.name}"
                        )
                    }
                } else {
                    JourneySortOption.entries.forEach { option ->
                        SortOptionRow(
                            label = option.displayName,
                            isSelected = journeySortOption == option,
                            onClick = {
                                journeySortOption = option
                                showSortSheet = false
                            },
                            testTag = "sort_option_${option.name}"
                        )
                    }
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = filterSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenHorizontal)
                    .padding(bottom = Spacing.xxl)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FILTER COLLECTION",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.8.sp
                    )

                    TextButton(
                        onClick = {
                            journeyStatusFilter = StatusFilter.ALL
                            momentsFilter = MomentsFilter.ALL
                            datePeriodFilter = DatePeriodFilter.ALL_TIME
                        },
                        modifier = Modifier.testTag("filter_reset_button")
                    ) {
                        Text(
                            text = "Reset All",
                            color = Terracotta,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Status Filter (Only for Journeys tab)
                if (selectedTabIndex == 1) {
                    FilterSectionTitle("JOURNEY STATUS")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        StatusFilter.entries.forEach { status ->
                            FilterChipItem(
                                label = status.displayName,
                                isSelected = journeyStatusFilter == status,
                                onClick = { journeyStatusFilter = status },
                                testTag = "filter_status_${status.name}"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.lg))
                }

                // Moments Filter
                FilterSectionTitle("MOMENTS")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    MomentsFilter.entries.forEach { filter ->
                        FilterChipItem(
                            label = filter.displayName,
                            isSelected = momentsFilter == filter,
                            onClick = { momentsFilter = filter },
                            testTag = "filter_moments_${filter.name}"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Date Period Filter
                FilterSectionTitle("TIME PERIOD")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    DatePeriodFilter.entries.forEach { period ->
                        FilterChipItem(
                            label = period.displayName,
                            isSelected = datePeriodFilter == period,
                            onClick = { datePeriodFilter = period },
                            testTag = "filter_period_${period.name}"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                Button(
                    onClick = { showFilterSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("filter_apply_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "APPLY FILTERS",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(Spacing.xs))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        modifier = Modifier.testTag(testTag),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ForestPine,
            selectedLabelColor = Color.White,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            selectedBorderColor = OchreGold,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun SortOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Terracotta else MaterialTheme.colorScheme.onSurface
        )
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Terracotta,
                unselectedColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

private fun getTripEpoch(trip: Trip): Long {
    val localDate = DateUtils.parseTripDate(trip.date)
    return localDate?.toEpochDay() ?: (trip.createdAt / (1000 * 60 * 60 * 24))
}

private fun getStampEpoch(stamp: TravelStamp): Long {
    val localDate = DateUtils.parseTripDate(stamp.dateText)
    return localDate?.toEpochDay() ?: (stamp.issuedAt / (1000 * 60 * 60 * 24))
}

private fun matchesDatePeriod(dateStr: String?, period: DatePeriodFilter, today: LocalDate): Boolean {
    if (period == DatePeriodFilter.ALL_TIME) return true
    val date = DateUtils.parseTripDate(dateStr) ?: return false
    return when (period) {
        DatePeriodFilter.ALL_TIME -> true
        DatePeriodFilter.THIS_MONTH -> date.year == today.year && date.month == today.month
        DatePeriodFilter.THIS_YEAR -> date.year == today.year
    }
}

private fun matchesStampSearch(stamp: TravelStamp, query: String): Boolean {
    if (query.isBlank()) return true
    val cleanQuery = query.trim().lowercase()

    val numStr = stamp.stampNumber.toString()
    val formattedCode = stamp.stampCode.lowercase()
    val paddedNum = String.format("%03d", stamp.stampNumber)

    return stamp.title.lowercase().contains(cleanQuery) ||
            stamp.destination.lowercase().contains(cleanQuery) ||
            stamp.dateText.lowercase().contains(cleanQuery) ||
            formattedCode.contains(cleanQuery) ||
            paddedNum.contains(cleanQuery) ||
            numStr.contains(cleanQuery) ||
            "#$numStr".contains(cleanQuery) ||
            "#$paddedNum".contains(cleanQuery) ||
            (stamp.reflectionNote?.lowercase()?.contains(cleanQuery) == true) ||
            stamp.inspectionText.lowercase().contains(cleanQuery)
}

private fun matchesTripSearch(trip: Trip, associatedStamp: TravelStamp?, query: String): Boolean {
    if (query.isBlank()) return true
    val cleanQuery = query.trim().lowercase()

    val matchesBase = trip.name.lowercase().contains(cleanQuery) ||
            trip.destination.lowercase().contains(cleanQuery) ||
            trip.description.lowercase().contains(cleanQuery) ||
            trip.date.lowercase().contains(cleanQuery) ||
            trip.status.name.lowercase().contains(cleanQuery)

    if (matchesBase) return true

    if (associatedStamp != null) {
        val numStr = associatedStamp.stampNumber.toString()
        val paddedNum = String.format("%03d", associatedStamp.stampNumber)
        val matchesStamp = associatedStamp.stampCode.lowercase().contains(cleanQuery) ||
                paddedNum.contains(cleanQuery) ||
                numStr.contains(cleanQuery) ||
                "#$numStr".contains(cleanQuery) ||
                "#$paddedNum".contains(cleanQuery) ||
                (associatedStamp.reflectionNote?.lowercase()?.contains(cleanQuery) == true)
        if (matchesStamp) return true
    }
    return false
}

