package com.masterofpuppets.parkspotter.ui.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.spike.toApiPlaceType
import com.masterofpuppets.parkspotter.domain.model.PlaceResult
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun SearchResultsMapScreen(
    modifier: Modifier = Modifier,
    originLat: Double,
    originLon: Double,
    resultsWithIndex: List<Pair<Int, PlaceResult>>,
    allResults: List<PlaceResult> = emptyList(),
    routeGeometry: List<Pair<Double, Double>> = emptyList(),
    currentPage: Int,
    totalPages: Int,
    isSingleResultMode: Boolean = false,
    navSettings: SearchUiSettings? = null,
    onSelectResult: (PlaceResult) -> Unit = {},
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navService = androidx.compose.runtime.remember { com.masterofpuppets.parkspotter.domain.service.navigation.NavigationServiceImpl() }
    val activeItemPair = if (isSingleResultMode && resultsWithIndex.isNotEmpty()) resultsWithIndex.first() else null
    val activeResult = activeItemPair?.second
    val activeDisplayIndex = activeItemPair?.first

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                Configuration.getInstance().userAgentValue = context.packageName
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                }
            },
            update = { mapView ->
                mapView.overlays.clear()

                val points = mutableListOf<GeoPoint>()
                val originPoint = GeoPoint(originLat, originLon)
                points.add(originPoint)

                val originMarker = Marker(mapView).apply {
                    id = "origin_marker"
                    position = originPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = mapView.context.getString(R.string.search_origin_marker_title)
                    icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_home_pin_marker_filled)?.mutate()?.apply {
                        setTint(ContextCompat.getColor(mapView.context, R.color.gray_dark))
                    }
                }
                mapView.overlays.add(originMarker)

                resultsWithIndex.forEach { (displayIndex, result) ->
                    val resultPoint = GeoPoint(result.latitude, result.longitude)
                    points.add(resultPoint)
                    val marker = Marker(mapView).apply {
                        id = "${result.osmType}/${result.osmId}"
                        position = resultPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "$displayIndex. ${result.name ?: mapView.context.getString(R.string.result_name_unknown)} [OSM: ${result.osmId}]"
                        icon = createMarkerWithBorder(
                            context = mapView.context,
                            fillColor = ContextCompat.getColor(mapView.context, R.color.secondary_dark),
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

        // Top Pagination and Back bar
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 4.dp
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
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.search_back_to_list),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPreviousPage, enabled = currentPage > 1) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = null)
                    }
                    val label = if (isSingleResultMode) {
                        stringResource(R.string.search_result_index_template, currentPage, totalPages)
                    } else {
                        stringResource(R.string.search_page_index_template, currentPage, totalPages)
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onNextPage, enabled = currentPage < totalPages) {
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        // Floating Action Card for the selected Pin (Bottom of Screen)
        if (activeResult != null) {
            val globalIdx = allResults.indexOf(activeResult)
            val displayNum = if (activeDisplayIndex != null) activeDisplayIndex else (if (globalIdx != -1) globalIdx + 1 else 1)
            val previousStops = if (globalIdx > 0) {
                allResults.take(globalIdx).map { it.latitude to it.longitude }
            } else emptyList()

            androidx.compose.material3.Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#$displayNum. ${activeResult.name ?: stringResource(R.string.result_name_unknown)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onSelectResult(activeResult) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OSM ID: ${activeResult.osmId} • ${stringResource(activeResult.placeType.toApiPlaceType().labelResId())} • ${stringResource(R.string.search_result_distance_template, activeResult.distanceMeters)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )

                        val badgeColor = if (activeResult.isFree) Color(0xFF2D6A4F) else Color(0xFFB3261E)
                        Surface(
                            color = badgeColor.copy(alpha = 0.1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (activeResult.isFree) stringResource(R.string.result_free_badge) else stringResource(R.string.result_paid_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }

                    androidx.compose.material3.HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Button(
                            onClick = {
                                navService.navigateDirect(
                                    context = context,
                                    destLat = activeResult.latitude,
                                    destLon = activeResult.longitude,
                                    destName = activeResult.name,
                                    targetPackageName = navSettings?.preferredNavAppPackage
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.action_navigate_direct))
                        }

                        if (displayNum > 1 && previousStops.isNotEmpty()) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
                                    val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
                                        destLat = activeResult.latitude,
                                        destLon = activeResult.longitude,
                                        destName = activeResult.name,
                                        targetPackageName = navSettings?.preferredNavAppPackage
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.action_navigate_trip))
                            }
                        }

                        androidx.compose.material3.TextButton(
                            onClick = {
                                navService.copyCoordinatesToClipboard(context, activeResult.latitude, activeResult.longitude)
                            }
                        ) {
                            Text(stringResource(R.string.action_copy_coords))
                        }
                    }
                }
            }
        }
    }
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

    // Create a Bitmap to draw the LayerDrawable + Text
    val width = layerDrawable.intrinsicWidth
    val height = layerDrawable.intrinsicHeight
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    layerDrawable.setBounds(0, 0, width, height)
    layerDrawable.draw(canvas)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = (width * 0.35f).coerceAtLeast(24f) // Responsive text size
        isFakeBoldText = true
    }

    // Draw text in the center of the upper circle of the teardrop
    // The teardrop circle is roughly in the top half
    val textBounds = Rect()
    paint.getTextBounds(text, 0, text.length, textBounds)
    val x = width / 2f
    val y = height * 0.32f + (textBounds.height() / 2f)
    
    canvas.drawText(text, x, y, paint)

    return BitmapDrawable(context.resources, bitmap)
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
