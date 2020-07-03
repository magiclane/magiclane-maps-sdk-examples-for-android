// Copyright (C) 2019-2020, General Magic B.V.
// All rights reserved.
//
// This software is confidential and proprietary information of General Magic
// ("Confidential Information"). You shall not disclose such Confidential
// Information and shall use it only in accordance with the terms of the
// license agreement you entered into with General Magic.

package com.generalmagic.gemsdkdemo.controllers

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdkdemo.R
import com.generalmagic.gemsdkdemo.util.MainMapStatusFollowingProvider
import com.generalmagic.gemsdkdemo.util.StaticsHolder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.app_bar_layout.view.*


open class AppLayoutController(context: Context?, attrs: AttributeSet?) :
	ConstraintLayout(context, attrs) {

	open fun onBackPressed(): Boolean {
		return false
	}

	open fun doStart() {}
	open fun doStop() {}
	open fun onMapFollowStatusChanged(following: Boolean) {}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()

		GEMSdkCall.execute { doStop() }
	}

	fun displayGpsButton() {
		bottomButtons()?.let { buttons ->
			buttons.bottomCenterButton?.visibility = android.view.View.GONE
			buttons.bottomRightButton?.visibility = android.view.View.GONE
			buttons.bottomLeftButton?.let {
				it.visibility = android.view.View.VISIBLE
				buttonAsFollowGps(it)
				it.setOnClickListener {
					GEMSdkCall.execute { MainMapStatusFollowingProvider.getInstance().doFollow() }
				}
			}
		}
	}

	protected fun hideProgress() {
		StaticsHolder.getMainActivity()?.hideProgress()
	}

	protected fun showProgress() {
		StaticsHolder.getMainActivity()?.showProgress()
	}

	fun disableScreenLock() {
		StaticsHolder.getMainActivity()?.disableScreenLock()
	}

	fun enableScreenLock() {
		StaticsHolder.getMainActivity()?.enableScreenLock()
	}

	fun showAppBar() {
		StaticsHolder.getMainActivity()?.showAppBar()
	}

	fun hideAppBar() {
		StaticsHolder.getMainActivity()?.hideAppBar()
	}

	fun showSystemBars() {
		StaticsHolder.getMainActivity()?.showSystemBars()
	}

	fun hideSystemBars() {
		StaticsHolder.getMainActivity()?.hideSystemBars()
	}

	fun bottomButtons(): ConstraintLayout? {
		return StaticsHolder.getMainActivity()?.bottomButtons()
	}

	//----------------------------------------------------------------------------------------------
	fun buttonAsStart(button: FloatingActionButton?) {
		button ?: return

		button.tag = "start"
		button.backgroundTintList = resources.getColorStateList(R.color.colorGreenFull)
		button.setImageDrawable(resources.getDrawable(android.R.drawable.ic_media_play))
	}

	fun buttonAsOk(button: FloatingActionButton?) {
		button ?: return

		button.tag = "ok"
		button.backgroundTintList = resources.getColorStateList(R.color.colorGreenFull)
		button.setImageDrawable(resources.getDrawable(android.R.drawable.checkbox_on_background))
	}

	fun buttonAsFollowGps(button: FloatingActionButton?) {
		button ?: return

		button.tag = "gps"
		button.backgroundTintList = resources.getColorStateList(R.color.colorPrimary)
		button.setImageDrawable(resources.getDrawable(R.drawable.ic_gps_fixed_white_24dp))
	}

	fun buttonAsStop(button: FloatingActionButton?) {
		button ?: return

		button.tag = "stop"
		button.backgroundTintList = resources.getColorStateList(R.color.colorRedFull)
		button.setImageDrawable(resources.getDrawable(R.drawable.ic_close_gray_24dp))
	}

	fun buttonAsAdd(button: FloatingActionButton?) {
		button ?: return

		button.tag = "add"
		button.backgroundTintList = resources.getColorStateList(R.color.colorGreenFull)
		button.setImageDrawable(resources.getDrawable(android.R.drawable.ic_input_add))
	}

	fun buttonAsDelete(button: FloatingActionButton?) {
		button ?: return

		button.tag = "delete"
		button.backgroundTintList = resources.getColorStateList(R.color.colorRedFull)
		button.setImageDrawable(resources.getDrawable(android.R.drawable.ic_delete))
	}

	fun buttonAsDescription(button: FloatingActionButton?) {
		button ?: return

		button.tag = "info"
		button.backgroundTintList = resources.getColorStateList(R.color.colorLightBlue)
		button.setImageDrawable(resources?.getDrawable(android.R.drawable.ic_dialog_info, null))
	}
}
