package com.masterofpuppets.parkspotter.ui.zone

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.masterofpuppets.parkspotter.domain.model.ZoneRecommendation
import com.masterofpuppets.parkspotter.domain.service.zone.ZoneClassificationConfig
import com.masterofpuppets.parkspotter.domain.service.zone.ZoneClassificationService
import com.masterofpuppets.parkspotter.R
import kotlinx.coroutines.launch

sealed interface ZoneSearchUiState {
    data object Idle : ZoneSearchUiState
    data object Loading : ZoneSearchUiState
    data class Success(val sourceElementCount: Int) : ZoneSearchUiState
    data class Error(val message: String) : ZoneSearchUiState
}

class ZoneSearchViewModel(
    private val service: ZoneClassificationService,
) : ViewModel() {

    var recommendations by mutableStateOf<List<ZoneRecommendation>>(emptyList())
        private set

    var routeAlternatives by mutableStateOf<List<ZoneRecommendation>>(emptyList())
        private set

    var uiState by mutableStateOf<ZoneSearchUiState>(ZoneSearchUiState.Idle)
        private set

    fun search(
        context: Context,
        latitude: Double,
        longitude: Double,
        originLatitude: Double?,
        originLongitude: Double?,
        config: ZoneClassificationConfig = ZoneClassificationConfig(),
    ) {
        uiState = ZoneSearchUiState.Loading
        recommendations = emptyList()
        routeAlternatives = emptyList()
        viewModelScope.launch {
            service.classifyZones(
                latitude = latitude,
                longitude = longitude,
                originLatitude = originLatitude,
                originLongitude = originLongitude,
                config = config,
            ).fold(
                onSuccess = { result ->
                    recommendations = result.recommendations
                    routeAlternatives = result.routeAlternatives
                    uiState = ZoneSearchUiState.Success(result.sourceElementCount)
                },
                onFailure = { error ->
                    val code = error.message?.substringAfter("HTTP_") ?: ""
                    val displayError = if (code.isNotBlank() && code.all { it.isDigit() }) {
                        context.getString(R.string.search_error_api_failed, code)
                    } else {
                        context.getString(R.string.error_unknown)
                    }
                    uiState = ZoneSearchUiState.Error(
                        displayError,
                    )
                },
            )
        }
    }

    fun consumeError() {
        if (uiState is ZoneSearchUiState.Error) {
            uiState = ZoneSearchUiState.Idle
        }
    }

    companion object {
        fun provideFactory(service: ZoneClassificationService): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ZoneSearchViewModel::class.java)) {
                        return ZoneSearchViewModel(service) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
    }
}
