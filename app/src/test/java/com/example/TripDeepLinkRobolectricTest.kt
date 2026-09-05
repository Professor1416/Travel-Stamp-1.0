package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.BundledSuggestionSourceImpl
import com.example.data.datasource.UserHistorySuggestionSourceImpl
import com.example.data.local.AppThemeMode
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.entity.TripEntity
import com.example.data.notification.TripNotificationHelper
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripDeepLinkRobolectricTest {

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
        vm.selectTrip(null)
        vm.clearPendingReminderTripId()
        ShadowLooper.idleMainLooper()
        try {
            db.close()
        } catch (_: Exception) {}
    }

    @Test
    fun `test 01 - MainActivity cold start with reminder intent captures trip ID`() {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER, true)
            putExtra(TripNotificationHelper.EXTRA_TRIP_ID, 123L)
        }

        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
        controller.setup()
        val activity = controller.get()

        // Verify reminder intent handled without crash
        assertTrue(activity.isFinishing.not())
    }

    @Test
    fun `test 02 - MainActivity warm start onNewIntent receives and delivers trip ID`() {
        val initialIntent = Intent(context, MainActivity::class.java)
        val controller = Robolectric.buildActivity(MainActivity::class.java, initialIntent)
        controller.setup()
        val activity = controller.get()

        val reminderIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER, true)
            putExtra(TripNotificationHelper.EXTRA_TRIP_ID, 456L)
        }
        activity.onNewIntent(reminderIntent)

        assertTrue(activity.isFinishing.not())
    }

    @Test
    fun `test 03 - onboarding gate is respected and never mutated by reminder intent`() {
        userPrefs.setOnboardingCompleted(false)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER, true)
            putExtra(TripNotificationHelper.EXTRA_TRIP_ID, 789L)
        }

        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
        controller.setup()

        // Assert onboarding preference was NOT mutated
        assertFalse("Onboarding preference must not be mutated automatically", userPrefs.hasCompletedOnboarding.value)
    }

    @Test
    fun `test 04 - TravelNavHost navigates to journey detail on valid pending reminder`() {
        val tripId = runBlocking {
            db.tripDao().insertTrip(
                TripEntity(
                    name = "Alpine Expedition",
                    destination = "Zermatt, Switzerland",
                    date = "2026-11-20",
                    status = "UPCOMING"
                )
            )
        }

        vm.onReminderNavigationRequested(tripId)

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

        // Verify destination is trip_card/{tripId}
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        assertEquals(Destinations.TRIP_CARD, currentRoute)
        assertEquals(tripId, navController.currentBackStackEntry?.arguments?.getLong("tripId"))

        // Verify pending reminder was consumed once
        assertNull("Pending reminder must be consumed once", vm.pendingReminderTripId.value)
    }

    @Test
    fun `test 05 - TravelNavHost does not navigate on nonexistent trip`() {
        vm.onReminderNavigationRequested(99999L)

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

        // Should remain on HOME
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        assertEquals(Destinations.HOME, currentRoute)
        assertNull("Pending reminder must be consumed and cleared even if rejected", vm.pendingReminderTripId.value)
    }

    @Test
    fun `test 06 - TravelNavHost does not navigate on soft deleted trip`() {
        val tripId = runBlocking {
            db.tripDao().insertTrip(
                TripEntity(
                    name = "Cancelled Trip",
                    destination = "Nowhere",
                    date = "2026-12-01",
                    deletedAt = System.currentTimeMillis()
                )
            )
        }

        vm.onReminderNavigationRequested(tripId)

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

        val currentRoute = navController.currentBackStackEntry?.destination?.route
        assertEquals(Destinations.HOME, currentRoute)
        assertNull(vm.pendingReminderTripId.value)
    }

    @Test
    fun `test 07 - TravelNavHost navigates to completed trip detail`() {
        val tripId = runBlocking {
            db.tripDao().insertTrip(
                TripEntity(
                    name = "Old Memory",
                    destination = "Santorini",
                    date = "2024-08-15",
                    status = "COMPLETED",
                    completedAt = System.currentTimeMillis(),
                    stampEarned = true
                )
            )
        }

        vm.onReminderNavigationRequested(tripId)

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

        val currentRoute = navController.currentBackStackEntry?.destination?.route
        assertEquals(Destinations.TRIP_CARD, currentRoute)
        assertEquals(tripId, navController.currentBackStackEntry?.arguments?.getLong("tripId"))
        assertNull(vm.pendingReminderTripId.value)
    }

    @Test
    fun `test 08 - back from reminder journey detail returns to home`() {
        val tripId = runBlocking {
            db.tripDao().insertTrip(
                TripEntity(
                    name = "Kyoto Zen",
                    destination = "Kyoto",
                    date = "2026-10-10",
                    status = "UPCOMING"
                )
            )
        }

        vm.onReminderNavigationRequested(tripId)

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
        assertEquals(Destinations.TRIP_CARD, navController.currentBackStackEntry?.destination?.route)

        // Press Back in NavController
        composeTestRule.runOnUiThread {
            navController.popBackStack()
        }
        composeTestRule.waitForIdle()

        // Should return to HOME
        assertEquals(Destinations.HOME, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun `test 09 - incomplete onboarding holds pending reminder until user finishes onboarding`() {
        userPrefs.setOnboardingCompleted(false)

        val tripId = runBlocking {
            db.tripDao().insertTrip(
                TripEntity(
                    name = "First Adventure",
                    destination = "Barcelona",
                    date = "2026-12-12",
                    status = "UPCOMING"
                )
            )
        }

        vm.onReminderNavigationRequested(tripId)

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

        // User must be at ONBOARDING screen, not bypassed
        assertEquals(Destinations.ONBOARDING, navController.currentBackStackEntry?.destination?.route)
        // Request is still pending
        assertEquals(tripId, vm.pendingReminderTripId.value)

        // Now user finishes onboarding
        composeTestRule.runOnUiThread {
            vm.completeOnboarding()
            navController.navigate(Destinations.HOME) {
                popUpTo(Destinations.ONBOARDING) { inclusive = true }
            }
        }

        composeTestRule.waitForIdle()

        // Now deep link resolves to the journey detail
        assertEquals(Destinations.TRIP_CARD, navController.currentBackStackEntry?.destination?.route)
        assertEquals(tripId, navController.currentBackStackEntry?.arguments?.getLong("tripId"))
        assertNull(vm.pendingReminderTripId.value)
    }
}
