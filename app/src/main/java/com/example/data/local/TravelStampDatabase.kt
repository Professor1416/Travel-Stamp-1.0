package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ChecklistDao
import com.example.data.local.dao.MomentDao
import com.example.data.local.dao.TravelStampDao
import com.example.data.local.dao.TripDao
import com.example.data.local.entity.ChecklistItemEntity
import com.example.data.local.entity.MomentEntity
import com.example.data.local.entity.StampSequenceEntity
import com.example.data.local.entity.TravelStampEntity
import com.example.data.local.entity.TripEntity

@Database(
    entities = [
        TripEntity::class,
        ChecklistItemEntity::class,
        MomentEntity::class,
        TravelStampEntity::class,
        StampSequenceEntity::class
    ],
    version = 3,
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure stamp_sequence table exists
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stamp_sequence` (
                        `id` TEXT NOT NULL,
                        `lastAllocatedNumber` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Upgrade trips table
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT (lower(hex(randomblob(16))))")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `deletedAt` INTEGER DEFAULT NULL")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trips_uuid` ON `trips` (`uuid`)")

                // 2. Upgrade checklist_items table
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT (lower(hex(randomblob(16))))")
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `deletedAt` INTEGER DEFAULT NULL")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_checklist_items_uuid` ON `checklist_items` (`uuid`)")

                // 3. Upgrade moments table
                db.execSQL("ALTER TABLE `moments` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT (lower(hex(randomblob(16))))")
                db.execSQL("ALTER TABLE `moments` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `moments` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `moments` ADD COLUMN `deletedAt` INTEGER DEFAULT NULL")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_moments_uuid` ON `moments` (`uuid`)")

                // 4. Upgrade travel_stamps table
                db.execSQL("ALTER TABLE `travel_stamps` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
            }
        }

        fun getDatabase(context: Context): TravelStampDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TravelStampDatabase::class.java,
                    "travel_stamp_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
