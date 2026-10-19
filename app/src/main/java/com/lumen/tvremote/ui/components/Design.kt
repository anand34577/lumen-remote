package com.lumen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** True when the current color scheme is dark; used to tune glass and glow intensity. */
@Composable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.4f

/**
 * Soft colored light behind the whole app. Deliberately static: a remote often stays open with
 * the screen on, and a full-screen animation would redraw every frame and drain the battery.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = isDarkScheme()
    val drift = 0.4f
    val a1 = colors.primary.copy(alpha = if (dark) 0.32f else 0.20f)
    val a2 = colors.tertiary.copy(alpha = if (dark) 0.22f else 0.14f)
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(
                    Brush.radialGradient(listOf(a1, Color.Transparent), Offset(w * (0.15f + 0.25f * drift), h * 0.08f), w * 0.9f),
                    radius = w * 0.9f,
                    center = Offset(w * (0.15f + 0.25f * drift), h * 0.08f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(a2, Color.Transparent), Offset(w * (0.95f - 0.2f * drift), h * 0.55f), w * 0.8f),
                    radius = w * 0.8f,
                    center = Offset(w * (0.95f - 0.2f * drift), h * 0.55f),
                )
            },
        content = content,
    )
}

/** Frosted translucent surface with a hairline highlight border. */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp), strong: Boolean = false): Modifier {
    val colors = MaterialTheme.colorScheme
    val dark = isDarkScheme()
    val fill = if (dark) Color.White.copy(alpha = if (strong) 0.09f else 0.055f)
    else Color.White.copy(alpha = if (strong) 0.85f else 0.65f)
    val edge = Brush.linearGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.03f))
        else listOf(Color.White, colors.primary.copy(alpha = 0.10f)),
    )
    return this
        .clip(shape)
        .background(fill)
        .border(1.dp, edge, shape)
}

/** Brand gradient used for hero elements (OK button, primary actions). */
@Composable
fun accentBrush(): Brush {
    val c = MaterialTheme.colorScheme
    return Brush.linearGradient(listOf(c.primary, c.tertiary))
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.glass(shape).padding(padding), content = content)
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier
            .size(size)
            .glass(CircleShape)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Small uppercase section header with an optional trailing action. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** Rounded square with a tinted background holding an icon; used in lists and settings. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.55f)) }
}

/** Glowing status dot. */
@Composable
fun StatusGlow(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(12.dp)
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent)), radius = size.minDimension)
                drawCircle(color, radius = size.minDimension / 3f)
            },
    )
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.size(height))
