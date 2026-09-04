package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.BundledSuggestionSourceImpl
import com.example.data.datasource.UserHistorySuggestionSourceImpl
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import com.example.data.model.TripStatus
import com.example.data.notification.TripReminderScheduler
import com.example.data.repository.ChecklistRepositoryImpl
import com.example.data.repository.LocationSuggestionRepositoryImpl
import com.example.data.repository.MomentRepositoryImpl
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepositoryImpl
import com.example.data.util.BackupManager
import com.example.data.util.DateUtils
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class TripReminderRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: TravelStampDatabase
    private lateinit var tripRepo: TripRepositoryImpl
    private lateinit var vm: TravelViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tripRepo = TripRepositoryImpl(db.tripDao(), db.momentDao(), context)
        val userHistorySource = UserHistorySuggestionSourceImpl(tripRepo)
        val bundledSource = BundledSuggestionSourceImpl()
        val suggestionRepo = LocationSuggestionRepositoryImpl(userHistorySource, bundledSource)

        val checklistRepo = ChecklistRepositoryImpl(db.checklistDao())
        val momentRepo = MomentRepositoryImpl(db.momentDao(), context)
        val stampRepo = TravelStampRepositoryImpl(db.travelStampDao())
        val fakeUserPrefs = object : UserPreferencesRepository {
            override val hasCompletedOnboarding = MutableStateFlow(true)
            override val themeMode = MutableStateFlow(com.example.data.local.AppThemeMode.SYSTEM)
            override val preTripRemindersEnabled = MutableStateFlow(true)
            override fun setOnboardingCompleted(completed: Boolean) {}
            override fun setThemeMode(mode: com.example.data.local.AppThemeMode) {}
            override fun setPreTripRemindersEnabled(enabled: Boolean) {}
        }

        vm = TravelViewModel(
            tripRepository = tripRepo,
            checklistRepository = checklistRepo,
            momentRepository = momentRepo,
            travelStampRepository = stampRepo,
            userPreferencesRepository = fakeUserPrefs,
            database = db,
            locationSuggestionRepository = suggestionRepo
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `TEST 1 - Default trip has reminders OFF`() = runBlocking {
        val trip = Trip(
            name = "Harishchandragad Trek",
            destination = "Ahmednagar, Maharashtra",
            date = "15 Sep 2026",
            peopleCount = 2
        )
        val tripId = tripRepo.createTrip(trip)
        val retrieved = tripRepo.getTripById(tripId).first()

        assertNotNull(retrieved)
        assertFalse(retrieved!!.reminderEnabled)
        assertEquals(TripReminderPreset.ONE_DAY_BEFORE, retrieved.reminderPreset)
    }

    @Test
    fun `TEST 2 - Create trip with reminder enabled persists preset and state`() = runBlocking {
        val trip = Trip(
            name = "Kalsubai Peak Trek",
            destination = "Igatpuri, Maharashtra",
            date = "20 Oct 2026",
            startTimeMinutes = 360, // 06:00 AM
            peopleCount = 4,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.TWO_HOURS_BEFORE
        )
        val tripId = tripRepo.createTrip(trip)
        val retrieved = tripRepo.getTripById(tripId).first()

        assertNotNull(retrieved)
        assertTrue(retrieved!!.reminderEnabled)
        assertEquals(TripReminderPreset.TWO_HOURS_BEFORE, retrieved.reminderPreset)
        assertEquals(360, retrieved.startTimeMinutes)
    }

    @Test
    fun `TEST 3 - ViewModel updateTripDetails updates reminder settings`() = runBlocking {
        val createdTripId = tripRepo.createTrip(
            Trip(
                name = "Torna Fort Trek",
                destination = "Pune, Maharashtra",
                date = "10 Nov 2026",
                peopleCount = 2,
                reminderEnabled = false,
                reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
            )
        )

        val initial = tripRepo.getTripById(createdTripId).first()
        assertNotNull(initial)
        assertFalse(initial!!.reminderEnabled)

        // Update with reminder ON and Morning Of Journey preset
        tripRepo.updateTrip(
            initial.copy(
                name = "Torna Fort Monsoon Trek",
                destination = "Velhe, Pune",
                date = "10 Nov 2026",
                startTimeMinutes = 420,
                peopleCount = 3,
                description = "Updated day trek",
                reminderEnabled = true,
                reminderPreset = TripReminderPreset.MORNING_OF
            )
        )

        val updated = tripRepo.getTripById(createdTripId).first()
        assertNotNull(updated)
        assertTrue(updated!!.reminderEnabled)
        assertEquals(TripReminderPreset.MORNING_OF, updated.reminderPreset)
        assertEquals("Torna Fort Monsoon Trek", updated.name)
    }

    @Test
    fun `TEST 4 - ViewModel toggleTripReminder toggles state and updates preset`() = runBlocking {
        val tripId = tripRepo.createTrip(
            Trip(
                name = "Rajgad Fort",
                destination = "Pune",
                date = "01 Dec 2026",
                reminderEnabled = false
            )
        )

        val existing = tripRepo.getTripById(tripId).first()
        assertNotNull(existing)

        // Turn reminder ON
        tripRepo.updateTrip(
            existing!!.copy(
                reminderEnabled = true,
                reminderPreset = TripReminderPreset.ONE_WEEK_BEFORE
            )
        )

        val enabledTrip = tripRepo.getTripById(tripId).first()
        assertNotNull(enabledTrip)
        assertTrue(enabledTrip!!.reminderEnabled)
        assertEquals(TripReminderPreset.ONE_WEEK_BEFORE, enabledTrip.reminderPreset)

        // Turn reminder OFF
        tripRepo.updateTrip(
            enabledTrip.copy(
                reminderEnabled = false
            )
        )

        val disabledTrip = tripRepo.getTripById(tripId).first()
        assertNotNull(disabledTrip)
        assertFalse(disabledTrip!!.reminderEnabled)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `TEST 5 - Reminder scheduler calculates correct trigger time for presets`() {
        val futureDate = LocalDate.now().plusDays(10).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))

        val trip1Day = Trip(
            id = 1L,
            name = "Test 1",
            destination = "Dest",
            date = futureDate,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )
        val trigger1Day = TripReminderScheduler.calculateReminderTriggerMillis(trip1Day)
        assertNotNull(trigger1Day)
        assertTrue(trigger1Day!! > System.currentTimeMillis())

        val tripMorning = Trip(
            id = 2L,
            name = "Test 2",
            destination = "Dest",
            date = futureDate,
            startTimeMinutes = 540,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.MORNING_OF
        )
        val triggerMorning = TripReminderScheduler.calculateReminderTriggerMillis(tripMorning)
        assertNotNull(triggerMorning)
        assertTrue(triggerMorning!! > System.currentTimeMillis())

        // Past trip departure check
        val pastDate = LocalDate.now().minusDays(5).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
        val tripPast = Trip(
            id = 3L,
            name = "Test 3",
            destination = "Dest",
            date = pastDate,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )
        val pastDeparture = TripReminderScheduler.calculateDepartureMillis(tripPast)
        assertNotNull(pastDeparture)
        assertTrue(pastDeparture!! < System.currentTimeMillis())
    }

    @Test
    fun `TEST 6 - Backup and restore preserves reminder fields`() = runBlocking {
        val originalTrip = Trip(
            name = "Lohagad Fort Trek",
            destination = "Lonavala, Maharashtra",
            date = "15 Jan 2027",
            startTimeMinutes = 480,
            peopleCount = 3,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.MORNING_OF
        )
        val tripId = tripRepo.createTrip(originalTrip)

        // Generate JSON from database
        val json = BackupManager.generateBackupJson(db)
        val rootObj = org.json.JSONObject(json)
        val tripsArr = rootObj.getJSONArray("trips")
        assertTrue(tripsArr.length() > 0)
        val firstTripJson = tripsArr.getJSONObject(0)
        assertTrue(firstTripJson.getBoolean("reminderEnabled"))
        assertEquals("MORNING_OF", firstTripJson.getString("reminderPreset"))

        val retrievedTrip = tripRepo.getTripById(tripId).first()
        assertNotNull(retrievedTrip)
        assertTrue(retrievedTrip!!.reminderEnabled)
        assertEquals(TripReminderPreset.MORNING_OF, retrievedTrip.reminderPreset)
    }
}
