package com.example.data.datasource

import com.example.data.model.LocationSearchResult

interface LocationSearchDataSource {
    suspend fun search(query: String): LocationSearchResult
}
