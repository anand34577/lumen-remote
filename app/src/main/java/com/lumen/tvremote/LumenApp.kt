package com.lumen.tvremote

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.lumen.tvremote.connection.ClientIdentity
import com.lumen.tvremote.connection.TvController
import com.lumen.tvremote.data.AppStore
import com.lumen.tvremote.discovery.TvDiscovery
import com.lumen.tvremote.service.RemoteService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class LumenApp : Application() {
    lateinit var store: AppStore
        private set
    lateinit var discovery: TvDiscovery
        private set
    lateinit var controller: TvController
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var backgroundDisconnect: Job? = null

    override fun onCreate() {
        super.onCreate()
        store = AppStore(this)
        discovery = TvDiscovery(this)
        controller = TvController(this, store, ClientIdentity(this), discovery)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                backgroundDisconnect?.cancel()
                appScope.launch {
                    if (store.currentSettings().autoConnect) controller.connectToLastIfIdle() else controller.restoreLastDevice()
                }
            }

            override fun onStop(owner: LifecycleOwner) = scheduleBackgroundDisconnect()
        })

        // Keep the notification remote in sync with the setting and connection state.
        appScope.launch {
            combine(store.settings, controller.state) { settings, state ->
                settings.persistentNotification && state.device != null
            }.distinctUntilChanged().collect { show ->
                if (show) RemoteService.start(this@LumenApp) else RemoteService.stop(this@LumenApp)
            }
        }
    }

    /** Releases the connection after a grace period while no UI is visible, unless the notification remote needs it. */
    fun scheduleBackgroundDisconnect() {
        appScope.launch {
            val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (foreground) return@launch
            backgroundDisconnect?.cancel()
            backgroundDisconnect = appScope.launch {
                if (store.currentSettings().persistentNotification) return@launch
                delay(BACKGROUND_GRACE_MS)
                controller.disconnect()
            }
        }
    }

    private companion object {
        const val BACKGROUND_GRACE_MS = 45_000L
    }
}

val Context.app: LumenApp get() = applicationContext as LumenApp
