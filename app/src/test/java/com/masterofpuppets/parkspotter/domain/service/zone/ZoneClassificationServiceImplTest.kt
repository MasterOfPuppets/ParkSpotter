package com.masterofpuppets.parkspotter.domain.service.zone

import com.masterofpuppets.parkspotter.domain.model.ZoneCategory
import com.masterofpuppets.parkspotter.spike.OverpassCenter
import com.masterofpuppets.parkspotter.spike.OverpassElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneClassificationServiceImplTest {

    private val service = ZoneClassificationServiceImpl()

    @Test
    fun residentialPolygonIsClassifiedFromContainedFeatures() {
        val result = service.classifyElements(residentialFixture())

        assertEquals(1, result.recommendations.size)
        val zone = result.recommendations.single()
        assertEquals(ZoneCategory.RESIDENTIAL_LOW_DENSITY, zone.category)
        assertTrue(zone.boundary.isNotEmpty())
        assertTrue(zone.score >= 60)
        assertEquals(38.701, zone.targetCoordinate.latitude, 0.000001)
    }

    @Test
    fun privatePolygonIsExcluded() {
        val elements = residentialFixture().map { element ->
            if (element.id == 1L) {
                element.copy(tags = element.tags + ("access" to "private"))
            } else {
                element
            }
        }

        val result = service.classifyElements(elements)

        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun residentialWeightsChangeRankingScore() {
        val defaultScore = service.classifyElements(residentialFixture())
            .recommendations.single().score
        val tunedConfig = ZoneClassificationConfig(
            residentialWeights = ResidentialZoneWeights(
                baseScore = 10.0,
                houseDominance = 5.0,
                residentialRoads = 2.0,
                publicRoadAccess = 2.0,
                parking = 0.0,
                apartmentDensityPenalty = 20.0,
                nightlifePenalty = 18.0,
                majorRoadPenalty = 10.0,
            ),
        )

        val tunedScore = service.classifyElements(residentialFixture(), tunedConfig)
            .recommendations.single().score

        assertTrue(tunedScore < defaultScore)
    }

    @Test
    fun routeAlternativesUseFinalThirtyPercentOfTravelDistance() {
        val route = listOf(
            0.0 to 0.0,
            0.0 to 0.01,
            0.0 to 0.09,
            0.0 to 0.10,
        )

        val finalRoute = service.finalRouteFraction(route, 0.30)

        assertEquals(3, finalRoute.size)
        assertEquals(0.07, finalRoute.first().second, 0.000001)
        assertEquals(0.10, finalRoute.last().second, 0.000001)
    }

    @Test
    fun serviceAreasAreNotMixedWithDestinationZones() {
        val elements = residentialFixture() + OverpassElement(
            type = "way",
            id = 99L,
            center = OverpassCenter(38.700, -9.301),
            tags = mapOf(
                "highway" to "services",
                "name" to "Test Service Area",
            ),
        )

        val result = service.classifyElements(elements)

        assertTrue(result.recommendations.none { it.category == ZoneCategory.SERVICE_24_7 })
    }

    private fun residentialFixture(): List<OverpassElement> = listOf(
        OverpassElement(
            type = "way",
            id = 1L,
            center = OverpassCenter(38.700, -9.300),
            geometry = square(),
            tags = mapOf(
                "landuse" to "residential",
                "name" to "Test Neighbourhood",
            ),
        ),
        OverpassElement(
            type = "way",
            id = 2L,
            center = OverpassCenter(38.701, -9.300),
            tags = mapOf(
                "highway" to "residential",
                "name" to "Test Street",
            ),
        ),
        building(3L, 38.6995, -9.3005, "house"),
        building(4L, 38.6997, -9.3003, "detached"),
        building(5L, 38.7003, -9.2997, "house"),
        building(6L, 38.7005, -9.2995, "semidetached_house"),
    )

    private fun building(id: Long, lat: Double, lon: Double, type: String) =
        OverpassElement(
            type = "way",
            id = id,
            center = OverpassCenter(lat, lon),
            tags = mapOf("building" to type),
        )

    private fun square() = listOf(
        OverpassCenter(38.698, -9.302),
        OverpassCenter(38.702, -9.302),
        OverpassCenter(38.702, -9.298),
        OverpassCenter(38.698, -9.298),
        OverpassCenter(38.698, -9.302),
    )
}
