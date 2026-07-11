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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.masterofpuppets.parkspotter.ui.theme.ParkSpotterTheme
import com.masterofpuppets.parkspotter.ui.home.HomeMapScreen
import com.masterofpuppets.parkspotter.ui.search.SearchScreen
import com.masterofpuppets.parkspotter.ui.search.SearchResultsMapScreen
import com.masterofpuppets.parkspotter.ui.search.SearchSessionState
import com.masterofpuppets.parkspotter.ui.search.SearchUiSettings
import com.masterofpuppets.parkspotter.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private const val LEGAL_PREFS_NAME = "legal_notice_prefs"
private const val LEGAL_LAST_SHOWN_KEY = "legal_last_shown_at"
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
        val now = System.currentTimeMillis()
        val shouldShowLegal = lastShownAt == 0L || now - lastShownAt >= LEGAL_SHOW_INTERVAL_MS
        val initialSearchSettings = SearchUiSettings(
            resultsPageSize = appSettingsPrefs.getInt(RESULTS_PAGE_SIZE_KEY, 10),
            warnIfResultsAbove = appSettingsPrefs.getInt(WARN_IF_RESULTS_ABOVE_KEY, 150),
            minRadiusMeters = appSettingsPrefs.getInt(MIN_RADIUS_METERS_KEY, 300),
            maxRadiusMeters = appSettingsPrefs.getInt(MAX_RADIUS_METERS_KEY, 3000),
        ).normalized()

        setContent {
            ParkSpotterTheme {
                ParkSpotterApp(
                    showLegalOnStart = shouldShowLegal,
                    initialSearchSettings = initialSearchSettings,
                    onLegalDismissed = {
                        prefs.edit { putLong(LEGAL_LAST_SHOWN_KEY, System.currentTimeMillis()) }
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

@Composable
private fun ParkSpotterApp(
    showLegalOnStart: Boolean,
    initialSearchSettings: SearchUiSettings,
    onLegalDismissed: () -> Unit,
    onSearchSettingsChanged: (SearchUiSettings) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf(AppScreen.Home) }
    val currentScreen = backStack.last()
    var showLegalDialog by remember(showLegalOnStart) { mutableStateOf(showLegalOnStart) }
    var searchSettings by remember(initialSearchSettings) { mutableStateOf(initialSearchSettings) }
    var searchSession by remember { mutableStateOf<SearchSessionState?>(null) }
    var showSearchMap by remember { mutableStateOf(false) }
    var isSearchConfigExpanded by remember { mutableStateOf(true) }

    fun navigateTo(screen: AppScreen) {
        if (backStack.last() != screen) backStack.add(screen)
        if (screen != AppScreen.Search) {
            showSearchMap = false
        }
    }

    // Always consume back: close drawer, pop stack, or do nothing on Home.
    BackHandler(enabled = true) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            currentScreen == AppScreen.Search && showSearchMap -> showSearchMap = false
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
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF8FD9E9)),
            ) {
                val currentSearchSession = searchSession
                when (currentScreen) {
                    AppScreen.Home -> HomeMapScreen(modifier = Modifier.fillMaxSize())
                    AppScreen.Search -> if (showSearchMap && currentSearchSession != null) {
                        SearchResultsMapScreen(
                            modifier = Modifier.fillMaxSize(),
                            session = currentSearchSession,
                            onBack = { showSearchMap = false },
                        )
                    } else {
                        SearchScreen(
                            modifier = Modifier.fillMaxSize(),
                            settings = searchSettings,
                            currentSession = currentSearchSession,
                            isConfigExpanded = isSearchConfigExpanded,
                            onConfigExpandedChanged = { isSearchConfigExpanded = it },
                            onSessionChanged = { 
                                searchSession = it 
                                if (it != null) isSearchConfigExpanded = false
                            },
                            onOpenMap = { if (currentSearchSession != null) showSearchMap = true },
                        )
                    }
                    AppScreen.Preferences -> SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        settings = searchSettings,
                        onSettingsChanged = { updated ->
                            searchSettings = updated.normalized()
                            onSearchSettingsChanged(searchSettings)
                        },
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
            onDismiss = {
                showLegalDialog = false
                onLegalDismissed()
            },
        )
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
private fun LegalNoticeDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = stringResource(R.string.legal_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.legal_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onDismiss) {
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
            initialSearchSettings = SearchUiSettings(),
            onLegalDismissed = {},
            onSearchSettingsChanged = {},
        )
    }
}
