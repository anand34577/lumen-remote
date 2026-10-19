package com.lumen.tvremote.ui.remote

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tvremote.connection.ConnectionStatus
import com.lumen.tvremote.connection.ControllerEvent
import com.lumen.tvremote.connection.TvState
import com.lumen.tvremote.data.AppSettings
import com.lumen.tvremote.data.AppShortcut
import com.lumen.tvremote.data.Macro
import com.lumen.tvremote.data.PadMode
import com.lumen.tvremote.data.RemoteSection
import com.lumen.tvremote.protocol.TvKey
import com.lumen.tvremote.ui.LocalHaptics
import com.lumen.tvremote.ui.MainViewModel
import com.lumen.tvremote.ui.components.AppTile
import com.lumen.tvremote.ui.components.AuroraBackground
import com.lumen.tvremote.ui.components.GlassCard
import com.lumen.tvremote.ui.components.GlassIconButton
import com.lumen.tvremote.ui.components.SectionHeader
import com.lumen.tvremote.ui.components.StatusGlow
import com.lumen.tvremote.ui.components.glass
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Green = Color(0xFF34D399)
private val Amber = Color(0xFFFBBF24)

@Composable
fun RemoteScreen(
    vm: MainViewModel,
    settings: AppSettings,
    onOpenDevices: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCustomize: () -> Unit,
) {
    val state by vm.tvState.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    val macros by vm.macros.collectAsStateWithLifecycle()
    val controller = vm.controller
    val haptics = LocalHaptics.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var keyboardOpen by rememberSaveable { mutableStateOf(false) }

    val sink = remember(controller) {
        object : KeySink {
            override fun tap(key: TvKey) {
                if (!controller.sendKey(key)) haptics.heavy()
            }
            override fun longStart(key: TvKey) {
                controller.keyDown(key)
            }
            override fun longEnd(key: TvKey) {
                controller.keyUp(key)
            }
        }
    }
    // Power goes through the controller so it can wake a sleeping TV with Wake-on-LAN.
    val powerSink = remember(controller) {
        object : KeySink by sink {
            override fun tap(key: TvKey) = controller.power()
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (text != null) controller.voiceInput(text)
    }
    val startVoice: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what to search or type on the TV")
        try {
            voiceLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Speech recognition isn't available on this phone") }
        }
    }

    val autoKeyboard by rememberUpdatedState(settings.autoKeyboard)
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is ControllerEvent.Message -> scope.launch { snackbar.showSnackbar(event.text) }
                ControllerEvent.KeyboardRequested -> if (autoKeyboard) keyboardOpen = true
            }
        }
    }

    val deviceName = state.device?.let { d -> devices?.find { it.id == d.id }?.name ?: d.name }

    AuroraBackground {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = Color.Transparent,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopBar(deviceName, state, onOpenDevices, onOpenSettings)
                ConnectionBanner(
                    state = state,
                    onRetry = controller::retry,
                    onPair = { state.device?.let(controller::startPairing) },
                    onChoose = onOpenDevices,
                )
                NowPlayingCard(state, shortcuts, onPin = vm::pinCurrentApp, onSetVolume = controller::setVolume)

                Spacer(Modifier.height(18.dp))
                QuickRow(sink, powerSink, state.powerOn, onVoice = startVoice, onKeyboard = { keyboardOpen = true }, leftHanded = settings.leftHanded)

                Spacer(Modifier.height(22.dp))
                ModeSwitcher(settings.padMode) { mode ->
                    haptics.light()
                    vm.updateSettings { it.copy(padMode = mode) }
                }
                Spacer(Modifier.height(18.dp))

                AnimatedContent(
                    targetState = settings.padMode,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.96f)) },
                    label = "surface",
                    modifier = Modifier.widthIn(max = 330.dp),
                ) { mode ->
                    when (mode) {
                        PadMode.DPAD -> DPad(sink, settings.repeatIntervalMs)
                        PadMode.TOUCHPAD -> Touchpad(sink, settings.touchpadSensitivity)
                        PadMode.NUMPAD -> NumPad(sink)
                        PadMode.MEDIA -> MediaPanel(sink, settings.repeatIntervalMs)
                    }
                }

                Spacer(Modifier.height(22.dp))
                NavRow(sink, settings.leftHanded)
                Spacer(Modifier.height(22.dp))
                VolumeAndChannel(sink, state, settings)

                val sections = settings.sections
                if (RemoteSection.APPS in sections) {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader("Quick launch", Modifier.fillMaxWidth(), action = "Edit", onAction = onOpenCustomize)
                    Spacer(Modifier.height(12.dp))
                    AppsRow(shortcuts, onLaunch = { controller.launch(it.launchLink); haptics.tick() }, onAdd = onOpenCustomize)
                }
                if (RemoteSection.MACROS in sections) {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Macros", Modifier.fillMaxWidth(), action = "Edit", onAction = onOpenCustomize)
                    Spacer(Modifier.height(12.dp))
                    MacrosRow(macros, state.runningMacro, onRun = controller::runMacro, onStop = controller::cancelMacro, onAdd = onOpenCustomize)
                }
                if (RemoteSection.COLOR_KEYS in sections) {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Color keys", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    ColorKeys(sink)
                }
                if (RemoteSection.EXTRA_KEYS in sections) {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("TV keys", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    ExtraKeys(sink)
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier
                        .glass(CircleShape)
                        .clickable(onClick = onOpenCustomize)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text("Customize apps, macros & layout", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (keyboardOpen) {
        KeyboardSheet(
            state = state,
            onTextChange = controller::setText,
            onKey = { controller.sendKey(it) },
            onVoice = startVoice,
            onDismiss = { keyboardOpen = false },
        )
    }
}

@Composable
private fun statusColor(status: ConnectionStatus): Color = when (status) {
    ConnectionStatus.Connected -> Green
    ConnectionStatus.Connecting, is ConnectionStatus.Reconnecting -> Amber
    ConnectionStatus.NeedsPairing -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun TopBar(deviceName: String?, state: TvState, onOpenDevices: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .glass(RoundedCornerShape(24.dp))
                .clickable(onClick = onOpenDevices)
                .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Tv, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    deviceName ?: "Choose a TV",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusGlow(statusColor(state.connection))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        statusText(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.Rounded.ExpandMore, contentDescription = "Switch TV", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(10.dp))
        GlassIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings, size = 58.dp)
    }
}

private fun statusText(state: TvState): String = when (val c = state.connection) {
    ConnectionStatus.Connected -> listOfNotNull(
        "Connected",
        state.tvModel,
        state.powerOn?.let { if (it) null else "Screen off" },
    ).joinToString(" · ")
    ConnectionStatus.Connecting -> "Connecting…"
    is ConnectionStatus.Reconnecting -> "Reconnecting…"
    ConnectionStatus.NeedsPairing -> "Pairing required"
    is ConnectionStatus.Offline -> "Offline"
    ConnectionStatus.Idle -> if (state.device == null) "Tap to add your TV" else "Not connected"
}

@Composable
private fun ConnectionBanner(state: TvState, onRetry: () -> Unit, onPair: () -> Unit, onChoose: () -> Unit) {
    val c = state.connection
    AnimatedVisibility(
        visible = c != ConnectionStatus.Connected,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(24.dp), padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val busy = c is ConnectionStatus.Connecting || c is ConnectionStatus.Reconnecting
                if (busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                } else {
                    StatusGlow(statusColor(c))
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = when (c) {
                        ConnectionStatus.Connecting -> "Connecting to your TV…"
                        is ConnectionStatus.Reconnecting -> c.reason.ifBlank { "Connection interrupted" } + ". Retrying…"
                        ConnectionStatus.NeedsPairing -> "This TV needs to be paired once."
                        is ConnectionStatus.Offline -> c.reason
                        else -> if (state.device == null) "Add your Android TV or Google TV to get started." else "Disconnected."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                val action: Pair<String, () -> Unit>? = when {
                    state.device == null -> "Add TV" to onChoose
                    c == ConnectionStatus.NeedsPairing -> "Pair" to onPair
                    c is ConnectionStatus.Reconnecting -> "Retry" to onRetry
                    c is ConnectionStatus.Offline || c == ConnectionStatus.Idle -> "Connect" to onRetry
                    else -> null
                }
                action?.let { (label, onClick) -> PillButton(label, onClick) }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge) }
}

/** Live TV card: what's playing, pin-to-shortcuts and an exact volume slider. */
@Composable
private fun NowPlayingCard(state: TvState, shortcuts: List<AppShortcut>, onPin: (String) -> Unit, onSetVolume: (Int) -> Unit) {
    AnimatedVisibility(
        visible = state.isConnected && (state.currentApp != null || state.volume != null),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(26.dp), padding = 16.dp) {
            state.currentApp?.let { pkg ->
                val known = shortcuts.find { it.target == pkg }
                val name = known?.name ?: AppShortcut.labelFor(pkg)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTile(name, pkg, size = 44.dp)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("NOW ON TV", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (known == null) GlassIconButton(Icons.Rounded.PushPin, "Pin $name to quick launch", { onPin(pkg) }, size = 40.dp)
                }
            }
            state.volume?.takeIf { it.max > 0 }?.let { v ->
                if (state.currentApp != null) Spacer(Modifier.height(10.dp))
                VolumeSlider(v.level, v.max, v.muted, onSetVolume)
            }
        }
    }
}

@Composable
private fun VolumeSlider(level: Int, max: Int, muted: Boolean, onSet: (Int) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf(level.toFloat()) }
    if (!dragging) value = level.toFloat()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Slider(
            value = value,
            onValueChange = { dragging = true; value = it },
            onValueChangeFinished = {
                dragging = false
                onSet(value.roundToInt())
            },
            valueRange = 0f..max.toFloat(),
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text(
            if (muted) "Muted" else "${value.roundToInt()}",
            style = MaterialTheme.typography.labelLarge,
            color = if (muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun QuickRow(
    sink: KeySink,
    powerSink: KeySink,
    powerOn: Boolean?,
    onVoice: () -> Unit,
    onKeyboard: () -> Unit,
    leftHanded: Boolean,
) {
    val voiceGlow = MaterialTheme.colorScheme.tertiary
    val items: List<@Composable () -> Unit> = listOf(
        {
            LabeledKey("Power") {
                RemoteKey(
                    TvKey.POWER, powerSink, icon = Icons.Rounded.PowerSettingsNew, behavior = HoldBehavior.LONG_PRESS,
                    style = if (powerOn == false) KeyStyle.GLASS else KeyStyle.DANGER,
                )
            }
        },
        { LabeledKey("Input") { RemoteKey(TvKey.INPUT, sink, icon = Icons.AutoMirrored.Rounded.Input) } },
        {
            LabeledKey("Voice") {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(voiceGlow, MaterialTheme.colorScheme.primary)))
                        .clickable(onClickLabel = "Voice search", onClick = onVoice),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Mic, contentDescription = "Voice search", tint = Color.White) }
            }
        },
        {
            LabeledKey("Type") {
                Box(
                    Modifier.size(56.dp).glass(CircleShape, strong = true).clickable(onClickLabel = "Keyboard", onClick = onKeyboard),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Keyboard, contentDescription = "Keyboard", modifier = Modifier.size(24.dp)) }
            }
        },
        { LabeledKey("Apps") { RemoteKey(TvKey.ALL_APPS, sink, icon = Icons.Rounded.Apps) } },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (if (leftHanded) items.reversed() else items).forEach { it() }
    }
}

/** Segmented glass switch with a sliding gradient indicator. */
@Composable
private fun ModeSwitcher(mode: PadMode, onChange: (PadMode) -> Unit) {
    val modes = PadMode.entries
    val icons: Map<PadMode, ImageVector> = mapOf(
        PadMode.DPAD to Icons.Rounded.Gamepad,
        PadMode.TOUCHPAD to Icons.Rounded.Swipe,
        PadMode.NUMPAD to Icons.Rounded.Dialpad,
        PadMode.MEDIA to Icons.Rounded.PlayArrow,
    )
    BoxWithConstraints(
        Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .height(52.dp)
            .glass(CircleShape)
            .padding(4.dp),
    ) {
        val itemWidth = maxWidth / modes.size
        val offset by animateDpAsState(itemWidth * modes.indexOf(mode), spring(dampingRatio = 0.75f, stiffness = 500f), label = "indicator")
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(itemWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
        )
        Row(Modifier.fillMaxSize()) {
            modes.forEach { m ->
                val selected = m == mode
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(onClickLabel = m.label) { onChange(m) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(icons.getValue(m), null, tint = tint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(m.label, color = tint, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun NavRow(sink: KeySink, leftHanded: Boolean) {
    val keys: List<@Composable () -> Unit> = listOf(
        { LabeledKey("Back") { RemoteKey(TvKey.BACK, sink, icon = Icons.AutoMirrored.Rounded.ArrowBack, behavior = HoldBehavior.LONG_PRESS, size = 64.dp) } },
        { LabeledKey("Home") { RemoteKey(TvKey.HOME, sink, icon = Icons.Rounded.Home, behavior = HoldBehavior.LONG_PRESS, size = 64.dp) } },
        { LabeledKey("TV settings") { RemoteKey(TvKey.SETTINGS, sink, icon = Icons.Rounded.Settings, size = 64.dp) } },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        (if (leftHanded) keys.reversed() else keys).forEach { it() }
    }
}

@Composable
private fun VolumeAndChannel(sink: KeySink, state: TvState, settings: AppSettings) {
    val volume: @Composable () -> Unit = {
        Rocker(
            "VOL", TvKey.VOLUME_UP, TvKey.VOLUME_DOWN, sink, settings.repeatIntervalMs,
            middle = { MuteKey(state.volume?.muted == true, sink) },
        )
    }
    val channel: @Composable () -> Unit = {
        Rocker(
            "CH", TvKey.CHANNEL_UP, TvKey.CHANNEL_DOWN, sink, settings.repeatIntervalMs,
            upIcon = Icons.Rounded.KeyboardArrowUp, downIcon = Icons.Rounded.KeyboardArrowDown,
        )
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (settings.leftHanded) channel() else volume()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            RemoteKey(TvKey.PLAY_PAUSE, sink, icon = Icons.Rounded.PlayArrow, size = 84.dp, style = KeyStyle.ACCENT)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteKey(TvKey.REWIND, sink, icon = Icons.Rounded.FastRewind, size = 48.dp, behavior = HoldBehavior.REPEAT, repeatMs = settings.repeatIntervalMs * 3)
                RemoteKey(TvKey.FAST_FORWARD, sink, icon = Icons.Rounded.FastForward, size = 48.dp, behavior = HoldBehavior.REPEAT, repeatMs = settings.repeatIntervalMs * 3)
            }
        }
        if (settings.leftHanded) volume() else channel()
    }
}

@Composable
private fun MediaPanel(sink: KeySink, repeatMs: Int) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(40.dp), padding = 22.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RemoteKey(TvKey.PREVIOUS, sink, icon = Icons.Rounded.SkipPrevious, size = 58.dp)
            RemoteKey(TvKey.PLAY_PAUSE, sink, icon = Icons.Rounded.PlayArrow, size = 96.dp, style = KeyStyle.ACCENT)
            RemoteKey(TvKey.NEXT, sink, icon = Icons.Rounded.SkipNext, size = 58.dp)
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LabeledKey("Rewind") { RemoteKey(TvKey.REWIND, sink, icon = Icons.Rounded.FastRewind, behavior = HoldBehavior.REPEAT, repeatMs = repeatMs * 3) }
            LabeledKey("Pause") { RemoteKey(TvKey.PAUSE, sink, icon = Icons.Rounded.Pause) }
            LabeledKey("Stop") { RemoteKey(TvKey.STOP, sink, icon = Icons.Rounded.Stop) }
            LabeledKey("Forward") { RemoteKey(TvKey.FAST_FORWARD, sink, icon = Icons.Rounded.FastForward, behavior = HoldBehavior.REPEAT, repeatMs = repeatMs * 3) }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LabeledKey("Subtitles") { RemoteKey(TvKey.CAPTIONS, sink, icon = Icons.Rounded.ClosedCaption, size = 52.dp) }
            LabeledKey("Audio") { RemoteKey(TvKey.AUDIO_TRACK, sink, icon = Icons.Rounded.GraphicEq, size = 52.dp) }
        }
    }
}

@Composable
private fun NumPad(sink: KeySink) {
    val rows = listOf(
        listOf(TvKey.NUM_1, TvKey.NUM_2, TvKey.NUM_3),
        listOf(TvKey.NUM_4, TvKey.NUM_5, TvKey.NUM_6),
        listOf(TvKey.NUM_7, TvKey.NUM_8, TvKey.NUM_9),
        listOf(TvKey.LAST_CHANNEL, TvKey.NUM_0, TvKey.ENTER),
    )
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(40.dp), padding = 18.dp) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { key ->
                        val text = when (key) {
                            TvKey.LAST_CHANNEL -> "↺"
                            TvKey.ENTER -> "OK"
                            else -> key.label
                        }
                        RemoteKey(
                            key, sink, text = text, size = 68.dp, shape = RoundedCornerShape(24.dp),
                            style = if (key == TvKey.ENTER) KeyStyle.ACCENT else KeyStyle.GLASS,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsRow(shortcuts: List<AppShortcut>, onLaunch: (AppShortcut) -> Unit, onAdd: () -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(shortcuts, key = { it.id }) { app ->
            Column(
                Modifier.width(72.dp).clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = "Open ${app.name}") { onLaunch(app) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppTile(app.name, app.target)
                Spacer(Modifier.height(8.dp))
                Text(app.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Column(
                Modifier.width(72.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onAdd),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(68.dp).glass(RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                Text("Add", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MacrosRow(macros: List<Macro>, running: String?, onRun: (Macro) -> Unit, onStop: () -> Unit, onAdd: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        macros.forEach { macro ->
            val isRunning = running == macro.name
            GlassChip(
                label = if (isRunning) "Stop ${macro.name}" else macro.name,
                onClick = { if (isRunning) onStop() else onRun(macro) },
                leading = {
                    if (isRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                },
            )
        }
        GlassChip(label = "New macro", onClick = onAdd, leading = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) })
    }
}

@Composable
private fun GlassChip(label: String, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .glass(CircleShape, strong = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.size(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ColorKeys(sink: KeySink) {
    val keys = listOf(
        TvKey.RED to Color(0xFFEF4444),
        TvKey.GREEN to Color(0xFF22C55E),
        TvKey.YELLOW to Color(0xFFEAB308),
        TvKey.BLUE to Color(0xFF3B82F6),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        keys.forEach { (key, color) ->
            RemoteKey(
                key, sink, modifier = Modifier.weight(1f), size = 46.dp,
                shape = RoundedCornerShape(16.dp), fill = color.copy(alpha = 0.9f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraKeys(sink: KeySink) {
    val keys = listOf(
        TvKey.GUIDE, TvKey.INFO, TvKey.CAPTIONS, TvKey.AUDIO_TRACK, TvKey.MENU,
        TvKey.HDMI_1, TvKey.HDMI_2, TvKey.HDMI_3, TvKey.HDMI_4, TvKey.SLEEP,
    )
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        keys.forEach { key -> GlassChip(label = key.label, onClick = { sink.tap(key) }) }
    }
}
