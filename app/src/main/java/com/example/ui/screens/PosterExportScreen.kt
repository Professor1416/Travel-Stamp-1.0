package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.TravelStamp
import com.example.data.model.Trip
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingView
import com.example.ui.components.PhotoStampBadgeView
import com.example.ui.components.Spacing
import com.example.ui.components.TravelOutlinedButton
import com.example.ui.components.TravelPrimaryButton
import com.example.ui.components.TravelStampView
import com.example.ui.components.parseInkColor
import com.example.ui.poster.PhotoStampLayout
import com.example.ui.poster.PosterExporter
import com.example.ui.poster.PosterRenderConfig
import com.example.ui.poster.PosterRenderResult
import com.example.ui.poster.PosterTemplate
import com.example.ui.poster.StampEditionFormat
import com.example.ui.poster.StampSize
import com.example.ui.util.PhotoUtils
import com.example.ui.theme.ForestPine
import com.example.ui.theme.OchreGold
import com.example.ui.theme.SandCanvasLight
import com.example.ui.theme.Terracotta
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosterExportScreen(
    tripId: Long,
    viewModel: TravelViewModel,
    onNavigateBack: () -> Unit,
    initialFormat: StampEditionFormat = StampEditionFormat.PORTRAIT,
    initialTemplate: PosterTemplate = PosterTemplate.PASSPORT_STAMP,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(tripId) {
        viewModel.selectTrip(tripId)
    }

    val trip by viewModel.currentTrip.collectAsStateWithLifecycle()
    val stamp by viewModel.currentTripStamp.collectAsStateWithLifecycle()
    val moments by viewModel.currentTripMoments.collectAsStateWithLifecycle()

    var isInitialLoading by remember(tripId) { mutableStateOf(true) }

    LaunchedEffect(tripId, stamp, trip) {
        if (stamp != null && trip != null) {
            isInitialLoading = false
        } else {
            delay(800)
            isInitialLoading = false
        }
    }

    if (stamp == null || trip == null) {
        if (isInitialLoading) {
            LoadingView(
                message = "Preparing stamp edition canvas...",
                testTag = "poster_loading_view"
            )
        } else {
            ErrorStateView(
                title = "Official Stamp Required",
                message = "Stamp editions are generated exclusively for completed journeys that have an official Travel Stamp.",
                retryAction = {
                    isInitialLoading = true
                    viewModel.selectTrip(tripId)
                },
                retryButtonText = "Retry",
                backAction = onNavigateBack,
                backButtonText = "Go Back",
                testTag = "poster_error_view"
            )
        }
        return
    }

    val currentTrip = trip!!
    val currentStamp = stamp!!

    // Format & Template selection state
    var selectedFormat by rememberSaveable(initialFormat) { mutableStateOf(initialFormat) }
    var selectedTemplate by rememberSaveable(initialTemplate) { mutableStateOf(initialTemplate) }

    // Moments belonging to current trip only
    val photoMoments = remember(moments, currentTrip.id) {
        moments.filter { it.tripId == currentTrip.id && !it.imageUri.isNullOrBlank() }
    }

    // Photo selection state (No automatic silent photo selection)
    var selectedPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }

    // Photo transform parameters (Zoom & Pan)
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }

    // Stamp layout parameters (Draggable position & Discrete size)
    var stampNormX by rememberSaveable { mutableFloatStateOf(0.5f) }
    var stampNormY by rememberSaveable { mutableFloatStateOf(0.44f) }
    var selectedStampSize by rememberSaveable { mutableStateOf(StampSize.MEDIUM) }

    // Export & Photo Preparation operation states
    var isSavingToGallery by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    var isPreparingPhoto by remember { mutableStateOf(false) }
    val isExportActive = isSavingToGallery || isSharing || isPreparingPhoto

    // Fingerprint snapshot of last successfully exported state
    var lastExportedFingerprint by rememberSaveable { mutableStateOf<String?>(null) }

    // Current state fingerprint for dirty tracking
    val currentPhotoStampFingerprint = remember(selectedPhotoUri, panX, panY, zoom, stampNormX, stampNormY, selectedStampSize, selectedFormat) {
        if (selectedPhotoUri == null && panX == 0f && panY == 0f && zoom == 1f && stampNormX == 0.5f && stampNormY == 0.44f && selectedStampSize == StampSize.MEDIUM) {
            null
        } else {
            "uri=${selectedPhotoUri}_fmt=${selectedFormat.name}_panX=$panX _panY=$panY _zoom=$zoom _sx=$stampNormX _sy=$stampNormY _sz=${selectedStampSize.name}"
        }
    }

    // Has unsaved/unexported edits
    val hasUnsavedPhotoEdits = remember(currentPhotoStampFingerprint, lastExportedFingerprint) {
        if (currentPhotoStampFingerprint == null) {
            false
        } else {
            currentPhotoStampFingerprint != lastExportedFingerprint
        }
    }

    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    // Handle Back action with confirmation if changes were made
    val handleBack = {
        if (selectedTemplate == PosterTemplate.PHOTO_STAMP && hasUnsavedPhotoEdits) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = selectedTemplate == PosterTemplate.PHOTO_STAMP && hasUnsavedPhotoEdits) {
        showDiscardDialog = true
    }

    // System Photo Picker launcher with canonical working image optimization
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isPreparingPhoto = true
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    PhotoUtils.prepareWorkingImage(context, uri)
                }
                isPreparingPhoto = false
                when (result) {
                    is PhotoUtils.WorkingImageResult.Success -> {
                        selectedPhotoUri = result.filePath
                        panX = 0f
                        panY = 0f
                        zoom = 1f
                    }
                    is PhotoUtils.WorkingImageResult.TooLarge -> {
                        snackbarHostState.showSnackbar("This photo is too large. Choose another image.")
                    }
                    is PhotoUtils.WorkingImageResult.UnsupportedFormat -> {
                        snackbarHostState.showSnackbar(result.message)
                    }
                    is PhotoUtils.WorkingImageResult.Error -> {
                        snackbarHostState.showSnackbar("Couldn’t prepare this photo. Choose another image.")
                    }
                }
            }
        }
    }

    val isPassportMode = selectedTemplate == PosterTemplate.PASSPORT_STAMP
    val topTitle = if (isPassportMode) "PASSPORT STAMP" else "PHOTO + STAMP"
    val topSubtitle = if (isPassportMode) {
        "${selectedFormat.title} • Classic Passport"
    } else {
        "${selectedFormat.title} • Photo Edition"
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(
                    text = "Discard changes?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Are you sure you want to discard your photo stamp edits?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onNavigateBack()
                    },
                    modifier = Modifier.testTag("discard_changes_button")
                ) {
                    Text(
                        text = "DISCARD",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false },
                    modifier = Modifier.testTag("keep_editing_button")
                ) {
                    Text(
                        text = "KEEP EDITING",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = topTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = topSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = handleBack,
                        modifier = Modifier.testTag("poster_back_button")
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Format Selector (Square / Portrait / Story)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = "FORMAT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("format_selection_row"),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        listOf(
                            StampEditionFormat.SQUARE,
                            StampEditionFormat.PORTRAIT,
                            StampEditionFormat.STORY
                        ).forEach { format ->
                            val isSelected = selectedFormat == format
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFormat = format },
                                label = {
                                    Text(
                                        text = format.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("format_chip_${format.title.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForestPine,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // 2. Responsive Live Preview Canvas (WYSIWYG with Pan, Zoom & Draggable Stamp)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    ResponsivePosterLivePreview(
                        trip = currentTrip,
                        stamp = currentStamp,
                        template = selectedTemplate,
                        format = selectedFormat,
                        photoUri = selectedPhotoUri,
                        panX = panX,
                        panY = panY,
                        zoom = zoom,
                        stampNormX = stampNormX,
                        stampNormY = stampNormY,
                        stampSize = selectedStampSize,
                        onPhotoTransform = { dPanX, dPanY, dZoom ->
                            if (selectedTemplate == PosterTemplate.PHOTO_STAMP && !selectedPhotoUri.isNullOrBlank()) {
                                zoom = (zoom * dZoom).coerceIn(1.0f, 3.5f)
                                panX = (panX + dPanX / 320f).coerceIn(-0.5f, 0.5f)
                                panY = (panY + dPanY / 320f).coerceIn(-0.5f, 0.5f)
                            }
                        },
                        onStampDrag = { dNormX, dNormY ->
                            if (selectedTemplate == PosterTemplate.PHOTO_STAMP) {
                                val (newX, newY) = PhotoStampLayout.clampStampPosition(
                                    stampNormX + dNormX,
                                    stampNormY + dNormY,
                                    selectedFormat,
                                    selectedStampSize
                                )
                                stampNormX = newX
                                stampNormY = newY
                            }
                        },
                        onSelectFromGallery = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }
            }

            // 3. Photo & Stamp Editor Controls (Only when PHOTO_STAMP is active)
            if (selectedTemplate == PosterTemplate.PHOTO_STAMP) {
                // Section A: Stamp Controls (Revealed ONLY after a photo is selected)
                if (!selectedPhotoUri.isNullOrBlank()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stamp_editor_controls_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.cardPadding),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                // Header: Stamp Size & Hint
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TouchApp,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "STAMP CONTROLS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.8.sp
                                        )
                                    }

                                    Text(
                                        text = "Drag stamp to position",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Discrete Stamp Size Options: Small, Medium, Large
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    StampSize.values().forEach { sizeOption ->
                                        val isSelected = selectedStampSize == sizeOption
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedStampSize = sizeOption },
                                            label = {
                                                Text(
                                                    text = sizeOption.title,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("stamp_size_${sizeOption.name.lowercase()}"),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = ForestPine,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }

                                // Reset Action Chips Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Reset Stamp Position & Size
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val (defX, defY) = PhotoStampLayout.clampStampPosition(
                                                    0.5f,
                                                    0.44f,
                                                    selectedFormat,
                                                    StampSize.MEDIUM
                                                )
                                                stampNormX = defX
                                                stampNormY = defY
                                                selectedStampSize = StampSize.MEDIUM
                                            }
                                            .testTag("reset_stamp_button")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestartAlt,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Reset Stamp",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Reset Photo Frame (Zoom & Pan)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                panX = 0f
                                                panY = 0f
                                                zoom = 1f
                                            }
                                            .testTag("reset_photo_framing_button")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestartAlt,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Reset Frame",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section B: Photo Selection (Progressive empty vs. populated state)
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        val isPhotoSelected = !selectedPhotoUri.isNullOrBlank()
                        val sectionTitle = if (isPhotoSelected) "CHOOSE PHOTO" else "SELECT A PHOTO"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sectionTitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )

                            // Pick from Device Gallery Button (Available in populated and unpopulated states)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ForestPine.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, ForestPine.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .testTag("choose_from_gallery_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = ForestPine
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "CHOOSE FROM GALLERY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ForestPine
                                    )
                                }
                            }
                        }

                        if (photoMoments.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.lg),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        tint = ForestPine,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = "No journey photos yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Choose a photo from your device to create this edition.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ForestPine,
                                        modifier = Modifier
                                            .clickable {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            }
                                            .testTag("choose_from_gallery_empty_button")
                                    ) {
                                        Text(
                                            text = "CHOOSE FROM GALLERY",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            if (!isPhotoSelected) {
                                Text(
                                    text = "From your journey moments:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("photo_selector_row")
                            ) {
                                itemsIndexed(photoMoments) { index, moment ->
                                    val isSelected = moment.imageUri == selectedPhotoUri
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) ForestPine else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                val momentUriStr = moment.imageUri
                                                if (!momentUriStr.isNullOrBlank()) {
                                                    isPreparingPhoto = true
                                                    coroutineScope.launch {
                                                        val uri = Uri.parse(momentUriStr)
                                                        val result = withContext(Dispatchers.IO) {
                                                            PhotoUtils.prepareWorkingImage(context, uri)
                                                        }
                                                        isPreparingPhoto = false
                                                        when (result) {
                                                            is PhotoUtils.WorkingImageResult.Success -> {
                                                                selectedPhotoUri = result.filePath
                                                                panX = 0f
                                                                panY = 0f
                                                                zoom = 1f
                                                            }
                                                            is PhotoUtils.WorkingImageResult.TooLarge -> {
                                                                snackbarHostState.showSnackbar("This photo is too large. Choose another image.")
                                                            }
                                                            is PhotoUtils.WorkingImageResult.UnsupportedFormat -> {
                                                                snackbarHostState.showSnackbar(result.message)
                                                            }
                                                            is PhotoUtils.WorkingImageResult.Error -> {
                                                                snackbarHostState.showSnackbar("Couldn’t open this journey photo. Choose another image.")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            .testTag("moment_photo_thumbnail_$index")
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(moment.imageUri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Moment Photo ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp)
                                                    .clip(CircleShape)
                                                    .background(ForestPine),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Primary Actions: SAVE TO GALLERY & SHARE
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    val isPhotoMissing = selectedTemplate == PosterTemplate.PHOTO_STAMP && selectedPhotoUri == null

                    // SAVE TO GALLERY BUTTON
                    TravelPrimaryButton(
                        text = "SAVE TO GALLERY",
                        icon = Icons.Default.Download,
                        isLoading = isSavingToGallery,
                        enabled = !isExportActive && !isPhotoMissing,
                        onClick = {
                            if (isExportActive) return@TravelPrimaryButton
                            if (isPhotoMissing) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Please select a photo first")
                                }
                                return@TravelPrimaryButton
                            }
                            isSavingToGallery = true
                            val config = PosterRenderConfig(
                                template = selectedTemplate,
                                format = selectedFormat,
                                photoUri = if (selectedTemplate == PosterTemplate.PHOTO_STAMP) selectedPhotoUri else null,
                                panX = panX,
                                panY = panY,
                                zoom = zoom,
                                stampSize = selectedStampSize,
                                stampPositionX = stampNormX,
                                stampPositionY = stampNormY
                            )

                            coroutineScope.launch {
                                val saveResult = withContext(Dispatchers.IO) {
                                    when (val renderResult = PosterExporter.createPosterBitmap(context, currentTrip, currentStamp, config)) {
                                        is PosterRenderResult.Success -> {
                                            val saved = PosterExporter.savePosterToGallery(
                                                context = context,
                                                bitmap = renderResult.bitmap,
                                                stamp = currentStamp,
                                                format = selectedFormat
                                            )
                                            try {
                                                renderResult.bitmap.recycle()
                                            } catch (_: Exception) {}
                                            if (saved) Result.success(Unit) else Result.failure(Exception("Failed to save to gallery"))
                                        }
                                        is PosterRenderResult.Failure -> {
                                            Result.failure(Exception(renderResult.reason))
                                        }
                                    }
                                }
                                isSavingToGallery = false
                                if (saveResult.isSuccess) {
                                    lastExportedFingerprint = currentPhotoStampFingerprint
                                    snackbarHostState.showSnackbar("Stamp saved to gallery")
                                } else {
                                    snackbarHostState.showSnackbar("Couldn’t export this photo stamp. Try selecting the photo again.")
                                }
                            }
                        },
                        testTag = "save_poster_to_gallery_button"
                    )

                    // SHARE BUTTON
                    TravelOutlinedButton(
                        text = "SHARE",
                        icon = Icons.Default.Share,
                        isLoading = isSharing,
                        enabled = !isExportActive && !isPhotoMissing,
                        onClick = {
                            if (isExportActive) return@TravelOutlinedButton
                            if (isPhotoMissing) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Please select a photo first")
                                }
                                return@TravelOutlinedButton
                            }
                            isSharing = true
                            val config = PosterRenderConfig(
                                template = selectedTemplate,
                                format = selectedFormat,
                                photoUri = if (selectedTemplate == PosterTemplate.PHOTO_STAMP) selectedPhotoUri else null,
                                panX = panX,
                                panY = panY,
                                zoom = zoom,
                                stampSize = selectedStampSize,
                                stampPositionX = stampNormX,
                                stampPositionY = stampNormY
                            )

                            coroutineScope.launch {
                                val shareResult = withContext(Dispatchers.IO) {
                                    when (val renderResult = PosterExporter.createPosterBitmap(context, currentTrip, currentStamp, config)) {
                                        is PosterRenderResult.Success -> {
                                            val uri = PosterExporter.getShareablePosterUri(
                                                context = context,
                                                bitmap = renderResult.bitmap,
                                                stamp = currentStamp,
                                                format = selectedFormat
                                            )
                                            try {
                                                renderResult.bitmap.recycle()
                                            } catch (_: Exception) {}
                                            if (uri != null) Result.success(uri) else Result.failure(Exception("Failed to prepare share URI"))
                                        }
                                        is PosterRenderResult.Failure -> {
                                            Result.failure(Exception(renderResult.reason))
                                        }
                                    }
                                }
                                isSharing = false
                                shareResult.fold(
                                    onSuccess = { shareUri ->
                                        lastExportedFingerprint = currentPhotoStampFingerprint
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Official Travel Stamp for ${currentStamp.title} (${currentStamp.stampCode})! 🏔️✨"
                                            )
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Share Travel Stamp")
                                        )
                                    },
                                    onFailure = {
                                        snackbarHostState.showSnackbar("Couldn’t prepare this photo stamp for sharing.")
                                    }
                                )
                            }
                        },
                        testTag = "share_poster_button"
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

/**
 * Responsive Live Preview composable adapting cleanly to Square (1:1), Portrait (3:4), and Story (9:16).
 * Replicates the exported bitmap layout faithfully with interactive pan/zoom and draggable stamp seal.
 */
@Composable
private fun ResponsivePosterLivePreview(
    trip: Trip,
    stamp: TravelStamp,
    template: PosterTemplate,
    format: StampEditionFormat,
    photoUri: String?,
    panX: Float,
    panY: Float,
    zoom: Float,
    stampNormX: Float,
    stampNormY: Float,
    stampSize: StampSize,
    onPhotoTransform: (dPanX: Float, dPanY: Float, dZoom: Float) -> Unit,
    onStampDrag: (dNormX: Float, dNormY: Float) -> Unit,
    onSelectFromGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val inkColor = parseInkColor(stamp.inkColorHex, ForestPine)

    val previewWidthFraction = when (format) {
        StampEditionFormat.SQUARE -> 0.82f
        StampEditionFormat.PORTRAIT -> 0.76f
        StampEditionFormat.STORY -> 0.70f
    }

    Card(
        modifier = modifier
            .fillMaxWidth(previewWidthFraction)
            .aspectRatio(format.aspectRatio)
            .shadow(10.dp, RoundedCornerShape(16.dp))
            .testTag("poster_live_preview_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (template == PosterTemplate.PASSPORT_STAMP) SandCanvasLight else Color(0xFF14241F)
        ),
        border = BorderStroke(1.5.dp, OchreGold.copy(alpha = 0.6f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val previewWidth = maxWidth
            val previewHeight = maxHeight
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()

            if (template == PosterTemplate.PHOTO_STAMP) {
                // Template A: Photo + Stamp
                if (!photoUri.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(photoUri) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    onPhotoTransform(pan.x, pan.y, gestureZoom)
                                }
                            }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(photoUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Poster Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = panX * previewWidth.toPx()
                                    translationY = panY * previewHeight.toPx()
                                }
                        )
                    }
                } else {
                    // Empty photo select state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1B332B), Color(0xFF14241F), Color(0xFF0C1512))
                                )
                            )
                            .clickable { onSelectFromGallery() }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = "Select a Photo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Tap here or choose from gallery below to preview your photo edition",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Gradients for text contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.55f),
                                    0.15f to Color.Transparent,
                                    (PhotoStampLayout.getFooterStartYRatio(format) - 0.22f).coerceAtLeast(0.40f) to Color.Transparent,
                                    (PhotoStampLayout.getFooterStartYRatio(format) - 0.05f) to Color(0xFF0A100E).copy(alpha = 0.75f),
                                    1.0f to Color(0xFF0A100E).copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                // Top Header Overlay
                Text(
                    text = "TRAVEL STAMP • EXPEDITION POSTER",
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )

                // Draggable Stamp Seal Overlay (Active when photo is selected)
                if (!photoUri.isNullOrBlank()) {
                    val stampRadiusPx = PhotoStampLayout.getStampRadiusPx(widthPx, stampSize)
                    val (clampedNormX, clampedNormY) = PhotoStampLayout.clampStampPosition(
                        stampNormX,
                        stampNormY,
                        format,
                        stampSize
                    )

                    val posX = (clampedNormX * widthPx) - stampRadiusPx
                    val posY = (clampedNormY * heightPx) - stampRadiusPx
                    val stampDiameterDp = with(LocalDensity.current) { (stampRadiusPx * 2f).toDp() }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
                            .size(stampDiameterDp)
                            .pointerInput(stampSize, format) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val dNormX = dragAmount.x / widthPx
                                    val dNormY = dragAmount.y / heightPx
                                    onStampDrag(dNormX, dNormY)
                                }
                            }
                            .testTag("draggable_stamp_seal"),
                        contentAlignment = Alignment.Center
                    ) {
                        PhotoStampBadgeView(
                            stampCode = stamp.stampCode,
                            stampNumber = stamp.stampNumber,
                            inkColorHex = stamp.inkColorHex,
                            size = stampDiameterDp
                        )
                    }
                }

                // Bottom Metadata Typography Overlay
                val footerStartYPx = heightPx * PhotoStampLayout.getFooterStartYRatio(format)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, footerStartYPx.roundToInt()) }
                        .padding(horizontal = 14.dp)
                ) {
                    Text(
                        text = stamp.title,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (stamp.destination.isNotBlank()) {
                        Text(
                            text = stamp.destination.uppercase().replace(",", " •"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            color = Color(0xFFFFAB91),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "📅 ${stamp.dateText.uppercase()} • ${stamp.stampCode}",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "TRAVEL STAMP 🏔️ • OFFICIAL DIGITAL PASSPORT",
                        fontSize = 6.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 0.8.sp
                    )
                }
            } else {
                // Template B: Responsive Passport / Stamp Focused
                val stampSize = when (format) {
                    StampEditionFormat.SQUARE -> 132.dp
                    StampEditionFormat.PORTRAIT -> 154.dp
                    StampEditionFormat.STORY -> 172.dp
                }

                val titleFontSize = when (format) {
                    StampEditionFormat.SQUARE -> 12.5.sp
                    StampEditionFormat.PORTRAIT -> 13.5.sp
                    StampEditionFormat.STORY -> 14.5.sp
                }

                val destFontSize = when (format) {
                    StampEditionFormat.SQUARE -> 8.5.sp
                    StampEditionFormat.PORTRAIT -> 9.sp
                    StampEditionFormat.STORY -> 9.5.sp
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .border(1.2.dp, OchreGold.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Passport Header
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "PASSPORT OF THE OPEN TRAIL",
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = OchreGold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "OFFICIAL EXPEDITION MEMORANDUM",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp,
                                color = inkColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .width(70.dp)
                                    .height(1.dp)
                                    .background(OchreGold.copy(alpha = 0.5f))
                            )
                        }

                        // Center Hero Stamp
                        TravelStampView(
                            stamp = stamp,
                            size = stampSize,
                            rotation = -1f
                        )

                        // Bottom Metadata (No text truncation bug)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stamp.title,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = titleFontSize,
                                color = inkColor,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (stamp.destination.isNotBlank()) {
                                Text(
                                    text = stamp.destination.uppercase().replace(",", " •"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = destFontSize,
                                    color = Terracotta,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "━◆ DATE: ${stamp.dateText.uppercase()} ◆━",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = inkColor.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "TRAVEL STAMP 🏔️ • OFFICIAL EXPEDITION LOG",
                                fontSize = 5.5.sp,
                                color = inkColor.copy(alpha = 0.6f),
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
