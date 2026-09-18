package com.example.data.util

import com.example.data.model.JourneyLocation
import com.example.data.model.TravelStamp
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class InkMapDataMapperTest {

    private fun createTestStamp(
        id: Long,
        tripId: Long,
        stampNumber: Long = 1L,
        inkColorHex: String = "#1E3A2F",
        title: String = "Stamp Title",
        destination: String = "Destination"
    ) = TravelStamp(
        id = id,
        tripId = tripId,
        stampNumber = stampNumber,
        stampCode = "#" + String.format("%03d", stampNumber),
        title = title,
        destination = destination,
        dateText = "2026-09-18",
        peopleCount = 1,
        momentsCount = 0,
        inkColorHex = inkColorHex
    )

    private fun createTestLocation(
        id: Long,
        tripId: Long,
        label: String = "Location Label",
        latitude: Double = 15.0,
        longitude: Double = 75.0,
        sortOrder: Int = 0,
        uuid: String = UUID.randomUUID().toString()
    ) = JourneyLocation(
        id = id,
        uuid = uuid,
        tripId = tripId,
        label = label,
        latitude = latitude,
        longitude = longitude,
        sortOrder = sortOrder
    )

    @Test
    fun test_1_emptyStampsAndEmptyLocationsProducesEmptyData() {
        val result = InkMapDataMapper.mapInkMapData(emptyList(), emptyList())
        assertTrue(result.points.isEmpty())
        assertTrue(result.missingLocations.isEmpty())
    }

    @Test
    fun test_2_actualStampAndOneValidLocationProducesOnePoint() {
        val stamp = createTestStamp(id = 1L, tripId = 10L, stampNumber = 5L)
        val location = createTestLocation(id = 100L, tripId = 10L, latitude = 12.34, longitude = 56.78)

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(location))

        assertEquals(1, result.points.size)
        assertTrue(result.missingLocations.isEmpty())

        val pt = result.points[0]
        assertEquals(10L, pt.journeyId)
        assertEquals(1L, pt.stampId)
        assertEquals(5L, pt.stampNumber)
        assertEquals(100L, pt.locationId)
        assertEquals(12.34, pt.latitude, 1e-9)
        assertEquals(56.78, pt.longitude, 1e-9)
    }

    @Test
    fun test_3_actualStampAndMultipleValidLocationsProducesMultiplePoints() {
        val stamp = createTestStamp(id = 1L, tripId = 10L, stampNumber = 5L)
        val loc1 = createTestLocation(id = 100L, tripId = 10L, latitude = 10.0, longitude = 20.0, sortOrder = 1)
        val loc2 = createTestLocation(id = 101L, tripId = 10L, latitude = 11.0, longitude = 21.0, sortOrder = 2)

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc1, loc2))

        assertEquals(2, result.points.size)
        assertTrue(result.missingLocations.isEmpty())
        assertEquals(100L, result.points[0].locationId)
        assertEquals(101L, result.points[1].locationId)
    }

    @Test
    fun test_4_multipleLocationsPreserveOneParentStampIdentity() {
        val stamp = createTestStamp(id = 1L, tripId = 10L, stampNumber = 7L, inkColorHex = "#FF0000")
        val loc1 = createTestLocation(id = 100L, tripId = 10L, sortOrder = 1)
        val loc2 = createTestLocation(id = 101L, tripId = 10L, sortOrder = 2)

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc1, loc2))

        assertEquals(2, result.points.size)
        for (pt in result.points) {
            assertEquals(10L, pt.journeyId)
            assertEquals(1L, pt.stampId)
            assertEquals(stamp.uuid, pt.stampUuid)
            assertEquals(7L, pt.stampNumber)
            assertEquals("#FF0000", pt.inkColorHex)
        }
    }

    @Test
    fun test_5_stampWithZeroLocationsProducesOneMissingLocation() {
        val stamp = createTestStamp(id = 1L, tripId = 10L, stampNumber = 7L, title = "Epic Trip", destination = "Nowhere")
        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), emptyList())

        assertTrue(result.points.isEmpty())
        assertEquals(1, result.missingLocations.size)

        val missing = result.missingLocations[0]
        assertEquals(10L, missing.journeyId)
        assertEquals(1L, missing.stampId)
        assertEquals(stamp.uuid, missing.stampUuid)
        assertEquals(7L, missing.stampNumber)
        assertEquals("Epic Trip", missing.title)
        assertEquals("Nowhere", missing.destination)
    }

    @Test
    fun test_6_stampWithOnlyInvalidLocationsProducesMissingLocation() {
        val stamp = createTestStamp(id = 1L, tripId = 10L)
        val badLoc = createTestLocation(id = 100L, tripId = 10L, latitude = 100.0) // Invalid lat > 90

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(badLoc))

        assertTrue(result.points.isEmpty())
        assertEquals(1, result.missingLocations.size)
    }

    @Test
    fun test_7_stampWithValidAndInvalidLocationsProducesValidPointsOnlyAndNotMissing() {
        val stamp = createTestStamp(id = 1L, tripId = 10L)
        val badLoc = createTestLocation(id = 100L, tripId = 10L, latitude = -100.0) // Invalid
        val goodLoc = createTestLocation(id = 101L, tripId = 10L, latitude = 12.0, longitude = 34.0) // Valid

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(badLoc, goodLoc))

        assertEquals(1, result.points.size)
        assertEquals(101L, result.points[0].locationId)
        assertTrue(result.missingLocations.isEmpty())
    }

    @Test
    fun test_8_orphanLocationWithoutStampIsIgnored() {
        val loc = createTestLocation(id = 100L, tripId = 10L) // No stamp for trip 10
        val result = InkMapDataMapper.mapInkMapData(emptyList(), listOf(loc))

        assertTrue(result.points.isEmpty())
        assertTrue(result.missingLocations.isEmpty())
    }

    @Test
    fun test_9_completedAndStampEarnedConceptIsIrrelevantWhenNoTravelStampExists() {
        // Since pure mapper consumes TravelStamp directly, a trip that might have been marked completed
        // in database but has no TravelStamp entity row is not passed to the mapper at all, thus produces no output.
        // We verify that mapper does not attempt to create anything if stamps is empty.
        val result = InkMapDataMapper.mapInkMapData(emptyList(), emptyList())
        assertTrue(result.points.isEmpty())
        assertTrue(result.missingLocations.isEmpty())
    }

    @Test
    fun test_10_duplicateCoordinatesWithDifferentLocationUuidsAreBothRetained() {
        val stamp = createTestStamp(id = 1L, tripId = 10L)
        val loc1 = createTestLocation(id = 100L, tripId = 10L, latitude = 12.0, longitude = 34.0, uuid = "UUID-A")
        val loc2 = createTestLocation(id = 101L, tripId = 10L, latitude = 12.0, longitude = 34.0, uuid = "UUID-B")

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc1, loc2))

        assertEquals(2, result.points.size)
        assertEquals("UUID-A", result.points[0].locationUuid)
        assertEquals("UUID-B", result.points[1].locationUuid)
    }

    @Test
    fun test_11_invalidLatitudeBelowMinus90Rejected() {
        val stamp = createTestStamp(id = 1L, tripId = 10L)
        val loc = createTestLocation(id = 100L, tripId = 10L, latitude = -90.1)

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc))
        assertTrue(result.points.isEmpty())
        assertEquals(1, result.missingLocations.size)
    }

    @Test
    fun test_12_invalidLatitudeAbove90Rejected() {
        val stamp = createTestStamp(id = 1L, tripId = 10L)
        val loc = createTestLocation(id = 100L, tripId = 10L, latitude = 90.1)

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc))
        assertTrue(result.points.isEmpty())
        assertEquals(1, result.missingLocations.size)
    }

    @Test
    fun test_13_invalidLongitudeBelowMinus180Rejected() {
        val stamp = createTestStamp(id = 1L, tripId = 10L)
        val loc = createTestLocation(id = 100L, tripId = 10L, longitude = -180.1)

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc))
        assertTrue(result.points.isEmpty())
        assertEquals(1, result.missingLocations.size)
    }

    @Test
    fun test_14_invalidLongitudeAbove180Rejected() {
        val stamp = createTestStamp(id = 1L, tripId = 10L)
        val loc = createTestLocation(id = 100L, tripId = 10L, longitude = 180.1)

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc))
        assertTrue(result.points.isEmpty())
        assertEquals(1, result.missingLocations.size)
    }

    @Test
    fun test_15_nanRejected() {
        val stamp = createTestStamp(id = 1L, tripId = 10L)
        val loc1 = createTestLocation(id = 100L, tripId = 10L, latitude = Double.NaN)
        val loc2 = createTestLocation(id = 101L, tripId = 10L, longitude = Double.NaN)

        val result1 = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc1))
        val result2 = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc2))

        assertTrue(result1.points.isEmpty())
        assertTrue(result2.points.isEmpty())
    }

    @Test
    fun test_16_positiveInfinityRejected() {
        val stamp = createTestStamp(id = 1L, tripId = 10L)
        val loc1 = createTestLocation(id = 100L, tripId = 10L, latitude = Double.POSITIVE_INFINITY)
        val loc2 = createTestLocation(id = 101L, tripId = 10L, longitude = Double.POSITIVE_INFINITY)

        val result1 = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc1))
        val result2 = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc2))

        assertTrue(result1.points.isEmpty())
        assertTrue(result2.points.isEmpty())
    }

    @Test
    fun test_17_negativeInfinityRejected() {
        val stamp = createTestStamp(id = 1L, tripId = 10L)
        val loc1 = createTestLocation(id = 100L, tripId = 10L, latitude = Double.NEGATIVE_INFINITY)
        val loc2 = createTestLocation(id = 101L, tripId = 10L, longitude = Double.NEGATIVE_INFINITY)

        val result1 = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc1))
        val result2 = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc2))

        assertTrue(result1.points.isEmpty())
        assertTrue(result2.points.isEmpty())
    }

    @Test
    fun test_18_deterministicStampOrdering() {
        val stamp1 = createTestStamp(id = 1L, tripId = 10L, stampNumber = 5L)
        val stamp2 = createTestStamp(id = 2L, tripId = 20L, stampNumber = 2L)
        val stamp3 = createTestStamp(id = 3L, tripId = 30L, stampNumber = 8L)

        // Shuffled input
        val result = InkMapDataMapper.mapInkMapData(listOf(stamp3, stamp1, stamp2), emptyList())

        // Missing locations should be ordered by stampNumber ASC (2L, 5L, 8L)
        assertEquals(3, result.missingLocations.size)
        assertEquals(2L, result.missingLocations[0].stampNumber)
        assertEquals(5L, result.missingLocations[1].stampNumber)
        assertEquals(8L, result.missingLocations[2].stampNumber)
    }

    @Test
    fun test_19_deterministicLocationOrderingBySortOrderThenLocationId() {
        val stamp = createTestStamp(id = 1L, tripId = 10L)
        val loc1 = createTestLocation(id = 100L, tripId = 10L, sortOrder = 5)
        val loc2 = createTestLocation(id = 101L, tripId = 10L, sortOrder = 2)
        val loc3 = createTestLocation(id = 102L, tripId = 10L, sortOrder = 5) // same sortOrder as loc1 but larger ID

        // Shuffled input
        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc1, loc3, loc2))

        assertEquals(3, result.points.size)
        assertEquals(101L, result.points[0].locationId) // sortOrder 2
        assertEquals(100L, result.points[1].locationId) // sortOrder 5, ID 100
        assertEquals(102L, result.points[2].locationId) // sortOrder 5, ID 102
    }

    @Test
    fun test_20_permutedInputProducesSameOutput() {
        val stamp1 = createTestStamp(id = 1L, tripId = 10L, stampNumber = 2L)
        val stamp2 = createTestStamp(id = 2L, tripId = 20L, stampNumber = 1L)
        val loc1 = createTestLocation(id = 100L, tripId = 10L)
        val loc2 = createTestLocation(id = 101L, tripId = 20L)

        val out1 = InkMapDataMapper.mapInkMapData(listOf(stamp1, stamp2), listOf(loc1, loc2))
        val out2 = InkMapDataMapper.mapInkMapData(listOf(stamp2, stamp1), listOf(loc2, loc1))

        assertEquals(out1.points.size, out2.points.size)
        assertEquals(out1.points[0].locationId, out2.points[0].locationId)
        assertEquals(out1.points[1].locationId, out2.points[1].locationId)
    }

    @Test
    fun test_21_oneStampCannotProduceDuplicatePointOutputAccidentally() {
        val stamp = createTestStamp(id = 1L, tripId = 10L)
        val loc = createTestLocation(id = 100L, tripId = 10L)

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp, stamp), listOf(loc)) // duplicate stamp passed
        assertEquals(1, result.points.size)
    }

    @Test
    fun test_22_multipleStampsWithSeparateLocationsCorrelateCorrectly() {
        val stamp1 = createTestStamp(id = 1L, tripId = 10L, stampNumber = 1L)
        val stamp2 = createTestStamp(id = 2L, tripId = 20L, stampNumber = 2L)
        val loc1 = createTestLocation(id = 100L, tripId = 10L)
        val loc2 = createTestLocation(id = 101L, tripId = 20L)

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp1, stamp2), listOf(loc1, loc2))

        assertEquals(2, result.points.size)
        assertEquals(10L, result.points[0].journeyId)
        assertEquals(20L, result.points[1].journeyId)
    }

    @Test
    fun test_23_locationBelongingToAnotherStampNeverCrossAssociates() {
        val stamp1 = createTestStamp(id = 1L, tripId = 10L, stampNumber = 1L)
        val stamp2 = createTestStamp(id = 2L, tripId = 20L, stampNumber = 2L)
        val loc = createTestLocation(id = 100L, tripId = 20L) // only belongs to stamp2

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp1, stamp2), listOf(loc))

        assertEquals(1, result.points.size)
        assertEquals(20L, result.points[0].journeyId) // belongs to stamp2
        assertEquals(1, result.missingLocations.size)
        assertEquals(10L, result.missingLocations[0].journeyId) // stamp1 has no locations
    }

    @Test
    fun test_24_stampIdentityFieldsArePreservedExactly() {
        val stamp = createTestStamp(id = 123L, tripId = 456L, stampNumber = 789L, title = "Himalayan Ridge", destination = "Manali")
        val loc = createTestLocation(id = 100L, tripId = 456L)

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc))

        assertEquals(1, result.points.size)
        val pt = result.points[0]
        assertEquals(123L, pt.stampId)
        assertEquals(stamp.uuid, pt.stampUuid)
        assertEquals(789L, pt.stampNumber)
    }

    @Test
    fun test_25_inkColorHexPreservedExactly() {
        val stamp = createTestStamp(id = 1L, tripId = 10L, inkColorHex = "#4A90E2")
        val loc = createTestLocation(id = 100L, tripId = 10L)

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp), listOf(loc))

        assertEquals(1, result.points.size)
        assertEquals("#4A90E2", result.points[0].inkColorHex)
    }

    @Test
    fun test_duplicateStampTripIdCorruptionHandledDeterministically() {
        // If DB contains duplicate stamps for the same tripId, keep only the lowest stampNumber or first encountered
        val stamp1 = createTestStamp(id = 1L, tripId = 10L, stampNumber = 5L)
        val stamp2 = createTestStamp(id = 2L, tripId = 10L, stampNumber = 2L) // duplicate tripId 10!
        val loc = createTestLocation(id = 100L, tripId = 10L)

        val result = InkMapDataMapper.mapInkMapData(listOf(stamp1, stamp2), listOf(loc))

        // We expect only 1 point, backed by the stamp with stampNumber = 2
        assertEquals(1, result.points.size)
        assertEquals(2L, result.points[0].stampNumber)
    }
}
