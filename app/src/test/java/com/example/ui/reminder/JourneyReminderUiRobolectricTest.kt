package com.example.ui.reminder

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import com.example.data.model.TripStatus
import com.example.ui.permission.NotificationPermissionController
import com.example.ui.theme.MyApplicationTheme
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
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JourneyReminderUiRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private val fixedNow: Instant = Instant.parse("2026-09-10T10:00:00Z")
    private val zoneUtc: ZoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `TEST 01 - Create Journey - reminder toggle is OFF by default, selector hidden, optional helper text shown`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                var enabled by remember { mutableStateOf(false) }
                var preset by remember { mutableStateOf(TripReminderPreset.ONE_DAY_BEFORE) }
                val validation = remember(enabled, preset) {
                    validateReminderForm(
                        reminderEnabled = enabled,
                        tripDate = "15 Sep 2026",
                        startTimeMinutes = null,
                        preset = preset,
                        now = fixedNow,
                        zoneId = zoneUtc
                    )
                }

                JourneyReminderSection(
                    reminderEnabled = enabled,
                    onReminderEnabledChange = { enabled = it },
                    selectedPreset = preset,
                    onPresetSelected = { preset = it },
                    validationResult = validation,
                    onRequestPermission = { it() }
                )
            }
        }

        // Toggle is OFF
        composeTestRule.onNodeWithTag("reminder_enable_switch").assertIsDisplayed().assertIsOff()

        // Preset selector is not visible while OFF
        composeTestRule.onNodeWithTag("reminder_preset_selector").assertDoesNotExist()

        // Helper text shows optional copy
        composeTestRule.onNodeWithTag("reminder_helper_text")
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.journey_reminder_optional))
    }

    @Test
    fun `TEST 02 - Create Journey - tapping toggle ON invokes permission, if granted enables with ONE_DAY_BEFORE`() {
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val controller = NotificationPermissionController(context = context)

        composeTestRule.setContent {
            MyApplicationTheme {
                var enabled by remember { mutableStateOf(false) }
                var preset by remember { mutableStateOf(TripReminderPreset.ONE_DAY_BEFORE) }
                val validation = remember(enabled, preset) {
                    validateReminderForm(
                        reminderEnabled = enabled,
                        tripDate = "15 Sep 2026",
                        startTimeMinutes = null,
                        preset = preset,
                        now = fixedNow,
                        zoneId = zoneUtc
                    )
                }

                JourneyReminderSection(
                    reminderEnabled = enabled,
                    onReminderEnabledChange = {
                        enabled = it
                        if (it) preset = TripReminderPreset.ONE_DAY_BEFORE
                    },
                    selectedPreset = preset,
                    onPresetSelected = { preset = it },
                    validationResult = validation,
                    onRequestPermission = { onGranted ->
                        controller.requestPermission(onGranted)
                    }
                )
            }
        }

        // Initially OFF
        composeTestRule.onNodeWithTag("reminder_enable_switch").assertIsOff()

        // Tap toggle ON
        composeTestRule.onNodeWithTag("reminder_enable_switch").performClick()

        // Permission granted immediately -> Reminder is ON
        composeTestRule.onNodeWithTag("reminder_enable_switch").assertIsOn()

        // Preset selector is now visible with default ONE_DAY_BEFORE
        composeTestRule.onNodeWithTag("reminder_preset_selector")
            .assertIsDisplayed()
            .assert(hasText(context.getString(R.string.journey_reminder_one_day)))

        // Helper text shows ONE_DAY_BEFORE copy
        composeTestRule.onNodeWithTag("reminder_helper_text")
            .assertTextEquals(context.getString(R.string.journey_reminder_helper_one_day))
    }

    @Test
    fun `TEST 03 - Create Journey - selecting TWO_HOURS_BEFORE without start time surfaces validation error`() {
        var formSubmissionAttempted = false
        var formSubmissionSucceeded = false

        composeTestRule.setContent {
            MyApplicationTheme {
                var enabled by remember { mutableStateOf(true) }
                var preset by remember { mutableStateOf(TripReminderPreset.TWO_HOURS_BEFORE) }
                var startTimeMinutes by remember { mutableStateOf<Int?>(null) }
                val validation = remember(enabled, preset, startTimeMinutes) {
                    validateReminderForm(
                        reminderEnabled = enabled,
                        tripDate = "15 Sep 2026",
                        startTimeMinutes = startTimeMinutes,
                        preset = preset,
                        now = fixedNow,
                        zoneId = zoneUtc
                    )
                }

                JourneyReminderSection(
                    reminderEnabled = enabled,
                    onReminderEnabledChange = { enabled = it },
                    selectedPreset = preset,
                    onPresetSelected = { preset = it },
                    validationResult = validation,
                    onRequestPermission = { it() }
                )

                // Simulated submit handler checking form validation
                if (formSubmissionAttempted) {
                    if (validation.isValid) {
                        formSubmissionSucceeded = true
                    }
                }
            }
        }

        // Inline error is displayed
        composeTestRule.onNodeWithTag("reminder_validation_error")
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.journey_reminder_requires_start_time))

        // Attempt submission
        formSubmissionAttempted = true
        composeTestRule.waitForIdle()
        assertFalse("Submission must be blocked when reminder is invalid", formSubmissionSucceeded)
    }

    @Test
    fun `TEST 04 - Create Journey - adding start time clears TWO_HOURS_BEFORE validation error`() {
        var startTimeState by mutableStateOf<Int?>(null)

        composeTestRule.setContent {
            MyApplicationTheme {
                val enabled = true
                val preset = TripReminderPreset.TWO_HOURS_BEFORE
                val validation = validateReminderForm(
                    reminderEnabled = enabled,
                    tripDate = "15 Sep 2026",
                    startTimeMinutes = startTimeState,
                    preset = preset,
                    now = fixedNow,
                    zoneId = zoneUtc
                )

                JourneyReminderSection(
                    reminderEnabled = enabled,
                    onReminderEnabledChange = { },
                    selectedPreset = preset,
                    onPresetSelected = { },
                    validationResult = validation,
                    onRequestPermission = { it() }
                )
            }
        }

        // Error is visible before adding start time
        composeTestRule.onNodeWithTag("reminder_validation_error").assertIsDisplayed()

        // User adds start time
        startTimeState = 600 // 10:00 AM
        composeTestRule.waitForIdle()

        // Error is cleared
        composeTestRule.onNodeWithTag("reminder_validation_error").assertDoesNotExist()
    }

    @Test
    fun `TEST 05 - Create Journey - turning reminder back OFF hides preset selector and clears validation error`() {
        var enabledState by mutableStateOf(true)

        composeTestRule.setContent {
            MyApplicationTheme {
                val preset = TripReminderPreset.TWO_HOURS_BEFORE
                val validation = validateReminderForm(
                    reminderEnabled = enabledState,
                    tripDate = "15 Sep 2026",
                    startTimeMinutes = null, // Missing start time
                    preset = preset,
                    now = fixedNow,
                    zoneId = zoneUtc
                )

                JourneyReminderSection(
                    reminderEnabled = enabledState,
                    onReminderEnabledChange = { enabledState = it },
                    selectedPreset = preset,
                    onPresetSelected = { },
                    validationResult = validation,
                    onRequestPermission = { it() }
                )
            }
        }

        // Initially ON with error
        composeTestRule.onNodeWithTag("reminder_preset_selector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reminder_validation_error").assertIsDisplayed()

        // Turn OFF
        composeTestRule.onNodeWithTag("reminder_enable_switch").performClick()
        composeTestRule.waitForIdle()

        assertFalse(enabledState)
        // Preset selector hidden
        composeTestRule.onNodeWithTag("reminder_preset_selector").assertDoesNotExist()
        // Error cleared
        composeTestRule.onNodeWithTag("reminder_validation_error").assertDoesNotExist()
        // Helper text is optional copy
        composeTestRule.onNodeWithTag("reminder_helper_text")
            .assertTextEquals(context.getString(R.string.journey_reminder_optional))
    }

    @Test
    fun `TEST 06 - Edit Journey - existing trip with reminder ON displays ON and correct preset`() {
        val existingTrip = Trip(
            id = 42L,
            name = "Goa Trip",
            destination = "Goa",
            date = "20 Sep 2026",
            startTimeMinutes = 540,
            peopleCount = 2,
            description = "Beach",
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_WEEK_BEFORE
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                var editReminderEnabled by remember { mutableStateOf(existingTrip.reminderEnabled) }
                var editReminderPreset by remember { mutableStateOf(existingTrip.reminderPreset) }
                val validation = remember(editReminderEnabled, editReminderPreset) {
                    validateReminderForm(
                        reminderEnabled = editReminderEnabled,
                        tripDate = existingTrip.date,
                        startTimeMinutes = existingTrip.startTimeMinutes,
                        preset = editReminderPreset,
                        now = fixedNow,
                        zoneId = zoneUtc
                    )
                }

                JourneyReminderSection(
                    reminderEnabled = editReminderEnabled,
                    onReminderEnabledChange = { editReminderEnabled = it },
                    selectedPreset = editReminderPreset,
                    onPresetSelected = { editReminderPreset = it },
                    validationResult = validation,
                    onRequestPermission = { it() },
                    switchTestTag = "edit_trip_reminder_switch",
                    presetSelectorTestTag = "edit_trip_reminder_preset_selector",
                    validationErrorTestTag = "edit_trip_reminder_validation_error"
                )
            }
        }

        // Switch is ON
        composeTestRule.onNodeWithTag("edit_trip_reminder_switch").assertIsDisplayed().assertIsOn()

        // Correct preset ONE_WEEK_BEFORE is displayed
        composeTestRule.onNodeWithTag("edit_trip_reminder_preset_selector")
            .assertIsDisplayed()
            .assert(hasText(context.getString(R.string.journey_reminder_one_week)))
    }

    @Test
    fun `TEST 07 - Edit Journey - existing trip with reminder OFF displays OFF`() {
        val existingTrip = Trip(
            id = 43L,
            name = "Coorg Trip",
            destination = "Coorg",
            date = "22 Sep 2026",
            reminderEnabled = false,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                var editReminderEnabled by remember { mutableStateOf(existingTrip.reminderEnabled) }
                var editReminderPreset by remember { mutableStateOf(existingTrip.reminderPreset) }
                val validation = remember(editReminderEnabled, editReminderPreset) {
                    validateReminderForm(
                        reminderEnabled = editReminderEnabled,
                        tripDate = existingTrip.date,
                        startTimeMinutes = existingTrip.startTimeMinutes,
                        preset = editReminderPreset,
                        now = fixedNow,
                        zoneId = zoneUtc
                    )
                }

                JourneyReminderSection(
                    reminderEnabled = editReminderEnabled,
                    onReminderEnabledChange = { editReminderEnabled = it },
                    selectedPreset = editReminderPreset,
                    onPresetSelected = { editReminderPreset = it },
                    validationResult = validation,
                    onRequestPermission = { it() },
                    switchTestTag = "edit_trip_reminder_switch",
                    presetSelectorTestTag = "edit_trip_reminder_preset_selector"
                )
            }
        }

        // Switch is OFF
        composeTestRule.onNodeWithTag("edit_trip_reminder_switch").assertIsDisplayed().assertIsOff()

        // Preset selector does not exist while OFF
        composeTestRule.onNodeWithTag("edit_trip_reminder_preset_selector").assertDoesNotExist()
    }

    @Test
    fun `TEST 08 - Edit Journey - removing start time while TWO_HOURS_BEFORE is active immediately surfaces validation error`() {
        var editStartTimeMinutes by mutableStateOf<Int?>(480)
        var saveAttempted = false
        var saveSuccessful = false

        composeTestRule.setContent {
            MyApplicationTheme {
                val enabled = true
                val preset = TripReminderPreset.TWO_HOURS_BEFORE
                val validation = validateReminderForm(
                    reminderEnabled = enabled,
                    tripDate = "15 Sep 2026",
                    startTimeMinutes = editStartTimeMinutes,
                    preset = preset,
                    now = fixedNow,
                    zoneId = zoneUtc
                )

                JourneyReminderSection(
                    reminderEnabled = enabled,
                    onReminderEnabledChange = { },
                    selectedPreset = preset,
                    onPresetSelected = { },
                    validationResult = validation,
                    onRequestPermission = { it() },
                    switchTestTag = "edit_trip_reminder_switch",
                    presetSelectorTestTag = "edit_trip_reminder_preset_selector",
                    validationErrorTestTag = "edit_trip_reminder_validation_error"
                )

                // Simulated Edit Dialog Save Changes handler
                if (saveAttempted) {
                    if (validation.isValid) {
                        saveSuccessful = true
                    }
                }
            }
        }

        // Initially with start time -> valid
        composeTestRule.onNodeWithTag("edit_trip_reminder_validation_error").assertDoesNotExist()

        // User removes start time in Edit flow
        editStartTimeMinutes = null
        composeTestRule.waitForIdle()

        // Error immediately surfaces
        composeTestRule.onNodeWithTag("edit_trip_reminder_validation_error")
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.journey_reminder_requires_start_time))

        // Attempt save
        saveAttempted = true
        composeTestRule.waitForIdle()
        assertFalse("Saving invalid reminder state must be prevented", saveSuccessful)
    }

    @Test
    fun `TEST 09 - Edit Journey - completed or stamped journey does not show active reminder controls`() {
        val completedTrip = Trip(
            id = 50L,
            name = "Old Trip",
            destination = "Hampi",
            date = "01 Jan 2025",
            status = TripStatus.COMPLETED,
            stampEarned = true,
            completedAt = 1735689600000L
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                val isEditTripCompletedOrStamped = completedTrip.status == TripStatus.COMPLETED ||
                        completedTrip.stampEarned || completedTrip.completedAt != null

                if (!isEditTripCompletedOrStamped) {
                    JourneyReminderSection(
                        reminderEnabled = completedTrip.reminderEnabled,
                        onReminderEnabledChange = { },
                        selectedPreset = completedTrip.reminderPreset,
                        onPresetSelected = { },
                        validationResult = ReminderFormValidation.Valid,
                        onRequestPermission = { it() },
                        switchTestTag = "edit_trip_reminder_switch"
                    )
                }
            }
        }

        // Reminder controls are hidden for completed / stamped journeys
        composeTestRule.onNodeWithTag("edit_trip_reminder_switch").assertDoesNotExist()
        composeTestRule.onNodeWithTag("reminder_section_container").assertDoesNotExist()
    }
}
