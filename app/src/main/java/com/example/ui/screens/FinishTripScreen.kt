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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.example.data.util.DateUtils
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
import com.example.ui.viewmodel.TravelViewModel

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

    var reflectionNote by remember { mutableStateOf("") }
    var selectedInkHex by remember { mutableStateOf("#C85A32") } // Default Terracotta
    var selectedStyle by remember { mutableStateOf("MOUNTAIN") }
    var showFinishConfirmationDialog by remember { mutableStateOf(false) }

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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading journey...")
        }
        return
    }

    val currentTrip = trip!!

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

            // 2. Ink Color Selection
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

            // 3. Stamp Motif / Style Option
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

            // 4. Final Reflection Note
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
                    TravelPrimaryButton(
                        text = if (isFutureTrip) "CANNOT FINISH (FUTURE DATE)" else "GENERATE OFFICIAL STAMP",
                        icon = if (isFutureTrip) Icons.Default.Lock else Icons.Default.MilitaryTech,
                        enabled = !isFutureTrip,
                        onClick = {
                            if (!isFutureTrip) {
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
            title = "Issue Travel Stamp?",
            message = "This will mark '${currentTrip.name}' as completed and issue a permanent certified Travel Stamp with a unique serial number in your passport.",
            confirmButtonText = "Seal Journey & Stamp",
            onConfirm = {
                showFinishConfirmationDialog = false
                viewModel.finishTrip(
                    tripId = currentTrip.id,
                    reflectionNote = reflectionNote.trim().ifBlank { null },
                    stampInkColorHex = selectedInkHex,
                    stampStyle = selectedStyle,
                    onFinished = { finishedTripId ->
                        onStampGenerated(finishedTripId)
                    }
                )
            },
            onDismiss = { showFinishConfirmationDialog = false }
        )
    }
}
