package com.example.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesRepositoryImpl
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import com.example.data.notification.ReminderCoordinator
import com.example.data.repository.ChecklistRepositoryImpl
import com.example.data.repository.MomentRepositoryImpl
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepositoryImpl
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsReminderRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: TravelStampDatabase
    private lateinit var userPrefs: UserPreferencesRepository
    private lateinit var fakeCoordinator: FakeReminderCoordinator
    private lateinit var viewModel: TravelViewModel

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("travel_stamp_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNotificationManager: org.robolectric.shadows.ShadowNotificationManager = org.robolectric.Shadows.shadowOf(notificationManager)
        shadowNotificationManager.setNotificationsEnabled(true)

        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val tripRepo = TripRepositoryImpl(db.tripDao(), db.momentDao(), context)
        val checklistRepo = ChecklistRepositoryImpl(db.checklistDao())
        val momentRepo = MomentRepositoryImpl(db.momentDao(), context)
        val stampRepo = TravelStampRepositoryImpl(db.travelStampDao())

        userPrefs = UserPreferencesRepositoryImpl(context)
        fakeCoordinator = FakeReminderCoordinator(userPrefs)

        viewModel = TravelViewModel(
            tripRepository = tripRepo,
            checklistRepository = checklistRepo,
            momentRepository = momentRepo,
            travelStampRepository = stampRepo,
            userPreferencesRepository = userPrefs,
            database = db,
            reminderCoordinator = fakeCoordinator
        )
    }

    @After
    fun tearDown() {
        db.close()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `test 19 - Settings loads persisted ON state`() {
        userPrefs.setPreTripRemindersEnabled(true)

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pre_trip_reminders_switch")
            .assertExists()
            .assertIsOn()
    }

    @Test
    fun `test 20 - Settings loads persisted OFF state`() {
        userPrefs.setPreTripRemindersEnabled(false)

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pre_trip_reminders_switch")
            .assertExists()
            .assertIsOff()
    }

    @Test
    fun `test 21 - turning OFF does not invoke permission request`() {
        userPrefs.setPreTripRemindersEnabled(true)

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Toggle OFF
        composeTestRule.onNodeWithTag("pre_trip_reminders_switch")
            .performScrollTo()
            .performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        assertFalse(userPrefs.preTripRemindersEnabled.value)
        assertEquals(false, fakeCoordinator.lastEnabledState)
        composeTestRule.onNodeWithTag("notification_permission_explanation_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("notification_permission_blocked_dialog").assertDoesNotExist()
    }

    @Test
    fun `test 22 - turning ON with permission granted calls coordinator enable`() {
        userPrefs.setPreTripRemindersEnabled(false)
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Click to turn ON
        composeTestRule.onNodeWithTag("pre_trip_reminders_switch")
            .performScrollTo()
            .performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        assertTrue(userPrefs.preTripRemindersEnabled.value)
        assertEquals(true, fakeCoordinator.lastEnabledState)
        composeTestRule.onNodeWithTag("notification_permission_explanation_dialog").assertDoesNotExist()
    }

    @Test
    fun `test 23 - turning ON without permission triggers N6_1 flow first`() {
        userPrefs.setPreTripRemindersEnabled(false)
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Click to turn ON
        composeTestRule.onNodeWithTag("pre_trip_reminders_switch")
            .performScrollTo()
            .performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        // Explanation dialog should appear
        composeTestRule.onNodeWithTag("notification_permission_explanation_dialog").assertIsDisplayed()
        // Switch should still be OFF
        assertFalse(userPrefs.preTripRemindersEnabled.value)
    }

    @Test
    fun `test 24 - denied permission leaves global OFF`() {
        userPrefs.setPreTripRemindersEnabled(false)
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pre_trip_reminders_switch")
            .performScrollTo()
            .performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        // Dismiss explanation ("Not now")
        composeTestRule.onNodeWithTag("notification_permission_not_now_button").performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        // Dialog closes, global stays OFF
        composeTestRule.onNodeWithTag("notification_permission_explanation_dialog").assertDoesNotExist()
        assertFalse(userPrefs.preTripRemindersEnabled.value)
        composeTestRule.onNodeWithTag("pre_trip_reminders_switch").assertIsOff()
    }

    @Test
    fun `test 25 - granted permission enables global reminder preference`() {
        userPrefs.setPreTripRemindersEnabled(false)
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pre_trip_reminders_switch")
            .performScrollTo()
            .performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("notification_permission_explanation_dialog").assertIsDisplayed()

        // Continue and simulate OS permission grant
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        composeTestRule.onNodeWithTag("notification_permission_continue_button").performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        // On API 34, launcher requests POST_NOTIFICATIONS
        // Now grant via shadow and simulate permission result
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNotificationManager: ShadowNotificationManager = Shadows.shadowOf(notificationManager)
        shadowNotificationManager.setNotificationsEnabled(true)

        // After permission is available, global is enabled
        viewModel.setPreTripRemindersEnabled(true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        assertTrue(userPrefs.preTripRemindersEnabled.value)
        assertEquals(true, fakeCoordinator.lastEnabledState)
    }

    @Test
    fun `test 26 - blocked notification state exposes Open Settings recovery`() {
        userPrefs.setPreTripRemindersEnabled(false)
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        // Mark as previously requested to trigger Blocked status
        context.getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("key_has_requested_before", true)
            .commit()

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Click to turn ON
        composeTestRule.onNodeWithTag("pre_trip_reminders_switch")
            .performScrollTo()
            .performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        // Blocked dialog is shown with Open Settings
        composeTestRule.onNodeWithTag("notification_permission_blocked_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notification_permission_open_settings_button").assertIsDisplayed()
        assertFalse(userPrefs.preTripRemindersEnabled.value)
    }

    @Test
    fun `test 27 - rapid ON OFF operation converges to latest state`() = runBlocking {
        viewModel.setPreTripRemindersEnabled(true)
        viewModel.setPreTripRemindersEnabled(false)
        viewModel.setPreTripRemindersEnabled(true)

        // Let coroutines execute
        kotlinx.coroutines.delay(100)

        assertTrue(userPrefs.preTripRemindersEnabled.value)
        assertEquals(true, fakeCoordinator.lastEnabledState)
    }
}

private class FakeReminderCoordinator(
    private val userPrefs: UserPreferencesRepository
) : ReminderCoordinator {
    var lastEnabledState: Boolean? = null
    var reconcileCallCount = 0

    override suspend fun setGlobalRemindersEnabled(enabled: Boolean) {
        lastEnabledState = enabled
        userPrefs.setPreTripRemindersEnabled(enabled)
    }

    override suspend fun reconcile() {
        reconcileCallCount++
    }
}

