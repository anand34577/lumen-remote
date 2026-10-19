package com.lumen.tvremote.ui.remote

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.tvremote.protocol.TvKey
import com.lumen.tvremote.ui.LocalHaptics
import com.lumen.tvremote.ui.components.glass
import com.lumen.tvremote.ui.components.isDarkScheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** How a key reacts to being held. */
enum class HoldBehavior { NONE, REPEAT, LONG_PRESS }

/** Callbacks the controls use to talk to the TV. */
interface KeySink {
    fun tap(key: TvKey)
    fun longStart(key: TvKey)
    fun longEnd(key: TvKey)
}

private const val LONG_PRESS_MS = 450L
private const val REPEAT_START_MS = 380L

/** Radius of the D-pad's OK orb as a fraction of the pad radius. */
private const val OK_ZONE = 0.42f

/** Visual style of a [RemoteKey]. */
enum class KeyStyle { GLASS, ACCENT, DANGER, PLAIN }

/**
 * Press handling shared by all keys: a tap sends a short press; holding either repeats the key
 * or sends a real long press (START_LONG/END_LONG) that the TV treats like a held remote button.
 */
private fun Modifier.keyPress(
    key: TvKey,
    behavior: HoldBehavior,
    repeatMs: Int,
    sink: KeySink,
    onPressed: (Boolean) -> Unit,
    haptic: (Boolean) -> Unit,
): Modifier = pointerInput(key, behavior, repeatMs, sink) {
    awaitEachGesture {
        awaitFirstDown()
        onPressed(true)
        haptic(true)
        val start = System.currentTimeMillis()
        var longStarted = false
        if (behavior == HoldBehavior.REPEAT) sink.tap(key)

        // Poll the pointer until it lifts, firing repeats or the long press as time passes.
        var nextRepeat = start + REPEAT_START_MS
        while (true) {
            val event = withTimeoutOrNull(16) { awaitPointerEvent() }
            if (event != null && event.changes.all { !it.pressed }) break
            val now = System.currentTimeMillis()
            when (behavior) {
                HoldBehavior.REPEAT -> if (now >= nextRepeat) {
                    sink.tap(key)
                    haptic(false)
                    nextRepeat = now + repeatMs
                }
                HoldBehavior.LONG_PRESS -> if (!longStarted && now - start >= LONG_PRESS_MS) {
                    longStarted = true
                    sink.longStart(key)
                    haptic(true)
                }
                HoldBehavior.NONE -> Unit
            }
        }
        when {
            longStarted -> sink.longEnd(key)
            behavior != HoldBehavior.REPEAT -> sink.tap(key)
        }
        onPressed(false)
    }
}

@Composable
fun RemoteKey(
    key: TvKey,
    sink: KeySink,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String? = null,
    behavior: HoldBehavior = HoldBehavior.NONE,
    repeatMs: Int = 110,
    size: Dp = 56.dp,
    shape: Shape = CircleShape,
    style: KeyStyle = KeyStyle.GLASS,
    tint: Color? = null,
    fill: Color? = null,
) {
    val haptics = LocalHaptics.current
    val colors = MaterialTheme.colorScheme
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 900f), label = "scale")

    val base = when {
        fill != null -> Modifier.clip(shape).background(fill)
        style == KeyStyle.ACCENT -> Modifier.clip(shape).background(Brush.linearGradient(listOf(colors.primary, colors.tertiary)))
        style == KeyStyle.DANGER -> Modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xFFFF5A5F), Color(0xFFD7263D))))
        style == KeyStyle.PLAIN -> Modifier.clip(shape)
        else -> Modifier.glass(shape, strong = true)
    }
    val contentColor = tint ?: when (style) {
        KeyStyle.ACCENT, KeyStyle.DANGER -> Color.White
        else -> colors.onSurface
    }
    val glow = if (style == KeyStyle.ACCENT || style == KeyStyle.DANGER) {
        if (style == KeyStyle.DANGER) Color(0xFFFF4D5A) else colors.primary
    } else {
        null
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .drawBehind {
                // Soft halo under colored keys, brighter while pressed.
                if (glow != null) {
                    drawCircle(
                        Brush.radialGradient(listOf(glow.copy(alpha = if (pressed) 0.6f else 0.35f), Color.Transparent)),
                        radius = this.size.minDimension * 0.85f,
                    )
                }
            }
            .then(base)
            .drawBehind {
                if (pressed) drawRect(colors.primary.copy(alpha = 0.28f))
            }
            .semantics {
                role = Role.Button
                contentDescription = key.label
                onClick { sink.tap(key); true }
            }
            .keyPress(key, behavior, repeatMs, sink, { pressed = it }) { primary ->
                if (primary) haptics.tick() else haptics.light()
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(size * 0.42f))
            text != null -> Text(text, color = contentColor, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

/** A key with a small caption underneath. */
@Composable
fun LabeledKey(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        content()
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Glass D-pad with a glowing gradient OK orb; directions repeat while held, OK supports long press. */
@Composable
fun DPad(sink: KeySink, repeatMs: Int, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    var active by remember { mutableStateOf<TvKey?>(null) }
    val colors = MaterialTheme.colorScheme
    val dark = isDarkScheme()
    val okScale by animateFloatAsState(if (active == TvKey.CENTER) 0.92f else 1f, spring(dampingRatio = 0.5f), label = "ok")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(repeatMs, sink) {
                awaitEachGesture {
                    // Zone is fixed at touch-down so sliding off a direction does not change the key.
                    val down = awaitFirstDown()
                    val radius = size.width / 2f
                    val dx = down.position.x - radius
                    val dy = down.position.y - radius
                    val dist = hypot(dx, dy)
                    val k = when {
                        dist > radius -> null
                        dist < radius * OK_ZONE -> TvKey.CENTER
                        else -> {
                            val angle = Math.toDegrees(atan2(dy, dx).toDouble())
                            when {
                                angle in -45.0..45.0 -> TvKey.RIGHT
                                angle in 45.0..135.0 -> TvKey.DOWN
                                angle in -135.0..-45.0 -> TvKey.UP
                                else -> TvKey.LEFT
                            }
                        }
                    } ?: return@awaitEachGesture
                    active = k
                    haptics.tick()
                    val longCapable = k == TvKey.CENTER
                    val start = System.currentTimeMillis()
                    var longStarted = false
                    var nextRepeat = start + REPEAT_START_MS
                    if (!longCapable) sink.tap(k)
                    while (true) {
                        val event = withTimeoutOrNull(16) { awaitPointerEvent() }
                        if (event != null && event.changes.all { !it.pressed }) break
                        val now = System.currentTimeMillis()
                        if (!longCapable && now >= nextRepeat) {
                            sink.tap(k)
                            haptics.light()
                            nextRepeat = now + repeatMs
                        } else if (longCapable && !longStarted && now - start >= LONG_PRESS_MS) {
                            longStarted = true
                            sink.longStart(k)
                            haptics.heavy()
                        }
                    }
                    if (longStarted) sink.longEnd(k) else if (longCapable) sink.tap(k)
                    active = null
                }
            }
            .semantics { contentDescription = "Directional pad. OK in the center" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            // Body: translucent disc with a subtle top highlight.
            drawCircle(if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.8f), r, c)
            drawCircle(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.9f), colors.primary.copy(alpha = 0.15f)),
                    start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r),
                ),
                r - 0.75.dp.toPx(), c, style = Stroke(1.5.dp.toPx()),
            )
            // Inner groove separating directions from the OK orb.
            drawCircle(colors.onSurface.copy(alpha = 0.06f), r * (OK_ZONE + 0.1f), c, style = Stroke(1.dp.toPx()))
            // Pressed wedge glow.
            active?.takeIf { it != TvKey.CENTER }?.let { k ->
                val startAngle = when (k) {
                    TvKey.RIGHT -> -45f
                    TvKey.DOWN -> 45f
                    TvKey.LEFT -> 135f
                    else -> 225f
                }
                drawArc(
                    Brush.radialGradient(listOf(Color.Transparent, colors.primary.copy(alpha = 0.45f)), c, r),
                    startAngle, 90f, useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                )
            }
        }
        val chevron = colors.onSurface.copy(alpha = 0.75f)
        val arrowModifier = Modifier.size(36.dp)
        // Rotated up-arrows: D-pad directions are physical and must not mirror in RTL layouts.
        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = if (active == TvKey.UP) colors.primary else chevron,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp).then(arrowModifier))
        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = if (active == TvKey.DOWN) colors.primary else chevron,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).then(arrowModifier).rotate(180f))
        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = if (active == TvKey.LEFT) colors.primary else chevron,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp).then(arrowModifier).rotate(-90f))
        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = if (active == TvKey.RIGHT) colors.primary else chevron,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp).then(arrowModifier).rotate(90f))

        // OK orb with halo; the orb's visible radius matches OK_ZONE.
        Box(
            Modifier
                .fillMaxSize(OK_ZONE / 0.8f)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(listOf(colors.primary.copy(alpha = if (active == TvKey.CENTER) 0.55f else 0.3f), Color.Transparent)),
                        radius = size.minDimension * 0.62f,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize(0.8f)
                    .scale(okScale)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary)))
                    .drawBehind {
                        drawCircle(
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), endY = size.height * 0.6f),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("OK", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

/** Swipe surface: swipes step the focus, tap is OK, long press is a held OK. */
@Composable
fun Touchpad(sink: KeySink, sensitivity: Float, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val stepPx = with(density) { (44.dp / sensitivity.coerceIn(0.4f, 3f)).toPx() }
    var touch by remember { mutableStateOf<Offset?>(null) }
    val scope = rememberCoroutineScope()
    val currentSink by rememberUpdatedState(sink)
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(40.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .glass(shape, strong = true)
            .pointerInput(stepPx) {
                var accX = 0f
                var accY = 0f
                detectDragGestures(
                    onDragStart = { touch = it; accX = 0f; accY = 0f },
                    onDragEnd = { touch = null },
                    onDragCancel = { touch = null },
                ) { change, drag ->
                    touch = change.position
                    accX += drag.x
                    accY += drag.y
                    // Step on the dominant axis only, so diagonal swipes stay predictable.
                    while (abs(accX) >= stepPx || abs(accY) >= stepPx) {
                        val dir = if (abs(accX) >= abs(accY)) {
                            val right = accX > 0
                            accX -= if (right) stepPx else -stepPx
                            accY = 0f
                            if (right) TvKey.RIGHT else TvKey.LEFT
                        } else {
                            val down = accY > 0
                            accY -= if (down) stepPx else -stepPx
                            accX = 0f
                            if (down) TvKey.DOWN else TvKey.UP
                        }
                        currentSink.tap(dir)
                        haptics.light()
                    }
                }
            }
            .pointerInput(Unit) {
                var longStarted = false
                detectTapGestures(
                    onPress = { pos ->
                        touch = pos
                        longStarted = false
                        val longJob: Job = scope.launch {
                            delay(LONG_PRESS_MS)
                            if (isActive) {
                                longStarted = true
                                currentSink.longStart(TvKey.CENTER)
                                haptics.heavy()
                            }
                        }
                        tryAwaitRelease()
                        longJob.cancel()
                        if (longStarted) currentSink.longEnd(TvKey.CENTER)
                        touch = null
                    },
                    onTap = {
                        if (!longStarted) {
                            currentSink.tap(TvKey.CENTER)
                            haptics.tick()
                        }
                    },
                )
            }
            .semantics { contentDescription = "Touchpad. Swipe to move, tap to select" },
        contentAlignment = Alignment.Center,
    ) {
        // Dotted texture so the surface reads as touchable.
        Canvas(Modifier.fillMaxSize()) {
            val gap = 22.dp.toPx()
            val dot = colors.onSurface.copy(alpha = 0.07f)
            var y = gap
            while (y < size.height) {
                var x = gap
                while (x < size.width) {
                    drawCircle(dot, 1.4.dp.toPx(), Offset(x, y))
                    x += gap
                }
                y += gap
            }
            touch?.let {
                drawCircle(Brush.radialGradient(listOf(colors.primary.copy(alpha = 0.45f), Color.Transparent), it, 70.dp.toPx()), 70.dp.toPx(), it)
                drawCircle(colors.primary, 10.dp.toPx(), it)
            }
        }
        if (touch == null) {
            Text(
                "Swipe to navigate\nTap to select · Hold for options",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Vertical glass capsule with + and − keys that repeat while held, e.g. volume or channel. */
@Composable
fun Rocker(
    label: String,
    upKey: TvKey,
    downKey: TvKey,
    sink: KeySink,
    repeatMs: Int,
    modifier: Modifier = Modifier,
    upIcon: ImageVector = Icons.Rounded.Add,
    downIcon: ImageVector = Icons.Rounded.Remove,
    middle: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .glass(RoundedCornerShape(36.dp), strong = true)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RemoteKey(upKey, sink, icon = upIcon, behavior = HoldBehavior.REPEAT, repeatMs = repeatMs, style = KeyStyle.PLAIN, size = 60.dp)
        if (middle != null) {
            middle()
        } else {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RemoteKey(downKey, sink, icon = downIcon, behavior = HoldBehavior.REPEAT, repeatMs = repeatMs, style = KeyStyle.PLAIN, size = 60.dp)
    }
}

@Composable
fun MuteKey(muted: Boolean, sink: KeySink) {
    RemoteKey(
        TvKey.MUTE, sink,
        icon = if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
        size = 42.dp,
        style = if (muted) KeyStyle.DANGER else KeyStyle.PLAIN,
        tint = if (muted) null else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
