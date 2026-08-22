package com.example.data.repository

import com.example.data.datasource.BundledSuggestionSource
import com.example.data.datasource.BundledSuggestionSourceImpl
import com.example.data.datasource.UserHistorySuggestionSource
import com.example.data.datasource.UserHistorySuggestionSourceImpl
import com.example.data.model.LocationSuggestion

interface LocationSuggestionRepository {
    suspend fun searchSuggestions(query: String, maxResults: Int = 5): List<LocationSuggestion>
}

class LocationSuggestionRepositoryImpl(
    private val userHistorySource: UserHistorySuggestionSource,
    private val bundledSource: BundledSuggestionSource = BundledSuggestionSourceImpl()
) : LocationSuggestionRepository {

    constructor(tripRepository: TripRepository) : this(
        userHistorySource = UserHistorySuggestionSourceImpl(tripRepository),
        bundledSource = BundledSuggestionSourceImpl()
    )

    override suspend fun searchSuggestions(query: String, maxResults: Int): List<LocationSuggestion> {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) {
            return emptyList()
        }

        val queryLower = cleanQuery.lowercase()
        val historyLocations = userHistorySource.getHistoryLocations()
        val curatedLocations = bundledSource.getCuratedLocations()

        // Match and compute rank score for an item
        fun computeScore(item: LocationSuggestion): Int {
            val nameLower = item.name.trim().lowercase()
            val destLower = item.destination.trim().lowercase()
            val nameWords = nameLower.split("\\s+".toRegex())
            val destWords = destLower.split("\\s+".toRegex())
            val aliasesLower = item.aliases.map { it.trim().lowercase() }

            val baseScore = when {
                // 1. Exact match on place name
                nameLower == queryLower -> 1000

                // 2. Place name starts with query
                nameLower.startsWith(queryLower) -> 800

                // 3. Any word in place name starts with query (e.g. "India" in "Gateway of India")
                nameWords.any { it.startsWith(queryLower) } -> 700

                // 4. Exact match in aliases
                aliasesLower.any { it == queryLower } -> 600

                // 5. Alias starts with query
                aliasesLower.any { it.startsWith(queryLower) } -> 500

                // 6. Destination starts with query
                destLower.startsWith(queryLower) -> 400

                // 7. Destination word starts with query
                destWords.any { it.startsWith(queryLower) } -> 350

                // 8. Place name contains query
                nameLower.contains(queryLower) -> 200

                // 9. Alias contains query
                aliasesLower.any { it.contains(queryLower) } -> 150

                // 10. Destination contains query
                destLower.contains(queryLower) -> 100

                else -> 0
            }

            if (baseScore == 0) return 0

            // User history gets a priority bonus so prefix matches in history rank ahead of curated
            val historyBonus = if (item.isFromHistory) 150 else 0
            return baseScore + historyBonus
        }

        val scoredHistory = historyLocations
            .map { it to computeScore(it) }
            .filter { it.second > 0 }

        val scoredCurated = curatedLocations
            .map { it to computeScore(it) }
            .filter { it.second > 0 }

        val allScored = (scoredHistory + scoredCurated)
            .sortedByDescending { it.second }

        // Deduplicate using normalized name (and destination)
        val seenKeys = mutableSetOf<String>()
        val result = mutableListOf<LocationSuggestion>()

        for ((item, _) in allScored) {
            val key = item.name.trim().lowercase()
            if (!seenKeys.contains(key)) {
                seenKeys.add(key)
                result.add(item)
                if (result.size >= maxResults) {
                    break
                }
            }
        }

        return result
    }
}
