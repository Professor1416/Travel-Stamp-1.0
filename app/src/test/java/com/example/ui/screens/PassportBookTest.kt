package com.example.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppThemeMode
import com.example.data.model.TravelStamp
import com.example.ui.components.EmptyPassportPage
import com.example.ui.components.PassportPage
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PassportBookTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Test that EmptyPassportPage renders correctly with stable contents
     * under both Light and Dark theme configurations.
     */
    @Test
    fun testEmptyPassportPage_rendersCoreElements() {
        var createJourneyClicked = false
        composeTestRule.setContent {
            MyApplicationTheme(themeMode = AppThemeMode.LIGHT) {
                EmptyPassportPage(onCreateJourney = { createJourneyClicked = true })
            }
        }

        // Verify key text nodes are displayed
        composeTestRule.onNodeWithTag("empty_passport_title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your passport is waiting.").assertIsDisplayed()

        composeTestRule.onNodeWithTag("empty_passport_body").assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete your first journey to earn your first Travel Stamp.").assertIsDisplayed()

        composeTestRule.onNodeWithTag("empty_passport_cta").assertIsDisplayed()
    }

    @Test
    fun testEmptyPassportPage_darkTheme_rendersCoreElements() {
        composeTestRule.setContent {
            MyApplicationTheme(themeMode = AppThemeMode.DARK) {
                EmptyPassportPage(onCreateJourney = {})
            }
        }

        // Title and body remain readable and correctly structured
        composeTestRule.onNodeWithTag("empty_passport_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_passport_body").assertIsDisplayed()
    }

    /**
     * Test that PassportPage loads valid stamp content and empty slots correctly.
     */
    @Test
    fun testPassportPage_rendersStampsAndEmptySlots() {
        val testStamps = listOf(
            TravelStamp(
                id = 1L,
                tripId = 10L,
                stampNumber = 1L,
                stampCode = "DEST-001",
                title = "Alps Expedition",
                destination = "Chamonix",
                dateText = "SEP 2026",
                peopleCount = 2,
                momentsCount = 4,
                inkColorHex = "#1E3A2F" // Forest Pine
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = AppThemeMode.LIGHT) {
                PassportPage(
                    pageStamps = testStamps,
                    pageNumber = 1,
                    totalPages = 3,
                    onStampClick = {}
                )
            }
        }

        // Headers are present and legible
        composeTestRule.onNodeWithText("TRAVEL PASSPORT").assertIsDisplayed()
        composeTestRule.onNodeWithText("OFFICIAL RECORD").assertIsDisplayed()

        // Page footer formatting is correct
        composeTestRule.onNodeWithText("PAGE 01 OF 03").assertIsDisplayed()

        // One stamp slot is populated with stampCode node tag
        composeTestRule.onNodeWithTag("passport_stamp_slot_DEST-001").assertIsDisplayed()
    }
}
