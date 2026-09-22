package com.masterofpuppets.parkspotter.ui.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.location.LocationManager
import androidx.annotation.ColorInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.domain.model.PlaceResult
import com.masterofpuppets.parkspotter.domain.service.navigation.NavigationServiceImpl
import com.masterofpuppets.parkspotter.spike.ApiPlaceType
import com.masterofpuppets.parkspotter.spike.toApiPlaceType
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import androidx.core.graphics.ColorUtils
import com.masterofpuppets.parkspotter.ui.components.RadiusPreviewDialog
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsMapScreen(
    modifier: Modifier = Modifier,
    originLat: Double,
    originLon: Double,
    radiusMeters: Int = 500,
    resultsWithIndex: List<Pair<Int, PlaceResult>>,
    allResults: List<PlaceResult> = emptyList(),
    routeGeometry: List<Pair<Double, Double>> = emptyList(),
    currentPage: Int,
    totalPages: Int,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    selectedResult: PlaceResult? = null,
    navSettings: SearchUiSettings? = null,
    formState: SearchFormState? = null,
    onSelectResult: (PlaceResult?) -> Unit = {},
    onNextPage: () -> Unit = {},
    onPreviousPage: () -> Unit = {},
    onBack: () -> Unit = {},
    onApplyFilters: (newRadius: Int, newLat: Double, newLon: Double) -> Unit = { _, _, _ -> },
    onRetrySearch: () -> Unit = {},
) {
    val context = LocalContext.current
    val navService = remember { NavigationServiceImpl() }
    val markerFillColor = MaterialTheme.colorScheme.primary.toArgb()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showRadiusPreview by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().userAgentValue = ctx.packageName
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                }
            },
            update = { mapView ->
                mapView.overlays.clear()

                val points = mutableListOf<GeoPoint>()
                if (originLat != 0.0 || originLon != 0.0) {
                    val originPoint = GeoPoint(originLat, originLon)
                    points.add(originPoint)

                    val originMarker = Marker(mapView).apply {
                        id = "origin_marker"
                        position = originPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = mapView.context.getString(R.string.search_origin_marker_title)
                        icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_my_location_marker)?.mutate()?.apply {
                            setTint(ContextCompat.getColor(mapView.context, R.color.primary_dark))
                        }
                    }
                    mapView.overlays.add(originMarker)
                }

                resultsWithIndex.forEach { (displayIndex, result) ->
                    val resultPoint = GeoPoint(result.latitude, result.longitude)
                    points.add(resultPoint)
                    val marker = Marker(mapView).apply {
                        id = "${result.osmType}/${result.osmId}"
                        position = resultPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "$displayIndex. ${result.name ?: mapView.context.getString(R.string.result_name_unknown)}"
                        icon = createMarkerWithBorder(
                            context = mapView.context,
                            fillColor = markerFillColor,
                            text = displayIndex.toString()
                        )
                        setOnMarkerClickListener { _, _ ->
                            onSelectResult(result)
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }

                if (points.isNotEmpty()) {
                    mapView.post {
                        val boundingBox = BoundingBox.fromGeoPoints(points)
                        mapView.zoomToBoundingBox(boundingBox, true, 120)
                    }
                }

                mapView.invalidate()
            },
        )

        // Top Navigation and Action Bar
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.search_back_to_list),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPreviousPage, enabled = currentPage > 1 && !isLoading) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = stringResource(R.string.search_previous_page)
                        )
                    }
                    val safeTotalPages = totalPages.coerceAtLeast(1)
                    val label = stringResource(R.string.search_page_index_template, currentPage, safeTotalPages)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onNextPage, enabled = currentPage < totalPages && !isLoading) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.search_next_page)
                        )
                    }
                }

                IconButton(onClick = { showFilterSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.map_action_filter),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Loading Overlay
        if (isLoading) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text(
                        text = stringResource(R.string.search_loading_spots),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Error Banner
        if (errorMessage != null && !isLoading) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onBack) {
                            Text(stringResource(R.string.search_map_picker_cancel))
                        }
                        Button(onClick = onRetrySearch) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.search_action_retry))
                        }
                    }
                }
            }
        }

        // Empty Results Banner
        if (!isLoading && errorMessage == null && allResults.isEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.search_no_results_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { showFilterSheet = true }) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.map_action_filter))
                    }
                }
            }
        }

        // Result Detail Card (Bottom of Screen) - ONLY shown when a spot is actively selected
        if (selectedResult != null && !isLoading) {
            val globalIdx = allResults.indexOf(selectedResult)
            val displayNum = if (globalIdx != -1) globalIdx + 1 else 1
            val previousStops = if (globalIdx > 0) {
                allResults.take(globalIdx).map { it.latitude to it.longitude }
            } else emptyList()

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#$displayNum. ${selectedResult.name ?: stringResource(R.string.result_name_unknown)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onSelectResult(null) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.content_desc_close_card)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(selectedResult.placeType.toApiPlaceType().labelResId())} • ${stringResource(R.string.search_result_distance_template, selectedResult.distanceMeters)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )

                        if (!selectedResult.isFree) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            ) {
                                Text(
                                    text = stringResource(R.string.result_paid_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (selectedResult.contextMatches.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            selectedResult.contextMatches.forEach { contextMatch ->
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(contextMatch.labelResId()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                navService.navigateDirect(
                                    context = context,
                                    destLat = selectedResult.latitude,
                                    destLon = selectedResult.longitude,
                                    destName = selectedResult.name,
                                    targetPackageName = navSettings?.preferredNavAppPackage
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_navigate))
                        }

                        if (displayNum > 1 && previousStops.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                                    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    val liveLoc = if (fineGranted || coarseGranted) {
                                        locationManager?.getProviders(true)?.mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }?.maxByOrNull { it.time }
                                    } else null

                                    val liveOrigLat = liveLoc?.latitude ?: originLat
                                    val liveOrigLon = liveLoc?.longitude ?: originLon

                                    navService.navigateTrip(
                                        context = context,
                                        originLat = liveOrigLat,
                                        originLon = liveOrigLon,
                                        stops = previousStops,
                                        destLat = selectedResult.latitude,
                                        destLon = selectedResult.longitude,
                                        destName = selectedResult.name,
                                        targetPackageName = navSettings?.preferredNavAppPackage
                                    )
                                }
                            ) {
                                Text(stringResource(R.string.action_navigate_trip))
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                navService.copyCoordinatesToClipboard(context, selectedResult.latitude, selectedResult.longitude)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_copy_coords))
                        }
                    }
                }
            }
        }
    }

    // Filters Bottom Sheet
    if (showFilterSheet && formState != null) {
        val minR = (navSettings?.minRadiusMeters ?: 100).toFloat()
        val maxR = (navSettings?.maxRadiusMeters ?: 2000).toFloat()
        var tempRadius by remember { mutableFloatStateOf(formState.radiusMeters.toFloat()) }
        var tempLat by remember(originLat) { mutableDoubleStateOf(originLat) }
        var tempLon by remember(originLon) { mutableDoubleStateOf(originLon) }
        var showPickerInFilters by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search_filter_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showFilterSheet = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.search_map_picker_cancel))
                    }
                }

                // Search Location Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.search_section_location_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (tempLat != 0.0 || tempLon != 0.0) {
                        Text(
                            text = stringResource(R.string.search_result_coords_template, tempLat, tempLon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                                val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (fineGranted || coarseGranted) {
                                    val liveLoc = locationManager?.getProviders(true)?.mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }?.maxByOrNull { it.time }
                                    if (liveLoc != null) {
                                        tempLat = liveLoc.latitude
                                        tempLon = liveLoc.longitude
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.search_origin_current_location), style = MaterialTheme.typography.labelSmall)
                        }

                        FilledTonalButton(
                            onClick = { showPickerInFilters = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.search_btn_choose_map), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Radius Slider with real-time map preview
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.search_radius_preview_distance, tempRadius.roundToInt()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = tempRadius,
                        onValueChange = {
                            tempRadius = it
                            showRadiusPreview = true
                        },
                        onValueChangeFinished = {
                            showRadiusPreview = false
                        },
                        valueRange = minR..maxR,
                        steps = ((maxR - minR) / 100).toInt().coerceAtLeast(0),
                    )
                }

                // Zone Contexts
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.search_context_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SearchContext.entries.forEach { contextEntry ->
                            val isSelected = formState.selectedContexts.contains(contextEntry)
                            FilterChip(
                                selected = isSelected,
                                onClick = { formState.toggleContext(contextEntry) },
                                label = { Text(stringResource(contextEntry.labelResId())) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // Base Types & Free only
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.search_types_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(ApiPlaceType.PARKING, ApiPlaceType.STREET).forEach { typeOption ->
                            val isSelected = formState.selectedTypes.contains(typeOption)
                            FilterChip(
                                selected = isSelected,
                                onClick = { formState.toggleType(typeOption) },
                                label = { Text(stringResource(typeOption.labelResId())) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }

                        FilterChip(
                            selected = formState.freeOnly,
                            onClick = { formState.freeOnly = !formState.freeOnly },
                            label = { Text(stringResource(R.string.search_free_only_label)) },
                            leadingIcon = if (formState.freeOnly) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }

                // Sort Mode
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.search_sort_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SearchSortMode.entries.forEach { mode ->
                            val isSelected = formState.sortMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { formState.sortMode = mode },
                                label = { Text(stringResource(mode.labelResId())) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            formState.resetFilterParams()
                            tempRadius = (navSettings?.minRadiusMeters ?: 500).toFloat()
                            tempLat = originLat
                            tempLon = originLon
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.search_reset_filters_button))
                    }

                    Button(
                        onClick = {
                            val newRadius = tempRadius.roundToInt()
                            formState.radiusMeters = newRadius
                            onApplyFilters(newRadius, tempLat, tempLon)
                            showFilterSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.search_apply_filter_button))
                    }
                }
            }
        }

        if (showPickerInFilters) {
            MapLocationPickerDialog(
                initialLat = tempLat.takeIf { it != 0.0 } ?: 38.696728,
                initialLon = tempLon.takeIf { it != 0.0 } ?: -9.364814,
                onDismiss = { showPickerInFilters = false },
                onConfirm = { lat, lon ->
                    tempLat = lat
                    tempLon = lon
                    showPickerInFilters = false
                },
                onGetCurrentLocation = {
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fineGranted || coarseGranted) {
                        locationManager?.getProviders(true)?.mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }?.maxByOrNull { it.time }?.let {
                            GeoPoint(it.latitude, it.longitude)
                        }
                    } else null
                }
            )
        }

        if (showRadiusPreview) {
            val previewCenter = GeoPoint(
                tempLat.takeIf { it != 0.0 } ?: (originLat.takeIf { it != 0.0 } ?: 38.696728),
                tempLon.takeIf { it != 0.0 } ?: (originLon.takeIf { it != 0.0 } ?: -9.364814)
            )
            RadiusPreviewDialog(
                origin = previewCenter,
                radiusMeters = tempRadius.roundToInt(),
                onDismiss = { showRadiusPreview = false }
            )
        }
    }
}

private fun SearchContext.labelResId(): Int = when (this) {
    SearchContext.RESIDENTIAL -> R.string.search_context_residential
    SearchContext.COMMERCIAL_INDUSTRIAL_SERVICES -> R.string.search_context_commercial_industrial_services
    SearchContext.NATURE_DEDICATED -> R.string.search_context_nature_dedicated
    SearchContext.OTHER -> R.string.search_context_other
}

private fun SearchSortMode.labelResId(): Int = when (this) {
    SearchSortMode.BEST_ROUTE -> R.string.search_sort_best_route
    SearchSortMode.DISTANCE -> R.string.search_sort_distance
    SearchSortMode.SCORE -> R.string.search_sort_score
}

private fun createMarkerWithBorder(
    context: Context,
    @ColorInt fillColor: Int,
    text: String? = null,
): Drawable? {
    val fill = ContextCompat.getDrawable(context, R.drawable.ic_marker_parkspotter_fill)?.mutate() ?: return null
    val border = ContextCompat.getDrawable(context, R.drawable.ic_marker_parkspotter_border)?.mutate() ?: return null

    val wrappedFill = DrawableCompat.wrap(fill)
    DrawableCompat.setTint(wrappedFill, fillColor)

    val layerDrawable = LayerDrawable(arrayOf(wrappedFill, border))
    if (text == null) return layerDrawable

    val width = layerDrawable.intrinsicWidth
    val height = layerDrawable.intrinsicHeight
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    layerDrawable.setBounds(0, 0, width, height)
    layerDrawable.draw(canvas)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = (width * 0.35f).coerceAtLeast(24f)
        isFakeBoldText = true
    }

    val textBounds = Rect()
    paint.getTextBounds(text, 0, text.length, textBounds)
    val x = width / 2f
    val y = height * 0.32f + (textBounds.height() / 2f)

    canvas.drawText(text, x, y, paint)

    return BitmapDrawable(context.resources, bitmap)
}
