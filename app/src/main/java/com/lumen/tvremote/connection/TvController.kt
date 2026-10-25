package com.lumen.tvremote.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.util.Log
import com.lumen.tvremote.BuildConfig
import com.lumen.tvremote.data.AppStore
import com.lumen.tvremote.data.Macro
import com.lumen.tvremote.data.MacroStep
import com.lumen.tvremote.data.TvDevice
import com.lumen.tvremote.discovery.TvDiscovery
import com.lumen.tvremote.protocol.RemoteEvent
import com.lumen.tvremote.protocol.RemoteProtocol
import com.lumen.tvremote.protocol.TvKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.min

sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Connecting : ConnectionStatus
    data object Connected : ConnectionStatus
    data object NeedsPairing : ConnectionStatus
    data class Reconnecting(val attempt: Int, val reason: String) : ConnectionStatus
    data class Offline(val reason: String) : ConnectionStatus
}

data class VolumeInfo(val level: Int, val max: Int, val muted: Boolean)

data class TvState(
    val device: TvDevice? = null,
    val connection: ConnectionStatus = ConnectionStatus.Idle,
    val tvModel: String? = null,
    val powerOn: Boolean? = null,
    val volume: VolumeInfo? = null,
    val currentApp: String? = null,
    /** True while the TV has a text field focused and accepts typed text. */
    val imeActive: Boolean = false,
    val imeText: String = "",
    val imeLabel: String? = null,
    val runningMacro: String? = null,
) {
    val isConnected: Boolean get() = connection == ConnectionStatus.Connected
}

sealed interface PairingState {
    data object Idle : PairingState
    data object Starting : PairingState
    data class AwaitingCode(val error: String? = null) : PairingState
    data object Verifying : PairingState
    data object Success : PairingState
    data class Failed(val message: String) : PairingState
}

sealed interface ControllerEvent {
    data class Message(val text: String) : ControllerEvent
    /** The TV focused a text field; the UI may open the keyboard. */
    data object KeyboardRequested : ControllerEvent
}

/**
 * Owns the connection to the selected TV: connects, re-connects with backoff, heals IP
 * changes via mDNS, pairs, and exposes the TV's live state.
 */
class TvController(
    context: Context,
    private val store: AppStore,
    private val identity: ClientIdentity,
    private val discovery: TvDiscovery,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val control = Mutex()

    private val _state = MutableStateFlow(TvState())
    val state: StateFlow<TvState> = _state.asStateFlow()

    private val _pairing = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairing: StateFlow<PairingState> = _pairing.asStateFlow()

    private val _events = MutableSharedFlow<ControllerEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<ControllerEvent> = _events.asSharedFlow()

    @Volatile private var session: RemoteSession? = null
    private var connectionJob: Job? = null
    private var pairingSession: PairingSession? = null
    private var pairingDevice: TvDevice? = null
    private var macroJob: Job? = null
    private var volumeJob: Job? = null
    private var voiceJob: Job? = null
    private val retryNow = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var imeCounter = 0
    @Volatile private var imeFieldCounter = 0

    init {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        runCatching {
            connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onNetworkAvailable()
            })
        }.onFailure { Log.w(TAG, "Network callback unavailable", it) }
    }

    // region Connection lifecycle

    fun connect(device: TvDevice) {
        scope.launch {
            control.withLock {
                stopConnectionLocked()
                _state.value = TvState(device = device, connection = ConnectionStatus.Connecting, tvModel = device.model)
                store.setLastDevice(device.id)
                connectionJob = scope.launch { connectionLoop(device.id) }
            }
        }
    }

    /** Reconnects to the last used TV if nothing is connected; an unpaired TV is shown as needing pairing. */
    fun connectToLastIfIdle() {
        scope.launch {
            val status = _state.value.connection
            val idle = status is ConnectionStatus.Idle || status is ConnectionStatus.Offline
            if (!idle) return@launch
            val device = _state.value.device ?: lastDevice() ?: return@launch
            if (device.paired) connect(device) else showUnpaired(device)
        }
    }

    /** Shows the last used TV without connecting, so the remote never forgets which TV it controls. */
    fun restoreLastDevice() {
        scope.launch {
            if (_state.value.device != null) return@launch
            val device = lastDevice() ?: return@launch
            if (device.paired) {
                _state.update { if (it.device == null) TvState(device = device, tvModel = device.model) else it }
            } else {
                showUnpaired(device)
            }
        }
    }

    private fun showUnpaired(device: TvDevice) {
        _state.update {
            if (it.device == null || it.device.id == device.id) {
                TvState(device = device, connection = ConnectionStatus.NeedsPairing, tvModel = device.model)
            } else {
                it
            }
        }
    }

    fun retry() {
        val device = _state.value.device ?: return
        if (connectionJob?.isActive == true) retryNow.trySend(Unit) else connect(device)
    }

    fun disconnect() {
        scope.launch {
            control.withLock {
                stopConnectionLocked()
                _state.update { TvState(device = it.device, tvModel = it.tvModel) }
            }
        }
    }

    /** Disconnects and deselects the TV after it was forgotten, then selects another saved TV if any. */
    fun clearSelection() {
        scope.launch {
            control.withLock {
                stopConnectionLocked()
                _state.value = TvState()
            }
            restoreLastDevice()
        }
    }

    private suspend fun stopConnectionLocked() {
        macroJob?.cancel()
        volumeJob?.cancel()
        voiceJob?.cancel()
        session?.close()
        connectionJob?.cancelAndJoin()
        connectionJob = null
        session = null
    }

    private fun onNetworkAvailable() {
        when (_state.value.connection) {
            is ConnectionStatus.Reconnecting -> retryNow.trySend(Unit)
            is ConnectionStatus.Offline -> retry()
            else -> Unit
        }
    }

    private suspend fun connectionLoop(deviceId: String) {
        var attempt = 0
        var notPairedStrikes = 0
        var lastReason = ""
        while (currentCoroutineContext().isActive) {
            val device = store.devices.first().find { it.id == deviceId } ?: run {
                _state.update { TvState() }
                return
            }
            _state.update {
                it.copy(
                    device = device,
                    connection = if (attempt == 0) ConnectionStatus.Connecting else ConnectionStatus.Reconnecting(attempt, lastReason),
                )
            }

            val s = RemoteSession(identity, device.host, device.port, Build.MODEL, Build.MANUFACTURER, BuildConfig.VERSION_NAME)
            session = s
            val end = s.run(
                onReady = {
                    attempt = 0
                    notPairedStrikes = 0
                    _state.update { it.copy(connection = ConnectionStatus.Connected) }
                    scope.launch {
                        store.updateDevice(deviceId) { d -> d.copy(paired = true, lastConnected = System.currentTimeMillis()) }
                    }
                },
                onEvent = ::handleEvent,
            )
            session = null
            _state.update { it.copy(imeActive = false) }

            when (end) {
                SessionEnd.Closed -> return
                SessionEnd.NotPaired -> {
                    notPairedStrikes++
                    // A paired TV may briefly reject during its own restart; give it one more chance.
                    if (!device.paired || notPairedStrikes >= 2) {
                        store.updateDevice(deviceId) { it.copy(paired = false) }
                        _state.update { it.copy(connection = ConnectionStatus.NeedsPairing) }
                        return
                    }
                    lastReason = "Verifying pairing"
                }
                is SessionEnd.Unreachable -> {
                    lastReason = "Can't reach ${device.name}"
                    if (relocate(device)) {
                        attempt = 0
                        continue
                    }
                }
                is SessionEnd.Lost -> lastReason = end.reason
            }

            attempt++
            if (attempt > MAX_ATTEMPTS) {
                _state.update { it.copy(connection = ConnectionStatus.Offline(offlineHint(lastReason))) }
                return
            }
            _state.update { it.copy(connection = ConnectionStatus.Reconnecting(attempt, lastReason)) }
            val backoff = min(1_000L shl (attempt - 1).coerceAtMost(5), 20_000L)
            withTimeoutOrNull(backoff) { retryNow.receive() }
        }
    }

    private fun offlineHint(reason: String) =
        "$reason. Make sure the TV is on and on the same Wi-Fi network."

    /** Looks the TV up again by its mDNS name in case its IP address changed. */
    private suspend fun relocate(device: TvDevice): Boolean {
        val serviceName = device.serviceName ?: return false
        val match = withTimeoutOrNull(RELOCATE_TIMEOUT_MS) {
            discovery.discover().firstOrNull { state -> state.tvs.any { it.serviceName == serviceName } }
        }?.tvs?.find { it.serviceName == serviceName } ?: return false
        if (match.host == device.host) return false
        Log.i(TAG, "${device.name} moved from ${device.host} to ${match.host}")
        store.updateDevice(device.id) { it.copy(host = match.host) }
        return true
    }

    private suspend fun lastDevice(): TvDevice? {
        val devices = store.devices.first()
        val id = store.lastDeviceId.first()
        return devices.find { it.id == id } ?: devices.firstOrNull()
    }

    // endregion

    // region Incoming events

    private fun handleEvent(event: RemoteEvent) {
        when (event) {
            is RemoteEvent.Configure -> {
                val model = listOf(event.vendor, event.model).filter { it.isNotBlank() }.joinToString(" ")
                if (model.isNotBlank()) {
                    _state.update { it.copy(tvModel = model) }
                    _state.value.device?.let { d -> scope.launch { store.updateDevice(d.id) { it.copy(model = model) } } }
                }
            }
            is RemoteEvent.Power -> _state.update { it.copy(powerOn = event.on) }
            is RemoteEvent.Volume -> _state.update {
                it.copy(volume = VolumeInfo(event.level, event.max, event.muted))
            }
            is RemoteEvent.ImeKeyInject -> {
                event.fieldCounter?.let { imeFieldCounter = it }
                _state.update {
                    val appChanged = event.appPackage != null && event.appPackage != it.currentApp
                    it.copy(
                        currentApp = event.appPackage ?: it.currentApp,
                        imeActive = if (appChanged) false else it.imeActive,
                        imeLabel = event.label ?: it.imeLabel,
                    )
                }
            }
            is RemoteEvent.ImeBatchEdit -> {
                val newField = event.fieldCounter != imeFieldCounter || !_state.value.imeActive
                imeCounter = event.imeCounter
                imeFieldCounter = event.fieldCounter
                _state.update { it.copy(imeActive = true, imeText = event.text ?: it.imeText) }
                if (newField) _events.tryEmit(ControllerEvent.KeyboardRequested)
            }
            is RemoteEvent.ImeShow -> {
                _state.update { it.copy(imeActive = true, imeText = event.text ?: "", imeLabel = event.label) }
                _events.tryEmit(ControllerEvent.KeyboardRequested)
            }
            RemoteEvent.Error -> Log.w(TAG, "TV reported a remote error")
            else -> Unit
        }
    }

    // endregion

    // region Commands

    private fun send(message: ByteArray): Boolean {
        val s = session
        if (s != null && s.send(message)) return true
        when (_state.value.connection) {
            ConnectionStatus.Connected, ConnectionStatus.Connecting, is ConnectionStatus.Reconnecting -> Unit
            ConnectionStatus.NeedsPairing -> _events.tryEmit(ControllerEvent.Message("Pair with your TV to control it"))
            else -> if (_state.value.device == null) {
                _events.tryEmit(ControllerEvent.Message("Choose a TV to control first"))
            } else {
                _events.tryEmit(ControllerEvent.Message("Not connected. Reconnecting…"))
                retry()
            }
        }
        return false
    }

    fun sendKey(key: TvKey): Boolean = send(RemoteProtocol.key(key.code))

    /** For widget and notification actions: connects to the last TV first if needed. */
    suspend fun sendKeyWhenReady(key: TvKey, timeoutMs: Long = 7_000): Boolean {
        if (!_state.value.isConnected) {
            if (_state.value.connection is ConnectionStatus.Reconnecting) retry() else connectToLastIfIdle()
            withTimeoutOrNull(timeoutMs) { _state.first { it.isConnected } } ?: return false
        }
        return sendKey(key)
    }

    fun keyDown(key: TvKey): Boolean = send(RemoteProtocol.key(key.code, RemoteProtocol.DIRECTION_START_LONG))

    fun keyUp(key: TvKey): Boolean = send(RemoteProtocol.key(key.code, RemoteProtocol.DIRECTION_END_LONG))

    fun launch(link: String): Boolean = send(RemoteProtocol.appLink(link))

    /** Replaces the focused TV text field's content with [text]. */
    fun setText(text: String): Boolean {
        val ok = send(RemoteProtocol.imeText(imeCounter, imeFieldCounter, text))
        if (ok) _state.update { it.copy(imeText = text) }
        return ok
    }

    /** Power that also wakes TVs whose network sleeps, if a MAC address is configured. */
    fun power() {
        val state = _state.value
        val device = state.device ?: return
        if (state.isConnected) {
            sendKey(TvKey.POWER)
            return
        }
        val mac = device.macAddress
        if (mac == null) {
            retry()
            _events.tryEmit(ControllerEvent.Message("TV not connected. Add its MAC address to wake it over Wi-Fi."))
            return
        }
        scope.launch {
            val sent = WakeOnLan.send(mac, device.host)
            _events.tryEmit(ControllerEvent.Message(if (sent) "Waking ${device.name}…" else "Could not send wake packet"))
            delay(1_500)
            retry()
        }
    }

    /** Steps the volume toward [target], following the TV's reported level. */
    fun setVolume(target: Int) {
        volumeJob?.cancel()
        volumeJob = scope.launch {
            var lastLevel = -1
            var stalled = 0
            repeat(MAX_VOLUME_STEPS) {
                val current = _state.value.volume ?: return@launch
                val diff = target - current.level
                if (diff == 0) return@launch
                if (current.level == lastLevel) stalled++ else stalled = 0
                if (stalled >= 4) return@launch
                lastLevel = current.level
                if (!sendKey(if (diff > 0) TvKey.VOLUME_UP else TvKey.VOLUME_DOWN)) return@launch
                // Wait for the TV to report the new level before deciding the next step.
                withTimeoutOrNull(VOLUME_STEP_TIMEOUT_MS) {
                    _state.first { it.volume?.level != current.level }
                }
                if (abs(diff) > 1) delay(15)
            }
        }
    }

    fun runMacro(macro: Macro) {
        macroJob?.cancel()
        macroJob = scope.launch {
            _state.update { it.copy(runningMacro = macro.name) }
            try {
                for (step in macro.steps) {
                    when (step) {
                        is MacroStep.Key -> if (step.longPress) {
                            keyDown(step.key)
                            try {
                                delay(LONG_PRESS_MS)
                            } finally {
                                // Cancelling mid-press must still release the key, or the TV keeps it held.
                                keyUp(step.key)
                            }
                        } else {
                            sendKey(step.key)
                        }
                        is MacroStep.Text -> setText(step.text)
                        is MacroStep.App -> launch(step.link)
                        is MacroStep.Delay -> delay(step.millis)
                    }
                    delay(MACRO_STEP_GAP_MS)
                }
            } finally {
                _state.update { it.copy(runningMacro = null) }
            }
        }
    }

    fun cancelMacro() {
        macroJob?.cancel()
    }

    /** Types recognised speech into the TV, opening search first when no text field is focused. */
    fun voiceInput(text: String) {
        if (text.isBlank()) return
        voiceJob?.cancel()
        voiceJob = scope.launch {
            if (!_state.value.imeActive) {
                if (!sendKey(TvKey.SEARCH)) return@launch
                val opened = withTimeoutOrNull(VOICE_FIELD_TIMEOUT_MS) { _state.first { it.imeActive } }
                if (opened == null) {
                    _events.tryEmit(ControllerEvent.Message("Open a search box on the TV, then try voice again"))
                    return@launch
                }
            }
            setText(text)
        }
    }

    // endregion

    // region Pairing

    fun startPairing(device: TvDevice) {
        scope.launch {
            control.withLock { stopConnectionLocked() }
            _state.update { it.copy(device = device, connection = ConnectionStatus.NeedsPairing) }
            store.setLastDevice(device.id)
            pairingSession?.close()
            pairingDevice = device
            _pairing.value = PairingState.Starting
            val session = PairingSession(identity, device.host, device.pairingPort, clientName())
            pairingSession = session
            _pairing.value = try {
                session.start()
                PairingState.AwaitingCode()
            } catch (e: Exception) {
                Log.w(TAG, "Pairing start failed", e)
                session.close()
                PairingState.Failed(pairingError(e))
            }
        }
    }

    fun submitPairingCode(code: String) {
        val session = pairingSession ?: return
        val device = pairingDevice ?: return
        scope.launch {
            _pairing.value = PairingState.Verifying
            when (val result = session.submitCode(code)) {
                PairingResult.Success -> {
                    store.upsertDevice(device.copy(paired = true))
                    pairingSession = null
                    _pairing.value = PairingState.Success
                    connect(device.copy(paired = true))
                }
                PairingResult.InvalidCode -> _pairing.value = PairingState.AwaitingCode("That code doesn't match the TV. Check and try again.")
                PairingResult.CodeRejected -> {
                    _pairing.value = PairingState.Starting
                    _pairing.value = try {
                        session.start()
                        PairingState.AwaitingCode("The TV rejected that code. Enter the new code shown on the TV.")
                    } catch (e: Exception) {
                        PairingState.Failed(pairingError(e))
                    }
                }
                is PairingResult.Failed -> _pairing.value = PairingState.Failed(result.message)
            }
        }
    }

    fun cancelPairing() {
        pairingSession?.close()
        pairingSession = null
        pairingDevice = null
        _pairing.value = PairingState.Idle
    }

    private fun clientName(): String = "Lumen Remote (${Build.MODEL})"

    private fun pairingError(e: Exception): String = when (e) {
        is java.net.ConnectException, is java.net.NoRouteToHostException ->
            "Can't reach the TV. Check that it's on and connected to the same network."
        is java.net.SocketTimeoutException -> "The TV didn't respond. Wake the TV and try again."
        is javax.net.ssl.SSLException -> "Secure connection failed. Update \"Android TV Remote Service\" on the TV and retry."
        else -> e.message ?: "Pairing failed"
    }

    // endregion

    private companion object {
        const val TAG = "TvController"
        const val MAX_ATTEMPTS = 8
        const val RELOCATE_TIMEOUT_MS = 6_000L
        const val MAX_VOLUME_STEPS = 100
        const val VOLUME_STEP_TIMEOUT_MS = 600L
        const val LONG_PRESS_MS = 800L
        const val MACRO_STEP_GAP_MS = 120L
        const val VOICE_FIELD_TIMEOUT_MS = 4_000L
    }
}
