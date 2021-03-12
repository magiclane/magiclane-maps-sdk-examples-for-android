/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.example.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import com.generalmagic.apihelper.EnumHelp
import com.generalmagic.sdk.BuildConfig
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.ContentUpdater
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.content.EContentUpdaterStatus
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import com.generalmagic.sdk.util.defaults.network.DefaultNetworkManager
import com.generalmagic.sdk.util.defaults.network.DefaultNetworkProvider

class SdkInitHelper {
    companion object {
        private var networkManager: DefaultNetworkManager? = null
        private var offboardListener: OffboardListener? = null
        private var mapUpdater: ContentUpdater? = null

        fun isInit(): Boolean{
            SdkCall.checkCurrentThread()
            return GemSdk.isInitialized()
        }

        fun init(context: Context): Boolean {
            return SdkCall.execute {
                if (!isInit()) {
                    val initCode = GemSdk.initialize(
                        context, context as Activity
                    )

                    if (initCode != SdkError.KNoError.value)
                        return@execute false

                    initNewtork(context)
                }

                return@execute true
            } ?: false
        }

        fun deinit() {
            SdkCall.execute {
                GemSdk.release()
            }
        }

        private fun initNewtork(context: Context) {
            SdkCall.checkCurrentThread()

            /** SDK Must be init by now ! */
            if (!GemSdk.isInitialized()) {
                if (BuildConfig.DEBUG) {
                    error("Assertion failed")
                } else
                    return
            }
            if (networkManager != null) return

            val app = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val bundle = app.metaData

            val listener = object : OffboardListener() {
                override fun onOnlineWorldMapSupportStatus(state: EStatus) {
                    val listener = object : ProgressListener() {
                        override fun notifyStatusChanged(status: Int) {
                            when (EnumHelp.fromInt<EContentUpdaterStatus>(status)) {
                                EContentUpdaterStatus.EFullyReady,
                                EContentUpdaterStatus.EPartiallyReady -> {
                                    val updater = mapUpdater ?: return
                                    // cancel routing
                                    updater.apply()
                                }
                                else -> {
                                }
                            }
                        }
                    }

                    val result =
                        ContentStore().createContentUpdater(EContentType.ECT_RoadMap.value)
                            ?: return

                    if (result.second == SdkError.KNoError.value) {
                        mapUpdater = result.first ?: return
                        mapUpdater?.update(true, listener)
                    }
                }

                override fun onApiTokenRejected() {
                    Util.postOnMain {
                        Toast.makeText(context, "Token Rejected", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // network
            val networkProvider = DefaultNetworkProvider()

            offboardListener = listener
            networkManager = DefaultNetworkManager(context.applicationContext)

            networkManager?.onConnectionTypeChangedCallback =
                { type: DefaultNetworkManager.EConnectionType,
                  https: ProxyDetails, http: ProxyDetails ->
                    networkProvider.onNetworkConnectionTypeChanged(type, https, http)
                }

            val token = bundle.getString("com.generalmagic.sdk.token") ?: "invalid"

            CommonSettings().setNetworkProvider(networkProvider)
            CommonSettings().setAllowConnection(true, listener)
            CommonSettings().setAppAuthorization(token)
        }
    }
}
