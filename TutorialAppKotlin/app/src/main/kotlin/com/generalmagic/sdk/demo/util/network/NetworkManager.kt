/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.demo.util.network

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.generalmagic.sdk.core.EProxyType
import com.generalmagic.sdk.core.ProxyDetails
import com.generalmagic.sdk.util.GEMSdkCall
import java.net.Proxy

class NetworkManager(val context: Context) {

    private var mConnectionType =
        TConnectionType.TYPE_NOT_CONNECTED
    private var connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateConnection()
        }
    }
    private lateinit var connectivityManagerCallback: ConnectivityManager.NetworkCallback

    var onConnectionTypeChangedCallback: (
        type: TConnectionType, https: ProxyDetails, http: ProxyDetails
    ) -> Unit = { _, _, _ -> }

    fun finalize() {
        release()
    }

    private fun release() {
        uninitialize()
    }

    init {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                connectivityManager.registerDefaultNetworkCallback(
                    getConnectivityManagerCallback()
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                lollipopNetworkAvailableRequest()
            }

            else -> {
                context.registerReceiver(
                    networkReceiver,
                    IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
                )
            }
        }
    }

    private fun getConnectionType(): TConnectionType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        var connectionType =
            TConnectionType.TYPE_NOT_CONNECTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm?.run {
                cm.getNetworkCapabilities(cm.activeNetwork)?.run {
                    connectionType = when {
                        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TConnectionType.TYPE_WIFI
                        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TConnectionType.TYPE_MOBILE
                        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TConnectionType.TYPE_ETHERNET
                        else -> TConnectionType.TYPE_NOT_CONNECTED
                    }
                }
            }
        } else {
            cm?.run {
                cm.activeNetworkInfo?.run {
                    if (type == ConnectivityManager.TYPE_WIFI) {
                        connectionType =
                            TConnectionType.TYPE_WIFI
                    } else if (type == ConnectivityManager.TYPE_MOBILE) {
                        connectionType =
                            TConnectionType.TYPE_MOBILE
                    }
                }
            }
        }

        return connectionType
    }

    private fun uninitialize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectivityManager.unregisterNetworkCallback(connectivityManagerCallback)
        } else {
            context.unregisterReceiver(networkReceiver)
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun lollipopNetworkAvailableRequest() {
        val builder =
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)

        connectivityManager.registerNetworkCallback(
            builder.build(),
            getConnectivityManagerCallback()
        )
    }

    private fun getConnectivityManagerCallback(): ConnectivityManager.NetworkCallback {
        connectivityManagerCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateConnection()
            }

            override fun onLost(network: Network) {
                updateConnection()
            }
        }
        return connectivityManagerCallback
    }

    private fun updateConnection() {
        val actualConnectionType = getConnectionType()
        if (actualConnectionType != mConnectionType) {

            mConnectionType = actualConnectionType

            var httpsProxyConfig: ProxyConfiguration? = null
            var httpProxyConfig: ProxyConfiguration? = null

            var config: ProxyConfiguration? = null
            if (mConnectionType != TConnectionType.TYPE_NOT_CONNECTED) {
                httpsProxyConfig = ProxyUtils.getProxyConfiguration(true)
                httpProxyConfig = ProxyUtils.getProxyConfiguration(false)
            }

            var connectionType = mConnectionType
            if (mConnectionType == TConnectionType.TYPE_ETHERNET) {
                connectionType = TConnectionType.TYPE_WIFI
            }

            val https = ProxyDetails()
            https.setProxyType(
                when (httpsProxyConfig?.proxyType ?: Proxy.Type.DIRECT) {
                    Proxy.Type.DIRECT -> EProxyType.EDirect
                    Proxy.Type.HTTP -> EProxyType.EHttp
                    Proxy.Type.SOCKS -> EProxyType.ESocks
                }
            )
            https.setProxyName(httpsProxyConfig?.proxyHost ?: "")
            https.setProxyPort(httpsProxyConfig?.proxyPort ?: -1)

            val http = ProxyDetails()
            https.setProxyType(
                when (httpProxyConfig?.proxyType ?: Proxy.Type.DIRECT) {
                    Proxy.Type.DIRECT -> EProxyType.EDirect
                    Proxy.Type.HTTP -> EProxyType.EHttp
                    Proxy.Type.SOCKS -> EProxyType.ESocks
                }
            )
            https.setProxyName(httpProxyConfig?.proxyHost ?: "")
            https.setProxyPort(httpProxyConfig?.proxyPort ?: -1)

            GEMSdkCall.execute {
                onConnectionTypeChangedCallback(connectionType, https, http)
            }
        }
    }

    enum class TConnectionType(val value: Int) {
        TYPE_NOT_CONNECTED(-1),
        TYPE_WIFI(0),
        TYPE_MOBILE(1),
        TYPE_ETHERNET(2);

        companion object {
            fun fromInt(value: Int) = values().first { it.value == value }
        }
    }
}
