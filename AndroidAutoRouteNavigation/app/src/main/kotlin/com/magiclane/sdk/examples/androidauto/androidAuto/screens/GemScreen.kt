/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

package com.magiclane.sdk.examples.androidauto.androidAuto.screens

import androidx.car.app.CarContext
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.examples.androidauto.androidAuto.Service
import com.magiclane.sdk.examples.androidauto.androidAuto.base.ScreenLifecycle
import com.magiclane.sdk.util.SdkCall

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