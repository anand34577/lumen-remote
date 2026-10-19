package com.lumen.tvremote.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Android 17 made talking to devices on the local network a runtime permission. */
private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val LOCAL_NETWORK_PERMISSION_SDK = 37

fun hasLocalNetworkAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SDK ||
        ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

@Stable
class LocalNetworkAccess(granted: Boolean) {
    var granted by mutableStateOf(granted)
        internal set
    internal var launch: () -> Unit = {}

    fun request() {
        if (!granted) launch()
    }
}

val LocalLocalNetworkAccess = staticCompositionLocalOf<LocalNetworkAccess> { error("LocalNetworkAccess not provided") }

@Composable
fun rememberLocalNetworkAccess(onGranted: () -> Unit): LocalNetworkAccess {
    val context = LocalContext.current
    val access = remember { LocalNetworkAccess(hasLocalNetworkAccess(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        access.granted = ok
        if (ok) onGranted()
    }
    SideEffect { access.launch = { launcher.launch(ACCESS_LOCAL_NETWORK) } }
    return access
}
