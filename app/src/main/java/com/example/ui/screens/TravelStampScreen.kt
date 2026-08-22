package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingView
import com.example.ui.components.Spacing
import com.example.ui.components.TravelOutlinedButton
import com.example.ui.components.TravelPrimaryButton
import com.example.ui.components.TravelStampCard
import com.example.ui.theme.ForestPine
import com.example.ui.theme.Terracotta
import com.example.ui.util.StampExporter
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelStampScreen(
    tripId: Long,
    viewModel: TravelViewModel,
    onNavigateBack: () -> Unit,
    onViewTripCard: () -> Unit,
    onCollectionClick: () -> Unit,
    onCreatePosterClick: (Long) -> Unit = {},
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

    var isSavingToGallery by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
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
    val firstPhotoUri = remember(moments) {
        moments.firstOrNull { !it.imageUri.isNullOrBlank() }?.imageUri
    }

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

            // 2. Large Travel Stamp Card (Hero Artwork)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(stampScale.value)
                        .alpha(stampAlpha.value)
                ) {
                    TravelStampCard(
                        stamp = currentStamp,
                        photoUri = firstPhotoUri,
                        elevation = 6.dp
                    )
                }
            }

            // 3. Primary Actions: SAVE TO GALLERY, SHARE STAMP, VIEW JOURNEY
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // CREATE 9:16 STORY POSTER Button
                    TravelPrimaryButton(
                        text = "CREATE 9:16 STORY POSTER",
                        icon = Icons.Default.AutoAwesome,
                        onClick = { onCreatePosterClick(currentStamp.tripId) },
                        testTag = "create_poster_button"
                    )

                    // SAVE TO GALLERY Button
                    TravelOutlinedButton(
                        text = "SAVE STAMP TO GALLERY",
                        icon = Icons.Default.Download,
                        isLoading = isSavingToGallery,
                        onClick = {
                            if (isSavingToGallery) return@TravelOutlinedButton
                            isSavingToGallery = true
                            coroutineScope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    val bitmap = StampExporter.createStampBitmap(
                                        context = context,
                                        stamp = currentStamp,
                                        photoUri = firstPhotoUri
                                    )
                                    val saved = StampExporter.saveToGallery(context, bitmap, currentStamp)
                                    try {
                                        bitmap.recycle()
                                    } catch (_: Exception) {}
                                    saved
                                }
                                isSavingToGallery = false
                                if (success) {
                                    snackbarHostState.showSnackbar("Stamp saved to Gallery ✓")
                                } else {
                                    snackbarHostState.showSnackbar("Couldn't save stamp. Please try again.")
                                }
                            }
                        },
                        testTag = "save_to_gallery_button"
                    )

                    // SHARE STAMP Button (Shares Image with FileProvider)
                    TravelOutlinedButton(
                        text = "SHARE STAMP",
                        icon = Icons.Default.Share,
                        isLoading = isSharing,
                        onClick = {
                            if (isSharing) return@TravelOutlinedButton
                            isSharing = true
                            coroutineScope.launch {
                                val shareUri = withContext(Dispatchers.IO) {
                                    val bitmap = StampExporter.createStampBitmap(
                                        context = context,
                                        stamp = currentStamp,
                                        photoUri = firstPhotoUri
                                    )
                                    val uri = StampExporter.getShareableUri(context, bitmap, currentStamp)
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
                                            "Check out my Travel Stamp for ${currentStamp.title} (${currentStamp.stampCode})! 🏔️✨"
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share Travel Stamp")
                                    )
                                } else {
                                    snackbarHostState.showSnackbar("Unable to prepare stamp for sharing.")
                                }
                            }
                        },
                        testTag = "share_stamp_button"
                    )

                    // VIEW EXPEDITION LOG Button
                    TravelOutlinedButton(
                        text = "VIEW EXPEDITION LOG",
                        icon = Icons.Default.Hiking,
                        onClick = onViewTripCard,
                        testTag = "view_trip_log_button"
                    )
                }
            }

            // 4. Expedition Memories & Timeline Section
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "⏱️", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = "EXPEDITION TIMELINE (${moments.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }

                        if (moments.isEmpty()) {
                            Text(
                                text = "No moments recorded yet. You can continue adding trail notes, milestones, and photos to this completed journey anytime.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                moments.take(3).forEach { moment ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .padding(Spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = moment.category.emoji, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(Spacing.sm))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = moment.category.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (moment.note.isNotBlank()) {
                                                Text(
                                                    text = moment.note,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        TravelOutlinedButton(
                            text = "VIEW TIMELINE & ADD MEMORIES",
                            icon = Icons.Default.Hiking,
                            onClick = onViewTripCard,
                            testTag = "view_timeline_button"
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}
