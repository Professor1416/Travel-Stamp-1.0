package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MomentCategory
import com.example.data.model.TripStatus
import com.example.data.util.DateUtils
import com.example.ui.components.ChecklistComponent
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingView
import com.example.ui.components.MomentItemCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.Spacing
import com.example.ui.components.TravelConfirmationDialog
import com.example.ui.components.TravelOutlinedButton
import com.example.ui.components.TravelPrimaryButton
import com.example.ui.components.TripCardTicket
import com.example.ui.theme.ForestPine
import com.example.ui.theme.Terracotta
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCardScreen(
    viewModel: TravelViewModel,
    onNavigateBack: () -> Unit,
    onAddMomentClick: (Long) -> Unit,
    onFinishTripClick: (Long) -> Unit,
    onViewStampClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val trip by viewModel.currentTrip.collectAsStateWithLifecycle()
    val checklistItems by viewModel.currentTripChecklist.collectAsStateWithLifecycle()
    val moments by viewModel.currentTripMoments.collectAsStateWithLifecycle()
    val stamp by viewModel.currentTripStamp.collectAsStateWithLifecycle()

    var isInitialLoading by remember(viewModel.selectedTripId.value) { mutableStateOf(true) }

    LaunchedEffect(viewModel.selectedTripId.value, trip) {
        if (trip != null) {
            isInitialLoading = false
        } else {
            delay(1000)
            isInitialLoading = false
        }
    }

    var selectedCategoryFilter by remember { mutableStateOf<MomentCategory?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteTripDialog by remember { mutableStateOf(false) }
    var showEditTripDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showCorrectDateDialog by remember { mutableStateOf(false) }
    var showCorrectDatePickerDialog by remember { mutableStateOf(false) }
    var proposedCorrectDate by remember { mutableStateOf("") }
    var correctDateError by remember { mutableStateOf<String?>(null) }

    var editName by remember { mutableStateOf("") }
    var editDestination by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    var editPeopleCount by remember { mutableStateOf("1") }
    var editDescription by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }

    val filteredMoments = remember(moments, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) {
            moments
        } else {
            moments.filter { it.category == selectedCategoryFilter }
        }
    }

    if (trip == null) {
        if (isInitialLoading) {
            LoadingView(
                message = "Loading journey...",
                testTag = "journey_loading_view"
            )
        } else {
            ErrorStateView(
                title = "Journey not found",
                message = "Unable to load the expedition log. It may have been deleted or is unavailable.",
                retryAction = {
                    isInitialLoading = true
                    viewModel.selectedTripId.value?.let { viewModel.selectTrip(it) }
                },
                retryButtonText = "Retry",
                backAction = onNavigateBack,
                backButtonText = "Go Back",
                testTag = "journey_error_view"
            )
        }
        return
    }

    val currentTrip = trip!!

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "EXPEDITION LOG",
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("trip_card_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("trip_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Trip Details") },
                            onClick = {
                                showMenu = false
                                editName = currentTrip.name
                                editDestination = currentTrip.destination
                                editDate = currentTrip.date
                                editPeopleCount = currentTrip.peopleCount.toString()
                                editDescription = currentTrip.description
                                editError = null
                                showEditTripDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.testTag("menu_edit_trip_details")
                        )
                        if (currentTrip.status == TripStatus.COMPLETED && (currentTrip.stampEarned || stamp != null)) {
                            DropdownMenuItem(
                                text = { Text("Correct Journey Date") },
                                onClick = {
                                    showMenu = false
                                    proposedCorrectDate = currentTrip.date
                                    correctDateError = null
                                    showCorrectDateDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("menu_correct_journey_date")
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete Trip", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showDeleteTripDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("menu_delete_trip")
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
            // 1. Trip Card Ticket Hero
            item {
                TripCardTicket(trip = currentTrip)
            }

            // 2. Primary Action Bar: View Stamp or Finish Journey
            item {
                if (currentTrip.status == TripStatus.COMPLETED) {
                    TravelPrimaryButton(
                        text = "VIEW OFFICIAL TRAVEL STAMP",
                        icon = Icons.Default.MilitaryTech,
                        onClick = { onViewStampClick(currentTrip.id) },
                        testTag = "view_official_stamp_button"
                    )
                } else {
                    val isUpcoming = currentTrip.status == TripStatus.UPCOMING || DateUtils.isFutureDate(currentTrip.date)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        TravelPrimaryButton(
                            text = if (isUpcoming) "FINISH TRIP & GET STAMP" else "FINISH TRIP & EARN STAMP",
                            icon = if (isUpcoming) Icons.Default.Lock else Icons.Default.MilitaryTech,
                            enabled = !isUpcoming,
                            onClick = {
                                if (!isUpcoming) {
                                    onFinishTripClick(currentTrip.id)
                                }
                            },
                            testTag = "finish_trip_button"
                        )
                        if (isUpcoming) {
                            Text(
                                text = "Trip starts on ${currentTrip.date}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Expedition Checklist Section
            item {
                ChecklistComponent(
                    items = checklistItems,
                    onAddItem = { text ->
                        viewModel.addCustomChecklistItem(currentTrip.id, text)
                    },
                    onToggleItem = { itemId, isCompleted ->
                        viewModel.toggleChecklistItem(itemId, isCompleted)
                    },
                    onDeleteItem = { itemId ->
                        viewModel.deleteChecklistItem(itemId)
                    }
                )
            }

            // 4. Expedition Timeline / Moments Header & Filter
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isCompactWidth = maxWidth < 380.dp

                        if (isCompactWidth) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "⏱️", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(Spacing.sm))
                                    Text(
                                        text = "EXPEDITION TIMELINE",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 1.2.sp
                                    )
                                }

                                Button(
                                    onClick = { onAddMomentClick(currentTrip.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .semantics { contentDescription = "Log a new travel moment" }
                                        .testTag("log_moment_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 2.dp,
                                        pressedElevation = 4.dp
                                    ),
                                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = "LOG MOMENT",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(text = "⏱️", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(Spacing.sm))
                                    Text(
                                        text = "EXPEDITION TIMELINE",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 1.2.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(Spacing.sm))

                                Button(
                                    onClick = { onAddMomentClick(currentTrip.id) },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .semantics { contentDescription = "Log a new travel moment" }
                                        .testTag("log_moment_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 2.dp,
                                        pressedElevation = 4.dp
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = "LOG MOMENT",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        letterSpacing = 0.8.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // Category Filter Pills
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (selectedCategoryFilter == null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.clickable { selectedCategoryFilter = null }
                            ) {
                                Text(
                                    text = "All (${moments.size})",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedCategoryFilter == null) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = 6.dp)
                                )
                            }
                        }

                        items(MomentCategory.entries) { cat ->
                            val count = moments.count { it.category == cat }
                            if (count > 0 || selectedCategoryFilter == cat) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selectedCategoryFilter == cat) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    modifier = Modifier.clickable {
                                        selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                                    }
                                ) {
                                    Text(
                                        text = "${cat.emoji} ${cat.title} ($count)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selectedCategoryFilter == cat) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Moments Stream
            if (filteredMoments.isEmpty()) {
                item {
                    EmptyStateView(
                        emoji = "📸",
                        title = "No moments recorded yet",
                        subtitle = "Capture milestones, trail notes, viewpoints and food stops along your journey.",
                        actionText = "Log First Moment",
                        onActionClick = { onAddMomentClick(currentTrip.id) }
                    )
                }
            } else {
                items(filteredMoments, key = { it.id }) { moment ->
                    MomentItemCard(
                        moment = moment,
                        onDelete = { viewModel.deleteMoment(moment.id, currentTrip.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.xxl))
            }
        }
    }

    // Delete Trip Dialog (Careful, permanent deletion with confirmation)
    if (showDeleteTripDialog) {
        TravelConfirmationDialog(
            title = "Delete this journey?",
            message = "This will permanently remove the trip, moments, checklist and local references associated with it. Any previously issued stamp number will never be reused.",
            confirmButtonText = "Delete Journey",
            isDestructive = true,
            onConfirm = {
                showDeleteTripDialog = false
                viewModel.deleteTrip(currentTrip.id)
                onNavigateBack()
            },
            onDismiss = { showDeleteTripDialog = false }
        )
    }

    // Edit Trip Dialog
    if (showEditTripDialog) {
        AlertDialog(
            onDismissRequest = { showEditTripDialog = false },
            title = {
                Text(
                    text = "Edit Trip Details",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it; editError = null },
                        label = { Text("Trip Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )

                    OutlinedTextField(
                        value = editDestination,
                        onValueChange = { editDestination = it; editError = null },
                        label = { Text("Destination *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )

                    // Date Field - Locked with lock icon for completed/stamped trips; editable via DatePicker for upcoming/in-progress trips
                    val isDateLocked = currentTrip.status == TripStatus.COMPLETED && (currentTrip.stampEarned || stamp != null)
                    if (isDateLocked) {
                        OutlinedTextField(
                            value = editDate,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Trip Date") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Official journey date locked",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            supportingText = {
                                Text(
                                    text = "Official journey date locked 🔒 Use 'Correct Journey Date' in menu to change.",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_trip_date_locked_input"),
                            shape = RoundedCornerShape(10.dp)
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = editDate,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Date *") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "Select Date",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_trip_date_input"),
                                shape = RoundedCornerShape(10.dp)
                            )
                            // Click overlay to trigger DatePicker dialog without soft keyboard
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showDatePickerDialog = true }
                                    .testTag("edit_trip_date_picker_button")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editPeopleCount,
                        onValueChange = { editPeopleCount = it.filter { char -> char.isDigit() } },
                        label = { Text("Number of People") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Short Description") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )

                    if (editError != null) {
                        Text(
                            text = editError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isBlank()) {
                            editError = "Trip name cannot be empty"
                            return@Button
                        }
                        if (editDestination.isBlank()) {
                            editError = "Destination cannot be empty"
                            return@Button
                        }
                        val count = editPeopleCount.toIntOrNull() ?: currentTrip.peopleCount
                        viewModel.updateTrip(
                            tripId = currentTrip.id,
                            name = editName.trim(),
                            destination = editDestination.trim(),
                            date = editDate.trim(),
                            peopleCount = count,
                            description = editDescription.trim(),
                            onUpdated = {
                                showEditTripDialog = false
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTripDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Material 3 Calendar DatePickerDialog for Edit Trip
    if (showDatePickerDialog) {
        val initialLocalDate = remember(editDate) {
            DateUtils.parseTripDate(editDate) ?: DateUtils.getTodayLocalDate()
        }
        val initialUtcMillis = remember(initialLocalDate) {
            initialLocalDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialUtcMillis
        )

        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedLocalDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            editDate = selectedLocalDate.format(
                                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
                            )
                            editError = null
                        }
                        showDatePickerDialog = false
                    },
                    modifier = Modifier.testTag("date_picker_confirm_button")
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePickerDialog = false },
                    modifier = Modifier.testTag("date_picker_cancel_button")
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(18.dp)
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = "Select date",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }

    // GITHUB ISSUE #5: Correct Journey Date Dialog for Completed/Stamped Trips
    if (showCorrectDateDialog) {
        AlertDialog(
            onDismissRequest = { showCorrectDateDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Correct Journey Date?",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "This date is part of your official Travel Stamp record. Correcting it will update both the journey record and its official stamp.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Text(
                                text = "Current Official Date:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currentTrip.date,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Proposed Corrected Date:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCorrectDatePickerDialog = true }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = proposedCorrectDate.ifBlank { "Select new date" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Pick date",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "The permanent stamp number (${stamp?.stampCode ?: "#---"}) and issuance identity will remain unchanged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (correctDateError != null) {
                        Text(
                            text = correctDateError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (proposedCorrectDate.isBlank()) {
                            correctDateError = "Please select a valid date"
                            return@Button
                        }
                        if (DateUtils.isFutureDate(proposedCorrectDate)) {
                            correctDateError = "A completed journey date cannot be set in the future"
                            return@Button
                        }
                        viewModel.correctOfficialJourneyDate(
                            tripId = currentTrip.id,
                            newDate = proposedCorrectDate.trim(),
                            onSuccess = {
                                showCorrectDateDialog = false
                            },
                            onError = { errorMsg ->
                                correctDateError = errorMsg
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("confirm_correct_date_button")
                ) {
                    Text("Confirm Correction")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCorrectDateDialog = false },
                    modifier = Modifier.testTag("cancel_correct_date_button")
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Material 3 DatePickerDialog for Correct Journey Date
    if (showCorrectDatePickerDialog) {
        val initialLocalDate = remember(proposedCorrectDate) {
            DateUtils.parseTripDate(proposedCorrectDate) ?: DateUtils.getTodayLocalDate()
        }
        val initialUtcMillis = remember(initialLocalDate) {
            initialLocalDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val correctDatePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialUtcMillis
        )

        DatePickerDialog(
            onDismissRequest = { showCorrectDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        correctDatePickerState.selectedDateMillis?.let { millis ->
                            val selectedLocalDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            proposedCorrectDate = selectedLocalDate.format(
                                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
                            )
                            correctDateError = null
                        }
                        showCorrectDatePickerDialog = false
                    },
                    modifier = Modifier.testTag("correct_date_picker_confirm_button")
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCorrectDatePickerDialog = false },
                    modifier = Modifier.testTag("correct_date_picker_cancel_button")
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(18.dp)
        ) {
            DatePicker(
                state = correctDatePickerState,
                title = {
                    Text(
                        text = "Select corrected journey date",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}
