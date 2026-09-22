package com.masterofpuppets.parkspotter.domain.service.zone

import android.util.Log
import com.masterofpuppets.parkspotter.domain.model.GeoCoordinate
import com.masterofpuppets.parkspotter.domain.model.ZoneCategory
import com.masterofpuppets.parkspotter.domain.model.ZoneFactor
import com.masterofpuppets.parkspotter.domain.model.ZoneFactorType
import com.masterofpuppets.parkspotter.domain.model.ZoneRating
import com.masterofpuppets.parkspotter.domain.model.ZoneRecommendation
import com.masterofpuppets.parkspotter.domain.model.ZoneSearchResult
import com.masterofpuppets.parkspotter.domain.service.routing.OsrmRoutingClient
import com.masterofpuppets.parkspotter.spike.OverpassClient
import com.masterofpuppets.parkspotter.spike.OverpassElement
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class ZoneClassificationServiceImpl : ZoneClassificationService {

    private val zoneCache = ConcurrentHashMap<String, ZoneSearchResult>()

    override suspend fun classifyZones(
        latitude: Double,
        longitude: Double,
        originLatitude: Double?,
        originLongitude: Double?,
        config: ZoneClassificationConfig,
    ): Result<ZoneSearchResult> {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return Result.failure(IllegalArgumentException("Invalid search coordinates"))
        }

        val cacheKey = String.format(
            Locale.US,
            "%.4f_%.4f_%d_%.4f_%.4f",
            latitude,
            longitude,
            config.analysisRadiusMeters,
            originLatitude ?: latitude,
            originLongitude ?: longitude
        )

        zoneCache[cacheKey]?.let { cached ->
            Log.d("ParkSpotter", "ZoneClassificationServiceImpl: Returning cached zone result for key=$cacheKey")
            return Result.success(cached)
        }

        val destinationElements = OverpassClient.queryZoneClassificationData(
            lat = latitude,
            lon = longitude,
            radiusMeters = config.analysisRadiusMeters,
        ).getOrElse { error -> return Result.failure(error) }

        val destinationResult = classifyElements(
            elements = destinationElements,
            config = config,
            destinationLatitude = latitude,
            destinationLongitude = longitude,
        )
        val routeAlternatives = findRouteAlternatives(
            originLatitude = originLatitude,
            originLongitude = originLongitude,
            destinationLatitude = latitude,
            destinationLongitude = longitude,
            config = config,
        )
        val result = destinationResult.copy(routeAlternatives = routeAlternatives)
        zoneCache[cacheKey] = result
        return Result.success(result)
    }

    internal fun classifyElements(
        elements: List<OverpassElement>,
        config: ZoneClassificationConfig = ZoneClassificationConfig(),
        destinationLatitude: Double? = null,
        destinationLongitude: Double? = null,
    ): ZoneSearchResult {
        val dest = if (destinationLatitude != null && destinationLongitude != null) {
            GeoCoordinate(destinationLatitude, destinationLongitude)
        } else null

        val candidates = elements.mapNotNull { element ->
            val zone = classifyCandidate(element, elements, config)
                ?.takeUnless { it.category == ZoneCategory.SERVICE_24_7 }
            if (zone != null && dest != null) {
                val dist = distanceMeters(zone.targetCoordinate, dest).roundToInt()
                zone.copy(distanceMeters = dist)
            } else {
                zone
            }
        }
        return ZoneSearchResult(
            recommendations = selectDiverseResults(candidates, config),
            sourceElementCount = elements.size,
        )
    }

    private suspend fun findRouteAlternatives(
        originLatitude: Double?,
        originLongitude: Double?,
        destinationLatitude: Double,
        destinationLongitude: Double,
        config: ZoneClassificationConfig,
    ): List<ZoneRecommendation> {
        if (originLatitude == null || originLongitude == null) return emptyList()
        val route = OsrmRoutingClient.computeDirectRoute(
            originLat = originLatitude,
            originLon = originLongitude,
            destinationLat = destinationLatitude,
            destinationLon = destinationLongitude,
        ).getOrElse { return emptyList() }
        if (route.distanceMeters < config.routeAlternativesMinimumTripMeters) {
            return emptyList()
        }
        val finalRoute = finalRouteFraction(
            route.geometryCoordinates,
            config.routeAlternativesFinalFraction,
        )
        val sampledRoute = sampleRoute(
            finalRoute,
            config.routeAlternativesMaximumGeometryPoints,
        )
        if (sampledRoute.size < 2) return emptyList()

        val elements = OverpassClient.queryRouteServiceAlternatives(
            routePoints = sampledRoute,
            corridorMeters = config.routeAlternativesCorridorMeters,
        ).getOrElse { return emptyList() }
        val destination = GeoCoordinate(destinationLatitude, destinationLongitude)
        return elements.mapNotNull { element ->
            val zone = classifyCandidate(element, elements, config)
                ?.takeIf { it.category == ZoneCategory.SERVICE_24_7 }
            if (zone != null) {
                val dist = distanceMeters(zone.targetCoordinate, destination).roundToInt()
                zone.copy(distanceMeters = dist)
            } else null
        }.distinctBy { it.osmReference }
            .sortedWith(
                compareBy<ZoneRecommendation> { it.distanceMeters }
                    .thenByDescending { it.score },
            )
            .take(config.routeAlternativesMaximumResults)
    }

    internal fun finalRouteFraction(
        route: List<Pair<Double, Double>>,
        fraction: Double,
    ): List<Pair<Double, Double>> {
        if (route.size < 2) return route
        val segmentLengths = route.zipWithNext { first, second ->
            distanceMeters(
                GeoCoordinate(first.first, first.second),
                GeoCoordinate(second.first, second.second),
            )
        }
        val targetLength = segmentLengths.sum() * fraction.coerceIn(0.0, 1.0)
        var accumulated = 0.0
        for (index in segmentLengths.lastIndex downTo 0) {
            val segmentLength = segmentLengths[index]
            if (accumulated + segmentLength >= targetLength) {
                val requiredFromSegmentEnd = targetLength - accumulated
                val ratioFromStart = if (segmentLength == 0.0) {
                    1.0
                } else {
                    1.0 - requiredFromSegmentEnd / segmentLength
                }
                val start = route[index]
                val end = route[index + 1]
                val boundaryPoint = (
                    start.first + (end.first - start.first) * ratioFromStart
                    ) to (
                    start.second + (end.second - start.second) * ratioFromStart
                    )
                return listOf(boundaryPoint) + route.subList(index + 1, route.size)
            }
            accumulated += segmentLength
        }
        return route
    }

    private fun sampleRoute(
        route: List<Pair<Double, Double>>,
        maximumPoints: Int,
    ): List<Pair<Double, Double>> {
        if (route.size <= maximumPoints) return route
        return (0 until maximumPoints).map { sampleIndex ->
            val sourceIndex = sampleIndex * (route.lastIndex.toDouble() / (maximumPoints - 1))
            route[sourceIndex.toInt().coerceIn(0, route.lastIndex)]
        }.distinct()
    }

    private fun classifyCandidate(
        candidate: OverpassElement,
        elements: List<OverpassElement>,
        config: ZoneClassificationConfig,
    ): ZoneRecommendation? {
        val category = categoryOf(candidate) ?: return null
        if (hasRestrictedAccess(candidate, config)) return null

        val boundary = candidate.geometry
            .ifEmpty { candidate.members.flatMap { it.geometry } }
            .filter { it.lat in -90.0..90.0 && it.lon in -180.0..180.0 }
            .map { GeoCoordinate(it.lat, it.lon) }

        if (category != ZoneCategory.SERVICE_24_7 && boundary.size < config.minimumPolygonPoints) {
            return null
        }

        val center = candidateCenter(candidate, boundary) ?: return null
        val contained = if (boundary.size >= config.minimumPolygonPoints) {
            elements.filter { element ->
                element !== candidate &&
                    element.hasUsableCenter() &&
                    isPointInsidePolygon(element.latitude, element.longitude, boundary)
            }
        } else {
            elements.filter { element ->
                element !== candidate &&
                    element.hasUsableCenter() &&
                    distanceMeters(center, element.coordinate()) <= config.nearbyNoiseRadiusMeters
            }
        }

        if (contained.any { it.tags["barrier"] == "gate" && hasRestrictedAccess(it, config) }) {
            return null
        }

        val accessibleRoads = contained.filter {
            it.tags["highway"] in config.accessibleRoadTypes && !hasRestrictedAccess(it, config)
        }
        if (category != ZoneCategory.SERVICE_24_7 && accessibleRoads.isEmpty()) return null

        val factors = when (category) {
            ZoneCategory.RESIDENTIAL_LOW_DENSITY ->
                scoreResidential(candidate, contained, center, elements, config)
            ZoneCategory.INDUSTRIAL_COMMERCIAL ->
                scoreIndustrial(candidate, contained, center, elements, config)
            ZoneCategory.SERVICE_24_7 ->
                scoreService(candidate, config)
        }

        val score = factors.sumOf { it.impact }.toInt().coerceIn(0, 100)
        val target = accessibleRoads
            .minByOrNull { distanceMeters(center, it.coordinate()) }
            ?.coordinate()
            ?: center
        val name = resolveName(candidate, contained, center)
        val confidence = calculateConfidence(
            candidate = candidate,
            boundary = boundary,
            contained = contained,
            accessibleRoads = accessibleRoads,
            config = config,
        )

        return ZoneRecommendation(
            id = "${candidate.type}_${candidate.id}",
            osmReference = "${candidate.type}/${candidate.id}",
            name = name,
            category = category,
            boundary = boundary,
            targetCoordinate = target,
            score = score,
            confidence = confidence,
            rating = ratingFor(score, config.scoreThresholds),
            factors = factors.filter { it.impact != 0.0 }.sortedByDescending { kotlin.math.abs(it.impact) },
            recommendedMicroRadiusMeters = config.defaultMicroRadiusMeters,
        )
    }

    private fun scoreResidential(
        candidate: OverpassElement,
        contained: List<OverpassElement>,
        center: GeoCoordinate,
        allElements: List<OverpassElement>,
        config: ZoneClassificationConfig,
    ): List<ZoneFactor> {
        val weights = config.residentialWeights
        val buildings = contained.filter { it.tags.containsKey("building") }
        val houses = buildings.count { it.tags["building"] in config.houseBuildingTypes }
        val apartments = buildings.count { it.tags["building"] in config.apartmentBuildingTypes }
        val knownDensityBuildings = houses + apartments
        val houseRatio = if (knownDensityBuildings == 0) 0.0 else houses.toDouble() / knownDensityBuildings
        val apartmentRatio = if (knownDensityBuildings == 0) 0.0 else apartments.toDouble() / knownDensityBuildings
        val residentialRoads = contained.count {
            it.tags["highway"] == "residential" || it.tags["highway"] == "living_street"
        }
        val accessibleRoads = contained.count {
            it.tags["highway"] in config.accessibleRoadTypes && !hasRestrictedAccess(it, config)
        }
        val parkingCount = contained.count { it.tags["amenity"] == "parking" }
        val nightlifeNearby = allElements.any {
            it.tags["amenity"] in config.nightlifeAmenities &&
                it.hasUsableCenter() &&
                distanceMeters(center, it.coordinate()) <= config.nearbyNoiseRadiusMeters
        }
        val majorRoadNearby = allElements.any {
            it.tags["highway"] in config.majorRoadTypes &&
                it.hasUsableCenter() &&
                distanceMeters(center, it.coordinate()) <= config.nearbyMajorRoadRadiusMeters
        }

        return buildList {
            add(ZoneFactor(ZoneFactorType.RESIDENTIAL_LAND_USE, weights.baseScore))
            if (knownDensityBuildings >= config.minimumBuildingsForDensityScore) {
                add(ZoneFactor(ZoneFactorType.LOW_DENSITY_HOUSING, weights.houseDominance * houseRatio))
                add(ZoneFactor(ZoneFactorType.APARTMENT_DENSITY, -weights.apartmentDensityPenalty * apartmentRatio))
            } else {
                add(ZoneFactor(ZoneFactorType.LIMITED_OSM_DATA, -5.0))
            }
            if (residentialRoads > 0) {
                add(ZoneFactor(ZoneFactorType.RESIDENTIAL_ROADS, weights.residentialRoads))
            }
            if (accessibleRoads > 0) {
                add(ZoneFactor(ZoneFactorType.PUBLIC_ROAD_ACCESS, weights.publicRoadAccess))
            }
            if (parkingCount > 0) {
                add(ZoneFactor(ZoneFactorType.AVAILABLE_PARKING, weights.parking))
            }
            if (!candidate.tags["name"].isNullOrBlank()) {
                add(ZoneFactor(ZoneFactorType.NAMED_ZONE, 2.0))
            }
            if (nightlifeNearby) {
                add(ZoneFactor(ZoneFactorType.NIGHTLIFE_NEARBY, -weights.nightlifePenalty))
            }
            if (majorRoadNearby) {
                add(ZoneFactor(ZoneFactorType.MAJOR_ROAD_NEARBY, -weights.majorRoadPenalty))
            }
        }
    }

    private fun scoreIndustrial(
        candidate: OverpassElement,
        contained: List<OverpassElement>,
        center: GeoCoordinate,
        allElements: List<OverpassElement>,
        config: ZoneClassificationConfig,
    ): List<ZoneFactor> {
        val weights = config.industrialWeights
        val accessibleRoadCount = contained.count {
            it.tags["highway"] in config.accessibleRoadTypes && !hasRestrictedAccess(it, config)
        }
        val parkingCount = contained.count { it.tags["amenity"] == "parking" }
        val residentialBuildings = contained.count {
            it.tags["building"] in config.houseBuildingTypes ||
                it.tags["building"] in config.apartmentBuildingTypes
        }
        val nightlifeNearby = allElements.any {
            it.tags["amenity"] in config.nightlifeAmenities &&
                it.hasUsableCenter() &&
                distanceMeters(center, it.coordinate()) <= config.nearbyNoiseRadiusMeters
        }

        return buildList {
            add(ZoneFactor(ZoneFactorType.INDUSTRIAL_LAND_USE, weights.baseScore))
            if (accessibleRoadCount > 0) {
                add(ZoneFactor(ZoneFactorType.PUBLIC_ROAD_ACCESS, weights.accessibleRoads))
                add(ZoneFactor(ZoneFactorType.PUBLIC_ROAD_ACCESS, weights.publicRoadAccess))
            }
            if (parkingCount > 0) {
                add(ZoneFactor(ZoneFactorType.AVAILABLE_PARKING, weights.parking))
            }
            if (!candidate.tags["name"].isNullOrBlank()) {
                add(ZoneFactor(ZoneFactorType.NAMED_ZONE, 3.0))
            }
            if (residentialBuildings > 0) {
                add(ZoneFactor(ZoneFactorType.APARTMENT_DENSITY, -weights.residentialPresencePenalty))
            }
            if (nightlifeNearby) {
                add(ZoneFactor(ZoneFactorType.NIGHTLIFE_NEARBY, -weights.nightlifePenalty))
            }
        }
    }

    private fun scoreService(
        candidate: OverpassElement,
        config: ZoneClassificationConfig,
    ): List<ZoneFactor> {
        val weights = config.serviceWeights
        val isAlwaysOpen = candidate.tags["opening_hours"]
            ?.replace(" ", "")
            ?.equals("24/7", ignoreCase = true) == true
        val hasSurfaceParking = candidate.tags["parking"] == "surface" ||
            candidate.tags["amenity"] == "parking"
        return buildList {
            add(ZoneFactor(ZoneFactorType.TRAVEL_SERVICES, weights.baseScore))
            if (isAlwaysOpen || candidate.tags["highway"] in setOf("services", "rest_area")) {
                add(ZoneFactor(ZoneFactorType.OPEN_24_7, weights.openAllDay))
            }
            if (hasSurfaceParking || candidate.tags["highway"] in setOf("services", "rest_area")) {
                add(ZoneFactor(ZoneFactorType.AVAILABLE_PARKING, weights.surfaceParking))
            }
            if (!candidate.tags["name"].isNullOrBlank() || !candidate.tags["brand"].isNullOrBlank()) {
                add(ZoneFactor(ZoneFactorType.NAMED_ZONE, weights.namedFeature))
            }
        }
    }

    private fun categoryOf(element: OverpassElement): ZoneCategory? = when {
        element.tags["landuse"] == "residential" -> ZoneCategory.RESIDENTIAL_LOW_DENSITY
        element.tags["landuse"] == "industrial" || element.tags["landuse"] == "commercial" ->
            ZoneCategory.INDUSTRIAL_COMMERCIAL
        element.tags["highway"] == "services" || element.tags["highway"] == "rest_area" ->
            ZoneCategory.SERVICE_24_7
        element.tags["amenity"] == "fuel" &&
            element.tags["opening_hours"]?.replace(" ", "")?.equals("24/7", ignoreCase = true) == true &&
            element.tags["parking"] == "surface" -> ZoneCategory.SERVICE_24_7
        else -> null
    }

    private fun calculateConfidence(
        candidate: OverpassElement,
        boundary: List<GeoCoordinate>,
        contained: List<OverpassElement>,
        accessibleRoads: List<OverpassElement>,
        config: ZoneClassificationConfig,
    ): Int {
        val weights = config.confidenceWeights
        val coverageRatio = (
            contained.size.toDouble() / config.minimumFeaturesForHighConfidence.coerceAtLeast(1)
            ).coerceIn(0.0, 1.0)
        val value =
            (if (boundary.size >= config.minimumPolygonPoints) weights.polygonGeometry else 0.0) +
                (if (!candidate.tags["name"].isNullOrBlank() || !candidate.tags["brand"].isNullOrBlank()) {
                    weights.namedFeature
                } else 0.0) +
                (if (candidate.tags.containsKey("landuse") ||
                    candidate.tags["highway"] in setOf("services", "rest_area")
                ) weights.landUse else 0.0) +
                weights.featureCoverage * coverageRatio +
                (if (accessibleRoads.isNotEmpty()) weights.publicRoadEvidence else 0.0)
        return value.toInt().coerceIn(0, 100)
    }

    private fun resolveName(
        candidate: OverpassElement,
        contained: List<OverpassElement>,
        center: GeoCoordinate,
    ): String {
        candidate.tags["name"]?.takeIf { it.isNotBlank() }?.let { return it }
        candidate.tags["brand"]?.takeIf { it.isNotBlank() }?.let { return it }
        contained
            .filter { it.tags["place"] == "neighbourhood" || it.tags["place"] == "suburb" }
            .minByOrNull { distanceMeters(center, it.coordinate()) }
            ?.tags
            ?.get("name")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        contained
            .filter { !it.tags["name"].isNullOrBlank() && it.tags.containsKey("highway") }
            .minByOrNull { distanceMeters(center, it.coordinate()) }
            ?.tags
            ?.get("name")
            ?.let { return it }
        return ""
    }

    private fun selectDiverseResults(
        candidates: List<ZoneRecommendation>,
        config: ZoneClassificationConfig,
    ): List<ZoneRecommendation> {
        val selected = mutableListOf<ZoneRecommendation>()
        val categoryCounts = mutableMapOf<ZoneCategory, Int>()
        val sortedCandidates = candidates.sortedWith(
            compareBy<ZoneRecommendation> { it.distanceMeters }
                .thenByDescending { it.score }
                .thenByDescending { it.confidence },
        )
        for (candidate in sortedCandidates) {
            if (selected.size >= config.maxRecommendations) break
            if ((categoryCounts[candidate.category] ?: 0) >= config.maxRecommendationsPerCategory) continue
            if (selected.any {
                    distanceMeters(it.targetCoordinate, candidate.targetCoordinate) <
                        config.minimumDistanceBetweenResultsMeters
                }) continue
            selected += candidate
            categoryCounts[candidate.category] = (categoryCounts[candidate.category] ?: 0) + 1
        }
        return selected
    }

    private fun ratingFor(score: Int, thresholds: ZoneScoreThresholds): ZoneRating = when {
        score >= thresholds.mostSuitable -> ZoneRating.MOST_SUITABLE
        score >= thresholds.suitable -> ZoneRating.SUITABLE
        score >= thresholds.possibleWithVerification -> ZoneRating.POSSIBLE_WITH_VERIFICATION
        else -> ZoneRating.DISCOURAGED
    }

    private fun hasRestrictedAccess(
        element: OverpassElement,
        config: ZoneClassificationConfig,
    ): Boolean = element.tags["access"]?.lowercase() in config.restrictedAccessValues

    private fun candidateCenter(
        element: OverpassElement,
        boundary: List<GeoCoordinate>,
    ): GeoCoordinate? {
        if (element.hasUsableCenter()) return element.coordinate()
        if (boundary.isEmpty()) return null
        return GeoCoordinate(
            latitude = boundary.map { it.latitude }.average(),
            longitude = boundary.map { it.longitude }.average(),
        )
    }

    private fun OverpassElement.hasUsableCenter(): Boolean =
        latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)

    private fun OverpassElement.coordinate(): GeoCoordinate =
        GeoCoordinate(latitude, longitude)

    private fun isPointInsidePolygon(
        latitude: Double,
        longitude: Double,
        polygon: List<GeoCoordinate>,
    ): Boolean {
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            val intersects = (current.latitude > latitude) != (previous.latitude > latitude) &&
                longitude < (previous.longitude - current.longitude) *
                (latitude - current.latitude) /
                (previous.latitude - current.latitude) + current.longitude
            if (intersects) inside = !inside
            previous = current
        }
        return inside
    }

    private fun distanceMeters(first: GeoCoordinate, second: GeoCoordinate): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(second.latitude - first.latitude)
        val dLon = Math.toRadians(second.longitude - first.longitude)
        val lat1 = Math.toRadians(first.latitude)
        val lat2 = Math.toRadians(second.latitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

}
