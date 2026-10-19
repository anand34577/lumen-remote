package com.lumen.tvremote.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lumen.tvremote.BuildConfig
import com.lumen.tvremote.data.AppSettings
import com.lumen.tvremote.data.HapticStrength
import com.lumen.tvremote.data.RemoteSection
import com.lumen.tvremote.data.ThemeMode
import com.lumen.tvremote.ui.MainViewModel
import com.lumen.tvremote.ui.components.AuroraBackground
import com.lumen.tvremote.ui.components.GlassCard
import com.lumen.tvremote.ui.components.GlassIconButton
import com.lumen.tvremote.ui.components.IconBadge
import com.lumen.tvremote.ui.components.SectionHeader
import com.lumen.tvremote.ui.components.glass
import com.lumen.tvremote.ui.theme.Accents
import kotlin.math.roundToInt

private const val REPO_URL = "https://github.com/anand34577/lumen-remote"

private val Violet = Color(0xFF8B5CF6)
private val Sky = Color(0xFF38BDF8)
private val Mint = Color(0xFF34D399)
private val Amber = Color(0xFFFBBF24)
private val Rose = Color(0xFFF472B6)
private val Coral = Color(0xFFFB7185)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, settings: AppSettings, onBack: () -> Unit, onOpenCustomize: () -> Unit) {
    val update = vm::updateSettings
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        update { it.copy(persistentNotification = granted) }
    }

    AuroraBackground {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                    Spacer(Modifier.size(16.dp))
                    Text("Settings", style = MaterialTheme.typography.headlineMedium)
                }

                Group("Appearance") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Brightness4, Violet)
                            Spacer(Modifier.size(14.dp))
                            Text("Theme", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        ChoiceRow(
                            options = ThemeMode.entries,
                            selected = settings.themeMode,
                            label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                            icon = {
                                when (it) {
                                    ThemeMode.SYSTEM -> Icons.Rounded.Contrast
                                    ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                    ThemeMode.DARK -> Icons.Rounded.DarkMode
                                }
                            },
                        ) { mode -> update { it.copy(themeMode = mode) } }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Divider()
                        ToggleRow(Icons.Rounded.Palette, Sky, "Material You colors", "Match your wallpaper", settings.dynamicColor) { v ->
                            update { it.copy(dynamicColor = v) }
                        }
                    }
                    if (!settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        Divider()
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Rounded.ColorLens, Rose)
                                Spacer(Modifier.size(14.dp))
                                Text("Accent", style = MaterialTheme.typography.titleSmall)
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Accents.forEachIndexed { i, accent ->
                                    val selected = settings.accentIndex == i
                                    Box(
                                        Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(accent.seed, accent.tertiary)))
                                            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            .clickable(onClickLabel = accent.name) { update { it.copy(accentIndex = i) } },
                                        contentAlignment = Alignment.Center,
                                    ) { if (selected) Icon(Icons.Rounded.Check, accent.name, tint = Color.White) }
                                }
                            }
                        }
                    }
                    Divider()
                    ToggleRow(Icons.Rounded.DarkMode, Violet, "Pure black", "Deepest blacks for OLED screens", settings.amoledBlack) { v ->
                        update { it.copy(amoledBlack = v) }
                    }
                }

                Group("Controls") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Vibration, Mint)
                            Spacer(Modifier.size(14.dp))
                            Text("Haptic feedback", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        ChoiceRow(
                            options = HapticStrength.entries,
                            selected = settings.haptics,
                            label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                        ) { h -> update { it.copy(haptics = h) } }
                    }
                    Divider()
                    ToggleRow(Icons.AutoMirrored.Rounded.VolumeUp, Sky, "Volume buttons control TV", "Use your phone's volume keys while the remote is open", settings.volumeKeysControlTv) { v ->
                        update { it.copy(volumeKeysControlTv = v) }
                    }
                    Divider()
                    ToggleRow(Icons.Rounded.PanTool, Amber, "Left-handed layout", "Mirror the main controls", settings.leftHanded) { v ->
                        update { it.copy(leftHanded = v) }
                    }
                    Divider()
                    ToggleRow(Icons.Rounded.ScreenLockPortrait, Coral, "Keep screen on", "While the remote is open", settings.keepScreenOn) { v ->
                        update { it.copy(keepScreenOn = v) }
                    }
                    Divider()
                    SliderRow(
                        icon = Icons.Rounded.Repeat, color = Violet,
                        title = "Hold-to-repeat speed",
                        valueLabel = { "${(1000f / it).roundToInt()}/s" },
                        value = settings.repeatIntervalMs.toFloat(),
                        range = 60f..300f,
                        // Lower interval is faster, so the slider is inverted for a natural "right = faster" feel.
                        inverted = true,
                    ) { v -> update { it.copy(repeatIntervalMs = v.roundToInt()) } }
                    Divider()
                    SliderRow(
                        icon = Icons.Rounded.Swipe, color = Mint,
                        title = "Touchpad sensitivity",
                        valueLabel = { "${(it * 100).roundToInt()}%" },
                        value = settings.touchpadSensitivity,
                        range = 0.5f..2.5f,
                    ) { v -> update { it.copy(touchpadSensitivity = v) } }
                }

                Group("Remote layout") {
                    RemoteSection.entries.forEachIndexed { i, section ->
                        if (i > 0) Divider()
                        ToggleRow(
                            when (section) {
                                RemoteSection.APPS -> Icons.Rounded.Apps
                                RemoteSection.MACROS -> Icons.Rounded.AutoAwesome
                                RemoteSection.COLOR_KEYS -> Icons.Rounded.Palette
                                RemoteSection.EXTRA_KEYS -> Icons.Rounded.ViewAgenda
                            },
                            listOf(Sky, Violet, Rose, Amber)[i % 4],
                            section.label, null, section in settings.sections,
                        ) { v -> update { it.copy(sections = if (v) it.sections + section else it.sections - section) } }
                    }
                    Divider()
                    NavRow(Icons.Rounded.Link, Mint, "Edit app shortcuts & macros", onOpenCustomize)
                }

                Group("Behavior") {
                    ToggleRow(Icons.Rounded.Bolt, Amber, "Auto-connect", "Reconnect to the last TV when the app opens", settings.autoConnect) { v ->
                        update { it.copy(autoConnect = v) }
                    }
                    Divider()
                    ToggleRow(Icons.Rounded.Keyboard, Sky, "Open keyboard automatically", "When a text field is focused on the TV", settings.autoKeyboard) { v ->
                        update { it.copy(autoKeyboard = v) }
                    }
                    Divider()
                    ToggleRow(
                        Icons.Rounded.Notifications, Rose,
                        "Notification remote",
                        "D-pad and volume in the notification shade and lock screen",
                        settings.persistentNotification,
                    ) { v ->
                        val needsPermission = v && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            update { it.copy(persistentNotification = v) }
                        }
                    }
                }

                Group("About") {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        IconBadge(Icons.Rounded.Info, Violet)
                        Spacer(Modifier.size(14.dp))
                        Column {
                            Text("Lumen Remote ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Uses the official Android TV Remote protocol (v2). Your phone pairs securely with each TV; nothing leaves your home network.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Divider()
                    NavRow(Icons.Rounded.Code, Sky, "Source code and feedback") { uriHandler.openUri(REPO_URL) }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(22.dp))
    SectionHeader(title)
    Spacer(Modifier.height(10.dp))
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), padding = 0.dp, content = content)
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 70.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    )
}

@Composable
private fun ToggleRow(icon: ImageVector, color: Color, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, color)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NavRow(icon: ImageVector, color: Color, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, color)
        Spacer(Modifier.size(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Pill-shaped single choice selector. */
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    icon: ((T) -> ImageVector)? = null,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth().glass(CircleShape).padding(4.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)))
                        else Modifier,
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                icon?.let {
                    Icon(it(option), null, tint = tint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                }
                Text(label(option), color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    color: Color,
    title: String,
    valueLabel: (Float) -> String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    inverted: Boolean = false,
    onChange: (Float) -> Unit,
) {
    fun toSlider(v: Float) = if (inverted) range.endInclusive + range.start - v else v
    var local by remember(value) { mutableFloatStateOf(toSlider(value)) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color)
            Spacer(Modifier.size(14.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueLabel(toSlider(local)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(toSlider(local)) },
            valueRange = range,
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            modifier = Modifier.padding(start = 54.dp),
        )
    }
}
