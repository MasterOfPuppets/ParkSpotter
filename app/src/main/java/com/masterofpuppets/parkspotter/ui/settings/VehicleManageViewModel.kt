package com.masterofpuppets.parkspotter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.masterofpuppets.parkspotter.domain.model.VehicleProfile
import com.masterofpuppets.parkspotter.domain.service.VehicleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VehicleManageViewModel(
    private val vehicleService: VehicleService
) : ViewModel() {

    private val _vehicles = MutableStateFlow<List<VehicleProfile>>(emptyList())
    val vehicles: StateFlow<List<VehicleProfile>> = _vehicles.asStateFlow()

    private val _activeVehicle = MutableStateFlow<VehicleProfile?>(null)
    val activeVehicle: StateFlow<VehicleProfile?> = _activeVehicle.asStateFlow()

    init {
        refreshVehicles()
    }

    private fun refreshVehicles() {
        viewModelScope.launch {
            val all = vehicleService.getAllVehicles()
            _vehicles.value = all
            _activeVehicle.value = all.find { it.isActive }
        }
    }

    fun saveVehicle(vehicle: VehicleProfile) {
        viewModelScope.launch {
            vehicleService.saveVehicle(vehicle)
            refreshVehicles()
        }
    }

    fun deleteVehicle(vehicleId: String) {
        viewModelScope.launch {
            vehicleService.deleteVehicle(vehicleId)
            refreshVehicles()
        }
    }

    fun setActiveVehicle(vehicleId: String) {
        viewModelScope.launch {
            vehicleService.setActiveVehicle(vehicleId)
            refreshVehicles()
        }
    }

    companion object {
        fun provideFactory(vehicleService: VehicleService): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(VehicleManageViewModel::class.java)) {
                        return VehicleManageViewModel(vehicleService) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
    }
}
