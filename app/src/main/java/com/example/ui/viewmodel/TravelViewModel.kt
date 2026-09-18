package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.TravelStampApp
import com.example.data.AppContainer
import com.example.data.local.AppThemeMode
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.ChecklistItem
import com.example.data.model.LocationSuggestion
import com.example.data.model.Moment
import com.example.data.model.MomentCategory
import com.example.data.model.MomentHyperlink
import com.example.data.model.HyperlinkUtils
import com.example.data.model.TravelStamp
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import com.example.data.model.TripStatus
import com.example.data.notification.ReminderCoordinator
import com.example.data.repository.ChecklistRepository
import com.example.data.repository.LocationSuggestionRepository
import com.example.data.repository.LocationSuggestionRepositoryImpl
import com.example.data.repository.MomentRepository
import com.example.data.repository.TravelStampRepository
import com.example.data.repository.TripRepository
import com.example.data.util.BackupExportResult
import com.example.data.util.BackupImportResult
import com.example.data.util.BackupManager
import com.example.data.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.data.model.JourneyLocation
import com.example.data.model.LocationSearchCandidate
import com.example.data.model.LocationSearchResult
import com.example.data.repository.JourneyLocationRepository
import com.example.data.repository.JourneyLocationRepositoryImpl
import com.example.data.repository.LocationSearchRepository
import java.io.File

sealed interface LocationSearchUiState {
    object Idle : LocationSearchUiState
    object Loading : LocationSearchUiState
    data class Success(val candidates: List<LocationSearchCandidate>) : LocationSearchUiState
    object NoResults : LocationSearchUiState
    object NoNetwork : LocationSearchUiState
    object Timeout : LocationSearchUiState
    object RateLimited : LocationSearchUiState
    object ProviderUnavailable : LocationSearchUiState
    object InvalidQuery : LocationSearchUiState
    data class UnknownError(val message: String?) : LocationSearchUiState
}

sealed interface FinishTripUiState {
    object Idle : FinishTripUiState
    object Loading : FinishTripUiState
    data class Success(val tripId: Long, val stamp: TravelStamp) : FinishTripUiState
    data class Error(val message: String) : FinishTripUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class TravelViewModel(
    private val tripRepository: TripRepository,
    private val checklistRepository: ChecklistRepository,
    private val momentRepository: MomentRepository,
    private val travelStampRepository: TravelStampRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val database: TravelStampDatabase,
    private val locationSuggestionRepository: LocationSuggestionRepository = LocationSuggestionRepositoryImpl(tripRepository),
    private val reminderCoordinator: ReminderCoordinator? = null,
    journeyLocationRepositoryParam: JourneyLocationRepository? = null,
    locationSearchRepositoryParam: LocationSearchRepository? = null
) : ViewModel() {

    private val journeyLocationRepository: JourneyLocationRepository =
        journeyLocationRepositoryParam ?: JourneyLocationRepositoryImpl(database.journeyLocationDao())

    private val locationSearchRepository: LocationSearchRepository? =
        locationSearchRepositoryParam

    // Search Location UX State
    private val _searchUiState = MutableStateFlow<LocationSearchUiState>(LocationSearchUiState.Idle)
    val searchUiState: StateFlow<LocationSearchUiState> = _searchUiState.asStateFlow()

    private var activeSearchJob: Job? = null

    // Location operation concurrency and spam guard state
    private val _isLocationOperationProcessing = MutableStateFlow(false)
    val isLocationOperationProcessing: StateFlow<Boolean> = _isLocationOperationProcessing.asStateFlow()

    fun searchLocations(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchUiState.value = LocationSearchUiState.InvalidQuery
            return
        }

        // Cancel previous search job to prevent duplicate/stale responses
        activeSearchJob?.cancel()

        _searchUiState.value = LocationSearchUiState.Loading

        activeSearchJob = viewModelScope.launch {
            try {
                val repo = locationSearchRepository
                if (repo == null) {
                    _searchUiState.value = LocationSearchUiState.ProviderUnavailable
                    return@launch
                }
                val result = repo.searchLocations(trimmed)
                // Map repository results directly to UI state
                _searchUiState.value = when (result) {
                    is LocationSearchResult.Success -> LocationSearchUiState.Success(result.candidates)
                    LocationSearchResult.NoResults -> LocationSearchUiState.NoResults
                    LocationSearchResult.NoNetwork -> LocationSearchUiState.NoNetwork
                    LocationSearchResult.Timeout -> LocationSearchUiState.Timeout
                    LocationSearchResult.RateLimited -> LocationSearchUiState.RateLimited
                    LocationSearchResult.ProviderUnavailable -> LocationSearchUiState.ProviderUnavailable
                    LocationSearchResult.InvalidQuery -> LocationSearchUiState.InvalidQuery
                    is LocationSearchResult.UnknownError -> LocationSearchUiState.UnknownError(result.message)
                }
            } catch (e: Exception) {
                _searchUiState.value = LocationSearchUiState.UnknownError(e.message)
            }
        }
    }

    fun clearSearchState() {
        activeSearchJob?.cancel()
        _searchUiState.value = LocationSearchUiState.Idle
    }

    fun addJourneyLocation(tripId: Long, candidate: LocationSearchCandidate, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_isLocationOperationProcessing.value) return
        _isLocationOperationProcessing.value = true
        viewModelScope.launch {
            try {
                val existingList = journeyLocationRepository.getLocationsForTripSync(tripId)
                val nextSortOrder = (existingList.maxOfOrNull { it.sortOrder } ?: 0) + 1
                val newLoc = JourneyLocation(
                    tripId = tripId,
                    label = candidate.label,
                    latitude = candidate.latitude,
                    longitude = candidate.longitude,
                    sortOrder = nextSortOrder
                )
                journeyLocationRepository.insertLocation(newLoc)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save location")
            } finally {
                _isLocationOperationProcessing.value = false
            }
        }
    }

    fun updateJourneyLocation(
        locationId: Long,
        candidate: LocationSearchCandidate,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isLocationOperationProcessing.value) return
        _isLocationOperationProcessing.value = true
        viewModelScope.launch {
            try {
                val allLocations = journeyLocationRepository.getAllLocationsSync()
                val existingLoc = allLocations.find { it.id == locationId }
                if (existingLoc != null) {
                    val updatedLoc = existingLoc.copy(
                        label = candidate.label,
                        latitude = candidate.latitude,
                        longitude = candidate.longitude,
                        updatedAt = System.currentTimeMillis()
                    )
                    journeyLocationRepository.updateLocation(updatedLoc)
                    onSuccess()
                } else {
                    onError("Location not found")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update location")
            } finally {
                _isLocationOperationProcessing.value = false
            }
        }
    }

    fun removeJourneyLocation(locationId: Long, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                journeyLocationRepository.deleteLocationById(locationId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete location")
            }
        }
    }

    val hasCompletedOnboarding: StateFlow<Boolean> = userPreferencesRepository.hasCompletedOnboarding
    val themeMode: StateFlow<AppThemeMode> = userPreferencesRepository.themeMode
    val preTripRemindersEnabled: StateFlow<Boolean> = userPreferencesRepository.preTripRemindersEnabled

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Location Suggestions State
    private val _locationSuggestions = MutableStateFlow<List<LocationSuggestion>>(emptyList())
    val locationSuggestions: StateFlow<List<LocationSuggestion>> = _locationSuggestions.asStateFlow()
    private var searchSuggestionsJob: Job? = null

    fun onTripNameQueryChanged(query: String) {
        searchSuggestionsJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _locationSuggestions.value = emptyList()
            return
        }

        searchSuggestionsJob = viewModelScope.launch {
            val results = locationSuggestionRepository.searchSuggestions(trimmed)
            _locationSuggestions.value = results
        }
    }

    fun clearLocationSuggestions() {
        searchSuggestionsJob?.cancel()
        _locationSuggestions.value = emptyList()
    }

    // Concurrency & state guards for trip completion
    private var finishTripJob: Job? = null
    private val finishTripMutex = Mutex()
    private val _finishTripUiState = MutableStateFlow<FinishTripUiState>(FinishTripUiState.Idle)
    val finishTripUiState: StateFlow<FinishTripUiState> = _finishTripUiState.asStateFlow()

    fun resetFinishTripState() {
        _finishTripUiState.value = FinishTripUiState.Idle
    }

    val allTrips: StateFlow<List<Trip>> = tripRepository.getAllTrips()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeTrips: StateFlow<List<Trip>> = tripRepository.getActiveTrips()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val upcomingExpeditions: StateFlow<List<Trip>> = activeTrips

    val completedTrips: StateFlow<List<Trip>> = tripRepository.getCompletedTrips()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentJourneys: StateFlow<List<Trip>> = completedTrips

    val stamps: StateFlow<List<TravelStamp>> = travelStampRepository.getAllStamps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalMomentsCount: StateFlow<Int> = momentRepository.getTotalMomentsCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val completedTripsCount: StateFlow<Int> = tripRepository.getCompletedTripsCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    // Current selected trip ID for details / moments / finish
    private val _selectedTripId = MutableStateFlow<Long?>(null)
    val selectedTripId: StateFlow<Long?> = _selectedTripId.asStateFlow()

    val currentTrip: StateFlow<Trip?> = _selectedTripId
        .flatMapLatest { id ->
            if (id != null) tripRepository.getTripById(id) else flowOf(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val currentTripChecklist: StateFlow<List<ChecklistItem>> = _selectedTripId
        .flatMapLatest { id ->
            if (id != null) checklistRepository.getItemsForTrip(id) else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentTripLocations: StateFlow<List<JourneyLocation>> = _selectedTripId
        .flatMapLatest { id ->
            if (id != null) journeyLocationRepository.observeLocationsForTrip(id) else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentTripMoments: StateFlow<List<Moment>> = _selectedTripId
        .flatMapLatest { id ->
            if (id != null) momentRepository.getMomentsForTrip(id) else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentTripStamp: StateFlow<TravelStamp?> = _selectedTripId
        .flatMapLatest { id ->
            if (id != null) travelStampRepository.getStampForTrip(id) else flowOf(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun completeOnboarding() {
        userPreferencesRepository.setOnboardingCompleted(true)
    }

    fun setThemeMode(mode: AppThemeMode) {
        userPreferencesRepository.setThemeMode(mode)
    }

    fun setPreTripRemindersEnabled(enabled: Boolean, onResult: ((Throwable?) -> Unit)? = null) {
        if (reminderCoordinator != null) {
            viewModelScope.launch {
                try {
                    reminderCoordinator.setGlobalRemindersEnabled(enabled)
                    onResult?.invoke(null)
                } catch (e: Exception) {
                    onResult?.invoke(e)
                }
            }
        } else {
            userPreferencesRepository.setPreTripRemindersEnabled(enabled)
            onResult?.invoke(null)
        }
    }

    fun selectTrip(tripId: Long?) {
        _selectedTripId.value = tripId
    }

    // Reminder Deep-Link Navigation State
    private val _pendingReminderTripId = MutableStateFlow<Long?>(null)
    val pendingReminderTripId: StateFlow<Long?> = _pendingReminderTripId.asStateFlow()

    fun onReminderNavigationRequested(tripId: Long) {
        if (tripId > 0L) {
            _pendingReminderTripId.value = tripId
        }
    }

    fun clearPendingReminderTripId() {
        _pendingReminderTripId.value = null
    }

    suspend fun validateTripForNavigation(tripId: Long): Boolean {
        if (tripId <= 0L) return false
        val trip = tripRepository.getTripByIdSync(tripId)
        return trip != null && trip.deletedAt == null
    }

    fun createTrip(
        name: String,
        destination: String,
        date: String,
        startTimeMinutes: Int? = null,
        peopleCount: Int,
        description: String,
        reminderEnabled: Boolean = false,
        reminderPreset: TripReminderPreset = TripReminderPreset.ONE_DAY_BEFORE,
        reminderTimeMinutes: Int? = null,
        onCreated: (Long) -> Unit
    ) {
        if (_isProcessing.value) return
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                val initialStatus = if (DateUtils.isFutureDate(date)) {
                    TripStatus.UPCOMING
                } else {
                    TripStatus.IN_PROGRESS
                }

                val validStartTime = startTimeMinutes?.takeIf { it in 0..1439 }
                val validReminderTime = reminderTimeMinutes?.takeIf { it in 0..1439 }

                val trip = Trip(
                    name = name.trim(),
                    destination = destination.trim(),
                    date = date.trim(),
                    startTimeMinutes = validStartTime,
                    peopleCount = if (peopleCount < 1) 1 else peopleCount,
                    description = description.trim(),
                    status = initialStatus,
                    stampEarned = false,
                    completedAt = null,
                    reminderEnabled = reminderEnabled,
                    reminderPreset = reminderPreset,
                    reminderTimeMinutes = validReminderTime,
                    createdAt = System.currentTimeMillis()
                )
                val newTripId = tripRepository.createTrip(trip)
                // Seed standard checklist items
                checklistRepository.seedDefaultItems(newTripId)
                _selectedTripId.value = newTripId
                onCreated(newTripId)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun updateTrip(trip: Trip, onUpdated: () -> Unit = {}) {
        viewModelScope.launch {
            tripRepository.updateTrip(trip.copy(updatedAt = System.currentTimeMillis()))
            onUpdated()
        }
    }

    fun updateTripDetails(
        tripId: Long,
        name: String,
        destination: String,
        date: String,
        startTimeMinutes: Int?,
        peopleCount: Int,
        description: String,
        reminderEnabled: Boolean,
        reminderPreset: TripReminderPreset,
        onUpdated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val existing = tripRepository.getTripByIdSync(tripId) ?: return@launch
            val updated = existing.copy(
                name = name.trim(),
                destination = destination.trim(),
                date = date.trim(),
                startTimeMinutes = startTimeMinutes?.takeIf { it in 0..1439 },
                peopleCount = maxOf(1, peopleCount),
                description = description.trim(),
                reminderEnabled = if (reminderEnabled) {
                    com.example.ui.reminder.ReminderFormValidator.validateReminderForm(
                        reminderEnabled = true,
                        tripDate = date,
                        startTimeMinutes = startTimeMinutes?.takeIf { it in 0..1439 },
                        preset = reminderPreset
                    ).isValid
                } else {
                    false
                },
                reminderPreset = reminderPreset,
                updatedAt = System.currentTimeMillis()
            )
            tripRepository.updateTrip(updated)
            onUpdated()
        }
    }

    fun toggleTripReminder(
        tripId: Long,
        enabled: Boolean,
        preset: TripReminderPreset = TripReminderPreset.ONE_DAY_BEFORE,
        now: java.time.Instant = java.time.Instant.now(),
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
        onResult: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val existing = tripRepository.getTripByIdSync(tripId) ?: run {
                onResult?.invoke(false)
                return@launch
            }
            if (enabled) {
                val validation = com.example.ui.reminder.ReminderFormValidator.validateReminderForm(
                    reminderEnabled = true,
                    tripDate = existing.date,
                    startTimeMinutes = existing.startTimeMinutes,
                    preset = preset,
                    now = now,
                    zoneId = zoneId
                )
                if (!validation.isValid) {
                    onResult?.invoke(false)
                    return@launch
                }
            }
            val updated = existing.copy(
                reminderEnabled = enabled,
                reminderPreset = preset,
                updatedAt = System.currentTimeMillis()
            )
            tripRepository.updateTrip(updated)
            onResult?.invoke(true)
        }
    }

    fun toggleChecklistItem(itemId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            checklistRepository.toggleItem(itemId, isCompleted)
        }
    }

    fun addCustomChecklistItem(tripId: Long, text: String) {
        viewModelScope.launch {
            checklistRepository.addCustomItem(tripId, text)
        }
    }

    fun deleteChecklistItem(itemId: Long) {
        viewModelScope.launch {
            checklistRepository.deleteItem(itemId)
        }
    }

    suspend fun getMomentByIdSync(momentId: Long): Moment? {
        return momentRepository.getMomentByIdSync(momentId)
    }

    fun addMoment(
        tripId: Long,
        category: MomentCategory,
        note: String,
        hyperlinks: List<com.example.data.model.MomentHyperlink> = emptyList(),
        imageUri: String?,
        onSaved: () -> Unit = {}
    ) {
        if (_isProcessing.value) return
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                val cleanedText = note.trim()
                val validLinks = HyperlinkUtils.cleanupAndDeduplicateSpans(hyperlinks, cleanedText.length)
                val moment = Moment(
                    tripId = tripId,
                    category = category,
                    note = cleanedText,
                    hyperlinks = validLinks,
                    imageUri = imageUri,
                    timestamp = System.currentTimeMillis()
                )
                momentRepository.addMoment(moment)
                travelStampRepository.updateStampMomentsCount(tripId)
                onSaved()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun updateMoment(
        momentId: Long,
        tripId: Long,
        category: MomentCategory,
        note: String,
        hyperlinks: List<com.example.data.model.MomentHyperlink> = emptyList(),
        imageUri: String?,
        onSaved: () -> Unit = {}
    ) {
        if (_isProcessing.value) return
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                val existing = momentRepository.getMomentByIdSync(momentId)
                val cleanedText = note.trim()
                val validLinks = HyperlinkUtils.cleanupAndDeduplicateSpans(hyperlinks, cleanedText.length)
                val updatedMoment = (existing ?: Moment(
                    id = momentId,
                    tripId = tripId,
                    timestamp = System.currentTimeMillis()
                )).copy(
                    id = momentId,
                    tripId = tripId,
                    category = category,
                    note = cleanedText,
                    hyperlinks = validLinks,
                    imageUri = imageUri,
                    updatedAt = System.currentTimeMillis()
                )
                momentRepository.updateMoment(updatedMoment)
                onSaved()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun deleteMoment(momentId: Long, tripId: Long? = null) {
        viewModelScope.launch {
            momentRepository.deleteMoment(momentId)
            val currentId = tripId ?: _selectedTripId.value
            if (currentId != null) {
                travelStampRepository.updateStampMomentsCount(currentId)
            }
        }
    }

    fun updateTrip(
        tripId: Long,
        name: String,
        destination: String,
        date: String,
        startTimeMinutes: Int? = null,
        peopleCount: Int,
        description: String,
        onUpdated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val existing = tripRepository.getTripByIdSync(tripId) ?: return@launch
            // For completed trips with official stamps, date cannot be casually modified here
            val targetDate = if (existing.status == TripStatus.COMPLETED && existing.stampEarned) {
                existing.date
            } else {
                date.trim()
            }

            val resolvedStatus = when {
                existing.status == TripStatus.COMPLETED -> TripStatus.COMPLETED
                DateUtils.isFutureDate(targetDate) -> TripStatus.UPCOMING
                else -> TripStatus.IN_PROGRESS
            }

            val validStartTime = startTimeMinutes?.takeIf { it in 0..1439 }

            val updated = existing.copy(
                name = name.trim(),
                destination = destination.trim(),
                date = targetDate,
                startTimeMinutes = validStartTime,
                peopleCount = if (peopleCount < 1) 1 else peopleCount,
                description = description.trim(),
                status = resolvedStatus,
                updatedAt = System.currentTimeMillis()
            )
            tripRepository.updateTrip(updated)
            onUpdated()
        }
    }

    /**
     * [GITHUB ISSUE #5 - CONTROLLED OFFICIAL JOURNEY DATE CORRECTION]:
     * Deliberate action to correct the official journey date on a completed/stamped trip.
     * Updates BOTH the Trip entity and the TravelStamp entity atomically.
     * Preserves stamp identity (number, UUID, code, style, ink color, sequence).
     */
    fun correctOfficialJourneyDate(
        tripId: Long,
        newDate: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = travelStampRepository.correctOfficialJourneyDate(tripId, newDate)
            result.fold(
                onSuccess = {
                    onSuccess()
                },
                onFailure = { error ->
                    onError(error.message ?: "Failed to correct official journey date")
                }
            )
        }
    }

    /**
     * [ATOMICITY, IDEMPOTENCY & CONCURRENCY GUARD]:
     * 1. Uses an active Job guard (`finishTripJob?.isActive`) and Mutex to prevent double-tap race conditions.
     * 2. Executes trip completion & stamp generation as a single atomic transaction.
     * 3. Retrying a completed trip returns the existing stamp idempotently without duplicates.
     * 4. Updates FinishTripUiState for explicit UI feedback (Loading -> Success/Error).
     */
    fun finishTrip(
        tripId: Long,
        reflectionNote: String?,
        stampInkColorHex: String,
        stampStyle: String,
        onFinished: (Long) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Concurrency Guard: Immediately ignore if a finish job is currently executing
        if (finishTripJob?.isActive == true) return

        finishTripJob = viewModelScope.launch {
            finishTripMutex.withLock {
                _finishTripUiState.value = FinishTripUiState.Loading
                _isProcessing.value = true

                try {
                    val trip = tripRepository.getTripByIdSync(tripId) ?: run {
                        val msg = "Journey not found"
                        _finishTripUiState.value = FinishTripUiState.Error(msg)
                        onError(msg)
                        return@withLock
                    }

                    // Layer 3 validation: Never complete a future trip
                    if (DateUtils.isFutureDate(trip.date)) {
                        val msg = "Cannot finish a future journey. Starts on ${trip.date}."
                        _finishTripUiState.value = FinishTripUiState.Error(msg)
                        onError(msg)
                        return@withLock
                    }

                    val moments = momentRepository.getMomentsForTripSync(tripId)
                    val completedTime = System.currentTimeMillis()

                    // Single atomic & idempotent call completing trip and issuing stamp
                    val result = travelStampRepository.completeTripAndIssueStamp(
                        tripId = tripId,
                        title = trip.name,
                        destination = trip.destination,
                        dateText = trip.date,
                        peopleCount = trip.peopleCount,
                        momentsCount = moments.size,
                        inkColorHex = stampInkColorHex,
                        stampStyle = stampStyle,
                        reflectionNote = reflectionNote?.ifBlank { null } ?: trip.description,
                        completedAt = completedTime
                    )

                    result.fold(
                        onSuccess = { stamp ->
                            _selectedTripId.value = tripId
                            _finishTripUiState.value = FinishTripUiState.Success(tripId, stamp)
                            onFinished(tripId)
                        },
                        onFailure = { throwable ->
                            val msg = throwable.localizedMessage ?: "Journey could not be completed."
                            _finishTripUiState.value = FinishTripUiState.Error(msg)
                            onError(msg)
                        }
                    )
                } catch (e: Exception) {
                    val msg = e.localizedMessage ?: "Unexpected error completing journey"
                    _finishTripUiState.value = FinishTripUiState.Error(msg)
                    onError(msg)
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun deleteTrip(tripId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId)
            if (_selectedTripId.value == tripId) {
                _selectedTripId.value = null
            }
            onDeleted()
        }
    }

    fun populateSampleJourney(onComplete: (Long) -> Unit) {
        if (_isProcessing.value) return
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                val sampleTrip = Trip(
                    name = "Harihar Fort",
                    destination = "Nashik, Maharashtra",
                    date = "10 August 2026",
                    peopleCount = 4,
                    description = "Sunday monsoon trek with friends through rock-cut steps & misty cliffs.",
                    status = TripStatus.IN_PROGRESS,
                    stampEarned = false,
                    completedAt = null,
                    createdAt = System.currentTimeMillis() - 86400000
                )
                val tripId = tripRepository.createTrip(sampleTrip)
                checklistRepository.seedDefaultItems(tripId)

                momentRepository.addMoment(
                    Moment(
                        tripId = tripId,
                        category = MomentCategory.CHAI,
                        note = "Hot cutting chai and ginger pakodas at the base village before starting the ascent.",
                        timestamp = System.currentTimeMillis() - 40000000
                    )
                )
                momentRepository.addMoment(
                    Moment(
                        tripId = tripId,
                        category = MomentCategory.RAIN,
                        note = "Heavy clouds opened up right at the plateau. Incredible monsoon mist swirling everywhere!",
                        timestamp = System.currentTimeMillis() - 30000000
                    )
                )
                momentRepository.addMoment(
                    Moment(
                        tripId = tripId,
                        category = MomentCategory.VIEW,
                        note = "Standing at the 80-degree vertical stone stairs looking into the boundless valley.",
                        timestamp = System.currentTimeMillis() - 20000000
                    )
                )
                momentRepository.addMoment(
                    Moment(
                        tripId = tripId,
                        category = MomentCategory.MEMORY,
                        note = "Summit conquered! Shared stories with all 4 of us at the top shrine.",
                        timestamp = System.currentTimeMillis() - 10000000
                    )
                )

                _selectedTripId.value = tripId
                onComplete(tripId)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun exportBackup(context: Context, onResult: (Result<BackupExportResult>) -> Unit) {
        if (_isProcessing.value) return
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                val result = BackupManager.createExportFile(context, database)
                onResult(Result.success(result))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun importBackup(context: Context, uri: Uri, onResult: (Result<BackupImportResult>) -> Unit) {
        if (_isProcessing.value) return
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                val result = BackupManager.importBackup(context, uri, database)
                onResult(result)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun clearForTest() {
        viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TravelStampApp)
                val appContainer: AppContainer = application.container
                TravelViewModel(
                    tripRepository = appContainer.tripRepository,
                    checklistRepository = appContainer.checklistRepository,
                    momentRepository = appContainer.momentRepository,
                    travelStampRepository = appContainer.travelStampRepository,
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                    database = appContainer.database,
                    locationSuggestionRepository = appContainer.locationSuggestionRepository,
                    reminderCoordinator = appContainer.reminderCoordinator,
                    journeyLocationRepositoryParam = appContainer.journeyLocationRepository,
                    locationSearchRepositoryParam = appContainer.locationSearchRepository
                )
            }
        }
    }
}
