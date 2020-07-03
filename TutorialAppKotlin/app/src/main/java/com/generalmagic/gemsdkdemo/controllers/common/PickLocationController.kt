// Copyright (C) 2019-2020, General Magic B.V.
// All rights reserved.
//
// This software is confidential and proprietary information of General Magic
// ("Confidential Information"). You shall not disclose such Confidential
// Information and shall use it only in accordance with the terms of the
// license agreement you entered into with General Magic.

package com.generalmagic.gemsdkdemo.controllers.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.models.QualifiedCoordinates
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdkdemo.R
import com.generalmagic.gemsdkdemo.controllers.AppLayoutController
import com.generalmagic.gemsdkdemo.util.StaticsHolder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.app_bar_layout.view.*
import kotlinx.android.synthetic.main.pick_location.view.*

class PickLocationController(context: Context?, attrs: AttributeSet?) :
	AppLayoutController(context, attrs) {

	var onCancelPressed: () -> Unit = {}
	var onStartPicked: (Landmark) -> Unit = {}
	var onIntermediatePicked: (Landmark) -> Unit = {}
	var onDestinationPicked: (Landmark) -> Unit = {}

	fun updateTopText(text: String) {
		pickTopText?.text = text
	}

	fun pickStart() {
		visibility = View.VISIBLE
		updateTopText("Pick Start")
		pickIcon.background = resources.getDrawable(R.drawable.ic_place_green_24dp)

		prepareStopBtn()
		bottomButtons()?.bottomCenterButton?.visibility = View.GONE
		bottomButtons()?.bottomRightButton?.let { button ->
			button.visibility = View.VISIBLE
			button.setOnClickListener {
				val landmark = getLandmark("Start")
				if (landmark == null) {
					onCancelPressed()
					return@setOnClickListener
				}

				onStartPicked(landmark)
				pickDestination()
			}
			buttonAsPickStart(button)
		}
	}

	fun pickIntermediate() {
		visibility = View.VISIBLE
		updateTopText("Pick Intermediate")
		pickIcon.background = resources.getDrawable(R.drawable.ic_place_gray_24dp)

		prepareStopBtn()
		bottomButtons()?.bottomCenterButton?.visibility = View.GONE
		bottomButtons()?.bottomRightButton?.let { button ->
			button.visibility = View.VISIBLE
			button.setOnClickListener {
				val landmark = getLandmark("Intermediate")
				if (landmark == null) {
					onCancelPressed()
					return@setOnClickListener
				}

				onIntermediatePicked(landmark)
				pickDestination()
			}
			buttonAsPickIntermediate(button)
		}
	}

	fun pickDestination() {
		visibility = View.VISIBLE
		updateTopText("Pick Destination")
		pickIcon.background = resources.getDrawable(R.drawable.ic_place_red_24dp)

		prepareStopBtn()
		bottomButtons()?.bottomCenterButton?.let { button ->
			button.visibility = View.VISIBLE
			button.setOnClickListener {
//				pickIntermediate()
				val landmark = getLandmark("Intermediate")
				if (landmark == null) {
					onCancelPressed()
					return@setOnClickListener
				}

				onIntermediatePicked(landmark)
			}
			buttonAsPickIntermediate(button)
		}
		bottomButtons()?.bottomRightButton?.let { button ->
			button.visibility = View.VISIBLE
			button.setOnClickListener {
				val landmark = getLandmark("Destination")
				if (landmark == null) {
					onCancelPressed()
					return@setOnClickListener
				}

				onDestinationPicked(landmark)
			}
			buttonAsPickDestination(button)
		}
	}

	private fun prepareStopBtn() {
		bottomButtons()?.let {
			it.bottomCenterButton?.visibility = View.GONE
			it.bottomLeftButton?.let { button ->
				button.visibility = View.VISIBLE
				button.setOnClickListener {
					onCancelPressed()
				}
				buttonAsStop(button)
			}
		}
	}

	private fun getLandmark(name: String): Landmark? {
		val myPosition = GEMSdkCall.execute {
			StaticsHolder.getMainMapView()?.getCursorWgsPosition()
		} ?: return null

		return Landmark(name, QualifiedCoordinates(myPosition))
	}

	//----------------------------------------------------------------------------------------------
	private fun buttonAsPickStart(button: FloatingActionButton?) {
		button ?: return

		button.tag = "start"
		button.backgroundTintList = resources.getColorStateList(R.color.colorGreenFull)
		button.setImageDrawable(resources.getDrawable(R.drawable.ic_place_green_24dp))
	}

	private fun buttonAsPickIntermediate(button: FloatingActionButton?) {
		button ?: return

		button.tag = "intermediate"
		button.backgroundTintList = resources.getColorStateList(android.R.color.darker_gray)
		button.setImageDrawable(resources.getDrawable(R.drawable.ic_place_gray_24dp))
	}

	private fun buttonAsPickDestination(button: FloatingActionButton?) {
		button ?: return

		button.tag = "destination"
		button.backgroundTintList = resources.getColorStateList(R.color.colorRedFull)
		button.setImageDrawable(resources.getDrawable(R.drawable.ic_place_red_24dp))
	}
}
