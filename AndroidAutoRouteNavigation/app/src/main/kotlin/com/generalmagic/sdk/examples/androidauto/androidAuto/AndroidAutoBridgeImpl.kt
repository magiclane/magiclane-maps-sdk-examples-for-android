@file:Suppress("unused")

package com.generalmagic.sdk.examples.androidauto.androidAuto

import com.generalmagic.sdk.examples.androidauto.androidAuto.controllers.RoutesPreviewController
import com.generalmagic.sdk.examples.androidauto.app.AndroidAutoService
import com.generalmagic.sdk.places.Landmark

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