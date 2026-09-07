package com.example.data.notification

import com.example.data.local.UserPreferencesRepository
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.data.repository.TripRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates global Journey Reminder settings and orchestrates reminder work.
 *
 * Responsibilities:
 * - GLOBAL OFF: Persists preference OFF and proactively cancels all pending WorkManager reminder jobs,
 *   retaining individual trip preferences without mutation.
 * - GLOBAL ON: Persists preference ON, queries eligible trips, and delegates rescheduling to [TripReminderScheduler].
 * - Serializes operations using [Mutex] to guarantee consistency across rapid toggles.
 * - Never calls WorkManager from Compose; acts as the single orchestration point.
 */
interface ReminderCoordinator {
    suspend fun setGlobalRemindersEnabled(enabled: Boolean)
    suspend fun reconcile()
    suspend fun reconcileEnabledTrips() = reconcile()
}

class ReminderCoordinatorImpl(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val tripRepository: TripRepository,
    private val reminderScheduler: TripReminderScheduler
) : ReminderCoordinator {

    private val mutex = Mutex()

    override suspend fun setGlobalRemindersEnabled(enabled: Boolean) {
        mutex.withLock {
            userPreferencesRepository.setPreTripRemindersEnabled(enabled)
            if (!enabled) {
                reminderScheduler.cancelAllReminders()
            } else {
                reconcileInternal()
            }
        }
    }

    override suspend fun reconcile() {
        mutex.withLock {
            if (!userPreferencesRepository.preTripRemindersEnabled.value) {
                reminderScheduler.cancelAllReminders()
                return@withLock
            }
            reconcileInternal()
        }
    }

    private suspend fun reconcileInternal() {
        val trips: List<Trip> = try {
            tripRepository.getAllTrips().first()
        } catch (_: Exception) {
            emptyList()
        }

        // Prefilter obvious lifecycle states
        val eligibleTrips = trips.filter { trip ->
            trip.reminderEnabled &&
                trip.deletedAt == null &&
                trip.status != TripStatus.COMPLETED &&
                !trip.stampEarned &&
                trip.completedAt == null
        }

        // Reschedule each eligible trip individually; one failure does not abort the rest
        for (trip in eligibleTrips) {
            try {
                reminderScheduler.scheduleReminder(trip)
            } catch (_: Exception) {
                // Partial failure safety
            }
        }
    }
}
