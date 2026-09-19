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

    private fun createTestStamp(id: Long, stampNumber: Long): TravelStamp {
        return TravelStamp(
            id = id,
            tripId = id * 10,
            stampNumber = stampNumber,
            stampCode = "DEST-${id}",
            title = "Title-${id}",
            destination = "Dest-${id}",
            dateText = "SEP 2026",
            peopleCount = 1,
            momentsCount = 0,
            inkColorHex = "#1E3A2F"
        )
    }

    @Test
    fun testBuildPassportPages_emptyList() {
        val pages = com.example.ui.components.buildPassportPages(emptyList())
        org.junit.Assert.assertTrue(pages.isEmpty())
    }

    @Test
    fun testBuildPassportPages_fewerThanOneFullPage() {
        val stamps = listOf(
            createTestStamp(1L, 1L),
            createTestStamp(2L, 2L)
        )
        val pages = com.example.ui.components.buildPassportPages(stamps)
        org.junit.Assert.assertEquals(1, pages.size)
        org.junit.Assert.assertEquals(2, pages[0].size)
        // Descending order: newest stamp (number 2) first
        org.junit.Assert.assertEquals(2L, pages[0][0].stampNumber)
        org.junit.Assert.assertEquals(1L, pages[0][1].stampNumber)
    }

    @Test
    fun testBuildPassportPages_exactPageSizeList() {
        val stamps = (1L..4L).map { createTestStamp(it, it) }
        val pages = com.example.ui.components.buildPassportPages(stamps)
        org.junit.Assert.assertEquals(1, pages.size)
        org.junit.Assert.assertEquals(4, pages[0].size)
        // Descending order: 4, 3, 2, 1
        org.junit.Assert.assertEquals(4L, pages[0][0].stampNumber)
        org.junit.Assert.assertEquals(3L, pages[0][1].stampNumber)
        org.junit.Assert.assertEquals(2L, pages[0][2].stampNumber)
        org.junit.Assert.assertEquals(1L, pages[0][3].stampNumber)
    }

    @Test
    fun testBuildPassportPages_multiplePagesAndDescendingAcrossBoundaries() {
        val stamps = listOf(1L, 2L, 3L, 4L, 25L, 26L).map { createTestStamp(it, it) }
        val pages = com.example.ui.components.buildPassportPages(stamps)
        
        org.junit.Assert.assertEquals(2, pages.size)
        
        // Page 1: 26, 25, 4, 3
        org.junit.Assert.assertEquals(4, pages[0].size)
        org.junit.Assert.assertEquals(26L, pages[0][0].stampNumber)
        org.junit.Assert.assertEquals(25L, pages[0][1].stampNumber)
        org.junit.Assert.assertEquals(4L, pages[0][2].stampNumber)
        org.junit.Assert.assertEquals(3L, pages[0][3].stampNumber)

        // Page 2: 2, 1
        org.junit.Assert.assertEquals(2, pages[1].size)
        org.junit.Assert.assertEquals(2L, pages[1][0].stampNumber)
        org.junit.Assert.assertEquals(1L, pages[1][1].stampNumber)
    }

    @Test
    fun testTargetStampId_resolvesToCorrectPageAfterDescendingOrdering() {
        val stamps = listOf(1L, 2L, 3L, 4L, 25L, 26L).map { createTestStamp(it, it) }
        val sortedStamps = stamps.sortedWith(
            compareByDescending<TravelStamp> { it.stampNumber }
                .thenByDescending { it.id }
        )
        
        // Find target page for stampNumber = 2 (id = 2)
        val targetStampId = 2L
        val targetIndex = sortedStamps.indexOfFirst { it.tripId == targetStampId || it.id == targetStampId }
        val targetPage = if (targetIndex >= 0) targetIndex / com.example.ui.components.PASSPORT_PAGE_SIZE else 0
        
        // Sorted sequence: 26 (index 0), 25 (index 1), 4 (index 2), 3 (index 3), 2 (index 4), 1 (index 5)
        // targetIndex of 2 is 4
        // targetPage = 4 / 4 = 1 (which is the second page, 0-based)
        org.junit.Assert.assertEquals(4, targetIndex)
        org.junit.Assert.assertEquals(1, targetPage)
    }
}
