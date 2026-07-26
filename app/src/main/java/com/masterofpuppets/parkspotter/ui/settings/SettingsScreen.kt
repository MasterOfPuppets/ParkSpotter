package com.masterofpuppets.parkspotter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.domain.model.VehicleProfile
import com.masterofpuppets.parkspotter.ui.search.SearchUiSettings

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    settings: SearchUiSettings,
    onSettingsChanged: (SearchUiSettings) -> Unit,
    vehicleViewModel: VehicleManageViewModel
) {
    val activeVehicle by vehicleViewModel.activeVehicle.collectAsState()
    val allVehicles by vehicleViewModel.vehicles.collectAsState()

    var showManageDialog by remember { mutableStateOf(false) }
    var showFormDialog by remember { mutableStateOf(false) }
    var showDeleteWarningDialog by remember { mutableStateOf(false) }

    var editingVehicle by remember { mutableStateOf<VehicleProfile?>(null) }
    var vehicleToDelete by remember { mutableStateOf<VehicleProfile?>(null) }

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

        // Vehicle Management Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current Vehicle:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val vehicleName = if (activeVehicle != null) {
                        listOfNotNull(activeVehicle?.make, activeVehicle?.model).joinToString(" ")
                            .takeIf { it.isNotBlank() } ?: "Unnamed Vehicle"
                    } else {
                        "Default (Standard Car)"
                    }
                    Text(
                        text = vehicleName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Button(onClick = { showManageDialog = true }) {
                    Text("Change")
                }
            }
        }

        Spacer(modifier = Modifier.padding(4.dp))

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

    // Dialogs
    if (showManageDialog) {
        VehicleManageDialog(
            vehicles = allVehicles,
            onDismiss = { showManageDialog = false },
            onNewClick = {
                editingVehicle = null
                showFormDialog = true
            },
            onEditClick = { vehicle ->
                editingVehicle = vehicle
                showFormDialog = true
            },
            onDeleteClick = { vehicle ->
                if (vehicle.isActive) {
                    vehicleToDelete = vehicle
                    showDeleteWarningDialog = true
                } else {
                    vehicleViewModel.deleteVehicle(vehicle.id)
                }
            },
            onMakeActiveClick = { vehicle ->
                vehicleViewModel.setActiveVehicle(vehicle.id)
            }
        )
    }

    if (showFormDialog) {
        VehicleFormDialog(
            initialVehicle = editingVehicle,
            onDismiss = { showFormDialog = false },
            onSave = { savedVehicle ->
                vehicleViewModel.saveVehicle(savedVehicle)
                showFormDialog = false
            }
        )
    }

    if (showDeleteWarningDialog) {
        DeleteWarningDialog(
            onDismiss = { 
                showDeleteWarningDialog = false
                vehicleToDelete = null
            },
            onConfirm = {
                vehicleToDelete?.let { vehicleViewModel.deleteVehicle(it.id) }
                showDeleteWarningDialog = false
                vehicleToDelete = null
            }
        )
    }
}

