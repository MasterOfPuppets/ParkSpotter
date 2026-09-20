package com.masterofpuppets.parkspotter.domain.service.zone

data class ZoneClassificationConfig(
    val analysisRadiusMeters: Int = 6_000,
    val maxRecommendations: Int = 4,
    val maxRecommendationsPerCategory: Int = 2,
    val minimumDistanceBetweenResultsMeters: Int = 450,
    val minimumPolygonPoints: Int = 3,
    val nearbyNoiseRadiusMeters: Int = 350,
    val nearbyMajorRoadRadiusMeters: Int = 250,
    val minimumBuildingsForDensityScore: Int = 4,
    val minimumFeaturesForHighConfidence: Int = 12,
    val defaultMicroRadiusMeters: Int = 500,
    val routeAlternativesFinalFraction: Double = 0.30,
    val routeAlternativesCorridorMeters: Int = 1_500,
    val routeAlternativesMaximumResults: Int = 3,
    val routeAlternativesMinimumTripMeters: Int = 20_000,
    val routeAlternativesMaximumGeometryPoints: Int = 24,
    val scoreThresholds: ZoneScoreThresholds = ZoneScoreThresholds(),
    val confidenceWeights: ZoneConfidenceWeights = ZoneConfidenceWeights(),
    val residentialWeights: ResidentialZoneWeights = ResidentialZoneWeights(),
    val industrialWeights: IndustrialZoneWeights = IndustrialZoneWeights(),
    val serviceWeights: ServiceZoneWeights = ServiceZoneWeights(),
    val restrictedAccessValues: Set<String> = setOf("private", "no"),
    val accessibleRoadTypes: Set<String> = setOf(
        "residential",
        "living_street",
        "tertiary",
        "unclassified",
        "service",
    ),
    val majorRoadTypes: Set<String> = setOf("motorway", "trunk", "primary"),
    val houseBuildingTypes: Set<String> = setOf("detached", "house", "semidetached_house"),
    val apartmentBuildingTypes: Set<String> = setOf("apartments"),
    val nightlifeAmenities: Set<String> = setOf("nightclub", "bar", "pub"),
) {
    init {
        require(analysisRadiusMeters in 1_000..25_000)
        require(maxRecommendations > 0)
        require(maxRecommendationsPerCategory > 0)
        require(minimumPolygonPoints >= 3)
        require(defaultMicroRadiusMeters in 200..2_000)
        require(routeAlternativesFinalFraction in 0.05..1.0)
        require(routeAlternativesCorridorMeters in 100..5_000)
        require(routeAlternativesMaximumResults > 0)
        require(routeAlternativesMinimumTripMeters >= 0)
        require(routeAlternativesMaximumGeometryPoints >= 2)
    }
}

data class ZoneScoreThresholds(
    val mostSuitable: Int = 75,
    val suitable: Int = 60,
    val possibleWithVerification: Int = 40,
) {
    init {
        require(mostSuitable in 0..100)
        require(suitable in 0..mostSuitable)
        require(possibleWithVerification in 0..suitable)
    }
}

data class ZoneConfidenceWeights(
    val polygonGeometry: Double = 25.0,
    val namedFeature: Double = 15.0,
    val landUse: Double = 20.0,
    val featureCoverage: Double = 30.0,
    val publicRoadEvidence: Double = 10.0,
)

data class ResidentialZoneWeights(
    val baseScore: Double = 42.0,
    val houseDominance: Double = 24.0,
    val residentialRoads: Double = 14.0,
    val publicRoadAccess: Double = 8.0,
    val parking: Double = 6.0,
    val apartmentDensityPenalty: Double = 20.0,
    val nightlifePenalty: Double = 18.0,
    val majorRoadPenalty: Double = 10.0,
)

data class IndustrialZoneWeights(
    val baseScore: Double = 45.0,
    val accessibleRoads: Double = 18.0,
    val publicRoadAccess: Double = 12.0,
    val parking: Double = 8.0,
    val residentialPresencePenalty: Double = 8.0,
    val nightlifePenalty: Double = 8.0,
)

data class ServiceZoneWeights(
    val baseScore: Double = 55.0,
    val openAllDay: Double = 18.0,
    val surfaceParking: Double = 12.0,
    val namedFeature: Double = 5.0,
)
