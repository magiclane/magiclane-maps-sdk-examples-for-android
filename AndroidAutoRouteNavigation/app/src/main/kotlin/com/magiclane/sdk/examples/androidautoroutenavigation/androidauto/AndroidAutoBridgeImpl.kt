/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("unused")

package com.magiclane.sdk.examples.androidautoroutenavigation.androidauto

import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.controllers.RoutesPreviewController
import com.magiclane.sdk.examples.androidautoroutenavigation.app.AndroidAutoService
import com.magiclane.sdk.places.Landmark

class AndroidAutoBridgeImpl : AndroidAutoService() {
    override fun finish() {
        Service.session?.context?.finishCarApp()
    }

    override fun invalidate() {
        Service.invalidateTop()
    }

    override fun showRoutesPreview(landmark: Landmark) {
        val context = Service.session?.context ?: return

        Service.pushScreen(RoutesPreviewController(context, landmark), true)
    }

    fun popToRoot() {
        Service.screenManager?.popToRoot()
    }
}
