package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.ui.components.Spacing
import com.example.ui.components.TravelOutlinedButton
import com.example.ui.components.TravelPrimaryButton
import com.example.ui.components.TravelStampView
import com.example.ui.components.parseInkColor
import com.example.ui.poster.PosterExporter
import com.example.ui.poster.PosterRenderConfig
import com.example.ui.poster.PosterTemplate
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
            delay(1000)
            isInitialLoading = false
        }
    }

    if (stamp == null || trip == null) {
        if (isInitialLoading) {
            LoadingView(
                message = "Preparing poster canvas...",
                testTag = "poster_loading_view"
            )
        } else {
            ErrorStateView(
                title = "Official Stamp Required",
                message = "Story posters are generated exclusively for completed journeys that have an official Travel Stamp.",
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

    // Moment photos available for this trip
    val photoMoments = remember(moments) {
        moments.filter { !it.imageUri.isNullOrBlank() }
    }

    // Interactive State
    var selectedTemplate by remember { mutableStateOf(PosterTemplate.PHOTO_STAMP) }
    var selectedPhotoUri by remember(photoMoments) {
        mutableStateOf(photoMoments.firstOrNull()?.imageUri)
    }

    // Pan & Zoom parameters for Template A
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }

    // Export operation states (single active export guard)
    var isSavingToGallery by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    val isExportActive = isSavingToGallery || isSharing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "STORY POSTER (9:16)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "1080 × 1920 High Resolution",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
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
            // 1. Template Selector Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    FilterChip(
                        selected = selectedTemplate == PosterTemplate.PHOTO_STAMP,
                        onClick = { selectedTemplate = PosterTemplate.PHOTO_STAMP },
                        label = { Text("📸 Photo + Stamp") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("template_photo_stamp_chip"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestPine,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = selectedTemplate == PosterTemplate.PASSPORT_STAMP,
                        onClick = { selectedTemplate = PosterTemplate.PASSPORT_STAMP },
                        label = { Text("📜 Passport Stamp") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("template_passport_stamp_chip"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestPine,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // 2. Interactive 9:16 Poster Live Preview Canvas
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    PosterLivePreview(
                        trip = currentTrip,
                        stamp = currentStamp,
                        template = selectedTemplate,
                        photoUri = selectedPhotoUri,
                        panX = panX,
                        panY = panY,
                        zoom = zoom,
                        onTransform = { dPanX, dPanY, dZoom ->
                            if (selectedTemplate == PosterTemplate.PHOTO_STAMP && !selectedPhotoUri.isNullOrBlank()) {
                                zoom = (zoom * dZoom).coerceIn(1.0f, 3.0f)
                                panX = (panX + dPanX / 300f).coerceIn(-0.5f, 0.5f)
                                panY = (panY + dPanY / 300f).coerceIn(-0.5f, 0.5f)
                            }
                        }
                    )
                }
            }

            // 3. Pan / Zoom controls & photo selector for Template A
            if (selectedTemplate == PosterTemplate.PHOTO_STAMP) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SELECT JOURNEY PHOTO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )

                            if (zoom != 1f || panX != 0f || panY != 0f) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clickable {
                                            zoom = 1f
                                            panX = 0f
                                            panY = 0f
                                        }
                                        .testTag("reset_transform_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RestartAlt,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = ForestPine
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Reset Frame",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ForestPine
                                        )
                                    }
                                }
                            }
                        }

                        if (photoMoments.isEmpty()) {
                            // Branded Fallback Info Badge
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = ForestPine,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = "No Moment photos in this journey. A branded expedition backdrop is used.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // Photo carousel from this trip's moments
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
                                            .size(68.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) ForestPine else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                selectedPhotoUri = moment.imageUri
                                                panX = 0f
                                                panY = 0f
                                                zoom = 1f
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
                            Text(
                                text = "💡 Pinch to zoom, drag to pan within the 9:16 frame.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    // SAVE TO GALLERY BUTTON
                    TravelPrimaryButton(
                        text = "SAVE POSTER (1080×1920)",
                        icon = Icons.Default.Download,
                        isLoading = isSavingToGallery,
                        enabled = !isExportActive,
                        onClick = {
                            if (isExportActive) return@TravelPrimaryButton
                            isSavingToGallery = true
                            val config = PosterRenderConfig(
                                template = selectedTemplate,
                                photoUri = if (selectedTemplate == PosterTemplate.PHOTO_STAMP) selectedPhotoUri else null,
                                panX = panX,
                                panY = panY,
                                zoom = zoom
                            )

                            coroutineScope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    val bitmap = PosterExporter.createPosterBitmap(
                                        context = context,
                                        trip = currentTrip,
                                        stamp = currentStamp,
                                        config = config
                                    )
                                    val saved = PosterExporter.savePosterToGallery(context, bitmap, currentStamp)
                                    try {
                                        bitmap.recycle()
                                    } catch (_: Exception) {}
                                    saved
                                }
                                isSavingToGallery = false
                                if (success) {
                                    snackbarHostState.showSnackbar("Poster saved to Gallery (1080×1920) ✓")
                                } else {
                                    snackbarHostState.showSnackbar("Unable to save poster. Please try again.")
                                }
                            }
                        },
                        testTag = "save_poster_to_gallery_button"
                    )

                    // SHARE STORY POSTER BUTTON
                    TravelOutlinedButton(
                        text = "SHARE 9:16 STORY POSTER",
                        icon = Icons.Default.Share,
                        isLoading = isSharing,
                        enabled = !isExportActive,
                        onClick = {
                            if (isExportActive) return@TravelOutlinedButton
                            isSharing = true
                            val config = PosterRenderConfig(
                                template = selectedTemplate,
                                photoUri = if (selectedTemplate == PosterTemplate.PHOTO_STAMP) selectedPhotoUri else null,
                                panX = panX,
                                panY = panY,
                                zoom = zoom
                            )

                            coroutineScope.launch {
                                val shareUri = withContext(Dispatchers.IO) {
                                    val bitmap = PosterExporter.createPosterBitmap(
                                        context = context,
                                        trip = currentTrip,
                                        stamp = currentStamp,
                                        config = config
                                    )
                                    val uri = PosterExporter.getShareablePosterUri(context, bitmap, currentStamp)
                                    try {
                                        bitmap.recycle()
                                    } catch (_: Exception) {}
                                    uri
                                }
                                isSharing = false
                                if (shareUri != null) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Travel Stamp 9:16 Story Poster for ${currentStamp.title} (${currentStamp.stampCode})! 🏔️✨"
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share 9:16 Story Poster")
                                    )
                                } else {
                                    snackbarHostState.showSnackbar("Unable to prepare poster for sharing.")
                                }
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
 * Live 9:16 Preview composable replicating the high-res 1080x1920 layout faithfully in Compose.
 */
@Composable
private fun PosterLivePreview(
    trip: Trip,
    stamp: TravelStamp,
    template: PosterTemplate,
    photoUri: String?,
    panX: Float,
    panY: Float,
    zoom: Float,
    onTransform: (dPanX: Float, dPanY: Float, dZoom: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val inkColor = parseInkColor(stamp.inkColorHex, ForestPine)

    Card(
        modifier = modifier
            .fillMaxWidth(0.72f)
            .aspectRatio(9f / 16f)
            .shadow(12.dp, RoundedCornerShape(16.dp))
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

            if (template == PosterTemplate.PHOTO_STAMP) {
                // Template A: Photo + Stamp
                if (!photoUri.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(photoUri) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    onTransform(pan.x, pan.y, gestureZoom)
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
                    // Branded backdrop fallback
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1B332B), Color(0xFF14241F), Color(0xFF0C1512))
                                )
                            )
                    )
                }

                // Gradients for text contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.6f),
                                    0.18f to Color.Transparent,
                                    0.50f to Color.Transparent,
                                    0.75f to Color(0xFF0A100E).copy(alpha = 0.75f),
                                    1.0f to Color(0xFF0A100E).copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                // Content Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Branding
                    Text(
                        text = "TRAVEL STAMP • EXPEDITION POSTER",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )

                    // Center Stamp with Badge Backdrop
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SandCanvasLight.copy(alpha = 0.95f),
                            border = BorderStroke(1.dp, OchreGold.copy(alpha = 0.7f)),
                            modifier = Modifier.size(136.dp),
                            shadowElevation = 6.dp
                        ) {}

                        TravelStampView(
                            stamp = stamp,
                            size = 126.dp,
                            rotation = 0f
                        )
                    }

                    // Bottom Typography
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stamp.title,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (stamp.destination.isNotBlank()) {
                            Text(
                                text = stamp.destination.uppercase().replace(",", " •"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp,
                                color = Color(0xFFFFAB91),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "TRAVEL STAMP 🏔️ • OFFICIAL DIGITAL PASSPORT",
                            fontSize = 6.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            } else {
                // Template B: Passport / Stamp Focused
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
                            .padding(8.dp),
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
                                fontSize = 9.sp,
                                color = inkColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(1.dp)
                                    .background(OchreGold.copy(alpha = 0.5f))
                            )
                        }

                        // Center Hero Stamp
                        TravelStampView(
                            stamp = stamp,
                            size = 150.dp,
                            rotation = -1f
                        )

                        // Bottom Metadata
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stamp.title,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = inkColor,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (stamp.destination.isNotBlank()) {
                                Text(
                                    text = stamp.destination.uppercase().replace(",", " •"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = Terracotta,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "━◆ DATE: ${stamp.dateText.uppercase()} ◆━",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = inkColor.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.7f),
                                border = BorderStroke(0.8.dp, OchreGold.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "AUTHENTICATED: ${stamp.stampCode}",
                                    fontSize = 7.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = inkColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "TRAVEL STAMP 🏔️ • OFFICIAL EXPEDITION LOG",
                                fontSize = 6.sp,
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
