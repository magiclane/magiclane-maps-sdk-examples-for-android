/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.util.network

import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.util.GEMError
import java.net.Proxy

class NetworkProviderImpl : NetworkProvider() {
    private var networkListener: NetworkListener? = null
    private var mConnected = false
    private var networkType = NetworkManager.TConnectionType.TYPE_NOT_CONNECTED
    private var listeners = ArrayList<NetworkListener>()
    override fun connectionError(host: String) {
    }

    override fun setListener(listener: NetworkListener?) {
        val pastNetworkListener = networkListener
        if (pastNetworkListener != null) {
            listeners.remove(pastNetworkListener)
        }

        networkListener = listener
        if (listener != null) {
            listeners.add(listener)
        }
    }

    fun addListener(listener: NetworkListener) {
        if (listeners.contains(listener)) {
            return
        }
        listeners.add(listener)
    }

    fun removeListener(listener: NetworkListener) {
        listeners.remove(listener)
    }

    fun onNetworkConnectionTypeChanged(
        type: NetworkManager.TConnectionType,
        https: TProxyDetails,
        http: TProxyDetails
    ) {
        val connectResult = if (type.value < 0) {
            GEMError.KNoConnection
        } else GEMError.KNoError

        mConnected = (connectResult == GEMError.KNoError)
        networkType = type

        var gemNetworkType = TNetworkType.EFree
        if (type == NetworkManager.TConnectionType.TYPE_MOBILE) {
            gemNetworkType = TNetworkType.EExtraCharged
        }

        for (listener in listeners) {
            listener.onConnectFinished(
                connectResult.value,
                gemNetworkType,
                https,
                http
            )
        }
    }

    fun onNetworkFailed(reason: Int) {
        for (listener in listeners) {
            listener.onNetworkFailed(reason)
        }
    }

    fun mobileCountryCodeChanged(mcc: Int) {
        for (listener in listeners) {
            listener.onMobileCountryCodeChanged(mcc)
        }
    }

    fun isConnected(): Boolean {
        return mConnected && (networkType == NetworkManager.TConnectionType.TYPE_WIFI || networkType == NetworkManager.TConnectionType.TYPE_MOBILE)
    }

    fun isWifiConnected(): Boolean {
        return mConnected && networkType == NetworkManager.TConnectionType.TYPE_WIFI
    }

    fun isMobileDataConnected(): Boolean {
        return mConnected && networkType == NetworkManager.TConnectionType.TYPE_MOBILE
    }

    fun getNetworkName(connected: Boolean, networkType: TNetworkType): String {
        if (!connected) {
            return "None"
        }

        return when (networkType) {
            TNetworkType.EFree -> "Wi-Fi"
            TNetworkType.EExtraCharged -> "Mobile Data"
        }
    }
}
