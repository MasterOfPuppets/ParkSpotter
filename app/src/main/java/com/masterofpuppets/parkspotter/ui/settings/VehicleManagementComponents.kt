package com.masterofpuppets.parkspotter.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.domain.model.DiscretionLevel
import com.masterofpuppets.parkspotter.domain.model.VehicleCategory
import com.masterofpuppets.parkspotter.domain.model.VehicleProfile
import java.util.UUID

@Composable
fun VehicleManageDialog(
    vehicles: List<VehicleProfile>,
    onDismiss: () -> Unit,
    onNewClick: () -> Unit,
    onEditClick: (VehicleProfile) -> Unit,
    onDeleteClick: (VehicleProfile) -> Unit,
    onMakeActiveClick: (VehicleProfile) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Manage Vehicles",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                if (vehicles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No vehicles found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(vehicles) { vehicle ->
                            VehicleListItem(
                                vehicle = vehicle,
                                onEdit = { onEditClick(vehicle) },
                                onDelete = { onDeleteClick(vehicle) },
                                onMakeActive = { onMakeActiveClick(vehicle) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Button(onClick = onNewClick) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New")
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleListItem(
    vehicle: VehicleProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMakeActive: () -> Unit
) {
    val categoryLabel = getCategoryLabel(vehicle.category)
    val displayTitle = listOfNotNull(vehicle.make, vehicle.model)
        .joinToString(" ")
        .takeIf { it.isNotBlank() } ?: "Unknown Vehicle"
    
    val subtitle = listOfNotNull(
        categoryLabel,
        vehicle.registration,
        vehicle.color
    ).joinToString(" • ")

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (vehicle.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vehicle.isActive) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = displayTitle,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (vehicle.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vehicle.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row {
                    if (!vehicle.isActive) {
                        IconButton(onClick = onMakeActive, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Check, contentDescription = "Make Active", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleFormDialog(
    initialVehicle: VehicleProfile?,
    onDismiss: () -> Unit,
    onSave: (VehicleProfile) -> Unit
) {
    var make by remember { mutableStateOf(initialVehicle?.make ?: "") }
    var model by remember { mutableStateOf(initialVehicle?.model ?: "") }
    var color by remember { mutableStateOf(initialVehicle?.color ?: "") }
    var registration by remember { mutableStateOf(initialVehicle?.registration ?: "") }
    
    var category by remember { mutableStateOf(initialVehicle?.category ?: VehicleCategory.CITY_CAR) }
    var categoryExpanded by remember { mutableStateOf(false) }

    var length by remember { mutableStateOf(initialVehicle?.lengthMeters?.toString() ?: "") }
    var height by remember { mutableStateOf(initialVehicle?.heightMeters?.toString() ?: "") }
    
    var discretion by remember { mutableStateOf(initialVehicle?.discretionLevel ?: DiscretionLevel.MEDIUM) }
    var discretionExpanded by remember { mutableStateOf(false) }

    var isBranded by remember { mutableStateOf(initialVehicle?.isBranded ?: false) }
    val isActive = initialVehicle?.isActive ?: false

    val isSavable = make.isNotBlank() || model.isNotBlank() || color.isNotBlank() || registration.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = if (initialVehicle == null) "New Vehicle" else "Edit Vehicle",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text("Identification (At least one required)", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = make, onValueChange = { make = it },
                            label = { Text("Make (e.g. Ford)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = model, onValueChange = { model = it },
                            label = { Text("Model (e.g. Focus)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = color, onValueChange = { color = it },
                            label = { Text("Color") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = registration, onValueChange = { registration = it },
                            label = { Text("Registration Plate") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }

                    item {
                        Text("Specifications", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = getCategoryLabel(category),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                VehicleCategory.entries.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(getCategoryLabel(cat)) },
                                        onClick = {
                                            category = cat
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = length, onValueChange = { length = it },
                                label = { Text("Length (m)") }, modifier = Modifier.weight(1f), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = height, onValueChange = { height = it },
                                label = { Text("Height (m)") }, modifier = Modifier.weight(1f), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }

                    item {
                        ExposedDropdownMenuBox(
                            expanded = discretionExpanded,
                            onExpandedChange = { discretionExpanded = it },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = getDiscretionLabel(discretion),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Discretion Level") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = discretionExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = discretionExpanded,
                                onDismissRequest = { discretionExpanded = false }
                            ) {
                                DiscretionLevel.entries.forEach { lvl ->
                                    DropdownMenuItem(
                                        text = { Text(getDiscretionLabel(lvl)) },
                                        onClick = {
                                            discretion = lvl
                                            discretionExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Checkbox(checked = isBranded, onCheckedChange = { isBranded = it })
                            Text("Vehicle is branded / commercial", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val newVehicle = VehicleProfile(
                                id = initialVehicle?.id ?: UUID.randomUUID().toString(),
                                make = make.takeIf { it.isNotBlank() },
                                model = model.takeIf { it.isNotBlank() },
                                color = color.takeIf { it.isNotBlank() },
                                registration = registration.takeIf { it.isNotBlank() },
                                category = category,
                                lengthMeters = length.toFloatOrNull(),
                                heightMeters = height.toFloatOrNull(),
                                discretionLevel = discretion,
                                isBranded = isBranded,
                                isActive = isActive
                            )
                            onSave(newVehicle)
                        },
                        enabled = isSavable
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete Active Vehicle") },
        text = { 
            Text(text = stringResource(R.string.vehicle_active_delete_warning))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun getCategoryLabel(category: VehicleCategory): String {
    val resId = when (category) {
        VehicleCategory.CITY_CAR -> R.string.vehicle_category_city_car
        VehicleCategory.SEDAN -> R.string.vehicle_category_sedan
        VehicleCategory.STATION_WAGON -> R.string.vehicle_category_station_wagon
        VehicleCategory.SUV -> R.string.vehicle_category_suv
        VehicleCategory.MINIVAN -> R.string.vehicle_category_minivan
        VehicleCategory.VAN -> R.string.vehicle_category_van
        VehicleCategory.PICKUP_TRUCK -> R.string.vehicle_category_pickup_truck
        VehicleCategory.CAMPERVAN -> R.string.vehicle_category_campervan
        VehicleCategory.MOTORHOME -> R.string.vehicle_category_motorhome
        VehicleCategory.CARAVAN -> R.string.vehicle_category_caravan
    }
    return stringResource(resId)
}

@Composable
fun getDiscretionLabel(level: DiscretionLevel): String {
    val resId = when (level) {
        DiscretionLevel.LOW -> R.string.discretion_level_low
        DiscretionLevel.MEDIUM -> R.string.discretion_level_medium
        DiscretionLevel.HIGH -> R.string.discretion_level_high
    }
    return stringResource(resId)
}
