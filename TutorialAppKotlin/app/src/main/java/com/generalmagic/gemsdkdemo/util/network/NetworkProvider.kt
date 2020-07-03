// Copyright (C) 2019-2020, General Magic B.V.
// All rights reserved.
//
// This software is confidential and proprietary information of General Magic
// ("Confidential Information"). You shall not disclose such Confidential
// Information and shall use it only in accordance with the terms of the
// license agreement you entered into with General Magic.

package com.generalmagic.gemsdkdemo.util.network

import com.generalmagic.gemsdk.NetworkListener
import com.generalmagic.gemsdk.NetworkProvider
import com.generalmagic.gemsdk.TNetworkType
import com.generalmagic.gemsdk.TProxyType
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
		if (pastNetworkListener != null)
			listeners.remove(pastNetworkListener)

		networkListener = listener
		if (listener != null)
			listeners.add(listener)
	}

	fun addListener(listener: NetworkListener) {
		if (listeners.contains(listener))
			return
		listeners.add(listener)
	}

	fun removeListener(listener: NetworkListener) {
		listeners.remove(listener)
	}

	fun onNetworkConnectionTypeChanged(
		type: NetworkManager.TConnectionType,
		proxyType: Proxy.Type,
		proxyHost: String = "",
		proxyPort: Int = -1
	) {
		val connectResult = if (type.value < 0)
			GEMError.KNoConnection
		else GEMError.KNoError

		mConnected = (connectResult == GEMError.KNoError)
		networkType = type

		var gemNetworkType = TNetworkType.EFree
		if (type == NetworkManager.TConnectionType.TYPE_MOBILE) {
			gemNetworkType = TNetworkType.EExtraCharged
		}

		val gemProxyType = when (proxyType) {
			Proxy.Type.DIRECT -> TProxyType.EDirect
			Proxy.Type.HTTP -> TProxyType.EHttp
			Proxy.Type.SOCKS -> TProxyType.ESocks
		}

		for (listener in listeners) {
			listener.onConnectFinished(
				connectResult.value,
				gemNetworkType,
				gemProxyType,
				proxyHost,
				proxyPort
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
			listener.mobileCountryCodeChanged(mcc)
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

	fun getNetworkName(connected: Boolean, networkType: NetworkManager.TConnectionType): String {
		if (!connected)
			return "None"

		return when (networkType) {
			NetworkManager.TConnectionType.TYPE_NOT_CONNECTED -> "NOT CONNECTED"
			NetworkManager.TConnectionType.TYPE_WIFI -> "Wi-Fi"
			NetworkManager.TConnectionType.TYPE_MOBILE -> "Mobile Data"
			NetworkManager.TConnectionType.TYPE_ETHERNET -> "Ethernet"
		}
	}
}
