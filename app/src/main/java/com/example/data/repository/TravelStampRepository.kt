package com.example.data.repository

import com.example.data.local.dao.TravelStampDao
import com.example.data.local.entity.TravelStampEntity
import com.example.data.model.TravelStamp
import com.example.data.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface TravelStampRepository {
    fun getAllStamps(): Flow<List<TravelStamp>>
    fun getStampForTrip(tripId: Long): Flow<TravelStamp?>
    suspend fun getStampForTripSync(tripId: Long): TravelStamp?
    fun getStampById(id: Long): Flow<TravelStamp?>
    suspend fun issueStamp(stamp: TravelStamp): Long
    
    /**
     * [ATOMICITY, IDEMPOTENCY & THREAD-SAFETY]:
     * Completes a trip and issues its Travel Stamp atomically in a single Room @Transaction.
     * Wrapped in Result<TravelStamp> and executes on Dispatchers.IO.
     */
    suspend fun completeTripAndIssueStamp(
        tripId: Long,
        title: String,
        destination: String,
        dateText: String,
        peopleCount: Int,
        momentsCount: Int,
        inkColorHex: String,
        stampStyle: String,
        reflectionNote: String?,
        completedAt: Long = System.currentTimeMillis()
    ): Result<TravelStamp>

    suspend fun issueOfficialStampForTrip(
        tripId: Long,
        title: String,
        destination: String,
        dateText: String,
        peopleCount: Int,
        momentsCount: Int,
        inkColorHex: String,
        stampStyle: String,
        reflectionNote: String?,
        completedAt: Long = System.currentTimeMillis()
    ): TravelStamp?

    suspend fun deleteStamp(id: Long)
    suspend fun updateStampMomentsCount(tripId: Long)
    suspend fun correctOfficialJourneyDate(tripId: Long, newDate: String): Result<Boolean>
    fun getStampsCount(): Flow<Int>
    suspend fun getStampsCountSync(): Int
    suspend fun allocateNextStampNumber(): Long
}

class TravelStampRepositoryImpl(
    private val stampDao: TravelStampDao
) : TravelStampRepository {

    override fun getAllStamps(): Flow<List<TravelStamp>> =
        stampDao.getAllStamps().map { entities -> entities.map { it.toDomain() } }

    override fun getStampForTrip(tripId: Long): Flow<TravelStamp?> =
        stampDao.getStampForTrip(tripId).map { it?.toDomain() }

    override suspend fun getStampForTripSync(tripId: Long): TravelStamp? =
        stampDao.getStampForTripSync(tripId)?.toDomain()

    override fun getStampById(id: Long): Flow<TravelStamp?> =
        stampDao.getStampById(id).map { it?.toDomain() }

    override suspend fun issueStamp(stamp: TravelStamp): Long {
        if (DateUtils.isFutureDate(stamp.dateText)) {
            return -1L
        }
        return stampDao.insertStamp(TravelStampEntity.fromDomain(stamp))
    }

    /**
     * [ATOMICITY & IDEMPOTENCY]:
     * 1. Validates trip date is not in the future.
     * 2. Checks if stamp already exists for tripId (idempotent shortcut).
     * 3. Executes atomic Room @Transaction via Dispatchers.IO.
     */
    override suspend fun completeTripAndIssueStamp(
        tripId: Long,
        title: String,
        destination: String,
        dateText: String,
        peopleCount: Int,
        momentsCount: Int,
        inkColorHex: String,
        stampStyle: String,
        reflectionNote: String?,
        completedAt: Long
    ): Result<TravelStamp> = withContext(Dispatchers.IO) {
        runCatching {
            // Validation: Never issue a stamp for a future trip
            if (DateUtils.isFutureDate(dateText)) {
                error("Cannot complete a future journey. Starts on $dateText.")
            }

            // Idempotency check: Return existing stamp immediately if already created
            val existing = stampDao.getStampForTripSync(tripId)
            if (existing != null) {
                stampDao.markTripCompleted(tripId, completedAt)
                return@runCatching existing.toDomain()
            }

            // Atomic transaction: Insert stamp & mark trip COMPLETED together
            val entity = stampDao.completeTripAndIssueStampAtomic(
                tripId = tripId,
                title = title,
                destination = destination,
                dateText = dateText,
                peopleCount = peopleCount,
                momentsCount = momentsCount,
                inkColorHex = inkColorHex,
                stampStyle = stampStyle,
                reflectionNote = reflectionNote,
                completedAt = completedAt
            )
            entity.toDomain()
        }
    }

    override suspend fun issueOfficialStampForTrip(
        tripId: Long,
        title: String,
        destination: String,
        dateText: String,
        peopleCount: Int,
        momentsCount: Int,
        inkColorHex: String,
        stampStyle: String,
        reflectionNote: String?,
        completedAt: Long
    ): TravelStamp? {
        return completeTripAndIssueStamp(
            tripId = tripId,
            title = title,
            destination = destination,
            dateText = dateText,
            peopleCount = peopleCount,
            momentsCount = momentsCount,
            inkColorHex = inkColorHex,
            stampStyle = stampStyle,
            reflectionNote = reflectionNote,
            completedAt = completedAt
        ).getOrNull()
    }

    override suspend fun deleteStamp(id: Long) =
        stampDao.deleteStampById(id)

    override suspend fun updateStampMomentsCount(tripId: Long) {
        withContext(Dispatchers.IO) {
            stampDao.updateStampMomentsCount(tripId)
        }
    }

    override suspend fun correctOfficialJourneyDate(tripId: Long, newDate: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val cleanedDate = newDate.trim()
                if (cleanedDate.isBlank()) {
                    throw IllegalArgumentException("Date cannot be blank")
                }
                if (DateUtils.isFutureDate(cleanedDate)) {
                    throw IllegalArgumentException("Official journey date cannot be set to a future date")
                }
                val success = stampDao.correctOfficialJourneyDate(tripId, cleanedDate)
                if (!success) {
                    throw IllegalStateException("Failed to update journey date or stamp record not found for tripId $tripId")
                }
                true
            }
        }
    }

    override fun getStampsCount(): Flow<Int> =
        stampDao.getStampsCount()

    override suspend fun getStampsCountSync(): Int =
        stampDao.getStampsCountSync()

    override suspend fun allocateNextStampNumber(): Long =
        stampDao.allocateNextStampNumber()
}
