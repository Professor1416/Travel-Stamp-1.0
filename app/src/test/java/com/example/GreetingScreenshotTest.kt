package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.TravelStamp
import com.example.ui.components.TravelStampView
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleStamp = TravelStamp(
        id = 1,
        tripId = 1,
        stampCode = "TS-2026-0001",
        title = "Harihar Fort",
        destination = "Nashik, Maharashtra",
        dateText = "18 August 2026",
        peopleCount = 4,
        momentsCount = 4,
        inkColorHex = "#C85A32"
    )

    composeTestRule.setContent {
        MyApplicationTheme {
            TravelStampView(stamp = sampleStamp)
        }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
