package com.example.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.ProxyLocationSearchDataSource
import com.example.data.datasource.ProxyLocationSearchService
import com.example.data.datasource.ProxySearchResponse
import com.example.data.datasource.ProxyCandidate
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

class FakeProxyLocationSearchService : ProxyLocationSearchService {
    var invocationCount = 0
    var lastQuery: String? = null

    var responseToReturn: Response<ProxySearchResponse>? = null
    var exceptionToThrow: Exception? = null

    override suspend fun search(query: String): Response<ProxySearchResponse> {
        invocationCount++
        lastQuery = query

        exceptionToThrow?.let { throw it }
        return responseToReturn ?: Response.success(ProxySearchResponse(emptyList()))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationSearchTest {

    private lateinit var db: TravelStampDatabase
    private lateinit var fakeService: FakeProxyLocationSearchService
    private lateinit var repo: LocationSearchRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TravelStampDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        fakeService = FakeProxyLocationSearchService()
        val dataSource = ProxyLocationSearchDataSource(fakeService)
        repo = LocationSearchRepositoryImpl(dataSource)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testBlankQueryRejectedLocally() = runBlocking {
        // 9. blank query -> no network request (tested empty and spaces)
        val result1 = repo.searchLocations("   ")
        assertEquals(LocationSearchResult.InvalidQuery, result1)

        val result2 = repo.searchLocations("")
        assertEquals(LocationSearchResult.InvalidQuery, result2)

        assertEquals(0, fakeService.invocationCount)
    }

    @Test
    fun testWhitespaceTrimmed() = runBlocking {
        fakeService.responseToReturn = Response.success(
            ProxySearchResponse(
                listOf(
                    ProxyCandidate(
                        label = "Harihar Fort",
                        secondaryLabel = "Maharashtra, India",
                        latitude = 19.9046,
                        longitude = 73.4719,
                        category = "fort"
                    )
                )
            )
        )

        val result = repo.searchLocations("  Harihar Fort  ")
        assertTrue(result is LocationSearchResult.Success)
        assertEquals("Harihar Fort", fakeService.lastQuery)
    }

    @Test
    fun testSuccessfulResponseMapsCandidates() = runBlocking {
        // 1. proxy 200 + candidates -> Success with correct order/fields
        // 11. response requires no provider/place ID
        fakeService.responseToReturn = Response.success(
            ProxySearchResponse(
                listOf(
                    ProxyCandidate(
                        label = "Harihar Fort",
                        secondaryLabel = "Nashik, India",
                        latitude = 19.9046,
                        longitude = 73.4719,
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
        assertNull(candidate.providerResultId) // Must be null as proxy response contains no place ID
        assertEquals("Harihar Fort", candidate.label)
        assertEquals("Nashik, India", candidate.secondaryLabel)
        assertEquals(19.9046, candidate.latitude, 0.0001)
        assertEquals(73.4719, candidate.longitude, 0.0001)
        assertEquals("natural.mountain", candidate.category)
    }

    @Test
    fun testMaximum5CandidatesExposed() = runBlocking {
        // 10. response >5 candidates -> never expose >5 if client defensive cap exists
        val sixResults = (1..6).map { i ->
            ProxyCandidate(
                label = "Place $i",
                secondaryLabel = "Context $i",
                latitude = 19.0 + (i * 0.01),
                longitude = 73.0 + (i * 0.01),
                category = "test"
            )
        }
        fakeService.responseToReturn = Response.success(ProxySearchResponse(sixResults))

        val result = repo.searchLocations("Test Limit")
        assertTrue(result is LocationSearchResult.Success)
        val candidates = (result as LocationSearchResult.Success).candidates
        assertEquals(5, candidates.size)
        assertEquals("Place 1", candidates[0].label)
        assertEquals("Place 5", candidates[4].label)
    }

    @Test
    fun testProviderRankingOrderPreserved() = runBlocking {
        // 1. proxy 200 + candidates -> Success with correct order/fields
        val resultsInOrder = listOf(
            ProxyCandidate("Three", "C3", 19.0, 73.0, "test"),
            ProxyCandidate("One", "C1", 19.1, 73.1, "test"),
            ProxyCandidate("Two", "C2", 19.2, 73.2, "test")
        )
        fakeService.responseToReturn = Response.success(ProxySearchResponse(resultsInOrder))

        val result = repo.searchLocations("Rank test")
        assertTrue(result is LocationSearchResult.Success)
        val candidates = (result as LocationSearchResult.Success).candidates
        assertEquals(3, candidates.size)
        assertEquals("Three", candidates[0].label)
        assertEquals("One", candidates[1].label)
        assertEquals("Two", candidates[2].label)
    }

    @Test
    fun testEmptyResponseNoResults() = runBlocking {
        // 2. proxy 200 + empty candidates -> NoResults
        fakeService.responseToReturn = Response.success(ProxySearchResponse(emptyList()))
        val result = repo.searchLocations("No exist")
        assertEquals(LocationSearchResult.NoResults, result)
    }

    @Test
    fun testMalformedCandidateSkipped() = runBlocking {
        fakeService.responseToReturn = Response.success(
            ProxySearchResponse(
                listOf(
                    ProxyCandidate("Valid", "C", 19.0, 73.0, null),
                    ProxyCandidate(null, null, 19.1, 73.1, null), // malformed: no label
                    ProxyCandidate("Valid 2", "C", 19.2, 73.2, null)
                )
            )
        )

        val result = repo.searchLocations("Skip malformed")
        assertTrue(result is LocationSearchResult.Success)
        val candidates = (result as LocationSearchResult.Success).candidates
        assertEquals(2, candidates.size)
        assertEquals("Valid", candidates[0].label)
        assertEquals("Valid 2", candidates[1].label)
    }

    @Test
    fun testInvalidCoordinateCandidateRejected() = runBlocking {
        fakeService.responseToReturn = Response.success(
            ProxySearchResponse(
                listOf(
                    ProxyCandidate("NaN Lat", "C", Double.NaN, 73.0, null),
                    ProxyCandidate("Inf Lon", "C", 19.0, Double.POSITIVE_INFINITY, null),
                    ProxyCandidate("Out Range", "C", 95.0, 73.0, null),
                    ProxyCandidate("Valid", "C", 19.0, 73.0, null)
                )
            )
        )

        val result = repo.searchLocations("Coordinates")
        assertTrue(result is LocationSearchResult.Success)
        val candidates = (result as LocationSearchResult.Success).candidates
        assertEquals(1, candidates.size)
        assertEquals("Valid", candidates[0].label)
    }

    @Test
    fun testOptionalCategoryMissingDoesNotCrash() = runBlocking {
        fakeService.responseToReturn = Response.success(
            ProxySearchResponse(
                listOf(
                    ProxyCandidate("No Category", "C", 19.0, 73.0, null)
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
    fun testInvalidQuery400() = runBlocking {
        // 3. 400 -> InvalidQuery
        fakeService.responseToReturn = Response.error(
            400,
            "Invalid Query".toResponseBody("application/json".toMediaTypeOrNull())
        )

        val result = repo.searchLocations("Invalid")
        assertEquals(LocationSearchResult.InvalidQuery, result)
    }

    @Test
    fun testRateLimited429() = runBlocking {
        // 4. 429 -> RateLimited
        fakeService.responseToReturn = Response.error(
            429,
            "Rate limit exceeded".toResponseBody("application/json".toMediaTypeOrNull())
        )

        val result = repo.searchLocations("Rate limit")
        assertEquals(LocationSearchResult.RateLimited, result)
    }

    @Test
    fun testProviderUnavailable502() = runBlocking {
        // 5. 502 -> ProviderUnavailable
        fakeService.responseToReturn = Response.error(
            502,
            "Bad Gateway".toResponseBody("application/json".toMediaTypeOrNull())
        )

        val result = repo.searchLocations("Server Error 502")
        assertEquals(LocationSearchResult.ProviderUnavailable, result)
    }

    @Test
    fun testProviderUnavailable503() = runBlocking {
        // 5. 503 -> ProviderUnavailable
        fakeService.responseToReturn = Response.error(
            503,
            "Service Unavailable".toResponseBody("application/json".toMediaTypeOrNull())
        )

        val result = repo.searchLocations("Server Error 503")
        assertEquals(LocationSearchResult.ProviderUnavailable, result)
    }

    @Test
    fun testTimeout504() = runBlocking {
        // 6. 504 -> Timeout
        fakeService.responseToReturn = Response.error(
            504,
            "Gateway Timeout".toResponseBody("application/json".toMediaTypeOrNull())
        )

        val result = repo.searchLocations("Timeout 504")
        assertEquals(LocationSearchResult.Timeout, result)
    }

    @Test
    fun testSocketTimeoutException() = runBlocking {
        // 8. SocketTimeout -> Timeout
        fakeService.exceptionToThrow = SocketTimeoutException("Read timed out")

        val result = repo.searchLocations("Timeout")
        assertEquals(LocationSearchResult.Timeout, result)
    }

    @Test
    fun testUnknownHostExceptionMapsToNoNetwork() = runBlocking {
        // 7. UnknownHost/Connect/NoRouteToHost -> NoNetwork
        fakeService.exceptionToThrow = java.net.UnknownHostException("Unable to resolve host")

        val result = repo.searchLocations("Query")
        assertEquals(LocationSearchResult.NoNetwork, result)
    }

    @Test
    fun testConnectExceptionMapsToNoNetwork() = runBlocking {
        // 7. UnknownHost/Connect/NoRouteToHost -> NoNetwork
        fakeService.exceptionToThrow = java.net.ConnectException("Connection refused")

        val result = repo.searchLocations("Query")
        assertEquals(LocationSearchResult.NoNetwork, result)
    }

    @Test
    fun testNoRouteToHostExceptionMapsToNoNetwork() = runBlocking {
        // 7. UnknownHost/Connect/NoRouteToHost -> NoNetwork
        fakeService.exceptionToThrow = java.net.NoRouteToHostException("No route to host")

        val result = repo.searchLocations("Query")
        assertEquals(LocationSearchResult.NoNetwork, result)
    }

    @Test
    fun testGenericIOExceptionMapsToProviderUnavailable() = runBlocking {
        fakeService.exceptionToThrow = java.io.IOException("Disk read error or broken pipe")

        val result = repo.searchLocations("Query")
        assertEquals(LocationSearchResult.ProviderUnavailable, result)
    }

    @Test
    fun testMalformedJsonException() = runBlocking {
        fakeService.exceptionToThrow = IllegalArgumentException("Malformed JSON adapter crash")

        val result = repo.searchLocations("Malformed")
        assertTrue(result is LocationSearchResult.UnknownError)
        assertEquals("Malformed JSON adapter crash", (result as LocationSearchResult.UnknownError).message)
    }

    @Test
    fun testResultOneIsNeverAutomaticallyPersisted() = runBlocking {
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
            ProxySearchResponse(
                listOf(
                    ProxyCandidate("Fort A", "Fort A", 19.0, 73.0, "fort"),
                    ProxyCandidate("Fort B", "Fort B", 19.1, 73.1, "fort")
                )
            )
        )

        val searchResult = repo.searchLocations("Forts")
        assertTrue(searchResult is LocationSearchResult.Success)

        val locations = db.journeyLocationDao().getLocationsForTripSync(tripId)
        assertTrue(locations.isEmpty())

        val trips = db.tripDao().getTripByIdSync(tripId)
        assertNotNull(trips)

        val stamps = db.travelStampDao().getStampForTripSync(tripId)
        assertNotNull(stamps)
    }
}
