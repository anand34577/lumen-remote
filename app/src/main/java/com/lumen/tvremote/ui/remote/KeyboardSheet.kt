package com.lumen.tvremote.ui.remote

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.lumen.tvremote.connection.TvState
import com.lumen.tvremote.protocol.TvKey

private const val ECHO_WINDOW_MS = 1_000L

/**
 * Live keyboard: every edit is mirrored to the focused TV text field, so typing, autocorrect,
 * paste and the phone's own voice typing all land on the TV instantly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSheet(
    state: TvState,
    onTextChange: (String) -> Unit,
    onKey: (TvKey) -> Unit,
    onVoice: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    var field by remember { mutableStateOf(TextFieldValue(state.imeText, TextRange(state.imeText.length))) }

    var lastLocalEdit by remember { mutableLongStateOf(0L) }

    // Follow edits made on the TV side (e.g. a suggestion picked with the D-pad), but ignore
    // late echoes of our own typing so fast input is never rolled back.
    LaunchedEffect(state.imeText) {
        val quiet = System.currentTimeMillis() - lastLocalEdit > ECHO_WINDOW_MS
        if (quiet && state.imeText != field.text) field = TextFieldValue(state.imeText, TextRange(state.imeText.length))
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun send(text: String) {
        lastLocalEdit = System.currentTimeMillis()
        onTextChange(text)
    }

    fun update(text: String) {
        field = TextFieldValue(text, TextRange(text.length))
        send(text)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp),
        ) {
            Text("Type on TV", style = MaterialTheme.typography.titleLarge)
            Text(
                state.imeLabel?.takeIf { it.isNotBlank() } ?: "Text appears on your TV as you type",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.imeActive) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Select a search or text box on the TV first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = field,
                onValueChange = { value ->
                    val changed = value.text != field.text
                    field = value
                    if (changed) send(value.text)
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("Search, URL, password…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onAny = { onKey(TvKey.ENTER) }),
                trailingIcon = {
                    if (field.text.isNotEmpty()) {
                        IconButton(onClick = { update("") }) { Icon(Icons.Rounded.Clear, contentDescription = "Clear") }
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FilledTonalIconButton(onClick = {
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                    val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!pasted.isNullOrEmpty()) update(field.text + pasted)
                }) { Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste") }
                FilledTonalIconButton(onClick = onVoice) { Icon(Icons.Rounded.Mic, contentDescription = "Voice") }
                FilledTonalIconButton(onClick = {
                    if (field.text.isNotEmpty()) update(field.text.dropLast(1)) else onKey(TvKey.DEL)
                }) { Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Backspace") }
                FilledTonalIconButton(onClick = { onKey(TvKey.ENTER) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, contentDescription = "Enter")
                }
            }
        }
    }
}
