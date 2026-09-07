package com.example.data.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesRepositoryImpl
import com.example.data.local.entity.TripEntity
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import com.example.data.model.TripStatus
import com.example.data.repository.TripRepository
import com.example.data.repository.TripRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class ReminderGlobalIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: TravelStampDatabase
    private lateinit var workManager: WorkManager
    private lateinit var userPrefs: UserPreferencesRepository
    private lateinit var reminderScheduler: TripReminderScheduler
    private lateinit var tripRepository: TripRepository
    private lateinit var coordinator: ReminderCoordinator

    private val futureDateStr: String
        get() = LocalDate.now().plusDays(10).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))

    private val pastDateStr: String
        get() = LocalDate.now().minusDays(3).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("travel_stamp_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        try {
            val config = androidx.work.Configuration.Builder().build()
            WorkManager.initialize(context, config)
        } catch (_: Exception) {
            // WorkManager already initialized
        }
        workManager = WorkManager.getInstance(context)
        try {
            workManager.cancelAllWork()
            workManager.pruneWork()
        } catch (_: Exception) {
            // ignore
        }
        database = androidx.room.Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userPrefs = UserPreferencesRepositoryImpl(context)
        reminderScheduler = TripReminderSchedulerImpl(context, workManager)
        tripRepository = TripRepositoryImpl(
            tripDao = database.tripDao(),
            momentDao = database.momentDao(),
            context = context,
            reminderScheduler = reminderScheduler
        )
        coordinator = ReminderCoordinatorImpl(
            userPreferencesRepository = userPrefs,
            tripRepository = tripRepository,
            reminderScheduler = reminderScheduler
        )
    }

    @After
    fun tearDown() {
        database.close()
        workManager.cancelAllWork()
    }

    @Test
    fun `test 1 - Global OFF cancels all WorkManager jobs without mutating trip reminder preferences`() = runBlocking {
        // Create 2 trips with reminders
        val id1 = tripRepository.createTrip(
            Trip(name = "Trip 1", destination = "Dest 1", date = futureDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = true, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE)
        )
        val id2 = tripRepository.createTrip(
            Trip(name = "Trip 2", destination = "Dest 2", date = futureDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = true, reminderPreset = TripReminderPreset.ONE_WEEK_BEFORE)
        )

        // Verify work enqueued
        assertEquals(1, workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(id1)).get().size)
        assertEquals(1, workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(id2)).get().size)

        // Turn Global OFF
        coordinator.setGlobalRemindersEnabled(false)

        assertFalse("Global preference must be false", userPrefs.preTripRemindersEnabled.value)

        // Check WorkManager: all tagged work should be CANCELLED
        val taggedWork = workManager.getWorkInfosByTag(TripReminderScheduler.TAG_REMINDER_WORK).get()
        assertTrue("All reminder work must be cancelled", taggedWork.all { it.state == WorkInfo.State.CANCELLED })

        // Check Database: Trip preferences remain intact
        val trip1 = tripRepository.getTripByIdSync(id1)
        val trip2 = tripRepository.getTripByIdSync(id2)
        assertNotNull(trip1)
        assertNotNull(trip2)
        assertTrue("Trip 1 reminderEnabled must remain true", trip1!!.reminderEnabled)
        assertEquals(TripReminderPreset.ONE_DAY_BEFORE, trip1.reminderPreset)
        assertTrue("Trip 2 reminderEnabled must remain true", trip2!!.reminderEnabled)
        assertEquals(TripReminderPreset.ONE_WEEK_BEFORE, trip2.reminderPreset)
    }

    @Test
    fun `test 2 - Global ON restores only eligible future journeys and rejects ineligible journeys`() = runBlocking {
        // Set Global OFF first
        coordinator.setGlobalRemindersEnabled(false)

        // 1. Eligible future journey
        val eligibleId = tripRepository.createTrip(
            Trip(name = "Eligible", destination = "Dest", date = futureDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = true, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE)
        )
        // 2. Completed journey (direct insert so status is not overwritten)
        val completedId = database.tripDao().insertTrip(
            TripEntity.fromDomain(Trip(name = "Completed", destination = "Dest", date = futureDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = true, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE, status = TripStatus.COMPLETED))
        )
        // 3. Stamped journey (direct insert so stamp is not overwritten)
        val stampedId = database.tripDao().insertTrip(
            TripEntity.fromDomain(Trip(name = "Stamped", destination = "Dest", date = futureDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = true, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE, status = TripStatus.COMPLETED, stampEarned = true, completedAt = System.currentTimeMillis()))
        )
        // 4. Disabled reminder journey
        val disabledId = tripRepository.createTrip(
            Trip(name = "Disabled", destination = "Dest", date = futureDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = false, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE)
        )
        // 5. Past-trigger journey (no catch-up!)
        val pastId = tripRepository.createTrip(
            Trip(name = "Past", destination = "Dest", date = pastDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = true, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE)
        )

        // Turn Global ON
        coordinator.setGlobalRemindersEnabled(true)
        assertTrue("Global preference must be true", userPrefs.preTripRemindersEnabled.value)

        // Verify eligible journey has enqueued work
        val eligibleWork = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(eligibleId)).get()
        assertEquals(1, eligibleWork.size)
        assertEquals(WorkInfo.State.ENQUEUED, eligibleWork.first().state)

        // Verify ineligible journeys have no enqueued work
        val completedWork = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(completedId)).get()
        assertTrue(completedWork.isEmpty() || completedWork.all { it.state == WorkInfo.State.CANCELLED })

        val stampedWork = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(stampedId)).get()
        assertTrue(stampedWork.isEmpty() || stampedWork.all { it.state == WorkInfo.State.CANCELLED })

        val disabledWork = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(disabledId)).get()
        assertTrue(disabledWork.isEmpty() || disabledWork.all { it.state == WorkInfo.State.CANCELLED })

        val pastWork = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(pastId)).get()
        assertTrue(pastWork.isEmpty() || pastWork.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `test 3 - Repeated reconciliation is idempotent with max one WorkRequest per eligible trip`() = runBlocking {
        coordinator.setGlobalRemindersEnabled(true)

        val id = tripRepository.createTrip(
            Trip(name = "Idempotent Trip", destination = "Dest", date = futureDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = true, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE)
        )

        // Reconcile 5 times
        repeat(5) {
            coordinator.reconcile()
        }

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(id)).get()
        assertEquals("Must never accumulate multiple work requests", 1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.first().state)
    }

    @Test
    fun `test 4 - Rapid global toggles converge to final state in both preferences and WorkManager`() = runBlocking {
        val id = tripRepository.createTrip(
            Trip(name = "Toggle Trip", destination = "Dest", date = futureDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = true, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE)
        )

        // Rapidly toggle ON -> OFF -> ON -> OFF
        coordinator.setGlobalRemindersEnabled(true)
        coordinator.setGlobalRemindersEnabled(false)
        coordinator.setGlobalRemindersEnabled(true)
        coordinator.setGlobalRemindersEnabled(false)

        assertFalse("Final preference must be false", userPrefs.preTripRemindersEnabled.value)
        val taggedWork = workManager.getWorkInfosByTag(TripReminderScheduler.TAG_REMINDER_WORK).get()
        assertTrue("All tagged work must be cancelled", taggedWork.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `test 5 - Empty database reconciliation completes safely with zero work`() = runBlocking {
        coordinator.setGlobalRemindersEnabled(true)
        coordinator.reconcile()

        val taggedWork = workManager.getWorkInfosByTag(TripReminderScheduler.TAG_REMINDER_WORK).get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
        assertTrue("Zero work requests should be created for empty database", taggedWork.isEmpty())
    }

    @Test
    fun `test 6 - Large local dataset sanity with 60 journeys reconciles cleanly`() = runBlocking {
        coordinator.setGlobalRemindersEnabled(false)

        val totalTrips = 60
        var expectedEligible = 0

        for (i in 1..totalTrips) {
            when (i % 6) {
                0 -> {
                    // Future eligible
                    tripRepository.createTrip(
                        Trip(name = "Eligible $i", destination = "D", date = futureDateStr, startTimeMinutes = 500 + i, peopleCount = 1, description = "", reminderEnabled = true, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE)
                    )
                    expectedEligible++
                }
                1 -> {
                    // Completed (direct insert)
                    database.tripDao().insertTrip(
                        TripEntity.fromDomain(Trip(name = "Completed $i", destination = "D", date = futureDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = true, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE, status = TripStatus.COMPLETED))
                    )
                }
                2 -> {
                    // Stamped (direct insert)
                    database.tripDao().insertTrip(
                        TripEntity.fromDomain(Trip(name = "Stamped $i", destination = "D", date = futureDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = true, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE, status = TripStatus.COMPLETED, stampEarned = true, completedAt = System.currentTimeMillis()))
                    )
                }
                3 -> {
                    // Disabled
                    tripRepository.createTrip(
                        Trip(name = "Disabled $i", destination = "D", date = futureDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = false, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE)
                    )
                }
                4 -> {
                    // Past trigger (no catch-up)
                    tripRepository.createTrip(
                        Trip(name = "Past $i", destination = "D", date = pastDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = true, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE)
                    )
                }
                5 -> {
                    // Soft-deleted
                    val id = tripRepository.createTrip(
                        Trip(name = "Deleted $i", destination = "D", date = futureDateStr, startTimeMinutes = 600, peopleCount = 1, description = "", reminderEnabled = true, reminderPreset = TripReminderPreset.ONE_DAY_BEFORE)
                    )
                    tripRepository.deleteTrip(id)
                }
            }
        }

        // Trigger global reconciliation
        coordinator.setGlobalRemindersEnabled(true)

        val enqueuedWork = workManager.getWorkInfosByTag(TripReminderScheduler.TAG_REMINDER_WORK).get()
            .filter { it.state == WorkInfo.State.ENQUEUED }

        assertEquals("Exactly eligible trips must be enqueued", expectedEligible, enqueuedWork.size)
    }
}
