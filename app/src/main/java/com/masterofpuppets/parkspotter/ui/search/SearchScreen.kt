package com.masterofpuppets.parkspotter.ui.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onSessionChanged: (SearchSessionState?) -> Unit,
    onOpenMap: () -> Unit,
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
        mutableStateOf(1000.coerceIn(minRadius, maxRadius))
    }
    var selectedContext by remember { mutableStateOf(SearchContext.URBAN) }
    var sortMode by remember { mutableStateOf(SearchSortMode.DISTANCE) }
    var selectedTypes by remember { mutableStateOf(defaultSearchTypes) }
    var pageIndex by remember { mutableStateOf(0) }
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var showRadiusPreview by remember { mutableStateOf(false) }
    var lastSubmittedParams by remember { mutableStateOf<SearchRequestParams?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = hasLocationPermission(context)
    }

    val pageSize = normalizedSettings.resultsPageSize
    val sessionResults = currentSession?.allResults.orEmpty()
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

        if (currentSession?.shouldShowTooManyResultsWarning == true) {
            Text(
                text = stringResource(
                    R.string.search_too_many_results_hint,
                    currentSession.allResults.size,
                    normalizedSettings.warnIfResultsAbove,
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

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
                        pageIndex = 0
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
                                    .let { list ->
                                        when (sortMode) {
                                            SearchSortMode.DISTANCE -> list.sortedBy { it.distanceMeters }
                                            SearchSortMode.SCORE -> list.sortedByDescending { it.score ?: 0f }
                                        }
                                    }

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
                Button(onClick = { if (canGoPrevious) pageIndex -= 1 }, enabled = canGoPrevious) {
                    Text(stringResource(R.string.search_previous_page))
                }
                Text(
                    text = stringResource(R.string.search_page_index, pageIndex + 1),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { if (canGoNext) pageIndex += 1 }, enabled = canGoNext) {
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
                        SearchResultCard(result = result)
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
        ApiPlaceType.RESIDENTIAL_STREET,
        ApiPlaceType.LIVING_STREET,
        ApiPlaceType.PARKING_AISLE,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
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
private fun SearchResultCard(result: PlaceResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            icon = createTintedMarkerDrawable(
                                context = mapView.context,
                                drawableRes = R.drawable.ic_home_pin_marker,
                                tintColor = ContextCompat.getColor(mapView.context, R.color.primary_dark),
                            )
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
    ApiPlaceType.LIVING_STREET -> R.string.place_type_living_street
    ApiPlaceType.RESIDENTIAL_STREET -> R.string.place_type_residential_street
    ApiPlaceType.PARKING_AISLE -> R.string.place_type_parking_aisle
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
