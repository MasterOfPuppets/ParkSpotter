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
    val rawResults: List<PlaceResult>,
    val filteredResults: List<PlaceResult>,
    val selectedContexts: Set<SearchContext>,
    val shouldShowTooManyResultsWarning: Boolean,
    val rawOverpassElements: List<com.masterofpuppets.parkspotter.spike.OverpassElement> = emptyList(),
)

enum class SearchContext {
    RESIDENTIAL,
    COMMERCIAL_WORK,
    SERVICES_TRANSPORT_HEALTH,
    NATURE_DEDICATED,
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

val defaultSearchContexts: Set<SearchContext> = SearchContext.entries.toSet()

class SearchFormState(initialRadius: Int) {
    var locationQuery by mutableStateOf("")
    var radiusMeters by mutableIntStateOf(initialRadius)
    var selectedContexts by mutableStateOf(defaultSearchContexts)
    var sortMode by mutableStateOf(SearchSortMode.DISTANCE)
    var selectedTypes by mutableStateOf(defaultSearchTypes)

    fun toggleContext(context: SearchContext) {
        selectedContexts = if (selectedContexts.contains(context)) {
            selectedContexts - context
        } else {
            selectedContexts + context
        }
    }

    fun toggleType(type: ApiPlaceType) {
        if (selectedTypes.contains(type)) {
            if (selectedTypes.size > 1) {
                selectedTypes = selectedTypes - type
            }
        } else {
            selectedTypes = selectedTypes + type
        }
    }

    fun resetSearchParams(initialRadius: Int) {
        locationQuery = ""
        radiusMeters = initialRadius
    }

    fun resetFilterParams() {
        selectedContexts = defaultSearchContexts
        sortMode = SearchSortMode.DISTANCE
        selectedTypes = defaultSearchTypes
    }

    fun reset(initialRadius: Int) {
        resetSearchParams(initialRadius)
        resetFilterParams()
    }
}
