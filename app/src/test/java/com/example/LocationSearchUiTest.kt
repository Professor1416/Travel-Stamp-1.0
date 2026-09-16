package com.example

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.GeoapifyConfig
import com.example.data.datasource.GeoapifyResponse
import com.example.data.datasource.GeoapifyResult
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.JourneyLocation
import com.example.data.model.LocationSearchCandidate
import com.example.data.model.LocationSearchResult
import com.example.data.model.Trip
import com.example.data.repository.*
import com.example.ui.screens.LocationSearchScreen
import com.example.ui.screens.TripCardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LocationSearchUiState
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationSearchUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: TravelStampDatabase
    private lateinit var tripRepo: TripRepositoryImpl
    private lateinit var momentRepo: MomentRepositoryImpl
    private lateinit var stampRepo: TravelStampRepositoryImpl
    private lateinit var checklistRepo: ChecklistRepositoryImpl
    private lateinit var journeyLocRepo: JourneyLocationRepositoryImpl
    private lateinit var fakeLocationSearchRepo: FakeLocationSearchRepository
    private lateinit var vm: TravelViewModel

    private var testTripId: Long = 0

    class FakeLocationSearchRepository : LocationSearchRepository {
        var searchCallCount = 0
        var lastQuery: String? = null
        var resultToReturn: LocationSearchResult = LocationSearchResult.Success(emptyList())
        var delayMs: Long = 0

        override suspend fun searchLocations(query: String): LocationSearchResult {
            searchCallCount++
            lastQuery = query
            if (delayMs > 0) {
                kotlinx.coroutines.delay(delayMs)
            }
            return resultToReturn
        }
    }

    @Before
    fun setup() = runBlocking {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

        tripRepo = TripRepositoryImpl(db.tripDao(), db.momentDao(), context)
        momentRepo = MomentRepositoryImpl(db.momentDao(), context)
        stampRepo = TravelStampRepositoryImpl(db.travelStampDao())
        checklistRepo = ChecklistRepositoryImpl(db.checklistDao())
        journeyLocRepo = JourneyLocationRepositoryImpl(db.journeyLocationDao())
        fakeLocationSearchRepo = FakeLocationSearchRepository()

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
            journeyLocationRepositoryParam = journeyLocRepo,
            locationSearchRepositoryParam = fakeLocationSearchRepo
        )

        // Setup a test trip
        val trip = Trip(
            name = "Test Journey to Sahyadris",
            destination = "Maharashtra",
            date = "15 Oct 2026",
            peopleCount = 2
        )
        testTripId = tripRepo.createTrip(trip)
        vm.selectTrip(testTripId)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        vm.clearForTest()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        db.close()
    }

    @Test
    fun test1_emptyLocationsShowNoMapLocations() {
        composeTestRule.setContent {
            MyApplicationTheme {
                TripCardScreen(
                    viewModel = vm,
                    onNavigateBack = {},
                    onAddMomentClick = {},
                    onFinishTripClick = {},
                    onViewStampClick = {},
                    onAddLocationClick = {},
                    onEditLocationClick = { _, _ -> }
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(2000)
        composeTestRule.waitForIdle()

        // Scroll to bring MAP LOCATIONS into view
        composeTestRule.onNodeWithTag("trip_card_lazy_column")
            .performScrollToNode(hasTestTag("no_locations_text"))
        composeTestRule.waitForIdle()

        // Verify that the "No map locations added" message is shown
        composeTestRule.onNodeWithTag("no_locations_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_location_button").assertIsDisplayed()
    }

    @Test
    fun test2_addLocationClickTriggersCallback() {
        var addLocationClicked = false
        var passedTripId: Long? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                TripCardScreen(
                    viewModel = vm,
                    onNavigateBack = {},
                    onAddMomentClick = {},
                    onFinishTripClick = {},
                    onViewStampClick = {},
                    onAddLocationClick = { id ->
                        addLocationClicked = true
                        passedTripId = id
                    },
                    onEditLocationClick = { _, _ -> }
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(2000)
        composeTestRule.waitForIdle()

        // Scroll to bring add_location_button into view
        composeTestRule.onNodeWithTag("trip_card_lazy_column")
            .performScrollToNode(hasTestTag("add_location_button"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("add_location_button").performClick()
        assertTrue(addLocationClicked)
        assertEquals(testTripId, passedTripId)
    }

    @Test
    fun test3_rapidDuplicateSearchClicksCancelPrevious() = runBlocking {
        fakeLocationSearchRepo.delayMs = 100
        fakeLocationSearchRepo.resultToReturn = LocationSearchResult.Success(
            listOf(
                LocationSearchCandidate("1", "Rajgad Fort", "Pune, Maharashtra", 18.2476, 73.6828, "fort")
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                LocationSearchScreen(
                    tripId = testTripId,
                    locationId = null,
                    viewModel = vm,
                    onNavigateBack = {}
                )
            }
        }

        // Perform multiple queries rapidly
        val searchInput = composeTestRule.onNodeWithTag("location_search_input")
        searchInput.performTextInput("Rajgad")
        composeTestRule.onNodeWithTag("location_search_submit_button").performClick()

        // Rapidly change text and search again
        searchInput.performTextClearance()
        searchInput.performTextInput("Torna")
        composeTestRule.onNodeWithTag("location_search_submit_button").performClick()

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Verify the repository was called twice
        assertTrue(fakeLocationSearchRepo.searchCallCount >= 2)
    }

    @Test
    fun test4_correctSearchResultsAreRendered() {
        val candidate = LocationSearchCandidate("1", "Sinhagad Fort", "Pune, Maharashtra", 18.3662, 73.7558, "fort")
        fakeLocationSearchRepo.resultToReturn = LocationSearchResult.Success(listOf(candidate))

        composeTestRule.setContent {
            MyApplicationTheme {
                LocationSearchScreen(
                    tripId = testTripId,
                    locationId = null,
                    viewModel = vm,
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("location_search_input").performTextInput("Sinhagad")
        composeTestRule.onNodeWithTag("location_search_submit_button").performClick()
        
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Verify that candidate is rendered
        composeTestRule.onNodeWithTag("candidate_item_Sinhagad_Fort").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sinhagad Fort").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pune, Maharashtra").assertIsDisplayed()
    }

    @Test
    fun test5_searchFailuresMapToCorrectMessages() {
        // A. NoNetwork
        fakeLocationSearchRepo.resultToReturn = LocationSearchResult.NoNetwork
        composeTestRule.setContent {
            MyApplicationTheme {
                LocationSearchScreen(
                    tripId = testTripId,
                    locationId = null,
                    viewModel = vm,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("location_search_input").performTextInput("Test Query")
        composeTestRule.onNodeWithTag("location_search_submit_button").performClick()
        
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("no_network_title").assertIsDisplayed()

        // B. Timeout
        fakeLocationSearchRepo.resultToReturn = LocationSearchResult.Timeout
        composeTestRule.onNodeWithTag("location_search_submit_button").performClick()
        
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("timeout_title").assertIsDisplayed()

        // C. RateLimited
        fakeLocationSearchRepo.resultToReturn = LocationSearchResult.RateLimited
        composeTestRule.onNodeWithTag("location_search_submit_button").performClick()
        
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rate_limit_title").assertIsDisplayed()

        // D. ProviderUnavailable
        fakeLocationSearchRepo.resultToReturn = LocationSearchResult.ProviderUnavailable
        composeTestRule.onNodeWithTag("location_search_submit_button").performClick()
        
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("provider_unavailable_title").assertIsDisplayed()
    }

    @Test
    fun test6_clickingCandidateThenCancelDoesNotInsert() = runBlocking {
        val candidate = LocationSearchCandidate("1", "Rajmachi Fort", "Lonavala", 18.8247, 73.3986, "fort")
        fakeLocationSearchRepo.resultToReturn = LocationSearchResult.Success(listOf(candidate))

        composeTestRule.setContent {
            MyApplicationTheme {
                LocationSearchScreen(
                    tripId = testTripId,
                    locationId = null,
                    viewModel = vm,
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("location_search_input").performTextInput("Rajmachi")
        composeTestRule.onNodeWithTag("location_search_submit_button").performClick()
        
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("candidate_item_Rajmachi_Fort").performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Click Cancel in Confirmation dialog
        composeTestRule.onNodeWithTag("cancel_add_location_button").performClick()
        
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Verify nothing inserted in DB
        val locs = journeyLocRepo.getLocationsForTripSync(testTripId)
        assertTrue(locs.isEmpty())
    }

    @Test
    fun test7_clickingCandidateThenConfirmInsertsSuccessfully() = runBlocking {
        val candidate = LocationSearchCandidate("1", "Korigad Fort", "Lonavala", 18.6185, 73.3853, "fort")
        fakeLocationSearchRepo.resultToReturn = LocationSearchResult.Success(listOf(candidate))

        var backNavigated = false
        composeTestRule.setContent {
            MyApplicationTheme {
                LocationSearchScreen(
                    tripId = testTripId,
                    locationId = null,
                    viewModel = vm,
                    onNavigateBack = { backNavigated = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("location_search_input").performTextInput("Korigad")
        composeTestRule.onNodeWithTag("location_search_submit_button").performClick()
        
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("candidate_item_Korigad_Fort").performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Click Add Location in Confirmation dialog
        composeTestRule.onNodeWithTag("confirm_add_location_button").performClick()
        
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Verify back navigation occurred
        assertTrue(backNavigated)

        // Verify inserted correctly
        val locs = journeyLocRepo.getLocationsForTripSync(testTripId)
        assertEquals(1, locs.size)
        assertEquals("Korigad Fort", locs[0].label)
        assertEquals(18.6185, locs[0].latitude, 0.0001)
        assertEquals(73.3853, locs[0].longitude, 0.0001)
        assertEquals(1, locs[0].sortOrder)
    }

    @Test
    fun test8_editAndRemoveOptionsWork() = runBlocking {
        // Pre-insert two locations to test Edit/Remove
        val loc1 = JourneyLocation(tripId = testTripId, label = "Tikona Fort", latitude = 18.6312, longitude = 73.5083, sortOrder = 1)
        val loc2 = JourneyLocation(tripId = testTripId, label = "Lohagad Fort", latitude = 18.6976, longitude = 73.4839, sortOrder = 2)
        val id1 = journeyLocRepo.insertLocation(loc1)
        val id2 = journeyLocRepo.insertLocation(loc2)

        var editCallbackCalled = false
        var editTripId: Long? = null
        var editLocId: Long? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                TripCardScreen(
                    viewModel = vm,
                    onNavigateBack = {},
                    onAddMomentClick = {},
                    onFinishTripClick = {},
                    onViewStampClick = {},
                    onAddLocationClick = {},
                    onEditLocationClick = { tId, lId ->
                        editCallbackCalled = true
                        editTripId = tId
                        editLocId = lId
                    }
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(2000)
        composeTestRule.waitForIdle()

        // Scroll to bring Tikona Fort into view
        composeTestRule.onNodeWithTag("trip_card_lazy_column")
            .performScrollToNode(hasText("Tikona Fort"))
        composeTestRule.waitForIdle()

        // Verify both locations displayed
        composeTestRule.onNodeWithText("Tikona Fort").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lohagad Fort").assertIsDisplayed()

        // Edit location 1
        composeTestRule.onNodeWithTag("trip_card_lazy_column")
            .performScrollToNode(hasTestTag("edit_location_$id1"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_location_$id1").performClick()
        assertTrue(editCallbackCalled)
        assertEquals(testTripId, editTripId)
        assertEquals(id1, editLocId)

        // Remove location 2
        composeTestRule.onNodeWithTag("trip_card_lazy_column")
            .performScrollToNode(hasTestTag("remove_location_$id2"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("remove_location_$id2").performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Confirm deletion in dialog
        composeTestRule.onNodeWithTag("confirm_remove_location_button").performClick()
        
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Verify second location is deleted from DB
        val locs = journeyLocRepo.getLocationsForTripSync(testTripId)
        assertEquals(1, locs.size)
        assertEquals("Tikona Fort", locs[0].label)
    }
}
