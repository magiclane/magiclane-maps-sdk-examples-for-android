/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.util

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.generalmagic.apihelper.EnumHelp
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.ContentUpdater
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.content.EContentUpdaterStatus
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import kotlin.system.exitProcess

object SdkInitHelper {
    private var mapUpdater: ContentUpdater? = null
    var onCancel: () -> Unit = {}
    var onNetworkConnected: () -> Unit = {}
    var onMapReady: () -> Unit = {}
    private var isMapReady = true

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun isInit(): Boolean = SdkCall.execute { GemSdk.isInitialized() } ?: false

    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun init(context: Context, token: String): Boolean {
//        if (token == "YOUR_TOKEN") {
//            throw Exception("Don't forget to add your application token.")
//        }

        return SdkCall.execute {
            if (isInit()) {
                throw Exception("Already initialized... :)")
            }

            // Start the initialization.
            val initCode = GemSdk.initialize(
                // A problem was encountered during the initialization.
                context, context as Activity
            )

            if (initCode != SdkError.NoError.value) {
                // A problem was encountered during the initialization.
                return@execute false
            }

            // No problem was encountered and the initialization of the SDK finished.

            CommonSettings.onOnlineWorldMapSupportStatus = {
                isMapReady = false

                SdkCall.execute stateUpdated@{
                    val updaterListener = ProgressListener.create(onStatusChanged = { status ->
                        when (EnumHelp.fromInt<EContentUpdaterStatus>(status)) {
                            EContentUpdaterStatus.FullyReady,
                            EContentUpdaterStatus.PartiallyReady -> {
                                val updater = mapUpdater ?: return@create
                                /*
                                This will apply the necessary updates,
                                but will also cancel any routing actions.
                                */
                                updater.apply()
                            }
                            else -> {
                            }
                        }
                    }, postOnMain = false)

                    // Make an instance of a content store updater regarding road maps.
                    val result = ContentStore().createContentUpdater(EContentType.RoadMap.value)
                        ?: return@stateUpdated

                    if (result.second == SdkError.NoError.value) {
                        // The updater was created without any problems.
                        mapUpdater = result.first ?: return@stateUpdated
                        mapUpdater?.update(true, updaterListener)
                    }
                }
            }

            CommonSettings.onApiTokenRejected = {
                /* 
                The TOKEN you provided in the AndroidManifest.xml file was rejected.
                Make sure you provided the correct value, or if you don't have a TOKEN,
                check the generalmagic.com website, sign up/ sing in and generate one. 
                 */
                onCancel()
                Toast.makeText(context, "TOKEN REJECTED", Toast.LENGTH_LONG).show()
            }

            CommonSettings.onConnectionStatusUpdated = {
                if (it) onNetworkConnected()
            }

            CommonSettings.onWorldMapVersionUpdated = {
                // Defines an action that should be done after the world map is updated.
                isMapReady = true
                onMapReady()
            }

            CommonSettings.setDefaultNetworkProvider(context, true)

            CommonSettings.setAppAuthorization(token)

            return@execute true
        } ?: false
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun deinit() {
        // Deinitialize the SDK.
        SdkCall.execute {
            GemSdk.release()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun terminateApp(activity: Activity) {
        activity.finish()
        exitProcess(0)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
