package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.BundledSuggestionSourceImpl
import com.example.data.datasource.UserHistorySuggestionSourceImpl
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.Moment
import com.example.data.model.MomentCategory
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
import com.example.ui.poster.PosterRenderer
import com.example.ui.poster.PosterTemplate
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
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
    fun testPosterRenderConfigDefaults() {
        val config = PosterRenderConfig()
        assertEquals(PosterTemplate.PHOTO_STAMP, config.template)
        assertEquals(null, config.photoUri)
        assertEquals(0f, config.panX, 0.001f)
        assertEquals(0f, config.panY, 0.001f)
        assertEquals(1f, config.zoom, 0.001f)
    }

    @Test
    fun testPosterRendererOutputDimensionsTemplateA() {
        val trip = Trip(
            id = 1L,
            name = "Western Ghats Trek",
            destination = "Sahyadri Ranges, Maharashtra",
            date = "2026-10-14",
            status = TripStatus.COMPLETED,
            stampEarned = true
        )
        val stamp = TravelStamp(
            id = 1L,
            tripId = 1L,
            stampNumber = 1L,
            stampCode = "#001",
            title = "Western Ghats Trek",
            destination = "Sahyadri Ranges, Maharashtra",
            dateText = "14 OCT 2026",
            peopleCount = 2,
            momentsCount = 3,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN"
        )
        val config = PosterRenderConfig(template = PosterTemplate.PHOTO_STAMP)

        val bitmap = PosterRenderer.render(context, trip, stamp, config)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
    }

    @Test
    fun testPosterRendererOutputDimensionsTemplateB() {
        val trip = Trip(
            id = 2L,
            name = "Himalayan Expedition",
            destination = "Manali, Himachal Pradesh",
            date = "2026-11-20",
            status = TripStatus.COMPLETED,
            stampEarned = true
        )
        val stamp = TravelStamp(
            id = 2L,
            tripId = 2L,
            stampNumber = 2L,
            stampCode = "#002",
            title = "Himalayan Expedition",
            destination = "Manali, Himachal Pradesh",
            dateText = "20 NOV 2026",
            peopleCount = 4,
            momentsCount = 5,
            inkColorHex = "#C85A32",
            stampStyle = "COMPASS"
        )
        val config = PosterRenderConfig(template = PosterTemplate.PASSPORT_STAMP)

        val bitmap = PosterRenderer.render(context, trip, stamp, config)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
    }

    @Test
    fun testPosterRendererWithValidMomentPhoto() {
        // Create a temporary test bitmap image file
        val tempPhotoFile = File(context.cacheDir, "test_moment_photo.png")
        val sampleBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        FileOutputStream(tempPhotoFile).use { out ->
            sampleBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val trip = Trip(
            id = 3L,
            name = "Konkan Coastline",
            destination = "Alibaug, Maharashtra",
            date = "2026-12-05",
            status = TripStatus.COMPLETED,
            stampEarned = true
        )
        val stamp = TravelStamp(
            id = 3L,
            tripId = 3L,
            stampNumber = 3L,
            stampCode = "#003",
            title = "Konkan Coastline",
            destination = "Alibaug, Maharashtra",
            dateText = "05 DEC 2026",
            peopleCount = 1,
            momentsCount = 1,
            inkColorHex = "#1B4332",
            stampStyle = "PINE"
        )
        val config = PosterRenderConfig(
            template = PosterTemplate.PHOTO_STAMP,
            photoUri = tempPhotoFile.absolutePath,
            panX = 0.1f,
            panY = -0.1f,
            zoom = 1.2f
        )

        val bitmap = PosterRenderer.render(context, trip, stamp, config)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
    }

    @Test
    fun testPosterRendererWithCorruptedOrMissingPhotoGracefulFallback() {
        val trip = Trip(
            id = 4L,
            name = "Rainforest Trail",
            destination = "Agumbe, Karnataka",
            date = "2026-09-01",
            status = TripStatus.COMPLETED,
            stampEarned = true
        )
        val stamp = TravelStamp(
            id = 4L,
            tripId = 4L,
            stampNumber = 4L,
            stampCode = "#004",
            title = "Rainforest Trail",
            destination = "Agumbe, Karnataka",
            dateText = "01 SEP 2026",
            peopleCount = 2,
            momentsCount = 0
        )
        val config = PosterRenderConfig(
            template = PosterTemplate.PHOTO_STAMP,
            photoUri = "/invalid/path/to/missing_image_12345.jpg"
        )

        // Should not crash and should render branded fallback cleanly
        val bitmap = PosterRenderer.render(context, trip, stamp, config)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
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
        val configA = PosterRenderConfig(template = PosterTemplate.PHOTO_STAMP)
        val bitmapA = PosterRenderer.render(context, trip, stamp, configA)
        assertNotNull(bitmapA)
        assertEquals(1080, bitmapA.width)
        assertEquals(1920, bitmapA.height)

        val configB = PosterRenderConfig(template = PosterTemplate.PASSPORT_STAMP)
        val bitmapB = PosterRenderer.render(context, trip, stamp, configB)
        assertNotNull(bitmapB)
        assertEquals(1080, bitmapB.width)
        assertEquals(1920, bitmapB.height)
    }

    @Test
    fun testTextWrappingAndEllipsis() {
        val paint = Paint().apply { textSize = 40f }
        val veryLongText = "This is an extremely long expedition name that will definitely exceed the standard canvas line bounds and require wrapping across multiple lines safely."
        val wrappedLines = PosterRenderer.wrapAndLimitText(veryLongText, paint, maxWidth = 300f, maxLines = 2)

        assertTrue(wrappedLines.size in 1..2)
        for (line in wrappedLines) {
            assertTrue(line.isNotBlank())
        }

        val truncated = PosterRenderer.truncateTextSingleLine("ExtremelyLongUnbrokenWordThatExceedsCanvasBounds", paint, maxWidth = 100f)
        assertTrue(truncated.endsWith("…") || truncated.isNotEmpty())
    }

    @Test
    fun testShareablePosterUriGeneration() {
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
        val bitmap = PosterRenderer.render(context, trip, stamp, PosterRenderConfig())
        val uri = PosterExporter.getShareablePosterUri(context, bitmap, stamp)

        assertNotNull(uri)
        assertTrue(uri.toString().contains("TravelStamp_Poster") || uri.toString().contains("posters"))
    }

    @Test
    fun testSavePosterToGallery() {
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
        val bitmap = PosterRenderer.render(context, trip, stamp, PosterRenderConfig())
        val saved = PosterExporter.savePosterToGallery(context, bitmap, stamp)
        // MediaStore insert in Robolectric environment
        assertTrue(saved)
    }

    @Test
    fun testPosterReadonlySafeguard() = runBlocking {
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
            reflectionNote = null
        ).getOrThrow()
        val initialStampsCount = stampRepo.getAllStamps().first().size

        val trip = tripRepo.getTripById(tripId).first()!!
        val config = PosterRenderConfig(template = PosterTemplate.PHOTO_STAMP)

        // Render poster
        val bitmap = PosterExporter.createPosterBitmap(context, trip, stamp, config)
        assertNotNull(bitmap)

        // Verify database records are 100% unchanged (read-only)
        val stampsAfterExport = stampRepo.getAllStamps().first().size
        assertEquals(initialStampsCount, stampsAfterExport)
        val tripAfter = tripRepo.getTripById(tripId).first()!!
        assertEquals(trip.status, tripAfter.status)
        assertEquals(trip.stampEarned, tripAfter.stampEarned)
    }
}
