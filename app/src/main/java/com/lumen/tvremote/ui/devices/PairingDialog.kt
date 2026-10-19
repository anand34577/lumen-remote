package com.lumen.tvremote.ui.devices

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tvremote.connection.PairingState
import com.lumen.tvremote.ui.LocalHaptics
import com.lumen.tvremote.ui.MainViewModel
import com.lumen.tvremote.ui.components.accentBrush
import com.lumen.tvremote.ui.components.isDarkScheme

private const val CODE_LENGTH = 6

/** Shown while pairing: asks for the code the TV displays and submits it automatically. */
@Composable
fun PairingDialog(vm: MainViewModel, onPaired: () -> Unit) {
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val tv by vm.tvState.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    if (pairing == PairingState.Idle) return

    var code by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val restart: () -> Unit = { code = ""; tv.device?.let(vm.controller::startPairing) }

    LaunchedEffect(pairing) {
        when (val p = pairing) {
            PairingState.Success -> {
                haptics.tick()
                vm.controller.cancelPairing()
                onPaired()
            }
            is PairingState.AwaitingCode -> {
                if (p.error != null) {
                    haptics.heavy()
                    code = ""
                }
                runCatching { focus.requestFocus() }
            }
            else -> Unit
        }
    }

    Dialog(onDismissRequest = { }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dark = isDarkScheme()
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(if (dark) Color(0xFF16161F) else colors.surface)
                .border(1.dp, Color.White.copy(alpha = if (dark) 0.10f else 0.6f), RoundedCornerShape(32.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val failed = pairing is PairingState.Failed
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (failed) colors.error.copy(alpha = 0.16f) else colors.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.Tv,
                    null,
                    tint = if (failed) colors.error else colors.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Pair with ${tv.device?.name ?: "your TV"}",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))

            AnimatedContent(
                targetState = pairing,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "pairing",
            ) { p ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (p) {
                        PairingState.Starting, PairingState.Verifying -> {
                            Text(
                                if (p == PairingState.Starting) "Asking the TV for a code…" else "Checking the code…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(24.dp))
                            CircularProgressIndicator(strokeWidth = 3.dp)
                            Spacer(Modifier.height(12.dp))
                        }
                        is PairingState.AwaitingCode -> {
                            Text(
                                "Type the 6-character code shown on your TV.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(20.dp))
                            CodeInput(code, focus, onDone = { if (code.length == CODE_LENGTH) vm.controller.submitPairingCode(code) }) { value ->
                                code = value
                                if (value.length == CODE_LENGTH) vm.controller.submitPairingCode(value)
                            }
                            p.error?.let {
                                Spacer(Modifier.height(12.dp))
                                Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.height(10.dp))
                            // Some TVs suppress the code popup after an abandoned attempt; a fresh request brings it back.
                            TextAction("No code on TV? Show a new one", colors.primary, onClick = restart)
                        }
                        is PairingState.Failed -> {
                            Text(p.message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                        }
                        else -> Unit
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton("Cancel", primary = false, modifier = Modifier.weight(1f), onClick = vm.controller::cancelPairing)
                when (pairing) {
                    is PairingState.Failed -> DialogButton("Try again", primary = true, modifier = Modifier.weight(1f), onClick = restart)
                    is PairingState.AwaitingCode -> DialogButton(
                        "Pair",
                        primary = true,
                        enabled = code.length == CODE_LENGTH,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.controller.submitPairingCode(code) },
                    )
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, primary: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = CircleShape
    val base = modifier
        .height(50.dp)
        .alpha(if (enabled) 1f else 0.45f)
        .clip(shape)
    val styled = if (primary) base.background(accentBrush())
    else base.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
    Box(styled.clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TextAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun CodeInput(code: String, focus: FocusRequester, onDone: () -> Unit, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = code,
        onValueChange = { raw ->
            val clean = raw.uppercase().filter { it in "0123456789ABCDEF" }.take(CODE_LENGTH)
            if (clean != code) onChange(clean)
        },
        modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp).focusRequester(focus),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(CODE_LENGTH) { i ->
                    val char = code.getOrNull(i)?.toString().orEmpty()
                    val active = i == code.length
                    val filled = char.isNotEmpty()
                    val shape = RoundedCornerShape(14.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(shape)
                            .background(if (filled) colors.primary.copy(alpha = 0.14f) else colors.onSurface.copy(alpha = 0.06f))
                            .then(
                                if (active) Modifier.border(2.dp, accentBrush(), shape)
                                else Modifier.border(1.dp, colors.onSurface.copy(alpha = 0.10f), shape),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(char, fontSize = 24.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    }
                }
            }
        },
    )
}
