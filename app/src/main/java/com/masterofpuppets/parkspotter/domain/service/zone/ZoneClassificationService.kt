package com.masterofpuppets.parkspotter.domain.service.zone

import com.masterofpuppets.parkspotter.domain.model.ZoneSearchResult

interface ZoneClassificationService {
    suspend fun classifyZones(
        latitude: Double,
        longitude: Double,
        originLatitude: Double? = null,
        originLongitude: Double? = null,
        config: ZoneClassificationConfig = ZoneClassificationConfig(),
    ): Result<ZoneSearchResult>
}
