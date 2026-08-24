package com.masterofpuppets.parkspotter.domain.service.routing

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

data class OsrmTripResult(
    val orderedWaypoints: List<Int>,
    val geometryCoordinates: List<Pair<Double, Double>> // List of (Lat, Lon)
)

object OsrmRoutingClient {

    private const val TAG = "OsrmRoutingClient"
    private const val BASE_URL = "https://router.project-osrm.org/trip/v1/driving"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Calcula o trajeto ótimo via OSRM Trip API começando no ponto de origem.
     * Retorna a ordem dos índices e a geometria real das curvas das estradas.
     */
    suspend fun computeOptimalTrip(
        originLat: Double,
        originLon: Double,
        spots: List<Pair<Double, Double>>
    ): Result<OsrmTripResult> = withContext(Dispatchers.IO) {
        try {
            if (spots.isEmpty()) {
                return@withContext Result.success(OsrmTripResult(emptyList(), emptyList()))
            }

            // Coordinate format: lon,lat;lon,lat;...
            val coordBuilder = StringBuilder()
            coordBuilder.append(String.format(Locale.US, "%.6f,%.6f", originLon, originLat))

            spots.forEach { spot ->
                coordBuilder.append(";")
                coordBuilder.append(String.format(Locale.US, "%.6f,%.6f", spot.second, spot.first))
            }

            val url = "$BASE_URL/$coordBuilder?source=first&roundtrip=false&geometries=geojson&overview=full"
            Log.d(TAG, "Requesting OSRM Trip: $url")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "ParkSpotter/0.2 (Android)")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = "OSM Trip HTTP error: ${response.code}"
                Log.w(TAG, errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = response.body?.string() ?: return@withContext Result.failure(Exception("Empty OSRM response"))
            val parsed = gson.fromJson(json, OsrmTripResponse::class.java)

            if (parsed.code != "Ok" || parsed.trips.isEmpty()) {
                Log.w(TAG, "OSRM Trip non-OK code: ${parsed.code}")
                return@withContext Result.failure(Exception("OSRM routing returned code: ${parsed.code}"))
            }

            // Extract geometry: GeoJSON coordinates are [lon, lat] -> convert to Pair(lat, lon)
            val geometryList = parsed.trips.firstOrNull()?.geometry?.coordinates?.map { coord ->
                Pair(coord[1], coord[0]) // lat, lon
            } ?: emptyList()

            // Extract ordered waypoint indices (skipping origin at index 0, subtract 1 for spots list)
            val orderedWaypoints = parsed.waypoints
                .sortedBy { it.waypointIndex }
                .map { it.originalIndex }
                .filter { it > 0 }
                .map { it - 1 }

            Log.d(TAG, "OSRM Trip computed successfully: ${geometryList.size} geometry points, ${orderedWaypoints.size} waypoints")
            Result.success(OsrmTripResult(orderedWaypoints, geometryList))
        } catch (e: Exception) {
            Log.w(TAG, "Exception during OSRM trip computation: ${e.message}", e)
            Result.failure(e)
        }
    }

    private data class OsrmTripResponse(
        val code: String = "",
        val trips: List<OsrmTrip> = emptyList(),
        val waypoints: List<OsrmWaypoint> = emptyList()
    )

    private data class OsrmTrip(
        val geometry: OsrmGeometry? = null
    )

    private data class OsrmGeometry(
        val coordinates: List<List<Double>> = emptyList() // [ [lon, lat], [lon, lat], ... ]
    )

    private data class OsrmWaypoint(
        @SerializedName("waypoint_index")
        val waypointIndex: Int = 0,
        @SerializedName("trips_index")
        val tripsIndex: Int = 0,
        val location: List<Double> = emptyList(),
        val name: String = "",
        @SerializedName("distance")
        val distance: Double = 0.0,
        @SerializedName("hint")
        val hint: String = ""
    ) {
        // In OSRM, waypointIndex is the position in the trip, while the order in response array represents original input index
        val originalIndex: Int get() = waypointIndex
    }
}
