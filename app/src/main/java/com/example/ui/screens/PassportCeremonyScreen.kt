package com.example.ui.screens

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.TravelStamp
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.ui.components.Spacing
import com.example.ui.components.TravelStampView
import com.example.ui.theme.ForestPine
import com.example.ui.theme.OchreGold
import com.example.ui.theme.SandCanvasLight
import com.example.ui.theme.Terracotta
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.delay

/**
 * UI-only presentation phase for the Passport Ceremony animation.
 * Strictly non-persistent and non-transactional.
 */
enum class PassportCeremonyPhase {
    ENTRY,
    PRE_PRESS,
    IMPACT,
    RESOLVE,
    COMPLETE
}

/**
 * Ensures that if configuration recreation occurs, the ceremony phase always restores as COMPLETE.
 */
val CeremonyPhaseSaver: Saver<PassportCeremonyPhase, String> = Saver(
    save = { PassportCeremonyPhase.COMPLETE.name },
    restore = { PassportCeremonyPhase.COMPLETE }
)

/**
 * Calculates a deterministic, stable small tilt angle from the stamp's identity.
 * Bounded between -1.8° and +1.8° without calling Random() or mutating on recomposition.
 */
fun calculateDeterministicTilt(stampId: Long, stampNumber: Long): Float {
    val seed = (stampId * 31 + stampNumber).toInt()
    val mod = ((seed % 36) + 36) % 36 // Strictly positive 0..35
    return (mod - 18) * 0.1f // Strictly -1.8f .. 1.7f
}

/**
 * Pure trigger policy for ceremony impact haptic.
 * Returns true only on the first transition into IMPACT phase.
 */
fun shouldTriggerImpactHaptic(hasFired: Boolean, currentPhase: PassportCeremonyPhase): Boolean {
    return !hasFired && currentPhase == PassportCeremonyPhase.IMPACT
}

/**
 * PassportCeremonyScreen presents the official travel stamp earned by completing a journey.
 *
 * SAFETY INVARIANT:
 * This screen is purely presentational. The completion transaction and TravelStamp issuance
 * have already succeeded and committed atomically in Room before this destination is loaded.
 * Under no circumstances does this screen allocate stamp numbers or write to Room.
 */
@Composable
fun PassportCeremonyScreen(
    tripId: Long,
    viewModel: TravelViewModel,
    onViewInPassport: () -> Unit,
    onCreateStampEdition: (Long) -> Unit,
    onExpeditionLog: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTrip by viewModel.currentTrip.collectAsStateWithLifecycle()
    val currentStamp by viewModel.currentTripStamp.collectAsStateWithLifecycle()

    // Ensure ViewModel observes this tripId
    LaunchedEffect(tripId) {
        if (viewModel.selectedTripId.value != tripId) {
            viewModel.selectTrip(tripId)
        }
    }

    PassportCeremonyContent(
        tripId = tripId,
        trip = currentTrip,
        stamp = currentStamp,
        onViewInPassport = onViewInPassport,
        onCreateStampEdition = onCreateStampEdition,
        onExpeditionLog = onExpeditionLog,
        modifier = modifier
    )
}

@Composable
fun PassportCeremonyContent(
    tripId: Long,
    trip: Trip?,
    stamp: TravelStamp?,
    onViewInPassport: () -> Unit,
    onCreateStampEdition: (Long) -> Unit,
    onExpeditionLog: (Long) -> Unit,
    modifier: Modifier = Modifier,
    initialPhase: PassportCeremonyPhase? = null,
    isMissingDataError: Boolean = false
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Detect if system animations are disabled
    val animatorsEnabled = remember {
        try {
            ValueAnimator.areAnimatorsEnabled()
        } catch (_: Throwable) {
            true
        }
    }

    // UI-only presentation state. Configuration changes or prior completion retain COMPLETE.
    var ceremonyPhase by rememberSaveable(stateSaver = CeremonyPhaseSaver) {
        mutableStateOf(
            initialPhase ?: if (!animatorsEnabled) PassportCeremonyPhase.COMPLETE else PassportCeremonyPhase.ENTRY
        )
    }

    // Haptic latch to guarantee single-fire across recompositions and config changes
    var hasFiredHaptic by rememberSaveable { mutableStateOf(false) }

    // Intercept back button:
    // If still in pre-complete animation -> snap to COMPLETE
    // If COMPLETE -> safely navigate to Passport
    BackHandler(enabled = true) {
        if (ceremonyPhase != PassportCeremonyPhase.COMPLETE) {
            ceremonyPhase = PassportCeremonyPhase.COMPLETE
        } else {
            onViewInPassport()
        }
    }

    // Background / Pause handler:
    // If app leaves foreground mid-ceremony, complete immediately to avoid looping on return
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (ceremonyPhase != PassportCeremonyPhase.COMPLETE) {
                    ceremonyPhase = PassportCeremonyPhase.COMPLETE
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Animation Drivers
    val isInitiallyComplete = (initialPhase ?: if (!animatorsEnabled) PassportCeremonyPhase.COMPLETE else null) == PassportCeremonyPhase.COMPLETE
    val surfaceAlpha = remember { Animatable(if (isInitiallyComplete) 1f else 0f) }
    val surfaceScale = remember { Animatable(if (isInitiallyComplete) 1f else 0.96f) }
    val stampAlpha = remember { Animatable(if (isInitiallyComplete) 1f else 0f) }
    val stampScale = remember { Animatable(if (isInitiallyComplete) 1f else 1.10f) }
    val stampElevation = remember { Animatable(if (isInitiallyComplete) 0f else 8f) }

    // Synchronize animatable values if snapped to COMPLETE
    LaunchedEffect(ceremonyPhase) {
        if (ceremonyPhase == PassportCeremonyPhase.COMPLETE) {
            surfaceAlpha.snapTo(1f)
            surfaceScale.snapTo(1f)
            stampAlpha.snapTo(1f)
            stampScale.snapTo(1f)
            stampElevation.snapTo(0f)
        }
    }

    val isAlreadyComplete = ceremonyPhase == PassportCeremonyPhase.COMPLETE

    // Master Timeline orchestration (~1,650 ms total)
    LaunchedEffect(animatorsEnabled, stamp?.id, isAlreadyComplete) {
        if (isAlreadyComplete || !animatorsEnabled || stamp == null) {
            ceremonyPhase = PassportCeremonyPhase.COMPLETE
            return@LaunchedEffect
        }

        // Phase 0: ENTRY (0 - 250ms)
        ceremonyPhase = PassportCeremonyPhase.ENTRY
        surfaceAlpha.animateTo(1f, animationSpec = tween(250, easing = FastOutSlowInEasing))
        surfaceScale.animateTo(1.0f, animationSpec = tween(250, easing = FastOutSlowInEasing))

        // Phase 1: PRE_PRESS (250 - 550ms)
        ceremonyPhase = PassportCeremonyPhase.PRE_PRESS
        stampAlpha.animateTo(0.85f, animationSpec = tween(200, easing = LinearOutSlowInEasing))
        stampElevation.animateTo(8f, animationSpec = tween(300, easing = LinearOutSlowInEasing))
        delay(100)

        // Phase 2: IMPACT (550 - 700ms)
        ceremonyPhase = PassportCeremonyPhase.IMPACT
        if (shouldTriggerImpactHaptic(hasFiredHaptic, ceremonyPhase)) {
            hasFiredHaptic = true
            try {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Throwable) {}
        }
        stampElevation.animateTo(0f, animationSpec = tween(120, easing = FastOutSlowInEasing))
        stampScale.animateTo(0.98f, animationSpec = tween(120, easing = FastOutSlowInEasing))
        stampScale.animateTo(1.00f, animationSpec = tween(50, easing = FastOutSlowInEasing))

        // Phase 3: RESOLVE (700 - 1250ms)
        ceremonyPhase = PassportCeremonyPhase.RESOLVE
        stampAlpha.animateTo(1.0f, animationSpec = tween(450, easing = FastOutSlowInEasing))
        delay(100)

        // Phase 4: COMPLETE (1250 - 1650ms)
        ceremonyPhase = PassportCeremonyPhase.COMPLETE
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            // Loading State (waiting for trip/stamp observation) or Missing Data Error
            val isCompletedTripMissingStamp = trip != null && trip.status == TripStatus.COMPLETED && stamp == null
            val isImmediateError = isMissingDataError || isCompletedTripMissingStamp
            if (trip == null || stamp == null) {
                var showTimeoutError by rememberSaveable(tripId, isImmediateError) { mutableStateOf(isImmediateError) }
                LaunchedEffect(tripId, isImmediateError) {
                    if (!showTimeoutError) {
                        delay(2000)
                        showTimeoutError = true
                    }
                }

                if (showTimeoutError) {
                    CeremonyMissingStampError(
                        onNavigateBack = onViewInPassport
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg)
                    ) {
                        CircularProgressIndicator(
                            color = ForestPine,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text(
                            text = stringResource(R.string.ceremony_loading_journey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Scaffold
            }

            // Normal Stamped Ceremony Presentation
            val isComplete = ceremonyPhase == PassportCeremonyPhase.COMPLETE
            val tiltAngle = remember(stamp.id, stamp.stampNumber) {
                calculateDeterministicTilt(stamp.id, stamp.stampNumber)
            }

            val semanticAnnouncement = stringResource(
                R.string.ceremony_accessibility_announcement,
                stamp.stampCode,
                trip.destination.ifBlank { trip.name }
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md)
                    .widthIn(max = 520.dp)
            ) {
                // Top Contextual Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.ceremony_journey_complete),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = ForestPine,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Center: Passport Booklet Parchment Surface
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(surfaceAlpha.value)
                        .scale(surfaceScale.value),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SandCanvasLight
                    ),
                    border = BorderStroke(1.2.dp, OchreGold.copy(alpha = 0.45f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.xl)
                    ) {
                        // Official Stamp with Stamping Motion
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .testTag("ceremony_stamp")
                                .shadow(
                                    elevation = stampElevation.value.dp,
                                    shape = RoundedCornerShape(115.dp),
                                    clip = false
                                )
                                .alpha(stampAlpha.value)
                                .scale(stampScale.value)
                                .then(
                                    if (isComplete) {
                                        Modifier.semantics {
                                            contentDescription = semanticAnnouncement
                                        }
                                    } else {
                                        Modifier.clearAndSetSemantics {}
                                    }
                                )
                        ) {
                            TravelStampView(
                                stamp = stamp,
                                size = 210.dp,
                                rotation = tiltAngle
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.lg))

                        // Official Stamp Number
                        AnimatedVisibility(
                            visible = isComplete,
                            enter = fadeIn(animationSpec = tween(300))
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.ceremony_official_stamp, stamp.stampCode),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = Terracotta,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Destination Name
                                Text(
                                    text = trip.destination.ifBlank { trip.name },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryLight,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                // Journey Date
                                Text(
                                    text = trip.date,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(Spacing.md))

                                // Added to Passport Badge
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ForestPine.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, ForestPine.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .testTag("ceremony_added_to_passport")
                                        .semantics {
                                            liveRegion = LiveRegionMode.Polite
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = ForestPine,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.ceremony_added_to_passport),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp,
                                            color = ForestPine
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Bottom Actions
                AnimatedVisibility(
                    visible = isComplete,
                    enter = fadeIn(animationSpec = tween(350))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Primary: VIEW IN PASSPORT
                        Button(
                            onClick = onViewInPassport,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForestPine,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("ceremony_view_passport_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = stringResource(R.string.ceremony_view_in_passport),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Secondary: CREATE STAMP EDITION
                        OutlinedButton(
                            onClick = { onCreateStampEdition(tripId) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, Terracotta),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Terracotta
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("ceremony_create_edition_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.IosShare,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = stringResource(R.string.ceremony_create_stamp_edition),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Tertiary: Expedition Log
                        TextButton(
                            onClick = { onExpeditionLog(tripId) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ceremony_trip_log_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.ceremony_expedition_log),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Safe presentation when a completed trip exists but the stamp could unexpectedly not be observed.
 * Under no circumstance does this screen invoke issuance or recovery writes.
 */
@Composable
private fun CeremonyMissingStampError(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.screenHorizontal)
            .testTag("ceremony_error_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Terracotta,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = stringResource(R.string.ceremony_error_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Text(
                text = stringResource(R.string.ceremony_error_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            Button(
                onClick = onNavigateBack,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestPine),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ceremony_error_return_button")
            ) {
                Text(
                    text = stringResource(R.string.ceremony_view_in_passport),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
