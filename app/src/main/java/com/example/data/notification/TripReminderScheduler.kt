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
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

interface TripReminderScheduler {
    fun scheduleReminder(trip: Trip)
    fun cancelReminder(tripId: Long)

    companion object {
        fun getUniqueWorkName(tripId: Long): String = "trip_reminder_$tripId"

        fun calculateDepartureMillis(trip: Trip, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
            val localDate = DateUtils.parseTripDate(trip.date) ?: return null
            val startMin = trip.startTimeMinutes ?: (8 * 60) // default 8:00 AM
            val departureDateTime = localDate.atTime(startMin / 60, startMin % 60)
            return departureDateTime.atZone(zoneId).toInstant().toEpochMilli()
        }

        fun calculateReminderTriggerMillis(
            trip: Trip,
            nowMillis: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): Long? {
            val localDate = DateUtils.parseTripDate(trip.date) ?: return null
            val startMin = trip.startTimeMinutes ?: (8 * 60)
            val departureDateTime = localDate.atTime(startMin / 60, startMin % 60)

            val triggerDateTime: LocalDateTime = when (trip.reminderPreset) {
                TripReminderPreset.ONE_DAY_BEFORE -> {
                    if (trip.reminderTimeMinutes != null) {
                        localDate.minusDays(1).atTime(trip.reminderTimeMinutes / 60, trip.reminderTimeMinutes % 60)
                    } else {
                        departureDateTime.minusHours(24)
                    }
                }
                TripReminderPreset.MORNING_OF -> {
                    localDate.atTime(7, 0)
                }
                TripReminderPreset.TWO_HOURS_BEFORE -> {
                    departureDateTime.minusHours(2)
                }
                TripReminderPreset.ONE_WEEK_BEFORE -> {
                    departureDateTime.minusDays(7)
                }
            }

            return triggerDateTime.atZone(zoneId).toInstant().toEpochMilli()
        }
    }
}

class TripReminderSchedulerImpl(
    private val context: Context
) : TripReminderScheduler {

    private val workManager by lazy { WorkManager.getInstance(context) }

    override fun scheduleReminder(trip: Trip) {
        // If reminders are disabled, trip is completed, or deleted -> cancel any pending work
        if (!trip.reminderEnabled || trip.status == TripStatus.COMPLETED || trip.deletedAt != null) {
            cancelReminder(trip.id)
            return
        }

        val targetTriggerMillis = TripReminderScheduler.calculateReminderTriggerMillis(trip)
        if (targetTriggerMillis == null) {
            cancelReminder(trip.id)
            return
        }

        val nowMillis = System.currentTimeMillis()
        val delayMillis = targetTriggerMillis - nowMillis

        // If target departure is already in the past, do not schedule
        val departureMillis = TripReminderScheduler.calculateDepartureMillis(trip)
        if (departureMillis != null && departureMillis <= nowMillis) {
            cancelReminder(trip.id)
            return
        }

        val effectiveDelayMillis = maxOf(0L, delayMillis)

        val inputData = Data.Builder()
            .putLong(TripReminderWorker.KEY_TRIP_ID, trip.id)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<TripReminderWorker>()
            .setInputData(inputData)
            .setInitialDelay(effectiveDelayMillis, TimeUnit.MILLISECONDS)
            .addTag("trip_reminder")
            .addTag("trip_${trip.id}")
            .build()

        val uniqueWorkName = TripReminderScheduler.getUniqueWorkName(trip.id)
        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    override fun cancelReminder(tripId: Long) {
        val uniqueWorkName = TripReminderScheduler.getUniqueWorkName(tripId)
        workManager.cancelUniqueWork(uniqueWorkName)
    }
}
