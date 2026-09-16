package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LocationSearchCandidate
import com.example.ui.components.Spacing
import com.example.ui.theme.ForestPine
import com.example.ui.viewmodel.LocationSearchUiState
import com.example.ui.viewmodel.TravelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSearchScreen(
    tripId: Long,
    locationId: Long?,
    viewModel: TravelViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchUiState by viewModel.searchUiState.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isLocationOperationProcessing.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // For explicit user confirmation dialog
    var selectedCandidate by remember { mutableStateOf<LocationSearchCandidate?>(null) }
    var operationError by remember { mutableStateOf<String?>(null) }

    // Pre-populate search query if editing
    LaunchedEffect(locationId) {
        if (locationId != null) {
            val locs = viewModel.currentTripLocations.value
            val currentLoc = locs.find { it.id == locationId }
            if (currentLoc != null) {
                searchQuery = currentLoc.label
            }
        }
    }

    // Clear state on disposal to avoid leaking state
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSearchState()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (locationId == null) "ADD MAP LOCATION" else "EDIT MAP LOCATION",
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("location_search_back_button")
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Search Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search for a place...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            viewModel.searchLocations(searchQuery)
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("location_search_input"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.searchLocations(searchQuery)
                    },
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .size(56.dp)
                        .testTag("location_search_submit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Results or States
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopStart
            ) {
                when (val state = searchUiState) {
                    is LocationSearchUiState.Idle -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Search for forts, peaks, or cities in India",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("location_search_idle_text")
                            )
                        }
                    }
                    is LocationSearchUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.testTag("location_search_progress")
                            )
                        }
                    }
                    is LocationSearchUiState.Success -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("location_candidates_list"),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            items(state.candidates) { candidate ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCandidate = candidate
                                        }
                                        .testTag("candidate_item_${candidate.label.replace(" ", "_")}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.cardPadding)
                                    ) {
                                        Text(
                                            text = candidate.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        candidate.secondaryLabel?.let { secondary ->
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = secondary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(Spacing.lg))
                                Text(
                                    text = "Data from OpenStreetMap via Geoapify",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.xs)
                                        .testTag("geoapify_attribution_text"),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    is LocationSearchUiState.NoResults -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No places found.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("no_results_title")
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Try another spelling or search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("no_results_subtitle")
                            )
                        }
                    }
                    is LocationSearchUiState.NoNetwork -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "You're offline.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("no_network_title")
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Connect to the internet to search for a location.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("no_network_subtitle")
                            )
                        }
                    }
                    is LocationSearchUiState.Timeout -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Search took too long.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("timeout_title")
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Button(
                                onClick = { viewModel.searchLocations(searchQuery) },
                                modifier = Modifier.testTag("timeout_retry_button")
                            ) {
                                Text("Try again")
                            }
                        }
                    }
                    is LocationSearchUiState.RateLimited -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Location search is temporarily busy.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("rate_limit_title")
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Try again shortly.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("rate_limit_subtitle")
                            )
                        }
                    }
                    is LocationSearchUiState.ProviderUnavailable -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Location search isn't available right now.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("provider_unavailable_title")
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Button(
                                onClick = { viewModel.searchLocations(searchQuery) },
                                modifier = Modifier.testTag("provider_unavailable_retry_button")
                            ) {
                                Text("Try again")
                            }
                        }
                    }
                    is LocationSearchUiState.InvalidQuery -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Please enter a valid search term.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("invalid_query_text")
                            )
                        }
                    }
                    is LocationSearchUiState.UnknownError -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "An unexpected error occurred.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("unknown_error_title")
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Please try again.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("unknown_error_subtitle")
                            )
                        }
                    }
                }
            }
        }

        // Error message Toast/Dialog if persistence fails
        operationError?.let { err ->
            AlertDialog(
                onDismissRequest = { operationError = null },
                title = { Text("Error") },
                text = { Text(err) },
                confirmButton = {
                    TextButton(
                        onClick = { operationError = null },
                        modifier = Modifier.testTag("dismiss_operation_error_button")
                    ) {
                        Text("OK")
                    }
                },
                modifier = Modifier.testTag("operation_error_dialog")
            )
        }

        // Explicit User Confirmation dialog
        selectedCandidate?.let { candidate ->
            AlertDialog(
                onDismissRequest = { selectedCandidate = null },
                title = {
                    Text(text = if (locationId == null) "Add this location?" else "Replace with this location?")
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = candidate.label,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        candidate.secondaryLabel?.let { secondary ->
                            Text(
                                text = secondary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "This location will be added to your journey.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (locationId == null) {
                                viewModel.addJourneyLocation(
                                    tripId = tripId,
                                    candidate = candidate,
                                    onSuccess = {
                                        selectedCandidate = null
                                        onNavigateBack()
                                    },
                                    onError = { err ->
                                        selectedCandidate = null
                                        operationError = err
                                    }
                                )
                            } else {
                                viewModel.updateJourneyLocation(
                                    locationId = locationId,
                                    candidate = candidate,
                                    onSuccess = {
                                        selectedCandidate = null
                                        onNavigateBack()
                                    },
                                    onError = { err ->
                                        selectedCandidate = null
                                        operationError = err
                                    }
                                )
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("confirm_add_location_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(text = if (locationId == null) "Add Location" else "Update Location")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { selectedCandidate = null },
                        modifier = Modifier.testTag("cancel_add_location_button")
                    ) {
                        Text("Cancel")
                    }
                },
                modifier = Modifier.testTag("confirm_location_dialog")
            )
        }
    }
}
