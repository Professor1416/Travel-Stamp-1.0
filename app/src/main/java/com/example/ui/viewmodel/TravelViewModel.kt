package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.TravelStampApp
import com.example.data.AppContainer
import com.example.data.model.ChecklistItem
import com.example.data.model.Moment
import com.example.data.model.MomentCategory
import com.example.data.model.TravelStamp
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.data.repository.ChecklistRepository
import com.example.data.repository.MomentRepository
import com.example.data.repository.TravelStampRepository
import com.example.data.repository.TripRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TravelViewModel(
    private val tripRepository: TripRepository,
    private val checklistRepository: ChecklistRepository,
    private val momentRepository: MomentRepository,
    private val travelStampRepository: TravelStampRepository
) : ViewModel() {

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

    val completedTrips: StateFlow<List<Trip>> = tripRepository.getCompletedTrips()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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

    fun selectTrip(tripId: Long) {
        _selectedTripId.value = tripId
    }

    fun createTrip(
        name: String,
        destination: String,
        date: String,
        peopleCount: Int,
        description: String,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val trip = Trip(
                name = name.trim(),
                destination = destination.trim(),
                date = date.trim(),
                peopleCount = if (peopleCount < 1) 1 else peopleCount,
                description = description.trim(),
                status = TripStatus.ACTIVE,
                createdAt = System.currentTimeMillis()
            )
            val newTripId = tripRepository.createTrip(trip)
            // Seed standard checklist items
            checklistRepository.seedDefaultItems(newTripId)
            _selectedTripId.value = newTripId
            onCreated(newTripId)
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

    fun addMoment(
        tripId: Long,
        category: MomentCategory,
        note: String,
        imageUri: String?,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val moment = Moment(
                tripId = tripId,
                category = category,
                note = note.trim(),
                imageUri = imageUri,
                timestamp = System.currentTimeMillis()
            )
            momentRepository.addMoment(moment)
            onSaved()
        }
    }

    fun deleteMoment(momentId: Long) {
        viewModelScope.launch {
            momentRepository.deleteMoment(momentId)
        }
    }

    fun updateTrip(
        tripId: Long,
        name: String,
        destination: String,
        date: String,
        peopleCount: Int,
        description: String,
        onUpdated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val existing = tripRepository.getTripByIdSync(tripId) ?: return@launch
            val updated = existing.copy(
                name = name.trim(),
                destination = destination.trim(),
                date = date.trim(),
                peopleCount = if (peopleCount < 1) 1 else peopleCount,
                description = description.trim()
            )
            tripRepository.updateTrip(updated)
            onUpdated()
        }
    }

    fun finishTrip(
        tripId: Long,
        reflectionNote: String?,
        stampInkColorHex: String,
        stampStyle: String,
        onFinished: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val trip = tripRepository.getTripByIdSync(tripId) ?: return@launch
            val moments = momentRepository.getMomentsForTripSync(tripId)

            // Update trip record
            tripRepository.finishTrip(
                tripId = tripId,
                reflectionNote = reflectionNote,
                stampInkColorHex = stampInkColorHex,
                stampStyle = stampStyle
            )

            // Generate auto-incrementing official Travel Stamp number: #001, #002, etc.
            val stampCount = travelStampRepository.getStampsCountSync()
            val nextNumber = stampCount + 1
            val stampCode = "#" + String.format(java.util.Locale.getDefault(), "%03d", nextNumber)

            val stamp = TravelStamp(
                tripId = tripId,
                stampCode = stampCode,
                title = trip.name,
                destination = trip.destination,
                dateText = trip.date,
                peopleCount = trip.peopleCount,
                momentsCount = moments.size,
                inkColorHex = stampInkColorHex,
                stampStyle = stampStyle,
                inspectionText = "OFFICIALLY LOGGED • CERTIFIED JOURNEY",
                issuedAt = System.currentTimeMillis(),
                reflectionNote = reflectionNote
            )

            travelStampRepository.issueStamp(stamp)
            _selectedTripId.value = tripId
            onFinished(tripId)
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

    fun populateSampleJourney(onComplete: () -> Unit) {
        viewModelScope.launch {
            val sampleTrip = Trip(
                name = "Harihar Fort",
                destination = "Nashik, Maharashtra",
                date = "18 August 2026",
                peopleCount = 4,
                description = "Sunday monsoon trek with friends through rock-cut steps & misty cliffs.",
                status = TripStatus.ACTIVE,
                createdAt = System.currentTimeMillis() - 86400000
            )
            val tripId = tripRepository.createTrip(sampleTrip)
            checklistRepository.seedDefaultItems(tripId)

            // Add sample moments
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
            onComplete()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TravelStampApp)
                val container: AppContainer = application.container
                TravelViewModel(
                    tripRepository = container.tripRepository,
                    checklistRepository = container.checklistRepository,
                    momentRepository = container.momentRepository,
                    travelStampRepository = container.travelStampRepository
                )
            }
        }
    }
}
