/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.wificlient

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import org.json.JSONException
import org.json.JSONObject

/**
 * Service for managing the TCP connection and data communication with a WiFiServer discovered
 * on the local network. It is the WiFi counterpart of the BLE example's `BLEService`: connection
 * state changes and received navigation data are delivered to the UI via broadcasts.
 */
class WiFiService : Service() {

    @Volatile
    private var socket: Socket? = null

    inner class LocalBinder : Binder() {
        val service: WiFiService
            get() = this@WiFiService
    }

    private val binder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder = binder

    override fun onUnbind(intent: Intent): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    /**
     * Connects to the WiFiServer at the given address. The result and all further updates are
     * reported asynchronously via [ACTION_CONNECTED], [ACTION_DISCONNECTED] and
     * [ACTION_DATA_AVAILABLE] broadcasts.
     */
    fun connect(host: String, port: Int) {
        disconnect()

        Thread({
            val newSocket = Socket()
            try {
                newSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                newSocket.tcpNoDelay = true
            } catch (e: IOException) {
                Log.w(TAG, "Failed to connect to $host:$port: $e")
                newSocket.closeSilently()
                broadcastUpdate(ACTION_DISCONNECTED)
                return@Thread
            }

            socket = newSocket
            Log.i(TAG, "Connected to $host:$port")
            broadcastUpdate(ACTION_CONNECTED)

            readLoop(newSocket)

            newSocket.closeSilently()
            broadcastUpdate(ACTION_DISCONNECTED)
        }, "WiFiService-connection").start()
    }

    /** Closes the current connection, which also ends the background read loop. */
    fun disconnect() {
        socket?.closeSilently()
        socket = null
    }

    /** Reads newline-delimited JSON messages until the server closes or [disconnect] is called. */
    private fun readLoop(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue

                try {
                    handleMessage(JSONObject(line))
                } catch (e: JSONException) {
                    Log.w(TAG, "Skipping malformed message: $e")
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "Connection closed: $e")
        }
    }

    private fun handleMessage(message: JSONObject) {
        // Explicitly target this app: since Android 14, implicit broadcasts are not delivered
        // to receivers registered with RECEIVER_NOT_EXPORTED.
        val intent = Intent(ACTION_DATA_AVAILABLE).setPackage(packageName)

        when (message.optString(NavProtocol.KEY_TYPE)) {
            NavProtocol.TYPE_INSTRUCTION -> {
                intent.putExtra(EXTRA_TYPE, DATA_TYPE_INSTRUCTION)
                intent.putExtra(EXTRA_DATA, message.optString(NavProtocol.KEY_TEXT))
            }

            NavProtocol.TYPE_DISTANCE -> {
                intent.putExtra(EXTRA_TYPE, DATA_TYPE_DISTANCE)
                intent.putExtra(EXTRA_DATA, message.optString(NavProtocol.KEY_TEXT))
            }

            NavProtocol.TYPE_TURN -> {
                intent.putExtra(EXTRA_TYPE, DATA_TYPE_TURN)
                // Same 4-byte layout the BLE example pair uses for its TURN_IMAGE characteristic.
                intent.putExtra(
                    EXTRA_DATA,
                    byteArrayOf(
                        message.optInt(NavProtocol.KEY_EVENT).toByte(),
                        message.optInt(NavProtocol.KEY_ENTRANCE).toByte(),
                        message.optInt(NavProtocol.KEY_EXIT).toByte(),
                        message.optInt(NavProtocol.KEY_DRIVE_SIDE).toByte(),
                    ),
                )
            }

            NavProtocol.TYPE_ROUTE -> {
                intent.putExtra(EXTRA_TYPE, DATA_TYPE_ROUTE)
                // [remaining travel time in seconds, remaining travel distance in meters]
                intent.putExtra(
                    EXTRA_DATA,
                    intArrayOf(
                        message.optInt(NavProtocol.KEY_REMAINING_TIME, -1),
                        message.optInt(NavProtocol.KEY_REMAINING_DISTANCE, -1),
                    ),
                )
            }

            NavProtocol.TYPE_SPEED -> {
                intent.putExtra(EXTRA_TYPE, DATA_TYPE_SPEED)
                intent.putExtra(EXTRA_DATA, message.optDouble(NavProtocol.KEY_SPEED, -1.0))
            }

            else -> return
        }

        sendBroadcast(intent)
    }

    private fun broadcastUpdate(action: String) {
        // Explicitly target this app: since Android 14, implicit broadcasts are not delivered
        // to receivers registered with RECEIVER_NOT_EXPORTED.
        sendBroadcast(Intent(action).setPackage(packageName))
    }

    private fun Socket.closeSilently() = try {
        close()
    } catch (_: IOException) {
    }

    companion object {
        private const val TAG = "WiFiService"

        private const val CONNECT_TIMEOUT_MS = 5000

        const val ACTION_CONNECTED =
            "com.magiclane.sdk.examples.wificlient.ACTION_CONNECTED"
        const val ACTION_DISCONNECTED =
            "com.magiclane.sdk.examples.wificlient.ACTION_DISCONNECTED"
        const val ACTION_DATA_AVAILABLE =
            "com.magiclane.sdk.examples.wificlient.ACTION_DATA_AVAILABLE"

        const val EXTRA_DATA = "com.magiclane.sdk.examples.wificlient.EXTRA_DATA"
        const val EXTRA_TYPE = "com.magiclane.sdk.examples.wificlient.EXTRA_TYPE"

        const val DATA_TYPE_INSTRUCTION = 0
        const val DATA_TYPE_DISTANCE = 1
        const val DATA_TYPE_TURN = 2
        const val DATA_TYPE_ROUTE = 3
        const val DATA_TYPE_SPEED = 4
    }
}
