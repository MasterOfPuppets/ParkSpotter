package com.masterofpuppets.parkspotter.domain.model

import java.util.UUID

data class PlaceResult(
    val osmType: String,
    val osmId: Long,
    val name: String?,
    val latitude: Double,
    val longitude: Double,
    val placeType: String,
    val tags: Map<String, String>,
    val distanceMeters: Int,
    val score: Float? = null
)

data class SearchSession(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val originLat: Double,
    val originLon: Double,
    val radiusMeters: Int,
    val vehicleProfileId: String? = null,
    val status: SearchSessionStatus = SearchSessionStatus.ACTIVE,
    val resultIds: List<Pair<String, Long>> = emptyList()
)

enum class SearchSessionStatus {
    ACTIVE, CLOSED
}

data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val tier: UserTier = UserTier.FREE,
    val createdAt: Long = System.currentTimeMillis(),
    val vehicleProfiles: List<VehicleProfile> = emptyList(),
    val activeVehicleProfileId: String? = null
)

enum class UserTier {
    FREE, PAID, PREMIUM
}

data class VehicleProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: VehicleCategory,
    val lengthMeters: Float? = null,
    val heightMeters: Float? = null,
    val discretionLevel: DiscretionLevel
)

enum class VehicleCategory {
    CAR, VAN, MOTORHOME, MOTORCYCLE
}

enum class DiscretionLevel {
    LOW, MEDIUM, HIGH
}

data class UserPreferences(
    val defaultRadiusMeters: Int = 2000,
    val legalNoticeLastShownAt: Long = 0L,
    val resultsPageSize: Int = 10,
    val warnIfResultsAbove: Int = 150,
)

data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val osmType: String,
    val osmId: Long,
    val actionType: HistoryActionType,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String? = null
)

enum class HistoryActionType {
    VIEWED, NAVIGATED, VISIT_CONFIRMED, OVERNIGHT_CONFIRMED
}
