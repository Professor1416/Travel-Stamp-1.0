package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TravelStampDatabase
import com.example.data.local.entity.TripEntity
import com.example.data.model.TravelStamp
import com.example.data.model.TripStatus
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepositoryImpl
import com.example.ui.util.StampExporter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: TravelStampDatabase
    private lateinit var tripRepo: TripRepositoryImpl
    private lateinit var stampRepo: TravelStampRepositoryImpl

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tripRepo = TripRepositoryImpl(db.tripDao())
        stampRepo = TravelStampRepositoryImpl(db.travelStampDao())
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

        // 5. Create and complete Trip 4: Brahmagiri (Past or Today's date)
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

        // 6. Finishing Trip 2 again must NOT generate a new stamp
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
}
