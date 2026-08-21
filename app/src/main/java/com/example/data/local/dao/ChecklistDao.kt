package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ChecklistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items WHERE tripId = :tripId ORDER BY sortOrder ASC, id ASC")
    fun getItemsForTrip(tripId: Long): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE tripId = :tripId ORDER BY sortOrder ASC, id ASC")
    suspend fun getItemsForTripSync(tripId: Long): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items ORDER BY id ASC")
    suspend fun getAllItemsListSync(): List<ChecklistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ChecklistItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ChecklistItemEntity>): List<Long>

    @Update
    suspend fun updateItem(item: ChecklistItemEntity)

    @Query("UPDATE checklist_items SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateItemCompletion(id: Long, isCompleted: Boolean)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM checklist_items WHERE tripId = :tripId")
    suspend fun deleteItemsForTrip(tripId: Long)
}
