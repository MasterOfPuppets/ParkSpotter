package com.masterofpuppets.parkspotter.spike

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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
                    .addHeader("User-Agent", "ParkSpotter/0.1 (Android)")
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

    private fun buildQuery(lat: Double, lon: Double, radius: Int): String = """
        [out:json][timeout:25];
        (
          node["amenity"="parking"](around:$radius,$lat,$lon);
          way["amenity"="parking"](around:$radius,$lat,$lon);
          way["highway"~"primary|secondary|tertiary|unclassified|residential|living_street|rest_area"](around:$radius,$lat,$lon);
          way["highway"="service"]["service"="parking_aisle"](around:$radius,$lat,$lon);
          node["leisure"="park"](around:$radius,$lat,$lon);
          way["leisure"="park"](around:$radius,$lat,$lon);
          node["tourism"="camp_site"](around:$radius,$lat,$lon);
          way["tourism"="camp_site"](around:$radius,$lat,$lon);
        );
        out center;
    """.trimIndent()
}
