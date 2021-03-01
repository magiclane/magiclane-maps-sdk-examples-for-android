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

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketAddress

class ProxyConfiguration(val proxyHost: String, val proxyPort: Int, val proxyType: Proxy.Type) {
    val proxy: Proxy
        get() = if (proxyHost.isNotEmpty()) {
            var sa: SocketAddress? = null
            try {
                sa = InetSocketAddress.createUnresolved(proxyHost, proxyPort)
            } catch (e: IllegalArgumentException) {
            }
            sa?.let { Proxy(proxyType, it) } ?: Proxy.NO_PROXY
        } else {
            Proxy.NO_PROXY
        }
}
