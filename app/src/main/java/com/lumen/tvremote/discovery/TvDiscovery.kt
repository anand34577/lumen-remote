package com.lumen.tvremote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class DiscoveredTv(val serviceName: String, val host: String, val port: Int)

data class DiscoveryState(
    val tvs: List<DiscoveredTv> = emptyList(),
    val error: String? = null,
)

/** Finds TVs advertising the Android TV Remote v2 service via mDNS. */
class TvDiscovery(context: Context) {
    private val nsd = context.applicationContext.getSystemService(NsdManager::class.java)

    fun discover(): Flow<DiscoveryState> = callbackFlow {
        val found = ConcurrentHashMap<String, DiscoveredTv>()
        val executor = Executors.newSingleThreadExecutor()
        val pendingResolves = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        val infoCallbacks = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()

        fun publish(error: String? = null) {
            trySend(DiscoveryState(found.values.sortedBy { it.serviceName.lowercase() }, error))
        }

        fun record(name: String, addresses: List<InetAddress>, port: Int) {
            val address = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull() ?: return
            val host = address.hostAddress ?: return
            found[name] = DiscoveredTv(name, host.substringBefore('%'), port)
            publish()
        }

        // Pre-Android 14 NsdManager can only resolve one service at a time.
        fun resolveNext() {
            synchronized(pendingResolves) {
                if (resolving) return
                val next = pendingResolves.removeFirstOrNull() ?: return
                resolving = true
                @Suppress("DEPRECATION")
                nsd.resolveService(next, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        synchronized(pendingResolves) {
                            resolving = false
                            if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) pendingResolves.addLast(info)
                        }
                        // The flow may have closed (and the executor shut down) while this callback was in flight.
                        runCatching { executor.execute { Thread.sleep(150); resolveNext() } }
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        info.host?.let { record(info.serviceName, listOf(it), info.port) }
                        synchronized(pendingResolves) { resolving = false }
                        resolveNext()
                    }
                })
            }
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun watch(info: NsdServiceInfo) {
            if (infoCallbacks.containsKey(info.serviceName)) return
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    infoCallbacks.remove(info.serviceName)
                }

                override fun onServiceUpdated(updated: NsdServiceInfo) =
                    record(updated.serviceName, updated.hostAddresses, updated.port)

                override fun onServiceLost() {
                    found.remove(info.serviceName)
                    publish()
                }

                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            infoCallbacks[info.serviceName] = callback
            runCatching { nsd.registerServiceInfoCallback(info, executor, callback) }
                .onFailure { infoCallbacks.remove(info.serviceName) }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = publish()
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                publish("Network discovery failed (code $errorCode). You can still add your TV by IP address.")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    watch(info)
                } else {
                    synchronized(pendingResolves) { pendingResolves.addLast(info) }
                    resolveNext()
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                if (found.remove(info.serviceName) != null) publish()
            }
        }

        publish()
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "discoverServices failed", e)
            publish("Network discovery is unavailable. You can still add your TV by IP address.")
        }

        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                infoCallbacks.values.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
            }
            executor.shutdown()
        }
    }

    private companion object {
        const val TAG = "TvDiscovery"
        const val SERVICE_TYPE = "_androidtvremote2._tcp."
    }
}
