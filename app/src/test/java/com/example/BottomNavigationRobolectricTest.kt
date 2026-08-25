package com.example

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.BundledSuggestionSourceImpl
import com.example.data.datasource.UserHistorySuggestionSourceImpl
import com.example.data.local.AppThemeMode
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.ChecklistRepositoryImpl
import com.example.data.repository.LocationSuggestionRepositoryImpl
import com.example.data.repository.MomentRepositoryImpl
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepositoryImpl
import com.example.ui.navigation.Destinations
import com.example.ui.navigation.TravelNavHost
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BottomNavigationRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: TravelStampDatabase
    private lateinit var tripRepo: TripRepositoryImpl
    private lateinit var stampRepo: TravelStampRepositoryImpl
    private lateinit var checklistRepo: ChecklistRepositoryImpl
    private lateinit var momentRepo: MomentRepositoryImpl
    private lateinit var userPrefs: UserPreferencesRepository
    private lateinit var suggestionRepo: LocationSuggestionRepositoryImpl
    private lateinit var vm: TravelViewModel
    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        tripRepo = TripRepositoryImpl(db.tripDao(), db.momentDao(), context)
        stampRepo = TravelStampRepositoryImpl(db.travelStampDao())
        checklistRepo = ChecklistRepositoryImpl(db.checklistDao())
        momentRepo = MomentRepositoryImpl(db.momentDao(), context)
        val userHistorySource = UserHistorySuggestionSourceImpl(tripRepo)
        val bundledSource = BundledSuggestionSourceImpl()
        suggestionRepo = LocationSuggestionRepositoryImpl(userHistorySource, bundledSource)

        userPrefs = object : UserPreferencesRepository {
            override val hasCompletedOnboarding = MutableStateFlow(true)
            override val themeMode = MutableStateFlow(AppThemeMode.LIGHT)
            override val preTripRemindersEnabled = MutableStateFlow(true)

            override fun setOnboardingCompleted(completed: Boolean) {
                (hasCompletedOnboarding as MutableStateFlow).value = completed
            }

            override fun setThemeMode(mode: AppThemeMode) {
                (themeMode as MutableStateFlow).value = mode
            }

            override fun setPreTripRemindersEnabled(enabled: Boolean) {
                (preTripRemindersEnabled as MutableStateFlow).value = enabled
            }
        }

        vm = TravelViewModel(
            tripRepository = tripRepo,
            checklistRepository = checklistRepo,
            momentRepository = momentRepo,
            travelStampRepository = stampRepo,
            userPreferencesRepository = userPrefs,
            database = db,
            locationSuggestionRepository = suggestionRepo
        )

        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun setupNavHost() {
        composeTestRule.setContent {
            navController = rememberNavController()
            MyApplicationTheme {
                TravelNavHost(
                    viewModel = vm,
                    navController = navController
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `TEST 1 - Home screen displays bottom navigation with 3 tabs and Home selected`() {
        setupNavHost()

        composeTestRule.onNodeWithTag("travel_bottom_navigation_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_nav_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_nav_passport").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_nav_settings").assertIsDisplayed()

        // Home should be selected
        composeTestRule.onNodeWithTag("bottom_nav_home").assertIsSelected()
    }

    @Test
    fun `TEST 2 - Navigation from Home to Passport displays Passport without back arrow and with Passport selected`() {
        setupNavHost()

        // Tap Passport tab
        composeTestRule.onNodeWithTag("bottom_nav_passport").performClick()
        composeTestRule.waitForIdle()

        // Title displayed
        composeTestRule.onNodeWithText("MY TRAVEL PASSPORT").assertIsDisplayed()

        // Passport tab selected
        composeTestRule.onNodeWithTag("bottom_nav_passport").assertIsSelected()

        // Root Passport must NOT display the back arrow
        composeTestRule.onNodeWithTag("collection_back_button").assertDoesNotExist()

        // Bottom bar remains displayed
        composeTestRule.onNodeWithTag("travel_bottom_navigation_bar").assertIsDisplayed()
    }

    @Test
    fun `TEST 3 - Navigation from Passport to Settings displays Settings without back arrow and with Settings selected`() {
        setupNavHost()

        // Tap Passport tab then Settings tab
        composeTestRule.onNodeWithTag("bottom_nav_passport").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeTestRule.waitForIdle()

        // Title displayed
        composeTestRule.onNodeWithText("SETTINGS & BACKUP").assertIsDisplayed()

        // Settings tab selected
        composeTestRule.onNodeWithTag("bottom_nav_settings").assertIsSelected()

        // Root Settings must NOT display the back arrow
        composeTestRule.onNodeWithTag("settings_back_button").assertDoesNotExist()

        // Bottom bar remains displayed
        composeTestRule.onNodeWithTag("travel_bottom_navigation_bar").assertIsDisplayed()
    }

    @Test
    fun `TEST 4 - Navigation cycle Home to Passport to Settings to Home maintains single top backstack`() {
        setupNavHost()

        // Cycle through tabs
        composeTestRule.onNodeWithTag("bottom_nav_passport").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_nav_passport").assertIsSelected()

        composeTestRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_nav_settings").assertIsSelected()

        composeTestRule.onNodeWithTag("bottom_nav_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_nav_home").assertIsSelected()

        // Repeated taps on current tab do not crash or alter selected state
        composeTestRule.onNodeWithTag("bottom_nav_home").performClick()
        composeTestRule.onNodeWithTag("bottom_nav_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_nav_home").assertIsSelected()
    }

    @Test
    fun `TEST 5 - Rapid switching between all 3 tabs does not crash and lands on final destination`() {
        setupNavHost()

        // Rapid switches
        composeTestRule.onNodeWithTag("bottom_nav_passport").performClick()
        composeTestRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeTestRule.onNodeWithTag("bottom_nav_home").performClick()
        composeTestRule.onNodeWithTag("bottom_nav_passport").performClick()
        composeTestRule.onNodeWithTag("bottom_nav_home").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("bottom_nav_home").assertIsSelected()
    }

    @Test
    fun `TEST 6 - Detail task flow Create Trip hides bottom navigation bar`() {
        setupNavHost()

        // Navigate to Create Trip from Home primary button
        composeTestRule.onNodeWithTag("create_trip_button").performClick()
        composeTestRule.waitForIdle()

        // Bottom bar must NOT be displayed on Create Trip
        composeTestRule.onNodeWithTag("travel_bottom_navigation_bar").assertDoesNotExist()

        // Back button returns to Home and restores bottom bar
        composeTestRule.onNodeWithTag("create_trip_back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("bottom_nav_home").assertIsSelected()
        composeTestRule.onNodeWithTag("travel_bottom_navigation_bar").assertIsDisplayed()
    }

    @Test
    fun `TEST 7 - Settings to About hides bottom navigation and back returns with bottom navigation`() {
        setupNavHost()

        // Navigate to Settings
        composeTestRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeTestRule.waitForIdle()

        // Click About row in Settings
        composeTestRule.onNodeWithTag("about_travel_stamp_row").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Bottom bar must NOT be displayed on About screen
        composeTestRule.onNodeWithTag("travel_bottom_navigation_bar").assertDoesNotExist()

        // Back returns to Settings
        composeTestRule.onNodeWithTag("about_back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("travel_bottom_navigation_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_nav_settings").assertIsSelected()
    }

    @Test
    fun `TEST 8 - Trip Card detail screen hides bottom navigation`() {
        setupNavHost()

        // Navigate directly to Trip Card
        composeTestRule.runOnUiThread {
            navController.navigate(Destinations.tripCard(1L))
        }
        composeTestRule.waitForIdle()

        // Bottom bar must NOT be displayed on Trip Card
        composeTestRule.onNodeWithTag("travel_bottom_navigation_bar").assertDoesNotExist()

        // Pop back stack returns to Home
        composeTestRule.runOnUiThread {
            navController.popBackStack()
        }
        composeTestRule.waitForIdle()

        // Bottom bar is restored on Home
        composeTestRule.onNodeWithTag("travel_bottom_navigation_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_nav_home").assertIsSelected()
    }

    @Test
    fun `TEST 9 - Home does not contain redundant top settings button and Settings is accessed via bottom bar`() {
        setupNavHost()

        // Redundant top settings gear must NOT exist on Home screen
        composeTestRule.onNodeWithTag("settings_button").assertDoesNotExist()

        // Settings is cleanly accessed via the persistent bottom navigation bar
        composeTestRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("bottom_nav_settings").assertIsSelected()
        composeTestRule.onNodeWithTag("travel_bottom_navigation_bar").assertIsDisplayed()
    }

    @Test
    fun `TEST 10 - State preservation across tab switches`() {
        setupNavHost()

        // Go to Passport
        composeTestRule.onNodeWithTag("bottom_nav_passport").performClick()
        composeTestRule.waitForIdle()

        // Type a search query in Passport
        composeTestRule.onNodeWithTag("passport_search_input").performTextInput("Sahyadri")
        composeTestRule.waitForIdle()

        // Switch to Settings and back to Passport
        composeTestRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("bottom_nav_passport").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_nav_passport").assertIsSelected()

        // Search text is preserved
        composeTestRule.onNodeWithText("Sahyadri").assertIsDisplayed()
    }

    @Test
    fun `TEST 11 - All detail routes hide bottom navigation bar`() {
        setupNavHost()

        val detailRoutes = listOf(
            Destinations.CREATE_TRIP,
            Destinations.tripCard(1L),
            Destinations.addMoment(1L),
            Destinations.editMoment(1L, 1L),
            Destinations.finishTrip(1L),
            Destinations.travelStamp(1L),
            Destinations.posterExport(1L),
            Destinations.ABOUT
        )

        for (route in detailRoutes) {
            composeTestRule.runOnUiThread {
                navController.navigate(route)
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("travel_bottom_navigation_bar").assertDoesNotExist()

            composeTestRule.runOnUiThread {
                navController.popBackStack()
            }
            composeTestRule.waitForIdle()
        }

        // Must be back on Home with bottom bar shown
        composeTestRule.onNodeWithTag("travel_bottom_navigation_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_nav_home").assertIsSelected()
    }
}
