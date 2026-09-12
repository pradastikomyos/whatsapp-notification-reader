package com.ridenotify.app.wa_reader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ridenotify.app.wa_reader.R
import com.ridenotify.app.wa_reader.di.AppContainer
import com.ridenotify.app.wa_reader.data.settings.ThemeMode
import com.ridenotify.app.wa_reader.ui.home.HomeRoute
import com.ridenotify.app.wa_reader.ui.riding.RidingModeRoute
import com.ridenotify.app.wa_reader.ui.settings.ReaderSettingsRoute
import com.ridenotify.app.wa_reader.ui.status.OnboardingStatus
import com.ridenotify.app.wa_reader.ui.status.StatusScreen
import com.ridenotify.app.wa_reader.ui.theme.RideNotifyTheme
import kotlinx.coroutines.launch

internal enum class AppDestination(@param:StringRes val titleRes: Int, val icon: ImageVector) {
    STATUS(R.string.navigation_status, Icons.Filled.Notifications),
    CONTROL(R.string.navigation_home, Icons.Filled.PlayArrow),
    RIDING(R.string.navigation_riding, Icons.Filled.LocationOn),
    SETTINGS(R.string.navigation_settings, Icons.Filled.Settings),
}

@Composable
fun RideNotifyApp(
    container: AppContainer,
    notificationAccessGranted: Boolean,
    onOpenNotificationAccess: () -> Unit,
) {
    val settings by container.settingsRepository.observeSettings().collectAsState(initial = null)
    val listenerConnection by container.listenerConnectionTracker.state.collectAsState()
    val ttsState by container.ttsEngine.state.collectAsState()
    val themeMode by container.themeSettingsRepository.observeThemeMode()
        .collectAsState(initial = ThemeMode.SYSTEM)
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    val status = OnboardingStatus.from(notificationAccessGranted, listenerConnection, settings, ttsState)

    RideNotifyTheme(themeMode = themeMode) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var destinationName by rememberSaveable { mutableStateOf(AppDestination.STATUS.name) }
            val destination = AppDestination.entries.firstOrNull { it.name == destinationName }
                ?: AppDestination.STATUS
            BoxWithConstraints {
                val wideLayout = maxWidth >= 600.dp
                if (wideLayout) {
                    Row(Modifier.fillMaxSize()) {
                        DestinationRail(destination) { destinationName = it.name }
                        DestinationScaffold(
                            destination = destination,
                            wideLayout = true,
                            container = container,
                            status = status,
                            onOpenNotificationAccess = onOpenNotificationAccess,
                            darkTheme = darkTheme,
                            onToggleTheme = {
                                scope.launch {
                                    container.themeSettingsRepository.setThemeMode(nextThemeMode(darkTheme))
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    DestinationScaffold(
                        destination = destination,
                        wideLayout = false,
                        container = container,
                        status = status,
                        onOpenNotificationAccess = onOpenNotificationAccess,
                        darkTheme = darkTheme,
                        onToggleTheme = {
                            scope.launch {
                                container.themeSettingsRepository.setThemeMode(nextThemeMode(darkTheme))
                            }
                        },
                        bottomBar = { DestinationBar(destination) { destinationName = it.name } },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DestinationScaffold(
    destination: AppDestination,
    wideLayout: Boolean,
    container: AppContainer,
    status: OnboardingStatus,
    onOpenNotificationAccess: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(destination.titleRes)) },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = stringResource(
                                if (darkTheme) R.string.theme_use_light else R.string.theme_use_dark,
                            ),
                        )
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { contentPadding ->
        val screenModifier = Modifier.fillMaxSize().padding(contentPadding)
        when (destination) {
            AppDestination.STATUS -> StatusScreen(
                status = status,
                onOpenNotificationAccess = onOpenNotificationAccess,
                wideLayout = wideLayout,
                modifier = screenModifier,
            )
            AppDestination.CONTROL -> HomeRoute(
                settingsRepository = container.settingsRepository,
                ttsEngine = container.ttsEngine,
                speechCoordinator = container.speechCoordinator,
                wideLayout = wideLayout,
                modifier = screenModifier,
            )
            AppDestination.RIDING -> RidingModeRoute(
                settingsRepository = container.settingsRepository,
                wideLayout = wideLayout,
                modifier = screenModifier,
            )
            AppDestination.SETTINGS -> ReaderSettingsRoute(
                settingsRepository = container.settingsRepository,
                wideLayout = wideLayout,
                modifier = screenModifier,
            )
        }
    }
}

internal fun nextThemeMode(currentlyDark: Boolean): ThemeMode =
    if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun DestinationBar(selected: AppDestination, onSelected: (AppDestination) -> Unit) {
    NavigationBar {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelected(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.titleRes)) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun DestinationRail(selected: AppDestination, onSelected: (AppDestination) -> Unit) {
    NavigationRail {
        AppDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = destination == selected,
                onClick = { onSelected(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.titleRes)) },
                alwaysShowLabel = true,
            )
        }
    }
}
