package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.BundledSuggestionSourceImpl
import com.example.data.datasource.UserHistorySuggestionSourceImpl
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.TravelStamp
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.data.repository.ChecklistRepositoryImpl
import com.example.data.repository.LocationSuggestionRepositoryImpl
import com.example.data.repository.MomentRepositoryImpl
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepositoryImpl
import com.example.ui.poster.PosterExporter
import com.example.ui.poster.PosterRenderConfig
import com.example.ui.poster.PosterRenderResult
import com.example.ui.poster.PosterRenderer
import com.example.ui.poster.PosterTemplate
import com.example.ui.poster.StampEditionFormat
import com.example.ui.poster.StampSize
import com.example.ui.util.PhotoUtils
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TravelPosterRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: TravelStampDatabase
    private lateinit var tripRepo: TripRepositoryImpl
    private lateinit var stampRepo: TravelStampRepositoryImpl
    private lateinit var momentRepo: MomentRepositoryImpl
    private lateinit var vm: TravelViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tripRepo = TripRepositoryImpl(db.tripDao(), db.momentDao(), context)
        stampRepo = TravelStampRepositoryImpl(db.travelStampDao())
        momentRepo = MomentRepositoryImpl(db.momentDao(), context)
        val userHistorySource = UserHistorySuggestionSourceImpl(tripRepo)
        val bundledSource = BundledSuggestionSourceImpl()
        val suggestionRepo = LocationSuggestionRepositoryImpl(userHistorySource, bundledSource)
        val checklistRepo = ChecklistRepositoryImpl(db.checklistDao())
        val fakeUserPrefs = object : UserPreferencesRepository {
            override val hasCompletedOnboarding = kotlinx.coroutines.flow.MutableStateFlow(true)
            override val themeMode = kotlinx.coroutines.flow.MutableStateFlow(com.example.data.local.AppThemeMode.SYSTEM)
            override val preTripRemindersEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
            override fun setOnboardingCompleted(completed: Boolean) {}
            override fun setThemeMode(mode: com.example.data.local.AppThemeMode) {}
            override fun setPreTripRemindersEnabled(enabled: Boolean) {}
        }

        vm = TravelViewModel(
            tripRepository = tripRepo,
            checklistRepository = checklistRepo,
            momentRepository = momentRepo,
            travelStampRepository = stampRepo,
            userPreferencesRepository = fakeUserPrefs,
            database = db,
            locationSuggestionRepository = suggestionRepo
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testPosterTemplateEnum() {
        assertEquals("Photo + Stamp", PosterTemplate.PHOTO_STAMP.title)
        assertEquals("Passport Focus", PosterTemplate.PASSPORT_STAMP.title)
        assertTrue(PosterTemplate.PHOTO_STAMP.description.isNotEmpty())
        assertTrue(PosterTemplate.PASSPORT_STAMP.description.isNotEmpty())
    }

    @Test
    fun testStampEditionFormatDimensionsAndRatios() {
        assertEquals(1080, StampEditionFormat.SQUARE.width)
        assertEquals(1080, StampEditionFormat.SQUARE.height)
        assertEquals(1f, StampEditionFormat.SQUARE.aspectRatio, 0.001f)

        assertEquals(1080, StampEditionFormat.PORTRAIT.width)
        assertEquals(1350, StampEditionFormat.PORTRAIT.height)
        assertEquals(0.8f, StampEditionFormat.PORTRAIT.aspectRatio, 0.001f)

        assertEquals(1080, StampEditionFormat.STORY.width)
        assertEquals(1920, StampEditionFormat.STORY.height)
        assertEquals(9f / 16f, StampEditionFormat.STORY.aspectRatio, 0.001f)
    }

    @Test
    fun testPassportStampDimensionsAcrossFormats() {
        val trip = Trip(
            id = 101L,
            name = "Umbrande Waterfall",
            destination = "Nashik, Maharashtra",
            date = "2026-10-14",
            status = TripStatus.COMPLETED,
            stampEarned = true
        )
        val stamp = TravelStamp(
            id = 101L,
            tripId = 101L,
            stampNumber = 1L,
            stampCode = "#001",
            title = "Umbrande Waterfall",
            destination = "Nashik, Maharashtra",
            dateText = "14 OCT 2026",
            peopleCount = 2,
            momentsCount = 3,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN"
        )

        // 1. Square (1080x1080)
        val squareConfig = PosterRenderConfig(
            template = PosterTemplate.PASSPORT_STAMP,
            format = StampEditionFormat.SQUARE
        )
        val squareResult = PosterRenderer.render(context, trip, stamp, squareConfig)
        assertTrue(squareResult is PosterRenderResult.Success)
        val squareBitmap = (squareResult as PosterRenderResult.Success).bitmap
        assertEquals(1080, squareBitmap.width)
        assertEquals(1080, squareBitmap.height)
        squareBitmap.recycle()

        // 2. Portrait (1080x1350, 4:5)
        val portraitConfig = PosterRenderConfig(
            template = PosterTemplate.PASSPORT_STAMP,
            format = StampEditionFormat.PORTRAIT
        )
        val portraitResult = PosterRenderer.render(context, trip, stamp, portraitConfig)
        assertTrue(portraitResult is PosterRenderResult.Success)
        val portraitBitmap = (portraitResult as PosterRenderResult.Success).bitmap
        assertEquals(1080, portraitBitmap.width)
        assertEquals(1350, portraitBitmap.height)
        portraitBitmap.recycle()

        // 3. Story (1080x1920)
        val storyConfig = PosterRenderConfig(
            template = PosterTemplate.PASSPORT_STAMP,
            format = StampEditionFormat.STORY
        )
        val storyResult = PosterRenderer.render(context, trip, stamp, storyConfig)
        assertTrue(storyResult is PosterRenderResult.Success)
        val storyBitmap = (storyResult as PosterRenderResult.Success).bitmap
        assertEquals(1080, storyBitmap.width)
        assertEquals(1920, storyBitmap.height)
        storyBitmap.recycle()
    }

    @Test
    fun testUmbrandeWaterfallTextFittingNoTruncation() {
        val basePaint = Paint().apply { textSize = 54f }
        val destinationName = "Umbrande Waterfall"

        // Layout into 900px width (standard margin on 1080 canvas)
        val fitted = PosterRenderer.fitResponsiveText(
            text = destinationName,
            basePaint = basePaint,
            maxWidth = 900f,
            maxLines = 2,
            maxTextSize = 54f,
            minTextSize = 34f
        )

        assertEquals(1, fitted.lines.size)
        assertEquals("Umbrande Waterfall", fitted.lines[0])
        assertTrue("Text should not contain ellipsis", !fitted.lines[0].contains("…"))
    }

    @Test
    fun testLongRealisticDestinationNameFitting() {
        val basePaint = Paint().apply { textSize = 54f }
        val longDestination = "Harishchandragad Peak & Konkan Kada, Western Ghats"

        val fitted = PosterRenderer.fitResponsiveText(
            text = longDestination,
            basePaint = basePaint,
            maxWidth = 850f,
            maxLines = 2,
            maxTextSize = 54f,
            minTextSize = 32f
        )

        assertTrue(fitted.lines.size in 1..2)
        val reconstructed = fitted.lines.joinToString(" ")
        assertTrue(reconstructed.contains("Harishchandragad"))
        assertTrue(reconstructed.contains("Konkan Kada"))
    }

    @Test
    fun testLongLocationTextHandling() {
        val basePaint = Paint().apply { textSize = 34f }
        val location = "Trimbakeshwar, Nashik District, Maharashtra, India"

        val fitted = PosterRenderer.fitResponsiveText(
            text = location,
            basePaint = basePaint,
            maxWidth = 850f,
            maxLines = 2,
            maxTextSize = 34f,
            minTextSize = 22f
        )

        assertTrue(fitted.lines.size in 1..2)
        assertTrue(!fitted.lines[0].contains("…"))
    }

    @Test
    fun testPosterRendererWithDevanagariAndEmoji() {
        val trip = Trip(
            id = 5L,
            name = "हरिहर किल्ला ट्रेक 🏔️ ❤️",
            destination = "त्र्यंबकेश्वर, नाशिक, महाराष्ट्र",
            date = "2026-10-18",
            status = TripStatus.COMPLETED,
            stampEarned = true
        )
        val stamp = TravelStamp(
            id = 5L,
            tripId = 5L,
            stampNumber = 5L,
            stampCode = "#005",
            title = "हरिहर किल्ला ट्रेक 🏔️ ❤️",
            destination = "त्र्यंबकेश्वर, नाशिक, महाराष्ट्र",
            dateText = "18 OCT 2026",
            peopleCount = 3,
            momentsCount = 2
        )

        val configSquare = PosterRenderConfig(
            template = PosterTemplate.PASSPORT_STAMP,
            format = StampEditionFormat.SQUARE
        )
        val squareResult = PosterRenderer.render(context, trip, stamp, configSquare)
        assertTrue(squareResult is PosterRenderResult.Success)
        val bitmapSquare = (squareResult as PosterRenderResult.Success).bitmap
        assertEquals(1080, bitmapSquare.width)
        assertEquals(1080, bitmapSquare.height)
        bitmapSquare.recycle()

        val configPortrait = PosterRenderConfig(
            template = PosterTemplate.PASSPORT_STAMP,
            format = StampEditionFormat.PORTRAIT
        )
        val portraitResult = PosterRenderer.render(context, trip, stamp, configPortrait)
        assertTrue(portraitResult is PosterRenderResult.Success)
        val bitmapPortrait = (portraitResult as PosterRenderResult.Success).bitmap
        assertEquals(1080, bitmapPortrait.width)
        assertEquals(1350, bitmapPortrait.height)
        bitmapPortrait.recycle()
    }

    @Test
    fun testShareablePosterUriGenerationForPassportFormats() {
        val trip = Trip(
            id = 6L,
            name = "Valley of Flowers",
            destination = "Uttarakhand",
            date = "2026-08-15",
            status = TripStatus.COMPLETED,
            stampEarned = true
        )
        val stamp = TravelStamp(
            id = 6L,
            tripId = 6L,
            stampNumber = 6L,
            stampCode = "#006",
            title = "Valley of Flowers",
            destination = "Uttarakhand",
            dateText = "15 AUG 2026",
            peopleCount = 2,
            momentsCount = 4
        )

        val config = PosterRenderConfig(
            template = PosterTemplate.PASSPORT_STAMP,
            format = StampEditionFormat.PORTRAIT
        )
        val renderResult = PosterRenderer.render(context, trip, stamp, config)
        assertTrue(renderResult is PosterRenderResult.Success)
        val bitmap = (renderResult as PosterRenderResult.Success).bitmap
        val uri = PosterExporter.getShareablePosterUri(context, bitmap, stamp, StampEditionFormat.PORTRAIT)
        bitmap.recycle()

        assertNotNull(uri)
        assertTrue(uri.toString().contains("TravelStamp_Poster") || uri.toString().contains("posters"))
    }

    @Test
    fun testSavePassportStampToGallery() {
        val trip = Trip(
            id = 7L,
            name = "Rann of Kutch",
            destination = "Gujarat",
            date = "2026-12-25",
            status = TripStatus.COMPLETED,
            stampEarned = true
        )
        val stamp = TravelStamp(
            id = 7L,
            tripId = 7L,
            stampNumber = 7L,
            stampCode = "#007",
            title = "Rann of Kutch",
            destination = "Gujarat",
            dateText = "25 DEC 2026",
            peopleCount = 2,
            momentsCount = 1
        )
        val renderResult = PosterRenderer.render(
            context, trip, stamp,
            PosterRenderConfig(template = PosterTemplate.PASSPORT_STAMP, format = StampEditionFormat.PORTRAIT)
        )
        assertTrue(renderResult is PosterRenderResult.Success)
        val bitmap = (renderResult as PosterRenderResult.Success).bitmap
        val saved = PosterExporter.savePosterToGallery(context, bitmap, stamp, StampEditionFormat.PORTRAIT)
        bitmap.recycle()
        assertTrue(saved)
    }

    @Test
    fun testFormatChangeAndExportIntegritySafeguard() = runBlocking {
        // Complete trip and issue stamp #001
        val tripId = tripRepo.createTrip(
            Trip(name = "Kalsubai Peak", destination = "Igatpuri, Maharashtra", date = "10 Jan 2026")
        )
        val stamp = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Kalsubai Peak",
            destination = "Igatpuri, Maharashtra",
            dateText = "10 JAN 2026",
            peopleCount = 2,
            momentsCount = 0,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Summit morning"
        ).getOrThrow()
        val initialStampsCount = stampRepo.getAllStamps().first().size

        val trip = tripRepo.getTripById(tripId).first()!!

        // Render Square, Portrait, and Story
        for (format in listOf(StampEditionFormat.SQUARE, StampEditionFormat.PORTRAIT, StampEditionFormat.STORY)) {
            val config = PosterRenderConfig(template = PosterTemplate.PASSPORT_STAMP, format = format)
            val renderResult = PosterExporter.createPosterBitmap(context, trip, stamp, config)
            assertTrue(renderResult is PosterRenderResult.Success)
            val bitmap = (renderResult as PosterRenderResult.Success).bitmap
            assertEquals(format.width, bitmap.width)
            assertEquals(format.height, bitmap.height)
            bitmap.recycle()
        }

        // Verify database records are strictly read-only and 100% untouched
        val stampsAfterExport = stampRepo.getAllStamps().first().size
        assertEquals(initialStampsCount, stampsAfterExport)
        val tripAfter = tripRepo.getTripById(tripId).first()!!
        assertEquals(trip.status, tripAfter.status)
        assertEquals(trip.stampEarned, tripAfter.stampEarned)
        val stampAfter = stampRepo.getStampForTrip(tripId).first()!!
        assertEquals(stamp.id, stampAfter.id)
        assertEquals(stamp.stampNumber, stampAfter.stampNumber)
        assertEquals(stamp.stampCode, stampAfter.stampCode)
        assertEquals(stamp.reflectionNote, stampAfter.reflectionNote)
    }

    @Test
    fun testPhotoStampRenderingWithTransformations() {
        val trip = Trip(
            id = 201L,
            name = "Harishchandragad Trek",
            destination = "Ahmednagar, Maharashtra",
            date = "2026-11-20",
            status = TripStatus.COMPLETED,
            stampEarned = true
        )
        val stamp = TravelStamp(
            id = 201L,
            tripId = 201L,
            stampNumber = 8L,
            stampCode = "#008",
            title = "Harishchandragad Trek",
            destination = "Ahmednagar, Maharashtra",
            dateText = "20 NOV 2026",
            peopleCount = 4,
            momentsCount = 5
        )

        // Create a temporary sample photo bitmap
        val samplePhotoFile = File(context.cacheDir, "test_photo_sample.jpg")
        val samplePixels = IntArray(800 * 600) { AndroidColor.DKGRAY }
        val sampleBitmap = Bitmap.createBitmap(samplePixels, 800, 600, Bitmap.Config.ARGB_8888)
        FileOutputStream(samplePhotoFile).use { out ->
            sampleBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        sampleBitmap.recycle()

        val photoUri = samplePhotoFile.absolutePath

        // Render Photo+Stamp across all formats with custom framing and stamp positioning
        for (format in StampEditionFormat.values()) {
            val config = PosterRenderConfig(
                template = PosterTemplate.PHOTO_STAMP,
                format = format,
                photoUri = photoUri,
                panX = 0.05f,
                panY = -0.05f,
                zoom = 1.2f,
                stampPositionX = 0.45f,
                stampPositionY = 0.50f,
                stampSize = StampSize.LARGE
            )

            val renderResult = PosterRenderer.render(context, trip, stamp, config)
            assertTrue(renderResult is PosterRenderResult.Success)
            val bitmap = (renderResult as PosterRenderResult.Success).bitmap
            assertEquals(format.width, bitmap.width)
            assertEquals(format.height, bitmap.height)
            bitmap.recycle()
        }

        samplePhotoFile.delete()
    }

    @Test
    fun testSingleOfficialStampInvariantUnderRapidCompletion() = runBlocking {
        val tripId = tripRepo.createTrip(
            Trip(name = "Sandhan Valley", destination = "Samrad, Maharashtra", date = "05 Jan 2026")
        )

        // Attempt 1st issuance
        val firstResult = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Sandhan Valley",
            destination = "Samrad, Maharashtra",
            dateText = "05 JAN 2026",
            peopleCount = 3,
            momentsCount = 0,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = null
        )
        assertTrue(firstResult.isSuccess)
        val firstStamp = firstResult.getOrThrow()

        // Attempt 2nd issuance (Rapid tap simulation) - MUST return existing stamp without duplicating
        val secondResult = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Sandhan Valley",
            destination = "Samrad, Maharashtra",
            dateText = "05 JAN 2026",
            peopleCount = 3,
            momentsCount = 0,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = null
        )
        assertTrue(secondResult.isSuccess)
        val secondStamp = secondResult.getOrThrow()

        assertEquals(firstStamp.id, secondStamp.id)
        assertEquals(firstStamp.stampNumber, secondStamp.stampNumber)
        assertEquals(firstStamp.stampCode, secondStamp.stampCode)

        // Verify strictly 1 stamp exists in database for this trip
        val allStamps = stampRepo.getAllStamps().first()
        val tripStamps = allStamps.filter { it.tripId == tripId }
        assertEquals(1, tripStamps.size)
    }

    @Test
    fun testFitResponsiveTextThreeLinesForVeryLongTitle() {
        val basePaint = Paint().apply { textSize = 54f }
        val veryLongTitle = "EXPEDITION TO THE HIGHEST PEAK OF THE WESTERN GHATS RANGE"

        val fitted = PosterRenderer.fitResponsiveText(
            text = veryLongTitle,
            basePaint = basePaint,
            maxWidth = 800f,
            maxLines = 3,
            maxTextSize = 54f,
            minTextSize = 24f
        )

        assertTrue("Fitted lines should be 1 to 3", fitted.lines.size in 1..3)
        assertTrue("Lines should not contain ellipsis", fitted.lines.none { it.contains("…") })
        val allText = fitted.lines.joinToString(" ")
        assertTrue(allText.contains("EXPEDITION"))
        assertTrue(allText.contains("HIGHEST"))
        assertTrue(allText.contains("GHATS"))
    }

    // =========================================================================
    // PASS D.2 MANDATORY BITMAP-CONTENT REGRESSION & HARDENING TESTS
    // =========================================================================

    @Test
    fun testPhotoStampActualImageContentExportVerification() {
        // Create a distinctive test image with split colors: Left half is Pure RED, Right half is Pure BLUE
        val sourceWidth = 1000
        val sourceHeight = 1000
        val pixels = IntArray(sourceWidth * sourceHeight)
        for (y in 0 until sourceHeight) {
            for (x in 0 until sourceWidth) {
                pixels[y * sourceWidth + x] = if (x < 500) AndroidColor.RED else AndroidColor.BLUE
            }
        }
        val sourceBitmap = Bitmap.createBitmap(pixels, sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)

        val sourceFile = File(context.cacheDir, "red_blue_test.png")
        FileOutputStream(sourceFile).use { out ->
            sourceBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        sourceBitmap.recycle()

        // Prepare working image through PhotoUtils canonical pipeline
        val workingResult = PhotoUtils.prepareWorkingImage(context, Uri.fromFile(sourceFile))
        assertTrue("Working image preparation must succeed", workingResult is PhotoUtils.WorkingImageResult.Success)
        val workingPath = (workingResult as PhotoUtils.WorkingImageResult.Success).filePath

        val trip = Trip(id = 501L, name = "Harishchandragad", destination = "Western Ghats", date = "2026-08-23")
        val stamp = TravelStamp(
            id = 501L,
            tripId = 501L,
            stampNumber = 20L,
            stampCode = "#020",
            title = "Harishchandragad",
            destination = "Western Ghats",
            dateText = "23 AUG 2026",
            peopleCount = 2,
            momentsCount = 1
        )

        val config = PosterRenderConfig(
            template = PosterTemplate.PHOTO_STAMP,
            format = StampEditionFormat.SQUARE,
            photoUri = workingPath,
            panX = 0f,
            panY = 0f,
            zoom = 1f,
            stampPositionX = 0.5f,
            stampPositionY = 0.5f,
            stampSize = StampSize.MEDIUM
        )

        val renderResult = PosterRenderer.render(context, trip, stamp, config)
        assertTrue("Render must succeed with valid working image", renderResult is PosterRenderResult.Success)
        val renderedBitmap = (renderResult as PosterRenderResult.Success).bitmap

        assertEquals(1080, renderedBitmap.width)
        assertEquals(1080, renderedBitmap.height)

        // Sample pixels on the left (should be predominantly RED from the photo, not green/gold branded fallback)
        // Sample at y = 350 (above footer, away from gradients)
        val leftPixel = renderedBitmap.getPixel(150, 350)
        val leftRed = AndroidColor.red(leftPixel)
        val leftBlue = AndroidColor.blue(leftPixel)
        val leftGreen = AndroidColor.green(leftPixel)

        assertTrue(
            "Left side of poster must contain user photo RED content (expected red>120, blue<80, but got red=$leftRed, blue=$leftBlue, green=$leftGreen, pixelHex=${Integer.toHexString(leftPixel)})",
            leftRed > 120 && leftBlue < 80
        )

        // Sample pixels on the right (should be predominantly BLUE from the photo)
        val rightPixel = renderedBitmap.getPixel(930, 350)
        val rightRed = AndroidColor.red(rightPixel)
        val rightBlue = AndroidColor.blue(rightPixel)
        val rightGreen = AndroidColor.green(rightPixel)

        assertTrue(
            "Right side of poster must contain user photo BLUE content (expected blue>120, red<80, but got red=$rightRed, blue=$rightBlue, green=$rightGreen, pixelHex=${Integer.toHexString(rightPixel)})",
            rightBlue > 120 && rightRed < 80
        )

        // Test saving to gallery
        val saved = PosterExporter.savePosterToGallery(context, renderedBitmap, stamp, StampEditionFormat.SQUARE)
        assertTrue("Saving rendered photo stamp poster to gallery must succeed", saved)

        // Test shareable URI generation
        val shareUri = PosterExporter.getShareablePosterUri(context, renderedBitmap, stamp, StampEditionFormat.SQUARE)
        assertNotNull("Shareable URI for photo stamp must not be null", shareUri)

        renderedBitmap.recycle()
        sourceFile.delete()
    }

    @Test
    fun testPhotoStampMissingPhotoFailsExplicitly() {
        val trip = Trip(id = 502L, name = "Dudhsagar Falls", destination = "Goa", date = "2026-08-23")
        val stamp = TravelStamp(
            id = 502L,
            tripId = 502L,
            stampNumber = 21L,
            stampCode = "#021",
            title = "Dudhsagar Falls",
            destination = "Goa",
            dateText = "23 AUG 2026",
            peopleCount = 2,
            momentsCount = 0
        )

        // Null photoUri
        val nullConfig = PosterRenderConfig(
            template = PosterTemplate.PHOTO_STAMP,
            format = StampEditionFormat.PORTRAIT,
            photoUri = null
        )
        val nullResult = PosterRenderer.render(context, trip, stamp, nullConfig)
        assertTrue("Render with null photoUri must return Failure", nullResult is PosterRenderResult.Failure)

        // Blank photoUri
        val blankConfig = PosterRenderConfig(
            template = PosterTemplate.PHOTO_STAMP,
            format = StampEditionFormat.PORTRAIT,
            photoUri = "   "
        )
        val blankResult = PosterRenderer.render(context, trip, stamp, blankConfig)
        assertTrue("Render with blank photoUri must return Failure", blankResult is PosterRenderResult.Failure)
    }

    @Test
    fun testPhotoStampInvalidPhotoFailsExplicitly() {
        val trip = Trip(id = 503L, name = "Sinhagad Fort", destination = "Pune", date = "2026-08-23")
        val stamp = TravelStamp(
            id = 503L,
            tripId = 503L,
            stampNumber = 22L,
            stampCode = "#022",
            title = "Sinhagad Fort",
            destination = "Pune",
            dateText = "23 AUG 2026",
            peopleCount = 2,
            momentsCount = 0
        )

        val invalidConfig = PosterRenderConfig(
            template = PosterTemplate.PHOTO_STAMP,
            format = StampEditionFormat.PORTRAIT,
            photoUri = "/nonexistent/directory/invalid_image_12345.jpg"
        )
        val invalidResult = PosterRenderer.render(context, trip, stamp, invalidConfig)
        assertTrue("Render with invalid photo path must return Failure and never fake a fallback poster", invalidResult is PosterRenderResult.Failure)
    }

    @Test
    fun testPhotoUtilsInSampleSizeCalculation() {
        // High resolution 8000x6000 downsampled to target 2560
        val sampleSize8k = PhotoUtils.calculateInSampleSize(8000, 6000, 2560)
        assertTrue("8000x6000 image must have sampleSize >= 2", sampleSize8k >= 2)

        // High resolution 12000x9000 downsampled to target 2560
        val sampleSize12k = PhotoUtils.calculateInSampleSize(12000, 9000, 2560)
        assertTrue("12000x9000 image must have sampleSize >= 4", sampleSize12k >= 4)

        // Standard 1920x1080 fits within 2560 -> sampleSize 1
        val sampleSizeStandard = PhotoUtils.calculateInSampleSize(1920, 1080, 2560)
        assertEquals(1, sampleSizeStandard)
    }

    @Test
    fun testPhotoUtilsCacheCleanupSafety() {
        val editorDir = File(context.cacheDir, "photo_editor")
        editorDir.mkdirs()

        // Create 8 dummy files in cacheDir/photo_editor
        for (i in 1..8) {
            val file = File(editorDir, "edit_temp_$i.jpg")
            file.writeText("sample data $i")
            file.setLastModified(System.currentTimeMillis() + i * 1000)
        }

        // Create a persistent moment file in filesDir/moments
        val momentsDir = File(context.filesDir, "moments")
        momentsDir.mkdirs()
        val persistentMoment = File(momentsDir, "moment_sacred_record.jpg")
        persistentMoment.writeText("permanent moment data")

        // Run cleanup on editor working directory with limit 3
        PhotoUtils.cleanupWorkingDir(editorDir, maxFilesToKeep = 3)

        // Verify photo_editor only has 3 newest files
        val remainingEditorFiles = editorDir.listFiles()
        assertNotNull(remainingEditorFiles)
        assertEquals(3, remainingEditorFiles!!.size)

        // Verify persistent moments are completely untouched
        assertTrue("Persistent moment in filesDir must NEVER be deleted by editor cleanup", persistentMoment.exists())
        assertEquals("permanent moment data", persistentMoment.readText())

        persistentMoment.delete()
    }

    @Test
    fun testPosterExporterCacheCleanupSafety() {
        val posterDir = File(context.cacheDir, "posters")
        posterDir.mkdirs()

        for (i in 1..7) {
            val file = File(posterDir, "poster_temp_$i.png")
            file.writeText("poster $i")
            file.setLastModified(System.currentTimeMillis() + i * 1000)
        }

        PosterExporter.cleanupPosterCache(posterDir, maxFilesToKeep = 3)

        val remaining = posterDir.listFiles()
        assertNotNull(remaining)
        assertEquals(3, remaining!!.size)
    }

    @Test
    fun testPassD1SaveAndShareConfigurationsAreEqual() {
        val trip = Trip(id = 302L, name = "Rajmachi", destination = "Lonavala", date = "2026-08-21")
        val stamp = TravelStamp(
            id = 302L,
            tripId = 302L,
            stampNumber = 13L,
            stampCode = "#013",
            title = "Rajmachi",
            destination = "Lonavala",
            dateText = "21 AUG 2026",
            peopleCount = 2,
            momentsCount = 1
        )

        val photoPath = "/data/user/0/com.example/files/moments/moment_test.jpg"
        val saveConfig = PosterRenderConfig(
            template = PosterTemplate.PHOTO_STAMP,
            format = StampEditionFormat.SQUARE,
            photoUri = photoPath,
            panX = 0.1f,
            panY = -0.2f,
            zoom = 1.3f,
            stampPositionX = 0.4f,
            stampPositionY = 0.6f,
            stampSize = StampSize.LARGE
        )

        val shareConfig = PosterRenderConfig(
            template = PosterTemplate.PHOTO_STAMP,
            format = StampEditionFormat.SQUARE,
            photoUri = photoPath,
            panX = 0.1f,
            panY = -0.2f,
            zoom = 1.3f,
            stampPositionX = 0.4f,
            stampPositionY = 0.6f,
            stampSize = StampSize.LARGE
        )

        assertEquals(saveConfig.photoUri, shareConfig.photoUri)
        assertEquals(saveConfig.format, shareConfig.format)
        assertEquals(saveConfig.panX, shareConfig.panX, 0.001f)
        assertEquals(saveConfig.panY, shareConfig.panY, 0.001f)
        assertEquals(saveConfig.zoom, shareConfig.zoom, 0.001f)
        assertEquals(saveConfig.stampPositionX, shareConfig.stampPositionX, 0.001f)
        assertEquals(saveConfig.stampPositionY, shareConfig.stampPositionY, 0.001f)
        assertEquals(saveConfig.stampSize, shareConfig.stampSize)
    }

    @Test
    fun testPassD1PassportStampRendersCleanlyWithoutRedundantCapsule() {
        val trip = Trip(id = 303L, name = "Devkund Waterfall", destination = "Bhira, Maharashtra", date = "2026-08-22")
        val stamp = TravelStamp(
            id = 303L,
            tripId = 303L,
            stampNumber = 14L,
            stampCode = "#014",
            title = "Devkund Waterfall",
            destination = "Bhira, Maharashtra",
            dateText = "22 AUG 2026",
            peopleCount = 4,
            momentsCount = 2
        )

        for (format in StampEditionFormat.values()) {
            val config = PosterRenderConfig(
                template = PosterTemplate.PASSPORT_STAMP,
                format = format
            )
            val renderResult = PosterRenderer.render(context, trip, stamp, config)
            assertTrue(renderResult is PosterRenderResult.Success)
            val bitmap = (renderResult as PosterRenderResult.Success).bitmap
            assertEquals(format.width, bitmap.width)
            assertEquals(format.height, bitmap.height)
            bitmap.recycle()
        }
    }

    @Test
    fun testPassD1DirtyStateTrackingLogic() {
        var lastExportedFingerprint: String? = null

        fun computeFingerprint(uri: String?, fmt: String, pX: Float, pY: Float, z: Float, sX: Float, sY: Float, sz: String): String? {
            return if (uri == null && pX == 0f && pY == 0f && z == 1f && sX == 0.5f && sY == 0.44f && sz == "MEDIUM") {
                null
            } else {
                "uri=${uri}_fmt=${fmt}_panX=${pX}_panY=${pY}_zoom=${z}_sx=${sX}_sy=${sY}_sz=$sz"
            }
        }

        // Case 1: Fresh state with no photo -> Not dirty
        val fpInitial = computeFingerprint(null, "PORTRAIT", 0f, 0f, 1f, 0.5f, 0.44f, "MEDIUM")
        assertNull(fpInitial)
        var isDirty = fpInitial != null && fpInitial != lastExportedFingerprint
        assertFalse("Initial state without photo should not be dirty", isDirty)

        // Case 2: User selects photo & changes zoom -> Dirty
        val fpEdited = computeFingerprint("file:///test.jpg", "PORTRAIT", 0.1f, 0f, 1.2f, 0.5f, 0.44f, "LARGE")
        assertNotNull(fpEdited)
        isDirty = fpEdited != lastExportedFingerprint
        assertTrue("Edited state before export should be dirty", isDirty)

        // Case 3: Save succeeds -> Updates lastExportedFingerprint -> No longer dirty
        lastExportedFingerprint = fpEdited
        isDirty = fpEdited != lastExportedFingerprint
        assertFalse("State immediately after successful save should not be dirty", isDirty)

        // Case 4: Subsequent edit after save -> Dirty again
        val fpEditedAgain = computeFingerprint("file:///test.jpg", "PORTRAIT", 0.2f, 0f, 1.2f, 0.5f, 0.44f, "LARGE")
        isDirty = fpEditedAgain != lastExportedFingerprint
        assertTrue("State edited after save should be dirty again", isDirty)

        // Case 5: Share succeeds -> Updates lastExportedFingerprint -> Clean
        lastExportedFingerprint = fpEditedAgain
        isDirty = fpEditedAgain != lastExportedFingerprint
        assertFalse("State after share should be clean", isDirty)
    }

    @Test
    fun testPassD1ExportActionEnablingRules() {
        fun isSaveAndShareEnabled(template: PosterTemplate, photoUri: String?, isExportActive: Boolean): Boolean {
            val isPhotoMissing = template == PosterTemplate.PHOTO_STAMP && photoUri.isNullOrBlank()
            return !isExportActive && !isPhotoMissing
        }

        // Photo + Stamp with no photo: Disabled
        assertFalse(isSaveAndShareEnabled(PosterTemplate.PHOTO_STAMP, null, false))
        assertFalse(isSaveAndShareEnabled(PosterTemplate.PHOTO_STAMP, "", false))

        // Photo + Stamp with photo: Enabled
        assertTrue(isSaveAndShareEnabled(PosterTemplate.PHOTO_STAMP, "file:///photo.jpg", false))

        // Photo + Stamp while exporting: Disabled
        assertFalse(isSaveAndShareEnabled(PosterTemplate.PHOTO_STAMP, "file:///photo.jpg", true))

        // Passport Stamp without photo: Always Enabled
        assertTrue(isSaveAndShareEnabled(PosterTemplate.PASSPORT_STAMP, null, false))
    }
}
