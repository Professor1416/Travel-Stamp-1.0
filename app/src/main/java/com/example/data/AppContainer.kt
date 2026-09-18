package com.example.data

import android.content.Context
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesRepositoryImpl
import com.example.data.notification.ReminderCoordinator
import com.example.data.notification.ReminderCoordinatorImpl
import com.example.data.notification.TripReminderScheduler
import com.example.data.notification.TripReminderSchedulerImpl
import com.example.data.repository.ChecklistRepository
import com.example.data.repository.ChecklistRepositoryImpl
import com.example.data.repository.LocationSuggestionRepository
import com.example.data.repository.LocationSuggestionRepositoryImpl
import com.example.data.repository.JourneyLocationRepository
import com.example.data.repository.JourneyLocationRepositoryImpl
import com.example.data.repository.InkMapRepository
import com.example.data.repository.InkMapRepositoryImpl
import com.example.data.repository.LocationSearchRepository
import com.example.data.repository.LocationSearchRepositoryImpl
import com.example.data.repository.MomentRepository
import com.example.data.repository.MomentRepositoryImpl
import com.example.data.repository.TravelStampRepository
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepository
import com.example.data.repository.TripRepositoryImpl
import com.example.data.datasource.ProxyLocationSearchDataSource
import com.example.data.datasource.ProxyLocationSearchService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

interface AppContainer {
    val database: TravelStampDatabase
    val tripRepository: TripRepository
    val checklistRepository: ChecklistRepository
    val momentRepository: MomentRepository
    val travelStampRepository: TravelStampRepository
    val userPreferencesRepository: UserPreferencesRepository
    val locationSuggestionRepository: LocationSuggestionRepository
    val journeyLocationRepository: JourneyLocationRepository
    val inkMapRepository: InkMapRepository
    val locationSearchRepository: LocationSearchRepository
    val tripReminderScheduler: TripReminderScheduler
    val reminderCoordinator: ReminderCoordinator
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: TravelStampDatabase by lazy {
        TravelStampDatabase.getDatabase(context)
    }

    override val tripReminderScheduler: TripReminderScheduler by lazy {
        TripReminderSchedulerImpl(context.applicationContext)
    }

    override val tripRepository: TripRepository by lazy {
        TripRepositoryImpl(
            tripDao = database.tripDao(),
            momentDao = database.momentDao(),
            context = context.applicationContext,
            reminderScheduler = tripReminderScheduler
        )
    }

    override val checklistRepository: ChecklistRepository by lazy {
        ChecklistRepositoryImpl(database.checklistDao())
    }

    override val momentRepository: MomentRepository by lazy {
        MomentRepositoryImpl(database.momentDao(), context.applicationContext)
    }

    override val travelStampRepository: TravelStampRepository by lazy {
        TravelStampRepositoryImpl(database.travelStampDao())
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepositoryImpl(context)
    }

    override val locationSuggestionRepository: LocationSuggestionRepository by lazy {
        LocationSuggestionRepositoryImpl(tripRepository)
    }

    override val journeyLocationRepository: JourneyLocationRepository by lazy {
        JourneyLocationRepositoryImpl(database.journeyLocationDao())
    }

    override val inkMapRepository: InkMapRepository by lazy {
        InkMapRepositoryImpl(
            stampRepository = travelStampRepository,
            locationRepository = journeyLocationRepository
        )
    }

    override val locationSearchRepository: LocationSearchRepository by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://travel-stamp-api.prashantdasnur11.workers.dev/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        val searchService = retrofit.create(ProxyLocationSearchService::class.java)
        val dataSource = ProxyLocationSearchDataSource(searchService)
        LocationSearchRepositoryImpl(dataSource)
    }

    override val reminderCoordinator: ReminderCoordinator by lazy {
        ReminderCoordinatorImpl(
            userPreferencesRepository = userPreferencesRepository,
            tripRepository = tripRepository,
            reminderScheduler = tripReminderScheduler
        )
    }
}
