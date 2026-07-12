package com.masterofpuppets.parkspotter.spike

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.masterofpuppets.parkspotter.R
import kotlinx.coroutines.launch

private const val SPIKE_LAT = 38.696728
private const val SPIKE_LON = -9.364814
private const val SPIKE_RADIUS = 1000

@Composable
fun OverpassSpikeScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<SpikeState>(SpikeState.Idle) }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.spike_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.spike_coords, SPIKE_LAT, SPIKE_LON, SPIKE_RADIUS),
            style = MaterialTheme.typography.bodySmall,
        )

        Button(
            onClick = {
                state = SpikeState.Loading
                scope.launch {
                    val result = OverpassClient.queryParkingAreas(SPIKE_LAT, SPIKE_LON, SPIKE_RADIUS)
                    state = result.fold(
                        onSuccess = { SpikeState.Success(it) },
                        onFailure = { SpikeState.Error(it.message ?: "") }
                    )
                }
            },
            enabled = state !is SpikeState.Loading,
        ) {
            Text(text = stringResource(R.string.spike_run))
        }

        when (val s = state) {
            is SpikeState.Idle -> Text(text = stringResource(R.string.spike_idle))
            is SpikeState.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is SpikeState.Error -> Text(
                text = stringResource(
                    R.string.spike_error,
                    if (s.message.isBlank()) stringResource(R.string.error_unknown) else s.message,
                ),
                color = MaterialTheme.colorScheme.error,
            )
            is SpikeState.Success -> {
                Text(text = stringResource(R.string.spike_results_count, s.elements.size))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.elements) { el ->
                        SpikeResultCard(el)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpikeResultCard(element: OverpassElement) {
    val context = LocalContext.current
    val coords = String.format(java.util.Locale.US, "%.6f, %.6f", element.latitude, element.longitude)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("coords", coords))
                        Toast.makeText(
                            context,
                            context.getString(R.string.coords_copied),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = element.displayName ?: stringResource(R.string.result_name_unknown), fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(element.placeType.labelResId()),
                style = MaterialTheme.typography.labelMedium,
            )
            HorizontalDivider()
            Text(
                text = coords,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun ApiPlaceType.labelResId(): Int = when (this) {
    ApiPlaceType.PARKING -> R.string.place_type_parking
    ApiPlaceType.STREET -> R.string.place_type_street
    ApiPlaceType.PARK -> R.string.place_type_park
    ApiPlaceType.CAMP_SITE -> R.string.place_type_camp_site
    ApiPlaceType.UNKNOWN -> R.string.place_type_unknown
}

sealed interface SpikeState {
    data object Idle : SpikeState
    data object Loading : SpikeState
    data class Success(val elements: List<OverpassElement>) : SpikeState
    data class Error(val message: String) : SpikeState
}
