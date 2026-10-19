package com.lumen.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.tvremote.ui.theme.Jakarta

/** Visual identity of an app tile: background gradient, glyph and glyph color. */
private data class Brand(val colors: List<Color>, val glyph: String? = null, val glyphColor: Color = Color.White)

private val brands = mapOf(
    "com.netflix.ninja" to Brand(listOf(Color(0xFF141414), Color(0xFF2B0A0C)), "N", Color(0xFFE50914)),
    "com.google.android.youtube.tv" to Brand(listOf(Color(0xFFFF3B30), Color(0xFFC4001D)), "▶"),
    "com.amazon.amazonvideo.livingroom" to Brand(listOf(Color(0xFF1A98FF), Color(0xFF0050A0)), "prime"),
    "com.disney.disneyplus" to Brand(listOf(Color(0xFF0B1B5A), Color(0xFF1F6FD0)), "D+"),
    "com.spotify.tv.android" to Brand(listOf(Color(0xFF1ED760), Color(0xFF0E7A37)), "♫", Color.Black),
    "com.google.android.youtube.tvmusic" to Brand(listOf(Color(0xFFFF0050), Color(0xFF8A0F3D)), "♪"),
    "com.plexapp.android" to Brand(listOf(Color(0xFF2A2C31), Color(0xFF111214)), "›", Color(0xFFE5A00D)),
    "org.xbmc.kodi" to Brand(listOf(Color(0xFF17B2E7), Color(0xFF0B5F8A)), "K"),
    "com.android.vending" to Brand(listOf(Color(0xFF01875F), Color(0xFF3B78E7)), "▷"),
)

private val fallbackGradients = listOf(
    listOf(Color(0xFF7C5CFF), Color(0xFF3A1FB8)),
    listOf(Color(0xFF00B4D8), Color(0xFF0060A0)),
    listOf(Color(0xFFFF7A59), Color(0xFFC2185B)),
    listOf(Color(0xFF2EC4B6), Color(0xFF00796B)),
    listOf(Color(0xFFFFB703), Color(0xFFFB5607)),
    listOf(Color(0xFF8338EC), Color(0xFFFF006E)),
)

@Composable
fun AppTile(name: String, target: String, modifier: Modifier = Modifier, size: Dp = 68.dp) {
    val brand = brands[target] ?: Brand(fallbackGradients[(target.hashCode() and Int.MAX_VALUE) % fallbackGradients.size])
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(brand.colors))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)), shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            target.contains("://") && brands[target] == null ->
                Icon(Icons.Rounded.Link, null, tint = Color.White, modifier = Modifier.size(size * 0.42f))
            else -> {
                val glyph = brand.glyph ?: name.take(1).uppercase()
                val fontSize = when {
                    glyph.length >= 4 -> size.value * 0.24f
                    glyph.length >= 2 -> size.value * 0.34f
                    else -> size.value * 0.46f
                }
                Text(
                    glyph,
                    color = brand.glyphColor,
                    style = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = fontSize.sp, letterSpacing = (-0.5).sp),
                )
            }
        }
    }
}
