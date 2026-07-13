package com.masterofpuppets.parkspotter.ui.search

import com.masterofpuppets.parkspotter.domain.model.PlaceResult
import com.masterofpuppets.parkspotter.spike.ApiPlaceType
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class SearchUiSettings(
    val resultsPageSize: Int = 10,
    val warnIfResultsAbove: Int = 150,
    val minRadiusMeters: Int = 200,
    val maxRadiusMeters: Int = 800,
) {
    fun normalized(): SearchUiSettings = copy(
        resultsPageSize = resultsPageSize.coerceIn(1, 100),
        warnIfResultsAbove = warnIfResultsAbove.coerceAtLeast(1),
        minRadiusMeters = minRadiusMeters.coerceIn(100, 2000),
        maxRadiusMeters = maxRadiusMeters.coerceIn(minRadiusMeters, 5000),
    )
}

data class SearchSessionState(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val originLat: Double,
    val originLon: Double,
    val radiusMeters: Int,
    val context: SearchContext,
    val allResults: List<PlaceResult>,
    val shouldShowTooManyResultsWarning: Boolean,
)

enum class SearchOriginMode {
    CURRENT_LOCATION,
    MANUAL_COORDINATES,
}

enum class SearchContext {
    URBAN,
    SUBURBAN,
    MIXED,
}

enum class SearchSortMode {
    DISTANCE,
    SCORE,
}

enum class SearchViewMode {
    LIST,
    MAP,
}

val defaultSearchTypes: Set<ApiPlaceType> = setOf(
    ApiPlaceType.PARKING,
    ApiPlaceType.STREET,
)

class SearchFormState(initialRadius: Int) {
    var originMode by mutableStateOf(SearchOriginMode.CURRENT_LOCATION)
    var manualLat by mutableStateOf("")
    var manualLon by mutableStateOf("")
    var radiusMeters by mutableIntStateOf(initialRadius)
    var selectedContext by mutableStateOf(SearchContext.URBAN)
    var sortMode by mutableStateOf(SearchSortMode.DISTANCE)
    var selectedTypes by mutableStateOf(defaultSearchTypes)

    fun reset(initialRadius: Int) {
        originMode = SearchOriginMode.CURRENT_LOCATION
        manualLat = ""
        manualLon = ""
        radiusMeters = initialRadius
        selectedContext = SearchContext.URBAN
        sortMode = SearchSortMode.DISTANCE
        selectedTypes = defaultSearchTypes
    }
}
