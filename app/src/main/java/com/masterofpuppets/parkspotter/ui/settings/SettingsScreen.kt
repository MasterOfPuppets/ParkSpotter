package com.masterofpuppets.parkspotter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.ui.search.SearchUiSettings

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    settings: SearchUiSettings,
    onSettingsChanged: (SearchUiSettings) -> Unit,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Results per page
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${stringResource(R.string.settings_results_page_size_label)}: ${settings.resultsPageSize}",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = settings.resultsPageSize.toFloat(),
                onValueChange = { value -> 
                    onSettingsChanged(settings.copy(resultsPageSize = Math.round(value)).normalized())
                },
                valueRange = 4f..20f,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }

        // Maximum Radius
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${stringResource(R.string.settings_radius_max_label)}: ${settings.maxRadiusMeters}m",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = settings.maxRadiusMeters.toFloat(),
                onValueChange = { value ->
                    onSettingsChanged(
                        settings.copy(
                            minRadiusMeters = 200,
                            maxRadiusMeters = Math.round(value)
                        ).normalized()
                    )
                },
                valueRange = 200f..2000f,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }

        // Results Threshold
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${stringResource(R.string.settings_warn_threshold_label)}: ${settings.warnIfResultsAbove}",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = settings.warnIfResultsAbove.toFloat(),
                onValueChange = { value ->
                    // Força o arredondamento para o múltiplo de 5 mais próximo
                    val roundedValue = Math.round(value / 5f) * 5
                    onSettingsChanged(settings.copy(warnIfResultsAbove = roundedValue).normalized())
                },
                valueRange = 10f..150f,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }
    }
}
