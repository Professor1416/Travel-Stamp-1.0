package com.example.data

import android.content.Context
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesRepositoryImpl
import com.example.data.repository.ChecklistRepository
import com.example.data.repository.ChecklistRepositoryImpl
import com.example.data.repository.MomentRepository
import com.example.data.repository.MomentRepositoryImpl
import com.example.data.repository.TravelStampRepository
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepository
import com.example.data.repository.TripRepositoryImpl

interface AppContainer {
    val database: TravelStampDatabase
    val tripRepository: TripRepository
    val checklistRepository: ChecklistRepository
    val momentRepository: MomentRepository
    val travelStampRepository: TravelStampRepository
    val userPreferencesRepository: UserPreferencesRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: TravelStampDatabase by lazy {
        TravelStampDatabase.getDatabase(context)
    }

    override val tripRepository: TripRepository by lazy {
        TripRepositoryImpl(database.tripDao(), database.momentDao(), context.applicationContext)
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
}
