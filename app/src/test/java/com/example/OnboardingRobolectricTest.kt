package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * TEST 1 — PAGE 1 BRANDING
     * Verify initial Page 1 renders:
     * - "TRAVEL STAMP"
     * - Official multiline tagline:
     *   "Your Journey,\nYour Memories,\nYour Collection."
     * - CTA: "NEXT →"
     * - SKIP: present
     * - Canonical brand symbol contentDescription
     */
    @Test
    fun test1_page1Branding() {
        var finishedCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingScreen(onFinished = { finishedCount++ })
            }
        }

        // Title and branding
        composeTestRule.onNodeWithText("TRAVEL STAMP").assertIsDisplayed()

        // Canonical brand symbol
        composeTestRule.onNodeWithContentDescription("Travel Stamp logo").assertIsDisplayed()

        // Multiline tagline
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedTagline = context.getString(R.string.travel_stamp_tagline_multiline)
        composeTestRule.onNodeWithText(expectedTagline).assertIsDisplayed()

        // Top-right SKIP button
        composeTestRule.onNodeWithTag("onboarding_skip_button").assertIsDisplayed()

        // Primary CTA
        composeTestRule.onNodeWithTag("onboarding_get_started_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("NEXT →").assertIsDisplayed()

        // Callback not called on initial render
        assertEquals(0, finishedCount)
    }

    /**
     * TEST 2 — PAGE 1 TO PAGE 2
     * Start on Page 1. Tap "NEXT →".
     * Verify Page 2 renders:
     * - "CREATE YOUR JOURNEY"
     * - "Trips"
     * - "Moments"
     * - "Checklist"
     * - CTA: "NEXT →"
     * - SKIP remains available.
     * Demonstrates that Page 1 NEXT advances instead of completing onboarding.
     */
    @Test
    fun test2_page1ToPage2() {
        var finishedCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingScreen(onFinished = { finishedCount++ })
            }
        }

        // Tap NEXT → on Page 1
        composeTestRule.onNodeWithTag("onboarding_get_started_button").performClick()
        composeTestRule.waitForIdle()

        // Verify Page 2 content exists
        composeTestRule.onNodeWithText("CREATE YOUR JOURNEY").assertIsDisplayed()
        composeTestRule.onNodeWithText("Trips").assertExists()
        composeTestRule.onNodeWithText("Moments").assertExists()
        composeTestRule.onNodeWithText("Checklist").assertExists()

        // Verify CTA is NEXT →
        composeTestRule.onNodeWithTag("onboarding_next_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("NEXT →").assertIsDisplayed()

        // Verify SKIP remains available on Page 2
        composeTestRule.onNodeWithTag("onboarding_skip_button").assertIsDisplayed()

        // Verify advance did NOT invoke onFinished
        assertEquals(0, finishedCount)
    }

    /**
     * TEST 3 — PAGE 2 TO PAGE 3
     * Navigate Page 1 -> Page 2 -> Page 3.
     * Verify Page 3 renders:
     * - "COLLECT YOUR STAMPS"
     * - "OFFICIAL STAMP #001"
     * - "Harihar Fort"
     * - "Nashik, Maharashtra"
     * - "CERTIFIED EXPEDITION"
     * - "START EXPLORING →"
     * - SKIP does NOT exist on Page 3.
     */
    @Test
    fun test3_page2ToPage3() {
        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingScreen(onFinished = {})
            }
        }

        // Page 1 -> Page 2
        composeTestRule.onNodeWithTag("onboarding_get_started_button").performClick()
        composeTestRule.waitForIdle()

        // Page 2 -> Page 3
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitForIdle()

        // Verify Page 3 headline
        composeTestRule.onNodeWithText("COLLECT YOUR STAMPS").assertIsDisplayed()

        // Verify stamp preview content
        composeTestRule.onNodeWithText("OFFICIAL STAMP #001").assertExists()
        composeTestRule.onNodeWithText("Harihar Fort").assertExists()
        composeTestRule.onNodeWithText("Nashik, Maharashtra").assertExists()
        composeTestRule.onNodeWithText("CERTIFIED EXPEDITION").assertExists()

        // Verify final CTA
        composeTestRule.onNodeWithTag("onboarding_start_exploring_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("START EXPLORING →").assertIsDisplayed()

        // Verify SKIP does NOT exist on Page 3
        composeTestRule.onNodeWithTag("onboarding_skip_button").assertDoesNotExist()
    }

    /**
     * TEST 4 — FINAL COMPLETION CALLBACK
     * Navigate to Page 3. Tap "START EXPLORING →".
     * Verify completion callback is invoked exactly once.
     */
    @Test
    fun test4_finalCompletionCallback() {
        var finishedCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingScreen(onFinished = { finishedCount++ })
            }
        }

        // Navigate to Page 3
        composeTestRule.onNodeWithTag("onboarding_get_started_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, finishedCount)

        // Tap final CTA
        composeTestRule.onNodeWithTag("onboarding_start_exploring_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, finishedCount)
    }

    /**
     * TEST 5 — SKIP FROM PAGE 1
     * On Page 1: Tap SKIP.
     * Verify completion callback invoked exactly once.
     */
    @Test
    fun test5_skipFromPage1() {
        var finishedCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingScreen(onFinished = { finishedCount++ })
            }
        }

        composeTestRule.onNodeWithTag("onboarding_skip_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_skip_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, finishedCount)
    }

    /**
     * TEST 6 — SKIP FROM PAGE 2
     * Navigate Page 1 -> Page 2. Verify SKIP displayed. Tap SKIP.
     * Verify completion callback invoked exactly once.
     */
    @Test
    fun test6_skipFromPage2() {
        var finishedCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingScreen(onFinished = { finishedCount++ })
            }
        }

        // Navigate to Page 2
        composeTestRule.onNodeWithTag("onboarding_get_started_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("onboarding_skip_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_skip_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, finishedCount)
    }

    /**
     * TEST 7 — NO SKIP ON FINAL PAGE
     * Navigate to Page 3.
     * Verify SKIP does not exist / is not displayed.
     */
    @Test
    fun test7_noSkipOnFinalPage() {
        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingScreen(onFinished = {})
            }
        }

        // Page 1 -> Page 2
        composeTestRule.onNodeWithTag("onboarding_get_started_button").performClick()
        composeTestRule.waitForIdle()

        // Page 2 -> Page 3
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitForIdle()

        // Ensure SKIP does not exist anywhere on Page 3
        composeTestRule.onNodeWithTag("onboarding_skip_button").assertDoesNotExist()
        composeTestRule.onNodeWithText("SKIP").assertDoesNotExist()
    }

    /**
     * TEST 8 — OFFICIAL TAGLINE REGRESSION
     * Verify Page 1 renders the exact locked wording:
     * "Your Journey,\nYour Memories,\nYour Collection."
     * Rejects lowercase or plural variants.
     */
    @Test
    fun test8_officialTaglineRegression() {
        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingScreen(onFinished = {})
            }
        }

        val exactLockedTagline = "Your Journey,\nYour Memories,\nYour Collection."
        composeTestRule.onNodeWithText(exactLockedTagline).assertIsDisplayed()

        // Negative assertions against regressions
        composeTestRule.onNodeWithText("Your journeys.", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Your memories.", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Your collection.", substring = true).assertDoesNotExist()
    }

    /**
     * TEST 9 — PAGE CONTENT OWNERSHIP
     * Protect against Page 2/Page 3 content displacement/swapping.
     * On Page 2: verify Trips, Moments, Checklist are present, and stamp-preview is absent.
     * On Page 3: verify stamp-preview is present, and Trips/Moments/Checklist cards are absent.
     */
    @Test
    fun test9_pageContentOwnership() {
        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingScreen(onFinished = {})
            }
        }

        // Page 1 -> Page 2
        composeTestRule.onNodeWithTag("onboarding_get_started_button").performClick()
        composeTestRule.waitForIdle()

        // Page 2 assertions
        composeTestRule.onNodeWithText("CREATE YOUR JOURNEY").assertIsDisplayed()
        composeTestRule.onNodeWithText("Trips").assertExists()
        composeTestRule.onNodeWithText("Moments").assertExists()
        composeTestRule.onNodeWithText("Checklist").assertExists()
        composeTestRule.onNodeWithText("OFFICIAL STAMP #001").assertDoesNotExist()
        composeTestRule.onNodeWithText("Harihar Fort").assertDoesNotExist()

        // Page 2 -> Page 3
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitForIdle()

        // Page 3 assertions
        composeTestRule.onNodeWithText("COLLECT YOUR STAMPS").assertIsDisplayed()
        composeTestRule.onNodeWithText("OFFICIAL STAMP #001").assertExists()
        composeTestRule.onNodeWithText("Harihar Fort").assertExists()
        composeTestRule.onNodeWithText("Nashik, Maharashtra").assertExists()
        composeTestRule.onNodeWithText("CERTIFIED EXPEDITION").assertExists()

        // Feature cards must NOT exist on Page 3
        composeTestRule.onNodeWithText("Trips").assertDoesNotExist()
        composeTestRule.onNodeWithText("Moments").assertDoesNotExist()
        composeTestRule.onNodeWithText("Checklist").assertDoesNotExist()
    }

    /**
     * TEST 10 — STATIC SAMPLE SAFETY
     * Inspect OnboardingScreen boundary:
     * Completing onboarding only invokes the provided callback.
     * Requires ZERO repositories, database, or stamp issuance interactions.
     */
    @Test
    fun test10_staticSampleSafety() {
        var callbackInvoked = false
        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingScreen(onFinished = { callbackInvoked = true })
            }
        }

        // Rapidly advance through all pages to complete
        composeTestRule.onNodeWithTag("onboarding_get_started_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_start_exploring_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue("Callback should be invoked without requiring any database/repository state", callbackInvoked)
    }

    /**
     * TEST 11 — SINGLE ARROW CTA REGRESSION
     * Verifies that the CTA button on each page renders exactly one arrow:
     * - Page 1: "NEXT →" (no duplicate arrow "NEXT → →")
     * - Page 2: "NEXT →" (no duplicate arrow "NEXT → →")
     * - Page 3: "START EXPLORING →" (no duplicate arrow "START EXPLORING → →")
     */
    @Test
    fun test11_singleArrowCtaRegression() {
        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingScreen(onFinished = {})
            }
        }

        // --- PAGE 1 ---
        val page1Cta = composeTestRule.onNodeWithTag("onboarding_get_started_button")
        page1Cta.assertIsDisplayed()
        page1Cta.assertTextEquals("NEXT →")
        composeTestRule.onNodeWithText("NEXT → →", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("NEXT >>", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("NEXT >", substring = true).assertDoesNotExist()

        // Page 1 -> Page 2
        page1Cta.performClick()
        composeTestRule.waitForIdle()

        // --- PAGE 2 ---
        val page2Cta = composeTestRule.onNodeWithTag("onboarding_next_button")
        page2Cta.assertIsDisplayed()
        page2Cta.assertTextEquals("NEXT →")
        composeTestRule.onNodeWithText("NEXT → →", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("NEXT >>", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("NEXT >", substring = true).assertDoesNotExist()

        // Page 2 -> Page 3
        page2Cta.performClick()
        composeTestRule.waitForIdle()

        // --- PAGE 3 ---
        val page3Cta = composeTestRule.onNodeWithTag("onboarding_start_exploring_button")
        page3Cta.assertIsDisplayed()
        page3Cta.assertTextEquals("START EXPLORING →")
        composeTestRule.onNodeWithText("START EXPLORING → →", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("START EXPLORING >>", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("START EXPLORING >", substring = true).assertDoesNotExist()
    }
}
