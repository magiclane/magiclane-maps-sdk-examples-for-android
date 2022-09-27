/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("unused")

package com.generalmagic.sdk.examples.androidauto.app

import android.content.Context
import android.widget.Toast
import com.generalmagic.sdk.core.ErrorCode
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.ImageDatabase
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.d3scene.MapCamera
import com.generalmagic.sdk.examples.androidauto.activities.BaseActivity
import com.generalmagic.sdk.examples.androidauto.services.FavouritesInstance
import com.generalmagic.sdk.examples.androidauto.services.HistoryInstance
import com.generalmagic.sdk.examples.androidauto.services.NavigationInstance
import com.generalmagic.sdk.examples.androidauto.services.RoutingInstance
import com.generalmagic.sdk.examples.androidauto.services.SearchInstance
import com.generalmagic.sdk.examples.androidauto.services.SettingsInstance
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.sensordatasource.DataSource
import com.generalmagic.sdk.sensordatasource.DataSourceFactory
import com.generalmagic.sdk.sensordatasource.DataSourceListener
import com.generalmagic.sdk.sensordatasource.PositionData
import com.generalmagic.sdk.sensordatasource.PositionListener
import com.generalmagic.sdk.sensordatasource.PositionService
import com.generalmagic.sdk.sensordatasource.enums.EDataType
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkImages
import com.generalmagic.sdk.util.Util

const val REQUEST_PERMISSIONS = 110

object AppProcess {
    var sharedCamera: MapCamera? = null

    var androidAutoService = AndroidAutoService.empty

    val currentPosition: Coordinates?
        get() {
            return PositionService.position?.let {
                Coordinates(it.latitude, it.longitude)
            }
        }

    @Synchronized
    fun init(context: Context) {
        if (GemSdk.isInitialized())
            return

        SdkSettings.onApiTokenRejected = {
            /*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/sign in and generate one.
             */
            Toast.makeText(context, "TOKEN REJECTED", Toast.LENGTH_SHORT).show()
        }

        if (!GemSdk.initSdkWithDefaults(context.applicationContext)) {
            throw Exception("GemSdk.initSdkWithDefaults failed")
//                return // No point to continue......
        }

        SdkCall.execute {
            FavouritesInstance.init()
            HistoryInstance.init()
            NavigationInstance.init()
            RoutingInstance.init()
            SearchInstance.init()
            SettingsInstance.init()
        }
    }

    fun showToast(message: String) {
        Util.postOnMain {
            val context = BaseActivity.topActivity
            if (context != null) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } else {
                androidAutoService.showToast(message)
            }
        }
    }

    private var datasource: DataSource? = null
    private var positionListener: DataSourceListener? = null

    fun waitForNextImprovedPosition(onEvent: (() -> Unit)) {
        datasource = DataSourceFactory.produceLive()
        if (datasource == null) {
//            showToast("No datasource!")
            return
        }

        positionListener = object : DataSourceListener() {
            override fun onNewData(dataType: EDataType) {
                if (dataType != EDataType.ImprovedPosition)
                    return

//                showToast("On valid position")
                onEvent()

                //  Stuff no longer needed
                datasource?.removeListener(this)
                datasource = null
                positionListener = null
            }
        }

        // listen for first valid position to start the nav
//        showToast("Waiting for valid position")
        datasource?.addListener(positionListener!!, EDataType.ImprovedPosition)
    }

    fun waitForNextPosition(onPosition: (() -> Unit)) {
        val positionListener = object : PositionListener() {
            override fun onNewPosition(value: PositionData) {
                if (!value.isValid()) return

                PositionService.removeListener(this)
                onPosition()
            }
        }

        // listen for first valid position to start the nav
        PositionService.addListener(positionListener)
    }

    fun onAndroidAutoConnected() {
        // open AndroidAutoLockedActivity
    }

    fun onAndroidAutoDisconnected() {
        // finish AndroidAutoLockedActivity
    }

    /**
     * @param uriString Is encoded as follows:
     *   1) "geo:12.345,14.8767" for a latitude, longitude pair.
     *   2) "geo:0,0?q=123+Main+St,+Seattle,+WA+98101" for an address.
     *   3) "geo:0,0?q=a+place+name" for a place to search for.
     */
    fun handleGeoUri(uriString: String) = SdkCall.execute {
        var address = ""
        var label = ""
        var referencePoint: Coordinates? = null

        val pos = uriString.indexOf('?')

        if (uriString.indexOf("geo:0,0?") == -1) {
            val start = uriString.indexOf(':') + 1
            val tmp = if (pos != -1)
                uriString.substring(start, pos)
            else uriString.substring(start)

            if (tmp.isNotEmpty()) {
                referencePoint =
                    com.generalmagic.sdk.examples.androidauto.util.Util.parseCoordinates(tmp)
            }
        }

        if (pos > 0) {
            val parameters =
                com.generalmagic.sdk.examples.androidauto.util.Util.getParameters(
                    uriString.substring(
                        pos + 1
                    )
                )

            for (parameter in parameters) {
                val name = parameter.first
                var value = parameter.second

                if (name == "q") {
                    val pos1 = value.indexOf("(")
                    val pos2 = value.indexOf(")")

                    if ((pos1 >= 0) && (pos2 >= 0) && (pos2 > (pos1 + 1))) {
                        label = value.substring(pos1 + 1, pos2 - pos1 - 1)
                        value = value.substring(0, pos1)
                    }

                    var bFoundCoordinates = false
                    if (referencePoint == null) {
                        referencePoint =
                            com.generalmagic.sdk.examples.androidauto.util.Util.parseCoordinates(
                                value
                            )
                        bFoundCoordinates = referencePoint != null
                    }

                    if (!bFoundCoordinates) {
                        address = value
                    }
                }
//                else if (name == "z") {
//                    val z = value.toInt()
//                    if (z in 0..21) {
//                        val view = surfaceAdapter?.mapView
//                        val zoomLevel = ((view?.maxZoomLevel ?: 0 * z)) / 21
                // use it??
//                    }
//                }
            }
        }


        if (address.isNotEmpty()) {
            val listener = object : ProgressListener() {
                override fun notifyComplete(errorCode: ErrorCode, hint: String) {
                    SearchInstance.listeners.remove(this)

                    Util.postOnMain {
                        androidAutoService.showRoutesPreview(SearchInstance.results[0])
                    }
                }
            }

            SearchInstance.service.cancelSearch()
            SearchInstance.listeners.add(listener)
            SearchInstance.service.searchByFilter(address, referencePoint)
        }

        if (address.isEmpty() && referencePoint != null && referencePoint.valid()) {
            var name = ""
            if (label.isNotEmpty()) {
                name = label
            } else {
                name.format(
                    "%.6f, %.6f",
                    referencePoint.latitude,
                    referencePoint.longitude
                )
            }

            val landmark = Landmark()
            landmark.coordinates = referencePoint
            landmark.name = name
            landmark.image =
                ImageDatabase().getImageById(SdkImages.UI.SearchForLatitudeLongitude.value)

            Util.postOnMain {
                androidAutoService.showRoutesPreview(landmark)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
}

open class AndroidAutoService {
    open fun showToast(message: String) {}
    open fun finish() {}
    open fun invalidate() {}
    open fun showRoutesPreview(landmark: Landmark) {}

    companion object {
        val empty: AndroidAutoService
            get() = AndroidAutoService()
    }
}
