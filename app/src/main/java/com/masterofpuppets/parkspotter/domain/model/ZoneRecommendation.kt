package com.masterofpuppets.parkspotter.domain.model

data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
)

enum class ZoneCategory {
    RESIDENTIAL_LOW_DENSITY,
    INDUSTRIAL_COMMERCIAL,
    SERVICE_24_7,
}

enum class ZoneRating {
    MOST_SUITABLE,
    SUITABLE,
    POSSIBLE_WITH_VERIFICATION,
    DISCOURAGED,
}

enum class ZoneFactorType {
    RESIDENTIAL_LAND_USE,
    LOW_DENSITY_HOUSING,
    RESIDENTIAL_ROADS,
    PUBLIC_ROAD_ACCESS,
    AVAILABLE_PARKING,
    NAMED_ZONE,
    INDUSTRIAL_LAND_USE,
    OPEN_24_7,
    TRAVEL_SERVICES,
    APARTMENT_DENSITY,
    NIGHTLIFE_NEARBY,
    MAJOR_ROAD_NEARBY,
    RESTRICTED_ACCESS,
    LIMITED_OSM_DATA,
}

data class ZoneFactor(
    val type: ZoneFactorType,
    val impact: Double,
)

data class ZoneRecommendation(
    val id: String,
    val osmReference: String,
    val name: String,
    val category: ZoneCategory,
    val boundary: List<GeoCoordinate>,
    val targetCoordinate: GeoCoordinate,
    val score: Int,
    val confidence: Int,
    val rating: ZoneRating,
    val factors: List<ZoneFactor>,
    val recommendedMicroRadiusMeters: Int,
)

data class ZoneSearchResult(
    val recommendations: List<ZoneRecommendation>,
    val routeAlternatives: List<ZoneRecommendation> = emptyList(),
    val sourceElementCount: Int,
)
