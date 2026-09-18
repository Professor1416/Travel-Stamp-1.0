package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TravelStampDatabase
import com.example.data.local.entity.TripEntity
import com.example.data.local.entity.TravelStampEntity
import com.example.data.local.entity.JourneyLocationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InkMapRepositoryTest {

    private lateinit var db: TravelStampDatabase
    private lateinit var stampRepo: TravelStampRepositoryImpl
    private lateinit var journeyRepo: JourneyLocationRepositoryImpl
    private lateinit var inkMapRepo: InkMapRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        stampRepo = TravelStampRepositoryImpl(db.travelStampDao())
        journeyRepo = JourneyLocationRepositoryImpl(db.journeyLocationDao())
        inkMapRepo = InkMapRepositoryImpl(stampRepo, journeyRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test_26_active_stamp_is_emitted() = runBlocking {
        // Given an active parent trip and its corresponding travel stamp
        val tripId = db.tripDao().insertTrip(
            TripEntity(id = 1, name = "India", destination = "Mumbai", date = "2026-09-18", status = "COMPLETED", deletedAt = null)
        )
        db.travelStampDao().insertStamp(
            TravelStampEntity(id = 10, tripId = tripId, stampNumber = 1L, stampCode = "#001", title = "Mumbai Visit", destination = "Mumbai", dateText = "2026-09-18", peopleCount = 1, momentsCount = 0, deletedAt = null)
        )

        // When observing active stamps
        val activeStamps = stampRepo.observeAllActiveStamps().first()

        // Then it should contain our active stamp
        assertEquals(1, activeStamps.size)
        assertEquals(10L, activeStamps[0].id)
    }

    @Test
    fun test_27_soft_deleted_parent_trip_stamp_is_excluded() = runBlocking {
        // Given a soft-deleted trip and its corresponding travel stamp
        val tripId = db.tripDao().insertTrip(
            TripEntity(id = 1, name = "India", destination = "Mumbai", date = "2026-09-18", status = "COMPLETED", deletedAt = 123456789L)
        )
        db.travelStampDao().insertStamp(
            TravelStampEntity(id = 10, tripId = tripId, stampNumber = 1L, stampCode = "#001", title = "Mumbai Visit", destination = "Mumbai", dateText = "2026-09-18", peopleCount = 1, momentsCount = 0, deletedAt = null)
        )

        // When observing active stamps
        val activeStamps = stampRepo.observeAllActiveStamps().first()

        // Then it should be empty because the parent trip is soft-deleted
        assertTrue(activeStamps.isEmpty())
    }

    @Test
    fun test_28_stamp_with_deletedAt_not_null_is_excluded() = runBlocking {
        // Given an active trip but its travel stamp is soft-deleted
        val tripId = db.tripDao().insertTrip(
            TripEntity(id = 1, name = "India", destination = "Mumbai", date = "2026-09-18", status = "COMPLETED", deletedAt = null)
        )
        db.travelStampDao().insertStamp(
            TravelStampEntity(id = 10, tripId = tripId, stampNumber = 1L, stampCode = "#001", title = "Mumbai Visit", destination = "Mumbai", dateText = "2026-09-18", peopleCount = 1, momentsCount = 0, deletedAt = 123456789L)
        )

        // When observing active stamps
        val activeStamps = stampRepo.observeAllActiveStamps().first()

        // Then it should be empty
        assertTrue(activeStamps.isEmpty())
    }

    @Test
    fun test_29_hard_deleted_trip_cannot_leak_stamp_or_location_due_to_fk_cascade() = runBlocking {
        // Given an active trip with stamp and locations
        val tripId = db.tripDao().insertTrip(
            TripEntity(id = 1, name = "India", destination = "Mumbai", date = "2026-09-18", status = "COMPLETED", deletedAt = null)
        )
        db.travelStampDao().insertStamp(
            TravelStampEntity(id = 10, tripId = tripId, stampNumber = 1L, stampCode = "#001", title = "Mumbai Visit", destination = "Mumbai", dateText = "2026-09-18", peopleCount = 1, momentsCount = 0, deletedAt = null)
        )
        db.journeyLocationDao().insertLocation(
            JourneyLocationEntity(id = 100, uuid = "loc-1", tripId = tripId, label = "Gateway", latitude = 18.9, longitude = 72.8, sortOrder = 0)
        )

        // Verify they exist in DB
        assertNotNull(db.travelStampDao().getStampForTripSync(tripId))
        assertEquals(1, db.journeyLocationDao().getLocationsForTripSync(tripId).size)

        // When hard-deleting the parent trip
        db.tripDao().deleteTripById(tripId)

        // Then cascading delete must purge both stamp and locations from DB
        assertNull(db.travelStampDao().getStampForTripSync(tripId))
        assertEquals(0, db.journeyLocationDao().getLocationsForTripSync(tripId).size)
    }

    @Test
    fun test_30_active_locations_query_still_excludes_soft_deleted_parent_trips() = runBlocking {
        // Given a soft-deleted trip and its location
        val tripId = db.tripDao().insertTrip(
            TripEntity(id = 1, name = "India", destination = "Mumbai", date = "2026-09-18", status = "COMPLETED", deletedAt = 123456789L)
        )
        db.journeyLocationDao().insertLocation(
            JourneyLocationEntity(id = 100, uuid = "loc-1", tripId = tripId, label = "Gateway", latitude = 18.9, longitude = 72.8, sortOrder = 0)
        )

        // When observing active locations
        val activeLocations = journeyRepo.observeAllActiveLocations().first()

        // Then it must be empty
        assertTrue(activeLocations.isEmpty())
    }

    @Test
    fun test_31_repository_combines_active_stamps_and_active_locations_correctly() = runBlocking {
        // Given an active trip with active stamp and active locations, plus one active stamp with no locations
        val tripId1 = db.tripDao().insertTrip(
            TripEntity(id = 1, name = "India", destination = "Mumbai", date = "2026-09-18", status = "COMPLETED", deletedAt = null)
        )
        db.travelStampDao().insertStamp(
            TravelStampEntity(id = 10, tripId = tripId1, stampNumber = 1L, stampCode = "#001", title = "Mumbai Visit", destination = "Mumbai", dateText = "2026-09-18", peopleCount = 1, momentsCount = 0, deletedAt = null)
        )
        db.journeyLocationDao().insertLocation(
            JourneyLocationEntity(id = 100, uuid = "loc-1", tripId = tripId1, label = "Gateway", latitude = 18.9, longitude = 72.8, sortOrder = 0)
        )

        val tripId2 = db.tripDao().insertTrip(
            TripEntity(id = 2, name = "India", destination = "Delhi", date = "2026-09-18", status = "COMPLETED", deletedAt = null)
        )
        db.travelStampDao().insertStamp(
            TravelStampEntity(id = 20, tripId = tripId2, stampNumber = 2L, stampCode = "#002", title = "Delhi Visit", destination = "Delhi", dateText = "2026-09-18", peopleCount = 1, momentsCount = 0, deletedAt = null)
        )

        // When observing Ink Map Data from repository
        val inkMapData = inkMapRepo.observeInkMapData().first()

        // Then points and missingLocations should match perfectly
        assertEquals(1, inkMapData.points.size)
        assertEquals(100L, inkMapData.points[0].locationId)
        assertEquals(10L, inkMapData.points[0].stampId)

        assertEquals(1, inkMapData.missingLocations.size)
        assertEquals(20L, inkMapData.missingLocations[0].stampId)
    }

    @Test
    fun test_32_no_Passport_getAllStamps_behavior_was_changed() = runBlocking {
        // Given an active trip + stamp, and a soft-deleted trip + stamp
        val tripIdActive = db.tripDao().insertTrip(
            TripEntity(id = 1, name = "Active", destination = "Dest1", date = "2026-09-18", status = "COMPLETED", deletedAt = null)
        )
        val stampActiveId = db.travelStampDao().insertStamp(
            TravelStampEntity(id = 10, tripId = tripIdActive, stampNumber = 1L, stampCode = "#001", title = "Title1", destination = "Dest1", dateText = "2026-09-18", peopleCount = 1, momentsCount = 0, deletedAt = null)
        )

        val tripIdDeleted = db.tripDao().insertTrip(
            TripEntity(id = 2, name = "Deleted", destination = "Dest2", date = "2026-09-18", status = "COMPLETED", deletedAt = 123456789L)
        )
        val stampDeletedId = db.travelStampDao().insertStamp(
            TravelStampEntity(id = 20, tripId = tripIdDeleted, stampNumber = 2L, stampCode = "#002", title = "Title2", destination = "Dest2", dateText = "2026-09-18", peopleCount = 1, momentsCount = 0, deletedAt = null)
        )

        // When retrieving getAllStamps (used by Passport)
        val allStamps = stampRepo.getAllStamps().first()

        // Then BOTH stamps must be returned because Passport's getAllStamps() does not filter out parent trip's soft deletion (the asymmetry IM-4E.1 found)
        assertEquals(2, allStamps.size)

        // And only active stamps are returned for our active read path
        val activeStamps = stampRepo.observeAllActiveStamps().first()
        assertEquals(1, activeStamps.size)
        assertEquals(stampActiveId, activeStamps[0].id)
    }
}
