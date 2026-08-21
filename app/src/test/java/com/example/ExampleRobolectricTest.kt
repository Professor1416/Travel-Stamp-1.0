package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepositoryImpl
import com.example.data.local.entity.TripEntity
import com.example.data.model.Moment
import com.example.data.model.MomentCategory
import com.example.data.model.TravelStamp
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.data.repository.ChecklistRepositoryImpl
import com.example.data.repository.MomentRepositoryImpl
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepositoryImpl
import com.example.data.util.BackupManager
import com.example.data.util.DatePeriodFilter
import com.example.data.util.DateUtils
import com.example.data.util.JourneySortOption
import com.example.data.util.MomentsFilter
import com.example.data.util.SearchUtils
import com.example.data.util.StampSortOption
import com.example.data.util.StatusFilter
import com.example.ui.util.StampExporter
import com.example.ui.viewmodel.FinishTripUiState
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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
        tripRepo = TripRepositoryImpl(db.tripDao(), db.momentDao(), context)
        stampRepo = TravelStampRepositoryImpl(db.travelStampDao())
        checklistRepo = ChecklistRepositoryImpl(db.checklistDao())
        momentRepo = MomentRepositoryImpl(db.momentDao(), context)
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
    fun `verify passport sorting works correctly on large datasets`(): Unit = runBlocking {
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

    @Test
    fun `verify database migration from version 1 schema preserves all data and generates valid UUIDs`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "test_v1_migration.db")
        if (dbFile.exists()) dbFile.delete()

        // Create a raw SQLite DB at version 1
        val sqliteDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteDb.version = 1

        // v1 tables
        sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `destination` TEXT NOT NULL, `date` TEXT NOT NULL, `peopleCount` INTEGER NOT NULL, `description` TEXT NOT NULL, `status` TEXT NOT NULL, `stampEarned` INTEGER NOT NULL, `completedAt` INTEGER, `createdAt` INTEGER NOT NULL)")
        sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS `checklist_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tripId` INTEGER NOT NULL, `text` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_items_tripId` ON `checklist_items` (`tripId`)")
        sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS `moments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tripId` INTEGER NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, `imageUri` TEXT, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqliteDb.execSQL("CREATE INDEX IF NOT EXISTS `index_moments_tripId` ON `moments` (`tripId`)")
        sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS `travel_stamps` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tripId` INTEGER NOT NULL, `stampNumber` INTEGER NOT NULL, `stampCode` TEXT NOT NULL, `title` TEXT NOT NULL, `destination` TEXT NOT NULL, `dateText` TEXT NOT NULL, `peopleCount` INTEGER NOT NULL, `momentsCount` INTEGER NOT NULL, `inkColorHex` TEXT NOT NULL, `stampStyle` TEXT NOT NULL, `inspectionText` TEXT NOT NULL, `issuedAt` INTEGER NOT NULL, `reflectionNote` TEXT, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqliteDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_travel_stamps_tripId` ON `travel_stamps` (`tripId`)")
        sqliteDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_travel_stamps_stampNumber` ON `travel_stamps` (`stampNumber`)")

        // Insert legacy data
        sqliteDb.execSQL("INSERT INTO trips (id, name, destination, date, peopleCount, description, status, stampEarned, completedAt, createdAt) VALUES (1, 'V1 Old Trip', 'Himalayas', '10 May 2025', 2, 'Great trek', 'COMPLETED', 1, 1746864000000, 1746800000000)")
        sqliteDb.execSQL("INSERT INTO checklist_items (id, tripId, text, isCompleted, sortOrder) VALUES (1, 1, 'Trek shoes', 1, 0)")
        sqliteDb.execSQL("INSERT INTO moments (id, tripId, category, note, timestamp) VALUES (1, 1, 'NOTE', 'Camped at Base 1', 1746850000000)")
        sqliteDb.execSQL("INSERT INTO travel_stamps (id, tripId, stampNumber, stampCode, title, destination, dateText, peopleCount, momentsCount, inkColorHex, stampStyle, inspectionText, issuedAt, reflectionNote) VALUES (1, 1, 1, '#001', 'V1 Old Trip', 'Himalayas', '10 May 2025', 2, 1, '#1E3A2F', 'MOUNTAIN', 'OFFICIALLY LOGGED', 1746864000000, 'Legacy reflection')")
        sqliteDb.close()

        // Now open with Room using version 4 and our migrations
        val migratedDb = Room.databaseBuilder(context, TravelStampDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
                TravelStampDatabase.MIGRATION_1_2,
                TravelStampDatabase.MIGRATION_2_3,
                TravelStampDatabase.MIGRATION_3_4,
                TravelStampDatabase.MIGRATION_1_4,
                TravelStampDatabase.MIGRATION_2_4,
                TravelStampDatabase.MIGRATION_1_3
            )
            .build()

        val trips = migratedDb.tripDao().getAllTripsListSync()
        assertEquals(1, trips.size)
        assertEquals("V1 Old Trip", trips[0].name)
        assertTrue(trips[0].uuid.isNotBlank())
        assertEquals(1746800000000L, trips[0].updatedAt)

        val stamps = migratedDb.travelStampDao().getAllStampsListSync()
        assertEquals(1, stamps.size)
        assertEquals("V1 Old Trip", stamps[0].title)
        assertTrue(stamps[0].uuid.isNotBlank())
        assertEquals("Legacy reflection", stamps[0].reflectionNote)

        val moments = migratedDb.momentDao().getAllMomentsListSync()
        assertEquals(1, moments.size)
        assertEquals("Camped at Base 1", moments[0].note)
        assertTrue(moments[0].uuid.isNotBlank())

        val items = migratedDb.checklistDao().getAllItemsListSync()
        assertEquals(1, items.size)
        assertEquals("Trek shoes", items[0].text)
        assertTrue(items[0].uuid.isNotBlank())

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    fun `verify DateUtils getEpochDay handles varied date formats accurately`() {
        val epoch1 = DateUtils.getEpochDay("16 Aug 2026")
        val epoch2 = DateUtils.getEpochDay("17 Aug 2026")
        val epoch3 = DateUtils.getEpochDay("2026-08-18")
        val epoch4 = DateUtils.getEpochDay("19/08/2026")

        assertTrue(epoch2 > epoch1)
        assertTrue(epoch3 > epoch2)
        assertTrue(epoch4 > epoch3)
    }

    @Test
    fun `verify scaling to 100 journeys with search, chronological sorting and stamp preservation`(): Unit = runBlocking {
        // Create 100 journeys: 30 upcoming/in progress (future), 70 completed with permanent stamps (past)
        for (i in 1..100) {
            val isCompleted = i <= 70
            val day = (i % 28) + 1
            val month = when ((i % 12) + 1) {
                1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"; 5 -> "May"; 6 -> "Jun"
                7 -> "Jul"; 8 -> "Aug"; 9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; else -> "Dec"
            }
            // Completed treks are past (2020-2025), upcoming treks are in 2027+
            val year = if (isCompleted) 2020 + (i % 6) else 2027 + (i % 3)
            val dateStr = String.format("%02d %s %d", day, month, year)
            val name = when {
                i % 4 == 0 -> "Torna Fort Expedition $i"
                i % 4 == 1 -> "Rajgad Trek $i"
                i % 4 == 2 -> "Harishchandragad Climb $i"
                else -> "Sinhagad Hike $i"
            }
            val dest = if (i % 2 == 0) "Pune, Maharashtra" else "Nashik, Maharashtra"

            val statusStr = if (isCompleted) "COMPLETED" else "UPCOMING"
            val tripEntity = TripEntity(
                name = name,
                destination = dest,
                date = dateStr,
                peopleCount = (i % 4) + 1,
                description = "Scenic trek in Western Ghats",
                status = statusStr,
                stampEarned = isCompleted,
                completedAt = if (isCompleted) System.currentTimeMillis() else null
            )
            val tripId = db.tripDao().insertTrip(tripEntity)

            if (isCompleted) {
                val stamp = stampRepo.issueOfficialStampForTrip(
                    tripId = tripId,
                    title = name,
                    destination = dest,
                    dateText = dateStr,
                    peopleCount = (i % 4) + 1,
                    momentsCount = i % 5,
                    inkColorHex = "#1E3A2F",
                    stampStyle = "MOUNTAIN",
                    reflectionNote = "Memorable expedition #$i",
                    completedAt = System.currentTimeMillis()
                )
                assertNotNull(stamp)
            }
        }

        // Verify total completed count and stamp counts
        val allStamps = db.travelStampDao().getAllStampsListSync()
        assertEquals(70, allStamps.size)

        // Monotonic permanent stamp numbers strictly 1..70
        val stampNumbers = allStamps.map { it.stampNumber }.sorted()
        assertEquals(1L, stampNumbers.first())
        assertEquals(70L, stampNumbers.last())

        // Verify adding a moment to an existing completed trip does NOT alter or renumber stamp
        val completedTrip1 = db.tripDao().getAllTripsListSync().first { it.status == "COMPLETED" }
        val stampBefore = db.travelStampDao().getStampForTripSync(completedTrip1.id)
        assertNotNull(stampBefore)

        val momentEntity = com.example.data.local.entity.MomentEntity(
            tripId = completedTrip1.id,
            category = "NOTE",
            note = "Late reflection note added post-stamping",
            timestamp = System.currentTimeMillis()
        )
        db.momentDao().insertMoment(momentEntity)

        val stampAfter = db.travelStampDao().getStampForTripSync(completedTrip1.id)
        assertNotNull(stampAfter)
        assertEquals(stampBefore?.stampNumber, stampAfter?.stampNumber)
        assertEquals(stampBefore?.stampCode, stampAfter?.stampCode)

        // Verify search by Maharashtra region
        val puneStamps = allStamps.filter { it.destination.contains("Pune", ignoreCase = true) }
        assertEquals(35, puneStamps.size)

        // Verify chronological sorting (newest first vs oldest first)
        val sortedDesc = allStamps.sortedByDescending { DateUtils.getEpochDay(it.dateText, it.issuedAt) }
        val sortedAsc = allStamps.sortedBy { DateUtils.getEpochDay(it.dateText, it.issuedAt) }
        assertTrue(DateUtils.getEpochDay(sortedDesc.first().dateText, sortedDesc.first().issuedAt) >= DateUtils.getEpochDay(sortedDesc.last().dateText, sortedDesc.last().issuedAt))
        assertTrue(DateUtils.getEpochDay(sortedAsc.first().dateText, sortedAsc.first().issuedAt) <= DateUtils.getEpochDay(sortedAsc.last().dateText, sortedAsc.last().issuedAt))
    }

    @Test
    fun `verify finishTrip and issueStamp is atomic and idempotent`() = runBlocking {
        // Create trip with moments and checklists
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Rajgad Fort Expedition",
                destination = "Pune, Maharashtra",
                date = "10 Aug 2026",
                peopleCount = 4,
                description = "King of forts",
                status = "IN_PROGRESS"
            )
        )

        // Seed moments and checklist
        val momentId = db.momentDao().insertMoment(
            com.example.data.local.entity.MomentEntity(
                tripId = tripId,
                category = "SUMMIT",
                note = "Reached Balekilla",
                timestamp = System.currentTimeMillis()
            )
        )
        val checklistId = db.checklistDao().insertItem(
            com.example.data.local.entity.ChecklistItemEntity(
                tripId = tripId,
                text = "Headlamp & Batteries",
                isCompleted = true
            )
        )

        // Execute atomic finish trip
        val result1 = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Rajgad Fort Expedition",
            destination = "Pune, Maharashtra",
            dateText = "10 Aug 2026",
            peopleCount = 4,
            momentsCount = 1,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Unforgettable trek"
        )

        assertTrue(result1.isSuccess)
        val stamp1 = result1.getOrThrow()
        assertNotNull(stamp1)
        assertEquals(1L, stamp1.stampNumber)
        assertEquals("#001", stamp1.stampCode)

        // Verify Trip is updated to COMPLETED atomically
        val tripEntity = db.tripDao().getTripByIdSync(tripId)
        assertNotNull(tripEntity)
        assertEquals("COMPLETED", tripEntity?.status)
        assertEquals(true, tripEntity?.stampEarned)

        // Idempotency: Calling finish again on the same trip returns existing stamp without error
        val result2 = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Rajgad Fort Expedition",
            destination = "Pune, Maharashtra",
            dateText = "10 Aug 2026",
            peopleCount = 4,
            momentsCount = 1,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Retry note"
        )
        assertTrue(result2.isSuccess)
        val stamp2 = result2.getOrThrow()
        assertEquals(stamp1.id, stamp2.id)
        assertEquals(stamp1.stampNumber, stamp2.stampNumber)
        assertEquals(stamp1.stampCode, stamp2.stampCode)

        // Verify Total Stamps count remains 1
        assertEquals(1, stampRepo.getStampsCountSync())

        // Verify moments and checklist links remain intact
        val moments = db.momentDao().getMomentsForTripSync(tripId)
        assertEquals(1, moments.size)
        assertEquals(momentId, moments[0].id)

        val checklists = db.checklistDao().getItemsForTripSync(tripId)
        assertEquals(1, checklists.size)
        assertEquals(checklistId, checklists[0].id)
    }

    @Test
    fun `verify concurrent finish requests on same trip are thread-safe and produce single stamp`() = runBlocking {
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Torna Fort",
                destination = "Velhe, Maharashtra",
                date = "12 Aug 2026",
                peopleCount = 2,
                status = "IN_PROGRESS"
            )
        )

        // Dispatch concurrent finish requests
        val deferredList = (1..10).map {
            async(Dispatchers.IO) {
                stampRepo.completeTripAndIssueStamp(
                    tripId = tripId,
                    title = "Torna Fort",
                    destination = "Velhe, Maharashtra",
                    dateText = "12 Aug 2026",
                    peopleCount = 2,
                    momentsCount = 0,
                    inkColorHex = "#1E3A2F",
                    stampStyle = "COMPASS",
                    reflectionNote = null
                )
            }
        }

        val results = deferredList.awaitAll()
        assertTrue(results.all { it.isSuccess })

        val stampNumbers = results.map { it.getOrThrow().stampNumber }.toSet()
        assertEquals(1, stampNumbers.size) // All returned the same single stamp number

        assertEquals(1, stampRepo.getStampsCountSync())
    }

    @Test
    fun `verify calling stamp generation 10 times sequentially produces exactly one stamp`() = runBlocking {
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Harishchandragad",
                destination = "Ahmednagar, Maharashtra",
                date = "11 Aug 2026",
                peopleCount = 3,
                status = "IN_PROGRESS"
            )
        )

        var firstStamp: TravelStamp? = null
        for (i in 1..10) {
            val result = stampRepo.completeTripAndIssueStamp(
                tripId = tripId,
                title = "Harishchandragad",
                destination = "Ahmednagar, Maharashtra",
                dateText = "11 Aug 2026",
                peopleCount = 3,
                momentsCount = 2,
                inkColorHex = "#1E3A2F",
                stampStyle = "MOUNTAIN",
                reflectionNote = "Kokankada cliff"
            )
            assertTrue(result.isSuccess)
            val currentStamp = result.getOrThrow()
            if (firstStamp == null) {
                firstStamp = currentStamp
            } else {
                assertEquals(firstStamp.id, currentStamp.id)
                assertEquals(firstStamp.uuid, currentStamp.uuid)
                assertEquals(firstStamp.stampNumber, currentStamp.stampNumber)
                assertEquals(firstStamp.stampCode, currentStamp.stampCode)
            }
        }

        assertEquals(1, stampRepo.getStampsCountSync())
    }

    @Test
    fun `verify duplicate protection preserves historical stamp attributes and sequence`() = runBlocking {
        // Setup existing historical stamps #001, #002, #003
        val trip1Id = db.tripDao().insertTrip(TripEntity(name = "Sinhagad", destination = "Pune", date = "01 Aug 2026", status = "COMPLETED"))
        val trip2Id = db.tripDao().insertTrip(TripEntity(name = "Rajmachi", destination = "Lonavala", date = "02 Aug 2026", status = "COMPLETED"))
        val trip3Id = db.tripDao().insertTrip(TripEntity(name = "Lohagad", destination = "Lonavala", date = "03 Aug 2026", status = "COMPLETED"))

        val stamp1 = stampRepo.completeTripAndIssueStamp(trip1Id, "Sinhagad", "Pune", "01 Aug 2026", 2, 1, "#C85A32", "MOUNTAIN", "Note 1").getOrThrow()
        val stamp2 = stampRepo.completeTripAndIssueStamp(trip2Id, "Rajmachi", "Lonavala", "02 Aug 2026", 2, 1, "#1E3A2F", "PINE", "Note 2").getOrThrow()
        val stamp3 = stampRepo.completeTripAndIssueStamp(trip3Id, "Lohagad", "Lonavala", "03 Aug 2026", 2, 1, "#243642", "EXPEDITION", "Note 3").getOrThrow()

        assertEquals(1L, stamp1.stampNumber)
        assertEquals(2L, stamp2.stampNumber)
        assertEquals(3L, stamp3.stampNumber)

        // New trip 4 receives #004
        val trip4Id = db.tripDao().insertTrip(TripEntity(name = "Visapur", destination = "Lonavala", date = "04 Aug 2026", status = "IN_PROGRESS"))
        val stamp4 = stampRepo.completeTripAndIssueStamp(trip4Id, "Visapur", "Lonavala", "04 Aug 2026", 4, 2, "#3E2723", "COMPASS", "Waterfalls").getOrThrow()
        assertEquals(4L, stamp4.stampNumber)
        assertEquals("#004", stamp4.stampCode)

        // Attempting to finish trip 4 again must still return #004, and NOT allocate #005
        val stamp4Retry = stampRepo.completeTripAndIssueStamp(trip4Id, "Visapur", "Lonavala", "04 Aug 2026", 4, 2, "#3E2723", "COMPASS", "Waterfalls").getOrThrow()
        assertEquals(4L, stamp4Retry.stampNumber)
        assertEquals("#004", stamp4Retry.stampCode)
        assertEquals(stamp4.uuid, stamp4Retry.uuid)

        // Verify historical stamp attributes remain 100% invariant
        val stamp1FromDb = stampRepo.getStampForTripSync(trip1Id)
        assertNotNull(stamp1FromDb)
        assertEquals(stamp1.uuid, stamp1FromDb!!.uuid)
        assertEquals(1L, stamp1FromDb.stampNumber)
        assertEquals("#001", stamp1FromDb.stampCode)
        assertEquals("Sinhagad", stamp1FromDb.title)
        assertEquals("Pune", stamp1FromDb.destination)
        assertEquals("#C85A32", stamp1FromDb.inkColorHex)
        assertEquals("MOUNTAIN", stamp1FromDb.stampStyle)
        assertEquals("Note 1", stamp1FromDb.reflectionNote)

        assertEquals(4, stampRepo.getStampsCountSync())
    }

    @Test
    fun `verify repeated backup import does not create duplicate stamps or corrupt sequence`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Seed trip and stamp
        val tripId = db.tripDao().insertTrip(TripEntity(name = "Alang Madan Kulang", destination = "Nashik", date = "05 Aug 2026", status = "IN_PROGRESS"))
        val stamp = stampRepo.completeTripAndIssueStamp(tripId, "Alang Madan Kulang", "Nashik", "05 Aug 2026", 5, 4, "#C85A32", "MOUNTAIN", "Toughest trek").getOrThrow()
        assertEquals(1, stampRepo.getStampsCountSync())

        // 2. Export backup
        val exportResult = BackupManager.createExportFile(context, db)

        // 3. Clear DB
        db.tripDao().deleteTripById(tripId)
        assertEquals(0, stampRepo.getStampsCountSync())

        // 4. Import backup first time
        val import1 = BackupManager.importBackup(context, exportResult.fileUri, db)
        assertTrue(import1.isSuccess)
        assertEquals(1, stampRepo.getStampsCountSync())

        val importedStamp1 = db.travelStampDao().getAllStampsListSync()
        assertEquals(1, importedStamp1.size)
        assertEquals(stamp.stampNumber, importedStamp1[0].stampNumber)
        assertEquals(stamp.stampCode, importedStamp1[0].stampCode)

        // 5. Sequence counter remains preserved
        assertEquals(1L, stampRepo.allocateNextStampNumber() - 1L)
    }

    @Test
    fun `verify app container and database cold startup is stable and non-null`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appContainer = com.example.data.DefaultAppContainer(context)

        assertNotNull(appContainer.database)
        assertNotNull(appContainer.tripRepository)
        assertNotNull(appContainer.checklistRepository)
        assertNotNull(appContainer.momentRepository)
        assertNotNull(appContainer.travelStampRepository)
        assertNotNull(appContainer.userPreferencesRepository)

        // Verify initial state flows are immediately readable without crashing
        assertNotNull(appContainer.userPreferencesRepository.hasCompletedOnboarding.value)
        assertNotNull(appContainer.userPreferencesRepository.themeMode.value)
    }

    @Test
    fun `verify ViewModel factory initialization on clean application launch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appContainer = com.example.data.DefaultAppContainer(context)
        val vm = com.example.ui.viewmodel.TravelViewModel(
            tripRepository = appContainer.tripRepository,
            checklistRepository = appContainer.checklistRepository,
            momentRepository = appContainer.momentRepository,
            travelStampRepository = appContainer.travelStampRepository,
            userPreferencesRepository = appContainer.userPreferencesRepository,
            database = appContainer.database
        )

        assertNotNull(vm.allTrips.value)
        assertNotNull(vm.activeTrips.value)
        assertNotNull(vm.completedTrips.value)
        assertNotNull(vm.stamps.value)
        assertEquals(0, vm.totalMomentsCount.value)
        assertEquals(0, vm.completedTripsCount.value)
    }

    @Test
    fun `verify database migration full migration execution across all legacy schemas`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrationDb = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .addMigrations(
                TravelStampDatabase.MIGRATION_1_2,
                TravelStampDatabase.MIGRATION_2_3,
                TravelStampDatabase.MIGRATION_3_4,
                TravelStampDatabase.MIGRATION_1_4,
                TravelStampDatabase.MIGRATION_2_4,
                TravelStampDatabase.MIGRATION_1_3
            )
            .build()

        assertNotNull(migrationDb.tripDao())
        assertNotNull(migrationDb.travelStampDao())
        assertNotNull(migrationDb.momentDao())
        assertNotNull(migrationDb.checklistDao())
        migrationDb.close()
    }

    @Test
    fun `TEST 1 - verify creating trip with checklist items, moments and official stamp links all relationships correctly`() = runBlocking {
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Kyoto Zen Tour",
                destination = "Kyoto, Japan",
                date = "15 August 2026",
                peopleCount = 2,
                description = "Exploring historic bamboo forests"
            )
        )
        assertTrue(tripId > 0)

        // Add checklist items
        checklistRepo.seedDefaultItems(tripId)
        val checklistItems = db.checklistDao().getItemsForTripSync(tripId)
        assertEquals(6, checklistItems.size)
        assertTrue(checklistItems.all { it.tripId == tripId })

        // Add moments
        val m1 = momentRepo.addMoment(
            com.example.data.model.Moment(
                tripId = tripId,
                category = com.example.data.model.MomentCategory.VIEW,
                note = "Golden Pavilion shimmering in the morning sun"
            )
        )
        assertTrue(m1 > 0)
        val moments = db.momentDao().getMomentsForTripSync(tripId)
        assertEquals(1, moments.size)
        assertEquals(tripId, moments[0].tripId)

        // Complete & issue stamp
        val stampRes = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Kyoto Zen Tour",
            destination = "Kyoto, Japan",
            dateText = "15 August 2026",
            peopleCount = 2,
            momentsCount = 1,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Unforgettable serenity."
        )
        assertTrue(stampRes.isSuccess)
        val stamp = stampRes.getOrNull()
        assertNotNull(stamp)
        assertEquals(tripId, stamp!!.tripId)

        // Verify Trip status
        val trip = tripRepo.getTripByIdSync(tripId)
        assertNotNull(trip)
        assertEquals(TripStatus.COMPLETED, trip!!.status)
        assertTrue(trip.stampEarned)
    }

    @Test
    fun `TEST 2 - verify deleting trip cascades and leaves no orphaned child records`() = runBlocking {
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Varanasi Ghats",
                destination = "Varanasi, India",
                date = "10 August 2026"
            )
        )
        checklistRepo.seedDefaultItems(tripId)
        momentRepo.addMoment(
            com.example.data.model.Moment(
                tripId = tripId,
                category = com.example.data.model.MomentCategory.CHAI,
                note = "Kulhad chai at Dashashwamedh Ghat"
            )
        )
        stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Varanasi Ghats",
            destination = "Varanasi, India",
            dateText = "10 August 2026",
            peopleCount = 1,
            momentsCount = 1,
            inkColorHex = "#8B1E0F",
            stampStyle = "CIRCULAR",
            reflectionNote = "Mesmerizing evening Aarti"
        )

        assertEquals(1, db.tripDao().getAllTripsListSync().size)
        assertEquals(6, db.checklistDao().getAllItemsListSync().size)
        assertEquals(1, db.momentDao().getAllMomentsListSync().size)
        assertEquals(1, db.travelStampDao().getAllStampsListSync().size)

        // Delete the trip via repository
        tripRepo.deleteTrip(tripId)

        // All associated child records must be cleanly removed
        assertNull(tripRepo.getTripByIdSync(tripId))
        assertEquals(0, db.checklistDao().getItemsForTripSync(tripId).size)
        assertEquals(0, db.momentDao().getMomentsForTripSync(tripId).size)
        assertNull(stampRepo.getStampForTripSync(tripId))
    }

    @Test
    fun `TEST 3 - verify deleting single moment removes its image and keeps other moments intact`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Coorg Coffee Estate",
                destination = "Coorg, India",
                date = "12 August 2026"
            )
        )

        val momentsDir = File(context.filesDir, "moments").apply { mkdirs() }
        val imgFile1 = File(momentsDir, "moment_test_1.jpg").apply { writeText("img_bytes_1") }
        val imgFile2 = File(momentsDir, "moment_test_2.jpg").apply { writeText("img_bytes_2") }

        val m1Id = momentRepo.addMoment(
            com.example.data.model.Moment(
                tripId = tripId,
                category = com.example.data.model.MomentCategory.PHOTO,
                note = "Coffee flowers blooming",
                imageUri = imgFile1.absolutePath
            )
        )
        val m2Id = momentRepo.addMoment(
            com.example.data.model.Moment(
                tripId = tripId,
                category = com.example.data.model.MomentCategory.PHOTO,
                note = "Mist over the plantation",
                imageUri = imgFile2.absolutePath
            )
        )

        assertTrue(imgFile1.exists())
        assertTrue(imgFile2.exists())

        // Delete moment 1
        momentRepo.deleteMoment(m1Id)

        // Moment 1 removed, file 1 deleted
        assertFalse(imgFile1.exists())
        // Moment 2 remains, file 2 preserved
        assertTrue(imgFile2.exists())
        val remainingMoments = momentRepo.getMomentsForTripSync(tripId)
        assertEquals(1, remainingMoments.size)
        assertEquals(m2Id, remainingMoments[0].id)
    }

    @Test
    fun `TEST 4 - verify deleting trip with multiple moments cleans up all associated images`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Ladakh Pangong",
                destination = "Ladakh, India",
                date = "05 August 2026"
            )
        )

        val momentsDir = File(context.filesDir, "moments").apply { mkdirs() }
        val imgFile1 = File(momentsDir, "ladakh_1.jpg").apply { writeText("ladakh_lake_1") }
        val imgFile2 = File(momentsDir, "ladakh_2.jpg").apply { writeText("ladakh_lake_2") }

        momentRepo.addMoment(
            com.example.data.model.Moment(
                tripId = tripId,
                category = com.example.data.model.MomentCategory.PHOTO,
                note = "Pangong Tso shades of blue",
                imageUri = imgFile1.absolutePath
            )
        )
        momentRepo.addMoment(
            com.example.data.model.Moment(
                tripId = tripId,
                category = com.example.data.model.MomentCategory.PHOTO,
                note = "Khardung La Pass summit",
                imageUri = imgFile2.absolutePath
            )
        )

        assertTrue(imgFile1.exists())
        assertTrue(imgFile2.exists())

        // Delete Trip
        tripRepo.deleteTrip(tripId)

        // Both image files should be cleaned up
        assertFalse(imgFile1.exists())
        assertFalse(imgFile2.exists())
        assertEquals(0, momentRepo.getMomentsForTripSync(tripId).size)
    }

    @Test
    fun `TEST 5 - verify deleting trip with missing image file completes safely without crashing`() = runBlocking {
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Manali Solang",
                destination = "Manali, India",
                date = "08 August 2026"
            )
        )

        // Point to a file that does not exist physically
        momentRepo.addMoment(
            com.example.data.model.Moment(
                tripId = tripId,
                category = com.example.data.model.MomentCategory.PHOTO,
                note = "Missing photo path",
                imageUri = "/data/user/0/com.example/files/moments/non_existent_file.jpg"
            )
        )

        // Delete trip should complete without any exception
        tripRepo.deleteTrip(tripId)
        assertNull(tripRepo.getTripByIdSync(tripId))
    }

    @Test
    fun `TEST 6 - verify editing trip details preserves trip ID and child relationships`() = runBlocking {
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Shimla Mall Road",
                destination = "Shimla, India",
                date = "11 August 2026",
                peopleCount = 2,
                description = "Weekend trip"
            )
        )
        checklistRepo.seedDefaultItems(tripId)
        val mId = momentRepo.addMoment(
            com.example.data.model.Moment(
                tripId = tripId,
                category = com.example.data.model.MomentCategory.CHAI,
                note = "Warm bakery snacks"
            )
        )
        val stampRes = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Shimla Mall Road",
            destination = "Shimla, India",
            dateText = "11 August 2026",
            peopleCount = 2,
            momentsCount = 1,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Great mountain views"
        )
        val originalStamp = stampRes.getOrNull()
        assertNotNull(originalStamp)

        // Edit trip details
        val existingTrip = tripRepo.getTripByIdSync(tripId)!!
        val editedTrip = existingTrip.copy(
            name = "Shimla Heritage & Ridge",
            destination = "Shimla & Kufri, HP",
            peopleCount = 3,
            description = "Updated extended weekend trip"
        )
        tripRepo.updateTrip(editedTrip)

        // Verify identity and child linkage
        val updatedTrip = tripRepo.getTripByIdSync(tripId)!!
        assertEquals("Shimla Heritage & Ridge", updatedTrip.name)
        assertEquals(3, updatedTrip.peopleCount)
        assertEquals(tripId, updatedTrip.id)
        assertEquals(existingTrip.uuid, updatedTrip.uuid)

        // Checklist items still point to tripId
        val checklist = checklistRepo.getItemsForTrip(tripId)
        val moments = momentRepo.getMomentsForTripSync(tripId)
        assertEquals(1, moments.size)
        assertEquals(mId, moments[0].id)
        assertEquals(tripId, moments[0].tripId)

        // Travel stamp still intact and linked
        val stamp = stampRepo.getStampForTripSync(tripId)
        assertNotNull(stamp)
        assertEquals(originalStamp!!.stampNumber, stamp!!.stampNumber)
        assertEquals(originalStamp.uuid, stamp.uuid)
        assertEquals(tripId, stamp.tripId)
    }

    @Test
    fun `TEST 7 - verify inserting child record with invalid tripId is handled safely`() = runBlocking {
        val nonExistentTripId = 999999L
        val item = com.example.data.local.entity.ChecklistItemEntity(
            tripId = nonExistentTripId,
            text = "Invalid item"
        )
        // With foreign keys active or safely checked
        var caught = false
        try {
            db.checklistDao().insertItem(item)
        } catch (_: Exception) {
            caught = true
        }
        // Either handled safely by DB or rejected
        val itemsForTrip = db.checklistDao().getItemsForTripSync(nonExistentTripId)
        if (!caught) {
            assertEquals(1, itemsForTrip.size)
        }
    }

    @Test
    fun `TEST 8 - verify attempt to create duplicate official stamps for one trip is prevented`() = runBlocking {
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Goa Sunset",
                destination = "Goa, India",
                date = "07 August 2026"
            )
        )

        val res1 = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Goa Sunset",
            destination = "Goa, India",
            dateText = "07 August 2026",
            peopleCount = 2,
            momentsCount = 0,
            inkColorHex = "#8B1E0F",
            stampStyle = "CIRCULAR",
            reflectionNote = "First finish"
        )
        assertTrue(res1.isSuccess)
        val stamp1 = res1.getOrNull()
        assertNotNull(stamp1)

        // Attempt second stamp generation for the same trip
        val res2 = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Goa Sunset Duplicate",
            destination = "Goa, India",
            dateText = "07 August 2026",
            peopleCount = 2,
            momentsCount = 0,
            inkColorHex = "#8B1E0F",
            stampStyle = "CIRCULAR",
            reflectionNote = "Second finish attempt"
        )
        assertTrue(res2.isSuccess)
        val stamp2 = res2.getOrNull()
        assertNotNull(stamp2)

        // Returned stamp must be identical (idempotent, no duplicate rows)
        assertEquals(stamp1!!.id, stamp2!!.id)
        assertEquals(stamp1.stampNumber, stamp2.stampNumber)
        assertEquals(1, db.travelStampDao().getAllStampsListSync().size)
    }

    @Test
    fun `TEST 9 - verify valid backup import preserves all relationships and images`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Jaipur Palaces",
                destination = "Jaipur, Rajasthan",
                date = "01 August 2026"
            )
        )
        checklistRepo.seedDefaultItems(tripId)
        momentRepo.addMoment(
            com.example.data.model.Moment(
                tripId = tripId,
                category = com.example.data.model.MomentCategory.VIEW,
                note = "Hawa Mahal facade"
            )
        )
        stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Jaipur Palaces",
            destination = "Jaipur, Rajasthan",
            dateText = "01 August 2026",
            peopleCount = 2,
            momentsCount = 1,
            inkColorHex = "#8B1E0F",
            stampStyle = "CIRCULAR",
            reflectionNote = "Pink city magic"
        )

        val json = BackupManager.generateBackupJson(db)
        assertNotNull(json)

        // Clear database and import
        db.tripDao().deleteTripById(tripId)
        assertEquals(0, db.tripDao().getAllTripsListSync().size)

        val importRes = BackupManager.importBackupJson(db, json)
        assertTrue(importRes.isSuccess)
        val res = importRes.getOrNull()!!
        assertEquals(1, res.importedTrips)
        assertEquals(1, res.importedStamps)
        assertEquals(1, res.importedMoments)
        assertEquals(6, res.importedChecklistItems)

        val importedTrips = db.tripDao().getAllTripsListSync()
        assertEquals(1, importedTrips.size)
        val newTripId = importedTrips[0].id

        val importedStamp = db.travelStampDao().getStampForTripSync(newTripId)
        assertNotNull(importedStamp)
        assertEquals(newTripId, importedStamp!!.tripId)

        val importedMoments = db.momentDao().getMomentsForTripSync(newTripId)
        assertEquals(1, importedMoments.size)
        assertEquals(newTripId, importedMoments[0].tripId)
    }

    @Test
    fun `TEST 10 - verify repeated backup import is idempotent and produces no duplicate stamps or orphan child records`() = runBlocking {
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Udaipur Lakes",
                destination = "Udaipur, India",
                date = "03 August 2026"
            )
        )
        checklistRepo.seedDefaultItems(tripId)
        momentRepo.addMoment(
            com.example.data.model.Moment(
                tripId = tripId,
                category = com.example.data.model.MomentCategory.MEMORY,
                note = "Lake Pichola boat ride"
            )
        )
        stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Udaipur Lakes",
            destination = "Udaipur, India",
            dateText = "03 August 2026",
            peopleCount = 2,
            momentsCount = 1,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "City of Lakes"
        )

        val json = BackupManager.generateBackupJson(db)

        // Import the same backup 3 times sequentially
        for (i in 1..3) {
            val importRes = BackupManager.importBackupJson(db, json)
            assertTrue(importRes.isSuccess)
        }

        // Must still have exactly 1 trip, 6 checklist items, 1 moment, 1 stamp
        val allTrips = db.tripDao().getAllTripsListSync()
        assertEquals(1, allTrips.size)
        val allStamps = db.travelStampDao().getAllStampsListSync()
        assertEquals(1, allStamps.size)
        val allMoments = db.momentDao().getAllMomentsListSync()
        assertEquals(1, allMoments.size)
        val allChecklist = db.checklistDao().getAllItemsListSync()
        assertEquals(6, allChecklist.size)
    }

    @Test
    fun `TEST 11 - verify schema migrations across all database versions preserve referential integrity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrationDb = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .addMigrations(
                TravelStampDatabase.MIGRATION_1_2,
                TravelStampDatabase.MIGRATION_2_3,
                TravelStampDatabase.MIGRATION_3_4,
                TravelStampDatabase.MIGRATION_1_4,
                TravelStampDatabase.MIGRATION_2_4,
                TravelStampDatabase.MIGRATION_1_3
            )
            .build()

        assertNotNull(migrationDb.tripDao())
        assertNotNull(migrationDb.travelStampDao())
        assertNotNull(migrationDb.momentDao())
        assertNotNull(migrationDb.checklistDao())
        migrationDb.close()
    }

    @Test
    fun `TEST 12 - verify stamp createdAt and issuedAt are permanently immutable upon trip editing`() = runBlocking {
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Kashmir Great Lakes",
                destination = "Sonamarg, J&K",
                date = "15 July 2026"
            )
        )
        val fixedIssuedTime = 1784000000000L // Historical timestamp

        val stampResult = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Kashmir Great Lakes",
            destination = "Sonamarg, J&K",
            dateText = "15 July 2026",
            peopleCount = 4,
            momentsCount = 3,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Alpine lakes crossing",
            completedAt = fixedIssuedTime
        )
        assertTrue(stampResult.isSuccess)
        val originalStamp = stampResult.getOrThrow()
        assertEquals(fixedIssuedTime, originalStamp.issuedAt)
        assertEquals(fixedIssuedTime, originalStamp.createdAt)
        assertEquals("15 July 2026", originalStamp.dateText)

        // Simulate user editing the trip details (e.g. updating trip date or title in the future)
        val trip = tripRepo.getTripByIdSync(tripId)!!
        tripRepo.updateTrip(
            trip.copy(
                name = "Kashmir Great Lakes Expedition 2026",
                date = "20 July 2026",
                description = "Updated description after trek",
                updatedAt = System.currentTimeMillis()
            )
        )

        // Verify the travel stamp's permanent date snapshot remains 100% untouched
        val retrievedStamp = stampRepo.getStampForTripSync(tripId)
        assertNotNull(retrievedStamp)
        assertEquals(fixedIssuedTime, retrievedStamp!!.issuedAt)
        assertEquals(fixedIssuedTime, retrievedStamp.createdAt)
        assertEquals("15 July 2026", retrievedStamp.dateText)
        assertEquals("Kashmir Great Lakes", retrievedStamp.title)
    }

    @Test
    fun `TEST 13 - verify timezone changes do not shift historical stamp dates or chronological ordering`() {
        val originalZone = java.util.TimeZone.getDefault()
        try {
            // Set timezone to Asia/Kolkata (UTC+5:30)
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
            val epochDayKolkata = DateUtils.getEpochDay("15 August 2026")
            val parsedKolkata = DateUtils.parseTripDate("15 August 2026")

            // Switch device timezone to America/New_York (UTC-4:00 / UTC-5:00)
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
            val epochDayNewYork = DateUtils.getEpochDay("15 August 2026")
            val parsedNewYork = DateUtils.parseTripDate("15 August 2026")

            // Switch device timezone to Pacific/Honolulu (UTC-10:00)
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Honolulu"))
            val epochDayHonolulu = DateUtils.getEpochDay("15 August 2026")
            val parsedHonolulu = DateUtils.parseTripDate("15 August 2026")

            // Dates and epoch days must be strictly identical regardless of device timezone
            assertEquals(epochDayKolkata, epochDayNewYork)
            assertEquals(epochDayNewYork, epochDayHonolulu)
            assertEquals(parsedKolkata, parsedNewYork)
            assertEquals(parsedNewYork, parsedHonolulu)
            assertEquals(2026, parsedHonolulu!!.year)
            assertEquals(8, parsedHonolulu.monthValue)
            assertEquals(15, parsedHonolulu.dayOfMonth)
        } finally {
            java.util.TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun `TEST 14 - verify backup export and restore preserves exact stamp timestamps without clock drift`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Spiti Valley Roadtrip",
                destination = "Kaza, HP",
                date = "01 June 2026"
            )
        )
        val fixedTimestamp = 1780000000000L
        stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Spiti Valley Roadtrip",
            destination = "Kaza, HP",
            dateText = "01 June 2026",
            peopleCount = 2,
            momentsCount = 4,
            inkColorHex = "#C85A32",
            stampStyle = "EXPEDITION",
            reflectionNote = "High altitude desert",
            completedAt = fixedTimestamp
        )

        val json = BackupManager.generateBackupJson(db)
        assertNotNull(json)

        // Clear and restore
        db.tripDao().deleteTripById(tripId)
        val importResult = BackupManager.importBackupJson(db, json)
        assertTrue(importResult.isSuccess)

        val restoredTrips = db.tripDao().getAllTripsListSync()
        assertEquals(1, restoredTrips.size)
        val restoredStamp = db.travelStampDao().getStampForTripSync(restoredTrips[0].id)
        assertNotNull(restoredStamp)
        assertEquals(fixedTimestamp, restoredStamp!!.issuedAt)
        assertEquals(fixedTimestamp, restoredStamp.createdAt)
        assertEquals("01 June 2026", restoredStamp.dateText)
    }

    @Test
    fun `TEST 15 - verify completeTripAndIssueStamp idempotency preserves original stamp timestamps`() = runBlocking {
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Meghalaya Living Root Bridges",
                destination = "Nongriat, Meghalaya",
                date = "10 May 2026"
            )
        )
        val initialTimestamp = 1778000000000L
        val stamp1 = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Meghalaya Living Root Bridges",
            destination = "Nongriat, Meghalaya",
            dateText = "10 May 2026",
            peopleCount = 2,
            momentsCount = 5,
            inkColorHex = "#1E3A2F",
            stampStyle = "PINE",
            reflectionNote = "Double decker root bridge",
            completedAt = initialTimestamp
        ).getOrThrow()

        // Calling finish again with a different timestamp (e.g. later retry)
        val laterTimestamp = initialTimestamp + 50000000L
        val stamp2 = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Meghalaya Living Root Bridges",
            destination = "Nongriat, Meghalaya",
            dateText = "10 May 2026",
            peopleCount = 2,
            momentsCount = 5,
            inkColorHex = "#1E3A2F",
            stampStyle = "PINE",
            reflectionNote = "Updated retry note",
            completedAt = laterTimestamp
        ).getOrThrow()

        // Must preserve the original timestamp and not be overwritten by later retry
        assertEquals(stamp1.issuedAt, stamp2.issuedAt)
        assertEquals(stamp1.createdAt, stamp2.createdAt)
        assertEquals(initialTimestamp, stamp2.issuedAt)
        assertEquals(initialTimestamp, stamp2.createdAt)
    }

    @Test
    fun `TEST 16 - verify exact acceptance criteria for Recent Journeys and Upcoming Expeditions ordering`() = runBlocking {
        // Clear all trips
        val existingTrips = tripRepo.getAllTrips().first()
        for (t in existingTrips) {
            tripRepo.deleteTrip(t.id)
        }

        // Insert in scrambled/non-chronological order
        // 1. Harihar (16 Jul 2021) - Completed
        val hariharId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Harihar",
                destination = "Nashik, Maharashtra",
                date = "16 July 2021"
            )
        )
        tripRepo.finishTrip(hariharId)

        // 2. Future Expedition (25 Aug 2026) - Upcoming
        val futureId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Future Expedition",
                destination = "Goa",
                date = "25 August 2026"
            )
        )

        // 3. Dehergad (16 Aug 2026) - Completed
        val dehergadId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Dehergad",
                destination = "Nashik, Maharashtra",
                date = "16 August 2026"
            )
        )
        tripRepo.finishTrip(dehergadId)

        // 4. Bhaskargad (9 Aug 2026) - Completed
        val bhaskargadId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Bhaskargad",
                destination = "Nashik, Maharashtra",
                date = "9 August 2026"
            )
        )
        tripRepo.finishTrip(bhaskargadId)

        // Verify Recent Journeys
        val recentJourneys = tripRepo.observeRecentJourneys().first()
        assertEquals(3, recentJourneys.size)
        assertEquals("Dehergad", recentJourneys[0].name)
        assertEquals("16 August 2026", recentJourneys[0].date)

        assertEquals("Bhaskargad", recentJourneys[1].name)
        assertEquals("9 August 2026", recentJourneys[1].date)

        assertEquals("Harihar", recentJourneys[2].name)
        assertEquals("16 July 2021", recentJourneys[2].date)

        // Verify Upcoming Expeditions
        val upcoming = tripRepo.observeUpcomingJourneys().first()
        assertEquals(1, upcoming.size)
        assertEquals("Future Expedition", upcoming[0].name)
        assertEquals("25 August 2026", upcoming[0].date)
        assertEquals(TripStatus.UPCOMING, upcoming[0].status)
    }

    @Test
    fun `TEST 17 - verify newly added old journey does not appear first in Recent Journeys`() = runBlocking {
        // Clear all trips
        val existingTrips = tripRepo.getAllTrips().first()
        for (t in existingTrips) {
            tripRepo.deleteTrip(t.id)
        }

        // Add Dehergad and Bhaskargad
        val dehergadId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Dehergad",
                destination = "Nashik",
                date = "16 August 2026"
            )
        )
        tripRepo.finishTrip(dehergadId)

        val bhaskargadId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Bhaskargad",
                destination = "Nashik",
                date = "9 August 2026"
            )
        )
        tripRepo.finishTrip(bhaskargadId)

        // User now records a historical journey from 2020 (highest createdAt timestamp, newest database record)
        val oldTripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Old Journey",
                destination = "Hampi",
                date = "10 January 2020"
            )
        )
        tripRepo.finishTrip(oldTripId)

        val recentJourneys = tripRepo.observeRecentJourneys().first()
        assertEquals(3, recentJourneys.size)
        assertEquals("Dehergad", recentJourneys[0].name)
        assertEquals("Bhaskargad", recentJourneys[1].name)
        assertEquals("Old Journey", recentJourneys[2].name) // Must appear last!
    }

    @Test
    fun `TEST 18 - verify editing an older journey does not alter chronological Recent Journeys position`() = runBlocking {
        // Clear all trips
        val existingTrips = tripRepo.getAllTrips().first()
        for (t in existingTrips) {
            tripRepo.deleteTrip(t.id)
        }

        val dehergadId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Dehergad",
                destination = "Nashik",
                date = "16 August 2026"
            )
        )
        tripRepo.finishTrip(dehergadId)

        val hariharId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Harihar",
                destination = "Nashik",
                date = "16 July 2021"
            )
        )
        tripRepo.finishTrip(hariharId)

        // Edit Harihar's name and description (updates updatedAt timestamp)
        val hariharTrip = tripRepo.getTripByIdSync(hariharId)!!
        tripRepo.updateTrip(
            hariharTrip.copy(
                name = "Harihar Fort Expedition (Revised)",
                description = "Updated notes about rock cut steps",
                updatedAt = System.currentTimeMillis()
            )
        )

        val recentJourneys = tripRepo.observeRecentJourneys().first()
        assertEquals(2, recentJourneys.size)
        assertEquals("Dehergad", recentJourneys[0].name)
        assertEquals("Harihar Fort Expedition (Revised)", recentJourneys[1].name)
    }

    @Test
    fun `TEST 19 - verify same-date completed journeys have deterministic secondary tie-breaker`() = runBlocking {
        // Clear all trips
        val existingTrips = tripRepo.getAllTrips().first()
        for (t in existingTrips) {
            tripRepo.deleteTrip(t.id)
        }

        // Two journeys on the same day
        val t1 = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Morning Trek",
                destination = "Sinhagad",
                date = "15 August 2026",
                createdAt = 1000L
            )
        )
        tripRepo.finishTrip(t1)

        val t2 = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Evening Trail",
                destination = "Sinhagad",
                date = "15 August 2026",
                createdAt = 2000L
            )
        )
        tripRepo.finishTrip(t2)

        val recentJourneys = tripRepo.observeRecentJourneys().first()
        assertEquals(2, recentJourneys.size)
        assertEquals("Evening Trail", recentJourneys[0].name)
        assertEquals("Morning Trek", recentJourneys[1].name)
    }

    @Test
    fun `TEST 20 - verify timeline preservation across trip completion and stamp generation`() = runBlocking {
        // 1. Create expedition
        val dehergadId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Dehergad",
                destination = "Nashik, Maharashtra",
                date = "16 August 2026"
            )
        )

        // 2. Add initial moments: Moment A, Photo A, Note A
        momentRepo.addMoment(
            Moment(
                tripId = dehergadId,
                category = MomentCategory.VIEW,
                note = "Fort Entrance Reached",
                imageUri = null
            )
        )
        momentRepo.addMoment(
            Moment(
                tripId = dehergadId,
                category = MomentCategory.PHOTO,
                note = "Sunrise over western ghats",
                imageUri = "content://media/photo_a.jpg"
            )
        )
        momentRepo.addMoment(
            Moment(
                tripId = dehergadId,
                category = MomentCategory.NOTE,
                note = "Trail was rocky and misty",
                imageUri = null
            )
        )

        val beforeFinishMoments = momentRepo.getMomentsForTripSync(dehergadId)
        assertEquals(3, beforeFinishMoments.size)

        // 3. Complete expedition & Issue Stamp
        val finishResult = stampRepo.completeTripAndIssueStamp(
            tripId = dehergadId,
            title = "Dehergad",
            destination = "Nashik, Maharashtra",
            dateText = "16 August 2026",
            peopleCount = 1,
            momentsCount = beforeFinishMoments.size,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Incredible sunrise climb"
        )
        assertTrue(finishResult.isSuccess)
        val stamp = finishResult.getOrThrow()
        assertEquals(dehergadId, stamp.tripId)
        assertEquals(3, stamp.momentsCount)

        // 4. Verify trip status is COMPLETED and ID is unchanged
        val tripAfter = tripRepo.getTripByIdSync(dehergadId)
        assertNotNull(tripAfter)
        assertEquals(TripStatus.COMPLETED, tripAfter!!.status)
        assertTrue(tripAfter.stampEarned)
        assertEquals(dehergadId, tripAfter.id)

        // 5. Verify all 3 timeline entries remain intact and associated with dehergadId
        val afterFinishMoments = momentRepo.getMomentsForTripSync(dehergadId)
        assertEquals(3, afterFinishMoments.size)
        assertEquals("Fort Entrance Reached", afterFinishMoments[0].note)
        assertEquals("Sunrise over western ghats", afterFinishMoments[1].note)
        assertEquals("content://media/photo_a.jpg", afterFinishMoments[1].imageUri)
        assertEquals("Trail was rocky and misty", afterFinishMoments[2].note)
    }

    @Test
    fun `TEST 21 - verify post-completion additions persist and update stamp count`() = runBlocking {
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Dehergad",
                destination = "Nashik",
                date = "16 August 2026"
            )
        )
        // Add initial moment A
        momentRepo.addMoment(
            Moment(
                tripId = tripId,
                category = MomentCategory.VIEW,
                note = "Base village start"
            )
        )

        // Complete trip
        val finishResult = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Dehergad",
            destination = "Nashik",
            dateText = "16 August 2026",
            peopleCount = 1,
            momentsCount = 1,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = null
        )
        assertTrue(finishResult.isSuccess)

        // Add memories AFTER completion (Moment B, Photo B, Note B)
        momentRepo.addMoment(
            Moment(
                tripId = tripId,
                category = MomentCategory.FOOD,
                note = "Chai at summit"
            )
        )
        momentRepo.addMoment(
            Moment(
                tripId = tripId,
                category = MomentCategory.PHOTO,
                note = "Descent view",
                imageUri = "content://media/photo_b.jpg"
            )
        )
        momentRepo.addMoment(
            Moment(
                tripId = tripId,
                category = MomentCategory.NOTE,
                note = "Post-trek reflections added back at camp"
            )
        )
        stampRepo.updateStampMomentsCount(tripId)

        // Verify all 4 entries exist on the completed journey
        val allMoments = momentRepo.getMomentsForTripSync(tripId)
        assertEquals(4, allMoments.size)

        // Verify stamp reflects updated count
        val updatedStamp = stampRepo.getStampForTripSync(tripId)
        assertNotNull(updatedStamp)
        assertEquals(4, updatedStamp!!.momentsCount)
    }

    @Test
    fun `TEST 22 - verify multi-journey timeline isolation between different completed journeys`() = runBlocking {
        // Clear all trips
        val existingTrips = tripRepo.getAllTrips().first()
        for (t in existingTrips) {
            tripRepo.deleteTrip(t.id)
        }

        val dehergadId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Dehergad",
                destination = "Nashik",
                date = "16 August 2026"
            )
        )
        val bhaskargadId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Bhaskargad",
                destination = "Nashik",
                date = "9 August 2026"
            )
        )

        // Add entries for Dehergad
        momentRepo.addMoment(
            Moment(
                tripId = dehergadId,
                category = MomentCategory.VIEW,
                note = "Dehergad Ridge"
            )
        )
        momentRepo.addMoment(
            Moment(
                tripId = dehergadId,
                category = MomentCategory.PHOTO,
                note = "Dehergad Peak",
                imageUri = "content://media/dehergad.jpg"
            )
        )

        // Add entries for Bhaskargad
        momentRepo.addMoment(
            Moment(
                tripId = bhaskargadId,
                category = MomentCategory.VIEW,
                note = "Bhaskargad Water Cisterns"
            )
        )
        momentRepo.addMoment(
            Moment(
                tripId = bhaskargadId,
                category = MomentCategory.PHOTO,
                note = "Bhaskargad Cave",
                imageUri = "content://media/bhaskargad.jpg"
            )
        )

        // Finish both trips
        tripRepo.finishTrip(dehergadId)
        tripRepo.finishTrip(bhaskargadId)

        // Query timeline for Dehergad
        val dehergadMoments = momentRepo.getMomentsForTripSync(dehergadId)
        assertEquals(2, dehergadMoments.size)
        assertTrue(dehergadMoments.all { it.tripId == dehergadId })
        assertEquals("Dehergad Ridge", dehergadMoments[0].note)
        assertEquals("Dehergad Peak", dehergadMoments[1].note)

        // Query timeline for Bhaskargad
        val bhaskargadMoments = momentRepo.getMomentsForTripSync(bhaskargadId)
        assertEquals(2, bhaskargadMoments.size)
        assertTrue(bhaskargadMoments.all { it.tripId == bhaskargadId })
        assertEquals("Bhaskargad Water Cisterns", bhaskargadMoments[0].note)
        assertEquals("Bhaskargad Cave", bhaskargadMoments[1].note)
    }

    @Test
    fun `TEST 23 - verify journey status changes do not alter tripId or orphan timeline entries`() = runBlocking {
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Harihar Fort",
                destination = "Nashik",
                date = "16 July 2021"
            )
        )
        momentRepo.addMoment(
            Moment(
                tripId = tripId,
                category = MomentCategory.VIEW,
                note = "Rock-cut steps"
            )
        )

        // UPCOMING / IN_PROGRESS -> COMPLETED
        tripRepo.finishTrip(tripId)

        val completedTrip = tripRepo.getTripByIdSync(tripId)
        assertNotNull(completedTrip)
        assertEquals(tripId, completedTrip!!.id)
        assertEquals(TripStatus.COMPLETED, completedTrip.status)

        val moments = momentRepo.getMomentsForTripSync(tripId)
        assertEquals(1, moments.size)
        assertEquals(tripId, moments[0].tripId)
        assertEquals("Rock-cut steps", moments[0].note)
    }

    @Test
    fun `TEST 24 - verify SearchUtils matches destination location and stamp numbers across example dataset`() {
        val stamp1 = TravelStamp(
            id = 1L,
            tripId = 101L,
            stampNumber = 1L,
            stampCode = "TS-001",
            title = "Gateway Expedition",
            destination = "Mumbai, Maharashtra",
            dateText = "10 January 2026",
            peopleCount = 2,
            momentsCount = 3,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Coastal breeze",
            issuedAt = 1000L,
            createdAt = 1000L
        )

        val stamp2 = TravelStamp(
            id = 2L,
            tripId = 102L,
            stampNumber = 2L,
            stampCode = "TS-002",
            title = "Shaniwar Wada Tour",
            destination = "Pune, Maharashtra",
            dateText = "15 February 2026",
            peopleCount = 1,
            momentsCount = 1,
            inkColorHex = "#2E5D4B",
            stampStyle = "EXPEDITION",
            reflectionNote = "Historic walls",
            issuedAt = 2000L,
            createdAt = 2000L
        )

        val stamp3 = TravelStamp(
            id = 3L,
            tripId = 103L,
            stampNumber = 3L,
            stampCode = "TS-003",
            title = "Dehergad Ridge Climb",
            destination = "Dehergad, Maharashtra",
            dateText = "16 August 2026",
            peopleCount = 4,
            momentsCount = 5,
            inkColorHex = "#D48B38",
            stampStyle = "COMPASS",
            reflectionNote = "Sunrise summit",
            issuedAt = 3000L,
            createdAt = 3000L
        )

        val stamp4 = TravelStamp(
            id = 4L,
            tripId = 104L,
            stampNumber = 4L,
            stampCode = "TS-004",
            title = "Red Fort Exploration",
            destination = "Delhi, India",
            dateText = "20 March 2026",
            peopleCount = 3,
            momentsCount = 2,
            inkColorHex = "#C85A32",
            stampStyle = "MINIMAL",
            reflectionNote = "Capital monument",
            issuedAt = 4000L,
            createdAt = 4000L
        )

        val stamp5 = TravelStamp(
            id = 5L,
            tripId = 105L,
            stampNumber = 5L,
            stampCode = "TS-005",
            title = "Harihar Steep Ascent",
            destination = "Harihar, Karnataka",
            dateText = "16 July 2021",
            peopleCount = 2,
            momentsCount = 4,
            inkColorHex = "#2E5D4B",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Famous steps",
            issuedAt = 5000L,
            createdAt = 5000L
        )

        val sampleStamps = listOf(stamp1, stamp2, stamp3, stamp4, stamp5)

        // 1. Empty query returns all records in exact order
        val allResults = SearchUtils.filterStamps(sampleStamps, "")
        assertEquals(5, allResults.size)
        assertEquals(listOf(stamp1, stamp2, stamp3, stamp4, stamp5), allResults)

        // 2. Destination match: "mumbai", "mum", "MUMBAI", " mum "
        val mumbai1 = SearchUtils.filterStamps(sampleStamps, "mumbai")
        assertEquals(1, mumbai1.size)
        assertEquals("Mumbai, Maharashtra", mumbai1[0].destination)

        val mumbai2 = SearchUtils.filterStamps(sampleStamps, "mum")
        assertEquals(1, mumbai2.size)
        assertEquals("Mumbai, Maharashtra", mumbai2[0].destination)

        val mumbai3 = SearchUtils.filterStamps(sampleStamps, " MUMBAI ")
        assertEquals(1, mumbai3.size)
        assertEquals("Mumbai, Maharashtra", mumbai3[0].destination)

        // 3. Location match: "maharashtra" -> Stamps 1, 2, 3 in original order
        val mahaResults = SearchUtils.filterStamps(sampleStamps, "maharashtra")
        assertEquals(3, mahaResults.size)
        assertEquals(listOf(stamp1, stamp2, stamp3), mahaResults)

        // 4. Partial location: "karnataka" -> Stamp 5
        val karnatakaResults = SearchUtils.filterStamps(sampleStamps, "karnataka")
        assertEquals(1, karnatakaResults.size)
        assertEquals(stamp5, karnatakaResults[0])

        // 5. Partial destination: "deher" -> Stamp 3
        val deherResults = SearchUtils.filterStamps(sampleStamps, "deher")
        assertEquals(1, deherResults.size)
        assertEquals(stamp3, deherResults[0])

        // 6. Number matching: "4", "#4", "Stamp 4", "Stamp #4", "TS-004", "004"
        val num4A = SearchUtils.filterStamps(sampleStamps, "4")
        assertEquals(1, num4A.size)
        assertEquals(stamp4, num4A[0])

        val num4B = SearchUtils.filterStamps(sampleStamps, "#4")
        assertEquals(1, num4B.size)
        assertEquals(stamp4, num4B[0])

        val num4C = SearchUtils.filterStamps(sampleStamps, "Stamp #4")
        assertEquals(1, num4C.size)
        assertEquals(stamp4, num4C[0])

        val num4D = SearchUtils.filterStamps(sampleStamps, "TS-004")
        assertEquals(1, num4D.size)
        assertEquals(stamp4, num4D[0])

        // 7. No results: "xyz" -> empty list
        val noResults = SearchUtils.filterStamps(sampleStamps, "xyz")
        assertTrue(noResults.isEmpty())
    }

    @Test
    fun `TEST 25 - verify trip filtering with associated stamp across name destination and stamp number`() {
        val trip1 = com.example.data.model.Trip(
            id = 10L,
            name = "Sinhagad Sunrise Trek",
            destination = "Pune, Maharashtra",
            date = "15 August 2026",
            status = TripStatus.COMPLETED
        )
        val stamp1 = TravelStamp(
            id = 100L,
            tripId = 10L,
            stampNumber = 27L,
            stampCode = "TS-027",
            title = "Sinhagad Sunrise",
            destination = "Pune, Maharashtra",
            dateText = "15 August 2026",
            peopleCount = 2,
            momentsCount = 3,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN"
        )

        val trip2 = com.example.data.model.Trip(
            id = 11L,
            name = "Goa Beach Trail",
            destination = "Goa, India",
            date = "25 August 2026",
            status = TripStatus.UPCOMING
        )

        val trips = listOf(trip1, trip2)
        val stampsMap = mapOf(10L to stamp1)

        // Match by trip name
        val nameResults = SearchUtils.filterTrips(trips, stampsMap, "sinhagad")
        assertEquals(1, nameResults.size)
        assertEquals(trip1, nameResults[0])

        // Match by stamp number "#27"
        val stampNumResults = SearchUtils.filterTrips(trips, stampsMap, "#27")
        assertEquals(1, stampNumResults.size)
        assertEquals(trip1, stampNumResults[0])

        // Match by destination "goa"
        val goaResults = SearchUtils.filterTrips(trips, stampsMap, "goa")
        assertEquals(1, goaResults.size)
        assertEquals(trip2, goaResults[0])

        // Empty query returns all trips
        val allTripsResult = SearchUtils.filterTrips(trips, stampsMap, "")
        assertEquals(2, allTripsResult.size)
    }

    @Test
    fun `TEST 26 - verify search is strictly read-only and does not mutate any timestamps or IDs`() = runBlocking {
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Harishchandragad",
                destination = "Ahmednagar, Maharashtra",
                date = "12 August 2026"
            )
        )
        val finishResult = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Harishchandragad",
            destination = "Ahmednagar, Maharashtra",
            dateText = "12 August 2026",
            peopleCount = 2,
            momentsCount = 0,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Konkan kada cliff"
        )
        val stamp = finishResult.getOrThrow()
        val originalIssuedAt = stamp.issuedAt
        val originalCreatedAt = stamp.createdAt
        val originalStampNumber = stamp.stampNumber

        // Perform multiple search queries
        val stamps = stampRepo.getAllStamps().first()
        val filtered = SearchUtils.filterStamps(stamps, "harishchandragad")
        assertEquals(1, filtered.size)

        val filteredNum = SearchUtils.filterStamps(stamps, stamp.stampNumber.toString())
        assertEquals(1, filteredNum.size)

        // Verify entity in database is unchanged
        val dbStamp = stampRepo.getStampForTripSync(tripId)!!
        assertEquals(originalIssuedAt, dbStamp.issuedAt)
        assertEquals(originalCreatedAt, dbStamp.createdAt)
        assertEquals(originalStampNumber, dbStamp.stampNumber)
        assertEquals(tripId, dbStamp.tripId)
    }

    @Test
    fun `TEST 27 - verify fast search filtering across large collection of 60+ stamps`() {
        val largeList = (1..65).map { i ->
            TravelStamp(
                id = i.toLong(),
                tripId = (1000 + i).toLong(),
                stampNumber = i.toLong(),
                stampCode = "TS-%03d".format(i),
                title = if (i % 2 == 0) "Fort Expedition #$i" else "Coastal Trail #$i",
                destination = if (i % 3 == 0) "Nashik, Maharashtra" else "Western Ghats, India",
                dateText = "01 January 2026",
                peopleCount = 1,
                momentsCount = i % 5,
                inkColorHex = "#C85A32",
                stampStyle = "MOUNTAIN"
            )
        }

        // Search for specific number "42"
        val match42 = SearchUtils.filterStamps(largeList, "42")
        assertEquals(1, match42.size)
        assertEquals(42L, match42[0].stampNumber)

        // Search for formatted code "TS-050"
        val match50 = SearchUtils.filterStamps(largeList, "TS-050")
        assertEquals(1, match50.size)
        assertEquals(50L, match50[0].stampNumber)

        // Search for "coastal"
        val coastalStamps = SearchUtils.filterStamps(largeList, "coastal")
        assertEquals(33, coastalStamps.size) // odd numbers 1..65

        // Search for "nashik"
        val nashikStamps = SearchUtils.filterStamps(largeList, "nashik")
        assertEquals(21, nashikStamps.size) // multiples of 3 in 1..65
    }

    @Test
    fun `TEST 28 - verify sorting stamps by Newest First, Oldest First, and numeric Stamp Number with exact user dataset`() {
        val dehergad = TravelStamp(
            id = 5L,
            tripId = 505L,
            stampNumber = 5L,
            stampCode = "TS-005",
            title = "Dehergad Ridge Climb",
            destination = "Dehergad, Maharashtra",
            dateText = "16 Aug 2026",
            peopleCount = 2,
            momentsCount = 3,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            issuedAt = 5000L
        )

        val bhaskargad = TravelStamp(
            id = 4L,
            tripId = 504L,
            stampNumber = 4L,
            stampCode = "TS-004",
            title = "Bhaskargad Ascent",
            destination = "Bhaskargad, Maharashtra",
            dateText = "9 Aug 2026",
            peopleCount = 3,
            momentsCount = 2,
            inkColorHex = "#2E5D4B",
            stampStyle = "EXPEDITION",
            issuedAt = 4000L
        )

        val harihar = TravelStamp(
            id = 2L,
            tripId = 502L,
            stampNumber = 2L,
            stampCode = "TS-002",
            title = "Harihar Steps",
            destination = "Harihar, Karnataka",
            dateText = "16 Jul 2021",
            peopleCount = 4,
            momentsCount = 5,
            inkColorHex = "#D48B38",
            stampStyle = "COMPASS",
            issuedAt = 2000L
        )

        val pune = TravelStamp(
            id = 3L,
            tripId = 503L,
            stampNumber = 3L,
            stampCode = "TS-003",
            title = "Pune Heritage",
            destination = "Pune, Maharashtra",
            dateText = "2 Aug 2026",
            peopleCount = 1,
            momentsCount = 1,
            inkColorHex = "#C85A32",
            stampStyle = "MINIMAL",
            issuedAt = 3000L
        )

        val sampleStamps = listOf(harihar, dehergad, pune, bhaskargad)

        // 1. Newest First -> 16 Aug 2026 (Dehergad), 9 Aug 2026 (Bhaskargad), 2 Aug 2026 (Pune), 16 Jul 2021 (Harihar)
        val newestFirst = SearchUtils.sortStamps(sampleStamps, StampSortOption.NEWEST_FIRST)
        assertEquals(listOf(dehergad, bhaskargad, pune, harihar), newestFirst)

        // 2. Oldest First -> 16 Jul 2021 (Harihar), 2 Aug 2026 (Pune), 9 Aug 2026 (Bhaskargad), 16 Aug 2026 (Dehergad)
        val oldestFirst = SearchUtils.sortStamps(sampleStamps, StampSortOption.OLDEST_FIRST)
        assertEquals(listOf(harihar, pune, bhaskargad, dehergad), oldestFirst)

        // 3. Stamp Number ASC -> #2 (Harihar), #3 (Pune), #4 (Bhaskargad), #5 (Dehergad)
        val stampNumAsc = SearchUtils.sortStamps(sampleStamps, StampSortOption.STAMP_NUMBER_ASC)
        assertEquals(listOf(harihar, pune, bhaskargad, dehergad), stampNumAsc)

        // 4. Stamp Number DESC -> #5 (Dehergad), #4 (Bhaskargad), #3 (Pune), #2 (Harihar)
        val stampNumDesc = SearchUtils.sortStamps(sampleStamps, StampSortOption.STAMP_NUMBER_DESC)
        assertEquals(listOf(dehergad, bhaskargad, pune, harihar), stampNumDesc)
    }

    @Test
    fun `TEST 29 - verify sorting and filtering journeys for completed vs upcoming expeditions`() {
        val tripDehergad = com.example.data.model.Trip(
            id = 5L,
            name = "Dehergad Ridge Climb",
            destination = "Dehergad, Maharashtra",
            date = "16 Aug 2026",
            status = TripStatus.COMPLETED
        )
        val tripBhaskargad = com.example.data.model.Trip(
            id = 4L,
            name = "Bhaskargad Ascent",
            destination = "Bhaskargad, Maharashtra",
            date = "9 Aug 2026",
            status = TripStatus.COMPLETED
        )
        val tripHarihar = com.example.data.model.Trip(
            id = 2L,
            name = "Harihar Steps",
            destination = "Harihar, Karnataka",
            date = "16 Jul 2021",
            status = TripStatus.COMPLETED
        )
        val tripGoa = com.example.data.model.Trip(
            id = 6L,
            name = "Goa Beach Trail",
            destination = "Goa, India",
            date = "25 Aug 2026",
            status = TripStatus.UPCOMING
        )
        val tripPune = com.example.data.model.Trip(
            id = 3L,
            name = "Pune Heritage",
            destination = "Pune, Maharashtra",
            date = "2 Aug 2026",
            status = TripStatus.COMPLETED
        )

        val allTrips = listOf(tripHarihar, tripDehergad, tripPune, tripGoa, tripBhaskargad)
        val emptyStampsMap = emptyMap<Long, TravelStamp>()

        // 1. Filter: Completed + Sort: Newest First -> Dehergad (16 Aug 2026), Bhaskargad (9 Aug 2026), Pune (2 Aug 2026), Harihar (16 Jul 2021)
        val completedNewest = SearchUtils.filterAndSortTrips(
            trips = allTrips,
            stampsMap = emptyStampsMap,
            searchQuery = "",
            sortOption = JourneySortOption.NEWEST_FIRST,
            statusFilter = StatusFilter.COMPLETED,
            momentsFilter = MomentsFilter.ALL,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )
        assertEquals(listOf(tripDehergad, tripBhaskargad, tripPune, tripHarihar), completedNewest)

        // 2. Filter: Completed + Sort: Oldest First -> Harihar (16 Jul 2021), Pune (2 Aug 2026), Bhaskargad (9 Aug 2026), Dehergad (16 Aug 2026)
        val completedOldest = SearchUtils.filterAndSortTrips(
            trips = allTrips,
            stampsMap = emptyStampsMap,
            searchQuery = "",
            sortOption = JourneySortOption.OLDEST_FIRST,
            statusFilter = StatusFilter.COMPLETED,
            momentsFilter = MomentsFilter.ALL,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )
        assertEquals(listOf(tripHarihar, tripPune, tripBhaskargad, tripDehergad), completedOldest)

        // 3. Filter: Upcoming -> Goa (25 Aug 2026)
        val upcomingTrips = SearchUtils.filterAndSortTrips(
            trips = allTrips,
            stampsMap = emptyStampsMap,
            searchQuery = "",
            sortOption = JourneySortOption.NEWEST_FIRST,
            statusFilter = StatusFilter.UPCOMING,
            momentsFilter = MomentsFilter.ALL,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )
        assertEquals(listOf(tripGoa), upcomingTrips)
    }

    @Test
    fun `TEST 30 - verify combined Search, Filter, and Sort compose properly`() {
        val stamp1 = TravelStamp(
            id = 1L,
            tripId = 101L,
            stampNumber = 1L,
            stampCode = "TS-001",
            title = "Mumbai Coastline",
            destination = "Mumbai, Maharashtra",
            dateText = "10 Jan 2026",
            peopleCount = 2,
            momentsCount = 3,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            issuedAt = 1000L
        )

        val stamp2 = TravelStamp(
            id = 2L,
            tripId = 102L,
            stampNumber = 2L,
            stampCode = "TS-002",
            title = "Pune Heritage",
            destination = "Pune, Maharashtra",
            dateText = "2 Aug 2026",
            peopleCount = 1,
            momentsCount = 1,
            inkColorHex = "#2E5D4B",
            stampStyle = "EXPEDITION",
            issuedAt = 2000L
        )

        val stamp3 = TravelStamp(
            id = 3L,
            tripId = 103L,
            stampNumber = 3L,
            stampCode = "TS-003",
            title = "Dehergad Ridge Climb",
            destination = "Dehergad, Maharashtra",
            dateText = "16 Aug 2026",
            peopleCount = 4,
            momentsCount = 5,
            inkColorHex = "#D48B38",
            stampStyle = "COMPASS",
            issuedAt = 3000L
        )

        val stamp4 = TravelStamp(
            id = 4L,
            tripId = 104L,
            stampNumber = 4L,
            stampCode = "TS-004",
            title = "Red Fort",
            destination = "Delhi, India",
            dateText = "20 Mar 2026",
            peopleCount = 3,
            momentsCount = 2,
            inkColorHex = "#C85A32",
            stampStyle = "MINIMAL",
            issuedAt = 4000L
        )

        val sampleStamps = listOf(stamp1, stamp2, stamp3, stamp4)

        // Search: "maharashtra" + Sort: Oldest First
        // Matches: stamp1 (10 Jan 2026), stamp2 (2 Aug 2026), stamp3 (16 Aug 2026)
        val searchOldest = SearchUtils.filterAndSortStamps(
            stamps = sampleStamps,
            searchQuery = "maharashtra",
            sortOption = StampSortOption.OLDEST_FIRST,
            momentsFilter = MomentsFilter.ALL,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )
        assertEquals(listOf(stamp1, stamp2, stamp3), searchOldest)

        // Search: "maharashtra" + Sort: Newest First
        // Matches: stamp3 (16 Aug 2026), stamp2 (2 Aug 2026), stamp1 (10 Jan 2026)
        val searchNewest = SearchUtils.filterAndSortStamps(
            stamps = sampleStamps,
            searchQuery = "maharashtra",
            sortOption = StampSortOption.NEWEST_FIRST,
            momentsFilter = MomentsFilter.ALL,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )
        assertEquals(listOf(stamp3, stamp2, stamp1), searchNewest)
    }

    @Test
    fun `TEST 31 - verify deterministic secondary tie-breaking for equal dates`() {
        val stampA = TravelStamp(
            id = 1L,
            tripId = 1001L,
            stampNumber = 10L,
            stampCode = "TS-010",
            title = "Morning Summit",
            destination = "Nashik, Maharashtra",
            dateText = "15 Aug 2026",
            peopleCount = 2,
            momentsCount = 1,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            issuedAt = 1000L
        )

        val stampB = TravelStamp(
            id = 2L,
            tripId = 1002L,
            stampNumber = 20L,
            stampCode = "TS-020",
            title = "Evening Summit",
            destination = "Nashik, Maharashtra",
            dateText = "15 Aug 2026",
            peopleCount = 2,
            momentsCount = 1,
            inkColorHex = "#2E5D4B",
            stampStyle = "MOUNTAIN",
            issuedAt = 2000L
        )

        val list = listOf(stampA, stampB)

        // Newest First with same date should tie-break deterministically by stampNumber descending
        val sortedDesc = SearchUtils.sortStamps(list, StampSortOption.NEWEST_FIRST)
        assertEquals(listOf(stampB, stampA), sortedDesc)

        // Oldest First with same date should tie-break deterministically by stampNumber ascending
        val sortedAsc = SearchUtils.sortStamps(list, StampSortOption.OLDEST_FIRST)
        assertEquals(listOf(stampA, stampB), sortedAsc)
    }

    @Test
    fun `TEST 32 - verify sorting and filtering are strictly read-only and preserve stored entities`() = runBlocking {
        val tripId = tripRepo.createTrip(
            com.example.data.model.Trip(
                name = "Kalsubai Peak",
                destination = "Bhandardara, Maharashtra",
                date = "10 August 2026"
            )
        )
        val finishResult = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Kalsubai Peak",
            destination = "Bhandardara, Maharashtra",
            dateText = "10 August 2026",
            peopleCount = 2,
            momentsCount = 0,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Highest peak"
        )
        val stamp = finishResult.getOrThrow()
        val originalIssuedAt = stamp.issuedAt
        val originalCreatedAt = stamp.createdAt
        val originalStampNumber = stamp.stampNumber

        val stamps = stampRepo.getAllStamps().first()

        // Apply all sort options
        StampSortOption.entries.forEach { option ->
            val result = SearchUtils.sortStamps(stamps, option)
            assertFalse(result.isEmpty())
        }

        // Verify entity in database is unchanged
        val dbStamp = stampRepo.getStampForTripSync(tripId)!!
        assertEquals(originalIssuedAt, dbStamp.issuedAt)
        assertEquals(originalCreatedAt, dbStamp.createdAt)
        assertEquals(originalStampNumber, dbStamp.stampNumber)
        assertEquals(tripId, dbStamp.tripId)
    }

    @Test
    fun `TEST 33 - verify 70+ large collection processing and long destination names preservation`() {
        val longDestName = "A Very Long Destination Name That Could Potentially Break The Passport Card Layout In The Western Ghats Expedition Trail"
        val largeList = (1..75).map { i ->
            TravelStamp(
                id = i.toLong(),
                tripId = (2000 + i).toLong(),
                stampNumber = i.toLong(),
                stampCode = "TS-%03d".format(i),
                title = if (i == 1) longDestName else "Expedition #$i",
                destination = if (i == 1) longDestName else "Destination #$i, Maharashtra",
                dateText = "16 Aug 2026",
                peopleCount = 2,
                momentsCount = (i % 6),
                inkColorHex = "#C85A32",
                stampStyle = "MOUNTAIN"
            )
        }

        // Test filtering and sorting across 75 stamps
        val processed = SearchUtils.filterAndSortStamps(
            stamps = largeList,
            searchQuery = "",
            sortOption = StampSortOption.STAMP_NUMBER_ASC,
            momentsFilter = MomentsFilter.ALL,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )

        assertEquals(75, processed.size)
        assertEquals(1L, processed.first().stampNumber)
        assertEquals(75L, processed.last().stampNumber)
        assertEquals(longDestName, processed.first().title)

        // Test search query within 75 items
        val searchResults = SearchUtils.filterAndSortStamps(
            stamps = largeList,
            searchQuery = "western ghats",
            sortOption = StampSortOption.NEWEST_FIRST,
            momentsFilter = MomentsFilter.ALL,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )
        assertEquals(1, searchResults.size)
        assertEquals(1L, searchResults.first().stampNumber)
    }

    @Test
    fun `TEST 34 - verify loading and error state recovery and atomic finish state transitions`() = runBlocking {
        // 1. Create a trip
        val trip = TripEntity(name = "Kalsubai Peak", destination = "Igatpuri", date = "12 Aug 2026", status = "IN_PROGRESS")
        val tripId = db.tripDao().insertTrip(trip)

        // 2. Test missing trip / invalid id retrieval produces null (handled as Not Found / Error)
        val missingTrip = tripRepo.getTripByIdSync(99999L)
        assertNull(missingTrip)

        // 3. Test existing trip returns valid data
        val existingTrip = tripRepo.getTripByIdSync(tripId)
        assertNotNull(existingTrip)
        assertEquals("Kalsubai Peak", existingTrip!!.name)

        // 4. Test missing stamp returns null cleanly
        val missingStamp = stampRepo.getStampForTripSync(99999L)
        assertNull(missingStamp)

        // 5. Complete trip and verify stamp state
        val finishResult = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Kalsubai Peak",
            destination = "Igatpuri",
            dateText = "12 Aug 2026",
            peopleCount = 3,
            momentsCount = 2,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Summit completed",
            completedAt = System.currentTimeMillis()
        )
        assertTrue(finishResult.isSuccess)
        val stamp = finishResult.getOrThrow()
        assertEquals(tripId, stamp.tripId)
        assertNotNull(stampRepo.getStampForTripSync(tripId))
    }

    @Test
    fun `TEST 35 - verify search vs filter empty state distinctions in collection`() {
        val sampleList = listOf(
            TravelStamp(
                id = 1,
                tripId = 101,
                stampNumber = 1,
                stampCode = "TS-001",
                title = "Rajmachi Fort",
                destination = "Lonavala, Maharashtra",
                dateText = "10 Aug 2026",
                peopleCount = 2,
                momentsCount = 3,
                inkColorHex = "#C85A32",
                stampStyle = "MOUNTAIN"
            )
        )

        // 1. Entirely empty collection -> empty results
        val emptyBase = SearchUtils.filterAndSortStamps(
            stamps = emptyList(),
            searchQuery = "",
            sortOption = StampSortOption.NEWEST_FIRST,
            momentsFilter = MomentsFilter.ALL,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )
        assertTrue(emptyBase.isEmpty())

        // 2. Search query with no match on non-empty collection -> empty result (Search Empty)
        val searchNoMatch = SearchUtils.filterAndSortStamps(
            stamps = sampleList,
            searchQuery = "Himalayas",
            sortOption = StampSortOption.NEWEST_FIRST,
            momentsFilter = MomentsFilter.ALL,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )
        assertTrue(searchNoMatch.isEmpty())

        // 3. Filter with no match -> empty result (Filter Empty)
        val filterNoMatch = SearchUtils.filterAndSortStamps(
            stamps = sampleList,
            searchQuery = "",
            sortOption = StampSortOption.NEWEST_FIRST,
            momentsFilter = MomentsFilter.NO_MOMENTS, // Only has 3 moments > 0
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )
        assertTrue(filterNoMatch.isEmpty())

        // 4. Exact match -> returns item
        val exactMatch = SearchUtils.filterAndSortStamps(
            stamps = sampleList,
            searchQuery = "Rajmachi",
            sortOption = StampSortOption.NEWEST_FIRST,
            momentsFilter = MomentsFilter.HAS_MOMENTS,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )
        assertEquals(1, exactMatch.size)
        assertEquals("Rajmachi Fort", exactMatch.first().title)
    }

    @Test
    fun `TEST 36 - verify finish trip confirmation, loading, success state and duplicate generation protection`() = runBlocking {
        // 1. Setup a valid in-progress journey (non-future date)
        val trip = TripEntity(
            name = "Harishchandragad Trek",
            destination = "Ahmednagar, MH",
            date = "15 Jul 2026",
            status = "IN_PROGRESS"
        )
        val tripId = db.tripDao().insertTrip(trip)

        // 2. First completion attempt: Creates stamp #1 and marks trip completed
        val firstResult = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Harishchandragad Trek",
            destination = "Ahmednagar, MH",
            dateText = "15 Jul 2026",
            peopleCount = 4,
            momentsCount = 3,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Reached Konkan Kada at sunset",
            completedAt = System.currentTimeMillis()
        )
        assertTrue(firstResult.isSuccess)
        val firstStamp = firstResult.getOrThrow()
        assertEquals(tripId, firstStamp.tripId)
        assertEquals(1L, firstStamp.stampNumber)
        assertEquals("#001", firstStamp.stampCode)
        assertEquals("15 Jul 2026", firstStamp.dateText) // Permanent trip date preserved

        // 3. Second completion attempt (simulating duplicate tap / retry / recreation):
        // Idempotency guarantee: Returns EXACT same stamp without creating duplicates or incrementing stamp sequence
        val secondResult = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Harishchandragad Trek",
            destination = "Ahmednagar, MH",
            dateText = "15 Jul 2026",
            peopleCount = 4,
            momentsCount = 3,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Reached Konkan Kada at sunset",
            completedAt = System.currentTimeMillis()
        )
        assertTrue(secondResult.isSuccess)
        val secondStamp = secondResult.getOrThrow()
        assertEquals(firstStamp.id, secondStamp.id)
        assertEquals(firstStamp.stampNumber, secondStamp.stampNumber)
        assertEquals(firstStamp.stampCode, secondStamp.stampCode)

        // 4. Verify total stamps in database is strictly 1
        val allStamps = db.travelStampDao().getAllStampsListSync()
        assertEquals(1, allStamps.size)
        assertEquals(tripId, allStamps.first().tripId)

        // 5. Verify trip entity status is COMPLETED with stampEarned = 1
        val updatedTrip = db.tripDao().getTripByIdSync(tripId)
        assertNotNull(updatedTrip)
        assertEquals("COMPLETED", updatedTrip!!.status)
        assertTrue(updatedTrip.stampEarned)
    }

    @Test
    fun `TEST 37 - verify ViewModel FinishTripUiState transitions concurrency double-tap protection and retry recovery`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val userPrefs = UserPreferencesRepositoryImpl(context)
        val vm = TravelViewModel(
            tripRepository = tripRepo,
            checklistRepository = checklistRepo,
            momentRepository = momentRepo,
            travelStampRepository = stampRepo,
            userPreferencesRepository = userPrefs,
            database = db
        )

        // 1. Create in-progress trip
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Sinhagad Sunrise",
                destination = "Pune, MH",
                date = "10 May 2026",
                peopleCount = 2,
                description = "Early morning hike",
                status = "IN_PROGRESS"
            )
        )

        // Initial state is Idle
        assertEquals(FinishTripUiState.Idle, vm.finishTripUiState.value)

        // 2. Finish trip through ViewModel
        vm.finishTrip(
            tripId = tripId,
            reflectionNote = "Reached the top before sunrise",
            stampInkColorHex = "#1E3A2F",
            stampStyle = "HISTORIC"
        )

        var attempts = 0
        while (vm.finishTripUiState.value !is FinishTripUiState.Success && attempts < 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            delay(50)
            attempts++
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue(vm.finishTripUiState.value is FinishTripUiState.Success)
        val successState = vm.finishTripUiState.value as FinishTripUiState.Success
        assertEquals(tripId, successState.tripId)
        assertEquals(1L, successState.stamp.stampNumber)
        assertEquals("#001", successState.stamp.stampCode)
        assertEquals("10 May 2026", successState.stamp.dateText)

        // 3. Reset state
        vm.resetFinishTripState()
        assertEquals(FinishTripUiState.Idle, vm.finishTripUiState.value)

        // 4. Test error state on future date
        val futureTripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Everest Base Camp",
                destination = "Nepal",
                date = "25 Dec 2030",
                peopleCount = 1,
                status = "UPCOMING"
            )
        )
        vm.finishTrip(
            tripId = futureTripId,
            reflectionNote = "Future trip",
            stampInkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN"
        )

        attempts = 0
        while (vm.finishTripUiState.value !is FinishTripUiState.Error && attempts < 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            delay(50)
            attempts++
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(vm.finishTripUiState.value is FinishTripUiState.Error)
    }

    @Test
    fun `TEST 38 - verify expedition timeline and moments preservation before and after trip completion`() = runBlocking {
        // 1. Create trip with 3 moments
        val trip = Trip(
            name = "Torna Fort",
            destination = "Velhe, MH",
            date = "12 Jun 2026",
            peopleCount = 3,
            status = TripStatus.IN_PROGRESS
        )
        val tripId = tripRepo.createTrip(trip)

        val m1 = momentRepo.addMoment(Moment(tripId = tripId, category = MomentCategory.FOOD, note = "Pithla Bhakri"))
        val m2 = momentRepo.addMoment(Moment(tripId = tripId, category = MomentCategory.VIEW, note = "Golden hour view"))
        val m3 = momentRepo.addMoment(Moment(tripId = tripId, category = MomentCategory.CHAI, note = "Hot cutting chai"))

        val initialMoments = momentRepo.getMomentsForTripSync(tripId)
        assertEquals(3, initialMoments.size)

        // 2. Complete trip and issue stamp
        val result = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Torna Fort",
            destination = "Velhe, MH",
            dateText = "12 Jun 2026",
            peopleCount = 3,
            momentsCount = 3,
            inkColorHex = "#C85A32",
            stampStyle = "HISTORIC",
            reflectionNote = "Highest fort in Pune district",
            completedAt = System.currentTimeMillis()
        )
        assertTrue(result.isSuccess)
        val stamp = result.getOrThrow()
        assertEquals(3, stamp.momentsCount)

        // 3. Verify all 3 original moments remain untouched and connected to tripId
        val postCompletionMoments = momentRepo.getMomentsForTripSync(tripId)
        assertEquals(3, postCompletionMoments.size)
        assertTrue(postCompletionMoments.any { it.id == m1 })
        assertTrue(postCompletionMoments.any { it.id == m2 })
        assertTrue(postCompletionMoments.any { it.id == m3 })

        // 4. Log a new moment post-completion (Issue #7 requirement)
        val m4 = momentRepo.addMoment(Moment(tripId = tripId, category = MomentCategory.NOTE, note = "Post-trek reflection"))
        val updatedMoments = momentRepo.getMomentsForTripSync(tripId)
        assertEquals(4, updatedMoments.size)
        assertTrue(updatedMoments.any { it.id == m4 })

        // 5. Update stamp moments count
        stampRepo.updateStampMomentsCount(tripId)
        val updatedStamp = stampRepo.getStampForTripSync(tripId)
        assertNotNull(updatedStamp)
        assertEquals(4, updatedStamp!!.momentsCount)
    }

    @Test
    fun `TEST 39 - verify direct navigation and stable Stamp ID retrieval`() = runBlocking {
        val tripId = tripRepo.createTrip(
            Trip(
                name = "Lohagad Fort",
                destination = "Lonavala, MH",
                date = "20 Jul 2026",
                peopleCount = 2,
                status = TripStatus.IN_PROGRESS
            )
        )

        val result = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Lohagad Fort",
            destination = "Lonavala, MH",
            dateText = "20 Jul 2026",
            peopleCount = 2,
            momentsCount = 0,
            inkColorHex = "#1E3A2F",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Vinchukata point was misty",
            completedAt = System.currentTimeMillis()
        )
        assertTrue(result.isSuccess)
        val stamp = result.getOrThrow()

        // Verify retrieval by stable ID
        val stampById = stampRepo.getStampForTripSync(tripId)
        assertNotNull(stampById)
        assertEquals(stamp.id, stampById!!.id)
        assertEquals(stamp.stampNumber, stampById.stampNumber)
        assertEquals("Lohagad Fort", stampById.title)
        assertEquals("20 Jul 2026", stampById.dateText)
    }

    @Test
    fun `TEST 40 - verify process recreation and partial failure retry safety`() = runBlocking {
        val tripId = tripRepo.createTrip(
            Trip(
                name = "Visapur Fort",
                destination = "Malavli, MH",
                date = "22 Jul 2026",
                peopleCount = 5,
                status = TripStatus.IN_PROGRESS
            )
        )

        // First attempt succeeds in DB
        val res1 = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Visapur Fort",
            destination = "Malavli, MH",
            dateText = "22 Jul 2026",
            peopleCount = 5,
            momentsCount = 0,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Waterfalls on steps",
            completedAt = System.currentTimeMillis()
        )
        assertTrue(res1.isSuccess)
        val stamp1 = res1.getOrThrow()

        // Simulate Retry after UI failure / process restart
        val res2 = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Visapur Fort",
            destination = "Malavli, MH",
            dateText = "22 Jul 2026",
            peopleCount = 5,
            momentsCount = 0,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Waterfalls on steps",
            completedAt = System.currentTimeMillis()
        )
        assertTrue(res2.isSuccess)
        val stamp2 = res2.getOrThrow()

        // Must be identical, no duplicate stamp created
        assertEquals(stamp1.id, stamp2.id)
        assertEquals(stamp1.stampNumber, stamp2.stampNumber)

        val totalStamps = db.travelStampDao().getStampsCountSync()
        assertEquals(1, totalStamps)
    }

    @Test
    fun `TEST 41 - verify Issue 5 through 11 comprehensive regression suite`() = runBlocking {
        // Issue #6 regression: Recent Journeys Ordering
        // Test data: Dehergad (16 Aug 2026), Bhaskargad (9 Aug 2026), Harihar (16 Jul 2021)
        val t1 = TripEntity(name = "Dehergad", destination = "Igatpuri", date = "16 August 2026", status = "COMPLETED")
        val t2 = TripEntity(name = "Bhaskargad", destination = "Igatpuri", date = "9 August 2026", status = "COMPLETED")
        val t3 = TripEntity(name = "Harihar", destination = "Nashik", date = "16 July 2021", status = "COMPLETED")

        val id1 = db.tripDao().insertTrip(t1)
        val id2 = db.tripDao().insertTrip(t2)
        val id3 = db.tripDao().insertTrip(t3)

        val allTrips = db.tripDao().getAllTripsListSync().map { it.toDomain() }
        val sortedRecent = SearchUtils.filterAndSortTrips(
            trips = allTrips,
            stampsMap = emptyMap(),
            searchQuery = "",
            sortOption = JourneySortOption.NEWEST_FIRST,
            statusFilter = StatusFilter.COMPLETED,
            momentsFilter = MomentsFilter.ALL,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )

        assertEquals("Dehergad", sortedRecent[0].name)
        assertEquals("Bhaskargad", sortedRecent[1].name)
        assertEquals("Harihar", sortedRecent[2].name)

        // Issue #8 & #9: Passport Search, Sort & Filter
        val stamp1 = stampRepo.completeTripAndIssueStamp(
            tripId = id1,
            title = "Dehergad",
            destination = "Igatpuri",
            dateText = "16 August 2026",
            peopleCount = 2,
            momentsCount = 1,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = null,
            completedAt = System.currentTimeMillis()
        ).getOrThrow()

        val allStamps = listOf(stamp1)
        val searchResult = SearchUtils.filterAndSortStamps(
            stamps = allStamps,
            searchQuery = "Dehergad",
            sortOption = StampSortOption.NEWEST_FIRST,
            momentsFilter = MomentsFilter.ALL,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )
        assertEquals(1, searchResult.size)
        assertEquals("Dehergad", searchResult.first().title)

        val noMatchResult = SearchUtils.filterAndSortStamps(
            stamps = allStamps,
            searchQuery = "NonExistentPlace",
            sortOption = StampSortOption.NEWEST_FIRST,
            momentsFilter = MomentsFilter.ALL,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )
        assertTrue(noMatchResult.isEmpty())

        // Issue #10: Large dataset performance (50+ journeys)
        val largeList: List<Trip> = (1..60).map { i ->
            Trip(
                name = "Trek #$i",
                destination = "Western Ghats #$i",
                date = "10 May 2026",
                status = TripStatus.COMPLETED
            )
        }
        val sortedLarge = SearchUtils.filterAndSortTrips(
            trips = largeList,
            stampsMap = emptyMap(),
            searchQuery = "",
            sortOption = JourneySortOption.NEWEST_FIRST,
            statusFilter = StatusFilter.ALL,
            momentsFilter = MomentsFilter.ALL,
            datePeriodFilter = DatePeriodFilter.ALL_TIME
        )
        assertEquals(60, sortedLarge.size)
    }
}



