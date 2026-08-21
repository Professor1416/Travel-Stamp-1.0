package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.StampSequenceEntity
import com.example.data.local.entity.TravelStampEntity
import kotlinx.coroutines.flow.Flow
import java.util.Locale

@Dao
interface TravelStampDao {
    @Query("SELECT * FROM travel_stamps ORDER BY stampNumber DESC, issuedAt DESC")
    fun getAllStamps(): Flow<List<TravelStampEntity>>

    @Query("SELECT * FROM travel_stamps ORDER BY stampNumber ASC")
    suspend fun getAllStampsListSync(): List<TravelStampEntity>

    @Query("SELECT * FROM travel_stamps WHERE tripId = :tripId LIMIT 1")
    fun getStampForTrip(tripId: Long): Flow<TravelStampEntity?>

    @Query("SELECT * FROM travel_stamps WHERE tripId = :tripId LIMIT 1")
    suspend fun getStampForTripSync(tripId: Long): TravelStampEntity?

    @Query("SELECT * FROM travel_stamps WHERE id = :id LIMIT 1")
    fun getStampById(id: Long): Flow<TravelStampEntity?>

    @Query("SELECT * FROM travel_stamps WHERE stampNumber = :stampNumber LIMIT 1")
    suspend fun getStampByNumberSync(stampNumber: Long): TravelStampEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStamp(stamp: TravelStampEntity): Long

    @Query("DELETE FROM travel_stamps WHERE id = :id")
    suspend fun deleteStampById(id: Long)

    @Query("SELECT COUNT(*) FROM travel_stamps")
    fun getStampsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM travel_stamps")
    suspend fun getStampsCountSync(): Int

    @Query("SELECT MAX(stampNumber) FROM travel_stamps")
    suspend fun getMaxStampNumber(): Long?

    @Query("SELECT lastAllocatedNumber FROM stamp_sequence WHERE id = 'STAMP_COUNTER'")
    suspend fun getLastAllocatedSequence(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setLastAllocatedSequence(sequence: StampSequenceEntity)

    @Query("UPDATE travel_stamps SET momentsCount = (SELECT COUNT(*) FROM moments WHERE tripId = :tripId AND deletedAt IS NULL) WHERE tripId = :tripId")
    suspend fun updateStampMomentsCount(tripId: Long)

    @Query("UPDATE trips SET status = 'COMPLETED', stampEarned = 1, completedAt = :completedAt, updatedAt = :completedAt WHERE id = :tripId")
    suspend fun markTripCompleted(tripId: Long, completedAt: Long): Int

    /**
     * Atomically allocates the next sequential permanent stamp number.
     * Guaranteed to be monotonic and NEVER decrements or reuses numbers even if previous stamps are deleted.
     */
    @Transaction
    suspend fun allocateNextStampNumber(): Long {
        val lastSeq = getLastAllocatedSequence() ?: 0L
        val maxFromStamps = getMaxStampNumber() ?: 0L
        val currentMax = maxOf(lastSeq, maxFromStamps)
        val nextNumber = currentMax + 1L
        setLastAllocatedSequence(StampSequenceEntity(id = "STAMP_COUNTER", lastAllocatedNumber = nextNumber))
        return nextNumber
    }

    /**
     * [ATOMICITY & IDEMPOTENCY]:
     * Executes stamp allocation, stamp insertion, and trip completion as a single atomic Room @Transaction.
     * If any sub-operation fails or throws an exception, Room automatically rolls back all SQLite mutations.
     * Idempotency guarantee: If a stamp already exists for this tripId, it returns the existing stamp directly.
     */
    @Transaction
    suspend fun completeTripAndIssueStampAtomic(
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
    ): TravelStampEntity {
        // 1. Idempotency Guard: Check if stamp already exists
        val existing = getStampForTripSync(tripId)
        if (existing != null) {
            markTripCompleted(tripId, completedAt)
            return existing
        }

        // 2. Allocate permanent monotonic sequence number
        val nextNumber = allocateNextStampNumber()
        val formattedCode = "#" + String.format(Locale.getDefault(), "%03d", nextNumber)

        val entity = TravelStampEntity(
            tripId = tripId,
            stampNumber = nextNumber,
            stampCode = formattedCode,
            title = title,
            destination = destination,
            dateText = dateText,
            peopleCount = peopleCount,
            momentsCount = momentsCount,
            inkColorHex = inkColorHex,
            stampStyle = stampStyle,
            reflectionNote = reflectionNote,
            issuedAt = completedAt,
            completedAt = completedAt,
            createdAt = completedAt,
            updatedAt = completedAt
        )

        // 3. Insert stamp entity
        insertStamp(entity)

        // 4. Update trip status to COMPLETED atomically in the exact same transaction
        markTripCompleted(tripId, completedAt)

        return getStampForTripSync(tripId) ?: entity
    }

    /**
     * Atomically issues the official stamp for a completed trip.
     * If a stamp already exists for this trip, it returns the existing stamp without allocating a new number.
     * Prevents race conditions, duplicates, and maintains 1:1 Trip-to-Stamp relationship.
     */
    @Transaction
    suspend fun issueOfficialStamp(
        tripId: Long,
        stampBuilder: (nextNumber: Long, formattedCode: String) -> TravelStampEntity
    ): TravelStampEntity {
        val existing = getStampForTripSync(tripId)
        if (existing != null) {
            return existing
        }

        val nextNumber = allocateNextStampNumber()
        val formattedCode = "#" + String.format(Locale.getDefault(), "%03d", nextNumber)
        val entity = stampBuilder(nextNumber, formattedCode)
        insertStamp(entity)
        return getStampForTripSync(tripId) ?: entity
    }
}
