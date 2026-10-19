package com.lumen.tvremote.ui.customize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tvremote.data.AppShortcut
import com.lumen.tvremote.data.Macro
import com.lumen.tvremote.data.MacroStep
import com.lumen.tvremote.protocol.TvKey
import com.lumen.tvremote.ui.MainViewModel
import com.lumen.tvremote.ui.components.AppTile
import com.lumen.tvremote.ui.components.AuroraBackground
import com.lumen.tvremote.ui.components.glass
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TabRowDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    val macros by vm.macros.collectAsStateWithLifecycle()
    val tv by vm.tvState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editingApp by remember { mutableStateOf<AppShortcut?>(null) }
    var addingApp by remember { mutableStateOf(false) }
    var editingMacro by remember { mutableStateOf<Macro?>(null) }

    AuroraBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        title = { Text("Customize") },
                        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                        actions = {
                            if (tab == 0) {
                                IconButton(onClick = { vm.saveShortcuts(AppShortcut.defaults) }) {
                                    Icon(Icons.Rounded.Restore, contentDescription = "Restore default apps")
                                }
                            }
                        },
                    )
                    PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("App shortcuts") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Macros") })
                    }
                }
            },
            floatingActionButton = {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)))
                        .clickable { if (tab == 0) addingApp = true else editingMacro = Macro(name = "", steps = emptyList()) }
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White)
                    Spacer(Modifier.size(8.dp))
                    Text(if (tab == 0) "Add app" else "New macro", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (tab == 0) {
                    item {
                        Text(
                            "Shortcuts launch apps on the TV directly. Use a package name (e.g. com.netflix.ninja) or a deep link URL. " +
                                "Tip: open an app on the TV, then tap the \"On TV\" chip on the remote to pin it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(shortcuts, key = { _, it -> it.id }) { index, app ->
                        ReorderableRow(
                            title = app.name,
                            subtitle = app.target,
                            leading = { AppTile(app.name, app.target, size = 44.dp) },
                            canMoveUp = index > 0,
                            canMoveDown = index < shortcuts.lastIndex,
                            onMove = { delta -> vm.saveShortcuts(shortcuts.moved(index, delta)) },
                            onClick = { editingApp = app },
                            onDelete = { vm.saveShortcuts(shortcuts.filterNot { it.id == app.id }) },
                        )
                    }
                } else {
                    item {
                        Text(
                            "Macros run a sequence of keys, text, app launches and pauses with one tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(macros, key = { _, it -> it.id }) { index, macro ->
                        ReorderableRow(
                            title = macro.name,
                            subtitle = "${macro.steps.size} step${if (macro.steps.size == 1) "" else "s"}",
                            leading = { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                            canMoveUp = index > 0,
                            canMoveDown = index < macros.lastIndex,
                            onMove = { delta -> vm.saveMacros(macros.moved(index, delta)) },
                            onClick = { editingMacro = macro },
                            onDelete = { vm.saveMacros(macros.filterNot { it.id == macro.id }) },
                        )
                    }
                }
            }
        }
    }

    if (addingApp || editingApp != null) {
        AppDialog(
            initial = editingApp,
            currentApp = tv.currentApp,
            onDismiss = { addingApp = false; editingApp = null },
            onSave = { saved ->
                val list = if (shortcuts.any { it.id == saved.id }) shortcuts.map { if (it.id == saved.id) saved else it } else shortcuts + saved
                vm.saveShortcuts(list)
                addingApp = false
                editingApp = null
            },
        )
    }

    editingMacro?.let { macro ->
        MacroEditor(
            initial = macro,
            shortcuts = shortcuts,
            onDismiss = { editingMacro = null },
            onTest = vm.controller::runMacro,
            onSave = { saved ->
                val list = if (macros.any { it.id == saved.id }) macros.map { if (it.id == saved.id) saved else it } else macros + saved
                vm.saveMacros(list)
                editingMacro = null
            },
        )
    }
}

private fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
    val target = (index + delta).coerceIn(indices)
    if (target == index) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}

@Composable
private fun ReorderableRow(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = leading,
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle, maxLines = 1) },
            trailingContent = {
                Row {
                    IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                    IconButton(onClick = { onMove(1) }, enabled = canMoveDown) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete") }
                }
            },
        )
    }
}

@Composable
private fun AppDialog(initial: AppShortcut?, currentApp: String?, onDismiss: () -> Unit, onSave: (AppShortcut) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var target by remember { mutableStateOf(initial?.target.orEmpty()) }
    val valid = name.isNotBlank() && isValidTarget(target)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add app shortcut" else "Edit shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    target, { target = it.trim() },
                    label = { Text("Package name or link") },
                    placeholder = { Text("com.example.app or https://…") },
                    singleLine = true,
                    isError = target.isNotEmpty() && !isValidTarget(target),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (currentApp != null && initial == null) {
                    TextButton(onClick = { target = currentApp }) { Text("Use app open on TV ($currentApp)") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave((initial ?: AppShortcut(name = "", target = "")).copy(name = name.trim(), target = target.trim()))
            }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val PACKAGE = Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$""")
private fun isValidTarget(t: String) = PACKAGE.matches(t) || Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://\S+$""").matches(t)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MacroEditor(
    initial: Macro,
    shortcuts: List<AppShortcut>,
    onDismiss: () -> Unit,
    onTest: (Macro) -> Unit,
    onSave: (Macro) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    val steps = remember { mutableStateListOf<MacroStep>().apply { addAll(initial.steps) } }
    var addMenu by remember { mutableStateOf(false) }
    var stepDialog by remember { mutableStateOf<StepKind?>(null) }
    val current = { initial.copy(name = name.trim(), steps = steps.toList()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (initial.name.isEmpty()) "New macro" else "Edit macro") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") } },
                    actions = {
                        TextButton(onClick = { onTest(current()) }, enabled = steps.isNotEmpty()) { Text("Test") }
                        TextButton(onClick = { onSave(current()) }, enabled = name.isNotBlank() && steps.isNotEmpty()) { Text("Save") }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    OutlinedTextField(name, { name = it }, label = { Text("Macro name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                itemsIndexed(steps) { index, step ->
                    ReorderableRow(
                        title = "${index + 1}. ${describe(step)}",
                        subtitle = kindLabel(step),
                        leading = {},
                        canMoveUp = index > 0,
                        canMoveDown = index < steps.lastIndex,
                        onMove = { delta ->
                            val target = (index + delta).coerceIn(steps.indices)
                            steps.add(target, steps.removeAt(index))
                        },
                        onClick = {},
                        onDelete = { steps.removeAt(index) },
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { addMenu = true }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Add step")
                        }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            StepKind.entries.forEach { kind ->
                                DropdownMenuItem(text = { Text(kind.label) }, onClick = { addMenu = false; stepDialog = kind })
                            }
                        }
                    }
                }
            }
        }
    }

    stepDialog?.let { kind ->
        StepDialog(kind, shortcuts, onDismiss = { stepDialog = null }) { step ->
            steps.add(step)
            stepDialog = null
        }
    }
}

private enum class StepKind(val label: String) { KEY("Press a key"), TEXT("Type text"), APP("Open an app"), DELAY("Wait") }

private fun kindLabel(step: MacroStep) = when (step) {
    is MacroStep.Key -> if (step.longPress) "Long press" else "Key"
    is MacroStep.Text -> "Text"
    is MacroStep.App -> "App"
    is MacroStep.Delay -> "Wait"
}

private fun describe(step: MacroStep) = when (step) {
    is MacroStep.Key -> step.key.label
    is MacroStep.Text -> "\"${step.text}\""
    is MacroStep.App -> step.link.removePrefix("market://launch?id=")
    is MacroStep.Delay -> "${step.millis} ms"
}

@Composable
private fun StepDialog(kind: StepKind, shortcuts: List<AppShortcut>, onDismiss: () -> Unit, onAdd: (MacroStep) -> Unit) {
    var key by remember { mutableStateOf(TvKey.HOME) }
    var longPress by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var delay by remember { mutableStateOf("500") }
    var menu by remember { mutableStateOf(false) }

    val result: MacroStep? = when (kind) {
        StepKind.KEY -> MacroStep.Key(key, longPress)
        StepKind.TEXT -> text.takeIf { it.isNotEmpty() }?.let { MacroStep.Text(it) }
        StepKind.APP -> text.takeIf { isValidTarget(it) }?.let { t ->
            MacroStep.App(AppShortcut(name = "", target = t).launchLink)
        }
        StepKind.DELAY -> delay.toLongOrNull()?.takeIf { it in 1..MacroStep.MAX_DELAY_MS }?.let { MacroStep.Delay(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kind.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (kind) {
                    StepKind.KEY -> {
                        Column {
                            OutlinedTextField(
                                key.label, {}, readOnly = true, label = { Text("Key") },
                                modifier = Modifier.fillMaxWidth().clickable { menu = true },
                                trailingIcon = { IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.KeyboardArrowDown, "Choose key") } },
                            )
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                TvKey.entries.forEach { k ->
                                    DropdownMenuItem(text = { Text(k.label) }, onClick = { key = k; menu = false })
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Long press", Modifier.weight(1f))
                            Switch(checked = longPress, onCheckedChange = { longPress = it })
                        }
                    }
                    StepKind.TEXT -> OutlinedTextField(text, { text = it }, label = { Text("Text to type") }, modifier = Modifier.fillMaxWidth())
                    StepKind.APP -> {
                        OutlinedTextField(text, { text = it.trim() }, label = { Text("Package name or link") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        shortcuts.take(8).forEach { app ->
                            TextButton(onClick = { text = app.target }) { Text(app.name) }
                        }
                    }
                    StepKind.DELAY -> OutlinedTextField(
                        delay, { delay = it.filter(Char::isDigit).take(5) },
                        label = { Text("Milliseconds (max 30000)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { result?.let(onAdd) }, enabled = result != null) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
