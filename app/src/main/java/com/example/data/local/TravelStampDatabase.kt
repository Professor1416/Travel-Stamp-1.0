package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ChecklistDao
import com.example.data.local.dao.MomentDao
import com.example.data.local.dao.TravelStampDao
import com.example.data.local.dao.TripDao
import com.example.data.local.entity.ChecklistItemEntity
import com.example.data.local.entity.MomentEntity
import com.example.data.local.entity.TravelStampEntity
import com.example.data.local.entity.TripEntity

@Database(
    entities = [
        TripEntity::class,
        ChecklistItemEntity::class,
        MomentEntity::class,
        TravelStampEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TravelStampDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun momentDao(): MomentDao
    abstract fun travelStampDao(): TravelStampDao

    companion object {
        @Volatile
        private var INSTANCE: TravelStampDatabase? = null

        fun getDatabase(context: Context): TravelStampDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TravelStampDatabase::class.java,
                    "travel_stamp_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
