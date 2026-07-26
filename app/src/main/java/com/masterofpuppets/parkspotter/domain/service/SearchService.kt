package com.masterofpuppets.parkspotter.domain.service

import com.masterofpuppets.parkspotter.domain.model.PlaceResult
import com.masterofpuppets.parkspotter.spike.ApiPlaceType
import com.masterofpuppets.parkspotter.ui.search.SearchSortMode

interface SearchService {
    suspend fun performSearch(
        lat: Double,
        lon: Double,
        radiusMeters: Int,
        selectedTypes: Set<ApiPlaceType>,
        sortMode: SearchSortMode
    ): Result<List<PlaceResult>>
}
