package com.masterofpuppets.parkspotter.ui.search

import com.masterofpuppets.parkspotter.domain.model.PlaceResult
import com.masterofpuppets.parkspotter.spike.ApiPlaceType
import java.util.UUID

data class SearchUiSettings(
    val resultsPageSize: Int = 10,
    val warnIfResultsAbove: Int = 150,
    val minRadiusMeters: Int = 300,
    val maxRadiusMeters: Int = 3000,
) {
    fun normalized(): SearchUiSettings = copy(
        resultsPageSize = resultsPageSize.coerceIn(1, 100),
        warnIfResultsAbove = warnIfResultsAbove.coerceAtLeast(1),
        minRadiusMeters = minRadiusMeters.coerceIn(100, 10000),
        maxRadiusMeters = maxRadiusMeters.coerceAtLeast(minRadiusMeters.coerceIn(100, 10000)),
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
    ApiPlaceType.RESIDENTIAL_STREET,
    ApiPlaceType.LIVING_STREET,
    ApiPlaceType.PARKING_AISLE,
)
