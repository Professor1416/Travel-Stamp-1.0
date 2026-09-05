package com.example.data.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepositoryImpl
import com.example.data.local.entity.TripEntity
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import com.example.data.model.TripStatus
import com.example.data.util.DateUtils
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class TripReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val tripId = inputData.getLong(KEY_TRIP_ID, -1L)
            val scheduledPresetName = inputData.getString(KEY_REMINDER_PRESET)
            val scheduledTriggerAtMillis = inputData.getLong(KEY_TRIGGER_AT, -1L)

            val database = TravelStampDatabase.getDatabase(applicationContext)
            val tripEntity = if (tripId > 0L) database.tripDao().getTripByIdSync(tripId) else null
            val userPrefs = UserPreferencesRepositoryImpl(applicationContext)
            val globalRemindersEnabled = userPrefs.preTripRemindersEnabled.value

            val decision = evaluateSafety(
                tripId = tripId,
                scheduledPresetName = scheduledPresetName,
                scheduledTriggerAtMillis = scheduledTriggerAtMillis,
                tripEntity = tripEntity,
                globalRemindersEnabled = globalRemindersEnabled,
                workerNow = Instant.now(),
                zoneId = ZoneId.systemDefault()
            )

            when (decision) {
                is WorkerSafetyResult.Proceed -> {
                    TripNotificationHelper.showTripReminderNotification(applicationContext, decision.trip)
                }
                is WorkerSafetyResult.Abort -> {
                    // Fail closed silently without notification
                }
            }

            Result.success()
        } catch (_: Exception) {
            // Fail closed on any unexpected exception
            Result.success()
        }
    }

    sealed class WorkerSafetyResult {
        data class Proceed(val trip: Trip) : WorkerSafetyResult()
        object Abort : WorkerSafetyResult()
    }

    companion object {
        const val KEY_TRIP_ID = "key_trip_id"
        const val KEY_REMINDER_PRESET = "key_reminder_preset"
        const val KEY_TRIGGER_AT = "key_trigger_at"

        val MAX_EXECUTION_LATENESS: Duration = Duration.ofHours(2)

        /**
         * Pure deterministic evaluation of reminder execution safety for N5.3.
         * Returns [WorkerSafetyResult.Proceed] with the domain [Trip] if all safety gates pass,
         * or [WorkerSafetyResult.Abort] to fail closed with zero notification.
         */
        fun evaluateSafety(
            tripId: Long,
            scheduledPresetName: String?,
            scheduledTriggerAtMillis: Long,
            tripEntity: TripEntity?,
            globalRemindersEnabled: Boolean,
            workerNow: Instant = Instant.now(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): WorkerSafetyResult {
            // 1. WorkRequest metadata validation
            if (tripId <= 0L) {
                return WorkerSafetyResult.Abort
            }
            if (scheduledPresetName.isNullOrBlank()) {
                return WorkerSafetyResult.Abort
            }
            if (scheduledTriggerAtMillis <= 0L) {
                return WorkerSafetyResult.Abort
            }
            val scheduledPreset = try {
                TripReminderPreset.valueOf(scheduledPresetName)
            } catch (_: IllegalArgumentException) {
                return WorkerSafetyResult.Abort
            }

            // 2. Trip existence and deletion check
            if (tripEntity == null || tripEntity.deletedAt != null) {
                return WorkerSafetyResult.Abort
            }

            // 3. Trip lifecycle completion and stamp checks
            if (tripEntity.status.equals(TripStatus.COMPLETED.name, ignoreCase = true) ||
                tripEntity.stampEarned ||
                tripEntity.completedAt != null
            ) {
                return WorkerSafetyResult.Abort
            }

            // 4. Departure minutes validation (reject malformed/out-of-range departure minutes)
            if (tripEntity.startTimeMinutes != null && tripEntity.startTimeMinutes !in 0..1439) {
                return WorkerSafetyResult.Abort
            }

            // 5. Global and individual reminder preference checks
            if (!globalRemindersEnabled || !tripEntity.reminderEnabled) {
                return WorkerSafetyResult.Abort
            }

            val trip = tripEntity.toDomain()

            // 6. Preset mismatch check (compare scheduled preset vs current trip preset)
            if (scheduledPreset != trip.reminderPreset) {
                return WorkerSafetyResult.Abort
            }

            // 7. Execution timing window checks
            val scheduledTriggerAt = Instant.ofEpochMilli(scheduledTriggerAtMillis)
            if (workerNow.isBefore(scheduledTriggerAt)) {
                // Unexpected early execution before trigger
                return WorkerSafetyResult.Abort
            }
            val lateness = Duration.between(scheduledTriggerAt, workerNow)
            if (lateness > MAX_EXECUTION_LATENESS) {
                // Stale execution (> 2 hours late)
                return WorkerSafetyResult.Abort
            }

            // 8. Canonical trigger revalidation via ReminderScheduleCalculator
            val validationReference = Instant.ofEpochMilli(scheduledTriggerAtMillis).minusMillis(1)
            val recalculated = ReminderScheduleCalculator.calculate(
                tripDate = trip.date,
                startTimeMinutes = trip.startTimeMinutes,
                preset = trip.reminderPreset,
                now = validationReference,
                zoneId = zoneId
            )
            if (recalculated !is ReminderScheduleResult.Schedulable) {
                return WorkerSafetyResult.Abort
            }
            if (recalculated.triggerAt.toEpochMilli() != scheduledTriggerAtMillis) {
                return WorkerSafetyResult.Abort
            }

            // 9. Explicit departure already occurred check
            if (trip.startTimeMinutes != null) {
                val departureDate = DateUtils.parseTripDate(trip.date) ?: return WorkerSafetyResult.Abort
                val hours = trip.startTimeMinutes / 60
                val minutes = trip.startTimeMinutes % 60
                if (hours !in 0..23 || minutes !in 0..59) {
                    return WorkerSafetyResult.Abort
                }
                val departureLocalTime = LocalTime.of(hours, minutes)
                val departureInstant = departureDate.atTime(departureLocalTime).atZone(zoneId).toInstant()
                if (!workerNow.isBefore(departureInstant)) {
                    // workerNow >= departureInstant (departure has already occurred)
                    return WorkerSafetyResult.Abort
                }
            }

            return WorkerSafetyResult.Proceed(trip)
        }
    }
}

