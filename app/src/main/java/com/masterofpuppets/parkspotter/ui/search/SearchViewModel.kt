package com.masterofpuppets.parkspotter.ui.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.domain.model.PlaceResult
import com.masterofpuppets.parkspotter.spike.OverpassClient
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Success : SearchUiState
    data class Error(val message: String) : SearchUiState
}

data class SearchRequestParams(
    val originLat: Double,
    val originLon: Double,
    val radiusMeters: Int,
    val context: SearchContext,
    val sortMode: SearchSortMode,
    val selectedTypes: Set<String>,
)

class SearchViewModel : ViewModel() {

    var searchSession by mutableStateOf<SearchSessionState?>(null)
    var showSearchMap by mutableStateOf(false)
    var selectedResultForMap by mutableStateOf<PlaceResult?>(null)
    var isSearchConfigExpanded by mutableStateOf(true)
    var searchPageIndex by mutableIntStateOf(0)

    private var _searchFormState: SearchFormState? = null
    
    val searchFormState: SearchFormState
        get() = _searchFormState ?: throw IllegalStateException("SearchFormState not initialized")

    fun initFormState(initialRadius: Int) {
        if (_searchFormState == null) {
            _searchFormState = SearchFormState(initialRadius)
        }
    }

    var uiState by mutableStateOf<SearchUiState>(SearchUiState.Idle)
        private set

    var lastSubmittedParams by mutableStateOf<SearchRequestParams?>(null)
        private set

    fun resetState() {
        uiState = SearchUiState.Idle
    }

    fun setError(message: String) {
        uiState = SearchUiState.Error(message)
    }

    fun executeSearch(
        context: Context,
        radius: Int,
        coords: Pair<Double, Double>,
        normalizedSettings: SearchUiSettings,
        applySanitization: (List<PlaceResult>) -> List<PlaceResult>
    ) {
        val requestParams = SearchRequestParams(
            originLat = coords.first,
            originLon = coords.second,
            radiusMeters = radius,
            context = searchFormState.selectedContext,
            sortMode = searchFormState.sortMode,
            selectedTypes = searchFormState.selectedTypes.map { it.key }.toSet(),
        )

        if (searchSession != null && requestParams == lastSubmittedParams) {
            isSearchConfigExpanded = false
            uiState = SearchUiState.Success
            return
        }

        uiState = SearchUiState.Loading
        searchPageIndex = 0
        searchSession = null
        lastSubmittedParams = requestParams

        viewModelScope.launch {
            val result = OverpassClient.queryParkingAreas(
                lat = coords.first,
                lon = coords.second,
                radiusMeters = radius,
            )

            uiState = result.fold(
                onSuccess = { elements ->
                    val mapped = elements
                        .filter { searchFormState.selectedTypes.contains(it.placeType) }
                        .map { it.toPlaceResult(coords.first, coords.second) }
                        .let { applySanitization(it) }
                        .let { list ->
                            when (searchFormState.sortMode) {
                                SearchSortMode.DISTANCE -> list.sortedBy { it.distanceMeters }
                                SearchSortMode.SCORE -> list.sortedByDescending { it.score ?: 0f }
                            }
                        }

                    searchSession = SearchSessionState(
                        originLat = coords.first,
                        originLon = coords.second,
                        radiusMeters = radius,
                        context = searchFormState.selectedContext,
                        allResults = mapped,
                        shouldShowTooManyResultsWarning = mapped.size > normalizedSettings.warnIfResultsAbove,
                    )
                    
                    isSearchConfigExpanded = false
                    SearchUiState.Success
                },
                onFailure = { error ->
                    val code = error.message?.substringAfter("HTTP_") ?: ""
                    val msg = error.localizedMessage ?: ""
                    val displayError = if (code.isNotBlank() && code.all { it.isDigit() }) {
                        context.getString(R.string.search_error_api_failed, code)
                    } else {
                        msg.takeIf { it.isNotBlank() } ?: context.getString(R.string.error_unknown)
                    }
                    SearchUiState.Error(displayError)
                }
            )
        }
    }
}
