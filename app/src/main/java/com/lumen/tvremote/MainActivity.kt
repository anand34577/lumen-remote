package com.lumen.tvremote

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat
import com.lumen.tvremote.ui.theme.isDarkTheme
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lumen.tvremote.protocol.TvKey
import com.lumen.tvremote.ui.LocalHaptics
import com.lumen.tvremote.ui.LocalLocalNetworkAccess
import com.lumen.tvremote.ui.rememberLocalNetworkAccess
import com.lumen.tvremote.ui.MainViewModel
import com.lumen.tvremote.ui.customize.CustomizeScreen
import com.lumen.tvremote.ui.devices.DevicesScreen
import com.lumen.tvremote.ui.devices.PairingDialog
import com.lumen.tvremote.ui.rememberHaptics
import com.lumen.tvremote.ui.remote.RemoteScreen
import com.lumen.tvremote.ui.settings.SettingsScreen
import com.lumen.tvremote.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.settings.value == null || vm.devices.value == null }
        enableEdgeToEdge()

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val devices by vm.devices.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            val savedDevices = devices ?: return@setContent

            val dark = isDarkTheme(s)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
                onDispose { }
            }

            DisposableEffect(s.keepScreenOn) {
                if (s.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { }
            }

            val networkAccess = rememberLocalNetworkAccess(onGranted = vm.controller::connectToLastIfIdle)
            LaunchedEffect(Unit) { networkAccess.request() }

            LumenTheme(s) {
                CompositionLocalProvider(
                    LocalHaptics provides rememberHaptics(s.haptics),
                    LocalLocalNetworkAccess provides networkAccess,
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        val nav = rememberNavController()
                        // Fixed at first composition: changing it later would reset the back stack.
                        val start = remember { if (savedDevices.isEmpty()) Routes.ONBOARDING else Routes.REMOTE }
                        Box {
                            NavHost(
                                navController = nav,
                                startDestination = start,
                                enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
                                exitTransition = { fadeOut() },
                                popEnterTransition = { fadeIn() },
                                popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
                            ) {
                                composable(Routes.ONBOARDING) {
                                    DevicesScreen(vm, onBack = null, onSelected = { nav.openRemote() })
                                }
                                composable(Routes.REMOTE) {
                                    RemoteScreen(
                                        vm, s,
                                        onOpenDevices = { nav.navigate(Routes.DEVICES) { launchSingleTop = true } },
                                        onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                                        onOpenCustomize = { nav.navigate(Routes.CUSTOMIZE) { launchSingleTop = true } },
                                    )
                                }
                                composable(Routes.DEVICES) {
                                    DevicesScreen(vm, onBack = { nav.popBackStack() }, onSelected = { nav.openRemote() })
                                }
                                composable(Routes.SETTINGS) {
                                    SettingsScreen(
                                        vm, s,
                                        onBack = { nav.popBackStack() },
                                        onOpenCustomize = { nav.navigate(Routes.CUSTOMIZE) { launchSingleTop = true } },
                                    )
                                }
                                composable(Routes.CUSTOMIZE) { CustomizeScreen(vm, onBack = { nav.popBackStack() }) }
                            }
                            PairingDialog(vm, onPaired = { nav.openRemote() })
                        }
                    }
                }
            }
        }
    }

    private fun NavHostController.openRemote() {
        if (!popBackStack(Routes.REMOTE, inclusive = false)) {
            navigate(Routes.REMOTE) {
                popUpTo(Routes.ONBOARDING) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    /** Hardware volume keys control the TV while the remote is open and connected. Holding a key repeats. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val tvKey = volumeKeyForTv(keyCode) ?: return super.onKeyDown(keyCode, event)
        vm.controller.sendKey(tvKey)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        volumeKeyForTv(keyCode) != null || super.onKeyUp(keyCode, event)

    private fun volumeKeyForTv(keyCode: Int): TvKey? {
        val tvKey = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> TvKey.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> TvKey.VOLUME_DOWN
            else -> return null
        }
        val routeToTv = vm.settings.value?.volumeKeysControlTv == true && vm.tvState.value.isConnected
        return tvKey.takeIf { routeToTv }
    }

    private object Routes {
        const val ONBOARDING = "onboarding"
        const val REMOTE = "remote"
        const val DEVICES = "devices"
        const val SETTINGS = "settings"
        const val CUSTOMIZE = "customize"
    }
}
