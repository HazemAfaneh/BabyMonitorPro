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
import com.hazemafaneh.babymonitorpro.ui.screens.FindCameraScreen
import com.hazemafaneh.babymonitorpro.ui.screens.HomeScreen
import com.hazemafaneh.babymonitorpro.ui.screens.LiveViewScreen
import com.hazemafaneh.babymonitorpro.ui.screens.SettingsScreen
import com.hazemafaneh.babymonitorpro.ui.theme.BabyMonitorTheme
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.koinConfiguration

@Composable
fun App() {
    KoinApplication(koinConfiguration { modules(appModule) }) {
        val settings = koinInject<AppSettings>()

        // Night, as one boolean the parent sets with the moon button and nothing else sets
        // for them. It used to be two — a preference that armed it, and a settle timer that
        // decided it was actually night six seconds after the last touch — and that pair was
        // wrong in both directions: it dimmed the screen while a parent was still reading
        // the pairing address, and it brightened the whole room the instant a sleeve
        // brushed the phone.
        //
        // Held here rather than in the broadcast state on purpose. The nursery phone is in a
        // dark room and the kitchen tablet is not; sharing this flag would let whichever
        // device was touched last decide for both.
        var night by remember { mutableStateOf(settings.nightMode) }
        val setNight: (Boolean) -> Unit = { on ->
            night = on
            settings.nightMode = on
        }

        // The endpoint being watched is held here rather than encoded into the route:
        // it keeps navigation free of URL escaping for device names.
        var target by remember { mutableStateOf<Watch?>(null) }

        BabyMonitorTheme(night = night) {
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
                    HomeScreen(
                        lastRole = settings.lastRole,
                        night = night,
                        onNightChanged = setNight,
                        onPick = { role ->
                            settings.lastRole = role
                            navController.navigate(
                                if (role == Role.CAMERA) Routes.CAMERA else Routes.FIND
                            )
                        },
                        // Already home: stopping the broadcast from the Settings tab has
                        // nowhere to navigate to, and popping the only entry would leave an
                        // empty back stack.
                        onStop = {},
                    )
                }

                composable(Routes.CAMERA) {
                    CameraScreen(
                        night = night,
                        onNightChanged = setNight,
                        onSettings = { navController.navigate(Routes.CAMERA_SETTINGS) },
                        onStop = { navController.popBackStack(Routes.ROLE, inclusive = false) },
                    )
                }

                composable(Routes.CAMERA_SETTINGS) {
                    SettingsScreen(
                        night = night,
                        onNightChanged = setNight,
                        onBack = { navController.popBackStack() },
                        onStop = {
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
                            night = night,
                            onNightChanged = setNight,
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
