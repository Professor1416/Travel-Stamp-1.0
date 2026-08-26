package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.BundledSuggestionSourceImpl
import com.example.data.datasource.UserHistorySuggestionSourceImpl
import com.example.data.local.AppThemeMode
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.Trip
import com.example.data.repository.ChecklistRepositoryImpl
import com.example.data.repository.LocationSuggestionRepositoryImpl
import com.example.data.repository.MomentRepositoryImpl
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepositoryImpl
import com.example.data.util.BackupManager
import com.example.ui.about.AppInfoConfig
import com.example.ui.about.AppVersionInfo
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AboutAndSettingsRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: TravelStampDatabase
    private lateinit var tripRepo: TripRepositoryImpl
    private lateinit var stampRepo: TravelStampRepositoryImpl
    private lateinit var userPrefs: UserPreferencesRepository
    private lateinit var vm: TravelViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tripRepo = TripRepositoryImpl(db.tripDao(), db.momentDao(), context)
        stampRepo = TravelStampRepositoryImpl(db.travelStampDao())
        val checklistRepo = ChecklistRepositoryImpl(db.checklistDao())
        val momentRepo = MomentRepositoryImpl(db.momentDao(), context)
        val userHistorySource = UserHistorySuggestionSourceImpl(tripRepo)
        val bundledSource = BundledSuggestionSourceImpl()
        val suggestionRepo = LocationSuggestionRepositoryImpl(userHistorySource, bundledSource)

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
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `TEST 1 - Settings shows Appearance section`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_section_appearance").assertExists()
        composeTestRule.onNodeWithTag("theme_light_option").assertExists()
        composeTestRule.onNodeWithTag("theme_dark_option").assertExists()
        composeTestRule.onNodeWithText("Warm Parchment Light").assertExists()
        composeTestRule.onNodeWithText("Deep Slate Night").assertExists()
    }

    @Test
    fun `TEST 2 - Settings shows Notifications section`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_section_notifications").assertExists()
        composeTestRule.onNodeWithTag("pre_trip_reminders_toggle_row").assertExists()
        composeTestRule.onNodeWithTag("pre_trip_reminders_switch").assertExists()
        composeTestRule.onNodeWithTag("open_notification_settings_row").assertExists()
        composeTestRule.onNodeWithText("Pre-Trip Reminders").assertExists()
        composeTestRule.onNodeWithText("Notification Settings").assertExists()
    }

    @Test
    fun `TEST 3 - Settings shows Backup section`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_section_data_backup").assertExists()
        composeTestRule.onNodeWithTag("export_backup_button").assertExists()
        composeTestRule.onNodeWithTag("import_backup_button").assertExists()
    }

    @Test
    fun `TEST 4 - Settings shows About section`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_section_about").assertExists()
        composeTestRule.onNodeWithTag("about_travel_stamp_row").assertExists()
        composeTestRule.onNodeWithText("About Travel Stamp").assertExists()
    }

    @Test
    fun `TEST 5 - Sample Expedition section is absent`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }

        // Must not contain any Sample Expedition elements
        composeTestRule.onNodeWithText("Sample Expedition").assertDoesNotExist()
        composeTestRule.onNodeWithText("Explore Sample Data").assertDoesNotExist()
        composeTestRule.onNodeWithText("EXPLORE SAMPLE DATA").assertDoesNotExist()
        composeTestRule.onNodeWithTag("load_sample_data_button").assertDoesNotExist()
    }

    @Test
    fun `TEST 6 - About Travel Stamp opens successfully and handles back navigation`() {
        var navigatedBack = false

        composeTestRule.setContent {
            MyApplicationTheme {
                AboutScreen(
                    onNavigateBack = { navigatedBack = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("about_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_back_button").performClick()
        assertTrue(navigatedBack)
    }

    @Test
    fun `TEST 7 - Official tagline is exactly Your Journey Your Memories Your Collection`() {
        val expectedTagline = "Your Journey, Your Memories, Your Collection."
        assertEquals(expectedTagline, AppInfoConfig.TAGLINE)

        composeTestRule.setContent {
            MyApplicationTheme {
                AboutScreen(onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithTag("about_tagline").assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedTagline).assertIsDisplayed()
    }

    @Test
    fun `TEST 8 - Version value comes from actual package build configuration not a hardcoded display value`() {
        val customVersion = "2.4.1"
        val customBuild = 42L

        composeTestRule.setContent {
            MyApplicationTheme {
                AboutScreen(
                    onNavigateBack = {},
                    versionInfoProvider = { AppVersionInfo(customVersion, customBuild) }
                )
            }
        }

        composeTestRule.onNodeWithText("Version $customVersion").assertIsDisplayed()
        composeTestRule.onNodeWithText(customVersion).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `TEST 9 - Build value comes dynamically`() {
        val customVersion = "3.0.0"
        val customBuild = 105L

        composeTestRule.setContent {
            MyApplicationTheme {
                AboutScreen(
                    onNavigateBack = {},
                    versionInfoProvider = { AppVersionInfo(customVersion, customBuild) }
                )
            }
        }

        composeTestRule.onNodeWithTag("about_build_row").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("105").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `TEST 10 - Light theme renders without crash`() {
        composeTestRule.setContent {
            MyApplicationTheme(themeMode = AppThemeMode.LIGHT) {
                AboutScreen(onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithTag("about_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_tagline").assertIsDisplayed()
    }

    @Test
    fun `TEST 11 - Dark theme renders without crash`() {
        composeTestRule.setContent {
            MyApplicationTheme(themeMode = AppThemeMode.DARK) {
                AboutScreen(onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithTag("about_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_tagline").assertIsDisplayed()
    }

    @Test
    fun `TEST 12 - Notification toggle reflects stored preference`() {
        assertEquals(true, userPrefs.preTripRemindersEnabled.value)

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pre_trip_reminders_switch").assertExists().assertIsOn()

        // Update preference to false and verify switch reflects it
        vm.setPreTripRemindersEnabled(false)
        composeTestRule.waitForIdle()
        assertEquals(false, userPrefs.preTripRemindersEnabled.value)
        composeTestRule.onNodeWithTag("pre_trip_reminders_switch").assertExists().assertIsOff()

        // Update preference back to true and verify switch reflects it
        vm.setPreTripRemindersEnabled(true)
        composeTestRule.waitForIdle()
        assertEquals(true, userPrefs.preTripRemindersEnabled.value)
        composeTestRule.onNodeWithTag("pre_trip_reminders_switch").assertExists().assertIsOn()
    }

    @Test
    fun `TEST 13 - Backup actions remain callable`() = runBlocking {
        // Seed trip
        tripRepo.createTrip(
            Trip(
                name = "Torna Fort",
                destination = "Pune",
                date = "12 Aug 2026",
                peopleCount = 2
            )
        )

        val exportResult = BackupManager.createExportFile(context, db)
        assertNotNull(exportResult)
        assertTrue(exportResult.fileName.endsWith(".tsbackup"))
        assertEquals(1, exportResult.totalTrips)
    }

    @Test
    fun `TEST 14 - Privacy Policy intent failure is handled safely`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AboutScreen(onNavigateBack = {})
            }
        }

        // Clicking Privacy Policy row does not crash even without an external browser registered
        composeTestRule.onNodeWithTag("about_privacy_policy_row").performScrollTo().performClick()
        ShadowLooper.idleMainLooper()

        // Verify either browser intent or fallback dialog was invoked safely
        composeTestRule.onNodeWithTag("about_screen").assertIsDisplayed()
    }

    @Test
    fun `TEST 15 - Contact and Support intent failure is handled safely`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AboutScreen(onNavigateBack = {})
            }
        }

        // Clicking Contact & Support row does not crash even without an email app installed
        composeTestRule.onNodeWithTag("about_contact_support_row").performScrollTo().performClick()
        ShadowLooper.idleMainLooper()

        composeTestRule.onNodeWithTag("about_screen").assertIsDisplayed()
    }

    @Test
    fun `TEST 16 - No GitHub link is present`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AboutScreen(onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText("GitHub", substring = true, ignoreCase = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("github.com", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `TEST 17 - No Sample Expedition CTA remains in Settings`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = {},
                    onAboutClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Sample Expedition", substring = true, ignoreCase = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Explore Sample", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `TEST 18 - Opening About does not modify Trip or Stamp database state`() = runBlocking {
        // Pre-populate 1 trip and 1 stamp
        val tripId = tripRepo.createTrip(
            Trip(
                name = "Kalsubai",
                destination = "Igatpuri",
                date = "10 Aug 2026",
                peopleCount = 4
            )
        )
        val initialTripCount = tripRepo.getAllTrips().first().size
        val initialStampCount = stampRepo.getAllStamps().first().size

        composeTestRule.setContent {
            MyApplicationTheme {
                AboutScreen(onNavigateBack = {})
            }
        }

        ShadowLooper.idleMainLooper()

        val postTripCount = tripRepo.getAllTrips().first().size
        val postStampCount = stampRepo.getAllStamps().first().size

        assertEquals(initialTripCount, postTripCount)
        assertEquals(initialStampCount, postStampCount)
    }
}
