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

import android.os.Build
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

object ProxyUtils {
    // ---------------------------------------------------------------------------------------------

    private val sdkInt = Build.VERSION.SDK_INT

    // ---------------------------------------------------------------------------------------------

    fun getProxyConfiguration(bHttpsProxyConfiguration: Boolean): ProxyConfiguration? {
        return if (bHttpsProxyConfiguration) {
            getProxyConfiguration(URI.create("https://www.generalmagic.com"))
        } else {
            getProxyConfiguration(URI.create("http://www.generalmagic.com"))
        }
    }

    // ---------------------------------------------------------------------------------------------

    @Throws(Exception::class)
    fun getProxyConfiguration(uri: URI?): ProxyConfiguration? {
        if (sdkInt >= 12) {
            return getProxySelectorConfiguration(uri) as ProxyConfiguration
        }

        return null
    }

    // ---------------------------------------------------------------------------------------------

    private fun getProxySelectorConfiguration(uri: URI?): ProxyConfiguration? {
        val defaultProxySelector = ProxySelector.getDefault()
        val proxy: Proxy?
        val proxyList = defaultProxySelector.select(uri)

        if (proxyList.size > 0) {
            proxy = proxyList[0]
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
}
