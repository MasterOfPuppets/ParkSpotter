package com.masterofpuppets.parkspotter.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masterofpuppets.parkspotter.R
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

@Composable
fun HomeMapScreen(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homeViewModel: HomeViewModel = viewModel()
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var didCenterOnUser by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = hasLocationPermission(context)
    }

    val permissionMsg = stringResource(R.string.home_location_permission_required)
    val unavailableMsg = stringResource(R.string.home_location_unavailable)
    val refreshLocationDesc = stringResource(R.string.home_refresh_location)
    val zoomInDesc = stringResource(R.string.home_zoom_in)
    val zoomOutDesc = stringResource(R.string.home_zoom_out)

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
            if (location == null) {
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
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = context.getString(R.string.home_current_location_title)
                            icon = ContextCompat.getDrawable(context, R.drawable.ic_home_pin_marker_filled)?.mutate()?.apply {
                                setTint(ContextCompat.getColor(context, R.color.primary_dark))
                            }
                        }
                        mapView.overlays.add(marker)
                    } else {
                        existingMarker.position = point
                    }
                    mapView.invalidate()
                }
            },
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
            ) {
                IconButton(onClick = { zoomMapAtCenter(zoomIn = true) }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = zoomInDesc,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
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
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
            ) {
                IconButton(onClick = { zoomMapAtCenter(zoomIn = false) }) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = zoomOutDesc,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
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
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null
    val providers = locationManager.getProviders(true)
    return providers
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }
}

private suspend fun fetchCurrentLocationWithRetry(context: Context): Location? {
    repeat(3) {
        val location = requestCurrentLocation(context)
        if (location != null) return location
        delay(1200)
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
): Location? = withTimeoutOrNull(6000) {
    suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }

        runCatching {
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(context),
            ) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }.onFailure {
            if (continuation.isActive) continuation.resume(null)
        }
    }
}
