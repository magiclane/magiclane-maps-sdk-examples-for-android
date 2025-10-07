/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
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