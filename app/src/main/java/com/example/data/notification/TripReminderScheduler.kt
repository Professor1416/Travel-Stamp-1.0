package com.example.data.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import com.example.data.model.TripStatus
import com.example.data.util.DateUtils
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

interface TripReminderScheduler {
    fun scheduleReminder(trip: Trip)
    fun cancelReminder(tripId: Long)

    companion object {
        fun getUniqueWorkName(tripId: Long): String = "trip_reminder_$tripId"

        @Deprecated("Use ReminderScheduleCalculator.calculate() instead", ReplaceWith("ReminderScheduleCalculator"))
        fun calculateReminderTriggerMillis(
            trip: Trip,
            nowMillis: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): Long? {
            val result = ReminderScheduleCalculator.calculate(
                tripDate = trip.date,
                startTimeMinutes = trip.startTimeMinutes,
                preset = trip.reminderPreset,
                now = Instant.ofEpochMilli(nowMillis),
                zoneId = zoneId
            )
            return (result as? ReminderScheduleResult.Schedulable)?.triggerAt?.toEpochMilli()
        }

        @Deprecated("Legacy helper for departure time computation")
        fun calculateDepartureMillis(trip: Trip, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
            val localDate = DateUtils.parseTripDate(trip.date) ?: return null
            val startMin = trip.startTimeMinutes ?: (8 * 60)
            val departureDateTime = localDate.atTime(startMin / 60, startMin % 60)
            return departureDateTime.atZone(zoneId).toInstant().toEpochMilli()
        }
    }
}

class TripReminderSchedulerImpl(
    private val context: Context,
    private val customWorkManager: WorkManager? = null
) : TripReminderScheduler {

    private val workManager: WorkManager by lazy {
        customWorkManager ?: WorkManager.getInstance(context)
    }

    override fun scheduleReminder(trip: Trip) {
        // Lifecycle & eligibility guards:
        // If reminders are disabled, trip is completed, deleted, completedAt is set, or stamp is earned -> cancel
        if (!trip.reminderEnabled ||
            trip.status == TripStatus.COMPLETED ||
            trip.deletedAt != null ||
            trip.stampEarned ||
            trip.completedAt != null
        ) {
            cancelReminder(trip.id)
            return
        }

        val now = Instant.now()
        val zoneId = ZoneId.systemDefault()

        val scheduleResult = ReminderScheduleCalculator.calculate(
            tripDate = trip.date,
            startTimeMinutes = trip.startTimeMinutes,
            preset = trip.reminderPreset,
            now = now,
            zoneId = zoneId
        )

        when (scheduleResult) {
            is ReminderScheduleResult.Schedulable -> {
                val delayMillis = scheduleResult.triggerAt.toEpochMilli() - now.toEpochMilli()
                // Strict no-catch-up check: Delay must be strictly positive
                if (delayMillis <= 0L) {
                    cancelReminder(trip.id)
                    return
                }

                val workRequest = buildWorkRequest(trip, scheduleResult.triggerAt, delayMillis)

                val uniqueWorkName = TripReminderScheduler.getUniqueWorkName(trip.id)
                workManager.enqueueUniqueWork(
                    uniqueWorkName,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
            is ReminderScheduleResult.DepartureTimeRequired,
            is ReminderScheduleResult.TriggerAlreadyPassed,
            is ReminderScheduleResult.InvalidTripDate,
            is ReminderScheduleResult.InvalidDepartureTime -> {
                cancelReminder(trip.id)
            }
        }
    }

    internal fun buildWorkRequest(
        trip: Trip,
        triggerAt: Instant,
        delayMillis: Long
    ): androidx.work.OneTimeWorkRequest {
        val inputData = Data.Builder()
            .putLong(TripReminderWorker.KEY_TRIP_ID, trip.id)
            .putString(TripReminderWorker.KEY_REMINDER_PRESET, trip.reminderPreset.name)
            .putLong(TripReminderWorker.KEY_TRIGGER_AT, triggerAt.toEpochMilli())
            .build()

        return OneTimeWorkRequestBuilder<TripReminderWorker>()
            .setInputData(inputData)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag("trip_reminder")
            .addTag("trip_${trip.id}")
            .build()
    }

    override fun cancelReminder(tripId: Long) {
        val uniqueWorkName = TripReminderScheduler.getUniqueWorkName(tripId)
        workManager.cancelUniqueWork(uniqueWorkName)
    }
}
