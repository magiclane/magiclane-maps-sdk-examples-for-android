/*
 * SPDX-FileCopyrightText: 2025-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.testing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/** Utility for network-related checks in instrumented tests. */
object NetworkUtils {

    const val DEFAULT_INTERNET_TIMEOUT_MS = 60_000L
    private const val POLL_INTERVAL_MIN_MS = 250L
    private const val POLL_INTERVAL_MAX_MS = 1_000L

    /** Checks if the device has usable (validated) internet connectivity. */
    fun hasUsableInternet(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                val network = cm.activeNetwork
                val caps = network?.let { cm.getNetworkCapabilities(it) }
                if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
                    return true
                }
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected == true
            }
        } catch (_: SecurityException) {
            // ACCESS_NETWORK_STATE permission may not yet be granted
            // (e.g. when called from @ClassRule before @Rule GrantPermissionRule).
            // Assume internet is available — SDK init will fail on its own if not.
            true
        }
    }

    /** Returns diagnostic info about current network state. */
    fun getNetworkDiagnostics(context: Context): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return "ConnectivityManager unavailable"

        return try {
            val network = cm.activeNetwork ?: return "No active network (API ${Build.VERSION.SDK_INT})"
            val caps = cm.getNetworkCapabilities(network)
                ?: return "Network exists but capabilities unavailable"

            buildString {
                val transports = mutableListOf<String>()
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports.add("WIFI")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports.add("CELLULAR")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports.add("ETHERNET")
                appendLine("Transport: ${transports.ifEmpty { listOf("UNKNOWN") }.joinToString()}")

                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                append("INTERNET: $hasInternet")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    append(", VALIDATED: $validated")
                }
            }
        } catch (_: SecurityException) {
            "ACCESS_NETWORK_STATE permission not yet granted"
        }
    }

    /** Waits for usable internet with exponential backoff polling. */
    fun awaitUsableInternet(
        context: Context,
        timeoutMs: Long = DEFAULT_INTERNET_TIMEOUT_MS
    ): Boolean {
        if (hasUsableInternet(context)) return true

        val startTime = System.currentTimeMillis()
        var sleepMs = POLL_INTERVAL_MIN_MS

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Thread.sleep(sleepMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
            if (hasUsableInternet(context)) return true
            sleepMs = (sleepMs * 2).coerceAtMost(POLL_INTERVAL_MAX_MS)
        }
        return hasUsableInternet(context)
    }

    /** Asserts usable internet is available, waiting if necessary. */
    fun assertInternetAvailable(
        context: Context,
        timeoutMs: Long = DEFAULT_INTERNET_TIMEOUT_MS
    ) {
        if (!awaitUsableInternet(context, timeoutMs)) {
            throw AssertionError(
                """
                |
                |------------------------------------------------------------------
                |No usable internet after ${timeoutMs}ms.
                |${getNetworkDiagnostics(context)}
                |------------------------------------------------------------------
                |
                """.trimMargin()
            )
        }
    }
}
