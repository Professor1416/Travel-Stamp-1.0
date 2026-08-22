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
    version = 7,
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

        private fun getExistingColumns(db: SupportSQLiteDatabase, tableName: String): Set<String> {
            val columns = mutableSetOf<String>()
            val cursor = db.query("PRAGMA table_info(`$tableName`)")
            cursor.use {
                val nameIndex = it.getColumnIndex("name")
                if (nameIndex >= 0) {
                    while (it.moveToNext()) {
                        columns.add(it.getString(nameIndex))
                    }
                }
            }
            return columns
        }

        private fun ensureUniqueUuids(db: SupportSQLiteDatabase, tableName: String) {
            val cursor = db.query("SELECT `id`, `uuid` FROM `$tableName`")
            val rowsToUpdate = mutableListOf<Long>()
            val seenUuids = mutableSetOf<String>()
            cursor.use {
                val idIdx = it.getColumnIndex("id")
                val uuidIdx = it.getColumnIndex("uuid")
                while (it.moveToNext()) {
                    val id = it.getLong(idIdx)
                    val uuid = if (uuidIdx >= 0) it.getString(uuidIdx) else null
                    if (uuid.isNullOrBlank() || !seenUuids.add(uuid)) {
                        rowsToUpdate.add(id)
                    }
                }
            }
            for (id in rowsToUpdate) {
                val newUuid = java.util.UUID.randomUUID().toString()
                db.execSQL("UPDATE `$tableName` SET `uuid` = ? WHERE `id` = ?", arrayOf<Any>(newUuid, id))
            }
        }

        /**
         * Safely performs universal non-destructive schema migrations across all database versions.
         * Preserves all trips, stamps, moments, checklists, sequences, and timestamps.
         */
        fun performFullMigration(db: SupportSQLiteDatabase) {
            // 1. Ensure stamp_sequence table exists
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `stamp_sequence` (
                    `id` TEXT NOT NULL,
                    `lastAllocatedNumber` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )

            // 2. Safely upgrade trips table
            val tripCols = getExistingColumns(db, "trips")
            if (!tripCols.contains("uuid")) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
            }
            if (!tripCols.contains("startTimeMinutes")) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `startTimeMinutes` INTEGER DEFAULT NULL")
            }
            if (!tripCols.contains("reminderEnabled")) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `reminderEnabled` INTEGER NOT NULL DEFAULT 0")
            }
            if (!tripCols.contains("reminderPreset")) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `reminderPreset` TEXT NOT NULL DEFAULT 'ONE_DAY_BEFORE'")
            }
            if (!tripCols.contains("reminderTimeMinutes")) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `reminderTimeMinutes` INTEGER DEFAULT NULL")
            }
            if (!tripCols.contains("updatedAt")) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            }
            if (!tripCols.contains("deletedAt")) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `deletedAt` INTEGER DEFAULT NULL")
            }
            ensureUniqueUuids(db, "trips")
            db.execSQL("UPDATE `trips` SET `updatedAt` = `createdAt` WHERE `updatedAt` = 0 AND `createdAt` > 0")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trips_uuid` ON `trips` (`uuid`)")

            // 3. Safely upgrade checklist_items table
            val checklistCols = getExistingColumns(db, "checklist_items")
            if (!checklistCols.contains("uuid")) {
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
            }
            if (!checklistCols.contains("createdAt")) {
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
            }
            if (!checklistCols.contains("updatedAt")) {
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            }
            if (!checklistCols.contains("deletedAt")) {
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `deletedAt` INTEGER DEFAULT NULL")
            }
            ensureUniqueUuids(db, "checklist_items")
            val now = System.currentTimeMillis()
            db.execSQL("UPDATE `checklist_items` SET `createdAt` = $now WHERE `createdAt` = 0")
            db.execSQL("UPDATE `checklist_items` SET `updatedAt` = $now WHERE `updatedAt` = 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_items_tripId` ON `checklist_items` (`tripId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_checklist_items_uuid` ON `checklist_items` (`uuid`)")

            // 4. Safely upgrade moments table
            val momentCols = getExistingColumns(db, "moments")
            if (!momentCols.contains("uuid")) {
                db.execSQL("ALTER TABLE `moments` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
            }
            if (!momentCols.contains("hyperlinksJson")) {
                db.execSQL("ALTER TABLE `moments` ADD COLUMN `hyperlinksJson` TEXT DEFAULT NULL")
            }
            if (!momentCols.contains("createdAt")) {
                db.execSQL("ALTER TABLE `moments` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
            }
            if (!momentCols.contains("updatedAt")) {
                db.execSQL("ALTER TABLE `moments` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            }
            if (!momentCols.contains("deletedAt")) {
                db.execSQL("ALTER TABLE `moments` ADD COLUMN `deletedAt` INTEGER DEFAULT NULL")
            }
            ensureUniqueUuids(db, "moments")
            db.execSQL("UPDATE `moments` SET `createdAt` = `timestamp` WHERE `createdAt` = 0 AND `timestamp` > 0")
            db.execSQL("UPDATE `moments` SET `updatedAt` = `timestamp` WHERE `updatedAt` = 0 AND `timestamp` > 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_moments_tripId` ON `moments` (`tripId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_moments_uuid` ON `moments` (`uuid`)")

            // 5. Safely upgrade travel_stamps table
            val stampCols = getExistingColumns(db, "travel_stamps")
            if (!stampCols.contains("uuid")) {
                db.execSQL("ALTER TABLE `travel_stamps` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
            }
            if (!stampCols.contains("createdAt")) {
                db.execSQL("ALTER TABLE `travel_stamps` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
            }
            if (!stampCols.contains("updatedAt")) {
                db.execSQL("ALTER TABLE `travel_stamps` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            }
            if (!stampCols.contains("completedAt")) {
                db.execSQL("ALTER TABLE `travel_stamps` ADD COLUMN `completedAt` INTEGER DEFAULT NULL")
            }
            if (!stampCols.contains("deletedAt")) {
                db.execSQL("ALTER TABLE `travel_stamps` ADD COLUMN `deletedAt` INTEGER DEFAULT NULL")
            }
            if (!stampCols.contains("reflectionNote")) {
                db.execSQL("ALTER TABLE `travel_stamps` ADD COLUMN `reflectionNote` TEXT DEFAULT NULL")
            }
            ensureUniqueUuids(db, "travel_stamps")
            db.execSQL("UPDATE `travel_stamps` SET `createdAt` = `issuedAt` WHERE `createdAt` = 0 AND `issuedAt` > 0")
            db.execSQL("UPDATE `travel_stamps` SET `updatedAt` = `issuedAt` WHERE `updatedAt` = 0 AND `issuedAt` > 0")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_travel_stamps_tripId` ON `travel_stamps` (`tripId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_travel_stamps_stampNumber` ON `travel_stamps` (`stampNumber`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_travel_stamps_uuid` ON `travel_stamps` (`uuid`)")
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_1_7 = object : Migration(1, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_2_7 = object : Migration(2, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_3_7 = object : Migration(3, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_4_7 = object : Migration(4, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_5_7 = object : Migration(5, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_2_6 = object : Migration(2, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_3_6 = object : Migration(3, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                performFullMigration(db)
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_1_7,
            MIGRATION_2_7,
            MIGRATION_3_7,
            MIGRATION_4_7,
            MIGRATION_5_7,
            MIGRATION_1_6,
            MIGRATION_2_6,
            MIGRATION_3_6,
            MIGRATION_4_6,
            MIGRATION_1_5,
            MIGRATION_2_5,
            MIGRATION_3_5,
            MIGRATION_1_4,
            MIGRATION_2_4,
            MIGRATION_1_3
        )

        fun getDatabase(context: Context): TravelStampDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TravelStampDatabase::class.java,
                    "travel_stamp_database"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.setForeignKeyConstraintsEnabled(true)
                            db.execSQL("PRAGMA foreign_keys = ON;")
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
