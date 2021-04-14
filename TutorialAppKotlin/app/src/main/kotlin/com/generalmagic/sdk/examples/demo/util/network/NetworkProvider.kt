/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.util.network

import com.generalmagic.sdk.core.ENetworkType
import com.generalmagic.sdk.core.NetworkListener
import com.generalmagic.sdk.core.NetworkProvider
import com.generalmagic.sdk.core.ProxyDetails
import com.generalmagic.sdk.util.SdkError

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
        https: ProxyDetails,
        http: ProxyDetails
    ) {
        val connectResult = if (type.value < 0) {
            SdkError.NoConnection
        } else SdkError.NoError

        mConnected = (connectResult == SdkError.NoError)
        networkType = type

        var gemNetworkType = ENetworkType.Free
        if (type == NetworkManager.TConnectionType.TYPE_MOBILE) {
            gemNetworkType = ENetworkType.ExtraCharged
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

    fun getNetworkName(connected: Boolean, networkType: ENetworkType): String {
        if (!connected) {
            return "None"
        }

        return when (networkType) {
            ENetworkType.Free -> "Wi-Fi"
            ENetworkType.ExtraCharged -> "Mobile Data"
        }
    }
}
