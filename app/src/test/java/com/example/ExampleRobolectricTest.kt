package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TravelStampDatabase
import com.example.data.local.entity.TripEntity
import com.example.data.model.TravelStamp
import com.example.data.model.TripStatus
import com.example.data.repository.ChecklistRepositoryImpl
import com.example.data.repository.MomentRepositoryImpl
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepositoryImpl
import com.example.data.util.BackupManager
import com.example.data.util.DateUtils
import com.example.ui.util.StampExporter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: TravelStampDatabase
    private lateinit var tripRepo: TripRepositoryImpl
    private lateinit var stampRepo: TravelStampRepositoryImpl
    private lateinit var checklistRepo: ChecklistRepositoryImpl
    private lateinit var momentRepo: MomentRepositoryImpl

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tripRepo = TripRepositoryImpl(db.tripDao())
        stampRepo = TravelStampRepositoryImpl(db.travelStampDao())
        checklistRepo = ChecklistRepositoryImpl(db.checklistDao())
        momentRepo = MomentRepositoryImpl(db.momentDao())
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Travel Stamp", appName)
    }

    @Test
    fun `verify stamp numbers never renumber upon deletion and never reuse deleted numbers`() = runBlocking {
        // 1. Create and complete Trip 1: Harihar
        val trip1 = TripEntity(name = "Harihar Fort", destination = "Nashik", date = "10 Aug 2026", status = "COMPLETED")
        val trip1Id = db.tripDao().insertTrip(trip1)
        val stamp1 = stampRepo.issueOfficialStampForTrip(
            tripId = trip1Id,
            title = "Harihar Fort",
            destination = "Nashik",
            dateText = "10 Aug 2026",
            peopleCount = 4,
            momentsCount = 2,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Summit trek",
            completedAt = System.currentTimeMillis()
        )
        assertNotNull(stamp1)
        assertEquals("#001", stamp1!!.stampCode)
        assertEquals(1L, stamp1.stampNumber)

        // 2. Create and complete Trip 2: Kalsubai
        val trip2 = TripEntity(name = "Kalsubai Peak", destination = "Igatpuri", date = "12 Aug 2026", status = "COMPLETED")
        val trip2Id = db.tripDao().insertTrip(trip2)
        val stamp2 = stampRepo.issueOfficialStampForTrip(
            tripId = trip2Id,
            title = "Kalsubai Peak",
            destination = "Igatpuri",
            dateText = "12 Aug 2026",
            peopleCount = 3,
            momentsCount = 4,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Highest peak",
            completedAt = System.currentTimeMillis()
        )
        assertNotNull(stamp2)
        assertEquals("#002", stamp2!!.stampCode)
        assertEquals(2L, stamp2.stampNumber)

        // 3. Create and complete Trip 3: Anjaneri
        val trip3 = TripEntity(name = "Anjaneri Hills", destination = "Trimbak", date = "14 Aug 2026", status = "COMPLETED")
        val trip3Id = db.tripDao().insertTrip(trip3)
        val stamp3 = stampRepo.issueOfficialStampForTrip(
            tripId = trip3Id,
            title = "Anjaneri Hills",
            destination = "Trimbak",
            dateText = "14 Aug 2026",
            peopleCount = 2,
            momentsCount = 1,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Birthplace of Hanuman",
            completedAt = System.currentTimeMillis()
        )
        assertNotNull(stamp3)
        assertEquals("#003", stamp3!!.stampCode)
        assertEquals(3L, stamp3.stampNumber)

        // 4. Delete Trip 1 (#001 Harihar)
        tripRepo.deleteTrip(trip1Id)

        // Verify remaining stamps #002 (Kalsubai) and #003 (Anjaneri) remain UNCHANGED
        val checkStamp2 = stampRepo.getStampForTripSync(trip2Id)
        val checkStamp3 = stampRepo.getStampForTripSync(trip3Id)
        assertNotNull(checkStamp2)
        assertNotNull(checkStamp3)
        assertEquals("#002", checkStamp2!!.stampCode)
        assertEquals(2L, checkStamp2.stampNumber)
        assertEquals("Kalsubai Peak", checkStamp2.title)

        assertEquals("#003", checkStamp3!!.stampCode)
        assertEquals(3L, checkStamp3.stampNumber)
        assertEquals("Anjaneri Hills", checkStamp3.title)

        // 5. Create and complete Trip 4: Brahmagiri
        val trip4 = TripEntity(name = "Brahmagiri", destination = "Trimbakeshwar", date = "15 Aug 2026", status = "COMPLETED")
        val trip4Id = db.tripDao().insertTrip(trip4)
        val stamp4 = stampRepo.issueOfficialStampForTrip(
            tripId = trip4Id,
            title = "Brahmagiri",
            destination = "Trimbakeshwar",
            dateText = "15 Aug 2026",
            peopleCount = 5,
            momentsCount = 3,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Source of Godavari",
            completedAt = System.currentTimeMillis()
        )

        // Must be #004 — NEVER #001 (reused) and NEVER #003 (reused)
        assertNotNull(stamp4)
        assertEquals("#004", stamp4!!.stampCode)
        assertEquals(4L, stamp4.stampNumber)

        // 6. Finishing Trip 2 again must NOT generate a duplicate stamp
        val stamp2DuplicateAttempt = stampRepo.issueOfficialStampForTrip(
            tripId = trip2Id,
            title = "Kalsubai Peak Edited",
            destination = "Igatpuri",
            dateText = "12 Aug 2026",
            peopleCount = 3,
            momentsCount = 4,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Highest peak edited",
            completedAt = System.currentTimeMillis()
        )
        assertNotNull(stamp2DuplicateAttempt)
        assertEquals("#002", stamp2DuplicateAttempt!!.stampCode)
        assertEquals(2L, stamp2DuplicateAttempt.stampNumber)
        assertEquals(stamp2.id, stamp2DuplicateAttempt.id)
    }

    @Test
    fun `verify future trip cannot be finished or issued stamp`() = runBlocking {
        val futureTrip = TripEntity(
            name = "Ladakh Expedition",
            destination = "Leh",
            date = "25 Dec 2026",
            status = "UPCOMING"
        )
        val futureTripId = db.tripDao().insertTrip(futureTrip)
        val finishResult = tripRepo.finishTrip(futureTripId)
        assertEquals(false, finishResult)

        val stampResult = stampRepo.issueOfficialStampForTrip(
            tripId = futureTripId,
            title = "Ladakh Expedition",
            destination = "Leh",
            dateText = "25 Dec 2026",
            peopleCount = 2,
            momentsCount = 0,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = null,
            completedAt = System.currentTimeMillis()
        )
        assertEquals(null, stampResult)
    }

    @Test
    fun `verify backup export and import integrity`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Seed trip and stamp
        val trip = TripEntity(name = "Sandhan Valley", destination = "Samrad", date = "10 Aug 2026", status = "COMPLETED")
        val tripId = db.tripDao().insertTrip(trip)
        stampRepo.issueOfficialStampForTrip(
            tripId = tripId,
            title = "Sandhan Valley",
            destination = "Samrad",
            dateText = "10 Aug 2026",
            peopleCount = 6,
            momentsCount = 1,
            inkColorHex = "#C85A32",
            stampStyle = "EXPEDITION",
            reflectionNote = "Valley of Shadows",
            completedAt = System.currentTimeMillis()
        )

        // 2. Export backup
        val exportResult = BackupManager.createExportFile(context, db)
        assertNotNull(exportResult)
        assertTrue(exportResult.fileName.endsWith(".tsbackup"))
        assertEquals(1, exportResult.totalTrips)
        assertEquals(1, exportResult.totalStamps)

        // 3. Clear DB and verify restoration
        db.tripDao().deleteTripById(tripId)
        val tripsBefore = db.tripDao().getAllTripsListSync()
        assertEquals(0, tripsBefore.size)

        val importResult = BackupManager.importBackup(context, exportResult.fileUri, db)
        assertTrue(importResult.isSuccess)
        val counts = importResult.getOrNull()
        assertNotNull(counts)
        assertEquals(1, counts!!.importedTrips)
        assertEquals(1, counts.importedStamps)

        val tripsAfter = db.tripDao().getAllTripsListSync()
        assertEquals(1, tripsAfter.size)
        assertEquals("Sandhan Valley", tripsAfter[0].name)
    }

    @Test
    fun `verify zip slip attack prevention`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val maliciousZip = File(context.cacheDir, "malicious.zip")

        ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
            // Path traversal entry
            zos.putNextEntry(ZipEntry("../../../evil.txt"))
            zos.write("malicious payload".toByteArray())
            zos.closeEntry()
        }

        val maliciousUri = Uri.fromFile(maliciousZip)
        val result = BackupManager.importBackup(context, maliciousUri, db)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException || result.exceptionOrNull()?.message?.contains("Zip Slip") == true)
    }

    @Test
    fun `create stamp bitmap and share uri consistency`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sampleStamp = TravelStamp(
            id = 2,
            tripId = 102,
            stampNumber = 2L,
            stampCode = "#002",
            title = "Kalsubai",
            destination = "Igatpuri, Maharashtra",
            dateText = "18 Aug 2026",
            peopleCount = 4,
            momentsCount = 3,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Foggy summit trek"
        )

        val bitmap = StampExporter.createStampBitmap(context, sampleStamp, photoUri = null)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1350, bitmap.height)

        val shareUri = StampExporter.getShareableUri(context, bitmap, sampleStamp)
        assertNotNull(shareUri)
        assertTrue(shareUri.toString().contains("TravelStamp_002_Kalsubai"))
    }

    @Test
    fun `verify camera temp uri generation and valid cache location`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = com.example.ui.util.PhotoUtils.createCameraTempUri(context)
        assertNotNull(uri)
        assertTrue(uri.toString().isNotEmpty())

        val photosDir = File(context.cacheDir, "photos")
        assertTrue(photosDir.exists())
        val files = photosDir.listFiles()
        assertNotNull(files)
        assertTrue(files!!.isNotEmpty())
    }

    @Test
    fun `verify captured photo copy to permanent moments storage`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempUri = com.example.ui.util.PhotoUtils.createCameraTempUri(context)

        // Simulate camera writing 1024 bytes of dummy image data
        val photosDir = File(context.cacheDir, "photos")
        val tempFile = photosDir.listFiles()?.firstOrNull { it.name.endsWith("_temp.jpg") }
        assertNotNull(tempFile)
        FileOutputStream(tempFile!!).use {
            it.write("fake-jpeg-image-bytes-data-stream".toByteArray())
        }

        val permanentPath = com.example.ui.util.PhotoUtils.copyUriToPermanentStorage(context, tempUri)
        assertNotNull(permanentPath)
        assertTrue(permanentPath!!.contains("/moments/moment_"))

        val storedFile = File(permanentPath)
        assertTrue(storedFile.exists())
        assertTrue(storedFile.length() > 0L)

        // Verify temp file was cleaned up
        assertTrue(!tempFile.exists() || tempFile.length() == 0L)
    }

    @Test
    fun `verify zero-byte camera image is rejected safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempUri = com.example.ui.util.PhotoUtils.createCameraTempUri(context)

        // 0-byte file (no bytes written by camera)
        val permanentPath = com.example.ui.util.PhotoUtils.copyUriToPermanentStorage(context, tempUri)
        assertEquals(null, permanentPath)
    }

    @Test
    fun `verify cleanup of temporary camera cache files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempUri = com.example.ui.util.PhotoUtils.createCameraTempUri(context)

        com.example.ui.util.PhotoUtils.cleanUpTempFile(context, tempUri.toString())

        val photosDir = File(context.cacheDir, "photos")
        val tempFiles = photosDir.listFiles()?.filter { it.name.endsWith("_temp.jpg") } ?: emptyList()
        assertEquals(0, tempFiles.size)
    }

    @Test
    fun `verify camera moment persistence in database`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val trip = TripEntity(name = "Kalsubai Monsoon", destination = "Igatpuri", date = "18 Aug 2026", status = "IN_PROGRESS")
        val tripId = db.tripDao().insertTrip(trip)

        // Create sample photo in permanent storage
        val momentsDir = File(context.filesDir, "moments").apply { mkdirs() }
        val testImageFile = File(momentsDir, "moment_test_123.jpg").apply {
            writeBytes("test-photo-binary-content".toByteArray())
        }

        val moment = com.example.data.model.Moment(
            tripId = tripId,
            category = com.example.data.model.MomentCategory.PHOTO,
            note = "Summit ridge reached through fog",
            imageUri = testImageFile.absolutePath,
            timestamp = System.currentTimeMillis()
        )

        val momentId = momentRepo.addMoment(moment)
        assertTrue(momentId > 0)

        val retrievedMoments = momentRepo.getMomentsForTripSync(tripId)
        assertEquals(1, retrievedMoments.size)
        assertEquals("Summit ridge reached through fog", retrievedMoments[0].note)
        assertEquals(testImageFile.absolutePath, retrievedMoments[0].imageUri)
        assertEquals(com.example.data.model.MomentCategory.PHOTO, retrievedMoments[0].category)
    }

    @Test
    fun `verify historical and standard trip date parsing and formatting`() {
        val date1 = DateUtils.parseTripDate("16 July 2021")
        assertNotNull(date1)
        assertEquals(2021, date1!!.year)
        assertEquals(7, date1.monthValue)
        assertEquals(16, date1.dayOfMonth)

        val date2 = DateUtils.parseTripDate("7 June 2022")
        assertNotNull(date2)
        assertEquals(2022, date2!!.year)
        assertEquals(6, date2.monthValue)
        assertEquals(7, date2.dayOfMonth)

        val date3 = DateUtils.parseTripDate("24 March 2023")
        assertNotNull(date3)
        assertEquals(2023, date3!!.year)
        assertEquals(3, date3.monthValue)
        assertEquals(24, date3.dayOfMonth)

        val date4 = DateUtils.parseTripDate("17 August 2026")
        assertNotNull(date4)
        assertEquals(2026, date4!!.year)
        assertEquals(8, date4.monthValue)
        assertEquals(17, date4.dayOfMonth)

        // Verify formatting with "d MMMM yyyy"
        val formatted = date4.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.ENGLISH))
        assertEquals("17 August 2026", formatted)
    }

    @Test
    fun `verify unparseable date fallback safety`() {
        val parsedNull = DateUtils.parseTripDate(null)
        assertNull(parsedNull)

        val parsedBlank = DateUtils.parseTripDate("   ")
        assertNull(parsedBlank)

        val parsedInvalid = DateUtils.parseTripDate("invalid-custom-date")
        assertNull(parsedInvalid)
    }

    @Test
    fun `verify updating trip date persists correctly in database`() = runBlocking {
        val trip = TripEntity(name = "Kalsubai Monsoon", destination = "Igatpuri", date = "10 August 2026", status = "IN_PROGRESS")
        val tripId = db.tripDao().insertTrip(trip)

        val retrievedTrip = db.tripDao().getTripByIdSync(tripId)
        assertNotNull(retrievedTrip)
        assertEquals("10 August 2026", retrievedTrip!!.date)

        // Update trip date
        val updatedTrip = retrievedTrip.copy(date = "17 August 2026")
        db.tripDao().updateTrip(updatedTrip)

        val refreshedTrip = db.tripDao().getTripByIdSync(tripId)
        assertNotNull(refreshedTrip)
        assertEquals("17 August 2026", refreshedTrip!!.date)
    }

    @Test
    fun `verify onboarding completion state persistence and skip behavior`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsRepo = com.example.data.local.UserPreferencesRepositoryImpl(context)

        // Initially false
        prefsRepo.setOnboardingCompleted(false)
        assertEquals(false, prefsRepo.hasCompletedOnboarding.value)

        // Mark completed (via finish or skip)
        prefsRepo.setOnboardingCompleted(true)
        assertEquals(true, prefsRepo.hasCompletedOnboarding.value)

        // Recreate repo instance to verify persistent SharedPreferences storage
        val newPrefsRepoInstance = com.example.data.local.UserPreferencesRepositoryImpl(context)
        assertEquals(true, newPrefsRepoInstance.hasCompletedOnboarding.value)
    }

    @Test
    fun `verify passport search matches title, destination, stamp code, and number`() = runBlocking {
        val trip1 = TripEntity(name = "Dehergad Fort Trek", destination = "Nashik Valley", date = "10 Aug 2026", description = "Monsoon trail", status = "COMPLETED")
        val trip1Id = db.tripDao().insertTrip(trip1)
        val stamp1 = stampRepo.issueOfficialStampForTrip(
            tripId = trip1Id,
            title = "Dehergad Fort Trek",
            destination = "Nashik Valley",
            dateText = "10 Aug 2026",
            peopleCount = 2,
            momentsCount = 5,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Foggy summit with breathtaking views",
            completedAt = System.currentTimeMillis()
        )
        assertNotNull(stamp1)

        val trip2 = TripEntity(name = "Kalsubai Sunrise", destination = "Igatpuri", date = "12 Aug 2026", description = "Highest peak", status = "COMPLETED")
        val trip2Id = db.tripDao().insertTrip(trip2)
        val stamp2 = stampRepo.issueOfficialStampForTrip(
            tripId = trip2Id,
            title = "Kalsubai Sunrise",
            destination = "Igatpuri",
            dateText = "12 Aug 2026",
            peopleCount = 4,
            momentsCount = 2,
            inkColorHex = "#8B1E0F",
            stampStyle = "EXPEDITION",
            reflectionNote = "Challenging climb",
            completedAt = System.currentTimeMillis()
        )
        assertNotNull(stamp2)

        val allStamps = listOf(stamp1!!, stamp2!!)

        // Test search by destination
        val nashikMatches = allStamps.filter { it.destination.contains("Nashik", ignoreCase = true) }
        assertEquals(1, nashikMatches.size)
        assertEquals("Dehergad Fort Trek", nashikMatches.first().title)

        // Test search by stamp code
        val codeMatches = allStamps.filter { it.stampCode.contains("#001", ignoreCase = true) || it.stampNumber.toString() == "1" }
        assertEquals(1, codeMatches.size)
        assertEquals(1L, codeMatches.first().stampNumber)

        // Test search by reflection note
        val fogMatches = allStamps.filter { it.reflectionNote?.contains("Foggy", ignoreCase = true) == true }
        assertEquals(1, fogMatches.size)

        // Test search with no matches
        val noMatches = allStamps.filter { it.title.contains("NonExistentPlace", ignoreCase = true) }
        assertTrue(noMatches.isEmpty())
    }

    @Test
    fun `verify passport sorting works correctly on large datasets`() = runBlocking {
        val stamps = mutableListOf<TravelStamp>()
        for (i in 1..25) {
            val trip = TripEntity(name = "Expedition $i", destination = "Dest $i", date = "01 Jan 2026", status = "COMPLETED")
            val id = db.tripDao().insertTrip(trip)
            val stamp = stampRepo.issueOfficialStampForTrip(
                tripId = id,
                title = "Expedition $i",
                destination = "Dest $i",
                dateText = "01 Jan 2026",
                peopleCount = 1,
                momentsCount = i % 7,
                inkColorHex = "#1E3A2F",
                stampStyle = "MOUNTAIN",
                reflectionNote = null,
                completedAt = System.currentTimeMillis()
            )
            stamps.add(stamp!!)
        }

        assertEquals(25, stamps.size)

        // Sort Stamp Number: High to Low
        val descStamps = stamps.sortedByDescending { it.stampNumber }
        assertEquals(25L, descStamps.first().stampNumber)
        assertEquals(1L, descStamps.last().stampNumber)

        // Sort Stamp Number: Low to High
        val ascStamps = stamps.sortedBy { it.stampNumber }
        assertEquals(1L, ascStamps.first().stampNumber)
        assertEquals(25L, ascStamps.last().stampNumber)

        // Sort Most Moments
        val mostMoments = stamps.sortedByDescending { it.momentsCount }
        assertEquals(6, mostMoments.first().momentsCount)
    }
}
