package com.masterofpuppets.parkspotter.ui.zone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.domain.model.ZoneCategory
import com.masterofpuppets.parkspotter.domain.model.ZoneFactor
import com.masterofpuppets.parkspotter.domain.model.ZoneFactorType
import com.masterofpuppets.parkspotter.domain.model.ZoneRating
import com.masterofpuppets.parkspotter.domain.model.ZoneRecommendation
import com.masterofpuppets.parkspotter.domain.service.navigation.NavigationServiceImpl
import com.masterofpuppets.parkspotter.ui.search.MapLocationPickerDialog
import com.masterofpuppets.parkspotter.ui.search.SearchUiSettings
import com.masterofpuppets.parkspotter.ui.components.LoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.util.Locale

@Composable
fun ZoneSearchScreen(
    modifier: Modifier = Modifier,
    viewModel: ZoneSearchViewModel,
    navSettings: SearchUiSettings,
    snackbarHostState: SnackbarHostState,
    onSearchParking: (Double, Double, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locationQuery by rememberSaveable { mutableStateOf("") }
    var selectedLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var selectedLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var showMapPicker by remember { mutableStateOf(false) }
    var isResolvingLocation by remember { mutableStateOf(false) }
    var expandedZoneId by rememberSaveable { mutableStateOf<String?>(null) }
    val zoneSearchState = viewModel.uiState
    val loadingMessages = listOf(
        stringResource(R.string.zone_loading_locality),
        stringResource(R.string.zone_loading_osm),
        stringResource(R.string.zone_loading_residential),
        stringResource(R.string.zone_loading_access),
        stringResource(R.string.zone_loading_density),
        stringResource(R.string.zone_loading_route),
        stringResource(R.string.zone_loading_alternatives),
        stringResource(R.string.zone_loading_comparing),
        stringResource(R.string.zone_loading_recommendations),
    )

    fun selectCurrentLocation() {
        val location = getBestLastKnownLocation(context)
        if (location == null) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.zone_search_location_unavailable))
            }
            return
        }
        selectedLat = location.latitude
        selectedLon = location.longitude
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = hasLocationPermission(context)
        if (hasLocationPermission) {
            selectCurrentLocation()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.search_error_location_permission))
            }
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission && selectedLat == null) {
            selectCurrentLocation()
        }
    }

    LaunchedEffect(zoneSearchState) {
        if (zoneSearchState is ZoneSearchUiState.Error) {
            snackbarHostState.showSnackbar(zoneSearchState.message)
            viewModel.consumeError()
        }
    }

    fun openCurrentLocation() {
        if (hasLocationPermission) {
            selectCurrentLocation()
            if (selectedLat != null && selectedLon != null) {
                showMapPicker = true
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    fun resolveTypedLocation() {
        val query = locationQuery.trim()
        if (query.isBlank()) {
            if (hasLocationPermission) {
                selectCurrentLocation()
                if (selectedLat != null && selectedLon != null) {
                    showMapPicker = true
                }
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
            return
        }

        scope.launch {
            isResolvingLocation = true
            val result = geocodeLocation(context, query)
            isResolvingLocation = false
            if (result == null) {
                snackbarHostState.showSnackbar(context.getString(R.string.zone_search_location_not_found))
            } else {
                selectedLat = result.latitude
                selectedLon = result.longitude
                showMapPicker = true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.zone_search_title),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.zone_search_intro),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.zone_search_location_title),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = locationQuery,
                    onValueChange = {
                        locationQuery = it
                        selectedLat = null
                        selectedLon = null
                    },
                    label = { Text(stringResource(R.string.zone_search_location_label)) },
                    placeholder = { Text(stringResource(R.string.zone_search_location_hint)) },
                    enabled = !isResolvingLocation,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = ::openCurrentLocation,
                        enabled = !isResolvingLocation,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.search_btn_current_location))
                    }
                    Button(
                        onClick = ::resolveTypedLocation,
                        enabled = !isResolvingLocation,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isResolvingLocation) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.width(20.dp),
                            )
                        } else {
                            Icon(Icons.Default.Map, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.zone_search_show_on_map))
                        }
                    }
                }

                if (selectedLat != null && selectedLon != null) {
                    Text(
                        text = stringResource(
                            R.string.zone_search_selected_coordinates,
                            selectedLat ?: 0.0,
                            selectedLon ?: 0.0,
                        ),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = { showMapPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.zone_search_adjust_on_map))
                    }

                    Button(
                        onClick = {
                            val currentLocation = getBestLastKnownLocation(context)
                            viewModel.search(
                                context = context,
                                latitude = selectedLat ?: return@Button,
                                longitude = selectedLon ?: return@Button,
                                originLatitude = currentLocation?.latitude,
                                originLongitude = currentLocation?.longitude,
                            )
                        },
                        enabled = zoneSearchState !is ZoneSearchUiState.Loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.zone_search_start))
                    }
                }
            }
        }

        if (zoneSearchState is ZoneSearchUiState.Loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                LoadingIndicator(
                    showText = true,
                    messages = loadingMessages,
                )
            }
        }

        if (zoneSearchState is ZoneSearchUiState.Success &&
            viewModel.recommendations.isEmpty()
        ) {
            Text(
                text = stringResource(R.string.zone_search_no_results),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }

        if (viewModel.recommendations.isNotEmpty()) {
            Text(
                text = stringResource(R.string.zone_search_destination_zones),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        viewModel.recommendations.forEach { zone ->
            ZoneRecommendationCard(
                zone = zone,
                expanded = expandedZoneId == zone.id,
                navSettings = navSettings,
                snackbarHostState = snackbarHostState,
                onToggleExpanded = {
                    expandedZoneId = if (expandedZoneId == zone.id) null else zone.id
                },
                onSearchParking = onSearchParking,
            )
        }

        if (viewModel.routeAlternatives.isNotEmpty()) {
            Text(
                text = stringResource(R.string.zone_search_route_alternatives),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.zone_search_route_alternatives_description),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
        viewModel.routeAlternatives.forEach { zone ->
            ZoneRecommendationCard(
                zone = zone,
                expanded = expandedZoneId == zone.id,
                navSettings = navSettings,
                snackbarHostState = snackbarHostState,
                onToggleExpanded = {
                    expandedZoneId = if (expandedZoneId == zone.id) null else zone.id
                },
                onSearchParking = onSearchParking,
            )
        }
    }

    if (showMapPicker) {
        MapLocationPickerDialog(
            initialLat = selectedLat,
            initialLon = selectedLon,
            initialZoom = 12.0,
            onDismiss = { showMapPicker = false },
            onConfirm = { lat, lon ->
                selectedLat = lat
                selectedLon = lon
                showMapPicker = false
            },
            onGetCurrentLocation = {
                getBestLastKnownLocation(context)?.let { GeoPoint(it.latitude, it.longitude) }
            },
        )
    }
}

@Composable
private fun ZoneRecommendationCard(
        zone: ZoneRecommendation,
        expanded: Boolean,
        navSettings: SearchUiSettings,
        snackbarHostState: SnackbarHostState,
        onToggleExpanded: () -> Unit,
        onSearchParking: (Double, Double, Int) -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val navigationService = remember { NavigationServiceImpl() }
        val displayName = zone.name.ifBlank {
            stringResource(zone.category.fallbackNameResId())
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onToggleExpanded,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = displayName,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(zone.category.labelResId()),
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(
                        R.string.zone_search_score_summary,
                        zone.score,
                        zone.confidence,
                        stringResource(zone.rating.labelResId()),
                    ),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )

                if (expanded) {
                    HorizontalDivider()
                    zone.factors.take(6).forEach { factor ->
                        Text(
                            text = factorLabel(factor),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.zone_search_osm_reference,
                            zone.osmReference,
                        ),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }

                Button(
                    onClick = {
                        val launched = navigationService.navigateDirect(
                            context = context,
                            destLat = zone.targetCoordinate.latitude,
                            destLon = zone.targetCoordinate.longitude,
                            destName = displayName,
                            targetPackageName = navSettings.preferredNavAppPackage,
                        )
                        if (!launched) {
                            navigationService.copyCoordinatesToClipboard(
                                context,
                                zone.targetCoordinate.latitude,
                                zone.targetCoordinate.longitude,
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.feedback_nav_app_not_found),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.zone_search_navigate))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            navigationService.copyCoordinatesToClipboard(
                                context,
                                zone.targetCoordinate.latitude,
                                zone.targetCoordinate.longitude,
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.feedback_coords_copied),
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.zone_search_copy_destination))
                    }
                    TextButton(
                        onClick = {
                            onSearchParking(
                                zone.targetCoordinate.latitude,
                                zone.targetCoordinate.longitude,
                                zone.recommendedMicroRadiusMeters,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.zone_search_parking_search))
                    }
                }
            }
        }
}

@Composable
private fun factorLabel(factor: ZoneFactor): String {
        val prefix = if (factor.impact > 0) "+ " else "− "
        return prefix + stringResource(factor.type.labelResId())
}

private fun ZoneCategory.labelResId(): Int = when (this) {
        ZoneCategory.RESIDENTIAL_LOW_DENSITY -> R.string.zone_category_residential
        ZoneCategory.INDUSTRIAL_COMMERCIAL -> R.string.zone_category_industrial
        ZoneCategory.SERVICE_24_7 -> R.string.zone_category_service
}

private fun ZoneCategory.fallbackNameResId(): Int = when (this) {
    ZoneCategory.RESIDENTIAL_LOW_DENSITY -> R.string.zone_name_residential
    ZoneCategory.INDUSTRIAL_COMMERCIAL -> R.string.zone_name_industrial
    ZoneCategory.SERVICE_24_7 -> R.string.zone_name_service
}

private fun ZoneRating.labelResId(): Int = when (this) {
        ZoneRating.MOST_SUITABLE -> R.string.zone_rating_most_suitable
        ZoneRating.SUITABLE -> R.string.zone_rating_suitable
        ZoneRating.POSSIBLE_WITH_VERIFICATION -> R.string.zone_rating_possible
        ZoneRating.DISCOURAGED -> R.string.zone_rating_discouraged
}

private fun ZoneFactorType.labelResId(): Int = when (this) {
        ZoneFactorType.RESIDENTIAL_LAND_USE -> R.string.zone_factor_residential_land_use
        ZoneFactorType.LOW_DENSITY_HOUSING -> R.string.zone_factor_low_density
        ZoneFactorType.RESIDENTIAL_ROADS -> R.string.zone_factor_residential_roads
        ZoneFactorType.PUBLIC_ROAD_ACCESS -> R.string.zone_factor_public_access
        ZoneFactorType.AVAILABLE_PARKING -> R.string.zone_factor_parking
        ZoneFactorType.NAMED_ZONE -> R.string.zone_factor_named
        ZoneFactorType.INDUSTRIAL_LAND_USE -> R.string.zone_factor_industrial
        ZoneFactorType.OPEN_24_7 -> R.string.zone_factor_open_all_day
        ZoneFactorType.TRAVEL_SERVICES -> R.string.zone_factor_travel_services
        ZoneFactorType.APARTMENT_DENSITY -> R.string.zone_factor_apartments
        ZoneFactorType.NIGHTLIFE_NEARBY -> R.string.zone_factor_nightlife
        ZoneFactorType.MAJOR_ROAD_NEARBY -> R.string.zone_factor_major_road
        ZoneFactorType.RESTRICTED_ACCESS -> R.string.zone_factor_restricted_access
        ZoneFactorType.LIMITED_OSM_DATA -> R.string.zone_factor_limited_data
}

private data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
)

@Suppress("DEPRECATION")
private suspend fun geocodeLocation(context: Context, query: String): ResolvedLocation? =
    withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            Geocoder(context, Locale.getDefault())
                .getFromLocationName(query, 1)
                ?.firstOrNull()
                ?.let { ResolvedLocation(it.latitude, it.longitude) }
        }.getOrNull()
    }

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun getBestLastKnownLocation(context: Context): Location? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null
    return locationManager.getProviders(true)
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }
}
