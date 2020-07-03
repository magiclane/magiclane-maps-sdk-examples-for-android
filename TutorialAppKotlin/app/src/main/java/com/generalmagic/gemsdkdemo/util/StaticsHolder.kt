// Copyright (C) 2019-2020, General Magic B.V.
// All rights reserved.
//
// This software is confidential and proprietary information of General Magic
// ("Confidential Information"). You shall not disclose such Confidential
// Information and shall use it only in accordance with the terms of the
// license agreement you entered into with General Magic.

package com.generalmagic.gemsdkdemo.util

import androidx.drawerlayout.widget.DrawerLayout
import com.generalmagic.gemsdk.Animation
import com.generalmagic.gemsdk.Screen
import com.generalmagic.gemsdk.View
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdkdemo.MainActivity
import com.generalmagic.gemsdkdemo.util.StaticsHolder.Companion.getMainMapView

class StaticsHolder {
	companion object {
		var getMainMapView: () -> View? = { null }
		var gemMapScreen: () -> Screen? = { null }
		var getMainActivity: () -> MainActivity? = { null }
		var getNavViewDrawerLayout: () -> DrawerLayout? = { null }
	}
}

class MainMapStatusFollowingProvider {
	companion object {
		private var instance: MainMapStatusFollowingProvider? = null
		fun getInstance(): MainMapStatusFollowingProvider {
			if (instance == null) instance = MainMapStatusFollowingProvider()
			return instance!!
		}
	}

	abstract class Listener {
		abstract fun statusChangedTo(following: Boolean)
	}

	fun noticePanInterruptFollow() {
		for (listener in listeners) {
			listener.statusChangedTo(false)
		}
	}

	fun doUnFollow() {
		GEMSdkCall.checkCurrentThread()
		val mainMapView = getMainMapView() ?: return
		mainMapView.stopFollowingPosition()

		for (listener in listeners) {
			listener.statusChangedTo(false)
		}
	}

	fun doFollow() {
		GEMSdkCall.checkCurrentThread()
		val mainMapView = getMainMapView() ?: return
		mainMapView.startFollowingPosition(Animation(), 2)

		for (listener in listeners) {
			listener.statusChangedTo(true)
		}
	}


	val listeners = ArrayList<Listener>()
}
