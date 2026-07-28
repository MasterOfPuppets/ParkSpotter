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
        sortMode: SearchSortMode,
        contexts: Set<com.masterofpuppets.parkspotter.ui.search.SearchContext> = com.masterofpuppets.parkspotter.ui.search.defaultSearchContexts
    ): Result<Triple<List<PlaceResult>, List<PlaceResult>, List<com.masterofpuppets.parkspotter.spike.OverpassElement>>>

    fun applyLocalFilter(
        rawResults: List<PlaceResult>,
        selectedTypes: Set<ApiPlaceType>,
        sortMode: SearchSortMode,
        contexts: Set<com.masterofpuppets.parkspotter.ui.search.SearchContext>,
        rawOverpassElements: List<com.masterofpuppets.parkspotter.spike.OverpassElement> = emptyList()
    ): List<PlaceResult>
}
