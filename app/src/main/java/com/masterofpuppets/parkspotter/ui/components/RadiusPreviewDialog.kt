package com.masterofpuppets.parkspotter.ui.components

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.masterofpuppets.parkspotter.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.min

@Composable
fun RadiusPreviewDialog(
    origin: GeoPoint,
    radiusMeters: Int,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(340.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.search_radius_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    factory = { context ->
                        Configuration.getInstance().userAgentValue = context.packageName
                        MapView(context).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(false)
                            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
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
                            this.points = Polygon.pointsAsCircle(origin, radiusMeters.toDouble())
                            fillPaint.color = Color.argb(40, 58, 74, 92)
                            outlinePaint.color = Color.argb(220, 58, 74, 92)
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
                            )
                        )
                        mapView.invalidate()
                    },
                )
                Text(
                    text = stringResource(R.string.search_radius_preview_distance, radiusMeters),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun calculateRadiusPreviewZoom(
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
    return adjusted.coerceIn(7.0, 19.0)
}
