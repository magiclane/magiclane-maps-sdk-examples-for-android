/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

@file:Suppress("unused")

package com.magiclane.sdk.examples.androidauto.app

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.multidex.MultiDexApplication
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.MapCamera
import com.magiclane.sdk.examples.androidauto.R
import com.magiclane.sdk.examples.androidauto.activities.BaseActivity
import com.magiclane.sdk.examples.androidauto.services.FavouritesInstance
import com.magiclane.sdk.examples.androidauto.services.HistoryInstance
import com.magiclane.sdk.examples.androidauto.services.NavigationInstance
import com.magiclane.sdk.examples.androidauto.services.RoutingInstance
import com.magiclane.sdk.examples.androidauto.services.SearchInstance
import com.magiclane.sdk.examples.androidauto.services.SettingsInstance
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.EImageFileFormat
import com.magiclane.sdk.sensordatasource.*
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import java.io.ByteArrayOutputStream

const val REQUEST_PERMISSIONS = 110

class AppProcess : MultiDexApplication() {

    init
    {
        instance = this
    }
    
    companion object
    {
        // app instance
        private lateinit var instance: Application
        
        var sharedCamera: MapCamera? = null

        var androidAutoService = AndroidAutoService.empty

        fun getApplicationContext(): Application
        {
            return instance
        }

        fun getAppResources(): Resources
        {
            return instance.resources
        }

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
                check the magiclane.com website, sign up/sign in and generate one.
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
                        com.magiclane.sdk.examples.androidauto.util.Util.parseCoordinates(tmp)
                }
            }

            if (pos > 0) {
                val parameters =
                    com.magiclane.sdk.examples.androidauto.util.Util.getParameters(
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
                                com.magiclane.sdk.examples.androidauto.util.Util.parseCoordinates(
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
                
                val bmp = ContextCompat.getDrawable(
                    instance,
                    R.drawable.search_for_latitude_longitude
                )?.toBitmap()
            
                bmp?.let {
                    val stream = ByteArrayOutputStream()
                    it.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val byteArray = stream.toByteArray()
                    it.recycle()

                    landmark.image = Image.Companion.produceWithDataBuffer(
                        DataBuffer(byteArray), EImageFileFormat.Bmp
                    )
                }

                Util.postOnMain {
                    androidAutoService.showRoutesPreview(landmark)
                }
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
