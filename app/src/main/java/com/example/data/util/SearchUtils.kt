package com.example.data.util

import com.example.data.model.TravelStamp
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import java.time.LocalDate

/**
 * Supported sort options for Travel Stamps in Passport.
 */
enum class StampSortOption(val displayName: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    STAMP_NUMBER_ASC("Stamp Number: Low → High"),
    STAMP_NUMBER_DESC("Stamp Number: High → Low"),
    MOST_MOMENTS("Most Moments"),
    LEAST_MOMENTS("Least Moments"),
    NAME_ASC("Name: A → Z"),
    NAME_DESC("Name: Z → A")
}

/**
 * Supported sort options for Journeys in Passport.
 */
enum class JourneySortOption(val displayName: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    UPCOMING_FIRST("Upcoming First"),
    COMPLETED_FIRST("Completed First"),
    MOST_MOMENTS("Most Moments"),
    LEAST_MOMENTS("Least Moments"),
    NAME_ASC("Name: A → Z"),
    NAME_DESC("Name: Z → A")
}

/**
 * Supported journey status filters in Passport.
 */
enum class StatusFilter(val displayName: String) {
    ALL("All"),
    UPCOMING("Upcoming"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed")
}

/**
 * Supported moments filters in Passport.
 */
enum class MomentsFilter(val displayName: String) {
    ALL("All"),
    HAS_MOMENTS("Has Moments"),
    NO_MOMENTS("No Moments")
}

/**
 * Supported date period filters in Passport.
 */
enum class DatePeriodFilter(val displayName: String) {
    ALL_TIME("All Time"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year")
}

/**
 * Utility functions for searching, filtering, and sorting Travel Stamps and Expeditions.
 * Supports:
 * - Search by Destination, Location, Stamp/Journey number, Title, Reflection Note
 * - Sort by Newest First, Oldest First, Stamp Number (numerical), Moments count, Name
 * - Filter by Status (All, Upcoming, In Progress, Completed), Moments, Time Period
 */
object SearchUtils {

    /**
     * Checks if a [TravelStamp] matches the given search query.
     */
    fun matchesStamp(stamp: TravelStamp, query: String): Boolean {
        if (query.isBlank()) return true
        val cleanQuery = query.trim().lowercase()

        // Extract potential numeric search intent (e.g., "12", "#12", "stamp 12", "stamp #12", "ts-012")
        val numericQuery = cleanQuery
            .removePrefix("stamp")
            .removePrefix("ts-")
            .removePrefix("ts")
            .trim()
            .removePrefix("#")
            .trim()

        val stampNum = stamp.stampNumber
        val stampNumStr = stampNum.toString()
        val paddedNum = String.format("%03d", stampNum)

        val matchesNumeric = when {
            numericQuery.isNotEmpty() && numericQuery.all { it.isDigit() } -> {
                val searchNum = numericQuery.toLongOrNull()
                stampNum == searchNum || stampNumStr == numericQuery || paddedNum == numericQuery
            }
            else -> false
        }

        if (matchesNumeric) return true

        // Textual matching across title, destination, code, date, reflection note, and inspection text
        return stamp.title.lowercase().contains(cleanQuery) ||
                stamp.destination.lowercase().contains(cleanQuery) ||
                stamp.stampCode.lowercase().contains(cleanQuery) ||
                stamp.dateText.lowercase().contains(cleanQuery) ||
                stamp.inspectionText.lowercase().contains(cleanQuery) ||
                (stamp.reflectionNote?.lowercase()?.contains(cleanQuery) == true)
    }

    /**
     * Checks if a [Trip] (and optionally its associated [TravelStamp]) matches the given search query.
     */
    fun matchesTrip(trip: Trip, associatedStamp: TravelStamp?, query: String): Boolean {
        if (query.isBlank()) return true
        val cleanQuery = query.trim().lowercase()

        // Extract potential numeric search intent
        val numericQuery = cleanQuery
            .removePrefix("journey")
            .removePrefix("trip")
            .removePrefix("stamp")
            .removePrefix("ts-")
            .removePrefix("ts")
            .trim()
            .removePrefix("#")
            .trim()

        val matchesNumericTripId = if (numericQuery.isNotEmpty() && numericQuery.all { it.isDigit() }) {
            val searchNum = numericQuery.toLongOrNull()
            trip.id == searchNum
        } else false

        if (matchesNumericTripId) return true

        // Match base trip fields
        val matchesTripText = trip.name.lowercase().contains(cleanQuery) ||
                trip.destination.lowercase().contains(cleanQuery) ||
                trip.description.lowercase().contains(cleanQuery) ||
                trip.date.lowercase().contains(cleanQuery) ||
                trip.status.name.lowercase().contains(cleanQuery)

        if (matchesTripText) return true

        // Match associated stamp fields if present
        if (associatedStamp != null) {
            return matchesStamp(associatedStamp, query)
        }

        return false
    }

    /**
     * Filters a list of [TravelStamp]s while preserving their existing order.
     */
    fun filterStamps(stamps: List<TravelStamp>, query: String): List<TravelStamp> {
        if (query.isBlank()) return stamps
        return stamps.filter { matchesStamp(it, query) }
    }

    /**
     * Filters a list of [Trip]s while preserving their existing order.
     */
    fun filterTrips(
        trips: List<Trip>,
        stampsMap: Map<Long, TravelStamp>,
        query: String
    ): List<Trip> {
        if (query.isBlank()) return trips
        return trips.filter { matchesTrip(it, stampsMap[it.id], query) }
    }

    /**
     * Sorts a list of [TravelStamp]s based on the selected [StampSortOption].
     */
    fun sortStamps(stamps: List<TravelStamp>, sortOption: StampSortOption): List<TravelStamp> {
        return when (sortOption) {
            StampSortOption.NEWEST_FIRST -> stamps.sortedWith(
                compareByDescending<TravelStamp> { getStampEpoch(it) }
                    .thenByDescending { it.stampNumber }
            )
            StampSortOption.OLDEST_FIRST -> stamps.sortedWith(
                compareBy<TravelStamp> { getStampEpoch(it) }
                    .thenBy { it.stampNumber }
            )
            StampSortOption.STAMP_NUMBER_ASC -> stamps.sortedWith(
                compareBy<TravelStamp> { it.stampNumber }
                    .thenBy { getStampEpoch(it) }
            )
            StampSortOption.STAMP_NUMBER_DESC -> stamps.sortedWith(
                compareByDescending<TravelStamp> { it.stampNumber }
                    .thenByDescending { getStampEpoch(it) }
            )
            StampSortOption.MOST_MOMENTS -> stamps.sortedWith(
                compareByDescending<TravelStamp> { it.momentsCount }
                    .thenByDescending { it.stampNumber }
            )
            StampSortOption.LEAST_MOMENTS -> stamps.sortedWith(
                compareBy<TravelStamp> { it.momentsCount }
                    .thenBy { it.stampNumber }
            )
            StampSortOption.NAME_ASC -> stamps.sortedWith(
                compareBy<TravelStamp> { it.title.lowercase() }
                    .thenBy { it.stampNumber }
            )
            StampSortOption.NAME_DESC -> stamps.sortedWith(
                compareByDescending<TravelStamp> { it.title.lowercase() }
                    .thenByDescending { it.stampNumber }
            )
        }
    }

    /**
     * Sorts a list of [Trip]s based on the selected [JourneySortOption].
     */
    fun sortTrips(
        trips: List<Trip>,
        stampsMap: Map<Long, TravelStamp>,
        sortOption: JourneySortOption
    ): List<Trip> {
        return when (sortOption) {
            JourneySortOption.NEWEST_FIRST -> trips.sortedWith(
                compareByDescending<Trip> { getTripEpoch(it) }
                    .thenByDescending { it.id }
            )
            JourneySortOption.OLDEST_FIRST -> trips.sortedWith(
                compareBy<Trip> { getTripEpoch(it) }
                    .thenBy { it.id }
            )
            JourneySortOption.UPCOMING_FIRST -> trips.sortedWith(
                compareBy<Trip> {
                    when (it.status) {
                        TripStatus.UPCOMING -> 0
                        TripStatus.IN_PROGRESS -> 1
                        TripStatus.COMPLETED -> 2
                    }
                }.thenByDescending { getTripEpoch(it) }
            )
            JourneySortOption.COMPLETED_FIRST -> trips.sortedWith(
                compareBy<Trip> {
                    when (it.status) {
                        TripStatus.COMPLETED -> 0
                        TripStatus.IN_PROGRESS -> 1
                        TripStatus.UPCOMING -> 2
                    }
                }.thenByDescending { getTripEpoch(it) }
            )
            JourneySortOption.MOST_MOMENTS -> trips.sortedWith(
                compareByDescending<Trip> { stampsMap[it.id]?.momentsCount ?: 0 }
                    .thenByDescending { getTripEpoch(it) }
            )
            JourneySortOption.LEAST_MOMENTS -> trips.sortedWith(
                compareBy<Trip> { stampsMap[it.id]?.momentsCount ?: 0 }
                    .thenBy { getTripEpoch(it) }
            )
            JourneySortOption.NAME_ASC -> trips.sortedWith(
                compareBy<Trip> { it.name.lowercase() }
                    .thenBy { it.id }
            )
            JourneySortOption.NAME_DESC -> trips.sortedWith(
                compareByDescending<Trip> { it.name.lowercase() }
                    .thenByDescending { it.id }
            )
        }
    }

    /**
     * Filters and sorts [TravelStamp]s with search, moments filter, time period filter, and sorting.
     */
    fun filterAndSortStamps(
        stamps: List<TravelStamp>,
        searchQuery: String,
        sortOption: StampSortOption,
        momentsFilter: MomentsFilter,
        datePeriodFilter: DatePeriodFilter,
        today: LocalDate = DateUtils.getTodayLocalDate()
    ): List<TravelStamp> {
        val filtered = stamps.filter { stamp ->
            val matchesMoments = when (momentsFilter) {
                MomentsFilter.ALL -> true
                MomentsFilter.HAS_MOMENTS -> stamp.momentsCount > 0
                MomentsFilter.NO_MOMENTS -> stamp.momentsCount == 0
            }
            val matchesPeriod = matchesDatePeriod(stamp.dateText, datePeriodFilter, today)
            val matchesSearch = matchesStamp(stamp, searchQuery)
            matchesMoments && matchesPeriod && matchesSearch
        }

        return sortStamps(filtered, sortOption)
    }

    /**
     * Filters and sorts [Trip]s with search, status filter, moments filter, time period filter, and sorting.
     */
    fun filterAndSortTrips(
        trips: List<Trip>,
        stampsMap: Map<Long, TravelStamp>,
        searchQuery: String,
        sortOption: JourneySortOption,
        statusFilter: StatusFilter,
        momentsFilter: MomentsFilter,
        datePeriodFilter: DatePeriodFilter,
        today: LocalDate = DateUtils.getTodayLocalDate()
    ): List<Trip> {
        val filtered = trips.filter { trip ->
            val matchesStatus = when (statusFilter) {
                StatusFilter.ALL -> true
                StatusFilter.UPCOMING -> trip.status == TripStatus.UPCOMING
                StatusFilter.IN_PROGRESS -> trip.status == TripStatus.IN_PROGRESS
                StatusFilter.COMPLETED -> trip.status == TripStatus.COMPLETED
            }
            val momentsCount = stampsMap[trip.id]?.momentsCount ?: 0
            val matchesMoments = when (momentsFilter) {
                MomentsFilter.ALL -> true
                MomentsFilter.HAS_MOMENTS -> momentsCount > 0
                MomentsFilter.NO_MOMENTS -> momentsCount == 0
            }
            val matchesPeriod = matchesDatePeriod(trip.date, datePeriodFilter, today)
            val matchesSearch = matchesTrip(trip, stampsMap[trip.id], searchQuery)

            matchesStatus && matchesMoments && matchesPeriod && matchesSearch
        }

        return sortTrips(filtered, stampsMap, sortOption)
    }

    private fun getTripEpoch(trip: Trip): Long {
        return DateUtils.getEpochDay(trip.date, trip.completedAt ?: trip.createdAt)
    }

    private fun getStampEpoch(stamp: TravelStamp): Long {
        return DateUtils.getEpochDay(stamp.dateText, stamp.issuedAt)
    }

    private fun matchesDatePeriod(dateStr: String?, period: DatePeriodFilter, today: LocalDate): Boolean {
        if (period == DatePeriodFilter.ALL_TIME) return true
        val date = DateUtils.parseTripDate(dateStr) ?: return false
        return when (period) {
            DatePeriodFilter.ALL_TIME -> true
            DatePeriodFilter.THIS_MONTH -> date.year == today.year && date.month == today.month
            DatePeriodFilter.THIS_YEAR -> date.year == today.year
        }
    }
}
