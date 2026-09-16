package com.example.data.local

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TravelStampDatabase
import com.example.data.local.entity.TripEntity
import com.example.data.local.entity.JourneyLocationEntity
import com.example.data.local.entity.TravelStampEntity
import com.example.data.local.entity.ChecklistItemEntity
import com.example.data.model.JourneyLocation
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.util.BackupManager
import com.example.data.util.JourneyLocationValidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JourneyLocationTest {

    private lateinit var db: TravelStampDatabase
    private lateinit var stampRepo: TravelStampRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        stampRepo = TravelStampRepositoryImpl(db.travelStampDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // 1. Trip can have one location
    @Test
    fun testTripCanHaveOneLocation() {
        runBlocking {
            val tripId = db.tripDao().insertTrip(
                TripEntity(name = "Harihar Fort", destination = "Nashik", date = "10 Aug 2026", status = "IN_PROGRESS")
            )
            val location = JourneyLocationEntity(
                uuid = "loc-1",
                tripId = tripId,
                label = "Base Camp",
                latitude = 19.9012,
                longitude = 73.4567,
                sortOrder = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            db.journeyLocationDao().insertLocation(location)

            val list = db.journeyLocationDao().getLocationsForTripSync(tripId)
            assertEquals(1, list.size)
            assertEquals("Base Camp", list[0].label)
            assertEquals(19.9012, list[0].latitude, 0.0001)
        }
    }

    // 2. Trip can have multiple locations
    @Test
    fun testTripCanHaveMultipleLocations() {
        runBlocking {
            val tripId = db.tripDao().insertTrip(
                TripEntity(name = "Rajgad Trek", destination = "Pune", date = "12 Aug 2026", status = "IN_PROGRESS")
            )

            val loc1 = JourneyLocationEntity(
                uuid = "loc-rajgad-1",
                tripId = tripId,
                label = "Gunjavane Base",
                latitude = 18.2567,
                longitude = 73.6543,
                sortOrder = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val loc2 = JourneyLocationEntity(
                uuid = "loc-rajgad-2",
                tripId = tripId,
                label = "Padmavati Machi",
                latitude = 18.2612,
                longitude = 73.6610,
                sortOrder = 1,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val loc3 = JourneyLocationEntity(
                uuid = "loc-rajgad-3",
                tripId = tripId,
                label = "Balekilla Summit",
                latitude = 18.2654,
                longitude = 73.6655,
                sortOrder = 2,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            db.journeyLocationDao().insertLocation(loc1)
            db.journeyLocationDao().insertLocation(loc2)
            db.journeyLocationDao().insertLocation(loc3)

            val list = db.journeyLocationDao().getLocationsForTripSync(tripId)
            assertEquals(3, list.size)
            assertEquals("Gunjavane Base", list[0].label)
            assertEquals("Padmavati Machi", list[1].label)
            assertEquals("Balekilla Summit", list[2].label)
        }
    }

    // 3. deterministic sort ordering
    @Test
    fun testDeterministicSortOrdering() {
        runBlocking {
            val tripId = db.tripDao().insertTrip(
                TripEntity(name = "Sinhagad Hike", destination = "Pune", date = "15 Aug 2026", status = "IN_PROGRESS")
            )

            val locMiddle = JourneyLocationEntity(
                uuid = "loc-mid",
                tripId = tripId,
                label = "Middle Point",
                latitude = 18.3654,
                longitude = 73.7543,
                sortOrder = 5,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val locStart = JourneyLocationEntity(
                uuid = "loc-start",
                tripId = tripId,
                label = "Start Point",
                latitude = 18.3611,
                longitude = 73.7501,
                sortOrder = 1,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val locEnd = JourneyLocationEntity(
                uuid = "loc-end",
                tripId = tripId,
                label = "End Point",
                latitude = 18.3701,
                longitude = 73.7601,
                sortOrder = 10,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Insert in random order
            db.journeyLocationDao().insertLocation(locMiddle)
            db.journeyLocationDao().insertLocation(locEnd)
            db.journeyLocationDao().insertLocation(locStart)

            val list = db.journeyLocationDao().getLocationsForTripSync(tripId)
            assertEquals(3, list.size)
            // Assert sorting is strictly ASC based on sortOrder
            assertEquals("Start Point", list[0].label)
            assertEquals(1, list[0].sortOrder)
            assertEquals("Middle Point", list[1].label)
            assertEquals(5, list[1].sortOrder)
            assertEquals("End Point", list[2].label)
            assertEquals(10, list[2].sortOrder)
        }
    }

    // 4. duplicate coordinates are allowed
    @Test
    fun testDuplicateCoordinatesAreAllowed() {
        runBlocking {
            val tripId = db.tripDao().insertTrip(
                TripEntity(name = "Korigad Trek", destination = "Lonavala", date = "18 Aug 2026", status = "IN_PROGRESS")
            )

            // Two points at identical coordinates but different labels/uuids
            val loc1 = JourneyLocationEntity(
                uuid = "loc-p1",
                tripId = tripId,
                label = "Point A",
                latitude = 18.7333,
                longitude = 73.3833,
                sortOrder = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val loc2 = JourneyLocationEntity(
                uuid = "loc-p2",
                tripId = tripId,
                label = "Point B",
                latitude = 18.7333,
                longitude = 73.3833,
                sortOrder = 1,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            db.journeyLocationDao().insertLocation(loc1)
            db.journeyLocationDao().insertLocation(loc2)

            val list = db.journeyLocationDao().getLocationsForTripSync(tripId)
            assertEquals(2, list.size)
            assertEquals(list[0].latitude, list[1].latitude, 0.0001)
            assertEquals(list[0].longitude, list[1].longitude, 0.0001)
            assertNotEquals(list[0].uuid, list[1].uuid)
        }
    }

    // 5. invalid latitude rejected
    @Test
    fun testInvalidLatitudeRejected() {
        assertFalse(JourneyLocationValidator.isValid("Pune Base", 90.1, 73.0))
        assertFalse(JourneyLocationValidator.isValid("Pune Base", -91.0, 73.0))
        assertFalse(JourneyLocationValidator.isValid("Pune Base", 120.0, 73.0))
        assertTrue(JourneyLocationValidator.isValid("Pune Base", 90.0, 73.0))
        assertTrue(JourneyLocationValidator.isValid("Pune Base", -90.0, 73.0))
    }

    // 6. invalid longitude rejected
    @Test
    fun testInvalidLongitudeRejected() {
        assertFalse(JourneyLocationValidator.isValid("Pune Base", 18.0, 180.1))
        assertFalse(JourneyLocationValidator.isValid("Pune Base", 18.0, -181.0))
        assertFalse(JourneyLocationValidator.isValid("Pune Base", 18.0, 240.0))
        assertTrue(JourneyLocationValidator.isValid("Pune Base", 18.0, 180.0))
        assertTrue(JourneyLocationValidator.isValid("Pune Base", 18.0, -180.0))
    }

    // 7. NaN rejected
    @Test
    fun testNaNRejected() {
        assertFalse(JourneyLocationValidator.isValid("Pune Base", Double.NaN, 73.0))
        assertFalse(JourneyLocationValidator.isValid("Pune Base", 18.0, Double.NaN))
        assertFalse(JourneyLocationValidator.isValid("Pune Base", Double.NaN, Double.NaN))
    }

    // 8. Infinity rejected
    @Test
    fun testInfinityRejected() {
        assertFalse(JourneyLocationValidator.isValid("Pune Base", Double.POSITIVE_INFINITY, 73.0))
        assertFalse(JourneyLocationValidator.isValid("Pune Base", Double.NEGATIVE_INFINITY, 73.0))
        assertFalse(JourneyLocationValidator.isValid("Pune Base", 18.0, Double.POSITIVE_INFINITY))
        assertFalse(JourneyLocationValidator.isValid("Pune Base", 18.0, Double.NEGATIVE_INFINITY))
    }

    // 9. blank label rejected
    @Test
    fun testBlankLabelRejected() {
        assertFalse(JourneyLocationValidator.isValid("", 18.0, 73.0))
        assertFalse(JourneyLocationValidator.isValid("   ", 18.0, 73.0))
    }

    // 10. deleting a hard-deleted Trip cascades its locations
    @Test
    fun testDeletingHardDeletedTripCascadesLocations() {
        runBlocking {
            val tripId = db.tripDao().insertTrip(
                TripEntity(name = "Visapur", destination = "Lonavala", date = "20 Aug 2026", status = "IN_PROGRESS")
            )
            val location = JourneyLocationEntity(
                uuid = "loc-cascade",
                tripId = tripId,
                label = "Waterfall Route",
                latitude = 18.7222,
                longitude = 73.4001,
                sortOrder = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            db.journeyLocationDao().insertLocation(location)

            // Verify inserted
            assertEquals(1, db.journeyLocationDao().getLocationsForTripSync(tripId).size)

            // Hard delete trip
            db.tripDao().deleteTripById(tripId)

            // Verify locations cascaded to empty
            assertEquals(0, db.journeyLocationDao().getLocationsForTripSync(tripId).size)
        }
    }

    // 11. Room migration 7 → 8 preserves existing data
    @Test
    fun testRoomMigration7To8PreservesExistingData() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbFile = File(context.cacheDir, "test_v7_migration.db")
            if (dbFile.exists()) dbFile.delete()

            // Create a raw SQLite DB at version 7 (it has trips, checklist_items, moments, travel_stamps tables)
            val sqliteDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            sqliteDb.version = 7

            sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `destination` TEXT NOT NULL, `date` TEXT NOT NULL, `startTimeMinutes` INTEGER, `peopleCount` INTEGER NOT NULL, `description` TEXT NOT NULL, `status` TEXT NOT NULL, `stampEarned` INTEGER NOT NULL, `reminderEnabled` INTEGER NOT NULL, `reminderPreset` TEXT NOT NULL, `reminderTimeMinutes` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `completedAt` INTEGER, `deletedAt` INTEGER)")
            sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS `checklist_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `tripId` INTEGER NOT NULL, `text` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS `moments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `tripId` INTEGER NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, `hyperlinksJson` TEXT, `imageUri` TEXT, `timestamp` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS `travel_stamps` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `tripId` INTEGER NOT NULL, `stampNumber` INTEGER NOT NULL, `stampCode` TEXT NOT NULL, `title` TEXT NOT NULL, `destination` TEXT NOT NULL, `dateText` TEXT NOT NULL, `peopleCount` INTEGER NOT NULL, `momentsCount` INTEGER NOT NULL, `inkColorHex` TEXT NOT NULL, `stampStyle` TEXT NOT NULL, `inspectionText` TEXT NOT NULL, `issuedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `completedAt` INTEGER, `deletedAt` INTEGER, `reflectionNote` TEXT, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

            // Seed data
            sqliteDb.execSQL("INSERT INTO trips (id, uuid, name, destination, date, peopleCount, description, status, stampEarned, reminderEnabled, reminderPreset, createdAt, updatedAt) VALUES (99, 'trip-uuid-99', 'Migrated Trip', 'Ghats', '10 Aug 2026', 4, 'Awesome', 'COMPLETED', 1, 0, 'ONE_DAY_BEFORE', 1700000000, 1700000000)")
            sqliteDb.execSQL("INSERT INTO checklist_items (id, uuid, tripId, text, isCompleted, sortOrder, createdAt, updatedAt) VALUES (1, 'item-uuid-1', 99, 'Rope', 1, 0, 1700000000, 1700000000)")
            sqliteDb.close()

            // Open with our Database class which runs migrations up to version 8
            val migratedDb = Room.databaseBuilder(context, TravelStampDatabase::class.java, dbFile.absolutePath)
                .addMigrations(*TravelStampDatabase.ALL_MIGRATIONS)
                .build()

            val trips = migratedDb.tripDao().getAllTripsListSync()
            assertEquals(1, trips.size)
            assertEquals("Migrated Trip", trips[0].name)
            assertEquals("trip-uuid-99", trips[0].uuid)

            val checklist = migratedDb.checklistDao().getAllItemsListSync()
            assertEquals(1, checklist.size)
            assertEquals("Rope", checklist[0].text)

            migratedDb.close()
            dbFile.delete()
        }
    }

    // 12. migration creates zero fake locations
    @Test
    fun testMigrationCreatesZeroFakeLocations() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbFile = File(context.cacheDir, "test_v7_empty_locs.db")
            if (dbFile.exists()) dbFile.delete()

            val sqliteDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            sqliteDb.version = 7
            sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `destination` TEXT NOT NULL, `date` TEXT NOT NULL, `startTimeMinutes` INTEGER, `peopleCount` INTEGER NOT NULL, `description` TEXT NOT NULL, `status` TEXT NOT NULL, `stampEarned` INTEGER NOT NULL, `reminderEnabled` INTEGER NOT NULL, `reminderPreset` TEXT NOT NULL, `reminderTimeMinutes` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `completedAt` INTEGER, `deletedAt` INTEGER)")
            sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS `checklist_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `tripId` INTEGER NOT NULL, `text` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS `moments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `tripId` INTEGER NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, `hyperlinksJson` TEXT, `imageUri` TEXT, `timestamp` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS `travel_stamps` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `tripId` INTEGER NOT NULL, `stampNumber` INTEGER NOT NULL, `stampCode` TEXT NOT NULL, `title` TEXT NOT NULL, `destination` TEXT NOT NULL, `dateText` TEXT NOT NULL, `peopleCount` INTEGER NOT NULL, `momentsCount` INTEGER NOT NULL, `inkColorHex` TEXT NOT NULL, `stampStyle` TEXT NOT NULL, `inspectionText` TEXT NOT NULL, `issuedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `completedAt` INTEGER, `deletedAt` INTEGER, `reflectionNote` TEXT, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

            sqliteDb.execSQL("INSERT INTO trips (id, uuid, name, destination, date, peopleCount, description, status, stampEarned, reminderEnabled, reminderPreset, createdAt, updatedAt) VALUES (10, 'trip-10', 'Old Trip', 'Base', '10 Aug 2026', 1, 'Desc', 'UPCOMING', 0, 0, 'ONE_DAY_BEFORE', 1700000000, 1700000000)")
            sqliteDb.close()

            val migratedDb = Room.databaseBuilder(context, TravelStampDatabase::class.java, dbFile.absolutePath)
                .addMigrations(*TravelStampDatabase.ALL_MIGRATIONS)
                .build()

            val locations = migratedDb.journeyLocationDao().getAllLocationsSync()
            // Ensure table was created but has exactly 0 entries (no fake records injected during migration)
            assertEquals(0, locations.size)

            migratedDb.close()
            dbFile.delete()
        }
    }

    // 13. new backup exports locations
    @Test
    fun testNewBackupExportsLocations() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val tripId = db.tripDao().insertTrip(
                TripEntity(name = "Korigad", destination = "Lonavala", date = "10 Aug 2026", status = "IN_PROGRESS")
            )
            db.journeyLocationDao().insertLocation(
                JourneyLocationEntity(
                    uuid = "export-uuid-1",
                    tripId = tripId,
                    label = "Fort Entrance",
                    latitude = 18.7244,
                    longitude = 73.3888,
                    sortOrder = 0,
                    createdAt = 123456789L,
                    updatedAt = 987654321L
                )
            )

            val jsonStr = BackupManager.generateBackupJson(db)
            val root = JSONObject(jsonStr)

            assertTrue(root.has("journeyLocations"))
            val arr = root.getJSONArray("journeyLocations")
            assertEquals(1, arr.length())

            val obj = arr.getJSONObject(0)
            assertEquals("export-uuid-1", obj.getString("uuid"))
            assertEquals("Fort Entrance", obj.getString("label"))
            assertEquals(18.7244, obj.getDouble("latitude"), 0.0001)
            assertEquals(73.3888, obj.getDouble("longitude"), 0.0001)
            assertEquals(0, obj.getInt("sortOrder"))
            assertEquals(123456789L, obj.getLong("createdAt"))
            assertEquals(987654321L, obj.getLong("updatedAt"))
        }
    }

    // 14. new backup restores multiple locations to correct journey
    @Test
    fun testNewBackupRestoresMultipleLocationsToCorrectJourney() {
        runBlocking {
            val backupJson = JSONObject().apply {
                put("version", 2)
                put("appName", "TravelStamp")

                // Seed Trips with old IDs (e.g. 500)
                val tripsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", 500)
                        put("uuid", "backup-trip-uuid-1")
                        put("name", "Restored Trip")
                        put("destination", "Lonavala")
                        put("date", "10 Aug 2026")
                        put("peopleCount", 2)
                        put("description", "")
                        put("status", "IN_PROGRESS")
                        put("stampEarned", false)
                        put("reminderEnabled", false)
                        put("reminderPreset", "ONE_DAY_BEFORE")
                        put("createdAt", 1000L)
                        put("updatedAt", 2000L)
                    })
                }
                put("trips", tripsArray)

                // Seed Journey Locations referencing old ID 500
                val locationsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", 10)
                        put("uuid", "restored-loc-uuid-1")
                        put("tripId", 500)
                        put("label", "Point A")
                        put("latitude", 18.1234)
                        put("longitude", 73.5678)
                        put("sortOrder", 0)
                        put("createdAt", 1000L)
                        put("updatedAt", 2000L)
                    })
                    put(JSONObject().apply {
                        put("id", 11)
                        put("uuid", "restored-loc-uuid-2")
                        put("tripId", 500)
                        put("label", "Point B")
                        put("latitude", 18.5678)
                        put("longitude", 73.1234)
                        put("sortOrder", 1)
                        put("createdAt", 1000L)
                        put("updatedAt", 2000L)
                    })
                }
                put("journeyLocations", locationsArray)
            }

            // Wipe Database and import
            db.tripDao().getAllTripsListSync().forEach { db.tripDao().deleteTripById(it.id) }

            val res = BackupManager.importBackupJson(db, backupJson.toString())
            assertTrue(res.isSuccess)

            val trips = db.tripDao().getAllTripsListSync()
            assertEquals(1, trips.size)
            val newTripId = trips[0].id

            val locations = db.journeyLocationDao().getLocationsForTripSync(newTripId)
            assertEquals(2, locations.size)
            assertEquals("Point A", locations[0].label)
            assertEquals("Point B", locations[1].label)
            assertEquals(newTripId, locations[0].tripId)
            assertEquals(newTripId, locations[1].tripId)
        }
    }

    // 15. old backup without journeyLocations still imports
    @Test
    fun testOldBackupWithoutJourneyLocationsStillImports() {
        runBlocking {
            val backupJson = JSONObject().apply {
                put("version", 2)
                put("appName", "TravelStamp")
                val tripsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", 123)
                        put("uuid", "old-trip-uuid")
                        put("name", "Old Format Trip")
                        put("destination", "Pune")
                        put("date", "10 Aug 2026")
                        put("peopleCount", 1)
                        put("description", "")
                        put("status", "IN_PROGRESS")
                        put("stampEarned", false)
                        put("reminderEnabled", false)
                        put("reminderPreset", "ONE_DAY_BEFORE")
                        put("createdAt", 1000L)
                        put("updatedAt", 2000L)
                    })
                }
                put("trips", tripsArray)
                // Lacks "journeyLocations" entirely!
            }

            db.tripDao().getAllTripsListSync().forEach { db.tripDao().deleteTripById(it.id) }

            val res = BackupManager.importBackupJson(db, backupJson.toString())
            assertTrue(res.isSuccess)

            val trips = db.tripDao().getAllTripsListSync()
            assertEquals(1, trips.size)

            val locations = db.journeyLocationDao().getAllLocationsSync()
            assertEquals(0, locations.size)
        }
    }

    // 16. backup/restore preserves location UUID and ordering
    @Test
    fun testBackupRestorePreservesLocationUuidAndOrdering() {
        runBlocking {
            val backupJson = JSONObject().apply {
                put("version", 2)
                put("appName", "TravelStamp")
                val tripsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", 80)
                        put("uuid", "trip-80")
                        put("name", "Trip 80")
                        put("destination", "Dest")
                        put("date", "10 Aug 2026")
                        put("peopleCount", 1)
                        put("status", "IN_PROGRESS")
                        put("createdAt", 1000L)
                        put("updatedAt", 2000L)
                    })
                }
                put("trips", tripsArray)

                val locationsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", 20)
                        put("uuid", "custom-uuid-preserved-2")
                        put("tripId", 80)
                        put("label", "Point B")
                        put("latitude", 18.0)
                        put("longitude", 73.0)
                        put("sortOrder", 99)
                    })
                    put(JSONObject().apply {
                        put("id", 21)
                        put("uuid", "custom-uuid-preserved-1")
                        put("tripId", 80)
                        put("label", "Point A")
                        put("latitude", 18.1)
                        put("longitude", 73.1)
                        put("sortOrder", 5)
                    })
                }
                put("journeyLocations", locationsArray)
            }

            db.tripDao().getAllTripsListSync().forEach { db.tripDao().deleteTripById(it.id) }

            val res = BackupManager.importBackupJson(db, backupJson.toString())
            assertTrue(res.isSuccess)

            val trips = db.tripDao().getAllTripsListSync()
            val newTripId = trips[0].id

            val locations = db.journeyLocationDao().getLocationsForTripSync(newTripId)
            assertEquals(2, locations.size)

            // Point A has sortOrder 5, Point B has sortOrder 99.
            // Due to "ORDER BY sortOrder ASC", Point A must be first, Point B second.
            assertEquals("Point A", locations[0].label)
            assertEquals("custom-uuid-preserved-1", locations[0].uuid)
            assertEquals(5, locations[0].sortOrder)

            assertEquals("Point B", locations[1].label)
            assertEquals("custom-uuid-preserved-2", locations[1].uuid)
            assertEquals(99, locations[1].sortOrder)
        }
    }

    // 17. malformed location backup record cannot corrupt unrelated valid journal data
    @Test
    fun testMalformedLocationBackupRecordDoesNotCorruptBackup() {
        runBlocking {
            val backupJson = JSONObject().apply {
                put("version", 2)
                put("appName", "TravelStamp")
                val tripsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", 90)
                        put("uuid", "trip-90")
                        put("name", "Trip 90")
                        put("destination", "Dest")
                        put("date", "10 Aug 2026")
                        put("peopleCount", 1)
                        put("status", "IN_PROGRESS")
                        put("createdAt", 1000L)
                        put("updatedAt", 2000L)
                    })
                }
                put("trips", tripsArray)

                val locationsArray = JSONArray().apply {
                    // 1st: Malformed (Latitude is NaN)
                    put(JSONObject().apply {
                        put("id", 30)
                        put("uuid", "malformed-1")
                        put("tripId", 90)
                        put("label", "Malformed point")
                        put("latitude", "NaN")
                        put("longitude", 73.0)
                        put("sortOrder", 0)
                    })
                    // 2nd: Malformed (Blank label)
                    put(JSONObject().apply {
                        put("id", 31)
                        put("uuid", "malformed-2")
                        put("tripId", 90)
                        put("label", "   ")
                        put("latitude", 18.0)
                        put("longitude", 73.0)
                        put("sortOrder", 1)
                    })
                    // 3rd: Malformed (Latitude out of range)
                    put(JSONObject().apply {
                        put("id", 32)
                        put("uuid", "malformed-3")
                        put("tripId", 90)
                        put("label", "Out of bounds")
                        put("latitude", 120.0)
                        put("longitude", 73.0)
                        put("sortOrder", 2)
                    })
                    // 4th: Perfectly Valid
                    put(JSONObject().apply {
                        put("id", 33)
                        put("uuid", "perfectly-valid")
                        put("tripId", 90)
                        put("label", "Valid Summit")
                        put("latitude", 18.99)
                        put("longitude", 73.88)
                        put("sortOrder", 3)
                    })
                }
                put("journeyLocations", locationsArray)
            }

            db.tripDao().getAllTripsListSync().forEach { db.tripDao().deleteTripById(it.id) }

            val res = BackupManager.importBackupJson(db, backupJson.toString())
            assertTrue(res.isSuccess) // Entire process must still succeed

            val trips = db.tripDao().getAllTripsListSync()
            assertEquals(1, trips.size)
            val newTripId = trips[0].id

            val locations = db.journeyLocationDao().getLocationsForTripSync(newTripId)
            assertEquals(1, locations.size) // Only 1 valid location should be imported
            assertEquals("perfectly-valid", locations[0].uuid)
            assertEquals("Valid Summit", locations[0].label)
        }
    }

    // 18. location modification does not modify/reissue TravelStamp
    @Test
    fun testLocationModificationDoesNotModifyStamp() {
        runBlocking {
            val tripId = db.tripDao().insertTrip(
                TripEntity(name = "Harihar Fort", destination = "Nashik", date = "10 Aug 2026", status = "COMPLETED")
            )

            // Issue initial travel stamp
            val initialStamp = stampRepo.issueOfficialStampForTrip(
                tripId = tripId,
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
            assertNotNull(initialStamp)
            val stampUuidBefore = initialStamp!!.uuid
            val stampNumberBefore = initialStamp.stampNumber
            val stampCodeBefore = initialStamp.stampCode

            // Perform location operations
            val locEntity = JourneyLocationEntity(
                uuid = "loc-stamp-test",
                tripId = tripId,
                label = "Checkpoint 1",
                latitude = 19.9011,
                longitude = 73.4561,
                sortOrder = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Insert
            val locId = db.journeyLocationDao().insertLocation(locEntity)

            // Verify stamp unchanged
            var refreshedStamp = db.travelStampDao().getStampForTripSync(tripId)
            assertNotNull(refreshedStamp)
            assertEquals(stampUuidBefore, refreshedStamp!!.uuid)
            assertEquals(stampNumberBefore, refreshedStamp.stampNumber)
            assertEquals(stampCodeBefore, refreshedStamp.stampCode)

            // Update location
            db.journeyLocationDao().updateLocation(locEntity.copy(id = locId, label = "Checkpoint 1 - Edited"))

            // Verify stamp unchanged
            refreshedStamp = db.travelStampDao().getStampForTripSync(tripId)
            assertNotNull(refreshedStamp)
            assertEquals(stampUuidBefore, refreshedStamp!!.uuid)
            assertEquals(stampNumberBefore, refreshedStamp.stampNumber)
            assertEquals(stampCodeBefore, refreshedStamp.stampCode)

            // Delete location
            db.journeyLocationDao().deleteLocationById(locId)

            // Verify stamp unchanged
            refreshedStamp = db.travelStampDao().getStampForTripSync(tripId)
            assertNotNull(refreshedStamp)
            assertEquals(stampUuidBefore, refreshedStamp!!.uuid)
            assertEquals(stampNumberBefore, refreshedStamp.stampNumber)
            assertEquals(stampCodeBefore, refreshedStamp.stampCode)
        }
    }
}
