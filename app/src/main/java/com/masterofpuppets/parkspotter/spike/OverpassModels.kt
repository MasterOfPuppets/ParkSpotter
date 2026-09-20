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
    val geometry: List<OverpassCenter> = emptyList(),
    val members: List<OverpassMember> = emptyList(),
    val tags: Map<String, String> = emptyMap()
) {
    val latitude: Double get() = lat ?: center?.lat ?: 0.0
    val longitude: Double get() = lon ?: center?.lon ?: 0.0
    val displayName: String? get() = tags["name"] ?: tags["ref"]
    val placeType: ApiPlaceType get() = ApiPlaceType.from(tags)
}

fun String.toApiPlaceType(): ApiPlaceType = ApiPlaceType.entries.find { it.key == this } ?: ApiPlaceType.UNKNOWN

enum class ApiPlaceType(val key: String) {
    PARKING("parking"),
    STREET("street"),
    PARK("park"),
    CAMP_SITE("camp_site"),
    UNKNOWN("unknown");

    fun labelResId(): Int = when (this) {
        PARKING -> com.masterofpuppets.parkspotter.R.string.place_type_parking
        STREET -> com.masterofpuppets.parkspotter.R.string.place_type_street
        PARK -> com.masterofpuppets.parkspotter.R.string.place_type_park
        CAMP_SITE -> com.masterofpuppets.parkspotter.R.string.place_type_camp_site
        UNKNOWN -> com.masterofpuppets.parkspotter.R.string.place_type_unknown
    }

    companion object {
        private val STREET_HIGHWAY_TYPES = setOf(
            "primary", "secondary", "tertiary", "unclassified", "residential", "living_street", "rest_area"
        )

        fun from(tags: Map<String, String>): ApiPlaceType {
            if (tags.containsKey("junction")) {
                return UNKNOWN
            }

            return when {
                tags["amenity"] == "parking" || 
                (tags["highway"] == "service" && tags["service"] == "parking_aisle") -> PARKING
                tags["highway"] in STREET_HIGHWAY_TYPES -> STREET
                tags["leisure"] == "park" -> PARK
                tags["tourism"] == "camp_site" -> CAMP_SITE
                else -> UNKNOWN
            }
        }
    }
}

data class OverpassCenter(
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

data class OverpassMember(
    val type: String = "",
    val ref: Long = 0L,
    val role: String = "",
    val geometry: List<OverpassCenter> = emptyList(),
)
