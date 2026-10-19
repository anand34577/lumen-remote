package com.lumen.tvremote.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Sends Wake-on-LAN magic packets, for TVs whose network sleeps in standby. */
object WakeOnLan {
    private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")

    fun isValidMac(mac: String): Boolean = MAC_REGEX.matches(mac.trim())

    fun normalize(mac: String): String = mac.trim().replace('-', ':').uppercase()

    suspend fun send(mac: String, host: String?): Boolean = withContext(Dispatchers.IO) {
        if (!isValidMac(mac)) return@withContext false
        val macBytes = normalize(mac).split(':').map { it.toInt(16).toByte() }
        val packet = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (i in 6 until packet.size) packet[i] = macBytes[(i - 6) % 6]

        val targets = buildList {
            add("255.255.255.255")
            // Subnet-directed broadcast reaches TVs on routers that drop limited broadcasts.
            host?.split('.')?.takeIf { it.size == 4 }?.let { add("${it[0]}.${it[1]}.${it[2]}.255") }
            host?.let { add(it) }
        }.distinct()

        var sent = false
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                repeat(3) {
                    for (target in targets) {
                        for (port in intArrayOf(9, 7)) {
                            runCatching {
                                socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(target), port))
                                sent = true
                            }
                        }
                    }
                }
            }
        }
        sent
    }
}
