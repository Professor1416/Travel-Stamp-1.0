package com.example.ui.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.BundledSuggestionSourceImpl
import com.example.data.datasource.UserHistorySuggestionSourceImpl
import com.example.data.local.AppThemeMode
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.entity.TripEntity
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.data.repository.ChecklistRepositoryImpl
import com.example.data.repository.LocationSuggestionRepositoryImpl
import com.example.data.repository.MomentRepositoryImpl
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripNavigationValidationTest {

    private lateinit var context: Context
    private lateinit var db: TravelStampDatabase
    private lateinit var tripRepo: TripRepositoryImpl
    private lateinit var stampRepo: TravelStampRepositoryImpl
    private lateinit var checklistRepo: ChecklistRepositoryImpl
    private lateinit var momentRepo: MomentRepositoryImpl
    private lateinit var userPrefs: UserPreferencesRepository
    private lateinit var suggestionRepo: LocationSuggestionRepositoryImpl
    private lateinit var vm: TravelViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        tripRepo = TripRepositoryImpl(db.tripDao(), db.momentDao(), context)
        stampRepo = TravelStampRepositoryImpl(db.travelStampDao())
        checklistRepo = ChecklistRepositoryImpl(db.checklistDao())
        momentRepo = MomentRepositoryImpl(db.momentDao(), context)
        val userHistorySource = UserHistorySuggestionSourceImpl(tripRepo)
        val bundledSource = BundledSuggestionSourceImpl()
        suggestionRepo = LocationSuggestionRepositoryImpl(userHistorySource, bundledSource)

        userPrefs = object : UserPreferencesRepository {
            override val hasCompletedOnboarding = MutableStateFlow(true)
            override val themeMode = MutableStateFlow(AppThemeMode.LIGHT)
            override val preTripRemindersEnabled = MutableStateFlow(true)

            override fun setOnboardingCompleted(completed: Boolean) {
                (hasCompletedOnboarding as MutableStateFlow).value = completed
            }

            override fun setThemeMode(mode: AppThemeMode) {
                (themeMode as MutableStateFlow).value = mode
            }

            override fun setPreTripRemindersEnabled(enabled: Boolean) {
                (preTripRemindersEnabled as MutableStateFlow).value = enabled
            }
        }

        vm = TravelViewModel(
            tripRepository = tripRepo,
            checklistRepository = checklistRepo,
            momentRepository = momentRepo,
            travelStampRepository = stampRepo,
            userPreferencesRepository = userPrefs,
            database = db,
            locationSuggestionRepository = suggestionRepo
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `test 01 - valid active trip is accepted for navigation`() = runBlocking {
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Kyoto Trip",
                destination = "Kyoto, Japan",
                date = "2026-10-15",
                status = "UPCOMING"
            )
        )

        val isValid = vm.validateTripForNavigation(tripId)
        assertTrue("Valid existing active trip should be accepted", isValid)
    }

    @Test
    fun `test 02 - nonexistent trip is rejected`() = runBlocking {
        val isValid = vm.validateTripForNavigation(99999L)
        assertFalse("Nonexistent trip ID must be rejected", isValid)
    }

    @Test
    fun `test 03 - soft deleted trip is rejected`() = runBlocking {
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Deleted Trip",
                destination = "Paris",
                date = "2026-10-15",
                deletedAt = System.currentTimeMillis()
            )
        )

        val isValid = vm.validateTripForNavigation(tripId)
        assertFalse("Soft-deleted trip must not be accepted for navigation", isValid)
    }

    @Test
    fun `test 04 - completed trip is accepted for navigation`() = runBlocking {
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Completed Journey",
                destination = "Rome",
                date = "2025-05-10",
                status = "COMPLETED",
                completedAt = System.currentTimeMillis(),
                stampEarned = true
            )
        )

        val isValid = vm.validateTripForNavigation(tripId)
        assertTrue("Completed journey should remain a valid navigation target", isValid)
    }

    @Test
    fun `test 05 - stamped trip is accepted for navigation`() = runBlocking {
        val tripId = db.tripDao().insertTrip(
            TripEntity(
                name = "Stamped Journey",
                destination = "Tokyo",
                date = "2025-06-01",
                status = "COMPLETED",
                stampEarned = true
            )
        )

        val isValid = vm.validateTripForNavigation(tripId)
        assertTrue("Stamped journey should remain a valid navigation target", isValid)
    }

    @Test
    fun `test 06 - zero or negative trip ID is rejected`() = runBlocking {
        assertFalse("Zero trip ID must be rejected", vm.validateTripForNavigation(0L))
        assertFalse("Negative trip ID must be rejected", vm.validateTripForNavigation(-42L))
    }

    @Test
    fun `test 07 - pending reminder trip ID lifecycle`() {
        assertNull(vm.pendingReminderTripId.value)

        vm.onReminderNavigationRequested(42L)
        assertEquals(42L, vm.pendingReminderTripId.value)

        vm.clearPendingReminderTripId()
        assertNull(vm.pendingReminderTripId.value)
    }

    @Test
    fun `test 08 - non-positive reminder trip ID is ignored`() {
        vm.onReminderNavigationRequested(0L)
        assertNull(vm.pendingReminderTripId.value)

        vm.onReminderNavigationRequested(-1L)
        assertNull(vm.pendingReminderTripId.value)
    }
}
