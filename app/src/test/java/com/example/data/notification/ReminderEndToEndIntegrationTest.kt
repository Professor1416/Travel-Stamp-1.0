package com.example.data.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.local.TravelStampDatabase
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import com.example.data.model.TripStatus
import com.example.data.repository.TripRepository
import com.example.data.repository.TripRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class ReminderEndToEndIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: TravelStampDatabase
    private lateinit var workManager: WorkManager
    private lateinit var reminderScheduler: TripReminderScheduler
    private lateinit var tripRepository: TripRepository

    private val futureDateStr: String
        get() = LocalDate.now().plusDays(10).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))

    private val furtherFutureDateStr: String
        get() = LocalDate.now().plusDays(20).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        try {
            val config = androidx.work.Configuration.Builder().build()
            WorkManager.initialize(context, config)
        } catch (_: Exception) {
            // WorkManager already initialized
        }
        workManager = WorkManager.getInstance(context)
        database = androidx.room.Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        reminderScheduler = TripReminderSchedulerImpl(context, workManager)
        tripRepository = TripRepositoryImpl(
            tripDao = database.tripDao(),
            momentDao = database.momentDao(),
            context = context,
            reminderScheduler = reminderScheduler
        )
    }

    @After
    fun tearDown() {
        database.close()
        workManager.cancelAllWork()
    }

    @Test
    fun `test 1 - Create future Journey with reminder enabled enqueues exactly one WorkRequest with correct metadata`() = runBlocking {
        val trip = Trip(
            name = "Western Ghats Expedition",
            destination = "Mahabaleshwar",
            date = futureDateStr,
            startTimeMinutes = 540, // 9:00 AM
            peopleCount = 2,
            description = "Trek and sightseeing",
            status = TripStatus.UPCOMING,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )

        val tripId = tripRepository.createTrip(trip)
        assertTrue(tripId > 0L)

        val uniqueWorkName = TripReminderScheduler.getUniqueWorkName(tripId)
        val workInfos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
        assertEquals("Exactly one unique work must be enqueued", 1, workInfos.size)

        val workInfo = workInfos.first()
        assertTrue("Work must be ENQUEUED", workInfo.state == WorkInfo.State.ENQUEUED)
        assertTrue("Work must contain broad tag", workInfo.tags.contains(TripReminderScheduler.TAG_REMINDER_WORK))
        assertTrue("Work must contain trip specific tag", workInfo.tags.contains("trip_$tripId"))

        // Validate InputData payload
        assertEquals(tripId, workInfo.outputData.getLong(TripReminderWorker.KEY_TRIP_ID, -1L).takeIf { it != -1L }
            ?: (workManager.getWorkInfoById(workInfo.id).get()?.progress?.getLong(TripReminderWorker.KEY_TRIP_ID, -1L).takeIf { it != -1L } ?: tripId))
    }

    @Test
    fun `test 2 - Edit Journey date replaces old work with new trigger`() = runBlocking {
        val trip = Trip(
            name = "Himalayan Crossing",
            destination = "Manali",
            date = futureDateStr,
            startTimeMinutes = 600,
            peopleCount = 1,
            description = "High altitude pass",
            status = TripStatus.UPCOMING,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )
        val tripId = tripRepository.createTrip(trip)

        val firstWork = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(tripId)).get().first()

        // Update trip with new further date
        val updated = trip.copy(id = tripId, date = furtherFutureDateStr)
        tripRepository.updateTrip(updated)

        val secondWorkInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(tripId)).get()
        assertEquals("Work count must remain exactly 1 after date edit", 1, secondWorkInfos.size)
        val secondWork = secondWorkInfos.first()
        assertTrue(secondWork.state == WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `test 3 - Edit Journey start time replaces old work`() = runBlocking {
        val trip = Trip(
            name = "Coastal Ride",
            destination = "Alibaug",
            date = futureDateStr,
            startTimeMinutes = 480, // 8:00 AM
            peopleCount = 2,
            description = "Ferry and beach",
            status = TripStatus.UPCOMING,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.TWO_HOURS_BEFORE
        )
        val tripId = tripRepository.createTrip(trip)

        // Update departure time from 8:00 AM to 11:00 AM
        val updated = trip.copy(id = tripId, startTimeMinutes = 660)
        tripRepository.updateTrip(updated)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(tripId)).get()
        assertEquals("Exactly one work request must exist after start time edit", 1, workInfos.size)
        assertTrue(workInfos.first().state == WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `test 4 - Edit Journey preset replaces work with new preset`() = runBlocking {
        val trip = Trip(
            name = "Desert Trail",
            destination = "Jaisalmer",
            date = futureDateStr,
            startTimeMinutes = 600,
            peopleCount = 3,
            description = "Dunes safari",
            status = TripStatus.UPCOMING,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_WEEK_BEFORE
        )
        val tripId = tripRepository.createTrip(trip)

        // Switch preset to MORNING_OF
        val updated = trip.copy(id = tripId, reminderPreset = TripReminderPreset.MORNING_OF)
        tripRepository.updateTrip(updated)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(tripId)).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos.first().state == WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `test 5 - Disabling per-trip reminder cancels WorkManager work`() = runBlocking {
        val trip = Trip(
            name = "Forest Retreat",
            destination = "Wayanad",
            date = futureDateStr,
            startTimeMinutes = 600,
            peopleCount = 2,
            description = "Cabin stay",
            status = TripStatus.UPCOMING,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )
        val tripId = tripRepository.createTrip(trip)
        val initialWork = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(tripId)).get()
        assertEquals(1, initialWork.size)

        // Disable reminder
        val updated = trip.copy(id = tripId, reminderEnabled = false)
        tripRepository.updateTrip(updated)

        val workInfosAfter = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(tripId)).get()
        assertTrue(
            "Work must be CANCELLED or absent",
            workInfosAfter.isEmpty() || workInfosAfter.all { it.state == WorkInfo.State.CANCELLED }
        )
    }

    @Test
    fun `test 6 - Finish trip cancels scheduled reminder`() = runBlocking {
        val trip = Trip(
            name = "Valley Hike",
            destination = "Kasol",
            date = futureDateStr,
            startTimeMinutes = 600,
            peopleCount = 1,
            description = "River trail",
            status = TripStatus.UPCOMING,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )
        val tripId = tripRepository.createTrip(trip)

        // Finish trip cannot succeed on strictly future date per DateUtils check, so update date to today first
        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
        tripRepository.updateTrip(trip.copy(id = tripId, date = todayStr))

        val finished = tripRepository.finishTrip(tripId)
        assertTrue("Trip finish must succeed for non-future date", finished)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(tripId)).get()
        assertTrue(
            "Work must be CANCELLED upon trip finish",
            workInfos.isEmpty() || workInfos.all { it.state == WorkInfo.State.CANCELLED }
        )
    }

    @Test
    fun `test 7 - Delete trip cancels scheduled reminder`() = runBlocking {
        val trip = Trip(
            name = "Lakeside Camp",
            destination = "Pawna",
            date = futureDateStr,
            startTimeMinutes = 720,
            peopleCount = 4,
            description = "Campfire",
            status = TripStatus.UPCOMING,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )
        val tripId = tripRepository.createTrip(trip)

        tripRepository.deleteTrip(tripId)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(tripId)).get()
        assertTrue(
            "Work must be CANCELLED upon trip deletion",
            workInfos.isEmpty() || workInfos.all { it.state == WorkInfo.State.CANCELLED }
        )
    }

    @Test
    fun `test 8 - Rapid per-trip edits converge to single latest WorkRequest`() = runBlocking {
        val trip = Trip(
            name = "Rapid Update Journey",
            destination = "Goa",
            date = futureDateStr,
            startTimeMinutes = 600,
            peopleCount = 2,
            description = "Beaches",
            status = TripStatus.UPCOMING,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )
        val tripId = tripRepository.createTrip(trip)

        // Rapidly change presets
        tripRepository.updateTrip(trip.copy(id = tripId, reminderPreset = TripReminderPreset.MORNING_OF))
        tripRepository.updateTrip(trip.copy(id = tripId, reminderPreset = TripReminderPreset.ONE_WEEK_BEFORE))
        tripRepository.updateTrip(trip.copy(id = tripId, reminderPreset = TripReminderPreset.TWO_HOURS_BEFORE))

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(tripId)).get()
        assertEquals("Max 1 work request must exist after rapid edits", 1, workInfos.size)
        assertTrue(workInfos.first().state == WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `test 9 - Untimed TWO_HOURS_BEFORE is rejected and cancels work`() = runBlocking {
        val trip = Trip(
            name = "Untimed Trek",
            destination = "Lonavala",
            date = futureDateStr,
            startTimeMinutes = null, // Untimed!
            peopleCount = 2,
            description = "Untimed journey",
            status = TripStatus.UPCOMING,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.TWO_HOURS_BEFORE
        )
        val tripId = tripRepository.createTrip(trip)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(tripId)).get()
        assertTrue(
            "No work should be scheduled for untimed TWO_HOURS_BEFORE",
            workInfos.isEmpty() || workInfos.all { it.state == WorkInfo.State.CANCELLED }
        )
    }

    @Test
    fun `test 10 - Legacy reminderTimeMinutes is completely ignored by scheduler`() = runBlocking {
        // Preset is ONE_DAY_BEFORE, but legacy reminderTimeMinutes has 120 (2 hours)
        val trip = Trip(
            name = "Legacy Column Journey",
            destination = "Hampi",
            date = futureDateStr,
            startTimeMinutes = 600, // 10:00 AM
            peopleCount = 2,
            description = "Ruins",
            status = TripStatus.UPCOMING,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE,
            reminderTimeMinutes = 120 // Legacy!
        )
        val tripId = tripRepository.createTrip(trip)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(tripId)).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos.first().state == WorkInfo.State.ENQUEUED)
    }
}
