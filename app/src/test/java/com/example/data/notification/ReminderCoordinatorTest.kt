package com.example.data.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesRepositoryImpl
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import com.example.data.model.TripStatus
import com.example.data.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderCoordinatorTest {

    private lateinit var context: Context
    private lateinit var userPrefs: UserPreferencesRepository
    private lateinit var fakeTripRepo: FakeTripRepository
    private lateinit var fakeScheduler: FakeTripReminderScheduler
    private lateinit var coordinator: ReminderCoordinatorImpl

    private val futureDateStr: String
        get() = LocalDate.now().plusDays(10).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("travel_stamp_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        userPrefs = UserPreferencesRepositoryImpl(context)
        fakeTripRepo = FakeTripRepository()
        fakeScheduler = FakeTripReminderScheduler()

        coordinator = ReminderCoordinatorImpl(
            userPreferencesRepository = userPrefs,
            tripRepository = fakeTripRepo,
            reminderScheduler = fakeScheduler
        )
    }

    @Test
    fun `test 1 - setGlobalRemindersEnabled false persists false`() = runBlocking {
        userPrefs.setPreTripRemindersEnabled(true)
        assertTrue(userPrefs.preTripRemindersEnabled.value)

        coordinator.setGlobalRemindersEnabled(false)

        assertFalse(userPrefs.preTripRemindersEnabled.value)
    }

    @Test
    fun `test 2 - setGlobalRemindersEnabled false cancels all reminders via scheduler`() = runBlocking {
        coordinator.setGlobalRemindersEnabled(false)

        assertEquals(1, fakeScheduler.cancelAllCallCount)
    }

    @Test
    fun `test 3 - setGlobalRemindersEnabled false does not mutate individual trips in repository`() = runBlocking {
        val trip = Trip(
            id = 1L,
            name = "Paris Trip",
            destination = "Paris",
            date = futureDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )
        fakeTripRepo.setTrips(listOf(trip))

        coordinator.setGlobalRemindersEnabled(false)

        val trips = fakeTripRepo.getTripsSnapshot()
        assertEquals(1, trips.size)
        assertTrue(trips[0].reminderEnabled)
        assertEquals(TripReminderPreset.ONE_DAY_BEFORE, trips[0].reminderPreset)
        assertEquals(600, trips[0].startTimeMinutes)
    }

    @Test
    fun `test 4 - setGlobalRemindersEnabled true persists true`() = runBlocking {
        userPrefs.setPreTripRemindersEnabled(false)
        assertFalse(userPrefs.preTripRemindersEnabled.value)

        coordinator.setGlobalRemindersEnabled(true)

        assertTrue(userPrefs.preTripRemindersEnabled.value)
    }

    @Test
    fun `test 5 - setGlobalRemindersEnabled true schedules eligible trips`() = runBlocking {
        val trip = Trip(
            id = 5L,
            name = "Tokyo Trip",
            destination = "Tokyo",
            date = futureDateStr,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )
        fakeTripRepo.setTrips(listOf(trip))

        coordinator.setGlobalRemindersEnabled(true)

        assertEquals(listOf(5L), fakeScheduler.scheduledTripIds)
    }

    @Test
    fun `test 6 - setGlobalRemindersEnabled true ignores completed trips`() = runBlocking {
        val completedTrip = Trip(
            id = 6L,
            name = "Old Trip",
            destination = "Rome",
            date = futureDateStr,
            status = TripStatus.COMPLETED,
            reminderEnabled = true
        )
        fakeTripRepo.setTrips(listOf(completedTrip))

        coordinator.setGlobalRemindersEnabled(true)

        assertTrue(fakeScheduler.scheduledTripIds.isEmpty())
    }

    @Test
    fun `test 7 - setGlobalRemindersEnabled true ignores trips with deletedAt not null`() = runBlocking {
        val deletedTrip = Trip(
            id = 7L,
            name = "Deleted Trip",
            destination = "London",
            date = futureDateStr,
            deletedAt = System.currentTimeMillis(),
            reminderEnabled = true
        )
        fakeTripRepo.setTrips(listOf(deletedTrip))

        coordinator.setGlobalRemindersEnabled(true)

        assertTrue(fakeScheduler.scheduledTripIds.isEmpty())
    }

    @Test
    fun `test 8 - setGlobalRemindersEnabled true ignores trips with reminderEnabled false`() = runBlocking {
        val disabledReminderTrip = Trip(
            id = 8L,
            name = "No Reminder Trip",
            destination = "Berlin",
            date = futureDateStr,
            reminderEnabled = false
        )
        fakeTripRepo.setTrips(listOf(disabledReminderTrip))

        coordinator.setGlobalRemindersEnabled(true)

        assertTrue(fakeScheduler.scheduledTripIds.isEmpty())
    }

    @Test
    fun `test 9 - setGlobalRemindersEnabled true ignores trips where stampEarned true`() = runBlocking {
        val stampedTrip = Trip(
            id = 9L,
            name = "Stamped Trip",
            destination = "Seoul",
            date = futureDateStr,
            stampEarned = true,
            reminderEnabled = true
        )
        fakeTripRepo.setTrips(listOf(stampedTrip))

        coordinator.setGlobalRemindersEnabled(true)

        assertTrue(fakeScheduler.scheduledTripIds.isEmpty())
    }

    @Test
    fun `test 10 - setGlobalRemindersEnabled true ignores trips where completedAt not null`() = runBlocking {
        val finishedTrip = Trip(
            id = 10L,
            name = "Finished Trip",
            destination = "Madrid",
            date = futureDateStr,
            completedAt = System.currentTimeMillis(),
            reminderEnabled = true
        )
        fakeTripRepo.setTrips(listOf(finishedTrip))

        coordinator.setGlobalRemindersEnabled(true)

        assertTrue(fakeScheduler.scheduledTripIds.isEmpty())
    }

    @Test
    fun `test 11 - setGlobalRemindersEnabled true passes trips with TWO_HOURS_BEFORE and null startTime to scheduler`() = runBlocking {
        val tripWithoutTime = Trip(
            id = 11L,
            name = "Missing Time Trip",
            destination = "Kyoto",
            date = futureDateStr,
            startTimeMinutes = null,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.TWO_HOURS_BEFORE
        )
        val validTrip = Trip(
            id = 12L,
            name = "Valid Trip",
            destination = "Osaka",
            date = futureDateStr,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )
        fakeTripRepo.setTrips(listOf(tripWithoutTime, validTrip))

        coordinator.setGlobalRemindersEnabled(true)

        // Both are dispatched to scheduler; scheduler declines/cancels 11L without throwing, 12L is scheduled
        assertTrue(fakeScheduler.scheduledTripIds.contains(11L))
        assertTrue(fakeScheduler.scheduledTripIds.contains(12L))
    }

    @Test
    fun `test 12 - setGlobalRemindersEnabled true with empty trip repository completes safely`() = runBlocking {
        fakeTripRepo.setTrips(emptyList())

        coordinator.setGlobalRemindersEnabled(true)

        assertTrue(fakeScheduler.scheduledTripIds.isEmpty())
        assertTrue(userPrefs.preTripRemindersEnabled.value)
    }

    @Test
    fun `test 13 - setGlobalRemindersEnabled true handles exception from one trip without aborting other trips`() = runBlocking {
        val failingTrip = Trip(
            id = 13L,
            name = "Crash Trip",
            destination = "Crash City",
            date = futureDateStr,
            reminderEnabled = true
        )
        val goodTrip = Trip(
            id = 14L,
            name = "Good Trip",
            destination = "Nice City",
            date = futureDateStr,
            reminderEnabled = true
        )
        fakeTripRepo.setTrips(listOf(failingTrip, goodTrip))
        fakeScheduler.failTripId = 13L

        coordinator.setGlobalRemindersEnabled(true)

        // 13L threw an exception, but 14L was still processed and scheduled
        assertTrue(fakeScheduler.scheduledTripIds.contains(14L))
    }

    @Test
    fun `test 14 - reconcile while global OFF cancels all reminders`() = runBlocking {
        userPrefs.setPreTripRemindersEnabled(false)
        val trip = Trip(id = 15L, name = "Trip", destination = "Place", date = futureDateStr, reminderEnabled = true)
        fakeTripRepo.setTrips(listOf(trip))

        coordinator.reconcile()

        assertEquals(1, fakeScheduler.cancelAllCallCount)
        assertTrue(fakeScheduler.scheduledTripIds.isEmpty())
    }

    @Test
    fun `test 15 - reconcile while global ON reschedules eligible trips`() = runBlocking {
        userPrefs.setPreTripRemindersEnabled(true)
        val trip = Trip(id = 16L, name = "Trip", destination = "Place", date = futureDateStr, reminderEnabled = true)
        fakeTripRepo.setTrips(listOf(trip))

        coordinator.reconcile()

        assertEquals(listOf(16L), fakeScheduler.scheduledTripIds)
    }

    @Test
    fun `test 16 - reconcile called multiple times while global ON is idempotent`() = runBlocking {
        userPrefs.setPreTripRemindersEnabled(true)
        val trip = Trip(id = 17L, name = "Trip", destination = "Place", date = futureDateStr, reminderEnabled = true)
        fakeTripRepo.setTrips(listOf(trip))

        coordinator.reconcile()
        coordinator.reconcile()
        coordinator.reconcile()

        // 3 calls to scheduleReminder for trip 17L; scheduler receives it each time without crashing
        assertEquals(3, fakeScheduler.scheduledTripIds.size)
        assertEquals(listOf(17L, 17L, 17L), fakeScheduler.scheduledTripIds)
    }

    @Test
    fun `test 17 - rapid toggle OFF to ON to OFF ends with all reminders cancelled and pref false`() = runBlocking {
        userPrefs.setPreTripRemindersEnabled(true)
        val trip = Trip(id = 18L, name = "Trip", destination = "Place", date = futureDateStr, reminderEnabled = true)
        fakeTripRepo.setTrips(listOf(trip))

        coordinator.setGlobalRemindersEnabled(false)
        coordinator.setGlobalRemindersEnabled(true)
        coordinator.setGlobalRemindersEnabled(false)

        assertFalse(userPrefs.preTripRemindersEnabled.value)
        // Final operation was cancelAllReminders
        assertTrue(fakeScheduler.cancelAllCallCount >= 2)
    }

    @Test
    fun `test 18 - rapid toggle ON to OFF to ON ends with eligible trips scheduled and pref true`() = runBlocking {
        userPrefs.setPreTripRemindersEnabled(false)
        val trip = Trip(id = 19L, name = "Trip", destination = "Place", date = futureDateStr, reminderEnabled = true)
        fakeTripRepo.setTrips(listOf(trip))

        coordinator.setGlobalRemindersEnabled(true)
        coordinator.setGlobalRemindersEnabled(false)
        coordinator.setGlobalRemindersEnabled(true)

        assertTrue(userPrefs.preTripRemindersEnabled.value)
        assertTrue(fakeScheduler.scheduledTripIds.contains(19L))
    }

    @Test
    fun `test 28 - Global OFF cancels actual enqueued WorkManager work for pre-trip reminders`() = runBlocking {
        try {
            val config = androidx.work.Configuration.Builder().build()
            WorkManager.initialize(context, config)
        } catch (_: Exception) {}
        val workManager = WorkManager.getInstance(context)
        val realScheduler = TripReminderSchedulerImpl(context, workManager)

        val realCoordinator = ReminderCoordinatorImpl(
            userPreferencesRepository = userPrefs,
            tripRepository = fakeTripRepo,
            reminderScheduler = realScheduler
        )

        val trip = Trip(
            id = 280L,
            name = "Active Trek",
            destination = "Himalayas",
            date = futureDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )
        fakeTripRepo.setTrips(listOf(trip))

        // Step 1: Enable global reminders
        realCoordinator.setGlobalRemindersEnabled(true)
        val enqueuedWorks = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(280L)).get()
        assertEquals(1, enqueuedWorks.filter { it.state == WorkInfo.State.ENQUEUED }.size)

        // Step 2: Disable global reminders
        realCoordinator.setGlobalRemindersEnabled(false)
        val afterDisableWorks = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(280L)).get()
        // Work must be cancelled or no longer ENQUEUED
        assertTrue(afterDisableWorks.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `test 29 - Global ON schedules actual WorkManager work for eligible trip`() = runBlocking {
        try {
            val config = androidx.work.Configuration.Builder().build()
            WorkManager.initialize(context, config)
        } catch (_: Exception) {}
        val workManager = WorkManager.getInstance(context)
        val realScheduler = TripReminderSchedulerImpl(context, workManager)

        val realCoordinator = ReminderCoordinatorImpl(
            userPreferencesRepository = userPrefs,
            tripRepository = fakeTripRepo,
            reminderScheduler = realScheduler
        )

        val trip = Trip(
            id = 290L,
            name = "Safari",
            destination = "Serengeti",
            date = futureDateStr,
            startTimeMinutes = 540,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )
        fakeTripRepo.setTrips(listOf(trip))

        realCoordinator.setGlobalRemindersEnabled(true)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(290L)).get()
        assertEquals(1, workInfos.filter { it.state == WorkInfo.State.ENQUEUED }.size)
    }

    @Test
    fun `test 30 - Reconcile does not duplicate WorkManager work`() = runBlocking {
        try {
            val config = androidx.work.Configuration.Builder().build()
            WorkManager.initialize(context, config)
        } catch (_: Exception) {}
        val workManager = WorkManager.getInstance(context)
        val realScheduler = TripReminderSchedulerImpl(context, workManager)

        val realCoordinator = ReminderCoordinatorImpl(
            userPreferencesRepository = userPrefs,
            tripRepository = fakeTripRepo,
            reminderScheduler = realScheduler
        )

        val trip = Trip(
            id = 300L,
            name = "Beach",
            destination = "Goa",
            date = futureDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )
        fakeTripRepo.setTrips(listOf(trip))

        realCoordinator.setGlobalRemindersEnabled(true)
        // Call reconcile multiple times
        realCoordinator.reconcile()
        realCoordinator.reconcile()

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(300L)).get()
        // ExistingWorkPolicy.REPLACE guarantees exactly one active enqueued job
        assertEquals(1, workInfos.filter { it.state == WorkInfo.State.ENQUEUED }.size)
    }
}

private class FakeTripReminderScheduler : TripReminderScheduler {
    val scheduledTripIds = mutableListOf<Long>()
    val cancelledTripIds = mutableListOf<Long>()
    var cancelAllCallCount = 0
    var failTripId: Long? = null

    override fun scheduleReminder(trip: Trip) {
        if (trip.id == failTripId) {
            throw RuntimeException("Simulated schedule failure for trip ${trip.id}")
        }
        scheduledTripIds.add(trip.id)
    }

    override fun cancelReminder(tripId: Long) {
        cancelledTripIds.add(tripId)
    }

    override fun cancelAllReminders() {
        cancelAllCallCount++
    }
}

private class FakeTripRepository : TripRepository {
    private val tripsFlow = MutableStateFlow<List<Trip>>(emptyList())

    fun setTrips(trips: List<Trip>) {
        tripsFlow.value = trips
    }

    fun getTripsSnapshot(): List<Trip> = tripsFlow.value

    override fun getAllTrips(): Flow<List<Trip>> = tripsFlow.asStateFlow()
    override fun getActiveTrips(): Flow<List<Trip>> = tripsFlow.asStateFlow()
    override fun getCompletedTrips(): Flow<List<Trip>> = tripsFlow.asStateFlow()
    override fun observeRecentJourneys(): Flow<List<Trip>> = tripsFlow.asStateFlow()
    override fun observeUpcomingJourneys(): Flow<List<Trip>> = tripsFlow.asStateFlow()
    override fun getTripById(id: Long): Flow<Trip?> = MutableStateFlow(tripsFlow.value.find { it.id == id })
    override suspend fun getTripByIdSync(id: Long): Trip? = tripsFlow.value.find { it.id == id }
    override suspend fun createTrip(trip: Trip): Long = trip.id
    override suspend fun updateTrip(trip: Trip) {}
    override suspend fun finishTrip(tripId: Long, reflectionNote: String?, stampInkColorHex: String, stampStyle: String): Boolean = true
    override suspend fun deleteTrip(id: Long) {}
    override fun getCompletedTripsCount(): Flow<Int> = MutableStateFlow(0)
    override fun getTotalTripsCount(): Flow<Int> = MutableStateFlow(0)
}
