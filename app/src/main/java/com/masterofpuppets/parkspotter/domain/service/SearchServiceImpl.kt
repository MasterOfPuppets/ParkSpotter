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
    ): Result<SearchExecutionResult> {
        
        val result = OverpassClient.queryParkingAreas(lat, lon, radiusMeters)
        
        return result.map { elements ->
            // Enrich on fetch: evaluate and stamp contextMatches for ALL raw places
            val enrichedRawList = elements
                .filter { it.placeType != ApiPlaceType.UNKNOWN }
                .map { element ->
                    val basePlace = element.toPlaceResult(lat, lon)
                    val matches = evaluateContextMatches(basePlace, elements)
                    basePlace.copy(contextMatches = matches)
                }
            
            android.util.Log.d(DIAG_TAG, "=== NEW SEARCH EXECUTION ===")
            android.util.Log.d(DIAG_TAG, "Raw API Overpass Elements: ${elements.size} | Enriched PlaceResults: ${enrichedRawList.size}")

            val filtered = applyLocalFilterInternal(enrichedRawList, selectedTypes, sortMode, contexts)
            
            // If sort mode is BEST_ROUTE, calculate optimal OSRM Trip & Road Geometry
            var finalFiltered = filtered
            var roadGeometry: List<Pair<Double, Double>> = emptyList()

            if (sortMode == SearchSortMode.BEST_ROUTE && filtered.isNotEmpty()) {
                val spotsCoords = filtered.map { it.latitude to it.longitude }
                val osrmResult = com.masterofpuppets.parkspotter.domain.service.routing.OsrmRoutingClient
                    .computeOptimalTrip(lat, lon, spotsCoords)
                    .getOrNull()

                if (osrmResult != null && osrmResult.geometryCoordinates.isNotEmpty()) {
                    roadGeometry = osrmResult.geometryCoordinates
                    // If OSRM returned a reordered sequence of waypoints, sort the filtered list accordingly
                    if (osrmResult.orderedWaypoints.size == filtered.size) {
                        finalFiltered = osrmResult.orderedWaypoints.mapNotNull { idx -> filtered.getOrNull(idx) }
                    }
                }
            }

            SearchExecutionResult(
                rawResults = enrichedRawList,
                filteredResults = finalFiltered,
                rawElements = elements,
                routeGeometry = roadGeometry
            )
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
        return applyLocalFilterInternal(rawResults, selectedTypes, sortMode, contexts)
    }

    private fun applyLocalFilterInternal(
        rawList: List<PlaceResult>,
        selectedTypes: Set<ApiPlaceType>,
        sortMode: SearchSortMode,
        contexts: Set<com.masterofpuppets.parkspotter.ui.search.SearchContext>
    ): List<PlaceResult> {
        val selectedTypeKeys = selectedTypes.map { it.key }.toSet()
        val activeContextsStr = if (contexts.isEmpty() || contexts.size == com.masterofpuppets.parkspotter.ui.search.SearchContext.entries.size) "ALL" else contexts.joinToString(", ")

        android.util.Log.d(DIAG_TAG, "==========================================================================================")
        android.util.Log.d(DIAG_TAG, ">>> EXECUÇÃO DE FILTRO DE PESQUISA <<<")
        android.util.Log.d(DIAG_TAG, ">>> FILTRO APLICADO: Contextos = [$activeContextsStr] | Tipos Base = ${selectedTypes.map { it.key }} | Ordenação = $sortMode")
        android.util.Log.d(DIAG_TAG, "==========================================================================================")

        // 1. LISTA ORIGINAL COMPLETA (RAW LIST)
        android.util.Log.d(DIAG_TAG, "--- [1/3] LISTA ORIGINAL DA API (RAW LIST - Total: ${rawList.size}) ---")
        rawList.forEachIndexed { idx, item ->
            val tagsStr = item.tags.entries.joinToString(", ") { "${it.key}=${it.value}" }
            android.util.Log.d(DIAG_TAG, "  RAW ITEM #$idx: ID=${item.osmId} | Type=${item.placeType} | Name='${item.name ?: "Unnamed"}' | Contexts=${item.contextMatches} | Tags=[$tagsStr]")
        }

        val isAllContexts = contexts.isEmpty() || contexts.size == com.masterofpuppets.parkspotter.ui.search.SearchContext.entries.size

        val typedFiltered = rawList.filter { selectedTypeKeys.contains(it.placeType) }
        val excludedByType = rawList.filter { !selectedTypeKeys.contains(it.placeType) }

        val contextFiltered = if (isAllContexts) {
            typedFiltered
        } else {
            typedFiltered.filter { place ->
                contexts.any { ctx -> place.contextMatches.contains(ctx) }
            }
        }
        val excludedByContext = typedFiltered.filter { place ->
            !isAllContexts && contexts.none { ctx -> place.contextMatches.contains(ctx) }
        }

        val sanitized = applySanitization(contextFiltered)
        val sanitizedDropped = contextFiltered.filter { res -> sanitized.find { it.osmId == res.osmId }?.isSanitized == true }
        val finalSurvivorsList = sanitized.filter { !it.isSanitized }

        // 2. LISTA DOS FILTRADOS / ELIMINADOS
        val totalExcluded = excludedByType.size + excludedByContext.size + sanitizedDropped.size
        android.util.Log.d(DIAG_TAG, "--- [2/3] LISTA DOS FILTRADOS / ELIMINADOS (TOTAL ELIMINADOS: $totalExcluded) ---")
        if (excludedByType.isNotEmpty()) {
            excludedByType.forEach { place ->
                android.util.Log.d(DIAG_TAG, "  ELIMINADO [MOTIVO: TIPO BASE] -> ID=${place.osmId} | Type=${place.placeType} | Name='${place.name ?: "Unnamed"}'")
            }
        }
        if (excludedByContext.isNotEmpty()) {
            excludedByContext.forEach { place ->
                android.util.Log.d(DIAG_TAG, "  ELIMINADO [MOTIVO: CONTEXTO] -> ID=${place.osmId} | Type=${place.placeType} | Name='${place.name ?: "Unnamed"}' | PlaceContexts=${place.contextMatches}")
            }
        }
        if (sanitizedDropped.isNotEmpty()) {
            sanitizedDropped.forEach { place ->
                val reason = sanitized.find { it.osmId == place.osmId }?.sanitizationReason ?: "Sanitized"
                android.util.Log.d(DIAG_TAG, "  ELIMINADO [MOTIVO: SANITIZAÇÃO] -> ID=${place.osmId} | Name='${place.name ?: "Unnamed"}' | Razão: $reason")
            }
        }
        if (totalExcluded == 0) {
            android.util.Log.d(DIAG_TAG, "  (Nenhum item foi eliminado nesta filtragem)")
        }

        val finalSortedSurvivors = when (sortMode) {
            SearchSortMode.BEST_ROUTE -> sortResultsByBestRoute(finalSurvivorsList)
            SearchSortMode.DISTANCE -> finalSurvivorsList.sortedBy { it.distanceMeters }
            SearchSortMode.SCORE -> finalSurvivorsList.sortedByDescending { it.score ?: 0f }
        }

        // 3. LISTA DOS SOBREVIVENTES
        android.util.Log.d(DIAG_TAG, "--- [3/3] LISTA DOS SOBREVIVENTES / MANTIDOS (TOTAL SOBREVIVENTES: ${finalSortedSurvivors.size}) ---")
        finalSortedSurvivors.forEachIndexed { i, surv ->
            val flagsDetail = "FLAGS[RES:${surv.contextMatches.contains(com.masterofpuppets.parkspotter.ui.search.SearchContext.RESIDENTIAL)}, COMM:${surv.contextMatches.contains(com.masterofpuppets.parkspotter.ui.search.SearchContext.COMMERCIAL_INDUSTRIAL_SERVICES)}, NAT:${surv.contextMatches.contains(com.masterofpuppets.parkspotter.ui.search.SearchContext.NATURE_DEDICATED)}, OTHER:${surv.contextMatches.contains(com.masterofpuppets.parkspotter.ui.search.SearchContext.OTHER)}]"
            android.util.Log.d(DIAG_TAG, "  SOBREVIVENTE #$i: ID=${surv.osmId} | Name='${surv.name ?: "Unnamed"}' | Type=${surv.placeType} | Dist=${surv.distanceMeters}m | $flagsDetail | ContextsSet=${surv.contextMatches}")
        }

        android.util.Log.d(DIAG_TAG, "=== RESUMO DA FILTRAGEM -> Total Original: ${rawList.size} | Eliminados por Tipo: ${excludedByType.size} | Eliminados por Contexto: ${excludedByContext.size} | Eliminados por Sanitização: ${sanitizedDropped.size} | SOBREVIVENTES FINAIS: ${finalSortedSurvivors.size} ===")
        android.util.Log.d(DIAG_TAG, "==========================================================================================")

        return finalSortedSurvivors
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

        // Rule 0: Sanitize private / restricted access areas (condominiums, gates, private property)
        workingList = sanitizeRestrictedAccess(workingList)

        // Rule 1: Exact coordinates sanitization
        workingList = sanitizeExactDuplicates(workingList)

        // Rule 2: Suppress access aisles near parking amenities (Situation 2 & 3)
        workingList = suppressAccessAislesByProximity(workingList)

        // Rule 3: Deduplicate redundant aisles (Situation 1)
        workingList = deduplicateRedundantAisles(workingList)

        // Rule 4: Sanitize streets by isolation and survival (with name inheritance)
        workingList = sanitizeStreetsByIsolationAndSurvival(workingList)

        // Rule 5: Suppress street or parking by proximity (amenity=parking always survives over street)
        workingList = suppressStreetOrParkingByProximity(workingList)

        return workingList
    }

    private fun sanitizeRestrictedAccess(results: List<PlaceResult>): List<PlaceResult> {
        // TODO: Future setting: Make private access exclusion configurable via UserPreferences (e.g. "Include private access")
        val restrictedAccessValues = setOf("private", "no", "destination", "customers", "permissive")

        return results.map { current ->
            if (current.isSanitized) return@map current

            val access = current.tags["access"]?.lowercase() ?: ""
            if (access in restrictedAccessValues) {
                current.copy(isSanitized = true, sanitizationReason = "Private or restricted access ($access)")
            } else {
                current
            }
        }
    }

    private fun suppressStreetOrParkingByProximity(results: List<PlaceResult>): List<PlaceResult> {
        val streetList = results.filter { it.placeType == ApiPlaceType.STREET.key && !it.isSanitized }
        val parkingList = results.filter { it.placeType == ApiPlaceType.PARKING.key && !it.isSanitized }

        val toSanitizeIds = mutableSetOf<Long>()

        for (street in streetList) {
            for (parking in parkingList) {
                val dist = haversineDistanceMeters(street.latitude, street.longitude, parking.latitude, parking.longitude)
                if (dist <= 35) {
                    val isDedicatedParkingAmenity = parking.tags["amenity"] == "parking"
                    if (isDedicatedParkingAmenity) {
                        // amenity=parking ALWAYS survives over transit street segment
                        toSanitizeIds.add(street.osmId)
                    } else if (street.tags.size >= parking.tags.size) {
                        toSanitizeIds.add(parking.osmId)
                    } else {
                        toSanitizeIds.add(street.osmId)
                    }
                }
            }
        }

        return results.map { current ->
            if (toSanitizeIds.contains(current.osmId) && !current.isSanitized) {
                current.copy(isSanitized = true, sanitizationReason = "Suppressed by nearby richer street/parking amenity within 35m")
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
        val inheritedNames = mutableMapOf<Long, String>() // Map target osmId -> inherited street name

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
                            // If the eliminated pin has a valid name and the winning richest pin is unnamed, inherit the name
                            val richestName = richestPin.name
                            val isRichestUnnamed = richestName.isNullOrBlank() || richestName.equals("unnamed", ignoreCase = true)
                            val pinName = pin.name
                            val isPinNamed = !pinName.isNullOrBlank() && !pinName.equals("unnamed", ignoreCase = true)

                            if (isRichestUnnamed && isPinNamed && pinName != null) {
                                inheritedNames[richestPin.osmId] = pinName
                            }
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
            } else if (inheritedNames.containsKey(res.osmId)) {
                val newName = inheritedNames[res.osmId]
                res.copy(name = newName)
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

    private fun evaluateContextMatches(
        result: PlaceResult,
        allElements: List<OverpassElement>
    ): Set<com.masterofpuppets.parkspotter.ui.search.SearchContext> {
        val matches = mutableSetOf<com.masterofpuppets.parkspotter.ui.search.SearchContext>()
        val tags = result.tags

        // --- PASS 1: Macro Territorial Classification by `landuse` ---
        val landuse = tags["landuse"]?.lowercase() ?: ""
        when {
            landuse == "residential" -> {
                matches.add(com.masterofpuppets.parkspotter.ui.search.SearchContext.RESIDENTIAL)
            }
            landuse in listOf("commercial", "industrial", "retail") -> {
                matches.add(com.masterofpuppets.parkspotter.ui.search.SearchContext.COMMERCIAL_INDUSTRIAL_SERVICES)
            }
            landuse in listOf("farmland", "farmyard", "forest", "meadow", "grass", "orchard", "vineyard", "plant_nursery") -> {
                matches.add(com.masterofpuppets.parkspotter.ui.search.SearchContext.NATURE_DEDICATED)
            }
        }

        // --- PASS 2: Micro Functional Reclassification by Explicit `tags` ---
        val highway = tags["highway"]?.lowercase() ?: ""
        val amenity = tags["amenity"]?.lowercase() ?: ""
        val tourism = tags["tourism"]?.lowercase() ?: ""
        val shop = tags["shop"]?.lowercase() ?: ""
        val building = tags["building"]?.lowercase() ?: ""
        val railway = tags["railway"]?.lowercase() ?: ""
        val parkingType = tags["parking"]?.lowercase() ?: ""

        // 1. Explicit Residential Streets & Lanes
        if (highway in listOf("residential", "living_street") ||
            tags.keys.any { it.startsWith("parking:lane") || it.startsWith("parking:condition") }
        ) {
            matches.add(com.masterofpuppets.parkspotter.ui.search.SearchContext.RESIDENTIAL)
        }

        // 2. Explicit Dedicated Sites (Camping, Caravans, ASAs)
        if (tourism in listOf("camp_site", "caravan_site") || amenity == "motorhome_stopover") {
            matches.add(com.masterofpuppets.parkspotter.ui.search.SearchContext.NATURE_DEDICATED)
        }

        // 3. Explicit Commercial, Industrial & Service Outlets/Hubs
        if (shop in listOf("supermarket", "mall") ||
            amenity in listOf("hospital", "stadium", "fuel", "bus_station") ||
            building in listOf("hospital", "stadium", "industrial", "warehouse") ||
            railway == "station" ||
            highway in listOf("services", "rest_area") ||
            parkingType in listOf("multi-storey", "underground", "park_and_ride") ||
            tags["park_ride"] == "yes"
        ) {
            matches.add(com.masterofpuppets.parkspotter.ui.search.SearchContext.COMMERCIAL_INDUSTRIAL_SERVICES)
        }

        // --- PASS 3: Fallback Spatial Resolution for "OTHER" Items ---
        if (matches.isEmpty() || matches == setOf(com.masterofpuppets.parkspotter.ui.search.SearchContext.OTHER)) {
            val resolvedSpatialContexts = resolveSpatialContextForOther(result, allElements)
            if (resolvedSpatialContexts.isNotEmpty()) {
                matches.addAll(resolvedSpatialContexts)
                matches.remove(com.masterofpuppets.parkspotter.ui.search.SearchContext.OTHER)
            } else {
                matches.add(com.masterofpuppets.parkspotter.ui.search.SearchContext.OTHER)
            }
        } else {
            // Remove OTHER if specific contexts were matched in Passes 1 or 2
            matches.remove(com.masterofpuppets.parkspotter.ui.search.SearchContext.OTHER)
        }

        return matches
    }

    private fun resolveSpatialContextForOther(
        result: PlaceResult,
        allElements: List<OverpassElement>
    ): Set<com.masterofpuppets.parkspotter.ui.search.SearchContext> {
        val resolved = mutableSetOf<com.masterofpuppets.parkspotter.ui.search.SearchContext>()

        // Diagnostic log: Find all landuse elements in the area to inspect what Overpass returned
        val areaLanduses = allElements.filter { it.tags.containsKey("landuse") }
        android.util.Log.d(DIAG_TAG, "SPATIAL RESOLVE for OTHER -> Item ID=${result.osmId} Name='${result.name}' | Total Landuse Elements in Area: ${areaLanduses.size}")
        areaLanduses.forEach { lu ->
            val dist = haversineDistanceMeters(result.latitude, result.longitude, lu.latitude, lu.longitude)
            android.util.Log.d(DIAG_TAG, "   FOUND LANDUSE: Type=${lu.type} ID=${lu.id} Value=${lu.tags["landuse"]} | Distance=${dist}m")
        }

        // 1. Check if this unassigned street/parking is contained within/near a `landuse=residential` area or nearby residential streets
        val isNearResidentialLanduse = hasNearbyTag(result, allElements, maxDistanceMeters = 350) { _, tags ->
            tags["landuse"]?.lowercase() == "residential" || tags["highway"]?.lowercase() == "residential"
        }
        if (isNearResidentialLanduse) {
            resolved.add(com.masterofpuppets.parkspotter.ui.search.SearchContext.RESIDENTIAL)
        }

        // 2. Check if this unassigned street/parking is near commercial/retail/industrial landuse or shops/amenities
        val isNearCommercialLanduseOrHub = hasNearbyTag(result, allElements, maxDistanceMeters = 300) { _, tags ->
            val landuse = tags["landuse"]?.lowercase() ?: ""
            val shop = tags["shop"]?.lowercase() ?: ""
            val amenity = tags["amenity"]?.lowercase() ?: ""
            val building = tags["building"]?.lowercase() ?: ""

            landuse in listOf("commercial", "industrial", "retail") ||
                    shop in listOf("supermarket", "mall") ||
                    amenity in listOf("hospital", "stadium", "fuel", "bus_station") ||
                    building in listOf("hospital", "stadium", "industrial", "warehouse") ||
                    tags["railway"] == "station"
        }
        if (isNearCommercialLanduseOrHub) {
            resolved.add(com.masterofpuppets.parkspotter.ui.search.SearchContext.COMMERCIAL_INDUSTRIAL_SERVICES)
        }

        // 3. Check if near rural/nature landuse outside urban tissue
        if (resolved.isEmpty()) {
            val isNearNatureLanduse = hasNearbyTag(result, allElements, maxDistanceMeters = 350) { _, tags ->
                val landuse = tags["landuse"]?.lowercase() ?: ""
                landuse in listOf("farmland", "farmyard", "forest", "meadow", "grass", "orchard", "vineyard", "plant_nursery")
            }
            if (isNearNatureLanduse) {
                resolved.add(com.masterofpuppets.parkspotter.ui.search.SearchContext.NATURE_DEDICATED)
            }
        }

        return resolved
    }

    private fun sortResultsByBestRoute(list: List<PlaceResult>): List<PlaceResult> {
        if (list.size <= 1) return list

        val unvisited = list.toMutableList()
        val orderedRoute = mutableListOf<PlaceResult>()

        // 1. First spot is the closest to the origin (minimal distanceMeters)
        val firstSpot = unvisited.minByOrNull { it.distanceMeters } ?: unvisited.removeAt(0)
        unvisited.remove(firstSpot)
        orderedRoute.add(firstSpot)

        var currentSpot = firstSpot

        // 2. Chain subsequent spots by nearest-neighbor distance to build a continuous logical route
        while (unvisited.isNotEmpty()) {
            val nextNearest = unvisited.minByOrNull { spot ->
                haversineDistanceMeters(currentSpot.latitude, currentSpot.longitude, spot.latitude, spot.longitude)
            } ?: unvisited.removeAt(0)

            unvisited.remove(nextNearest)
            orderedRoute.add(nextNearest)
            currentSpot = nextNearest
        }

        return orderedRoute
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
