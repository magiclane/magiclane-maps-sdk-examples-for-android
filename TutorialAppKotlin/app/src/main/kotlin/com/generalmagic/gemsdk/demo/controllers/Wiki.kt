/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.controllers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.Toast
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.app.BaseLayoutController
import com.generalmagic.gemsdk.demo.app.StaticsHolder
import com.generalmagic.gemsdk.demo.util.IntentHelper
import com.generalmagic.gemsdk.models.Coordinates
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.models.SearchPreferences
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.location_details_panel.view.*

class WikiController(context: Context, attrs: AttributeSet?) :
    BaseLayoutController(context, attrs) {
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
                val coords = landmark.getCoordinates() ?: return@execute

                val area = GEMSdkCall.execute { landmark.getContourGeograficArea() }
                val settings = HighlightRenderSettings()
                settings.setOptions(
                    if (area == null) {
                        THighlightOptions.EHO_ShowLandmark.value
                    } else {
                        THighlightOptions.EHO_ShowContour.value
                    }
                )
                
                mainMap?.activateHighlightLandmarks(arrayListOf(landmark), settings)
                mainMap?.centerOnCoordinates(coords, -1, TXy(), animation)
            }

            wikiView.onWikiFetchCompleteCallback = callback@{ reason: Int, _: String ->
                val gemError = GEMError.fromInt(reason)
                if (gemError != GEMError.KNoError) {
                    if (ignoreWikiErrorsCount == 0) {
                        Toast.makeText(
                            context,
                            "Fetching Wiki error: $gemError!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    ignoreWikiErrorsCount--
                }
            }

            if (!wikiView.show(landmark)) {
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

    override fun onBackPressed(): Boolean {
        hideProgress()
        StaticsHolder.getMainMapView()?.deactivateHighlight()
        StaticsHolder.getMainActivity()?.nav_view?.setCheckedItem(R.id.tutorial_hello)
        StaticsHolder.getMainActivity()?.nav_view?.menu?.performIdentifierAction(R.id.tutorial_hello, 0)
        return true
    }
}
