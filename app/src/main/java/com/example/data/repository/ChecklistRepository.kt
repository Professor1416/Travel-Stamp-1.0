package com.example.data.repository

import com.example.data.local.dao.ChecklistDao
import com.example.data.local.entity.ChecklistItemEntity
import com.example.data.model.ChecklistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ChecklistRepository {
    fun getItemsForTrip(tripId: Long): Flow<List<ChecklistItem>>
    suspend fun addItem(item: ChecklistItem): Long
    suspend fun addCustomItem(tripId: Long, text: String): Long
    suspend fun toggleItem(itemId: Long, isCompleted: Boolean)
    suspend fun deleteItem(itemId: Long)
    suspend fun seedDefaultItems(tripId: Long)
}

class ChecklistRepositoryImpl(
    private val checklistDao: ChecklistDao
) : ChecklistRepository {

    val defaultPackingItems = listOf(
        "Water",
        "Raincoat",
        "Power Bank",
        "First Aid",
        "Torch",
        "Snacks"
    )

    override fun getItemsForTrip(tripId: Long): Flow<List<ChecklistItem>> =
        checklistDao.getItemsForTrip(tripId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun addItem(item: ChecklistItem): Long =
        checklistDao.insertItem(ChecklistItemEntity.fromDomain(item))

    override suspend fun addCustomItem(tripId: Long, text: String): Long {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return checklistDao.insertItem(
            ChecklistItemEntity(
                tripId = tripId,
                text = trimmed,
                isCompleted = false,
                sortOrder = 999
            )
        )
    }

    override suspend fun toggleItem(itemId: Long, isCompleted: Boolean) {
        checklistDao.updateItemCompletion(itemId, isCompleted)
    }

    override suspend fun deleteItem(itemId: Long) {
        checklistDao.deleteItemById(itemId)
    }

    override suspend fun seedDefaultItems(tripId: Long) {
        val entities = defaultPackingItems.mapIndexed { index, text ->
            ChecklistItemEntity(
                tripId = tripId,
                text = text,
                isCompleted = false,
                sortOrder = index
            )
        }
        checklistDao.insertItems(entities)
    }
}
