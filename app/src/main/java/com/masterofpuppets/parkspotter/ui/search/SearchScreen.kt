package com.masterofpuppets.parkspotter.ui.search

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.CustomZoomButtonsController
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.spike.toApiPlaceType
import com.masterofpuppets.parkspotter.domain.model.PlaceResult
import com.masterofpuppets.parkspotter.spike.ApiPlaceType
import com.masterofpuppets.parkspotter.spike.OverpassClient
import com.masterofpuppets.parkspotter.spike.OverpassElement
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
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
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
private fun CoordinatesInputCard(
    currentQuery: String,
    onQueryConfirmed: (String) -> Unit,
    onInvalidFormat: () -> Unit,
    onEditingStateChanged: (Boolean) -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    var draftQuery by remember(isEditing, currentQuery) { mutableStateOf(currentQuery) }
    val clipboardManager = LocalClipboardManager.current

    // Notifica o pai quando o estado de edição muda
    LaunchedEffect(isEditing) {
        onEditingStateChanged(isEditing)
    }

    val pasteSuggestion = remember {
        val clipText = clipboardManager.getText()?.text ?: ""
        if (sanitizeCoordinatesString(clipText) != null) clipText else null
    }

    if (isEditing) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = draftQuery,
                onValueChange = { draftQuery = it },
                label = { Text(stringResource(R.string.search_manual_location_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    Row {
                        IconButton(onClick = { isEditing = false }) {
                            Icon(Icons.Default.Clear, contentDescription = "Cancel")
                        }
                        IconButton(onClick = {
                            val finalQuery = if (draftQuery.isBlank()) currentQuery else draftQuery
                            val parsed = parseCoordinates(finalQuery)
                            if (parsed != null) {
                                onQueryConfirmed(formatCoordinates(parsed.first, parsed.second))
                                isEditing = false
                            } else {
                                onInvalidFormat()
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Confirm", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
            if (pasteSuggestion != null && pasteSuggestion != draftQuery) {
                FilterChip(
                    selected = false,
                    onClick = { draftQuery = pasteSuggestion },
                    label = { Text("Paste: $pasteSuggestion") }
                )
            }
        }
    } else {
        OutlinedTextField(
            value = currentQuery,
            onValueChange = { },
            label = { Text(stringResource(R.string.search_manual_location_label)) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { isEditing = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
            }
        )
    }
}

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel,
    settings: SearchUiSettings,
    onOpenMap: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val normalizedSettings = settings.normalized()
    val minRadius = normalizedSettings.minRadiusMeters
    val maxRadius = normalizedSettings.maxRadiusMeters
    
    viewModel.initFormState(minRadius)
    val formState = viewModel.searchFormState
    val currentSession = viewModel.searchSession
    val isConfigExpanded = viewModel.isSearchConfigExpanded
    val pageIndex = viewModel.searchPageIndex
    val state = viewModel.uiState

    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var showRadiusPreview by remember { mutableStateOf(false) }
    var showMapPicker by remember { mutableStateOf(false) }

    val warningMessage = if (currentSession?.shouldShowTooManyResultsWarning == true) {
        stringResource(
            R.string.search_too_many_results_hint,
            currentSession.filteredResults.size,
            normalizedSettings.warnIfResultsAbove,
        )
    } else null

    LaunchedEffect(warningMessage) {
        if (warningMessage != null) {
            snackbarHostState.showSnackbar(warningMessage)
        }
    }

    LaunchedEffect(state) {
        if (state is SearchUiState.Error) {
            val errorMessage = (state as SearchUiState.Error).message
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.resetState()
        }
    }

    val updateCurrentLocation = {
        if (hasLocationPermission) {
            getBestLastKnownLocation(context)?.let {
                formState.locationQuery = formatCoordinates(it.latitude, it.longitude)
            } ?: run {
                viewModel.setError(context.getString(R.string.search_error_location_unavailable))
            }
        }
    }

    var isEditingCoordinates by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = hasLocationPermission(context)
        if (hasLocationPermission) {
            updateCurrentLocation()
        }
    }

    LaunchedEffect(Unit) {
        if (formState.locationQuery.isBlank() && hasLocationPermission) {
            updateCurrentLocation()
        }
    }

    val pageSize = normalizedSettings.resultsPageSize
    val sessionResults = currentSession?.filteredResults.orEmpty()
    val pagedResults = sessionResults
        .drop(pageIndex * pageSize)
        .take(pageSize)
    val canGoPrevious = pageIndex > 0
    val canGoNext = (pageIndex + 1) * pageSize < sessionResults.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(R.string.search_title), style = MaterialTheme.typography.headlineSmall)

        if (currentSession == null || isConfigExpanded) {
            val invalidFormatMsg = stringResource(R.string.search_error_invalid_coordinates_format)
            val hasRawResults = currentSession?.rawResults?.isNotEmpty() == true

            // Card 1: Search Parameters (API Data Fetching)
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.search_section_location_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    CoordinatesInputCard(
                        currentQuery = formState.locationQuery,
                        onQueryConfirmed = { formState.locationQuery = it },
                        onInvalidFormat = {
                            scope.launch { snackbarHostState.showSnackbar(invalidFormatMsg) }
                        },
                        onEditingStateChanged = { isEditing ->
                            isEditingCoordinates = isEditing
                        }
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = {
                            if (!hasLocationPermission) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            } else {
                                updateCurrentLocation()
                            }
                        }) {
                            Icon(Icons.Default.MyLocation, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.search_btn_current_location))
                        }

                        TextButton(onClick = { showMapPicker = true }) {
                            Icon(Icons.Default.Map, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.search_btn_choose_map))
                        }
                    }

                    if (showMapPicker) {
                        val currentCoords = parseCoordinates(formState.locationQuery)
                        MapLocationPickerDialog(
                            initialLat = currentCoords?.first,
                            initialLon = currentCoords?.second,
                            onDismiss = { showMapPicker = false },
                            onConfirm = { lat, lon ->
                                formState.locationQuery = formatCoordinates(lat, lon)
                                showMapPicker = false
                            },
                            onGetCurrentLocation = {
                                getBestLastKnownLocation(context)?.let {
                                    GeoPoint(it.latitude, it.longitude)
                                }
                            }
                        )
                    }

                    Text(
                        text = stringResource(R.string.search_radius_value_template, formState.radiusMeters, minRadius, maxRadius),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = formState.radiusMeters.toFloat(),
                        onValueChange = { value ->
                            formState.radiusMeters = value.toInt().coerceIn(minRadius, maxRadius)
                            showRadiusPreview = true
                        },
                        onValueChangeFinished = { showRadiusPreview = false },
                        valueRange = minRadius.toFloat()..maxRadius.toFloat(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { formState.resetSearchParams(minRadius) },
                            enabled = state !is SearchUiState.Loading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.search_reset_search_button))
                        }

                        Button(
                            onClick = {
                                val coords = extractCoordinatesPair(formState.locationQuery)
                                if (coords == null) {
                                    viewModel.setError(context.getString(R.string.search_error_invalid_coordinates_format))
                                    return@Button
                                }

                                viewModel.executeSearch(
                                    context = context,
                                    radius = formState.radiusMeters,
                                    coords = coords,
                                    normalizedSettings = normalizedSettings
                                )
                            },
                            enabled = state !is SearchUiState.Loading && !isEditingCoordinates,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state is SearchUiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp).width(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(stringResource(R.string.search_start_button))
                            }
                        }
                    }
                }
            }

            // Card 2: Result Filters & Sorting
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.search_section_filter_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    SearchContextSelector(
                        selectedContexts = formState.selectedContexts,
                        onToggleContext = { formState.toggleContext(it) }
                    )
                    SearchTypeSelector(
                        selectedTypes = formState.selectedTypes,
                        freeOnly = formState.freeOnly,
                        onSelectedTypes = { formState.selectedTypes = it },
                        onFreeOnlyChanged = { formState.freeOnly = it }
                    )
                    SearchSortSelector(
                        selected = formState.sortMode,
                        onSelected = { formState.sortMode = it }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { formState.resetFilterParams() },
                            enabled = state !is SearchUiState.Loading && hasRawResults,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.search_reset_filters_button))
                        }

                        Button(
                            onClick = {
                                viewModel.applyLocalFilter(normalizedSettings)
                            },
                            enabled = state !is SearchUiState.Loading && hasRawResults,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.search_apply_filter_button))
                        }
                    }
                }
            }
        } else {
            SearchSessionSummaryCard(
                session = currentSession,
                onEdit = { viewModel.isSearchConfigExpanded = true },
            )
        }

        if (state is SearchUiState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
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
                Button(onClick = { if (canGoPrevious) viewModel.searchPageIndex = pageIndex - 1 }, enabled = canGoPrevious) {
                    Text(stringResource(R.string.search_previous_page))
                }
                val totalPages = (sessionResults.size + pageSize - 1) / pageSize
                Text(
                    text = stringResource(R.string.search_page_index_template, pageIndex + 1, totalPages),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { if (canGoNext) viewModel.searchPageIndex = pageIndex + 1 }, enabled = canGoNext) {
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
                        val previousStops = if (index > 0) {
                            sessionResults.take(index).map { it.latitude to it.longitude }
                        } else emptyList()

                        SearchResultCard(
                            result = result,
                            displayIndex = if (index != -1) index + 1 else null,
                            originLat = currentSession.originLat,
                            originLon = currentSession.originLon,
                            allPreviousStops = previousStops,
                            navSettings = normalizedSettings,
                            snackbarHostState = snackbarHostState,
                            onClick = { viewModel.selectedResultForMap = result; onOpenMap() }
                        )
                    }
                }
            }
        }

    }

    if (showRadiusPreview) {
        val currentCoords = extractCoordinatesPair(formState.locationQuery)
        val centerPoint = if (currentCoords != null) {
            GeoPoint(currentCoords.first, currentCoords.second)
        } else {
            getBestLastKnownLocation(context)?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(41.1496, -8.6109)
        }
        
        RadiusPreviewDialog(
            origin = centerPoint,
            radiusMeters = formState.radiusMeters,
            onDismiss = { showRadiusPreview = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContextSelector(
    selectedContexts: Set<SearchContext>,
    onToggleContext: (SearchContext) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val displayText = when {
        selectedContexts.isEmpty() || selectedContexts.size == SearchContext.entries.size -> "All"
        selectedContexts.size == 1 -> stringResource(selectedContexts.first().labelResId())
        else -> "${selectedContexts.size} selected"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.search_context_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SearchContext.entries.forEach { context ->
                val isSelected = selectedContexts.contains(context)
                DropdownMenuItem(
                    text = { Text(stringResource(context.labelResId())) },
                    leadingIcon = {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null
                        )
                    },
                    onClick = {
                        onToggleContext(context)
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchTypeSelector(
    selectedTypes: Set<ApiPlaceType>,
    freeOnly: Boolean,
    onSelectedTypes: (Set<ApiPlaceType>) -> Unit,
    onFreeOnlyChanged: (Boolean) -> Unit,
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

        FilterChip(
            selected = freeOnly,
            onClick = { onFreeOnlyChanged(!freeOnly) },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.search_free_only_label))
                }
            },
            leadingIcon = if (freeOnly) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSortSelector(
    selected: SearchSortMode,
    onSelected: (SearchSortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = stringResource(selected.labelResId()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.search_sort_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SearchSortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.labelResId())) },
                    onClick = {
                        onSelected(mode)
                        expanded = false
                    }
                )
            }
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
            val contextSummary = if (session.selectedContexts.isEmpty() || session.selectedContexts.size == SearchContext.entries.size) {
                "All"
            } else {
                session.selectedContexts.map { stringResource(it.labelResId()) }.joinToString(", ")
            }
            Text(
                text = stringResource(
                    R.string.search_session_summary_template,
                    session.originLat,
                    session.originLon,
                    session.radiusMeters,
                    contextSummary,
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
    originLat: Double? = null,
    originLon: Double? = null,
    allPreviousStops: List<Pair<Double, Double>> = emptyList(),
    navSettings: SearchUiSettings,
    snackbarHostState: SnackbarHostState,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navService = remember { com.masterofpuppets.parkspotter.domain.service.navigation.NavigationServiceImpl() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        text = "${result.name ?: stringResource(R.string.result_name_unknown)} [OSM ID: ${result.osmId}]",
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(result.placeType.toApiPlaceType().labelResId()),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        
                        val badgeColor = if (result.isFree) Color(0xFF2D6A4F) else Color(0xFFB3261E)
                        Surface(
                            color = badgeColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (result.isFree) stringResource(R.string.result_free_badge) else stringResource(R.string.result_paid_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
                    
                    val flagsSummary = if (result.contextMatches.isEmpty()) {
                        "None"
                    } else {
                        result.contextMatches.map { stringResource(it.labelResId()) }.joinToString(", ")
                    }
                    Text(
                        text = "Context Flags: $flagsSummary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            HorizontalDivider()

            // Navigation and Copy Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direct Navigation
                Button(
                    onClick = {
                        val launched = navService.navigateDirect(
                            context = context,
                            destLat = result.latitude,
                            destLon = result.longitude,
                            destName = result.name,
                            targetPackageName = navSettings.preferredNavAppPackage
                        )
                        if (!launched) {
                            navService.copyCoordinatesToClipboard(context, result.latitude, result.longitude)
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.feedback_nav_app_not_found))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_navigate_direct))
                }

                // Trip Navigation (Multi-stop starting from REAL-TIME CURRENT LOCATION passing through intermediate spots)
                if (displayIndex != null && displayIndex > 1) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            val currentLocation = getBestLastKnownLocation(context)
                            val liveOrigLat = currentLocation?.latitude ?: originLat ?: result.latitude
                            val liveOrigLon = currentLocation?.longitude ?: originLon ?: result.longitude

                            val launched = navService.navigateTrip(
                                context = context,
                                originLat = liveOrigLat,
                                originLon = liveOrigLon,
                                stops = allPreviousStops,
                                destLat = result.latitude,
                                destLon = result.longitude,
                                destName = result.name,
                                targetPackageName = navSettings.preferredNavAppPackage
                            )
                            if (!launched) {
                                navService.copyCoordinatesToClipboard(context, result.latitude, result.longitude)
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.feedback_nav_app_not_found))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_navigate_trip))
                    }
                }

                // Copy Action
                androidx.compose.material3.TextButton(
                    onClick = {
                        navService.copyCoordinatesToClipboard(context, result.latitude, result.longitude)
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.feedback_coords_copied))
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_copy_coords))
                }
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



private fun sanitizeCoordinatesString(query: String): String? {
    val cleanQuery = query.uppercase().trim()

    // Helper to evaluate and validate boundaries
    fun evaluate(lat: Double?, lon: Double?): String? {
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
            return String.format(java.util.Locale.US, "%.6f, %.6f", lat, lon)
        }
        return null
    }

    fun parseNumberTolerant(str: String): Double? {
        val s = str.trim().replace(',', '.')
        if (s.isEmpty()) return null
        return s.toDoubleOrNull()
    }

    val cleanDmsQuery = cleanQuery
        .replace(Regex("[^\\d\\.\\,\\-NSEW]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    val dmsRegex = Regex("^(\\d+(?:[.,]\\d+)?)\\s+(\\d+(?:[.,]\\d+)?)\\s+(\\d+(?:[.,]\\d+)?)\\s*([NS])\\s+(\\d+(?:[.,]\\d+)?)\\s+(\\d+(?:[.,]\\d+)?)\\s+(\\d+(?:[.,]\\d+)?)\\s*([EW])$")
    dmsRegex.matchEntire(cleanDmsQuery)?.let { match ->
        val latDeg = parseNumberTolerant(match.groupValues[1]) ?: 0.0
        val latMin = parseNumberTolerant(match.groupValues[2]) ?: 0.0
        val latSec = parseNumberTolerant(match.groupValues[3]) ?: 0.0
        var lat = latDeg + latMin / 60.0 + latSec / 3600.0
        if (match.groupValues[4] == "S") lat = -lat

        val lonDeg = parseNumberTolerant(match.groupValues[5]) ?: 0.0
        val lonMin = parseNumberTolerant(match.groupValues[6]) ?: 0.0
        val lonSec = parseNumberTolerant(match.groupValues[7]) ?: 0.0
        var lon = lonDeg + lonMin / 60.0 + lonSec / 3600.0
        if (match.groupValues[8] == "W") lon = -lon
        
        evaluate(lat, lon)?.let { return it }
    }
    
    val ddmRegex = Regex("^(\\d+(?:[.,]\\d+)?)\\s+(\\d+(?:[.,]\\d+)?)\\s*([NS])\\s+(\\d+(?:[.,]\\d+)?)\\s+(\\d+(?:[.,]\\d+)?)\\s*([EW])$")
    ddmRegex.matchEntire(cleanDmsQuery)?.let { match ->
        val latDeg = parseNumberTolerant(match.groupValues[1]) ?: 0.0
        val latMin = parseNumberTolerant(match.groupValues[2]) ?: 0.0
        var lat = latDeg + latMin / 60.0
        if (match.groupValues[3] == "S") lat = -lat

        val lonDeg = parseNumberTolerant(match.groupValues[4]) ?: 0.0
        val lonMin = parseNumberTolerant(match.groupValues[5]) ?: 0.0
        var lon = lonDeg + lonMin / 60.0
        if (match.groupValues[6] == "W") lon = -lon
        
        evaluate(lat, lon)?.let { return it }
    }
    
    val ddLettersRegex = Regex("^([-+]?\\d+(?:[.,]\\d+)?)\\s*([NS])?\\s+([-+]?\\d+(?:[.,]\\d+)?)\\s*([EW])?$")
    ddLettersRegex.matchEntire(cleanDmsQuery)?.let { match ->
        var lat = parseNumberTolerant(match.groupValues[1]) ?: return@let
        if (match.groupValues[2] == "S") lat = -lat
        var lon = parseNumberTolerant(match.groupValues[3]) ?: return@let
        if (match.groupValues[4] == "W") lon = -lon
        
        evaluate(lat, lon)?.let { return it }
    }

    val numberPattern = "([-+]?\\d+(?:[.,]\\d+)?)"
    val matcher = Regex(numberPattern).findAll(cleanQuery)
    val numbersList = matcher.map { it.value }.toList()
    
    if (numbersList.size >= 2) {
        val lat = parseNumberTolerant(numbersList[0])
        val lon = parseNumberTolerant(numbersList[1])
        evaluate(lat, lon)?.let { return it }
    }

    return null
}

private fun formatCoordinates(lat: Double, lon: Double): String {
    return String.format(java.util.Locale.US, "%.6f, %.6f", lat, lon)
}

private fun parseCoordinates(query: String): Pair<Double, Double>? {
    val sanitized = sanitizeCoordinatesString(query) ?: return null
    val parts = sanitized.split(",").map { it.trim() }
    if (parts.size == 2) {
        val lat = parts[0].toDoubleOrNull()
        val lon = parts[1].toDoubleOrNull()
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
            return lat to lon
        }
    }
    return null
}

private fun extractCoordinatesPair(query: String): Pair<Double, Double>? = parseCoordinates(query)

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

@Composable
fun MapLocationPickerDialog(
    initialLat: Double?,
    initialLon: Double?,
    initialZoom: Double = 16.0,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
    onGetCurrentLocation: () -> GeoPoint?
) {
    var mapCenter by remember {
        mutableStateOf(GeoPoint(initialLat ?: 41.1496, initialLon ?: -8.6109))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.search_map_picker_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = {
                        val currentLoc = onGetCurrentLocation()
                        if (currentLoc != null) {
                            mapCenter = currentLoc
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "My Location"
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    AndroidView(
                        factory = { context ->
                            MapView(context).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                                controller.setZoom(initialZoom)
                                controller.setCenter(mapCenter)
                                
                                addMapListener(object : MapListener {
                                    override fun onScroll(event: ScrollEvent?): Boolean {
                                        val center = this@apply.mapCenter
                                        val newCenter = GeoPoint(center.latitude, center.longitude)
                                        mapCenter = newCenter
                                        return true
                                    }
                                    override fun onZoom(event: ZoomEvent?): Boolean = true
                                })
                            }
                        },
                        update = { view ->
                            val currentLat = view.mapCenter.latitude
                            val currentLon = view.mapCenter.longitude
                            if (abs(currentLat - mapCenter.latitude) > 1e-5 || abs(currentLon - mapCenter.longitude) > 1e-5) {
                                view.controller.setCenter(mapCenter)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Center",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.search_map_picker_cancel))
                    }
                    Button(
                        onClick = {
                            val center = mapCenter
                            onConfirm(center.latitude, center.longitude)
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(stringResource(R.string.search_map_picker_ok))
                    }
                }
            }
        }
    }
}
