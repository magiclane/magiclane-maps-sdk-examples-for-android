/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.wifiserver1

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.magiclane.sdk.core.TAG
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * WiFi counterpart of the BLE example's GATT server.
 *
 * Opens a TCP server socket on a system-assigned port and advertises it on the local network
 * via Network Service Discovery (DNS-SD) — the WiFi analog of BLE advertising — so clients can
 * find this device without knowing its IP address. Connected clients receive navigation data
 * as newline-delimited JSON messages (see [NavProtocol]).
 */
class NavWifiServer(
    private val context: Context,
    /** Called on a background thread to obtain the messages that bring a new client up to date. */
    private val snapshotProvider: () -> List<String>,
    /** Called on a background thread whenever the number of connected clients changes. */
    private val onClientCountChanged: (Int) -> Unit,
) {
    private class Client(val socket: Socket, val writer: BufferedWriter)

    private val clients = CopyOnWriteArrayList<Client>()

    /** Single-threaded so messages reach every client in the order they were produced. */
    private var sendExecutor: ExecutorService? = null

    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    /** The port the server socket is bound to; 0 until [start] succeeds. */
    var localPort = 0
        private set

    val isRunning: Boolean
        get() = serverSocket?.isClosed == false

    /**
     * Starts the TCP server and the DNS-SD advertisement.
     * @throws IOException if the server socket cannot be opened.
     */
    fun start() {
        if (isRunning) return

        val socket = ServerSocket(0) // 0 = let the system pick a free port.
        serverSocket = socket
        localPort = socket.localPort
        sendExecutor = Executors.newSingleThreadExecutor()

        registerNsdService(localPort)

        Thread({ acceptLoop(socket) }, "NavWifiServer-accept").start()
        Log.i(TAG, "WiFi navigation server listening on port $localPort")
    }

    /** Stops the advertisement, the server socket and all client connections. */
    fun stop() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "NSD service was not registered: $e")
            }
            registrationListener = null
        }

        serverSocket?.closeSilently()
        serverSocket = null

        clients.forEach { it.socket.closeSilently() }
        clients.clear()

        sendExecutor?.shutdownNow()
        sendExecutor = null
    }

    /** Queues a message for delivery to every connected client. */
    fun broadcast(message: String) {
        sendExecutor?.execute {
            for (client in clients) {
                client.send(message)
            }
        }
    }

    // ---- TCP -----------------------------------------------------------------

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val clientSocket = try {
                socket.accept()
            } catch (_: IOException) {
                // The socket was closed by stop(); leave the loop.
                break
            }

            val client = try {
                clientSocket.tcpNoDelay = true
                Client(clientSocket, BufferedWriter(OutputStreamWriter(clientSocket.getOutputStream())))
            } catch (e: IOException) {
                Log.w(TAG, "Failed to open client stream: $e")
                clientSocket.closeSilently()
                continue
            }

            clients.add(client)
            onClientCountChanged(clients.size)
            Log.i(TAG, "Client connected: ${clientSocket.inetAddress?.hostAddress}")

            // Bring the new client up to date, like the BLE server does when a
            // client subscribes to notifications.
            sendExecutor?.execute {
                snapshotProvider().forEach { client.send(it) }
            }

            // The protocol is one-way, so the only purpose of this reader thread is to detect
            // when the client goes away (read returns -1 or throws).
            Thread({ watchForDisconnect(client) }, "NavWifiServer-client").start()
        }
    }

    private fun watchForDisconnect(client: Client) {
        try {
            val input = client.socket.getInputStream()
            while (input.read() != -1) {
                // Ignore any data the client sends.
            }
        } catch (_: IOException) {
            // Fall through to the cleanup below.
        }
        removeClient(client)
    }

    private fun Client.send(message: String) {
        try {
            writer.write(message)
            writer.newLine()
            writer.flush()
        } catch (e: IOException) {
            Log.i(TAG, "Client write failed, dropping client: $e")
            removeClient(this)
        }
    }

    private fun removeClient(client: Client) {
        if (clients.remove(client)) {
            client.socket.closeSilently()
            onClientCountChanged(clients.size)
            Log.i(TAG, "Client disconnected: ${client.socket.inetAddress?.hostAddress}")
        }
    }

    private fun ServerSocket.closeSilently() = try {
        close()
    } catch (_: IOException) {
    }

    private fun Socket.closeSilently() = try {
        close()
    } catch (_: IOException) {
    }

    // ---- DNS-SD advertisement --------------------------------------------------

    private val nsdManager: NsdManager
        get() = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private fun registerNsdService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            // NSD appends a suffix automatically if another device already uses this name.
            serviceName = "Magic Lane Nav 1 (${Build.MODEL})"
            serviceType = NavProtocol.SERVICE_TYPE
            setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "NSD service registered as ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "NSD service unregistered")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD unregistration failed: $errorCode")
            }
        }

        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }
}
