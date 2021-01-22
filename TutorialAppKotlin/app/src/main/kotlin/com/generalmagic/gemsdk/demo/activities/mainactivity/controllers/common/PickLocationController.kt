/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.activities.mainactivity.controllers.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.app.MapLayoutController
import com.generalmagic.gemsdk.demo.app.elements.ButtonsDecorator
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.pick_location.view.*

class PickLocationController(context: Context, attrs: AttributeSet?) :
    MapLayoutController(context, attrs) {

    var onCancelPressed: () -> Unit = {}
    var onStartPicked: (Landmark) -> Unit = {}
    var onIntermediatePicked: (Landmark) -> Unit = {}
    var onDestinationPicked: (Landmark) -> Unit = {}

    fun updateTopText(text: String) {
        pickTopText?.text = text
    }

    override fun onMapFollowStatusChanged(following: Boolean) { }

    override fun doStop() {
        GEMApplication.postOnMain { onCancelPressed() }
    }

    private fun onPickDestinationPressed() {
        val landmark = getLandmark("Destination")
        if (landmark == null) {
            onCancelPressed()
            return
        }

        onDestinationPicked(landmark)
    }

    private fun onPickStartPressed() {
        val landmark = getLandmark("Start")
        if (landmark == null) {
            onCancelPressed()
            return
        }

        onStartPicked(landmark)
        pickDestination()
    }

    private fun onPickIntermediatePressed() {
        val landmark = getLandmark("Intermediate")
        if (landmark == null) {
            onCancelPressed()
            return
        }

        onIntermediatePicked(landmark)
        pickDestination()
    }

    fun pickStart() {
        visibility = View.VISIBLE
        updateTopText("Pick Start")
        pickIcon.background = ContextCompat.getDrawable(context, R.drawable.ic_place_green_24dp)

        setStopButtonVisible(true)
        setPickIntermediateButtonVisible(false) {}
        setPickStartButtonVisible(true) {
            onPickStartPressed()
        }
    }

    fun pickIntermediate() {
        visibility = View.VISIBLE
        updateTopText("Pick Intermediate")
        pickIcon.background = ContextCompat.getDrawable(context, R.drawable.ic_place_gray_24dp)

        setStopButtonVisible(true)
        setPickIntermediateButtonVisible(true) {
            onPickIntermediatePressed()
        }
        setPickDestinationButtonVisible(false) {}
    }

    fun pickDestination() {
        visibility = View.VISIBLE
        updateTopText("Pick Destination")
        pickIcon.background = ContextCompat.getDrawable(context, R.drawable.ic_place_red_24dp)

        setStopButtonVisible(true)
        setPickIntermediateButtonVisible(true) {
            pickIntermediate()
        }
        setPickDestinationButtonVisible(true) {
            onPickDestinationPressed()
        }
    }

    private fun getLandmark(name: String): Landmark? {
        return GEMSdkCall.execute {
            val myPosition =
                GEMApplication.getMainMapView()?.getCursorWgsPosition() ?: return@execute null
            Landmark(name, myPosition)
        }
    }

    private fun setPickIntermediateButtonVisible(visible: Boolean, action: () -> Unit) {
        val button = getBottomCenterButton() ?: return

        if (visible) {
            ButtonsDecorator.buttonAsPickIntermediate(context, button, action)

            button.visibility = View.VISIBLE
        } else {
            button.visibility = View.GONE
        }
    }

    private fun setPickDestinationButtonVisible(visible: Boolean, action: () -> Unit) {
        val button = getBottomRightButton() ?: return

        if (visible) {
            ButtonsDecorator.buttonAsPickDestination(context, button, action)

            button.visibility = View.VISIBLE
        } else {
            button.visibility = View.GONE
        }
    }

    private fun setPickStartButtonVisible(visible: Boolean, action: () -> Unit) {
        val button = getBottomRightButton() ?: return

        if (visible) {
            ButtonsDecorator.buttonAsPickStart(context, button, action)

            button.visibility = View.VISIBLE
        } else {
            button.visibility = View.GONE
        }
    }
}
