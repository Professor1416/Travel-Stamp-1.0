package com.example.data.model

sealed interface LocationSearchResult {
    data class Success(val candidates: List<LocationSearchCandidate>) : LocationSearchResult
    object NoResults : LocationSearchResult
    object NoNetwork : LocationSearchResult
    object Timeout : LocationSearchResult
    object RateLimited : LocationSearchResult
    object ProviderUnavailable : LocationSearchResult
    object InvalidQuery : LocationSearchResult
    data class UnknownError(val message: String?) : LocationSearchResult
}
