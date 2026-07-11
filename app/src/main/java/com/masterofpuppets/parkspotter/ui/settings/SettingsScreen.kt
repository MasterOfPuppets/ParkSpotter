package com.masterofpuppets.parkspotter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.ui.search.SearchUiSettings

private val pageSizeOptions = listOf(4, 6, 8, 10, 20)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    settings: SearchUiSettings,
    onSettingsChanged: (SearchUiSettings) -> Unit,
) {
    var warningThresholdText by remember(settings.warnIfResultsAbove) {
        mutableStateOf(settings.warnIfResultsAbove.toString())
    }
    var minRadiusText by remember(settings.minRadiusMeters) {
        mutableStateOf(settings.minRadiusMeters.toString())
    }
    var maxRadiusText by remember(settings.maxRadiusMeters) {
        mutableStateOf(settings.maxRadiusMeters.toString())
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = stringResource(R.string.settings_results_page_size_label),
            style = MaterialTheme.typography.labelLarge,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pageSizeOptions.forEach { option ->
                FilterChip(
                    selected = settings.resultsPageSize == option,
                    onClick = { onSettingsChanged(settings.copy(resultsPageSize = option).normalized()) },
                    label = { Text(option.toString()) },
                )
            }
        }

        Text(
            text = stringResource(R.string.settings_warn_threshold_label),
            style = MaterialTheme.typography.labelLarge,
        )

        OutlinedTextField(
            value = warningThresholdText,
            onValueChange = { updated ->
                warningThresholdText = updated.filter(Char::isDigit)
                val parsed = warningThresholdText.toIntOrNull()
                if (parsed != null && parsed > 0) {
                    onSettingsChanged(settings.copy(warnIfResultsAbove = parsed).normalized())
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_warn_threshold_input_label)) },
        )

        Text(
            text = stringResource(R.string.settings_radius_range_label),
            style = MaterialTheme.typography.labelLarge,
        )

        OutlinedTextField(
            value = minRadiusText,
            onValueChange = { updated ->
                minRadiusText = updated.filter(Char::isDigit)
                val parsed = minRadiusText.toIntOrNull()
                if (parsed != null && parsed > 0) {
                    onSettingsChanged(
                        settings.copy(minRadiusMeters = parsed).normalized(),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_radius_min_label)) },
        )

        OutlinedTextField(
            value = maxRadiusText,
            onValueChange = { updated ->
                maxRadiusText = updated.filter(Char::isDigit)
                val parsed = maxRadiusText.toIntOrNull()
                if (parsed != null && parsed > 0) {
                    onSettingsChanged(
                        settings.copy(maxRadiusMeters = parsed).normalized(),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_radius_max_label)) },
        )
    }
}
