package com.example.data.datasource

import com.example.data.model.LocationSearchCandidate
import com.example.data.model.LocationSearchResult
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class ProxySearchResponse(
    @Json(name = "candidates") val candidates: List<ProxyCandidate>?
)

@JsonClass(generateAdapter = true)
data class ProxyCandidate(
    @Json(name = "label") val label: String?,
    @Json(name = "secondaryLabel") val secondaryLabel: String?,
    @Json(name = "latitude") val latitude: Double?,
    @Json(name = "longitude") val longitude: Double?,
    @Json(name = "category") val category: String?
)

interface ProxyLocationSearchService {
    @GET("v1/places/search")
    suspend fun search(
        @Query("q") query: String
    ): Response<ProxySearchResponse>
}

class ProxyLocationSearchDataSource(
    private val searchService: ProxyLocationSearchService
) : LocationSearchDataSource {

    override suspend fun search(query: String): LocationSearchResult {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return LocationSearchResult.InvalidQuery
        }

        try {
            val response = searchService.search(trimmedQuery)

            if (response.isSuccessful) {
                val body = response.body()
                val candidatesList = body?.candidates
                if (candidatesList.isNullOrEmpty()) {
                    return LocationSearchResult.NoResults
                }

                val candidates = candidatesList.mapNotNull { result ->
                    val lat = result.latitude
                    val lon = result.longitude

                    if (lat == null || lon == null) return@mapNotNull null
                    if (lat.isNaN() || lat.isInfinite() || lat !in -90.0..90.0) return@mapNotNull null
                    if (lon.isNaN() || lon.isInfinite() || lon !in -180.0..180.0) return@mapNotNull null

                    val label = result.label
                    if (label.isNullOrBlank()) return@mapNotNull null

                    LocationSearchCandidate(
                        providerResultId = null,
                        label = label,
                        secondaryLabel = result.secondaryLabel,
                        latitude = lat,
                        longitude = lon,
                        category = result.category
                    )
                }

                if (candidates.isEmpty()) {
                    return LocationSearchResult.NoResults
                }

                return LocationSearchResult.Success(candidates.take(5))
            } else {
                return when (response.code()) {
                    400 -> LocationSearchResult.InvalidQuery
                    429 -> LocationSearchResult.RateLimited
                    502 -> LocationSearchResult.ProviderUnavailable
                    503 -> LocationSearchResult.ProviderUnavailable
                    504 -> LocationSearchResult.Timeout
                    else -> LocationSearchResult.ProviderUnavailable
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            return LocationSearchResult.Timeout
        } catch (e: java.net.UnknownHostException) {
            return LocationSearchResult.NoNetwork
        } catch (e: java.net.ConnectException) {
            return LocationSearchResult.NoNetwork
        } catch (e: java.net.NoRouteToHostException) {
            return LocationSearchResult.NoNetwork
        } catch (e: java.io.IOException) {
            return LocationSearchResult.ProviderUnavailable
        } catch (e: Exception) {
            return LocationSearchResult.UnknownError(e.message ?: "Unknown exception")
        }
    }
}
