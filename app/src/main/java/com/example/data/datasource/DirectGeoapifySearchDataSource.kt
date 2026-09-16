package com.example.data.datasource

import com.example.data.model.LocationSearchCandidate
import com.example.data.model.LocationSearchResult
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class GeoapifyResponse(
    @Json(name = "results") val results: List<GeoapifyResult>?
)

@JsonClass(generateAdapter = true)
data class GeoapifyResult(
    @Json(name = "place_id") val placeId: String?,
    @Json(name = "formatted") val formatted: String?,
    @Json(name = "address_line1") val addressLine1: String?,
    @Json(name = "address_line2") val addressLine2: String?,
    @Json(name = "lat") val lat: Double?,
    @Json(name = "lon") val lon: Double?,
    @Json(name = "category") val category: String?
)

interface GeoapifyService {
    @GET("v1/geocode/search")
    suspend fun search(
        @Query("text") text: String,
        @Query("filter") filter: String,
        @Query("limit") limit: Int,
        @Query("apiKey") apiKey: String
    ): Response<GeoapifyResponse>
}

class DirectGeoapifySearchDataSource(
    private val geoapifyService: GeoapifyService,
    private val config: GeoapifyConfig
) : LocationSearchDataSource {

    override suspend fun search(query: String): LocationSearchResult {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return LocationSearchResult.InvalidQuery
        }

        try {
            val response = geoapifyService.search(
                text = trimmedQuery,
                filter = "countrycode:in",
                limit = 5,
                apiKey = config.apiKey
            )

            if (response.isSuccessful) {
                val body = response.body()
                val resultsList = body?.results
                if (resultsList.isNullOrEmpty()) {
                    return LocationSearchResult.NoResults
                }

                val candidates = resultsList.mapNotNull { result ->
                    val lat = result.lat
                    val lon = result.lon

                    if (lat == null || lon == null) return@mapNotNull null
                    if (lat.isNaN() || lat.isInfinite() || lat !in -90.0..90.0) return@mapNotNull null
                    if (lon.isNaN() || lon.isInfinite() || lon !in -180.0..180.0) return@mapNotNull null

                    val label = result.addressLine1 ?: result.formatted
                    if (label.isNullOrBlank()) return@mapNotNull null

                    LocationSearchCandidate(
                        providerResultId = result.placeId,
                        label = label,
                        secondaryLabel = result.addressLine2,
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
                    401, 403 -> LocationSearchResult.ProviderUnavailable
                    429 -> LocationSearchResult.RateLimited
                    in 500..599 -> LocationSearchResult.ProviderUnavailable
                    else -> LocationSearchResult.UnknownError("HTTP error: ${response.code()}")
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            return LocationSearchResult.Timeout
        } catch (e: java.io.IOException) {
            return LocationSearchResult.NoNetwork
        } catch (e: Exception) {
            return LocationSearchResult.UnknownError(e.message ?: "Unknown exception")
        }
    }
}
