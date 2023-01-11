/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

@file:Suppress("unused")

package com.magiclane.sdk.examples.androidauto.androidAuto

import com.magiclane.sdk.examples.androidauto.androidAuto.controllers.RoutesPreviewController
import com.magiclane.sdk.examples.androidauto.app.AndroidAutoService
import com.magiclane.sdk.places.Landmark

class AndroidAutoBridgeImpl: AndroidAutoService() {
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
