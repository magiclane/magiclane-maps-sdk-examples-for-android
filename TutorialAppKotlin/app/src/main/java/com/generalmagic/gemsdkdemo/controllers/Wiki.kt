// Copyright (C) 2019-2020, General Magic B.V.
// All rights reserved.
//
// This software is confidential and proprietary information of General Magic
// ("Confidential Information"). You shall not disclose such Confidential
// Information and shall use it only in accordance with the terms of the
// license agreement you entered into with General Magic.

package com.generalmagic.gemsdkdemo.controllers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.Toast
import com.generalmagic.gemsdk.Animation
import com.generalmagic.gemsdk.TAnimation
import com.generalmagic.gemsdk.TXy
import com.generalmagic.gemsdk.models.Coordinates
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.models.SearchPreferences
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdkdemo.util.IntentHelper
import com.generalmagic.gemsdkdemo.util.StaticsHolder
import kotlinx.android.synthetic.main.location_details_panel.view.*

class WikiController(context: Context?, attrs: AttributeSet?) :
	AppLayoutController(context, attrs) {
	private lateinit var landmark: Landmark
	private var ignoreWikiErrorsCount = 0

	private val preferences = SearchPreferences()
	private val search = object : SearchServiceWrapper() {
		override fun onSearchStarted() {
			showProgress()
		}

		override fun onSearchCompleted(reason: Int) {
			val gemError = GEMError.fromInt(reason)
			if (gemError == GEMError.KCancel) return

			hideProgress()

			if (gemError != GEMError.KNoError) {
				Toast.makeText(context, "Search failed: $gemError", Toast.LENGTH_SHORT).show()
				return
			}

			val results = GEMSdkCall.execute { this.results.asArrayList() }
			if (results == null || results.size == 0) return

			val value = results[0]

			GEMSdkCall.execute { doStart(value) }
		}
	}

	override fun doStart() {
		GEMSdkCall.checkCurrentThread()

		val inLandmark = IntentHelper.getObjectForKey(EXTRA_LANDMARK) as Landmark?
		if (inLandmark == null) {
			val text = "Tokyo"
			val coords = Coordinates(35.682838439941406, 139.75946044921875)
			doSearch(text, coords)

			return
		}
		landmark = inLandmark

		doStart(landmark)
	}

	fun doStart(landmark: Landmark) {
		GEMSdkCall.checkCurrentThread()

		this.landmark = landmark

		Handler(Looper.getMainLooper()).post {
			ignoreWikiErrorsCount = 3 // reset

			val mainMap = StaticsHolder.getMainMapView()
			GEMSdkCall.execute {
				val animation = Animation()
				animation.setMethod(TAnimation.EAnimationFly)
				val coords = landmark.getCoordinates()?.coordinates() ?: return@execute
				mainMap?.centerOnCoordinates(coords, -1, TXy(), animation)
			}

			wikiView.onWikiFetchCompleteCallback = callback@{ reason: Int, _: String ->
				val gemError = GEMError.fromInt(reason)
				if (gemError != GEMError.KNoError) {
					if (ignoreWikiErrorsCount == 0) {
						Toast.makeText(
							context, "Fetching Wiki error: $gemError!", Toast.LENGTH_SHORT
						).show()
					}

					ignoreWikiErrorsCount--
				}


				Handler(Looper.getMainLooper()).post {
					hideProgress()
				}
			}

			showProgress()
			if (!wikiView.show(landmark)) {
//			Toast.makeText(context, "No Wiki!", Toast.LENGTH_SHORT).show()
				hideProgress()
			}
		}
	}

	override fun doStop() {
		GEMSdkCall.checkCurrentThread()

		search.service.cancelRequest(search.listener)
	}

	private fun doSearch(text: String, coords: Coordinates) {
		GEMSdkCall.checkCurrentThread()

		preferences.setReferencePoint(coords)
		preferences.setSearchMapPOIs(true)
		search.service.searchByFilter(search.results, search.listener, text, preferences)
	}


	companion object {
		const val EXTRA_LANDMARK = "landmark"
	}
}
