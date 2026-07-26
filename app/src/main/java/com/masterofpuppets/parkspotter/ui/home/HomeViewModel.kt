package com.masterofpuppets.parkspotter.ui.home

import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    var currentLocation by mutableStateOf<Location?>(null)
        private set

    var isLocating by mutableStateOf(false)
        private set

    var hasAttemptedLocationFetch by mutableStateOf(false)
        private set

    var hasRequestedLocationPermission by mutableStateOf(false)
        private set

    fun markPermissionRequestTriggered() {
        hasRequestedLocationPermission = true
    }

    fun updateLocating(value: Boolean) {
        isLocating = value
    }

    fun updateCurrentLocation(location: Location?) {
        currentLocation = location
    }

    fun markLocationFetchAttempted() {
        hasAttemptedLocationFetch = true
    }
}
