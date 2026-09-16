package com.example.data.repository

import com.example.data.datasource.LocationSearchDataSource
import com.example.data.model.LocationSearchResult

class LocationSearchRepositoryImpl(
    private val dataSource: LocationSearchDataSource
) : LocationSearchRepository {

    override suspend fun searchLocations(query: String): LocationSearchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return LocationSearchResult.InvalidQuery
        }
        if (trimmed.length > 250) {
            return LocationSearchResult.InvalidQuery
        }
        return dataSource.search(trimmed)
    }
}
