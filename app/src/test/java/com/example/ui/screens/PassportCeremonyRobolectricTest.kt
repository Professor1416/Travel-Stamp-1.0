package com.example.ui.screens

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.BundledSuggestionSourceImpl
import com.example.data.datasource.UserHistorySuggestionSourceImpl
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.TravelStamp
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.data.repository.ChecklistRepositoryImpl
import com.example.data.repository.LocationSuggestionRepositoryImpl
import com.example.data.repository.MomentRepositoryImpl
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepositoryImpl
import com.example.ui.viewmodel.TravelViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class PassportCeremonyRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var db: TravelStampDatabase
    private lateinit var tripRepo: TripRepositoryImpl
    private lateinit var stampRepo: TravelStampRepositoryImpl
    private lateinit var checklistRepo: ChecklistRepositoryImpl
    private lateinit var momentRepo: MomentRepositoryImpl
    private lateinit var vm: TravelViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tripRepo = TripRepositoryImpl(db.tripDao(), db.momentDao(), context)
        checklistRepo = ChecklistRepositoryImpl(db.checklistDao())
        momentRepo = MomentRepositoryImpl(db.momentDao(), context)
        stampRepo = TravelStampRepositoryImpl(db.travelStampDao())

        val userHistorySource = UserHistorySuggestionSourceImpl(tripRepo)
        val bundledSource = BundledSuggestionSourceImpl()
        val suggestionRepo = LocationSuggestionRepositoryImpl(userHistorySource, bundledSource)

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

    // 1. Successful already-issued stamp -> Ceremony content appears.
    @Test
    fun testCeremonyDisplaysSuccessfullyIssuedStamp(): Unit = runBlocking {
        val trip = Trip(
            name = "Harishchandragad Trek",
            destination = "Western Ghats, Maharashtra",
            date = "10 Aug 2026",
            peopleCount = 4
        )
        val tripId = tripRepo.createTrip(trip)
        val stampResult = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Harishchandragad Trek",
            destination = "Western Ghats, Maharashtra",
            dateText = "10 Aug 2026",
            peopleCount = 4,
            momentsCount = 2,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Spectacular sunset",
            completedAt = System.currentTimeMillis()
        )
        assertTrue(stampResult.isSuccess)
        val issuedStamp = stampResult.getOrThrow()
        assertEquals("#001", issuedStamp.stampCode)

        val completedTrip = tripRepo.getTripByIdSync(tripId)
        assertNotNull(completedTrip)
        assertEquals(TripStatus.COMPLETED, completedTrip!!.status)

        composeTestRule.setContent {
            PassportCeremonyContent(
                tripId = tripId,
                trip = completedTrip,
                stamp = issuedStamp,
                onViewInPassport = {},
                onCreateStampEdition = {},
                onExpeditionLog = {},
                initialPhase = PassportCeremonyPhase.COMPLETE
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("JOURNEY COMPLETE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ceremony_stamp").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ceremony_added_to_passport").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("ceremony_view_passport_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("ceremony_create_edition_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("ceremony_trip_log_button").performScrollTo().assertIsDisplayed()
    }

    // 2. Ceremony does NOT issue a stamp.
    @Test
    fun testCeremonyDoesNotMutateDatabaseOrIssueStamps(): Unit = runBlocking {
        val initialStampCount = db.travelStampDao().getStampsCountSync()
        assertEquals(0, initialStampCount)

        val trip = Trip(
            id = 42L,
            name = "Sinhagad Fort",
            destination = "Pune, Maharashtra",
            date = "05 Aug 2026",
            status = TripStatus.COMPLETED
        )
        val testStamp = TravelStamp(
            id = 10L,
            tripId = 42L,
            stampNumber = 1L,
            stampCode = "#001",
            title = "Sinhagad Fort",
            destination = "Pune, Maharashtra",
            dateText = "05 Aug 2026",
            peopleCount = 2,
            momentsCount = 1
        )

        composeTestRule.setContent {
            PassportCeremonyContent(
                tripId = 42L,
                trip = trip,
                stamp = testStamp,
                onViewInPassport = {},
                onCreateStampEdition = {},
                onExpeditionLog = {},
                initialPhase = PassportCeremonyPhase.COMPLETE
            )
        }
        composeTestRule.waitForIdle()

        val postStampCount = db.travelStampDao().getStampsCountSync()
        assertEquals(0, postStampCount)
    }

    // 3. Completion flow still results in exactly one TravelStamp row.
    @Test
    fun testCompletionFlowResultsInExactlyOneStampRow(): Unit = runBlocking {
        val tripId = tripRepo.createTrip(Trip(name = "Rajgad", destination = "Pune", date = "01 Jan 2026"))
        val result = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Rajgad",
            destination = "Pune",
            dateText = "01 Jan 2026",
            peopleCount = 2,
            momentsCount = 1,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Sealed",
            completedAt = System.currentTimeMillis()
        )
        assertTrue(result.isSuccess)
        assertEquals(1, db.travelStampDao().getStampsCountSync())
    }

    // 4. Double completion remains idempotent.
    @Test
    fun testDoubleCompletionRemainsIdempotent(): Unit = runBlocking {
        val tripId = tripRepo.createTrip(Trip(name = "Lonavala", destination = "Pune", date = "02 Feb 2026"))
        val result1 = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Lonavala",
            destination = "Pune",
            dateText = "02 Feb 2026",
            peopleCount = 1,
            momentsCount = 0,
            inkColorHex = "#1E3A2F",
            stampStyle = "EXPLORER",
            reflectionNote = null,
            completedAt = System.currentTimeMillis()
        )
        assertTrue(result1.isSuccess)

        val result2 = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Lonavala",
            destination = "Pune",
            dateText = "02 Feb 2026",
            peopleCount = 1,
            momentsCount = 0,
            inkColorHex = "#1E3A2F",
            stampStyle = "EXPLORER",
            reflectionNote = null,
            completedAt = System.currentTimeMillis()
        )
        assertTrue(result2.isSuccess)
        assertEquals(result1.getOrThrow().id, result2.getOrThrow().id)
        assertEquals(1, db.travelStampDao().getStampsCountSync())
    }

    // 5. Same trip/stamp number reaches ceremony.
    @Test
    fun testSameTripAndStampNumberReachesCeremony(): Unit = runBlocking {
        val tripId = tripRepo.createTrip(Trip(name = "Prabalmachi", destination = "Panvel", date = "03 Mar 2026"))
        val result = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Prabalmachi",
            destination = "Panvel",
            dateText = "03 Mar 2026",
            peopleCount = 3,
            momentsCount = 2,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = null,
            completedAt = System.currentTimeMillis()
        )
        val stamp = result.getOrThrow()
        assertEquals(tripId, stamp.tripId)
        assertEquals(1L, stamp.stampNumber)
    }

    // 6. Back during active animation -> COMPLETE.
    @Test
    fun testBackDuringActiveAnimationSkipsToComplete(): Unit = runBlocking {
        var passportClicked = false
        val trip = Trip(id = 55L, name = "Kalsubai", destination = "Igatpuri", date = "04 Apr 2026", status = TripStatus.COMPLETED)
        val stamp = TravelStamp(id = 55L, tripId = 55L, stampNumber = 1L, stampCode = "#001", title = "Kalsubai", destination = "Igatpuri", dateText = "04 Apr 2026", peopleCount = 2, momentsCount = 1)

        composeTestRule.setContent {
            PassportCeremonyContent(
                tripId = 55L,
                trip = trip,
                stamp = stamp,
                onViewInPassport = { passportClicked = true },
                onCreateStampEdition = {},
                onExpeditionLog = {},
                initialPhase = PassportCeremonyPhase.ENTRY
            )
        }

        // Press back while in animation: should skip to COMPLETE without navigating out
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()

        assertFalse(passportClicked)
        composeTestRule.onNodeWithTag("ceremony_added_to_passport").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("ceremony_view_passport_button").performScrollTo().assertIsDisplayed()
    }

    // 7. Back when COMPLETE -> Passport navigation callback.
    @Test
    fun testBackWhenCompleteNavigatesToPassport(): Unit = runBlocking {
        var passportClicked = false
        val trip = Trip(id = 56L, name = "Alang Fort", destination = "Nashik", date = "05 May 2026", status = TripStatus.COMPLETED)
        val stamp = TravelStamp(id = 56L, tripId = 56L, stampNumber = 1L, stampCode = "#001", title = "Alang Fort", destination = "Nashik", dateText = "05 May 2026", peopleCount = 2, momentsCount = 1)

        composeTestRule.setContent {
            PassportCeremonyContent(
                tripId = 56L,
                trip = trip,
                stamp = stamp,
                onViewInPassport = { passportClicked = true },
                onCreateStampEdition = {},
                onExpeditionLog = {},
                initialPhase = PassportCeremonyPhase.COMPLETE
            )
        }

        // Press back when COMPLETE: should invoke passport navigation
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()

        assertTrue(passportClicked)
    }

    // 8 & 9. Impact haptic guarded to max once, no duplicate on recomposition.
    @Test
    fun testImpactHapticGuardedToOneShotPolicy() {
        var hasFired = false

        // Not in IMPACT
        assertFalse(shouldTriggerImpactHaptic(hasFired, PassportCeremonyPhase.ENTRY))
        assertFalse(shouldTriggerImpactHaptic(hasFired, PassportCeremonyPhase.PRE_PRESS))

        // Entering IMPACT: should trigger
        assertTrue(shouldTriggerImpactHaptic(hasFired, PassportCeremonyPhase.IMPACT))
        hasFired = true

        // Recomposition in IMPACT: guarded against repeat
        assertFalse(shouldTriggerImpactHaptic(hasFired, PassportCeremonyPhase.IMPACT))

        // Leaving IMPACT
        assertFalse(shouldTriggerImpactHaptic(hasFired, PassportCeremonyPhase.RESOLVE))
        assertFalse(shouldTriggerImpactHaptic(hasFired, PassportCeremonyPhase.COMPLETE))
    }

    // 10. Configuration recreation -> COMPLETE / no reward replay.
    @Test
    fun testConfigurationRecreationRestoresComplete() {
        val trip = Trip(id = 60L, name = "Raigad", destination = "Mahad", date = "06 Jun 2026", status = TripStatus.COMPLETED)
        val stamp = TravelStamp(id = 60L, tripId = 60L, stampNumber = 1L, stampCode = "#001", title = "Raigad", destination = "Mahad", dateText = "06 Jun 2026", peopleCount = 2, momentsCount = 1)

        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            PassportCeremonyContent(
                tripId = 60L,
                trip = trip,
                stamp = stamp,
                onViewInPassport = {},
                onCreateStampEdition = {},
                onExpeditionLog = {},
                initialPhase = PassportCeremonyPhase.ENTRY
            )
        }

        // Emulate config change / saved instance state restore
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        // Restored state must be COMPLETE
        composeTestRule.onNodeWithTag("ceremony_added_to_passport").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("ceremony_view_passport_button").performScrollTo().assertIsDisplayed()
    }

    // 11. VIEW IN PASSPORT callback executes once.
    @Test
    fun testViewInPassportCallbackExecutesOnce() {
        val trip = Trip(id = 70L, name = "Sudhagad", destination = "Pali", date = "07 Jul 2026", status = TripStatus.COMPLETED)
        val stamp = TravelStamp(id = 70L, tripId = 70L, stampNumber = 1L, stampCode = "#001", title = "Sudhagad", destination = "Pali", dateText = "07 Jul 2026", peopleCount = 2, momentsCount = 1)
        var callCount = 0

        composeTestRule.setContent {
            PassportCeremonyContent(
                tripId = 70L,
                trip = trip,
                stamp = stamp,
                onViewInPassport = { callCount++ },
                onCreateStampEdition = {},
                onExpeditionLog = {},
                initialPhase = PassportCeremonyPhase.COMPLETE
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ceremony_view_passport_button").performScrollTo().performClick()
        assertEquals(1, callCount)
    }

    // 12 & 13. CREATE STAMP EDITION and Expedition Log carry same tripId.
    @Test
    fun testNavigationActionsCarryCorrectTripId(): Unit = runBlocking {
        val tripId = 99L
        val trip = Trip(
            id = tripId,
            name = "Torna Fort",
            destination = "Velhe, Maharashtra",
            date = "12 Aug 2026",
            status = TripStatus.COMPLETED
        )
        val stamp = TravelStamp(
            id = 7L,
            tripId = tripId,
            stampNumber = 2L,
            stampCode = "#002",
            title = "Torna Fort",
            destination = "Velhe, Maharashtra",
            dateText = "12 Aug 2026",
            peopleCount = 3,
            momentsCount = 2
        )

        var passportClicked = false
        var editionTripId: Long? = null
        var logTripId: Long? = null

        composeTestRule.setContent {
            PassportCeremonyContent(
                tripId = tripId,
                trip = trip,
                stamp = stamp,
                onViewInPassport = { passportClicked = true },
                onCreateStampEdition = { id: Long -> editionTripId = id },
                onExpeditionLog = { id: Long -> logTripId = id },
                initialPhase = PassportCeremonyPhase.COMPLETE
            )
        }
        composeTestRule.waitForIdle()

        // Click Primary: VIEW IN PASSPORT
        composeTestRule.onNodeWithTag("ceremony_view_passport_button").performScrollTo().performClick()
        assertTrue(passportClicked)

        // Click Secondary: CREATE STAMP EDITION
        composeTestRule.onNodeWithTag("ceremony_create_edition_button").performScrollTo().performClick()
        assertEquals(tripId, editionTripId)

        // Click Tertiary: Expedition Log
        composeTestRule.onNodeWithTag("ceremony_trip_log_button").performScrollTo().performClick()
        assertEquals(tripId, logTripId)
    }

    // Deterministic tilt tests
    @Test
    fun testDeterministicTiltCalculation() {
        val angle1 = calculateDeterministicTilt(1L, 1L)
        val angle2 = calculateDeterministicTilt(1L, 1L)
        assertEquals(angle1, angle2, 0.0001f)

        val angleDiff = calculateDeterministicTilt(2L, 2L)
        assertTrue(angle1 in -1.8f..1.8f)
        assertTrue(angleDiff in -1.8f..1.8f)

        // Test negative seeds and boundary numbers
        val negTilt = calculateDeterministicTilt(-99L, -5L)
        assertTrue(negTilt in -1.8f..1.8f)
    }

    // 14. Missing/invalid trip -> safe error state, no issuance.
    @Test
    fun testMissingStampShowsSafeErrorWithoutRecoveryIssuance(): Unit = runBlocking {
        var returnClicked = false
        val initialCount = db.travelStampDao().getStampsCountSync()

        composeTestRule.setContent {
            PassportCeremonyContent(
                tripId = 888L,
                trip = null,
                stamp = null,
                onViewInPassport = { returnClicked = true },
                onCreateStampEdition = {},
                onExpeditionLog = {},
                isMissingDataError = true
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ceremony_error_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ceremony_error_return_button").performClick()
        assertTrue(returnClicked)

        val afterCount = db.travelStampDao().getStampsCountSync()
        assertEquals(initialCount, afterCount)
    }

    // 17. Reminder completion behavior remains unaffected: completed journey must not regain pre-trip reminders.
    @Test
    fun testCompletedJourneyDoesNotRegainPreTripReminders(): Unit = runBlocking {
        val tripId = tripRepo.createTrip(Trip(name = "Korigad", destination = "Aamby Valley", date = "15 Jan 2024", reminderEnabled = true))
        val activeTrip = tripRepo.getTripByIdSync(tripId)
        assertNotNull(activeTrip)
        assertNotEquals(TripStatus.COMPLETED, activeTrip!!.status)

        // Complete the trip
        val stampRes = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Korigad",
            destination = "Aamby Valley",
            dateText = "15 Jan 2024",
            peopleCount = 2,
            momentsCount = 1,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = null,
            completedAt = System.currentTimeMillis()
        )
        assertTrue(stampRes.isSuccess)

        val completedTrip = tripRepo.getTripByIdSync(tripId)
        assertNotNull(completedTrip)
        assertEquals(TripStatus.COMPLETED, completedTrip!!.status)
        // Completed trips in repository never remain in active trips
        val activeTrips = tripRepo.getActiveTrips().first()
        assertFalse(activeTrips.any { it.id == tripId })
    }
}
