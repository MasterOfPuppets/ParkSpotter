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

    companion object {
        private const val DIAG_TAG = "ParkSpotter_FilterDiag"
    }

    override suspend fun performSearch(
        lat: Double,
        lon: Double,
        radiusMeters: Int,
        selectedTypes: Set<ApiPlaceType>,
        sortMode: SearchSortMode,
        contexts: Set<com.masterofpuppets.parkspotter.ui.search.SearchContext>
    ): Result<Triple<List<PlaceResult>, List<PlaceResult>, List<OverpassElement>>> {
        
        val result = OverpassClient.queryParkingAreas(lat, lon, radiusMeters)
        
        return result.map { elements ->
            val rawList = elements
                .filter { it.placeType != ApiPlaceType.UNKNOWN }
                .map { it.toPlaceResult(lat, lon) }
            
            android.util.Log.d(DIAG_TAG, "=== NEW SEARCH EXECUTION ===")
            android.util.Log.d(DIAG_TAG, "Raw API Overpass Elements: ${elements.size} | Valid PlaceResults: ${rawList.size}")

            val filtered = applyLocalFilterInternal(rawList, selectedTypes, sortMode, contexts, elements)
            
            Triple(rawList, filtered, elements)
        }
    }

    override fun applyLocalFilter(
        rawResults: List<PlaceResult>,
        selectedTypes: Set<ApiPlaceType>,
        sortMode: SearchSortMode,
        contexts: Set<com.masterofpuppets.parkspotter.ui.search.SearchContext>,
        rawOverpassElements: List<OverpassElement>
    ): List<PlaceResult> {
        android.util.Log.d(DIAG_TAG, "=== APPLY LOCAL FILTER (OFFLINE / CACHED) ===")
        return applyLocalFilterInternal(rawResults, selectedTypes, sortMode, contexts, rawOverpassElements)
    }

    private fun applyLocalFilterInternal(
        rawList: List<PlaceResult>,
        selectedTypes: Set<ApiPlaceType>,
        sortMode: SearchSortMode,
        contexts: Set<com.masterofpuppets.parkspotter.ui.search.SearchContext>,
        allElements: List<OverpassElement>
    ): List<PlaceResult> {
        val selectedTypeKeys = selectedTypes.map { it.key }.toSet()
        val activeContextsStr = if (contexts.isEmpty() || contexts.size == com.masterofpuppets.parkspotter.ui.search.SearchContext.entries.size) "ALL (RES, COMM, SERV, NAT)" else contexts.joinToString(", ")

        android.util.Log.d(DIAG_TAG, "==========================================================================================")
        android.util.Log.d(DIAG_TAG, ">>> EXECUÇÃO DE FILTRO DE PESQUISA <<<")
        android.util.Log.d(DIAG_TAG, ">>> FILTRO APLICADO: Contextos = [$activeContextsStr] | Tipos Base = ${selectedTypes.map { it.key }} | Ordenação = $sortMode")
        android.util.Log.d(DIAG_TAG, "==========================================================================================")

        // 1. LISTA ORIGINAL COMPLETA (RAW LIST)
        android.util.Log.d(DIAG_TAG, "--- [1/3] LISTA ORIGINAL DA API (RAW LIST - Total: ${rawList.size}) ---")
        rawList.forEachIndexed { idx, item ->
            val tagsStr = item.tags.entries.joinToString(", ") { "${it.key}=${it.value}" }
            android.util.Log.d(DIAG_TAG, "  RAW ITEM #$idx: ID=${item.osmId} | Type=${item.placeType} | Name='${item.name ?: "Unnamed"}' | Tags=[$tagsStr]")
        }

        val isAllContexts = contexts.isEmpty() || contexts.size == com.masterofpuppets.parkspotter.ui.search.SearchContext.entries.size

        val evaluatedItems = rawList.map { place ->
            val passesType = selectedTypeKeys.contains(place.placeType)
            val isRes = matchesSingleContext(place, com.masterofpuppets.parkspotter.ui.search.SearchContext.RESIDENTIAL, allElements)
            val isComm = matchesSingleContext(place, com.masterofpuppets.parkspotter.ui.search.SearchContext.COMMERCIAL_WORK, allElements)
            val isServ = matchesSingleContext(place, com.masterofpuppets.parkspotter.ui.search.SearchContext.SERVICES_TRANSPORT_HEALTH, allElements)
            val isNat = matchesSingleContext(place, com.masterofpuppets.parkspotter.ui.search.SearchContext.NATURE_DEDICATED, allElements)

            val passesContext = if (isAllContexts) true else {
                contexts.any { ctx -> matchesSingleContext(place, ctx, allElements) }
            }

            val tagsStr = place.tags.entries.joinToString(", ") { "${it.key}=${it.value}" }

            ItemEvaluation(
                place = place,
                passesType = passesType,
                passesContext = passesContext,
                isRes = isRes,
                isComm = isComm,
                isServ = isServ,
                isNat = isNat,
                tagsStr = tagsStr
            )
        }

        val excludedByType = evaluatedItems.filter { !it.passesType }
        val excludedByContext = evaluatedItems.filter { it.passesType && !it.passesContext }
        val contextSurvivors = evaluatedItems.filter { it.passesType && it.passesContext }

        val sanitized = applySanitization(contextSurvivors.map { it.place })
        val sanitizedDropped = contextSurvivors.filter { ev -> sanitized.find { it.osmId == ev.place.osmId }?.isSanitized == true }
        val finalSurvivorsList = sanitized.filter { !it.isSanitized }

        // 2. LISTA DOS FILTRADOS / ELIMINADOS
        val totalExcluded = excludedByType.size + excludedByContext.size + sanitizedDropped.size
        android.util.Log.d(DIAG_TAG, "--- [2/3] LISTA DOS FILTRADOS / ELIMINADOS (TOTAL ELIMINADOS: $totalExcluded) ---")
        if (excludedByType.isNotEmpty()) {
            excludedByType.forEach { ev ->
                android.util.Log.d(DIAG_TAG, "  ELIMINADO [MOTIVO: TIPO BASE] -> ID=${ev.place.osmId} | Type=${ev.place.placeType} | Name='${ev.place.name ?: "Unnamed"}'")
            }
        }
        if (excludedByContext.isNotEmpty()) {
            excludedByContext.forEach { ev ->
                android.util.Log.d(DIAG_TAG, "  ELIMINADO [MOTIVO: CONTEXTO] -> ID=${ev.place.osmId} | Type=${ev.place.placeType} | Name='${ev.place.name ?: "Unnamed"}' | MatchesContexts -> [RES:${ev.isRes}, COMM:${ev.isComm}, SERV:${ev.isServ}, NAT:${ev.isNat}] | Tags=[${ev.tagsStr}]")
            }
        }
        if (sanitizedDropped.isNotEmpty()) {
            sanitizedDropped.forEach { ev ->
                val reason = sanitized.find { it.osmId == ev.place.osmId }?.sanitizationReason ?: "Sanitized"
                android.util.Log.d(DIAG_TAG, "  ELIMINADO [MOTIVO: SANITIZAÇÃO] -> ID=${ev.place.osmId} | Name='${ev.place.name ?: "Unnamed"}' | Razão: $reason")
            }
        }
        if (totalExcluded == 0) {
            android.util.Log.d(DIAG_TAG, "  (Nenhum item foi eliminado nesta filtragem)")
        }

        val scored = applyContextScoring(finalSurvivorsList, contexts, allElements)
        val finalSortedSurvivors = when (sortMode) {
            SearchSortMode.DISTANCE -> scored.sortedBy { it.distanceMeters }
            SearchSortMode.SCORE -> scored.sortedByDescending { it.score ?: 0f }
        }

        // 3. LISTA DOS SOBREVIVENTES
        android.util.Log.d(DIAG_TAG, "--- [3/3] LISTA DOS SOBREVIVENTES / MANTIDOS (TOTAL SOBREVIVENTES: ${finalSortedSurvivors.size}) ---")
        finalSortedSurvivors.forEachIndexed { i, surv ->
            val matchFlags = evaluatedItems.find { it.place.osmId == surv.osmId }
            val flagsStr = if (matchFlags != null) "[RES:${matchFlags.isRes}, COMM:${matchFlags.isComm}, SERV:${matchFlags.isServ}, NAT:${matchFlags.isNat}]" else ""
            android.util.Log.d(DIAG_TAG, "  SOBREVIVENTE #$i: ID=${surv.osmId} | Name='${surv.name ?: "Unnamed"}' | Type=${surv.placeType} | Dist=${surv.distanceMeters}m | Score=${surv.score} | ContextMatches=$flagsStr")
        }

        android.util.Log.d(DIAG_TAG, "=== RESUMO DA FILTRAGEM -> Total Original: ${rawList.size} | Eliminados por Tipo: ${excludedByType.size} | Eliminados por Contexto: ${excludedByContext.size} | Eliminados por Sanitização: ${sanitizedDropped.size} | SOBREVIVENTES FINAIS: ${finalSortedSurvivors.size} ===")
        android.util.Log.d(DIAG_TAG, "==========================================================================================")

        return finalSortedSurvivors
    }

    private data class ItemEvaluation(
        val place: PlaceResult,
        val passesType: Boolean,
        val passesContext: Boolean,
        val isRes: Boolean,
        val isComm: Boolean,
        val isServ: Boolean,
        val isNat: Boolean,
        val tagsStr: String
    )

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

        // Rule 5: Suppress street or parking by proximity based on information density (35m)
        workingList = suppressStreetOrParkingByProximity(workingList)

        return workingList
    }

    private fun suppressStreetOrParkingByProximity(results: List<PlaceResult>): List<PlaceResult> {
        val streetList = results.filter { it.placeType == ApiPlaceType.STREET.key && !it.isSanitized }
        val parkingList = results.filter { it.placeType == ApiPlaceType.PARKING.key && !it.isSanitized }

        val toSanitizeIds = mutableSetOf<Long>()

        for (street in streetList) {
            for (parking in parkingList) {
                val dist = haversineDistanceMeters(street.latitude, street.longitude, parking.latitude, parking.longitude)
                if (dist <= 35) {
                    if (street.tags.size >= parking.tags.size) {
                        toSanitizeIds.add(parking.osmId)
                    } else {
                        toSanitizeIds.add(street.osmId)
                    }
                }
            }
        }

        return results.map { current ->
            if (toSanitizeIds.contains(current.osmId) && !current.isSanitized) {
                current.copy(isSanitized = true, sanitizationReason = "Suppressed by nearby richer street/parking within 35m")
            } else {
                current
            }
        }
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

    private fun applyContextScoring(
        results: List<PlaceResult>,
        contexts: Set<com.masterofpuppets.parkspotter.ui.search.SearchContext>,
        allElements: List<OverpassElement>
    ): List<PlaceResult> {
        val effectiveContexts = if (contexts.isEmpty() || contexts.size == com.masterofpuppets.parkspotter.ui.search.SearchContext.entries.size) {
            com.masterofpuppets.parkspotter.ui.search.SearchContext.entries.toSet()
        } else {
            contexts
        }

        return results.map { result ->
            val totalScore = effectiveContexts.fold(0f) { acc, ctx ->
                acc + calculateSingleContextScore(result, ctx, allElements)
            } / effectiveContexts.size.toFloat()

            result.copy(score = totalScore)
        }
    }

    private fun calculateSingleContextScore(
        result: PlaceResult,
        context: com.masterofpuppets.parkspotter.ui.search.SearchContext,
        allElements: List<OverpassElement>
    ): Float {
        var score = 50f // Base score

        val tags = result.tags
        val isParking = result.placeType == ApiPlaceType.PARKING.key
        val isStreet = result.placeType == ApiPlaceType.STREET.key

        when (context) {
            com.masterofpuppets.parkspotter.ui.search.SearchContext.RESIDENTIAL -> {
                if (isStreet) {
                    val hw = tags["highway"]?.lowercase() ?: ""
                    if (hw == "residential" || hw == "living_street") score += 30f
                    if (tags.keys.any { it.startsWith("parking:lane") || it.startsWith("parking:condition") }) score += 15f
                }
                if (isParking) {
                    val landuseNear = hasNearbyTag(result, allElements, 250) { _, t -> t["landuse"] == "residential" }
                    if (landuseNear) score += 25f
                }
            }

            com.masterofpuppets.parkspotter.ui.search.SearchContext.COMMERCIAL_WORK -> {
                if (isParking) {
                    val parkingType = tags["parking"]?.lowercase() ?: ""
                    if (parkingType in listOf("surface", "multi-storey", "underground")) score += 20f

                    val shopNear = hasNearbyTag(result, allElements, 300) { _, t ->
                        val shop = t["shop"]?.lowercase() ?: ""
                        shop == "supermarket" || shop == "mall" || t["landuse"] == "commercial"
                    }
                    if (shopNear) score += 30f

                    val industrialNear = hasNearbyTag(result, allElements, 300) { _, t ->
                        t["landuse"] == "industrial" || t["building"] == "industrial" || t["building"] == "warehouse"
                    }
                    if (industrialNear) score += 25f
                }
            }

            com.masterofpuppets.parkspotter.ui.search.SearchContext.SERVICES_TRANSPORT_HEALTH -> {
                val hw = tags["highway"]?.lowercase() ?: ""
                val amenity = tags["amenity"]?.lowercase() ?: ""
                val parkRide = tags["park_ride"]?.lowercase() ?: ""

                if (hw in listOf("services", "rest_area")) score += 40f
                if (parkRide == "yes" || tags["parking"] == "park_and_ride") score += 40f

                val hasFuel = amenity == "fuel" || tags.containsValue("fuel") || hasNearbyTag(result, allElements, 150) { _, t -> t["amenity"] == "fuel" }
                if (hasFuel) score += 20f

                val stationNear = hasNearbyTag(result, allElements, 300) { _, t ->
                    t["building"] == "train_station" || t["railway"] == "station" || t["amenity"] == "bus_station"
                }
                if (stationNear) score += 30f

                val hospitalNear = hasNearbyTag(result, allElements, 300) { _, t ->
                    t["amenity"] == "hospital"
                }
                if (hospitalNear) score += 30f
            }

            com.masterofpuppets.parkspotter.ui.search.SearchContext.NATURE_DEDICATED -> {
                val tourism = tags["tourism"]?.lowercase() ?: ""
                val amenity = tags["amenity"]?.lowercase() ?: ""

                if (tourism in listOf("camp_site", "caravan_site") || amenity == "motorhome_stopover") {
                    score += 50f
                }

                val natureNear = hasNearbyTag(result, allElements, 350) { _, t ->
                    val nat = t["natural"]?.lowercase() ?: ""
                    val tour = t["tourism"]?.lowercase() ?: ""
                    val leis = t["leisure"]?.lowercase() ?: ""
                    nat in listOf("beach", "cliff") || tour == "viewpoint" || leis in listOf("nature_reserve", "park") || t["water"] == "reservoir"
                }
                if (natureNear) score += 30f
            }
        }

        return score.coerceIn(0f, 100f)
    }

    private fun matchesSingleContext(
        result: PlaceResult,
        context: com.masterofpuppets.parkspotter.ui.search.SearchContext,
        allElements: List<OverpassElement>
    ): Boolean {
        val tags = result.tags
        val isParking = result.placeType == ApiPlaceType.PARKING.key
        val isStreet = result.placeType == ApiPlaceType.STREET.key

        return when (context) {
            com.masterofpuppets.parkspotter.ui.search.SearchContext.RESIDENTIAL -> {
                if (isStreet) {
                    val hw = tags["highway"]?.lowercase() ?: ""
                    hw == "residential" || hw == "living_street" || tags.keys.any { it.startsWith("parking:lane") || it.startsWith("parking:condition") }
                } else if (isParking) {
                    hasNearbyTag(result, allElements, 250) { _, t -> t["landuse"] == "residential" }
                } else false
            }

            com.masterofpuppets.parkspotter.ui.search.SearchContext.COMMERCIAL_WORK -> {
                if (isParking) {
                    val parkingType = tags["parking"]?.lowercase() ?: ""
                    val hasExplicitParking = parkingType in listOf("surface", "multi-storey", "underground")
                    val shopNear = hasNearbyTag(result, allElements, 300) { _, t ->
                        val shop = t["shop"]?.lowercase() ?: ""
                        shop == "supermarket" || shop == "mall" || t["landuse"] == "commercial"
                    }
                    val industrialNear = hasNearbyTag(result, allElements, 300) { _, t ->
                        t["landuse"] == "industrial" || t["building"] == "industrial" || t["building"] == "warehouse"
                    }
                    hasExplicitParking || shopNear || industrialNear
                } else false
            }

            com.masterofpuppets.parkspotter.ui.search.SearchContext.SERVICES_TRANSPORT_HEALTH -> {
                val hw = tags["highway"]?.lowercase() ?: ""
                val amenity = tags["amenity"]?.lowercase() ?: ""
                val parkRide = tags["park_ride"]?.lowercase() ?: ""

                if (hw in listOf("services", "rest_area") || parkRide == "yes" || tags["parking"] == "park_and_ride") return true
                if (amenity == "fuel" || tags.containsValue("fuel")) return true

                val fuelNear = hasNearbyTag(result, allElements, 150) { _, t -> t["amenity"] == "fuel" }
                val stationNear = hasNearbyTag(result, allElements, 300) { _, t ->
                    t["building"] == "train_station" || t["railway"] == "station" || t["amenity"] == "bus_station"
                }
                val hospitalNear = hasNearbyTag(result, allElements, 300) { _, t ->
                    t["amenity"] == "hospital"
                }
                fuelNear || stationNear || hospitalNear
            }

            com.masterofpuppets.parkspotter.ui.search.SearchContext.NATURE_DEDICATED -> {
                val tourism = tags["tourism"]?.lowercase() ?: ""
                val amenity = tags["amenity"]?.lowercase() ?: ""

                if (tourism in listOf("camp_site", "caravan_site") || amenity == "motorhome_stopover") return true

                hasNearbyTag(result, allElements, 350) { _, t ->
                    val nat = t["natural"]?.lowercase() ?: ""
                    val tour = t["tourism"]?.lowercase() ?: ""
                    val leis = t["leisure"]?.lowercase() ?: ""
                    nat in listOf("beach", "cliff") || tour == "viewpoint" || leis in listOf("nature_reserve", "park") || t["water"] == "reservoir"
                }
            }
        }
    }

    private fun hasNearbyTag(
        origin: PlaceResult,
        elements: List<OverpassElement>,
        maxDistanceMeters: Int,
        predicate: (OverpassElement, Map<String, String>) -> Boolean
    ): Boolean {
        return elements.any { el ->
            predicate(el, el.tags) &&
                    haversineDistanceMeters(origin.latitude, origin.longitude, el.latitude, el.longitude) <= maxDistanceMeters
        }
    }
}
