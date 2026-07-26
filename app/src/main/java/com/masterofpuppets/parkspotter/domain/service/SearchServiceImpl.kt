package com.masterofpuppets.parkspotter.domain.service

import com.masterofpuppets.parkspotter.domain.model.PlaceResult
import com.masterofpuppets.parkspotter.spike.ApiPlaceType
import com.masterofpuppets.parkspotter.spike.OverpassClient
import com.masterofpuppets.parkspotter.spike.OverpassElement
import com.masterofpuppets.parkspotter.ui.search.SearchSortMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SearchServiceImpl : SearchService {

    override suspend fun performSearch(
        lat: Double,
        lon: Double,
        radiusMeters: Int,
        selectedTypes: Set<ApiPlaceType>,
        sortMode: SearchSortMode
    ): Result<List<PlaceResult>> {
        
        val result = OverpassClient.queryParkingAreas(lat, lon, radiusMeters)
        
        return result.map { elements ->
            val mapped = elements
                .filter { selectedTypes.contains(it.placeType) }
                .map { it.toPlaceResult(lat, lon) }
            
            val sanitized = applySanitization(mapped)
            
            when (sortMode) {
                SearchSortMode.DISTANCE -> sanitized.sortedBy { it.distanceMeters }
                SearchSortMode.SCORE -> sanitized.sortedByDescending { it.score ?: 0f }
            }
        }
    }

    private fun OverpassElement.toPlaceResult(originLat: Double, originLon: Double): PlaceResult {
        val distance = haversineDistanceMeters(originLat, originLon, latitude, longitude)
        return PlaceResult(
            osmType = type,
            osmId = id,
            name = displayName,
            latitude = latitude,
            longitude = longitude,
            placeType = placeType.key,
            tags = tags,
            distanceMeters = distance,
            score = null,
        )
    }

    private fun applySanitization(results: List<PlaceResult>): List<PlaceResult> {
        var workingList = results

        // Rule 1: Exact coordinates sanitization
        workingList = sanitizeExactDuplicates(workingList)

        // Rule 2: Suppress access aisles near parking amenities (Situation 2 & 3)
        workingList = suppressAccessAislesByProximity(workingList)

        // Rule 3: Deduplicate redundant aisles (Situation 1)
        workingList = deduplicateRedundantAisles(workingList)

        // Rule 4: Sanitize streets by isolation and survival
        workingList = sanitizeStreetsByIsolationAndSurvival(workingList)

        return workingList
    }

    private fun sanitizeExactDuplicates(results: List<PlaceResult>): List<PlaceResult> {
        return results.mapIndexed { index, current ->
            if (current.isSanitized) return@mapIndexed current

            val duplicate = results.subList(0, index).find { other ->
                !other.isSanitized &&
                        haversineDistanceMeters(current.latitude, current.longitude, other.latitude, other.longitude) < 2
            }

            if (duplicate != null) {
                if (current.osmType == "node" && duplicate.osmType == "way") {
                    current.copy(isSanitized = true, sanitizationReason = "Exact coordinate duplicate (kept way over node)")
                } else if (current.osmType == "way" && duplicate.osmType == "node") {
                    current
                } else {
                    current.copy(isSanitized = true, sanitizationReason = "Exact coordinate duplicate")
                }
            } else {
                current
            }
        }
    }

    private fun suppressAccessAislesByProximity(results: List<PlaceResult>): List<PlaceResult> {
        val parkingAmenities = results.filter { it.tags["amenity"] == "parking" && !it.isSanitized }

        return results.map { current ->
            if (current.isSanitized || current.tags["service"] != "parking_aisle") return@map current

            val nearbyParking = parkingAmenities.find { parking ->
                haversineDistanceMeters(current.latitude, current.longitude, parking.latitude, parking.longitude) <= 35
            }

            if (nearbyParking != null) {
                current.copy(isSanitized = true, sanitizationReason = "Aisle suppressed by nearby parking amenity (${nearbyParking.osmId})")
            } else {
                current
            }
        }
    }

    private fun deduplicateRedundantAisles(results: List<PlaceResult>): List<PlaceResult> {
        return results.mapIndexed { index, current ->
            if (current.isSanitized || current.tags["service"] != "parking_aisle") return@mapIndexed current

            val nearbyAisle = results.subList(0, index).find { other ->
                !other.isSanitized &&
                        other.tags["service"] == "parking_aisle" &&
                        haversineDistanceMeters(current.latitude, current.longitude, other.latitude, other.longitude) <= 35
            }

            if (nearbyAisle != null) {
                if (current.distanceMeters > nearbyAisle.distanceMeters) {
                    current
                } else {
                    current.copy(isSanitized = true, sanitizationReason = "Redundant aisle (kept one further from origin)")
                }
            } else {
                val betterAisleLater = results.subList(index + 1, results.size).find { other ->
                    other.tags["service"] == "parking_aisle" &&
                            haversineDistanceMeters(current.latitude, current.longitude, other.latitude, other.longitude) <= 35 &&
                            other.distanceMeters > current.distanceMeters
                }
                if (betterAisleLater != null) {
                    current.copy(isSanitized = true, sanitizationReason = "Redundant aisle (better candidate exists)")
                } else {
                    current
                }
            }
        }
    }

    private fun sanitizeStreetsByIsolationAndSurvival(results: List<PlaceResult>): List<PlaceResult> {
        val streetResults = results.filter { it.placeType == ApiPlaceType.STREET.key && !it.isSanitized }
        if (streetResults.isEmpty()) return results

        val groupedByStreet = streetResults.filter { !it.name.isNullOrBlank() }.groupBy { it.name }
        val sanitizedIds = mutableSetOf<Long>()
        val savedIds = mutableSetOf<Long>()

        groupedByStreet.forEach { (_, pins) ->
            if (pins.size <= 1) return@forEach

            val clusteredPins = pins.filter { current ->
                pins.any { other ->
                    current.osmId != other.osmId &&
                            haversineDistanceMeters(current.latitude, current.longitude, other.latitude, other.longitude) < 35
                }
            }
            val isolatedPins = pins.filter { it !in clusteredPins }

            if (isolatedPins.isNotEmpty()) {
                clusteredPins.forEach { sanitizedIds.add(it.osmId) }
            } else {
                val richestPin = clusteredPins.maxByOrNull { it.tags.size }
                if (richestPin != null) {
                    clusteredPins.forEach { pin ->
                        if (pin.osmId != richestPin.osmId) {
                            sanitizedIds.add(pin.osmId)
                        } else {
                            savedIds.add(pin.osmId)
                        }
                    }
                }
            }
        }

        return results.map { res ->
            if (sanitizedIds.contains(res.osmId) && !savedIds.contains(res.osmId)) {
                res.copy(isSanitized = true, sanitizationReason = "Clustered street segment suppressed by isolation rule")
            } else {
                res
            }
        }
    }

    private fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadius * c).toInt()
    }
}
