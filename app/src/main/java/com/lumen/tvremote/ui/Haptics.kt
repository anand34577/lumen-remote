package com.lumen.tvremote.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.lumen.tvremote.data.HapticStrength

/** Haptic feedback tuned by the user's strength preference. */
class Haptics(context: Context, private val strength: HapticStrength) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /** Feedback for a normal key press. */
    fun tick() = play(primary = true)

    /** Lighter feedback for repeats and touchpad steps. */
    fun light() = play(primary = false)

    /** Distinct feedback for long presses and errors. */
    fun heavy() {
        if (strength == HapticStrength.OFF) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun play(primary: Boolean) {
        if (strength == HapticStrength.OFF) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = when {
                !primary || strength == HapticStrength.LIGHT -> VibrationEffect.EFFECT_TICK
                strength == HapticStrength.MEDIUM -> VibrationEffect.EFFECT_CLICK
                else -> VibrationEffect.EFFECT_HEAVY_CLICK
            }
            v.vibrate(VibrationEffect.createPredefined(effect))
        } else {
            val (ms, amp) = when (strength) {
                HapticStrength.LIGHT -> 8L to 60
                HapticStrength.MEDIUM -> 14L to 120
                else -> 22L to 220
            }
            v.vibrate(VibrationEffect.createOneShot(if (primary) ms else ms / 2, amp))
        }
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { error("Haptics not provided") }

@Composable
fun rememberHaptics(strength: HapticStrength): Haptics {
    val context = LocalContext.current
    return remember(strength) { Haptics(context, strength) }
}
