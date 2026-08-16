package com.hazemafaneh.babymonitorpro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.Role
import com.hazemafaneh.babymonitorpro.di.appModule
import com.hazemafaneh.babymonitorpro.store.AppSettings
import com.hazemafaneh.babymonitorpro.ui.Routes
import com.hazemafaneh.babymonitorpro.ui.screens.CameraScreen
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
        var nightDim by remember { mutableStateOf(settings.nightMode) }

        // The endpoint being watched is held here rather than encoded into the route:
        // it keeps navigation free of URL escaping for device names and PINs.
        var target by remember { mutableStateOf<Pair<CameraEndpoint, String?>?>(null) }

        BabyMonitorTheme(darkTheme = true, nightDim = nightDim) {
            val navController = rememberNavController()

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
                        onNightDimChanged = {
                            nightDim = it
                            settings.nightMode = it
                        },
                        onStop = { navController.popBackStack() },
                    )
                }

                composable(Routes.FIND) {
                    FindCameraScreen(
                        onConnect = { endpoint, pin ->
                            target = endpoint to pin
                            navController.navigate(Routes.LIVE)
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
                            endpoint = current.first,
                            pin = current.second,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
