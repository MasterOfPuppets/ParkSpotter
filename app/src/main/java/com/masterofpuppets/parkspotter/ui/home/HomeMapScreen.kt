package com.masterofpuppets.parkspotter.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.ui.search.MapLocationPickerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun HomeMapScreen(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    onOpenDrawer: () -> Unit = {},
    onNavigateToParking: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
    onNavigateToZone: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homeViewModel: HomeViewModel = viewModel()
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var didCenterOnUser by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var showMapPicker by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = hasLocationPermission(context)
    }

    val permissionMsg = stringResource(R.string.home_location_permission_required)
    val unavailableMsg = stringResource(R.string.home_location_unavailable)
    val currentLocationTitle = stringResource(R.string.home_current_location_title)
    val refreshLocationDesc = stringResource(R.string.home_refresh_location)
    val zoomInDesc = stringResource(R.string.home_zoom_in)
    val zoomOutDesc = stringResource(R.string.home_zoom_out)
    val searchSpotsLabel = stringResource(R.string.home_action_search_spots)
    val exploreZonesLabel = stringResource(R.string.home_action_explore_zones)

    fun zoomMapAtCenter(zoomIn: Boolean) {
        val mapView = mapViewRef ?: return
        val fixedCenter = GeoPoint(mapView.mapCenter.latitude, mapView.mapCenter.longitude)
        if (zoomIn) {
            mapView.controller.zoomIn()
        } else {
            mapView.controller.zoomOut()
        }
        mapView.controller.setCenter(fixedCenter)
    }

    fun refreshCurrentLocation() {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            scope.launch { snackbarHostState.showSnackbar(permissionMsg) }
            return
        }
        if (homeViewModel.isLocating) return
        scope.launch {
            homeViewModel.updateLocating(true)
            val location = fetchCurrentLocationWithRetry(context)
            homeViewModel.updateCurrentLocation(location)
            homeViewModel.updateLocating(false)
            homeViewModel.markLocationFetchAttempted()
            if (location != null) {
                Log.d("ParkSpotter", "HomeMapScreen: refreshCurrentLocation fetched lat=${location.latitude}, lon=${location.longitude}")
                didCenterOnUser = false
                val point = GeoPoint(location.latitude, location.longitude)
                mapViewRef?.controller?.animateTo(point)
            } else {
                snackbarHostState.showSnackbar(unavailableMsg)
            }
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            if (!homeViewModel.hasRequestedLocationPermission) {
                homeViewModel.markPermissionRequestTriggered()
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
                snackbarHostState.showSnackbar(permissionMsg)
            }
        } else if (!homeViewModel.hasAttemptedLocationFetch && !homeViewModel.isLocating) {
            homeViewModel.updateLocating(true)
            val location = fetchCurrentLocationWithRetry(context)
            homeViewModel.updateCurrentLocation(location)
            homeViewModel.updateLocating(false)
            homeViewModel.markLocationFetchAttempted()
            if (location != null) {
                Log.d("ParkSpotter", "HomeMapScreen: Initial GPS location set to lat=${location.latitude}, lon=${location.longitude}")
            } else {
                snackbarHostState.showSnackbar(unavailableMsg)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().userAgentValue = ctx.packageName
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(15.0)
                    val initialPoint = homeViewModel.currentLocation?.let { GeoPoint(it.latitude, it.longitude) }
                        ?: GeoPoint(38.696728, -9.364814)
                    controller.setCenter(initialPoint)
                }
            },
            update = { mapView ->
                mapViewRef = mapView
                val location = homeViewModel.currentLocation
                if (location != null) {
                    val point = GeoPoint(location.latitude, location.longitude)
                    if (!didCenterOnUser) {
                        mapView.controller.animateTo(point)
                        didCenterOnUser = true
                    }

                    val existingMarker = mapView.overlays
                        .filterIsInstance<Marker>()
                        .firstOrNull { it.id == "current_location" }

                    if (existingMarker == null) {
                        val marker = Marker(mapView).apply {
                            id = "current_location"
                            position = point
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = currentLocationTitle
                            icon = ContextCompat.getDrawable(context, R.drawable.ic_my_location_marker)?.mutate()?.apply {
                                setTint(ContextCompat.getColor(context, R.color.primary_dark))
                            }
                        }
                        mapView.overlays.add(marker)
                    } else {
                        existingMarker.position = point
                        existingMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    mapView.invalidate()
                }
            },
        )

        FloatingActionButton(
            onClick = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 16.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = stringResource(R.string.content_desc_open_menu),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 170.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
            ) {
                IconButton(onClick = ::refreshCurrentLocation, enabled = !homeViewModel.isLocating) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = refreshLocationDesc,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
            ) {
                IconButton(onClick = { showMapPicker = true }) {
                    Icon(
                        imageVector = Icons.Default.PinDrop,
                        contentDescription = stringResource(R.string.search_btn_choose_map),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
            ) {
                Column {
                    IconButton(onClick = { zoomMapAtCenter(zoomIn = true) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = zoomInDesc,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { zoomMapAtCenter(zoomIn = false) }) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = zoomOutDesc,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val loc = homeViewModel.currentLocation ?: getBestLastKnownLocation(context)
                        val lat = loc?.latitude ?: mapViewRef?.mapCenter?.latitude ?: 38.696728
                        val lon = loc?.longitude ?: mapViewRef?.mapCenter?.longitude ?: -9.364814
                        Log.d("ParkSpotter", "HomeMapScreen: 'Search spots here' tapped with lat=$lat, lon=$lon (currentLocation=${homeViewModel.currentLocation})")
                        onNavigateToParking(lat, lon)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(imageVector = Icons.Default.LocalParking, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = searchSpotsLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                FilledTonalButton(
                    onClick = {
                        val loc = homeViewModel.currentLocation ?: getBestLastKnownLocation(context)
                        val lat = loc?.latitude ?: mapViewRef?.mapCenter?.latitude ?: 38.696728
                        val lon = loc?.longitude ?: mapViewRef?.mapCenter?.longitude ?: -9.364814
                        Log.d("ParkSpotter", "HomeMapScreen: 'Plan / explore zones' tapped with lat=$lat, lon=$lon (currentLocation=${homeViewModel.currentLocation})")
                        onNavigateToZone(lat, lon)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(imageVector = Icons.Default.Explore, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = exploreZonesLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (showMapPicker) {
            val initialPoint = homeViewModel.currentLocation?.let { GeoPoint(it.latitude, it.longitude) }
                ?: mapViewRef?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) }
                ?: GeoPoint(38.696728, -9.364814)

            MapLocationPickerDialog(
                initialLat = initialPoint.latitude,
                initialLon = initialPoint.longitude,
                onDismiss = { showMapPicker = false },
                onConfirm = { lat, lon ->
                    showMapPicker = false
                    Log.d("ParkSpotter", "HomeMapScreen: Location picked via MapLocationPickerDialog -> lat=$lat, lon=$lon")
                    val newLoc = Location(LocationManager.GPS_PROVIDER).apply {
                        latitude = lat
                        longitude = lon
                        time = System.currentTimeMillis()
                    }
                    homeViewModel.updateCurrentLocation(newLoc)
                    val newPoint = GeoPoint(lat, lon)
                    mapViewRef?.controller?.animateTo(newPoint)
                },
                onGetCurrentLocation = {
                    homeViewModel.currentLocation?.let { GeoPoint(it.latitude, it.longitude) }
                        ?: getBestLastKnownLocation(context)?.let { GeoPoint(it.latitude, it.longitude) }
                }
            )
        }

        if (homeViewModel.isLocating) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
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
    if (!hasLocationPermission(context)) return null
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null
    val providers = locationManager.getProviders(true)
    return providers
        .mapNotNull { provider ->
            try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
        }
        .maxByOrNull { it.time }
}

private suspend fun fetchCurrentLocationWithRetry(context: Context): Location? {
    repeat(3) {
        val location = requestCurrentLocation(context)
        if (location != null) return location
        delay(1200.milliseconds)
    }
    return getBestLastKnownLocation(context)
}

private suspend fun requestCurrentLocation(context: Context): Location? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null

    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
    ).filter { provider ->
        runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
    }

    if (providers.isEmpty()) return getBestLastKnownLocation(context)

    providers.forEach { provider ->
        val location = requestProviderLocation(context, locationManager, provider)
        if (location != null) return location
    }

    return null
}

private suspend fun requestProviderLocation(
    context: Context,
    locationManager: LocationManager,
    provider: String,
): Location? = withTimeoutOrNull(6000.milliseconds) {
    if (!hasLocationPermission(context)) return@withTimeoutOrNull null
    suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }

        try {
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(context),
            ) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        } catch (_: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        }
    }
}