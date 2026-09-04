package com.example.data.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.TravelStampDatabase
import com.example.data.model.TripStatus

class TripReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val tripId = inputData.getLong(KEY_TRIP_ID, -1L)
        if (tripId == -1L) {
            return Result.success()
        }

        val database = TravelStampDatabase.getDatabase(applicationContext)
        val tripEntity = database.tripDao().getTripByIdSync(tripId)

        // Lifecycle & State Revalidation:
        // 1. If trip was deleted, ignore
        if (tripEntity == null || tripEntity.deletedAt != null) {
            return Result.success()
        }

        // 2. If trip was completed or stamped, ignore
        if (tripEntity.status == TripStatus.COMPLETED.name || tripEntity.stampEarned || tripEntity.completedAt != null) {
            return Result.success()
        }

        // 3. If user toggled reminder OFF in the meantime (global or trip-specific), ignore
        val userPrefs = com.example.data.local.UserPreferencesRepositoryImpl(applicationContext)
        if (!userPrefs.preTripRemindersEnabled.value || !tripEntity.reminderEnabled) {
            return Result.success()
        }

        val trip = tripEntity.toDomain()

        // 4. Dispatch notification
        TripNotificationHelper.showTripReminderNotification(applicationContext, trip)

        return Result.success()
    }

    companion object {
        const val KEY_TRIP_ID = "key_trip_id"
        const val KEY_REMINDER_PRESET = "key_reminder_preset"
        const val KEY_TRIGGER_AT = "key_trigger_at"
    }
}
