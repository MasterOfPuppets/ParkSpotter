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
        contexts: Set<com.masterofpuppets.parkspotter.ui.search.SearchContext> = com.masterofpuppets.parkspotter.ui.search.defaultSearchContexts,
        freeOnly: Boolean = false
    ): Result<SearchExecutionResult>

    fun applyLocalFilter(
        rawResults: List<PlaceResult>,
        selectedTypes: Set<ApiPlaceType>,
        sortMode: SearchSortMode,
        contexts: Set<com.masterofpuppets.parkspotter.ui.search.SearchContext>,
        rawOverpassElements: List<com.masterofpuppets.parkspotter.spike.OverpassElement> = emptyList(),
        freeOnly: Boolean = false
    ): List<PlaceResult>
}

data class SearchExecutionResult(
    val rawResults: List<PlaceResult>,
    val filteredResults: List<PlaceResult>,
    val rawElements: List<com.masterofpuppets.parkspotter.spike.OverpassElement>,
    val routeGeometry: List<Pair<Double, Double>> = emptyList()
)
