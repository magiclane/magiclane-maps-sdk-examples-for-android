/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.util.network

// -------------------------------------------------------------------------------------------------

import android.os.Build
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

// -------------------------------------------------------------------------------------------------

object ProxyUtils {
    // ---------------------------------------------------------------------------------------------

    private val sdkInt = Build.VERSION.SDK_INT

    // ---------------------------------------------------------------------------------------------

    fun getProxyConfiguration(bWiFi: Boolean): ProxyConfiguration? {
        if (bWiFi) {
            return getWifiProxyConfiguration()
        }

        return getMobileDataProxyConfiguration(URI.create("https://www.generalmagic.com"))
    }

    // ---------------------------------------------------------------------------------------------

    private fun getWifiProxyConfiguration(): ProxyConfiguration? {
        var proxyAddress = ""
        var proxyPort = -1

        if (sdkInt >= 14) {
            try {
                proxyAddress = System.getProperty("http.proxyHost") ?: ""
                proxyPort = (System.getProperty("http.proxyPort") ?: "-1").toInt()
            } catch (e: Exception) {
            }
        }

        return if (proxyAddress.isNotEmpty() || proxyPort != -1) {
            ProxyConfiguration(proxyAddress, proxyPort, Proxy.Type.DIRECT)
        } else null
    }

    // ---------------------------------------------------------------------------------------------

    @Throws(Exception::class)
    fun getMobileDataProxyConfiguration(uri: URI?): ProxyConfiguration? {
        if (sdkInt >= 12) {
            return getProxySelectorConfiguration(uri) as ProxyConfiguration
        }

        return null
    }

    // ---------------------------------------------------------------------------------------------

    private fun getProxySelectorConfiguration(uri: URI?): ProxyConfiguration? {
        val defaultProxySelector = ProxySelector.getDefault()
        val proxyList = defaultProxySelector.select(uri)

        val proxy = if (proxyList.size > 0) {
            proxyList[0]
        } else {
            return null
        }

        var proxyConfig: ProxyConfiguration? = null
        if (proxy === Proxy.NO_PROXY) {
            proxyConfig = ProxyConfiguration("", -1, proxy.type())
        } else {
            if (proxy.address() != null) {
                val proxyAddress = proxy.address() as InetSocketAddress
                proxyConfig =
                    ProxyConfiguration(proxyAddress.hostName, proxyAddress.port, proxy.type())
            }
        }

        return proxyConfig
    }

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
