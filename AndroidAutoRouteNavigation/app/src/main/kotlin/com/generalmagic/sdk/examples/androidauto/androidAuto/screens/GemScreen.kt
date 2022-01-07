/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.androidauto.androidAuto.screens

import androidx.car.app.CarContext
import com.generalmagic.sdk.d3scene.MapView
import com.generalmagic.sdk.examples.androidauto.androidAuto.Service
import com.generalmagic.sdk.examples.androidauto.androidAuto.base.ScreenLifecycle
import com.generalmagic.sdk.util.SdkCall

abstract class GemScreen(context: CarContext) : ScreenLifecycle(context) {
    protected val mapView: MapView?
        get() = Service.instance?.surfaceAdapter?.mapView

    var isLoading = false
    var isMapVisible = false

    override fun onBackPressed() {
        finish()
    }

    override fun onResume() {
        super.onResume()

        updateMapView()
    }

    open fun onMapFollowChanged(following: Boolean) {}

    open fun updateMapView() {
        SdkCall.execute {
            val mapView = mapView ?: return@execute

            mapView.hideRoutes()
            mapView.deactivateAllHighlights()
            mapView.followPosition()
        }
    }

    abstract fun updateData()
}