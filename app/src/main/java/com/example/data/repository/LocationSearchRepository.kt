package com.example.data.repository

import com.example.data.model.LocationSearchResult

interface LocationSearchRepository {
    suspend fun searchLocations(query: String): LocationSearchResult
}
