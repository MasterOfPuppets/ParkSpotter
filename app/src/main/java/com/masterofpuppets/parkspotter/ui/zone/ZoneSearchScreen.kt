package com.masterofpuppets.parkspotter.ui.zone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.ContextCompat
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.domain.model.ZoneCategory
import com.masterofpuppets.parkspotter.domain.model.ZoneFactorType
import com.masterofpuppets.parkspotter.domain.model.ZoneRating
import com.masterofpuppets.parkspotter.domain.model.ZoneRecommendation
import com.masterofpuppets.parkspotter.domain.service.navigation.NavigationServiceImpl
import com.masterofpuppets.parkspotter.domain.service.zone.ZoneClassificationConfig
import com.masterofpuppets.parkspotter.ui.components.LoadingIndicator
import com.masterofpuppets.parkspotter.ui.components.RadiusPreviewDialog
import com.masterofpuppets.parkspotter.ui.search.MapLocationPickerDialog
import com.masterofpuppets.parkspotter.ui.search.SearchUiSettings
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ZoneSearchScreen(
    modifier: Modifier = Modifier,
    viewModel: ZoneSearchViewModel,
    targetLat: Double,
    targetLon: Double,
    navSettings: SearchUiSettings,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit = {},
    onSearchParking: (Double, Double, Int) -> Unit,
    onChangeLocation: (Double, Double) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var expandedZoneId by remember { mutableStateOf<String?>(null) }
    var showMapPicker by remember { mutableStateOf(false) }
    var tempRadius by remember { mutableFloatStateOf(2500f) }
    var appliedRadiusMeters by remember { mutableIntStateOf(2500) }
    var showRadiusPreview by remember { mutableStateOf(false) }
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

    LaunchedEffect(zoneSearchState) {
        if (zoneSearchState is ZoneSearchUiState.Error) {
            snackbarHostState.showSnackbar(zoneSearchState.message)
            viewModel.consumeError()
            onBack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top Navigation Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.surface,
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

                Text(
                    text = stringResource(R.string.zone_search_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                IconButton(onClick = { showMapPicker = true }) {
                    Icon(
                        imageVector = Icons.Default.PinDrop,
                        contentDescription = stringResource(R.string.search_btn_choose_map),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Radius Slider with Apply Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val radiusKm = String.format(Locale.US, "%.1f km", tempRadius / 1000f)
                    Text(
                        text = "${stringResource(R.string.search_radius_label)}: $radiusKm",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
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
                        valueRange = 1000f..5000f,
                        steps = 7,
                    )
                }

                Button(
                    onClick = {
                        val newRadius = tempRadius.roundToInt()
                        appliedRadiusMeters = newRadius
                        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val liveLoc = if (fineGranted || coarseGranted) {
                            locManager?.getProviders(true)?.mapNotNull { runCatching { locManager.getLastKnownLocation(it) }.getOrNull() }?.maxByOrNull { it.time }
                        } else null

                        viewModel.search(
                            context = context,
                            latitude = targetLat,
                            longitude = targetLon,
                            originLatitude = liveLoc?.latitude ?: targetLat,
                            originLongitude = liveLoc?.longitude ?: targetLon,
                            config = ZoneClassificationConfig(analysisRadiusMeters = newRadius),
                        )
                    },
                    enabled = zoneSearchState !is ZoneSearchUiState.Loading && tempRadius.roundToInt() != appliedRadiusMeters,
                ) {
                    Text(stringResource(R.string.search_apply_filter_button))
                }
            }
        }

        // Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (zoneSearchState is ZoneSearchUiState.Loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        showText = true,
                        messages = loadingMessages,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (zoneSearchState is ZoneSearchUiState.Success && viewModel.recommendations.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.zone_search_no_results),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (viewModel.recommendations.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.zone_search_destination_zones),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
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
                    }

                    if (viewModel.routeAlternatives.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.zone_search_route_alternatives),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.zone_search_route_alternatives_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                }
            }
        }
    }

    if (showRadiusPreview) {
        RadiusPreviewDialog(
            origin = GeoPoint(targetLat, targetLon),
            radiusMeters = tempRadius.roundToInt(),
            onDismiss = { showRadiusPreview = false }
        )
    }

    if (showMapPicker) {
        val firstRec = viewModel.recommendations.firstOrNull()
        MapLocationPickerDialog(
            initialLat = firstRec?.targetCoordinate?.latitude ?: targetLat,
            initialLon = firstRec?.targetCoordinate?.longitude ?: targetLon,
            initialZoom = 13.0,
            onDismiss = { showMapPicker = false },
            onConfirm = { lat, lon ->
                showMapPicker = false
                onChangeLocation(lat, lon)
            },
            onGetCurrentLocation = {
                val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fineGranted || coarseGranted) {
                    locManager?.getProviders(true)?.mapNotNull { runCatching { locManager.getLastKnownLocation(it) }.getOrNull() }?.maxByOrNull { it.time }?.let {
                        GeoPoint(it.latitude, it.longitude)
                    }
                } else null
            }
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
    val navService = remember { NavigationServiceImpl() }
    val scope = rememberCoroutineScope()

    val distText = if (zone.distanceMeters >= 1000) {
        String.format(Locale.US, "%.1f km", zone.distanceMeters / 1000.0)
    } else {
        "${zone.distanceMeters} m"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${zone.name.ifBlank { stringResource(zone.category.nameResId()) }} • $distText",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(
                            R.string.zone_search_score_summary,
                            zone.score,
                            zone.confidence,
                            stringResource(zone.rating.labelResId()),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AssistChip(
                    onClick = onToggleExpanded,
                    label = { Text(if (expanded) "Less" else "Details") },
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = stringResource(R.string.zone_search_osm_reference, zone.osmReference),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (zone.factors.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        zone.factors.forEach { factor ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(factor.type.labelResId()),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                val delta = factor.impact.roundToInt()
                                Text(
                                    text = "${if (delta >= 0) "+" else ""}$delta",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val radius = zone.recommendedMicroRadiusMeters.coerceAtLeast(navSettings.minRadiusMeters)
                        Log.d("ParkSpotter", "ZoneSearchScreen: 'Parking Search' clicked on card '${zone.name}' -> target lat=${zone.targetCoordinate.latitude}, lon=${zone.targetCoordinate.longitude}, radius=$radius")
                        onSearchParking(
                            zone.targetCoordinate.latitude,
                            zone.targetCoordinate.longitude,
                            radius,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.zone_search_parking_search))
                }

                FilledTonalButton(
                    onClick = {
                        navService.navigateDirect(
                            context = context,
                            destLat = zone.targetCoordinate.latitude,
                            destLon = zone.targetCoordinate.longitude,
                            destName = zone.name,
                            targetPackageName = navSettings.preferredNavAppPackage,
                        )
                    },
                ) {
                    Text(stringResource(R.string.zone_search_navigate))
                }

                val copiedMsg = stringResource(R.string.feedback_coords_copied)
                IconButton(
                    onClick = {
                        navService.copyCoordinatesToClipboard(
                            context,
                            zone.targetCoordinate.latitude,
                            zone.targetCoordinate.longitude,
                        )
                        scope.launch {
                            snackbarHostState.showSnackbar(copiedMsg)
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.PinDrop,
                        contentDescription = stringResource(R.string.zone_search_copy_destination),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun ZoneCategory.nameResId(): Int = when (this) {
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
