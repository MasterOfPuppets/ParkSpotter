package com.masterofpuppets.parkspotter.ui.search

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.masterofpuppets.parkspotter.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun SearchResultsMapScreen(
    modifier: Modifier = Modifier,
    session: SearchSessionState,
    onBack: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                Configuration.getInstance().userAgentValue = context.packageName
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(14.5)
                    controller.setCenter(GeoPoint(session.originLat, session.originLon))
                }
            },
            update = { mapView ->
                mapView.overlays.clear()

                val originMarker = Marker(mapView).apply {
                    id = "origin_marker"
                    position = GeoPoint(session.originLat, session.originLon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = mapView.context.getString(R.string.search_origin_marker_title)
                    icon = createTintedMarkerDrawable(
                        context = mapView.context,
                        drawableRes = R.drawable.ic_home_pin_marker,
                        tintColor = ContextCompat.getColor(mapView.context, R.color.primary_dark),
                    )
                }
                mapView.overlays.add(originMarker)

                session.allResults.forEach { result ->
                    val marker = Marker(mapView).apply {
                        id = "${result.osmType}/${result.osmId}"
                        position = GeoPoint(result.latitude, result.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = result.name ?: mapView.context.getString(R.string.result_name_unknown)
                        icon = createTintedMarkerDrawable(
                            context = mapView.context,
                            drawableRes = R.drawable.ic_not_listed_location_marker,
                            tintColor = ContextCompat.getColor(mapView.context, R.color.secondary_dark),
                        )
                    }
                    mapView.overlays.add(marker)
                }

                mapView.invalidate()
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.search_back_to_list),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.search_map_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
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
