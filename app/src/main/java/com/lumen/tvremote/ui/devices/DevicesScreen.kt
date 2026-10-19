package com.lumen.tvremote.ui.devices

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tvremote.connection.ConnectionStatus
import com.lumen.tvremote.connection.WakeOnLan
import com.lumen.tvremote.data.TvDevice
import com.lumen.tvremote.discovery.DiscoveredTv
import com.lumen.tvremote.discovery.DiscoveryState
import com.lumen.tvremote.ui.LocalLocalNetworkAccess
import com.lumen.tvremote.ui.MainViewModel
import com.lumen.tvremote.ui.components.AuroraBackground
import com.lumen.tvremote.ui.components.GlassCard
import com.lumen.tvremote.ui.components.GlassIconButton
import com.lumen.tvremote.ui.components.SectionHeader
import com.lumen.tvremote.ui.components.StatusGlow
import com.lumen.tvremote.ui.components.glass
import kotlinx.coroutines.flow.flowOf

private val IPV4 = Regex("""^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$""")
private val HOSTNAME = Regex("""^[A-Za-z0-9]([A-Za-z0-9-]{0,62})(\.[A-Za-z0-9-]{1,63})*$""")

fun isValidHost(host: String): Boolean = host.trim().let { IPV4.matches(it) || (HOSTNAME.matches(it) && it.any(Char::isLetter)) }

@Composable
fun DevicesScreen(vm: MainViewModel, onBack: (() -> Unit)?, onSelected: () -> Unit) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val networkAccess = LocalLocalNetworkAccess.current
    // Only scan once local network access is granted, otherwise discovery just fails.
    val discoveryFlow = remember(networkAccess.granted) {
        if (networkAccess.granted) vm.discovery else flowOf(DiscoveryState())
    }
    val discovery by discoveryFlow.collectAsStateWithLifecycle(DiscoveryState())
    val tvState by vm.tvState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TvDevice?>(null) }
    var showHelp by remember { mutableStateOf(devices.isNullOrEmpty()) }

    val saved = devices.orEmpty()
    val newTvs = discovery.tvs.filter { tv -> saved.none { it.serviceName == tv.serviceName || it.host == tv.host } }
    val scanning = networkAccess.granted && discovery.error == null

    AuroraBackground {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                Row(
                    Modifier
                        .navigationBarsPadding()
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)))
                        .clickable(onClick = { showAdd = true })
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White)
                    Spacer(Modifier.size(8.dp))
                    Text("Add by IP", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    if (onBack != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                            Spacer(Modifier.size(16.dp))
                            Text("Your TVs", style = MaterialTheme.typography.headlineMedium)
                        }
                    } else {
                        Hero(scanning)
                    }
                }

                if (saved.isNotEmpty()) {
                    item { SectionHeader("Saved", Modifier.padding(top = 8.dp)) }
                    items(saved, key = { it.id }) { device ->
                        val isCurrent = tvState.device?.id == device.id
                        SavedDeviceCard(
                            device = device,
                            status = if (isCurrent) tvState.connection else null,
                            onClick = {
                                vm.select(device)
                                if (device.paired) onSelected()
                            },
                            onEdit = { editing = device },
                        )
                    }
                }

                if (!networkAccess.granted) {
                    item {
                        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Lan, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.size(12.dp))
                                Text("Allow local network access", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "Needed to find and control TVs on your Wi-Fi. If the prompt no longer appears, enable it in system settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                            Row {
                                TextButton(onClick = networkAccess::request) { Text("Allow") }
                                TextButton(onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }) { Text("Open settings") }
                            }
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        SectionHeader("On this network", Modifier.weight(1f))
                        if (scanning) {
                            Text("Scanning", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(6.dp))
                            StatusGlow(MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                discovery.error?.let { error ->
                    item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
                if (newTvs.isEmpty() && scanning) {
                    item {
                        Text(
                            if (discovery.tvs.isEmpty()) "Looking for Android TV and Google TV devices…" else "Every TV found is already saved.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                items(newTvs, key = { "d-" + it.serviceName }) { tv -> DiscoveredCard(tv) { vm.addDiscovered(tv) } }

                item { HelpCard(expanded = showHelp, onToggle = { showHelp = !showHelp }) }
            }
        }
    }

    if (showAdd) {
        DeviceEditorDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { name, host, mac ->
                showAdd = false
                vm.addManual(name, host, mac)
            },
        )
    }
    editing?.let { device ->
        DeviceEditorDialog(
            initial = device,
            onDismiss = { editing = null },
            onSave = { name, host, mac ->
                editing = null
                vm.saveDevice(
                    device.copy(
                        name = name.ifBlank { device.name },
                        host = host.trim(),
                        macAddress = mac?.takeIf { WakeOnLan.isValidMac(it) }?.let(WakeOnLan::normalize),
                    ),
                )
            },
            onForget = {
                editing = null
                vm.forget(device)
            },
            onRepair = {
                editing = null
                vm.controller.startPairing(device)
            },
        )
    }
}

/** Onboarding header: pulsing TV glyph and a short pitch. */
@Composable
private fun Hero(scanning: Boolean) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val primary = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(120.dp)
                .drawBehind {
                    val s = if (scanning) pulse else 1f
                    drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.45f), Color.Transparent)), radius = size.minDimension * 0.6f * s)
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .scale(if (scanning) 0.97f + (pulse - 1f) * 0.1f else 1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Tv, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
        }
        Spacer(Modifier.height(16.dp))
        Text("Connect your TV", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Works with every Android TV and Google TV.\nNo developer mode, no cables.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SavedDeviceCard(device: TvDevice, status: ConnectionStatus?, onClick: () -> Unit, onEdit: () -> Unit) {
    val connected = status == ConnectionStatus.Connected
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), strong = connected)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvBadge(connected)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            val detail = listOfNotNull(
                when {
                    connected -> "Connected"
                    status is ConnectionStatus.Reconnecting || status == ConnectionStatus.Connecting -> "Connecting…"
                    !device.paired -> "Not paired"
                    else -> null
                },
                device.model,
                device.host,
            ).joinToString(" · ")
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.IconButton(onClick = onEdit) {
            Icon(Icons.Rounded.Edit, "Edit ${device.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TvBadge(connected: Boolean) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (connected) Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
                else Brush.linearGradient(listOf(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Tv, null, tint = if (connected) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DiscoveredCard(tv: DiscoveredTv, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.WifiFind, null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tv.serviceName, style = MaterialTheme.typography.titleMedium)
            Text(tv.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "Pair",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun HelpCard(expanded: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .glass(RoundedCornerShape(24.dp))
            .clickable(onClick = onToggle)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.Help, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.size(12.dp))
            Text("TV not showing up?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "Turn the TV on and connect your phone to the same Wi-Fi (not a guest network).",
                    "Works with Android TV and Google TV: Sony, TCL, Philips, Hisense, Xiaomi, Chromecast with Google TV, Nvidia Shield and more.",
                    "On the TV, keep \"Android TV Remote Service\" enabled and updated (Settings › Apps › System apps).",
                    "Routers with \"AP / client isolation\" block discovery. Add the TV by IP instead; it's under TV Settings › Network › About.",
                    "To power on from standby, enable \"Network standby\" or \"Wake on LAN\" on the TV and add its MAC address here.",
                ).forEachIndexed { i, tip ->
                    Row {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(tip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceEditorDialog(
    initial: TvDevice?,
    onDismiss: () -> Unit,
    onSave: (name: String, host: String, mac: String?) -> Unit,
    onForget: (() -> Unit)? = null,
    onRepair: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var host by remember { mutableStateOf(initial?.host.orEmpty()) }
    var mac by remember { mutableStateOf(initial?.macAddress.orEmpty()) }
    var confirmForget by remember { mutableStateOf(false) }
    val hostValid = isValidHost(host)
    val macValid = mac.isBlank() || WakeOnLan.isValidMac(mac)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add TV" else "TV details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                OutlinedTextField(
                    host, { host = it.trim() },
                    label = { Text("IP address") },
                    placeholder = { Text("192.168.1.20") },
                    singleLine = true,
                    isError = host.isNotEmpty() && !hostValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    mac, { mac = it.trim() },
                    label = { Text("MAC address (optional)") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    supportingText = { Text(if (macValid) "Lets the power button wake the TV from standby" else "Use the format AA:BB:CC:DD:EE:FF") },
                    singleLine = true,
                    isError = !macValid,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
                if (initial != null) {
                    Row {
                        onRepair?.let { TextButton(onClick = it) { Text("Pair again") } }
                        Spacer(Modifier.weight(1f))
                        onForget?.let {
                            TextButton(onClick = { if (confirmForget) it() else confirmForget = true }) {
                                Text(if (confirmForget) "Tap to confirm" else "Forget TV", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), host, mac.ifBlank { null }) }, enabled = hostValid && macValid) {
                Text(if (initial == null) "Add & pair" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(30.dp),
    )
}
