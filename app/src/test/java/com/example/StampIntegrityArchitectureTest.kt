package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TravelStampDatabase
import com.example.data.local.entity.ChecklistItemEntity
import com.example.data.local.entity.MomentEntity
import com.example.data.local.entity.TravelStampEntity
import com.example.data.local.entity.TripEntity
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.data.repository.ChecklistRepositoryImpl
import com.example.data.repository.MomentRepositoryImpl
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepositoryImpl
import com.example.data.util.BackupManager
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StampIntegrityArchitectureTest {

    private lateinit var context: Context
    private lateinit var db: TravelStampDatabase
    private lateinit var tripRepo: TripRepositoryImpl
    private lateinit var stampRepo: TravelStampRepositoryImpl
    private lateinit var momentRepo: MomentRepositoryImpl
    private lateinit var checklistRepo: ChecklistRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tripRepo = TripRepositoryImpl(db.tripDao(), db.momentDao())
        stampRepo = TravelStampRepositoryImpl(db.travelStampDao())
        momentRepo = MomentRepositoryImpl(db.momentDao(), context)
        checklistRepo = ChecklistRepositoryImpl(db.checklistDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `scenario 1 - completed trip without travel stamp has stampEarned false`() = runBlocking {
        // Insert a completed trip with stampEarned = false
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Aunda Fort",
                destination = "Nashik, Maharashtra",
                date = "15 Jan 2024",
                status = "COMPLETED",
                stampEarned = false,
                completedAt = 1705300000000L
            )
        )

        val tripDomain = tripRepo.getTripByIdSync(tripId)
        assertNotNull(tripDomain)
        assertEquals(TripStatus.COMPLETED, tripDomain?.status)
        assertFalse("Completed trip without a stamp record must have stampEarned = false", tripDomain?.stampEarned == true)

        val stamp = stampRepo.getStampForTripSync(tripId)
        assertNull("No stamp record should exist for Aunda Fort", stamp)
    }

    @Test
    fun `scenario 2 - completed trip with travel stamp has stampEarned true`() = runBlocking {
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Harihar Fort",
                destination = "Nashik, Maharashtra",
                date = "16 Jul 2021",
                status = "IN_PROGRESS",
                stampEarned = false
            )
        )

        val issueResult = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Harihar Fort",
            destination = "Nashik, Maharashtra",
            dateText = "16 Jul 2021",
            peopleCount = 2,
            momentsCount = 3,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = "Iconic rock cut steps"
        )
        assertTrue(issueResult.isSuccess)

        val tripDomain = tripRepo.getTripByIdSync(tripId)
        assertNotNull(tripDomain)
        assertEquals(TripStatus.COMPLETED, tripDomain?.status)
        assertTrue("Trip with issued stamp must have stampEarned = true", tripDomain?.stampEarned == true)

        val stamp = stampRepo.getStampForTripSync(tripId)
        assertNotNull(stamp)
        assertEquals(tripId, stamp?.tripId)
        assertEquals(1L, stamp?.stampNumber)
        assertEquals("#001", stamp?.stampCode)
    }

    @Test
    fun `scenario 3 - reconciliation clears stale stampEarned flags on orphan trips`() = runBlocking {
        // Seed corrupted / stale trips that claim stampEarned = true but have NO travel_stamps row
        val corruptedTrip1 = db.tripDao().insertTrip(
            TripEntity(
                name = "RatanGad",
                destination = "Bhandardara, Maharashtra",
                date = "10 Feb 2024",
                status = "COMPLETED",
                stampEarned = true,
                completedAt = 1707550000000L
            )
        )
        val corruptedTrip2 = db.tripDao().insertTrip(
            TripEntity(
                name = "Aunda Fort",
                destination = "Nashik, Maharashtra",
                date = "15 Jan 2024",
                status = "COMPLETED",
                stampEarned = true,
                completedAt = 1705300000000L
            )
        )

        // Seed a valid trip WITH a real travel stamp
        val validTripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Tringalwadi Fort",
                destination = "Igatpuri, Maharashtra",
                date = "22 Aug 2026",
                status = "COMPLETED",
                stampEarned = true,
                completedAt = 1724300000000L
            )
        )
        db.travelStampDao().insertStamp(
            TravelStampEntity(
                tripId = validTripId,
                stampNumber = 1L,
                stampCode = "#001",
                title = "Tringalwadi Fort",
                destination = "Igatpuri, Maharashtra",
                dateText = "22 Aug 2026",
                peopleCount = 1,
                momentsCount = 0
            )
        )

        // Run reconciliation
        stampRepo.reconcileStampIntegrity()

        // Verify corrupted trips have stampEarned reset to false
        val trip1 = db.tripDao().getTripByIdSync(corruptedTrip1)
        assertNotNull(trip1)
        assertEquals(false, trip1?.stampEarned)
        assertEquals(false, trip1?.toDomain()?.stampEarned)

        val trip2 = db.tripDao().getTripByIdSync(corruptedTrip2)
        assertNotNull(trip2)
        assertEquals(false, trip2?.stampEarned)
        assertEquals(false, trip2?.toDomain()?.stampEarned)

        // Verify valid trip retains stampEarned = true
        val validTrip = db.tripDao().getTripByIdSync(validTripId)
        assertNotNull(validTrip)
        assertEquals(true, validTrip?.stampEarned)
        assertEquals(true, validTrip?.toDomain()?.stampEarned)
    }

    @Test
    fun `scenario 4 - backup import roundtrip reconciles stamps accurately`() = runBlocking {
        // Construct backup JSON representing 4 trips: 2 legitimate stamps and 2 completed stamp-less journeys
        val backupJson = """
        {
            "version": 2,
            "exportedAt": 1726000000000,
            "trips": [
                {
                    "id": 1,
                    "uuid": "trip-uuid-1",
                    "name": "Tringalwadi Fort",
                    "destination": "Igatpuri",
                    "date": "22 Aug 2026",
                    "status": "COMPLETED",
                    "stampEarned": true,
                    "completedAt": 1724300000000
                },
                {
                    "id": 2,
                    "uuid": "trip-uuid-2",
                    "name": "BhaskarGad",
                    "destination": "Nashik",
                    "date": "9 Aug 2026",
                    "status": "COMPLETED",
                    "stampEarned": true,
                    "completedAt": 1723180000000
                },
                {
                    "id": 3,
                    "uuid": "trip-uuid-3",
                    "name": "Aunda Fort",
                    "destination": "Nashik",
                    "date": "15 Jan 2024",
                    "status": "COMPLETED",
                    "stampEarned": true,
                    "completedAt": 1705300000000
                },
                {
                    "id": 4,
                    "uuid": "trip-uuid-4",
                    "name": "RatanGad",
                    "destination": "Bhandardara",
                    "date": "10 Feb 2024",
                    "status": "COMPLETED",
                    "stampEarned": true,
                    "completedAt": 1707550000000
                }
            ],
            "travelStamps": [
                {
                    "tripId": 1,
                    "stampNumber": 1,
                    "stampCode": "#001",
                    "title": "Tringalwadi Fort",
                    "destination": "Igatpuri",
                    "dateText": "22 Aug 2026"
                },
                {
                    "tripId": 2,
                    "stampNumber": 2,
                    "stampCode": "#002",
                    "title": "BhaskarGad",
                    "destination": "Nashik",
                    "dateText": "9 Aug 2026"
                }
            ]
        }
        """.trimIndent()

        val importResult = BackupManager.importBackupJson(db, backupJson)
        assertTrue(importResult.isSuccess)
        val result = importResult.getOrThrow()
        assertEquals(4, result.importedTrips)
        assertEquals(2, result.importedStamps)

        val allTrips = tripRepo.getAllTrips().first()
        assertEquals(4, allTrips.size)

        val allStamps = stampRepo.getAllStamps().first()
        assertEquals(2, allStamps.size)

        val tringalwadi = allTrips.first { it.name == "Tringalwadi Fort" }
        assertTrue("Tringalwadi has a real stamp record so stampEarned must be true", tringalwadi.stampEarned)

        val bhaskargad = allTrips.first { it.name == "BhaskarGad" }
        assertTrue("BhaskarGad has a real stamp record so stampEarned must be true", bhaskargad.stampEarned)

        val aunda = allTrips.first { it.name == "Aunda Fort" }
        assertEquals(TripStatus.COMPLETED, aunda.status)
        assertFalse("Aunda Fort does NOT have a stamp record, so stampEarned must be false", aunda.stampEarned)

        val ratangad = allTrips.first { it.name == "RatanGad" }
        assertEquals(TripStatus.COMPLETED, ratangad.status)
        assertFalse("RatanGad does NOT have a stamp record, so stampEarned must be false", ratangad.stampEarned)
    }

    @Test
    fun `scenario 5 - deleting stamp resets trip stampEarned flag`() = runBlocking {
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Dehergad",
                destination = "Nashik",
                date = "16 Aug 2026",
                status = "IN_PROGRESS",
                stampEarned = false
            )
        )

        val stampResult = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Dehergad",
            destination = "Nashik",
            dateText = "16 Aug 2026",
            peopleCount = 1,
            momentsCount = 0,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = null
        )
        assertTrue(stampResult.isSuccess)
        val stamp = stampResult.getOrThrow()

        val tripBeforeDelete = tripRepo.getTripByIdSync(tripId)
        assertTrue(tripBeforeDelete?.stampEarned == true)

        // Delete stamp
        stampRepo.deleteStamp(stamp.id)

        val tripAfterDelete = tripRepo.getTripByIdSync(tripId)
        assertNotNull(tripAfterDelete)
        assertFalse("Deleting stamp must reset trip.stampEarned to false", tripAfterDelete?.stampEarned == true)
        val fetchedStamp = stampRepo.getStampForTripSync(tripId)
        assertNull(fetchedStamp)
    }

    @Test
    fun `scenario 6 - finishTrip without issuing stamp does not set stampEarned`() = runBlocking {
        val tripId = tripRepo.createTrip(
            Trip(
                name = "Kalsubai Peak",
                destination = "Igatpuri, Maharashtra",
                date = "10 Jan 2024"
            )
        )

        val finished = tripRepo.finishTrip(tripId)
        assertTrue(finished)

        val trip = tripRepo.getTripByIdSync(tripId)
        assertNotNull(trip)
        assertEquals(TripStatus.COMPLETED, trip?.status)
        assertFalse("finishTrip alone without stamp issuance must not set stampEarned", trip?.stampEarned == true)
        val stamp = stampRepo.getStampForTripSync(tripId)
        assertNull(stamp)
    }

    @Test
    fun `scenario 7 - atomic stamp issuance is idempotent`() = runBlocking {
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Ramshej Fort",
                destination = "Nashik",
                date = "5 Jul 2026",
                status = "IN_PROGRESS"
            )
        )

        val res1 = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Ramshej Fort",
            destination = "Nashik",
            dateText = "5 Jul 2026",
            peopleCount = 3,
            momentsCount = 1,
            inkColorHex = "#C85A32",
            stampStyle = "FORT",
            reflectionNote = "Historic fort"
        )
        assertTrue(res1.isSuccess)
        val stamp1 = res1.getOrThrow()

        // Calling again on the same trip
        val res2 = stampRepo.completeTripAndIssueStamp(
            tripId = tripId,
            title = "Ramshej Fort",
            destination = "Nashik",
            dateText = "5 Jul 2026",
            peopleCount = 3,
            momentsCount = 1,
            inkColorHex = "#C85A32",
            stampStyle = "FORT",
            reflectionNote = "Historic fort"
        )
        assertTrue(res2.isSuccess)
        val stamp2 = res2.getOrThrow()

        assertEquals(stamp1.id, stamp2.id)
        assertEquals(stamp1.stampNumber, stamp2.stampNumber)
        assertEquals(stamp1.stampCode, stamp2.stampCode)
        assertEquals(1, stampRepo.getStampsCountSync())
    }

    @Test
    fun `scenario 8 - future trip cannot issue stamp`() = runBlocking {
        val futureTripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Everest Base Camp",
                destination = "Nepal",
                date = "25 Dec 2030",
                status = "UPCOMING"
            )
        )

        val res = stampRepo.completeTripAndIssueStamp(
            tripId = futureTripId,
            title = "Everest Base Camp",
            destination = "Nepal",
            dateText = "25 Dec 2030",
            peopleCount = 2,
            momentsCount = 0,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = null
        )
        assertTrue("Issuing stamp for future trip must fail", res.isFailure)
        assertEquals(0, stampRepo.getStampsCountSync())
    }

    @Test
    fun `scenario 9 - stamp numbers remain immutable and non-contiguous numbering is preserved`() = runBlocking {
        // Insert stamps with non-contiguous numbers (#001, #002, #003, #004, #025, #026)
        val tripIds = (1..6).map { i ->
            db.tripDao().insertTrip(
                TripEntity(
                    id = i.toLong(),
                    name = "Trip $i",
                    destination = "Dest $i",
                    date = "10 Jan 2024",
                    status = "COMPLETED"
                )
            )
        }

        val numbers = listOf(1L, 2L, 3L, 4L, 25L, 26L)
        numbers.forEachIndexed { index, num ->
            db.travelStampDao().insertStamp(
                TravelStampEntity(
                    tripId = tripIds[index],
                    stampNumber = num,
                    stampCode = String.format("#%03d", num),
                    title = "Trip ${index + 1}",
                    destination = "Dest ${index + 1}",
                    dateText = "10 Jan 2024",
                    peopleCount = 1,
                    momentsCount = 0
                )
            )
        }

        stampRepo.reconcileStampIntegrity()

        val allStamps = db.travelStampDao().getAllStampsListSync()
        assertEquals(6, allStamps.size)
        assertEquals(listOf(1L, 2L, 3L, 4L, 25L, 26L), allStamps.map { it.stampNumber })
        assertEquals(listOf("#001", "#002", "#003", "#004", "#025", "#026"), allStamps.map { it.stampCode })
    }

    @Test
    fun `scenario 10 - adding moments and checklists to unstamped trip preserves data and does not issue fake stamps`() = runBlocking {
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Aunda Fort",
                destination = "Nashik",
                date = "15 Jan 2024",
                status = "COMPLETED",
                stampEarned = false
            )
        )

        val momentId = db.momentDao().insertMoment(
            MomentEntity(
                tripId = tripId,
                category = "PHOTO",
                note = "Base village trail"
            )
        )
        val checklistId = db.checklistDao().insertItem(
            ChecklistItemEntity(
                tripId = tripId,
                text = "Trekking shoes",
                isCompleted = true
            )
        )

        val trip = tripRepo.getTripByIdSync(tripId)
        assertNotNull(trip)
        assertEquals(TripStatus.COMPLETED, trip?.status)
        assertFalse(trip?.stampEarned == true)

        val moments = momentRepo.getMomentsForTrip(tripId).first()
        assertEquals(1, moments.size)
        assertEquals(momentId, moments.first().id)

        val checklist = checklistRepo.getItemsForTrip(tripId).first()
        assertEquals(1, checklist.size)
        assertEquals(checklistId, checklist.first().id)

        val stamp = stampRepo.getStampForTripSync(tripId)
        assertNull(stamp)
    }

    @Test
    fun `scenario 11 - adding moments and checklists to stamped trip preserves all data and stamp`() = runBlocking {
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Tringalwadi Fort",
                destination = "Igatpuri",
                date = "22 Aug 2026",
                status = "COMPLETED",
                stampEarned = true
            )
        )
        val stampEntity = TravelStampEntity(
            tripId = tripId,
            stampNumber = 1L,
            stampCode = "#001",
            title = "Tringalwadi Fort",
            destination = "Igatpuri",
            dateText = "22 Aug 2026",
            peopleCount = 1,
            momentsCount = 0
        )
        db.travelStampDao().insertStamp(stampEntity)

        db.momentDao().insertMoment(
            MomentEntity(
                tripId = tripId,
                category = "HIGHLIGHT",
                note = "Ruins and panoramic view"
            )
        )

        val trip = tripRepo.getTripByIdSync(tripId)
        assertNotNull(trip)
        assertTrue(trip?.stampEarned == true)

        val stamp = stampRepo.getStampForTripSync(tripId)
        assertNotNull(stamp)
        assertEquals("#001", stamp?.stampCode)
        assertEquals(1L, stamp?.stampNumber)
    }

    @Test
    fun `scenario 12 - atomic date correction updates trip and stamp while keeping stamp properties intact`() = runBlocking {
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Umbhrande Waterfall",
                destination = "Igatpuri",
                date = "25 Aug 2026",
                status = "COMPLETED",
                stampEarned = true
            )
        )
        val originalStamp = TravelStampEntity(
            tripId = tripId,
            stampNumber = 26L,
            stampCode = "#026",
            title = "Umbhrande Waterfall",
            destination = "Igatpuri",
            dateText = "25 Aug 2026",
            peopleCount = 1,
            momentsCount = 0,
            inkColorHex = "#2E5D4B",
            stampStyle = "WATERFALL",
            reflectionNote = "Lush green valley"
        )
        db.travelStampDao().insertStamp(originalStamp)

        val correctResult = stampRepo.correctOfficialJourneyDate(tripId, "28 Aug 2026")
        assertTrue(correctResult.isSuccess)

        val updatedTrip = tripRepo.getTripByIdSync(tripId)
        assertEquals("28 Aug 2026", updatedTrip?.date)
        assertTrue(updatedTrip?.stampEarned == true)

        val updatedStamp = stampRepo.getStampForTripSync(tripId)
        assertNotNull(updatedStamp)
        assertEquals("28 Aug 2026", updatedStamp?.dateText)
        assertEquals(26L, updatedStamp?.stampNumber)
        assertEquals("#026", updatedStamp?.stampCode)
        assertEquals("#2E5D4B", updatedStamp?.inkColorHex)
        assertEquals("WATERFALL", updatedStamp?.stampStyle)
        assertEquals("Lush green valley", updatedStamp?.reflectionNote)
    }

    @Test
    fun `scenario 13 - next stamp number allocates monotonically after max imported number`() = runBlocking {
        // Set max sequence to 26
        db.travelStampDao().setLastAllocatedSequence(com.example.data.local.entity.StampSequenceEntity(id = "STAMP_COUNTER", lastAllocatedNumber = 26L))

        val newTripId = db.tripDao().insertTrip(
            TripEntity(
                name = "New Expedition",
                destination = "Sahyadri",
                date = "1 Sep 2026",
                status = "IN_PROGRESS"
            )
        )

        val issueResult = stampRepo.completeTripAndIssueStamp(
            tripId = newTripId,
            title = "New Expedition",
            destination = "Sahyadri",
            dateText = "1 Sep 2026",
            peopleCount = 4,
            momentsCount = 2,
            inkColorHex = "#C85A32",
            stampStyle = "MOUNTAIN",
            reflectionNote = null
        )
        assertTrue(issueResult.isSuccess)
        val newStamp = issueResult.getOrThrow()
        assertEquals(27L, newStamp.stampNumber)
        assertEquals("#027", newStamp.stampCode)
    }

    @Test
    fun `scenario 14 - export and restore preserves all entities without manufacturing stamps`() = runBlocking {
        // Setup 25 trips: 6 with stamps, 19 without stamps
        for (i in 1..25) {
            db.tripDao().insertTrip(
                TripEntity(
                    id = i.toLong(),
                    name = "Trip $i",
                    destination = "Dest $i",
                    date = "10 Jan 2024",
                    status = "COMPLETED",
                    stampEarned = (i in listOf(1, 2, 3, 4, 5, 25))
                )
            )
        }

        val legitimateTripIds = listOf(2L, 3L, 1L, 5L, 4L, 25L)
        val stampNums = listOf(1L, 2L, 3L, 4L, 25L, 26L)
        legitimateTripIds.forEachIndexed { idx, tId ->
            db.travelStampDao().insertStamp(
                TravelStampEntity(
                    tripId = tId,
                    stampNumber = stampNums[idx],
                    stampCode = String.format("#%03d", stampNums[idx]),
                    title = "Trip $tId",
                    destination = "Dest $tId",
                    dateText = "10 Jan 2024",
                    peopleCount = 1,
                    momentsCount = 0
                )
            )
        }

        // Export to JSON
        val json = BackupManager.generateBackupJson(db)
        assertTrue(json.isNotBlank())

        // Create a new fresh in-memory database to restore into
        val freshDb = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val importResult = BackupManager.importBackupJson(freshDb, json)
        assertTrue(importResult.isSuccess)

        val freshTrips = freshDb.tripDao().getAllTripsListSync()
        assertEquals(25, freshTrips.size)

        val freshStamps = freshDb.travelStampDao().getAllStampsListSync()
        assertEquals(6, freshStamps.size)

        val stampedTripsCount = freshTrips.count { it.stampEarned }
        assertEquals(6, stampedTripsCount)

        freshDb.close()
    }
}
