package com.masterofpuppets.parkspotter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.masterofpuppets.parkspotter.ui.theme.ParkSpotterTheme
import com.masterofpuppets.parkspotter.ui.home.HomeMapScreen
import com.masterofpuppets.parkspotter.ui.search.SearchScreen
import com.masterofpuppets.parkspotter.ui.search.SearchResultsMapScreen
import com.masterofpuppets.parkspotter.ui.search.SearchUiSettings
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masterofpuppets.parkspotter.ui.search.SearchViewModel
import com.masterofpuppets.parkspotter.ui.settings.SettingsScreen
import com.masterofpuppets.parkspotter.ui.settings.VehicleManageViewModel
import com.masterofpuppets.parkspotter.ui.about.AboutScreen
import com.masterofpuppets.parkspotter.domain.model.PlaceResult
import kotlinx.coroutines.launch

private const val LEGAL_PREFS_NAME = "legal_notice_prefs"
private const val LEGAL_LAST_SHOWN_KEY = "legal_last_shown_at"
private const val LEGAL_DONT_SHOW_AGAIN_KEY = "legal_dont_show_again"
private const val LEGAL_SHOW_INTERVAL_MS = 24L * 60L * 60L * 1000L
private const val APP_SETTINGS_PREFS_NAME = "app_settings_prefs"
private const val RESULTS_PAGE_SIZE_KEY = "results_page_size"
private const val WARN_IF_RESULTS_ABOVE_KEY = "warn_if_results_above"
private const val MIN_RADIUS_METERS_KEY = "min_radius_meters"
private const val MAX_RADIUS_METERS_KEY = "max_radius_meters"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences(LEGAL_PREFS_NAME, MODE_PRIVATE)
        val appSettingsPrefs = getSharedPreferences(APP_SETTINGS_PREFS_NAME, MODE_PRIVATE)
        val lastShownAt = prefs.getLong(LEGAL_LAST_SHOWN_KEY, 0L)
        val dontShowAgain = prefs.getBoolean(LEGAL_DONT_SHOW_AGAIN_KEY, false)
        val now = System.currentTimeMillis()
        val isIntervalPassed = lastShownAt == 0L || now - lastShownAt >= LEGAL_SHOW_INTERVAL_MS
        
        val shouldShowLegalDialog = isIntervalPassed && !dontShowAgain
        val shouldShowLegalSnackbar = isIntervalPassed && dontShowAgain

        if (isIntervalPassed && dontShowAgain) {
            prefs.edit { putLong(LEGAL_LAST_SHOWN_KEY, now) }
        }

        val initialSearchSettings = SearchUiSettings(
            resultsPageSize = appSettingsPrefs.getInt(RESULTS_PAGE_SIZE_KEY, 10),
            warnIfResultsAbove = appSettingsPrefs.getInt(WARN_IF_RESULTS_ABOVE_KEY, 150),
            minRadiusMeters = appSettingsPrefs.getInt(MIN_RADIUS_METERS_KEY, 200),
            maxRadiusMeters = appSettingsPrefs.getInt(MAX_RADIUS_METERS_KEY, 800),
        ).normalized()

        setContent {
            ParkSpotterTheme {
                ParkSpotterApp(
                    showLegalOnStart = shouldShowLegalDialog,
                    showLegalSnackbarOnStart = shouldShowLegalSnackbar,
                    initialSearchSettings = initialSearchSettings,
                    onLegalDismissed = { checkedDontShowAgain ->
                        prefs.edit { 
                            putLong(LEGAL_LAST_SHOWN_KEY, System.currentTimeMillis())
                            putBoolean(LEGAL_DONT_SHOW_AGAIN_KEY, checkedDontShowAgain)
                        }
                    },
                    onSearchSettingsChanged = { updated ->
                        appSettingsPrefs.edit {
                            putInt(RESULTS_PAGE_SIZE_KEY, updated.resultsPageSize)
                            putInt(WARN_IF_RESULTS_ABOVE_KEY, updated.warnIfResultsAbove)
                            putInt(MIN_RADIUS_METERS_KEY, updated.minRadiusMeters)
                            putInt(MAX_RADIUS_METERS_KEY, updated.maxRadiusMeters)
                        }
                    },
                )
            }
        }
    }
}

private enum class AppScreen(val titleRes: Int) {
    Home(R.string.screen_home),
    Search(R.string.screen_search),
    Preferences(R.string.screen_preferences),
    History(R.string.screen_history),
    About(R.string.screen_about),
}

private val appScreenBackStackSaver = listSaver(
    save = { stack: List<AppScreen> -> stack.map { it.name } },
    restore = { names ->
        val restored = names.mapNotNull { name -> AppScreen.entries.firstOrNull { it.name == name } }
        mutableStateListOf<AppScreen>().apply {
            addAll(if (restored.isEmpty()) listOf(AppScreen.Home) else restored)
        }
    },
)

private val searchUiSettingsSaver = listSaver(
    save = { settings: SearchUiSettings ->
        listOf(
            settings.resultsPageSize,
            settings.warnIfResultsAbove,
            settings.minRadiusMeters,
            settings.maxRadiusMeters,
        )
    },
    restore = { values ->
        if (values.size != 4) {
            null
        } else {
            SearchUiSettings(
                resultsPageSize = values[0],
                warnIfResultsAbove = values[1],
                minRadiusMeters = values[2],
                maxRadiusMeters = values[3],
            ).normalized()
        }
    },
)

@Composable
private fun ParkSpotterApp(
    showLegalOnStart: Boolean,
    showLegalSnackbarOnStart: Boolean,
    initialSearchSettings: SearchUiSettings,
    onLegalDismissed: (Boolean) -> Unit,
    onSearchSettingsChanged: (SearchUiSettings) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStack = rememberSaveable(saver = appScreenBackStackSaver) { mutableStateListOf(AppScreen.Home) }
    val currentScreen = backStack.last()
    var showLegalDialog by rememberSaveable { mutableStateOf(showLegalOnStart) }
    var searchSettings by rememberSaveable(stateSaver = searchUiSettingsSaver) { mutableStateOf(initialSearchSettings) }
    
    val searchViewModel: SearchViewModel = viewModel()
    
    val app = LocalContext.current.applicationContext as ParkSpotterApplication
    val vehicleManageViewModel: VehicleManageViewModel = viewModel(
        factory = VehicleManageViewModel.provideFactory(app.vehicleService)
    )
    
    // As search states are now inside ViewModel, we just access them directly when needed
    val currentSearchSession = searchViewModel.searchSession
    val showSearchMap = searchViewModel.showSearchMap
    val selectedResultForMap = searchViewModel.selectedResultForMap
    val isSearchConfigExpanded = searchViewModel.isSearchConfigExpanded
    val searchPageIndex = searchViewModel.searchPageIndex

    fun navigateTo(screen: AppScreen) {
        if (backStack.last() != screen) backStack.add(screen)
        if (screen != AppScreen.Search) {
            searchViewModel.showSearchMap = false
        }
    }

    // Always consume back: close drawer, pop stack, or do nothing on Home.
    BackHandler(enabled = true) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            currentScreen == AppScreen.Search && showSearchMap -> {
                searchViewModel.showSearchMap = false
                searchViewModel.selectedResultForMap = null
            }
            backStack.size > 1 -> backStack.removeAt(backStack.lastIndex)
            else -> Unit
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.menu_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                AppScreen.entries.forEach { screen ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(screen.titleRes)) },
                        selected = currentScreen == screen,
                        onClick = {
                            navigateTo(screen)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF8FD9E9)),
            ) {
                when (currentScreen) {
                    AppScreen.Home -> HomeMapScreen(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHostState = snackbarHostState,
                    )
                    AppScreen.Search -> if (showSearchMap && currentSearchSession != null) {
                        val pageSize = searchSettings.resultsPageSize
                        val singleResult = searchViewModel.selectedResultForMap
                        val allResults = currentSearchSession.allResults.filter { !it.isSanitized }
                        
                        val (mapResults, currentPageDisplay, totalPagesDisplay) = if (singleResult != null) {
                            val globalIndex = allResults.indexOf(singleResult)
                            val displayList: List<Pair<Int, PlaceResult>> = listOf(Pair(globalIndex + 1, singleResult))
                            Triple<List<Pair<Int, PlaceResult>>, Int, Int>(displayList, globalIndex + 1, allResults.size)
                        } else {
                            val totalPages = (allResults.size + pageSize - 1) / pageSize
                            val pagedList: List<Pair<Int, PlaceResult>> = allResults
                                .mapIndexed { index, result -> Pair(index + 1, result) }
                                .drop(searchPageIndex * pageSize)
                                .take(pageSize)
                            Triple<List<Pair<Int, PlaceResult>>, Int, Int>(pagedList, searchPageIndex + 1, totalPages)
                        }

                        SearchResultsMapScreen(
                            modifier = Modifier.fillMaxSize(),
                            originLat = currentSearchSession.originLat,
                            originLon = currentSearchSession.originLon,
                            resultsWithIndex = mapResults,
                            currentPage = currentPageDisplay,
                            totalPages = totalPagesDisplay,
                            isSingleResultMode = singleResult != null,
                            onNextPage = {
                                if (singleResult != null) {
                                    val currentIndex = allResults.indexOf(singleResult)
                                    if (currentIndex < allResults.size - 1) {
                                        val nextResult = allResults[currentIndex + 1]
                                        searchViewModel.selectedResultForMap = nextResult
                                        searchViewModel.searchPageIndex = (currentIndex + 1) / pageSize
                                    }
                                } else {
                                    val totalPages = (allResults.size + pageSize - 1) / pageSize
                                    if (searchPageIndex < totalPages - 1) searchViewModel.searchPageIndex++
                                }
                            },
                            onPreviousPage = {
                                if (singleResult != null) {
                                    val currentIndex = allResults.indexOf(singleResult)
                                    if (currentIndex > 0) {
                                        val prevResult = allResults[currentIndex - 1]
                                        searchViewModel.selectedResultForMap = prevResult
                                        searchViewModel.searchPageIndex = (currentIndex - 1) / pageSize
                                    }
                                } else {
                                    if (searchPageIndex > 0) searchViewModel.searchPageIndex--
                                }
                            },
                            onBack = { 
                                searchViewModel.showSearchMap = false
                                searchViewModel.selectedResultForMap = null
                            },
                        )
                    } else {
                        SearchScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = searchViewModel,
                            settings = searchSettings,
                            onOpenMap = { if (currentSearchSession != null) searchViewModel.showSearchMap = true },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    AppScreen.Preferences -> SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        settings = searchSettings,
                        onSettingsChanged = { updated ->
                            searchSettings = updated.normalized()
                            onSearchSettingsChanged(searchSettings)
                        },
                        vehicleViewModel = vehicleManageViewModel
                    )
                    AppScreen.About -> AboutScreen(
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> ScreenPlaceholder(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        title = stringResource(currentScreen.titleRes),
                        description = stringResource(R.string.placeholder_screen_content),
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.content_desc_open_menu),
                            tint = Color.DarkGray,
                        )
                    }
                }
            }
        }
    }

    if (showLegalDialog) {
        LegalNoticeDialog(
            onDismiss = { dontShowAgain ->
                showLegalDialog = false
                onLegalDismissed(dontShowAgain)
            },
        )
    }

    val reminderMessage = stringResource(R.string.legal_reminder_snackbar)
    LaunchedEffect(showLegalSnackbarOnStart) {
        if (showLegalSnackbarOnStart) {
            snackbarHostState.showSnackbar(message = reminderMessage)
        }
    }
}

@Composable
private fun ScreenPlaceholder(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(text = description, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LegalNoticeDialog(onDismiss: (Boolean) -> Unit) {
    var dontShowAgain by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { onDismiss(dontShowAgain) }) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = stringResource(R.string.legal_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.legal_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowAgain = !dontShowAgain }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Text(text = stringResource(R.string.legal_dont_show_again))
                }
                TextButton(
                    onClick = { onDismiss(dontShowAgain) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(text = stringResource(R.string.action_acknowledge))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ParkSpotterAppPreview() {
    ParkSpotterTheme {
        ParkSpotterApp(
            showLegalOnStart = true,
            showLegalSnackbarOnStart = false,
            initialSearchSettings = SearchUiSettings(),
            onLegalDismissed = {},
            onSearchSettingsChanged = {},
        )
    }
}
