package com.masterofpuppets.parkspotter.spike

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.cos

object OverpassClient {

    private const val TAG = "OverpassClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )

    private fun executeOverpassQuery(query: String): String {
        var lastException: Exception? = null
        for (endpoint in ENDPOINTS) {
            try {
                Log.d(TAG, "Executing query on Overpass endpoint: $endpoint")
                val body = FormBody.Builder()
                    .add("data", query)
                    .build()
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Accept", "application/json")
                    .addHeader("User-Agent", "ParkSpotter/0.2 (Android)")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    if (!json.isNullOrBlank()) {
                        return json
                    }
                } else {
                    Log.w(TAG, "Endpoint $endpoint returned HTTP ${response.code}")
                    lastException = IOException("HTTP_${response.code}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Endpoint $endpoint failed with ${e.message}, trying next mirror...")
                lastException = e
            }
        }
        throw lastException ?: IOException("All Overpass endpoints failed")
    }

    suspend fun queryParkingAreas(lat: Double, lon: Double, radiusMeters: Int): Result<List<OverpassElement>> =
        withContext(Dispatchers.IO) {
            try {
                val query = buildQuery(lat, lon, radiusMeters)
                val json = executeOverpassQuery(query)

                val parsed = gson.fromJson(json, OverpassResponse::class.java)
                Log.d(TAG, "queryParkingAreas: Parsed ${parsed.elements.size} elements")
                Result.success(parsed.elements)
            } catch (e: Exception) {
                Log.e(TAG, "queryParkingAreas Exception: ${e.javaClass.simpleName} — ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun queryZoneClassificationData(
        lat: Double,
        lon: Double,
        radiusMeters: Int,
    ): Result<List<OverpassElement>> = withContext(Dispatchers.IO) {
        try {
            val latDelta = (radiusMeters.toDouble() / 111320.0)
            val lonDelta = (radiusMeters.toDouble() / (111320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.001)))
            val south = String.format(Locale.US, "%.6f", lat - latDelta)
            val north = String.format(Locale.US, "%.6f", lat + latDelta)
            val west = String.format(Locale.US, "%.6f", lon - lonDelta)
            val east = String.format(Locale.US, "%.6f", lon + lonDelta)

            val query = """
                [out:json][timeout:25][bbox:$south,$west,$north,$east];
                (
                  way["landuse"~"^(residential|industrial|commercial)$"];
                  relation["landuse"~"^(residential|industrial|commercial)$"];
                )->.zones;
                (
                  way["building"~"^(apartments|house|detached|semidetached_house)$"];
                  way["highway"~"^(residential|living_street|service|motorway|trunk|primary|tertiary|unclassified)$"];
                  node["amenity"~"^(parking|nightclub|bar|pub)$"];
                  way["amenity"="parking"];
                  node["barrier"="gate"];
                  node["place"~"^(neighbourhood|suburb)$"];
                )->.features;
                .zones out center geom qt;
                .features out tags center qt;
            """.trimIndent()

            val json = executeOverpassQuery(query)
            val elements = gson.fromJson(json, OverpassResponse::class.java).elements
            Result.success(elements)
        } catch (error: Exception) {
            Log.e(TAG, "Zone classification request failed: ${error.message}", error)
            Result.failure(error)
        }
    }

    suspend fun queryRouteServiceAlternatives(
        routePoints: List<Pair<Double, Double>>,
        corridorMeters: Int,
    ): Result<List<OverpassElement>> = withContext(Dispatchers.IO) {
        require(routePoints.size >= 2)
        try {
            val routeCoordinates = routePoints.joinToString(",") { (lat, lon) ->
                String.format(Locale.US, "%.6f,%.6f", lat, lon)
            }
            val query = """
                [out:json][timeout:25];
                (
                  node["highway"~"services|rest_area"](around:$corridorMeters,$routeCoordinates);
                  way["highway"~"services|rest_area"](around:$corridorMeters,$routeCoordinates);
                  node["amenity"="fuel"]["opening_hours"="24/7"]["parking"="surface"](around:$corridorMeters,$routeCoordinates);
                  way["amenity"="fuel"]["opening_hours"="24/7"]["parking"="surface"](around:$corridorMeters,$routeCoordinates);
                );
                out center;
            """.trimIndent()

            val json = executeOverpassQuery(query)
            val elements = gson.fromJson(json, OverpassResponse::class.java).elements
            Result.success(elements)
        } catch (error: Exception) {
            Log.e(TAG, "Route service alternatives request failed: ${error.message}", error)
            Result.failure(error)
        }
    }

    private fun buildQuery(lat: Double, lon: Double, radius: Int): String {
        val formattedLat = String.format(Locale.US, "%.6f", lat)
        val formattedLon = String.format(Locale.US, "%.6f", lon)

        return """
        [out:json][timeout:25];
        (
          node["amenity"="parking"](around:$radius,$formattedLat,$formattedLon);
          way["amenity"="parking"](around:$radius,$formattedLat,$formattedLon);
          way["highway"~"primary|secondary|tertiary|unclassified|residential|living_street|rest_area|services"](around:$radius,$formattedLat,$formattedLon);
          way["highway"="service"]["service"="parking_aisle"](around:$radius,$formattedLat,$formattedLon);
          node["landuse"~"residential|commercial|industrial|retail|farmland|farmyard|forest|meadow|grass|orchard|vineyard"](around:$radius,$formattedLat,$formattedLon);
          way["landuse"~"residential|commercial|industrial|retail|farmland|farmyard|forest|meadow|grass|orchard|vineyard"](around:$radius,$formattedLat,$formattedLon);
          relation["landuse"~"residential|commercial|industrial|retail|farmland|farmyard|forest|meadow|grass|orchard|vineyard"](around:$radius,$formattedLat,$formattedLon);
          node["amenity"~"fuel|hospital|bus_station|motorhome_stopover|stadium"](around:$radius,$formattedLat,$formattedLon);
          way["amenity"~"fuel|hospital|bus_station|motorhome_stopover|stadium"](around:$radius,$formattedLat,$formattedLon);
          node["shop"~"supermarket|mall"](around:$radius,$formattedLat,$formattedLon);
          way["shop"~"supermarket|mall"](around:$radius,$formattedLat,$formattedLon);
          node["railway"="station"](around:$radius,$formattedLat,$formattedLon);
          way["railway"="station"](around:$radius,$formattedLat,$formattedLon);
          node["natural"~"beach|cliff"](around:$radius,$formattedLat,$formattedLon);
          way["natural"~"beach|cliff"](around:$radius,$formattedLat,$formattedLon);
          node["tourism"~"camp_site|caravan_site|viewpoint"](around:$radius,$formattedLat,$formattedLon);
          way["tourism"~"camp_site|caravan_site|viewpoint"](around:$radius,$formattedLat,$formattedLon);
          node["leisure"="park"](around:$radius,$formattedLat,$formattedLon);
          way["leisure"="park"](around:$radius,$formattedLat,$formattedLon);
        );
        out center;
    """.trimIndent()
    }
}
