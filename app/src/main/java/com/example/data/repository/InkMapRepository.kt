package com.example.data.repository

import com.example.data.model.InkMapData
import com.example.data.util.InkMapDataMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface InkMapRepository {
    fun observeInkMapData(): Flow<InkMapData>
}

class InkMapRepositoryImpl(
    private val stampRepository: TravelStampRepository,
    private val locationRepository: JourneyLocationRepository
) : InkMapRepository {

    override fun observeInkMapData(): Flow<InkMapData> {
        return combine(
            stampRepository.observeAllActiveStamps(),
            locationRepository.observeAllActiveLocations()
        ) { stamps, locations ->
            InkMapDataMapper.mapInkMapData(stamps, locations)
        }
    }
}
