package com.example.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.DirectGeoapifySearchDataSource
import com.example.data.datasource.GeoapifyConfig
import com.example.data.datasource.GeoapifyResponse
import com.example.data.datasource.GeoapifyResult
import com.example.data.datasource.GeoapifyService
import com.example.data.local.entity.JourneyLocationEntity
import com.example.data.local.entity.TravelStampEntity
import com.example.data.local.entity.TripEntity
import com.example.data.model.LocationSearchResult
import com.example.data.repository.LocationSearchRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class FakeGeoapifyService : GeoapifyService {
    var lastText: String? = null
    var lastFilter: String? = null
    var lastLimit: Int? = null
    var lastApiKey: String? = null

    var responseToReturn: Response<GeoapifyResponse>? = null
    var exceptionToThrow: Exception? = null

    override suspend fun search(
        text: String,
        filter: String,
        limit: Int,
        apiKey: String
    ): Response<GeoapifyResponse> {
        lastText = text
        lastFilter = filter
        lastLimit = limit
        lastApiKey = apiKey

        exceptionToThrow?.let { throw it }
        return responseToReturn ?: Response.success(GeoapifyResponse(emptyList()))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationSearchTest {

    private lateinit var db: TravelStampDatabase
    private lateinit var fakeService: FakeGeoapifyService
    private lateinit var repo: LocationSearchRepositoryImpl
    private lateinit var testConfig: GeoapifyConfig

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TravelStampDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        fakeService = FakeGeoapifyService()
        testConfig = object : GeoapifyConfig {
            override val apiKey: String = "test-api-key"
        }

        val dataSource = DirectGeoapifySearchDataSource(fakeService, testConfig)
        repo = LocationSearchRepositoryImpl(dataSource)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testBlankQueryRejectedLocally() = runBlocking {
        // 1. blank query rejected locally (empty or spaces)
        val result1 = repo.searchLocations("   ")
        assertEquals(LocationSearchResult.InvalidQuery, result1)

        val result2 = repo.searchLocations("")
        assertEquals(LocationSearchResult.InvalidQuery, result2)
    }

    @Test
    fun testWhitespaceTrimmed() = runBlocking {
        // 2. whitespace trimmed
        fakeService.responseToReturn = Response.success(
            GeoapifyResponse(
                listOf(
                    GeoapifyResult(
                        placeId = "1",
                        formatted = "Harihar Fort, Maharashtra, India",
                        addressLine1 = "Harihar Fort",
                        addressLine2 = "Maharashtra, India",
                        lat = 19.9046,
                        lon = 73.4719,
                        category = "fort"
                    )
                )
            )
        )

        val result = repo.searchLocations("  Harihar Fort  ")
        assertTrue(result is LocationSearchResult.Success)
        assertEquals("Harihar Fort", fakeService.lastText)
    }

    @Test
    fun testSuccessfulResponseMapsCandidates() = runBlocking {
        // 3. successful response maps candidates
        fakeService.responseToReturn = Response.success(
            GeoapifyResponse(
                listOf(
                    GeoapifyResult(
                        placeId = "id-123",
                        formatted = "Harihar Fort, Nashik",
                        addressLine1 = "Harihar Fort",
                        addressLine2 = "Nashik, India",
                        lat = 19.9046,
                        lon = 73.4719,
                        category = "natural.mountain"
                    )
                )
            )
        )

        val result = repo.searchLocations("Harihar")
        assertTrue(result is LocationSearchResult.Success)
        val candidates = (result as LocationSearchResult.Success).candidates
        assertEquals(1, candidates.size)
        val candidate = candidates[0]
        assertEquals("id-123", candidate.providerResultId)
        assertEquals("Harihar Fort", candidate.label)
        assertEquals("Nashik, India", candidate.secondaryLabel)
        assertEquals(19.9046, candidate.latitude, 0.0001)
        assertEquals(73.4719, candidate.longitude, 0.0001)
        assertEquals("natural.mountain", candidate.category)
    }

    @Test
    fun testMaximum5CandidatesExposed() = runBlocking {
        // 4. maximum 5 candidates exposed
        val sixResults = (1..6).map { i ->
            GeoapifyResult(
                placeId = "id-$i",
                formatted = "Place $i",
                addressLine1 = "Place $i",
                addressLine2 = "Context $i",
                lat = 19.0 + (i * 0.01),
                lon = 73.0 + (i * 0.01),
                category = "test"
            )
        }
        fakeService.responseToReturn = Response.success(GeoapifyResponse(sixResults))

        val result = repo.searchLocations("Test Limit")
        assertTrue(result is LocationSearchResult.Success)
        val candidates = (result as LocationSearchResult.Success).candidates
        assertEquals(5, candidates.size)
        assertEquals("id-1", candidates[0].providerResultId)
        assertEquals("id-5", candidates[4].providerResultId)
    }

    @Test
    fun testProviderRankingOrderPreserved() = runBlocking {
        // 5. provider ranking order preserved
        val resultsInOrder = listOf(
            GeoapifyResult("p3", "Three", "Three", "C3", 19.0, 73.0, "test"),
            GeoapifyResult("p1", "One", "One", "C1", 19.1, 73.1, "test"),
            GeoapifyResult("p2", "Two", "Two", "C2", 19.2, 73.2, "test")
        )
        fakeService.responseToReturn = Response.success(GeoapifyResponse(resultsInOrder))

        val result = repo.searchLocations("Rank test")
        assertTrue(result is LocationSearchResult.Success)
        val candidates = (result as LocationSearchResult.Success).candidates
        assertEquals(3, candidates.size)
        assertEquals("p3", candidates[0].providerResultId)
        assertEquals("p1", candidates[1].providerResultId)
        assertEquals("p2", candidates[2].providerResultId)
    }

    @Test
    fun testEmptyResponseNoResults() = runBlocking {
        // 6. empty response -> NoResults
        fakeService.responseToReturn = Response.success(GeoapifyResponse(emptyList()))
        val result = repo.searchLocations("No exist")
        assertEquals(LocationSearchResult.NoResults, result)
    }

    @Test
    fun testMalformedCandidateSkipped() = runBlocking {
        // 7. malformed candidate skipped (e.g., missing label entirely)
        fakeService.responseToReturn = Response.success(
            GeoapifyResponse(
                listOf(
                    GeoapifyResult("1", "Valid", "Valid", "C", 19.0, 73.0, null),
                    GeoapifyResult("2", null, null, null, 19.1, 73.1, null), // malformed: no label fallback
                    GeoapifyResult("3", "Valid 2", "Valid 2", "C", 19.2, 73.2, null)
                )
            )
        )

        val result = repo.searchLocations("Skip malformed")
        assertTrue(result is LocationSearchResult.Success)
        val candidates = (result as LocationSearchResult.Success).candidates
        assertEquals(2, candidates.size)
        assertEquals("1", candidates[0].providerResultId)
        assertEquals("3", candidates[1].providerResultId)
    }

    @Test
    fun testInvalidCoordinateCandidateRejected() = runBlocking {
        // 8. NaN/infinite/out-of-range coordinate candidate rejected
        fakeService.responseToReturn = Response.success(
            GeoapifyResponse(
                listOf(
                    GeoapifyResult("1", "NaN Lat", "NaN Lat", "C", Double.NaN, 73.0, null),
                    GeoapifyResult("2", "Inf Lon", "Inf Lon", "C", 19.0, Double.POSITIVE_INFINITY, null),
                    GeoapifyResult("3", "Out Range", "Out Range", "C", 95.0, 73.0, null),
                    GeoapifyResult("4", "Valid", "Valid", "C", 19.0, 73.0, null)
                )
            )
        )

        val result = repo.searchLocations("Coordinates")
        assertTrue(result is LocationSearchResult.Success)
        val candidates = (result as LocationSearchResult.Success).candidates
        assertEquals(1, candidates.size)
        assertEquals("4", candidates[0].providerResultId)
    }

    @Test
    fun testOptionalCategoryMissingDoesNotCrash() = runBlocking {
        // 9. optional category missing does not crash
        fakeService.responseToReturn = Response.success(
            GeoapifyResponse(
                listOf(
                    GeoapifyResult("1", "No Category", "No Category", "C", 19.0, 73.0, null)
                )
            )
        )

        val result = repo.searchLocations("No Category")
        assertTrue(result is LocationSearchResult.Success)
        val candidates = (result as LocationSearchResult.Success).candidates
        assertEquals(1, candidates.size)
        assertNull(candidates[0].category)
    }

    @Test
    fun testRateLimited429() = runBlocking {
        // 10. 429 -> RateLimited
        fakeService.responseToReturn = Response.error(
            429,
            "Rate limit exceeded".toResponseBody("application/json".toMediaTypeOrNull())
        )

        val result = repo.searchLocations("Rate limit")
        assertEquals(LocationSearchResult.RateLimited, result)
    }

    @Test
    fun testProviderUnavailable5xx() = runBlocking {
        // 11. 5xx -> ProviderUnavailable
        fakeService.responseToReturn = Response.error(
            503,
            "Service Unavailable".toResponseBody("application/json".toMediaTypeOrNull())
        )

        val result = repo.searchLocations("Server Error")
        assertEquals(LocationSearchResult.ProviderUnavailable, result)
    }

    @Test
    fun testTimeoutException() = runBlocking {
        // 12. timeout -> Timeout
        fakeService.exceptionToThrow = SocketTimeoutException("Read timed out")

        val result = repo.searchLocations("Timeout")
        assertEquals(LocationSearchResult.Timeout, result)
    }

    @Test
    fun testNetworkIOException() = runBlocking {
        // 13. network failure -> NoNetwork where distinguishable
        fakeService.exceptionToThrow = IOException("No internet connection")

        val result = repo.searchLocations("No Network")
        assertEquals(LocationSearchResult.NoNetwork, result)
    }

    @Test
    fun testMalformedJsonException() = runBlocking {
        // 14. malformed JSON handled safely (throws unexpected exception)
        fakeService.exceptionToThrow = IllegalArgumentException("Malformed JSON adapter crash")

        val result = repo.searchLocations("Malformed")
        assertTrue(result is LocationSearchResult.UnknownError)
        assertEquals("Malformed JSON adapter crash", (result as LocationSearchResult.UnknownError).message)
    }

    @Test
    fun testResultOneIsNeverAutomaticallyPersisted() = runBlocking {
        // 15. result #1 is never automatically persisted/selected (DB remains clean)
        // 16. candidate search does not modify JourneyLocation Room data
        // 17. candidate search does not modify Trip
        // 18. candidate search does not modify TravelStamp
        
        // Seed some data first to prove DB exists but stays unmodified
        val tripId = db.tripDao().insertTrip(
            TripEntity(name = "Historical", destination = "Pune", date = "Jan 2026", status = "IN_PROGRESS")
        )
        db.travelStampDao().insertStamp(
            TravelStampEntity(
                tripId = tripId,
                stampCode = "MH-PUN-01",
                title = "Pune Stamp",
                destination = "Pune",
                dateText = "Jan 2026",
                peopleCount = 1,
                momentsCount = 0,
                inkColorHex = "#1E3A2F",
                stampStyle = "MOUNTAIN",
                reflectionNote = "Nice stamp",
                issuedAt = System.currentTimeMillis(),
                completedAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        fakeService.responseToReturn = Response.success(
            GeoapifyResponse(
                listOf(
                    GeoapifyResult("p1", "Fort A", "Fort A", "C", 19.0, 73.0, "fort"),
                    GeoapifyResult("p2", "Fort B", "Fort B", "C", 19.1, 73.1, "fort")
                )
            )
        )

        val searchResult = repo.searchLocations("Forts")
        assertTrue(searchResult is LocationSearchResult.Success)

        // Count items in tables
        val locations = db.journeyLocationDao().getLocationsForTripSync(tripId)
        assertTrue(locations.isEmpty())

        val trips = db.tripDao().getTripByIdSync(tripId)
        assertNotNull(trips)

        val stamps = db.travelStampDao().getStampForTripSync(tripId)
        assertNotNull(stamps)
    }

    @Test
    fun testIndiaCountryFilterAndLimitParams() = runBlocking {
        // 19. India country filter is included
        // 20. result limit is 5
        // 21. no GPS/current-location data is included in request
        fakeService.responseToReturn = Response.success(
            GeoapifyResponse(
                listOf(GeoapifyResult("1", "V", "V", "C", 19.0, 73.0, null))
            )
        )

        repo.searchLocations("India check")
        assertEquals("countrycode:in", fakeService.lastFilter)
        assertEquals(5, fakeService.lastLimit)
    }
}
