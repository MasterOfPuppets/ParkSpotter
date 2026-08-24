package com.masterofpuppets.parkspotter.ui.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.domain.model.PlaceResult
import com.masterofpuppets.parkspotter.domain.service.SearchService
import kotlinx.coroutines.launch

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
    val contexts: Set<SearchContext>,
    val sortMode: SearchSortMode,
    val selectedTypes: Set<String>,
)

class SearchViewModel(
    private val searchService: SearchService
) : ViewModel() {

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

    fun applyLocalFilter(normalizedSettings: SearchUiSettings) {
        val current = searchSession ?: return
        val filtered = searchService.applyLocalFilter(
            rawResults = current.rawResults,
            selectedTypes = searchFormState.selectedTypes,
            sortMode = searchFormState.sortMode,
            contexts = searchFormState.selectedContexts,
            rawOverpassElements = current.rawOverpassElements
        )
        searchSession = current.copy(
            filteredResults = filtered,
            selectedContexts = searchFormState.selectedContexts,
            shouldShowTooManyResultsWarning = filtered.size > normalizedSettings.warnIfResultsAbove,
        )
        searchPageIndex = 0
        isSearchConfigExpanded = false
        uiState = SearchUiState.Success
    }

    fun executeSearch(
        context: Context,
        radius: Int,
        coords: Pair<Double, Double>,
        normalizedSettings: SearchUiSettings
    ) {
        val requestParams = SearchRequestParams(
            originLat = coords.first,
            originLon = coords.second,
            radiusMeters = radius,
            contexts = searchFormState.selectedContexts,
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
            val result = searchService.performSearch(
                lat = coords.first,
                lon = coords.second,
                radiusMeters = radius,
                selectedTypes = searchFormState.selectedTypes,
                sortMode = searchFormState.sortMode,
                contexts = searchFormState.selectedContexts
            )

            uiState = result.fold(
                onSuccess = { execResult ->
                    searchSession = SearchSessionState(
                        originLat = coords.first,
                        originLon = coords.second,
                        radiusMeters = radius,
                        rawResults = execResult.rawResults,
                        filteredResults = execResult.filteredResults,
                        selectedContexts = searchFormState.selectedContexts,
                        shouldShowTooManyResultsWarning = execResult.filteredResults.size > normalizedSettings.warnIfResultsAbove,
                        rawOverpassElements = execResult.rawElements,
                        routeGeometry = execResult.routeGeometry,
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

    companion object {
        fun provideFactory(searchService: SearchService): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                        return SearchViewModel(searchService) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
    }
}
