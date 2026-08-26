package com.hazemafaneh.babymonitorpro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.DeepLinks
import com.hazemafaneh.babymonitorpro.core.Role
import com.hazemafaneh.babymonitorpro.di.appModule
import com.hazemafaneh.babymonitorpro.store.AppSettings
import com.hazemafaneh.babymonitorpro.ui.Routes
import com.hazemafaneh.babymonitorpro.ui.screens.CameraScreen
import com.hazemafaneh.babymonitorpro.ui.screens.CameraSettingsScreen
import com.hazemafaneh.babymonitorpro.ui.screens.FindCameraScreen
import com.hazemafaneh.babymonitorpro.ui.screens.LiveViewScreen
import com.hazemafaneh.babymonitorpro.ui.screens.RolePickerScreen
import com.hazemafaneh.babymonitorpro.ui.theme.BabyMonitorTheme
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.koinConfiguration

@Composable
fun App() {
    KoinApplication(koinConfiguration { modules(appModule) }) {
        val settings = koinInject<AppSettings>()

        // The *preference*, which merely arms night mode. Whether it is currently in force
        // is [nightActive] below — the two are separate because the screen has to come back
        // to full brightness the moment it is touched.
        var nightPreference by remember { mutableStateOf(settings.nightMode) }
        var nightActive by remember { mutableStateOf(false) }

        // The endpoint being watched is held here rather than encoded into the route:
        // it keeps navigation free of URL escaping for device names.
        var target by remember { mutableStateOf<Watch?>(null) }

        BabyMonitorTheme(darkTheme = true, nightActive = nightActive) {
            val navController = rememberNavController()

            // A tapped alert notification, or a bmpro:// link handed over by the OS. It can
            // arrive before this composition exists — the tap may be what launched the
            // process — so it is parked in DeepLinks and picked up here whenever we come up.
            val pending by DeepLinks.pending.collectAsState()
            LaunchedEffect(pending) {
                val request = pending ?: return@LaunchedEffect
                target = Watch(request.endpoint, request.autoAudio)
                DeepLinks.consume()
                // singleTop, because this is the alarm path and it repeats. Every tapped
                // alert notification arrives here, and without it each tap pushed a second
                // live view on top of the first: two pictures drawn over each other with
                // two status chips, and — the part that does not show up in a screenshot —
                // two MJPEG streams, two control channels and two audio players running at
                // once off one camera. The endpoint lives in [target] rather than the route,
                // so reusing the entry still switches cameras.
                navController.navigate(Routes.LIVE) { launchSingleTop = true }
            }

            NavHost(navController = navController, startDestination = Routes.ROLE) {
                composable(Routes.ROLE) {
                    RolePickerScreen(
                        lastRole = settings.lastRole,
                        onPick = { role ->
                            settings.lastRole = role
                            navController.navigate(
                                if (role == Role.CAMERA) Routes.CAMERA else Routes.FIND
                            )
                        },
                    )
                }

                composable(Routes.CAMERA) {
                    CameraScreen(
                        nightEnabled = nightPreference,
                        onNightActiveChanged = { nightActive = it },
                        onSettings = { navController.navigate(Routes.CAMERA_SETTINGS) },
                        onStop = { navController.popBackStack(Routes.ROLE, inclusive = false) },
                    )
                }

                composable(Routes.CAMERA_SETTINGS) {
                    CameraSettingsScreen(
                        nightEnabled = nightPreference,
                        onNightEnabledChanged = {
                            nightPreference = it
                            settings.nightMode = it
                            // Any interaction returns the screen to full brightness; the
                            // camera screen re-arms the settle timer when it resumes.
                            nightActive = false
                        },
                        onBack = { navController.popBackStack() },
                        onStop = {
                            nightActive = false
                            navController.popBackStack(Routes.ROLE, inclusive = false)
                        },
                    )
                }

                composable(Routes.FIND) {
                    FindCameraScreen(
                        onConnect = { endpoint ->
                            target = Watch(endpoint, autoAudio = false)
                            navController.navigate(Routes.LIVE) { launchSingleTop = true }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.LIVE) {
                    val current = target
                    if (current == null) {
                        // Nothing selected (process restart landed here) — go back.
                        navController.popBackStack()
                    } else {
                        LiveViewScreen(
                            endpoint = current.endpoint,
                            autoAudio = current.autoAudio,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

/** What the live view needs, kept out of the route so names need no escaping. */
private data class Watch(
    val endpoint: CameraEndpoint,
    val autoAudio: Boolean,
)
