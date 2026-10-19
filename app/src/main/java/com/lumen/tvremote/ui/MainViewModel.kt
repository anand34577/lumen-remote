package com.lumen.tvremote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.tvremote.app
import com.lumen.tvremote.connection.WakeOnLan
import com.lumen.tvremote.data.AppSettings
import com.lumen.tvremote.data.AppShortcut
import com.lumen.tvremote.data.Macro
import com.lumen.tvremote.data.TvDevice
import com.lumen.tvremote.discovery.DiscoveredTv
import com.lumen.tvremote.discovery.DiscoveryState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val lumen = application.app
    private val store = lumen.store
    val controller = lumen.controller

    val tvState = controller.state
    val pairing = controller.pairing
    val events = controller.events

    val settings: StateFlow<AppSettings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val devices: StateFlow<List<TvDevice>?> = store.devices.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val shortcuts: StateFlow<List<AppShortcut>> = store.shortcuts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val macros: StateFlow<List<Macro>> = store.macros.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Scans only while a collector (the devices screen) is active. */
    val discovery: StateFlow<DiscoveryState> = lumen.discovery.discover()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), DiscoveryState())

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { store.updateSettings(transform) }
    }

    /** Selects a TV: connects if paired, otherwise starts pairing. */
    fun select(device: TvDevice) {
        if (device.paired) controller.connect(device) else controller.startPairing(device)
    }

    fun addDiscovered(tv: DiscoveredTv) {
        viewModelScope.launch {
            val existing = store.devices.first().find { it.serviceName == tv.serviceName || it.host == tv.host }
            val device = existing?.copy(host = tv.host, serviceName = tv.serviceName)
                ?: TvDevice(name = tv.serviceName, host = tv.host, serviceName = tv.serviceName)
            store.upsertDevice(device)
            select(device)
        }
    }

    fun addManual(name: String, host: String, mac: String?) {
        viewModelScope.launch {
            val device = TvDevice(
                name = name.ifBlank { host },
                host = host.trim(),
                macAddress = mac?.takeIf { WakeOnLan.isValidMac(it) }?.let(WakeOnLan::normalize),
            )
            store.upsertDevice(device)
            select(device)
        }
    }

    fun saveDevice(device: TvDevice) {
        viewModelScope.launch {
            store.upsertDevice(device)
            val current = tvState.value.device
            if (current?.id == device.id && current.host != device.host) controller.connect(device)
        }
    }

    fun forget(device: TvDevice) {
        viewModelScope.launch {
            store.removeDevice(device.id)
            if (tvState.value.device?.id == device.id) controller.clearSelection()
        }
    }

    fun saveShortcuts(list: List<AppShortcut>) {
        viewModelScope.launch { store.saveShortcuts(list) }
    }

    fun saveMacros(list: List<Macro>) {
        viewModelScope.launch { store.saveMacros(list) }
    }

    fun pinCurrentApp(packageName: String) {
        if (shortcuts.value.any { it.target == packageName }) return
        saveShortcuts(shortcuts.value + AppShortcut(name = AppShortcut.labelFor(packageName), target = packageName))
    }
}
