package com.masterofpuppets.parkspotter.spike

data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList()
)

data class OverpassElement(
    val type: String = "",
    val id: Long = 0L,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String> = emptyMap()
) {
    val latitude: Double get() = lat ?: center?.lat ?: 0.0
    val longitude: Double get() = lon ?: center?.lon ?: 0.0
    val displayName: String? get() = tags["name"] ?: tags["ref"]
    val placeType: ApiPlaceType get() = ApiPlaceType.from(tags)
}

enum class ApiPlaceType(val key: String) {
    PARKING("parking"),
    LIVING_STREET("living_street"),
    RESIDENTIAL_STREET("residential_street"),
    PARKING_AISLE("parking_aisle"),
    PARK("park"),
    CAMP_SITE("camp_site"),
    UNKNOWN("unknown");

    companion object {
        fun from(tags: Map<String, String>): ApiPlaceType = when {
            tags["amenity"] == "parking" -> PARKING
            tags["highway"] == "living_street" -> LIVING_STREET
            tags["highway"] == "residential" -> RESIDENTIAL_STREET
            tags["highway"] == "service" && tags["service"] == "parking_aisle" -> PARKING_AISLE
            tags["leisure"] == "park" -> PARK
            tags["tourism"] == "camp_site" -> CAMP_SITE
            else -> UNKNOWN
        }
    }
}

data class OverpassCenter(
    val lat: Double = 0.0,
    val lon: Double = 0.0
)
