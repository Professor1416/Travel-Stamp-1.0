package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingView
import com.example.ui.components.Spacing
import com.example.ui.components.TravelOutlinedButton
import com.example.ui.components.TravelStampCard
import com.example.ui.poster.PosterTemplate
import com.example.ui.poster.StampEditionFormat
import com.example.ui.theme.ForestPine
import com.example.ui.theme.Terracotta
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelStampScreen(
    tripId: Long,
    viewModel: TravelViewModel,
    onNavigateBack: () -> Unit,
    onViewTripCard: () -> Unit,
    onCollectionClick: () -> Unit,
    onCreatePosterClick: (Long) -> Unit = {},
    onCreateEditionClick: (tripId: Long, format: StampEditionFormat, template: PosterTemplate) -> Unit = { id, _, _ -> onCreatePosterClick(id) },
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(tripId) {
        viewModel.selectTrip(tripId)
    }

    val trip by viewModel.currentTrip.collectAsStateWithLifecycle()
    val stamp by viewModel.currentTripStamp.collectAsStateWithLifecycle()

    var selectedFormat by remember { mutableStateOf(StampEditionFormat.PORTRAIT) }
    var showFormatPicker by remember { mutableStateOf(false) }
    var isInitialLoading by remember(tripId) { mutableStateOf(true) }

    LaunchedEffect(tripId, stamp, trip) {
        if (stamp != null && trip != null) {
            isInitialLoading = false
        } else {
            // Allow up to 1000ms for Room Flow emission before declaring not found
            delay(1000)
            isInitialLoading = false
        }
    }

    // Stamp Reveal Animation States
    val stampScale = remember { Animatable(1.25f) }
    val stampAlpha = remember { Animatable(0f) }

    LaunchedEffect(stamp?.id) {
        if (stamp != null) {
            stampAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = LinearEasing)
            )
            stampScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    if (stamp == null || trip == null) {
        if (isInitialLoading) {
            LoadingView(
                message = "Loading stamp...",
                testTag = "stamp_loading_view"
            )
        } else {
            ErrorStateView(
                title = "Stamp not found",
                message = "Unable to load the official travel stamp for this journey. Please check your journey log or try again.",
                retryAction = {
                    isInitialLoading = true
                    viewModel.selectTrip(tripId)
                },
                retryButtonText = "Retry",
                backAction = onNavigateBack,
                backButtonText = "Go Back",
                testTag = "stamp_error_view"
            )
        }
        return
    }

    val currentStamp = stamp!!

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "TRAVEL STAMP",
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("stamp_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onCollectionClick,
                        modifier = Modifier.testTag("collection_nav_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = "My Collection",
                            tint = MaterialTheme.colorScheme.primary
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
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Celebratory Reveal Badge
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ForestPine.copy(alpha = 0.12f))
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ForestPine,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = "Official Stamp ${currentStamp.stampCode} issued to passport",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ForestPine,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 2. Large Official Travel Stamp Card (Canonical Official Artwork - No automatic moment photo)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(stampScale.value)
                        .alpha(stampAlpha.value)
                ) {
                    TravelStampCard(
                        stamp = currentStamp,
                        photoUri = null,
                        elevation = 6.dp
                    )
                }
            }

            // 3. Single Action: VIEW EXPEDITION LOG
            item {
                TravelOutlinedButton(
                    text = "VIEW EXPEDITION LOG",
                    icon = Icons.Default.Hiking,
                    onClick = onViewTripCard,
                    testTag = "view_trip_log_button"
                )
            }

            // 4. CREATE YOUR STAMP EDITION Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_stamp_edition_section"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        // Section Title
                        Text(
                            text = "CREATE YOUR STAMP EDITION",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 0.5.sp
                        )

                        // Selected Format indicator & Change action
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    Text(
                                        text = "Selected:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = selectedFormat.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("selected_format_label")
                                    )
                                }

                                Text(
                                    text = if (showFormatPicker) "Done" else "Change",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showFormatPicker = !showFormatPicker }
                                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                                        .testTag("change_format_button")
                                )
                            }
                        }

                        // Expandable Format Selector (Square, Portrait, Story)
                        AnimatedVisibility(visible = showFormatPicker) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                val formats = listOf(
                                    StampEditionFormat.SQUARE,
                                    StampEditionFormat.PORTRAIT,
                                    StampEditionFormat.STORY
                                )
                                formats.forEach { format ->
                                    val isSelected = selectedFormat == format
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        ),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedFormat = format
                                            }
                                            .testTag("format_option_${format.title}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = format.title,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Two Large Clear Choices: Passport Stamp & Photo + Stamp
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            // Choice 1: Passport Stamp ("Classic official edition")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onCreateEditionClick(
                                            currentStamp.tripId,
                                            selectedFormat,
                                            PosterTemplate.PASSPORT_STAMP
                                        )
                                    }
                                    .testTag("edition_choice_passport_stamp"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(ForestPine.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MilitaryTech,
                                            contentDescription = null,
                                            tint = ForestPine,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Passport Stamp",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Classic official edition",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Choice 2: Photo + Stamp ("Create with a journey photo")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onCreateEditionClick(
                                            currentStamp.tripId,
                                            selectedFormat,
                                            PosterTemplate.PHOTO_STAMP
                                        )
                                    }
                                    .testTag("edition_choice_photo_stamp"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Terracotta.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            tint = Terracotta,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Photo + Stamp",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Create with a journey photo",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}
