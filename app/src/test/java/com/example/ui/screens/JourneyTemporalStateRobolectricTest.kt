package com.example.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.JourneyDisplayState
import com.example.data.model.JourneyDisplayStateResolver
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import com.example.data.model.TripStatus
import com.example.ui.components.StatusBadge
import com.example.ui.components.TripCardTicket
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JourneyTemporalStateRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // 1. Test Resolver Logic
    @Test
    fun testResolverLogic() {
        val today = LocalDate.of(2026, 9, 12)
        
        // STAMP_EARNED when status == COMPLETED
        val completedTrip = Trip(
            id = 1L,
            name = "Paris",
            destination = "France",
            date = "10 Sep 2026",
            status = TripStatus.COMPLETED,
            createdAt = System.currentTimeMillis()
        )
        assertEquals(JourneyDisplayState.STAMP_EARNED, JourneyDisplayStateResolver.resolve(completedTrip, today))

        // UPCOMING when date is in the future
        val upcomingTrip = Trip(
            id = 2L,
            name = "Berlin",
            destination = "Germany",
            date = "15 Sep 2026",
            status = TripStatus.UPCOMING,
            createdAt = System.currentTimeMillis()
        )
        assertEquals(JourneyDisplayState.UPCOMING, JourneyDisplayStateResolver.resolve(upcomingTrip, today))

        // IN_PROGRESS when date is today
        val todayTrip = Trip(
            id = 3L,
            name = "Rome",
            destination = "Italy",
            date = "12 Sep 2026",
            status = TripStatus.IN_PROGRESS,
            createdAt = System.currentTimeMillis()
        )
        assertEquals(JourneyDisplayState.IN_PROGRESS, JourneyDisplayStateResolver.resolve(todayTrip, today))

        // READY_TO_COMPLETE when date is in the past and unfinished
        val pastUnfinishedTrip = Trip(
            id = 4L,
            name = "Spiti Valley",
            destination = "India",
            date = "6 Sep 2026",
            status = TripStatus.IN_PROGRESS,
            createdAt = System.currentTimeMillis()
        )
        assertEquals(JourneyDisplayState.READY_TO_COMPLETE, JourneyDisplayStateResolver.resolve(pastUnfinishedTrip, today))
        
        // Null/corrupt date fallback matches stored status
        val corruptDateTrip = Trip(
            id = 5L,
            name = "Tokyo",
            destination = "Japan",
            date = "", // Empty/Corrupt
            status = TripStatus.UPCOMING,
            createdAt = System.currentTimeMillis()
        )
        assertEquals(JourneyDisplayState.UPCOMING, JourneyDisplayStateResolver.resolve(corruptDateTrip, today))
    }

    // 2. Test StatusBadge rendering
    @Test
    fun testStatusBadgeRendering() {
        composeTestRule.setContent {
            MyApplicationTheme {
                StatusBadge(displayState = JourneyDisplayState.READY_TO_COMPLETE)
            }
        }
        composeTestRule.onNodeWithText("✓ READY TO COMPLETE").assertIsDisplayed()
    }

    // 3. Test TripCardTicket hides reminder badge on READY_TO_COMPLETE
    @Test
    fun testTripCardTicketReminderBadgeHiding() {
        // Create an unfinished trip in the past with reminders enabled
        val pastUnfinishedTrip = Trip(
            id = 4L,
            name = "Spiti Valley",
            destination = "India",
            date = "06 Sep 2026", // past date
            status = TripStatus.IN_PROGRESS,
            createdAt = System.currentTimeMillis(),
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                TripCardTicket(trip = pastUnfinishedTrip)
            }
        }

        // The StatusBadge should say READY TO COMPLETE
        composeTestRule.onNodeWithText("✓ READY TO COMPLETE").assertIsDisplayed()

        // The reminder badge should NOT be displayed
        composeTestRule.onNodeWithText("🔔 1 Day Before").assertDoesNotExist()
    }
}
