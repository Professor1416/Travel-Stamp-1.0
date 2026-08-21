package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TripStatus
import com.example.data.util.DateUtils
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingView
import com.example.ui.components.SectionHeader
import com.example.ui.components.Spacing
import com.example.ui.components.TravelConfirmationDialog
import com.example.ui.components.TravelPrimaryButton
import com.example.ui.theme.ForestPine
import com.example.ui.theme.InkAmber
import com.example.ui.theme.InkCrimson
import com.example.ui.theme.InkEspresso
import com.example.ui.theme.InkForest
import com.example.ui.theme.InkNavy
import com.example.ui.theme.InkTerracotta
import com.example.ui.theme.Terracotta
import com.example.ui.viewmodel.FinishTripUiState
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.delay

data class InkOption(val name: String, val hex: String, val color: Color)
data class StampStyleOption(val id: String, val name: String, val emoji: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishTripScreen(
    tripId: Long,
    viewModel: TravelViewModel,
    onNavigateBack: () -> Unit,
    onStampGenerated: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val trip by viewModel.currentTrip.collectAsStateWithLifecycle()
    val moments by viewModel.currentTripMoments.collectAsStateWithLifecycle()
    val checklistItems by viewModel.currentTripChecklist.collectAsStateWithLifecycle()
    val finishUiState by viewModel.finishTripUiState.collectAsStateWithLifecycle()
    val stamp by viewModel.currentTripStamp.collectAsStateWithLifecycle()

    var reflectionNote by remember { mutableStateOf("") }
    var selectedInkHex by remember { mutableStateOf("#C85A32") } // Default Terracotta
    var selectedStyle by remember { mutableStateOf("MOUNTAIN") }
    var showFinishConfirmationDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isInitialLoading by remember(tripId) { mutableStateOf(true) }

    LaunchedEffect(tripId, trip) {
        if (trip != null) {
            isInitialLoading = false
        } else {
            delay(1000)
            isInitialLoading = false
        }
    }

    val isSealing = finishUiState is FinishTripUiState.Loading

    // Intercept back button during atomic stamp generation to prevent corrupt/partial state
    BackHandler(enabled = isSealing) {}

    val inkOptions = listOf(
        InkOption("Terracotta", "#C85A32", InkTerracotta),
        InkOption("Forest Pine", "#1E3A2F", InkForest),
        InkOption("Navy Ink", "#243642", InkNavy),
        InkOption("Espresso", "#3E2723", InkEspresso),
        InkOption("Crimson Seal", "#8B1E28", InkCrimson),
        InkOption("Amber Gold", "#B07D46", InkAmber)
    )

    val styleOptions = listOf(
        StampStyleOption("MOUNTAIN", "Mountain Ridge", "🏔️"),
        StampStyleOption("COMPASS", "Trail Compass", "🧭"),
        StampStyleOption("PINE", "Wild Timber", "🌲"),
        StampStyleOption("EXPEDITION", "Heritage Seal", "⚜️")
    )

    if (trip == null) {
        if (isInitialLoading) {
            LoadingView(
                message = "Loading expedition details...",
                testTag = "finish_trip_loading"
            )
        } else {
            ErrorStateView(
                title = "Journey not found",
                message = "Unable to load the expedition details to complete this trip.",
                retryAction = {
                    isInitialLoading = true
                    viewModel.selectTrip(tripId)
                },
                retryButtonText = "Retry",
                backAction = onNavigateBack,
                backButtonText = "Go Back",
                testTag = "finish_trip_error"
            )
        }
        return
    }

    val currentTrip = trip!!

    // Handle Generation UI State Modes (Loading / Success / Error)
    when (val state = finishUiState) {
        is FinishTripUiState.Loading -> {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                LoadingView(
                    message = "Generating your official stamp...",
                    modifier = Modifier.padding(innerPadding),
                    testTag = "generating_stamp_loading"
                )
            }
            return
        }

        is FinishTripUiState.Success -> {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "OFFICIAL STAMP READY",
                                style = MaterialTheme.typography.titleMedium,
                                letterSpacing = 1.sp
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(Spacing.screenHorizontal),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            Terracotta.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.xxl)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Terracotta.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "🎖️", fontSize = 36.sp)
                            }

                            Spacer(modifier = Modifier.height(Spacing.lg))

                            Text(
                                text = "Your official stamp is ready!",
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(Spacing.xs))

                            Text(
                                text = "Expedition '${currentTrip.name}' has been sealed with official Stamp ${state.stamp.stampCode}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(Spacing.xxl))

                            TravelPrimaryButton(
                                text = "VIEW STAMP",
                                icon = Icons.Default.MilitaryTech,
                                onClick = {
                                    viewModel.resetFinishTripState()
                                    onStampGenerated(state.tripId)
                                },
                                testTag = "view_stamp_button"
                            )
                        }
                    }
                }
            }
            return
        }

        is FinishTripUiState.Error -> {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "FINISH EXPEDITION",
                                style = MaterialTheme.typography.titleMedium,
                                letterSpacing = 1.sp
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                ErrorStateView(
                    title = "Unable to generate your stamp",
                    message = state.message.ifBlank { "Please try again." },
                    retryAction = {
                        viewModel.finishTrip(
                            tripId = currentTrip.id,
                            reflectionNote = reflectionNote.trim().ifBlank { null },
                            stampInkColorHex = selectedInkHex,
                            stampStyle = selectedStyle
                        )
                    },
                    retryButtonText = "Retry",
                    backAction = {
                        viewModel.resetFinishTripState()
                        onNavigateBack()
                    },
                    backButtonText = "Go Back",
                    modifier = Modifier.padding(innerPadding),
                    testTag = "finish_trip_error_state"
                )
            }
            return
        }

        FinishTripUiState.Idle -> Unit
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "FINISH EXPEDITION",
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("finish_trip_back_button")
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
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            // Already-Completed Notice if trip has earned a stamp
            if (currentTrip.status == TripStatus.COMPLETED && (currentTrip.stampEarned || stamp != null)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ForestPine.copy(alpha = 0.08f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ForestPine.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MilitaryTech,
                                    contentDescription = null,
                                    tint = ForestPine
                                )
                                Text(
                                    text = "Expedition Already Sealed",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "This expedition has already earned its official Travel Stamp (${stamp?.stampCode ?: "#---"}).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TravelPrimaryButton(
                                text = "VIEW OFFICIAL STAMP",
                                icon = Icons.Default.MilitaryTech,
                                onClick = { onStampGenerated(currentTrip.id) },
                                testTag = "view_already_earned_stamp_button"
                            )
                        }
                    }
                }
            }

            // 1. Debrief Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "🎖️", fontSize = 22.sp)
                            }
                            Spacer(modifier = Modifier.width(Spacing.md))
                            Column {
                                Text(
                                    text = currentTrip.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Ready to stamp your passport",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Stats Summary Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(Spacing.md),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${moments.size}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "MOMENTS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${checklistItems.count { it.isCompleted }}/${checklistItems.size}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Terracotta
                                )
                                Text(
                                    text = "CHECKLIST",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${currentTrip.peopleCount}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = ForestPine
                                )
                                Text(
                                    text = "EXPLORERS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // 2. Clear Pre-Generation Explanation Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.cardPadding),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 2.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            Text(
                                text = "Ready to seal this journey?",
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Finishing this expedition will add it to your travel history and issue its official Travel Stamp. Its stamp number will remain permanently associated with this journey. The journey date (${currentTrip.date}) becomes part of your official passport record and can only be adjusted via 'Correct Journey Date'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // 3. Ink Color Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        SectionHeader(title = "Select Stamp Ink", emoji = "🎨")

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(inkOptions) { ink ->
                                val isSelected = selectedInkHex.equals(ink.hex, ignoreCase = true)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedInkHex = ink.hex }
                                        .padding(Spacing.xs)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(ink.color)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Text(
                                        text = ink.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Stamp Motif / Style Option
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        SectionHeader(title = "Passport Stamp Motif", emoji = "🏷️")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            styleOptions.forEach { style ->
                                val isSelected = selectedStyle == style.id
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
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
                                        .clickable { selectedStyle = style.id }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = Spacing.md, horizontal = Spacing.xs),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = style.emoji, fontSize = 22.sp)
                                        Spacer(modifier = Modifier.height(Spacing.xs))
                                        Text(
                                            text = style.name,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Final Reflection Note
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        SectionHeader(title = "Expedition Reflection", emoji = "✍️")

                        OutlinedTextField(
                            value = reflectionNote,
                            onValueChange = { reflectionNote = it },
                            placeholder = { Text("e.g. Unforgettable summit climb through the heavy monsoon mist. Best chai at base village.") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("finish_reflection_input"),
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }

            // Primary Action: ISSUE TRAVEL STAMP
            item {
                val isFutureTrip = DateUtils.isFutureDate(currentTrip.date)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = Spacing.xs)
                        )
                    }

                    TravelPrimaryButton(
                        text = when {
                            isSealing -> "SEALING EXPEDITION & STAMPING..."
                            isFutureTrip -> "CANNOT FINISH (FUTURE DATE)"
                            else -> "GENERATE OFFICIAL STAMP"
                        },
                        icon = if (isFutureTrip) Icons.Default.Lock else Icons.Default.MilitaryTech,
                        enabled = !isFutureTrip && !isSealing,
                        onClick = {
                            if (!isFutureTrip && !isSealing) {
                                errorMessage = null
                                showFinishConfirmationDialog = true
                            }
                        },
                        testTag = "generate_stamp_submit_button"
                    )
                    if (isFutureTrip) {
                        Text(
                            text = "Trip starts on ${currentTrip.date}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.lg))
            }
        }
    }

    if (showFinishConfirmationDialog) {
        TravelConfirmationDialog(
            title = "Seal Expedition & Issue Stamp?",
            message = "Finishing will mark '${currentTrip.name}' as completed and permanently issue its official Travel Stamp with a unique number. The journey date (${currentTrip.date}) will become part of your permanent passport record.",
            confirmButtonText = "Seal & Issue Stamp",
            onConfirm = {
                if (!isSealing) {
                    showFinishConfirmationDialog = false
                    viewModel.finishTrip(
                        tripId = currentTrip.id,
                        reflectionNote = reflectionNote.trim().ifBlank { null },
                        stampInkColorHex = selectedInkHex,
                        stampStyle = selectedStyle
                    )
                }
            },
            onDismiss = {
                if (!isSealing) {
                    showFinishConfirmationDialog = false
                }
            }
        )
    }
}
