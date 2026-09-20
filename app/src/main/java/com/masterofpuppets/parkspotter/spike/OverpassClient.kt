package com.masterofpuppets.parkspotter.spike

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.Locale

import okio.IOException

object OverpassClient {

    private const val TAG = "OverpassClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    suspend fun queryParkingAreas(lat: Double, lon: Double, radiusMeters: Int): Result<List<OverpassElement>> =
        withContext(Dispatchers.IO) {
            try {
                val query = buildQuery(lat, lon, radiusMeters)

                Log.d(TAG, "=== REQUEST ===")
                Log.d(TAG, "URL: $ENDPOINT")
                Log.d(TAG, "Query:\n$query")

                val body = FormBody.Builder()
                    .add("data", query)
                    .build()
                val request = Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("Accept", "*/*")
                    .addHeader("User-Agent", "ParkSpotter/0.2 (Android)")
                    .post(body)
                    .build()

                Log.d(TAG, "Headers sent: ${request.headers}")
                Log.d(TAG, "Content-Type: ${body.contentType()}")
                Log.d(TAG, "Body size: ${body.contentLength()} bytes")

                val response = client.newCall(request).execute()

                Log.d(TAG, "=== RESPONSE ===")
                Log.d(TAG, "HTTP code: ${response.code}")
                Log.d(TAG, "Response headers: ${response.headers}")

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "(empty)"
                    Log.e(TAG, "Error body: $errorBody")
                    // Throw an IOException with a structured message containing the code
                    throw IOException("HTTP_${response.code}")
                }

                val json = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                Log.d(TAG, "Response body (first 500 chars): ${json.take(500)}")
                
                // New logging for each item in the response with ALL tags
                val parsed = gson.fromJson(json, OverpassResponse::class.java)
                Log.d(TAG, "Parsed elements: ${parsed.elements.size}")
                parsed.elements.forEachIndexed { index, el ->
                    val name = el.tags["name"] ?: el.tags["ref"] ?: "Unnamed"
                    val tagsFormatted = el.tags.entries.joinToString(", ") { "${it.key}=${it.value}" }
                    Log.d(TAG, "ITEM[$index]: ID=${el.id} | TYPE=${el.type} | NAME=$name | LAT=${el.latitude} | LON=${el.longitude} | TAGS=[$tagsFormatted]")
                }

                Result.success(parsed.elements)
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.javaClass.simpleName} — ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun queryZoneClassificationData(
        lat: Double,
        lon: Double,
        radiusMeters: Int,
    ): Result<List<OverpassElement>> = withContext(Dispatchers.IO) {
        try {
            val formattedLat = String.format(Locale.US, "%.6f", lat)
            val formattedLon = String.format(Locale.US, "%.6f", lon)
            val query = """
                [out:json][timeout:45];
                (
                  way["landuse"~"residential|industrial|commercial"](around:$radiusMeters,$formattedLat,$formattedLon);
                  relation["landuse"~"residential|industrial|commercial"](around:$radiusMeters,$formattedLat,$formattedLon);
                )->.zones;
                (
                  way["building"~"detached|house|semidetached_house|apartments"](around:$radiusMeters,$formattedLat,$formattedLon);
                  way["highway"~"residential|living_street|tertiary|unclassified|service|motorway|trunk|primary"](around:$radiusMeters,$formattedLat,$formattedLon);
                  node["amenity"~"parking|nightclub|bar|pub"](around:$radiusMeters,$formattedLat,$formattedLon);
                  way["amenity"~"parking|nightclub|bar|pub"](around:$radiusMeters,$formattedLat,$formattedLon);
                  node["barrier"="gate"](around:$radiusMeters,$formattedLat,$formattedLon);
                  node["place"~"neighbourhood|suburb"](around:$radiusMeters,$formattedLat,$formattedLon);
                )->.features;
                .zones out center geom;
                .features out center;
            """.trimIndent()

            val body = FormBody.Builder().add("data", query).build()
            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "ParkSpotter/0.2 (Android)")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP_${response.code}")
                }

                val json = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response"))
                val elements = gson.fromJson(json, OverpassResponse::class.java).elements
                Result.success(elements)
            }
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
                [out:json][timeout:30];
                (
                  node["highway"~"services|rest_area"](around:$corridorMeters,$routeCoordinates);
                  way["highway"~"services|rest_area"](around:$corridorMeters,$routeCoordinates);
                  node["amenity"="fuel"]["opening_hours"="24/7"]["parking"="surface"](around:$corridorMeters,$routeCoordinates);
                  way["amenity"="fuel"]["opening_hours"="24/7"]["parking"="surface"](around:$corridorMeters,$routeCoordinates);
                );
                out center;
            """.trimIndent()
            val body = FormBody.Builder().add("data", query).build()
            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "ParkSpotter/0.2 (Android)")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP_${response.code}")
                }
                val json = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response"))
                val elements = gson.fromJson(json, OverpassResponse::class.java).elements
                Result.success(elements)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Route service alternatives request failed: ${error.message}", error)
            Result.failure(error)
        }
    }

    private fun buildQuery(lat: Double, lon: Double, radius: Int): String {
        val formattedLat = String.format(java.util.Locale.US, "%.6f", lat)
        val formattedLon = String.format(java.util.Locale.US, "%.6f", lon)

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
          node["leisure"="park"](around:$radius,$lat,$lon);
          way["leisure"="park"](around:$radius,$formattedLat,$formattedLon);
        );
        out center;
    """.trimIndent()
    }
}
