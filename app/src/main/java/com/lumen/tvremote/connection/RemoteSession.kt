package com.lumen.tvremote.connection

import android.util.Log
import com.lumen.tvremote.protocol.ProtoMessage
import com.lumen.tvremote.protocol.ProtoWriter
import com.lumen.tvremote.protocol.RemoteEvent
import com.lumen.tvremote.protocol.RemoteProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/** Why a [RemoteSession] ended. */
sealed interface SessionEnd {
    /** The TV does not recognise this phone's certificate. */
    data object NotPaired : SessionEnd
    data class Unreachable(val reason: String) : SessionEnd
    data class Lost(val reason: String) : SessionEnd
    data object Closed : SessionEnd
}

/** A single connection to the TV's control port. Not reusable: create a new one per attempt. */
class RemoteSession(
    private val identity: ClientIdentity,
    private val host: String,
    private val port: Int,
    private val clientModel: String,
    private val clientVendor: String,
    private val appVersion: String,
) {
    private val outgoing = Channel<ByteArray>(capacity = 128)

    @Volatile private var socket: SSLSocket? = null
    @Volatile private var closedByUs = false
    @Volatile var isReady = false
        private set

    /** Queues a message. Returns false when the session is not ready or the queue is full. */
    fun send(message: ByteArray): Boolean = isReady && outgoing.trySend(message).isSuccess

    fun close() {
        closedByUs = true
        outgoing.close()
        runCatching { socket?.close() }
    }

    /** Connects and processes messages until the connection ends. */
    suspend fun run(onReady: () -> Unit, onEvent: (RemoteEvent) -> Unit): SessionEnd = withContext(Dispatchers.IO) {
        val s = try {
            identity.openSocket(host, port, CONNECT_TIMEOUT_MS)
        } catch (e: SSLException) {
            Log.i(TAG, "Handshake rejected by $host", e)
            return@withContext SessionEnd.NotPaired
        } catch (e: IOException) {
            return@withContext SessionEnd.Unreachable(e.message ?: "Cannot reach TV")
        }
        if (closedByUs) {
            runCatching { s.close() }
            return@withContext SessionEnd.Closed
        }
        socket = s
        s.soTimeout = READ_TIMEOUT_MS
        val out = s.outputStream

        try {
            coroutineScope {
                val writer = launch {
                    try {
                        for (message in outgoing) write(out, message)
                    } catch (e: IOException) {
                        // Closing the socket unblocks the reader, which reports the loss.
                        runCatching { s.close() }
                    }
                }
                try {
                    readLoop(s, out, onReady, onEvent)
                } finally {
                    writer.cancel()
                }
            }
        } catch (e: SocketTimeoutException) {
            if (closedByUs) SessionEnd.Closed else SessionEnd.Lost("The TV stopped responding")
        } catch (e: SSLException) {
            when {
                closedByUs -> SessionEnd.Closed
                !isReady -> SessionEnd.NotPaired
                else -> SessionEnd.Lost(e.message ?: "Secure connection failed")
            }
        } catch (e: IOException) {
            if (closedByUs) SessionEnd.Closed else SessionEnd.Lost(e.message ?: "Connection lost")
        } finally {
            isReady = false
            runCatching { s.close() }
            outgoing.close()
        }
    }

    private fun readLoop(
        s: SSLSocket,
        out: OutputStream,
        onReady: () -> Unit,
        onEvent: (RemoteEvent) -> Unit,
    ): SessionEnd {
        while (true) {
            val frame = ProtoMessage.readFrame(s.inputStream)
            if (frame == null) {
                return when {
                    closedByUs -> SessionEnd.Closed
                    // A TV that does not know our certificate drops the connection before configuring.
                    !isReady -> SessionEnd.NotPaired
                    else -> SessionEnd.Lost("The TV closed the connection")
                }
            }
            val event = RemoteProtocol.decode(frame) ?: continue
            when (event) {
                is RemoteEvent.Configure -> {
                    write(out, RemoteProtocol.configure(clientModel, clientVendor, appVersion))
                    onEvent(event)
                }
                RemoteEvent.SetActive -> {
                    write(out, RemoteProtocol.setActive())
                    if (!isReady) {
                        isReady = true
                        onReady()
                    }
                }
                is RemoteEvent.Ping -> write(out, RemoteProtocol.pingResponse(event.value))
                else -> onEvent(event)
            }
        }
    }

    private fun write(out: OutputStream, message: ByteArray) {
        synchronized(out) {
            out.write(ProtoWriter.frame(message))
            out.flush()
        }
    }

    companion object {
        const val DEFAULT_REMOTE_PORT = 6466
        private const val TAG = "RemoteSession"
        private const val CONNECT_TIMEOUT_MS = 6_000

        /** The TV pings every few seconds; silence beyond this means the link is dead. */
        private const val READ_TIMEOUT_MS = 20_000
    }
}
