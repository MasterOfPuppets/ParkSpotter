package com.masterofpuppets.parkspotter.ui.search

import android.Manifest
import android.content.Context
import android.util.Log
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import android.graphics.drawable.LayerDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.domain.model.PlaceResult
import com.masterofpuppets.parkspotter.spike.ApiPlaceType
import com.masterofpuppets.parkspotter.spike.OverpassClient
import com.masterofpuppets.parkspotter.spike.OverpassElement
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    settings: SearchUiSettings,
    currentSession: SearchSessionState?,
    isConfigExpanded: Boolean,
    onConfigExpandedChanged: (Boolean) -> Unit,
    pageIndex: Int,
    onPageIndexChanged: (Int) -> Unit,
    onResultClick: (PlaceResult) -> Unit,
    onSessionChanged: (SearchSessionState?) -> Unit,
    onOpenMap: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val normalizedSettings = settings.normalized()
    val minRadius = normalizedSettings.minRadiusMeters
    val maxRadius = normalizedSettings.maxRadiusMeters
    var state by remember { mutableStateOf<SearchUiState>(SearchUiState.Idle) }
    var originMode by remember { mutableStateOf(SearchOriginMode.CURRENT_LOCATION) }
    var manualLat by remember { mutableStateOf("") }
    var manualLon by remember { mutableStateOf("") }
    var radiusMeters by remember(minRadius, maxRadius) {
        mutableStateOf(300.coerceIn(minRadius, maxRadius))
    }
    var selectedContext by remember { mutableStateOf(SearchContext.URBAN) }
    var sortMode by remember { mutableStateOf(SearchSortMode.DISTANCE) }
    var selectedTypes by remember { mutableStateOf(defaultSearchTypes) }
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var showRadiusPreview by remember { mutableStateOf(false) }
    var lastSubmittedParams by remember { mutableStateOf<SearchRequestParams?>(null) }

    val warningMessage = if (currentSession?.shouldShowTooManyResultsWarning == true) {
        stringResource(
            R.string.search_too_many_results_hint,
            currentSession.allResults.size,
            normalizedSettings.warnIfResultsAbove,
        )
    } else null

    LaunchedEffect(warningMessage) {
        if (warningMessage != null) {
            snackbarHostState.showSnackbar(warningMessage)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = hasLocationPermission(context)
    }

    val pageSize = normalizedSettings.resultsPageSize
    val sessionResults = currentSession?.allResults.orEmpty().filter { !it.isSanitized }
    val pagedResults = sessionResults
        .drop(pageIndex * pageSize)
        .take(pageSize)
    val canGoPrevious = pageIndex > 0
    val canGoNext = (pageIndex + 1) * pageSize < sessionResults.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(R.string.search_title), style = MaterialTheme.typography.headlineSmall)

        if (currentSession == null || isConfigExpanded) {
            SearchOriginSelector(
                selected = originMode,
                onSelected = { originMode = it },
            )

            if (originMode == SearchOriginMode.MANUAL_COORDINATES) {
                OutlinedTextField(
                    value = manualLat,
                    onValueChange = { manualLat = it },
                    label = { Text(stringResource(R.string.search_latitude_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = manualLon,
                    onValueChange = { manualLon = it },
                    label = { Text(stringResource(R.string.search_longitude_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Text(
                text = stringResource(R.string.search_radius_value_template, radiusMeters, minRadius, maxRadius),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = radiusMeters.toFloat(),
                onValueChange = { value ->
                    radiusMeters = value.toInt().coerceIn(minRadius, maxRadius)
                    showRadiusPreview = true
                },
                onValueChangeFinished = { showRadiusPreview = false },
                valueRange = minRadius.toFloat()..maxRadius.toFloat(),
            )

            SearchContextSelector(selected = selectedContext, onSelected = { selectedContext = it })
            SearchTypeSelector(selectedTypes = selectedTypes, onSelectedTypes = { selectedTypes = it })
            SearchSortSelector(selected = sortMode, onSelected = { sortMode = it })

            Button(
                onClick = {
                    scope.launch {
                        val radius = radiusMeters
                        val manualCoordinates = if (originMode == SearchOriginMode.MANUAL_COORDINATES) {
                            val lat = manualLat.toDoubleOrNull()
                            val lon = manualLon.toDoubleOrNull()
                            if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                                state = SearchUiState.Error(context.getString(R.string.search_error_invalid_coordinates))
                                return@launch
                            }
                            lat to lon
                        } else {
                            null
                        }
                        val requestParams = SearchRequestParams(
                            originMode = originMode,
                            manualLat = manualCoordinates?.first,
                            manualLon = manualCoordinates?.second,
                            radiusMeters = radius,
                            context = selectedContext,
                            sortMode = sortMode,
                            selectedTypes = selectedTypes.map { it.key }.toSet(),
                        )

                        if (currentSession != null && requestParams == lastSubmittedParams) {
                            onConfigExpandedChanged(false)
                            state = SearchUiState.Success
                            return@launch
                        }

                        state = SearchUiState.Loading
                        onPageIndexChanged(0)
                        onSessionChanged(null)

                        val origin = when (originMode) {
                            SearchOriginMode.CURRENT_LOCATION -> {
                                if (!hasLocationPermission) {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        ),
                                    )
                                    state = SearchUiState.Error(context.getString(R.string.search_error_location_permission))
                                    return@launch
                                }
                                getBestLastKnownLocation(context)?.let { it.latitude to it.longitude }
                                    ?: run {
                                        state = SearchUiState.Error(context.getString(R.string.search_error_location_unavailable))
                                        return@launch
                                    }
                            }

                            SearchOriginMode.MANUAL_COORDINATES -> manualCoordinates
                                ?: return@launch
                        }

                        val result = OverpassClient.queryParkingAreas(
                            lat = origin.first,
                            lon = origin.second,
                            radiusMeters = radius,
                        )

                        state = result.fold(
                            onSuccess = { elements ->
                                val mapped = elements
                                    .filter { selectedTypes.contains(it.placeType) }
                                    .map { it.toPlaceResult(origin.first, origin.second) }
                                    .let { applySanitization(it) }
                                    .let { list ->
                                        when (sortMode) {
                                            SearchSortMode.DISTANCE -> list.sortedBy { it.distanceMeters }
                                            SearchSortMode.SCORE -> list.sortedByDescending { it.score ?: 0f }
                                        }
                                    }

                                // Logging the final filtered results
                                Log.d("PARK_SPOTTER_FILTER", "=== FILTERED RESULTS (Count: ${mapped.size}) ===")
                                mapped.forEachIndexed { index, res ->
                                    Log.d("PARK_SPOTTER_FILTER", "RESULT[$index]: ID=${res.osmId} | TYPE=${res.osmType} | NAME=${res.name ?: "Unnamed"} | DIST=${res.distanceMeters}m | LAT=${res.latitude} | LON=${res.longitude} | TAGS=${res.tags}")
                                }
                                Log.d("PARK_SPOTTER_FILTER", "==============================================")

                                val session = SearchSessionState(
                                    originLat = origin.first,
                                    originLon = origin.second,
                                    radiusMeters = radius,
                                    context = selectedContext,
                                    allResults = mapped,
                                    shouldShowTooManyResultsWarning = mapped.size > normalizedSettings.warnIfResultsAbove,
                                )
                                onSessionChanged(session)
                                lastSubmittedParams = requestParams
                                onConfigExpandedChanged(false)
                                SearchUiState.Success
                            },
                            onFailure = {
                                SearchUiState.Error(
                                    it.message?.takeIf(String::isNotBlank)
                                        ?: context.getString(R.string.error_unknown),
                                )
                            },
                        )
                    }
                },
                enabled = state !is SearchUiState.Loading,
            ) {
                Text(stringResource(R.string.search_start_button))
            }
        } else if (currentSession != null) {
            SearchSessionSummaryCard(
                session = currentSession,
                onEdit = { onConfigExpandedChanged(true) },
            )
        }

        when (state) {
            is SearchUiState.Loading -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is SearchUiState.Error -> Text(
                text = stringResource(R.string.search_error_template, (state as SearchUiState.Error).message),
                color = MaterialTheme.colorScheme.error,
            )

            else -> Unit
        }

        if (currentSession != null && !isConfigExpanded) {
            Text(
                text = stringResource(R.string.search_results_count, sessionResults.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenMap) {
                Text(stringResource(R.string.search_view_map))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { if (canGoPrevious) onPageIndexChanged(pageIndex - 1) }, enabled = canGoPrevious) {
                    Text(stringResource(R.string.search_previous_page))
                }
                val totalPages = (sessionResults.size + pageSize - 1) / pageSize
                Text(
                    text = stringResource(R.string.search_page_index_template, pageIndex + 1, totalPages),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { if (canGoNext) onPageIndexChanged(pageIndex + 1) }, enabled = canGoNext) {
                    Text(stringResource(R.string.search_next_page))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            ) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pagedResults, key = { "${it.osmType}/${it.osmId}" }) { result ->
                        val index = sessionResults.indexOf(result)
                        SearchResultCard(
                            result = result,
                            displayIndex = if (index != -1) index + 1 else null,
                            onClick = { onResultClick(result) }
                        )
                    }
                }
            }
        }

    }

    if (showRadiusPreview) {
        RadiusPreviewDialog(
            origin = resolvePreviewOrigin(
                context = context,
                originMode = originMode,
                manualLat = manualLat,
                manualLon = manualLon,
            ),
            radiusMeters = radiusMeters,
            onDismiss = { showRadiusPreview = false },
        )
    }
}

@Composable
private fun SearchOriginSelector(
    selected: SearchOriginMode,
    onSelected: (SearchOriginMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == SearchOriginMode.CURRENT_LOCATION,
            onClick = { onSelected(SearchOriginMode.CURRENT_LOCATION) },
            label = { Text(stringResource(R.string.search_origin_current_location)) },
        )
        FilterChip(
            selected = selected == SearchOriginMode.MANUAL_COORDINATES,
            onClick = { onSelected(SearchOriginMode.MANUAL_COORDINATES) },
            label = { Text(stringResource(R.string.search_origin_manual_coordinates)) },
        )
    }
}

@Composable
private fun SearchContextSelector(
    selected: SearchContext,
    onSelected: (SearchContext) -> Unit,
) {
    Text(text = stringResource(R.string.search_context_label), style = MaterialTheme.typography.labelLarge)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        SearchContext.entries.forEach { context ->
            FilterChip(
                selected = selected == context,
                onClick = { onSelected(context) },
                label = { Text(stringResource(context.labelResId())) },
            )
        }
    }
}

@Composable
private fun SearchTypeSelector(
    selectedTypes: Set<ApiPlaceType>,
    onSelectedTypes: (Set<ApiPlaceType>) -> Unit,
) {
    Text(text = stringResource(R.string.search_types_label), style = MaterialTheme.typography.labelLarge)
    val options = listOf(
        ApiPlaceType.PARKING,
        ApiPlaceType.STREET,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selectedTypes.contains(option),
                onClick = {
                    val updated = if (selectedTypes.contains(option)) {
                        selectedTypes - option
                    } else {
                        selectedTypes + option
                    }
                    onSelectedTypes(if (updated.isEmpty()) selectedTypes else updated)
                },
                label = { Text(stringResource(option.labelResId())) },
            )
        }
    }
}

@Composable
private fun SearchSortSelector(
    selected: SearchSortMode,
    onSelected: (SearchSortMode) -> Unit,
) {
    Text(text = stringResource(R.string.search_sort_label), style = MaterialTheme.typography.labelLarge)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        SearchSortMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                label = { Text(stringResource(mode.labelResId())) },
            )
        }
    }
}

@Composable
private fun SearchSessionSummaryCard(
    session: SearchSessionState,
    onEdit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(
                    R.string.search_session_summary_template,
                    session.originLat,
                    session.originLon,
                    session.radiusMeters,
                    stringResource(session.context.labelResId()),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onEdit) {
                Text(stringResource(R.string.search_edit_session_button))
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: PlaceResult,
    displayIndex: Int? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (displayIndex != null) {
                Text(
                    text = "#$displayIndex",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = result.name ?: stringResource(R.string.result_name_unknown),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(result.placeType.toApiPlaceType().labelResId()),
                    style = MaterialTheme.typography.labelMedium,
                )
                HorizontalDivider()
                Text(
                    text = stringResource(
                        R.string.search_result_coords_template,
                        result.latitude,
                        result.longitude,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.search_result_distance_template, result.distanceMeters),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RadiusPreviewDialog(
    origin: GeoPoint,
    radiusMeters: Int,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(340.dp)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.search_radius_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    factory = { context ->
                        Configuration.getInstance().userAgentValue = context.packageName
                        MapView(context).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                        }
                    },
                    update = { mapView ->
                        mapView.overlays.clear()

                        val marker = Marker(mapView).apply {
                            id = "radius_preview_origin"
                            position = origin
                            title = mapView.context.getString(R.string.search_origin_marker_title)
                            icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_home_pin_marker_filled)?.mutate()?.apply {
                                setTint(ContextCompat.getColor(mapView.context, R.color.gray_dark))
                            }
                        }
                        mapView.overlays.add(marker)

                        val circle = Polygon(mapView).apply {
                            points = Polygon.pointsAsCircle(origin, radiusMeters.toDouble())
                            fillPaint.color = android.graphics.Color.argb(40, 58, 74, 92)
                            outlinePaint.color = android.graphics.Color.argb(220, 58, 74, 92)
                            outlinePaint.strokeWidth = 3f
                        }
                        mapView.overlays.add(circle)
                        mapView.controller.setCenter(origin)
                        mapView.controller.setZoom(
                            calculateRadiusPreviewZoom(
                                radiusMeters = radiusMeters,
                                centerLatitude = origin.latitude,
                                viewWidthPx = mapView.width,
                                viewHeightPx = mapView.height,
                            ),
                        )
                        mapView.invalidate()
                    },
                )
                Text(
                    text = stringResource(R.string.search_radius_preview_distance, radiusMeters),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun SearchContext.labelResId(): Int = when (this) {
    SearchContext.URBAN -> R.string.search_context_urban
    SearchContext.SUBURBAN -> R.string.search_context_suburban
    SearchContext.MIXED -> R.string.search_context_mixed
}

private fun SearchSortMode.labelResId(): Int = when (this) {
    SearchSortMode.DISTANCE -> R.string.search_sort_distance
    SearchSortMode.SCORE -> R.string.search_sort_score
}

private fun ApiPlaceType.labelResId(): Int = when (this) {
    ApiPlaceType.PARKING -> R.string.place_type_parking
    ApiPlaceType.STREET -> R.string.place_type_street
    ApiPlaceType.PARK -> R.string.place_type_park
    ApiPlaceType.CAMP_SITE -> R.string.place_type_camp_site
    ApiPlaceType.UNKNOWN -> R.string.place_type_unknown
}

private fun String.toApiPlaceType(): ApiPlaceType = ApiPlaceType.entries.firstOrNull { it.key == this } ?: ApiPlaceType.UNKNOWN

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

private fun resolvePreviewOrigin(
    context: Context,
    originMode: SearchOriginMode,
    manualLat: String,
    manualLon: String,
): GeoPoint {
    if (originMode == SearchOriginMode.MANUAL_COORDINATES) {
        val lat = manualLat.toDoubleOrNull()
        val lon = manualLon.toDoubleOrNull()
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
            return GeoPoint(lat, lon)
        }
    }
    val current = getBestLastKnownLocation(context)
    return if (current != null) {
        GeoPoint(current.latitude, current.longitude)
    } else {
        GeoPoint(38.696728, -9.364814)
    }
}

private fun calculateRadiusPreviewZoom(
    radiusMeters: Int,
    centerLatitude: Double,
    viewWidthPx: Int,
    viewHeightPx: Int,
): Double {
    if (viewWidthPx <= 0 || viewHeightPx <= 0) return 14.5
    val targetDiameterPx = min(viewWidthPx, viewHeightPx) * 0.98
    val metersPerPixel = (radiusMeters.coerceAtLeast(1) * 2.0) / targetDiameterPx
    val latitudeCorrection = cos(Math.toRadians(centerLatitude)).coerceAtLeast(0.0001)
    val adjusted = log2((156543.03392 * latitudeCorrection) / metersPerPixel)
    return adjusted.coerceIn(9.0, 19.0)
}

private fun createMarkerWithBorder(
    context: Context,
    @ColorInt fillColor: Int,
): Drawable? {
    val fill = ContextCompat.getDrawable(context, R.drawable.ic_marker_parkspotter_fill)?.mutate() ?: return null
    val border = ContextCompat.getDrawable(context, R.drawable.ic_marker_parkspotter_border)?.mutate() ?: return null
    
    val wrappedFill = DrawableCompat.wrap(fill)
    DrawableCompat.setTint(wrappedFill, fillColor)
    
    return LayerDrawable(arrayOf(wrappedFill, border))
}

private fun createTintedMarkerDrawable(
    context: Context,
    @DrawableRes drawableRes: Int,
    @ColorInt tintColor: Int,
): Drawable? {
    val drawable = ContextCompat.getDrawable(context, drawableRes)?.mutate() ?: return null
    val wrapped = DrawableCompat.wrap(drawable)
    DrawableCompat.setTint(wrapped, tintColor)
    return wrapped
}

private data class SearchRequestParams(
    val originMode: SearchOriginMode,
    val manualLat: Double?,
    val manualLon: Double?,
    val radiusMeters: Int,
    val context: SearchContext,
    val sortMode: SearchSortMode,
    val selectedTypes: Set<String>,
)

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun getBestLastKnownLocation(context: Context): Location? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = locationManager.getProviders(true)
    return providers
        .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
}

private fun applySanitization(results: List<PlaceResult>): List<PlaceResult> {
    var workingList = results

    // Rule 1: Exact coordinates sanitization
    workingList = sanitizeExactDuplicates(workingList)

    // Rule 2: Suppress access aisles near parking amenities (Situation 2 & 3)
    workingList = suppressAccessAislesByProximity(workingList)

    // Rule 3: Deduplicate consecutive/redundant aisles (Situation 1)
    workingList = deduplicateRedundantAisles(workingList)

    return workingList
}

private fun sanitizeExactDuplicates(results: List<PlaceResult>): List<PlaceResult> {
    return results.mapIndexed { index, current ->
        if (current.isSanitized) return@mapIndexed current

        val duplicate = results.subList(0, index).find { other ->
            !other.isSanitized &&
                    haversineDistanceMeters(current.latitude, current.longitude, other.latitude, other.longitude) < 2
        }

        if (duplicate != null) {
            // Keep the 'way' if there's a conflict between node and way
            if (current.osmType == "node" && duplicate.osmType == "way") {
                current.copy(isSanitized = true, sanitizationReason = "Exact coordinate duplicate (kept way over node)")
            } else if (current.osmType == "way" && duplicate.osmType == "node") {
                // In this case we would have already processed the node, but since we are mapping, 
                // we'll handle this by marking current as active and we'd need to mark the previously processed as sanitized.
                // To keep it simple in a single pass: mark the current as sanitized if it's the "weaker" one.
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
        // Only sanitize if it's a parking_aisle
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
    // Only process parking_aisles that aren't already sanitized
    return results.mapIndexed { index, current ->
        if (current.isSanitized || current.tags["service"] != "parking_aisle") return@mapIndexed current

        // Look for other parking_aisles nearby (within 35m) that are already processed and NOT sanitized
        val nearbyAisle = results.subList(0, index).find { other ->
            !other.isSanitized &&
                    other.tags["service"] == "parking_aisle" &&
                    haversineDistanceMeters(current.latitude, current.longitude, other.latitude, other.longitude) <= 35
        }

        if (nearbyAisle != null) {
            // Rule 1 Logic: Keep the one further from origin
            if (current.distanceMeters > nearbyAisle.distanceMeters) {
                // We want to keep the current one. But the 'nearbyAisle' was already mapped as NOT sanitized.
                // This is a limitation of a single-pass map.
                // Let's refine the strategy: mark for sanitization if there is a 'better' one anywhere in the list.
                current
            } else {
                current.copy(isSanitized = true, sanitizationReason = "Redundant aisle (kept one further from origin)")
            }
        } else {
            // Check if there is a better candidate LATER in the list to avoid keeping a sub-optimal one
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

private fun haversineDistanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Int {
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadius * c).toInt()
}

private sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Success : SearchUiState
    data class Error(val message: String) : SearchUiState
}
