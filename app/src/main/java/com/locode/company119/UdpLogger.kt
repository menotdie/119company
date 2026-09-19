package com.locode.company119

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object UdpLogger {
    private val HOSTS = BuildConfig.UDP_LOG_HOSTS.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    private val PORT = BuildConfig.UDP_LOG_PORT
    private val executor = Executors.newSingleThreadExecutor()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile var enabled: Boolean = false

    fun log(tag: String, msg: String) {
        if (!enabled || HOSTS.isEmpty() || PORT <= 0) return
        val line = "[${fmt.format(Date())}][${tag}] $msg"
        executor.execute {
            val bytes = line.toByteArray(Charsets.UTF_8)
            try {
                DatagramSocket().use { s ->
                    for (host in HOSTS) {
                        try {
                            s.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(host), PORT))
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
