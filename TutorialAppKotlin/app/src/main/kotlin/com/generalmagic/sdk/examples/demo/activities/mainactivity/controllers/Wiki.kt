/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import com.generalmagic.sdk.*
import com.generalmagic.sdk.core.ImageDatabase
import com.generalmagic.sdk.core.Xy
import com.generalmagic.sdk.d3scene.Animation
import com.generalmagic.sdk.d3scene.EAnimation
import com.generalmagic.sdk.d3scene.EHighlightOptions
import com.generalmagic.sdk.d3scene.HighlightRenderSettings
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.app.MapLayoutController
import com.generalmagic.sdk.examples.demo.app.elements.ButtonsDecorator
import com.generalmagic.sdk.examples.demo.util.IntentHelper
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.SearchService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import com.generalmagic.sdk.util.SdkIcons
import kotlinx.android.synthetic.main.location_details_panel.view.*

class WikiController(context: Context, attrs: AttributeSet?) :
    MapLayoutController(context, attrs) {
    private lateinit var landmark: Landmark
    private var ignoreWikiErrorsCount = 0

    private val searchService = SearchService()

    override fun onCreated() {
        super.onCreated()
        hideAllButtons()

        searchService.onStarted = {
            showProgress()
        }

        searchService.onCompleted = onCompleted@{ results, reason, _ ->
            val gemError = SdkError.fromInt(reason)
            if (gemError == SdkError.Cancel) return@onCompleted

            hideProgress()

            if (gemError != SdkError.NoError) {
                Toast.makeText(context, "Search failed: $gemError", Toast.LENGTH_SHORT).show()
                return@onCompleted
            }

            if (results.size == 0) return@onCompleted

            val value = results[0]

            SdkCall.execute { doStart(value) }
        }
    }

    override fun onMapFollowStatusChanged(following: Boolean) {}

    override fun doStart() {
        val inLandmark = IntentHelper.getObjectForKey(EXTRA_LANDMARK) as Landmark?
        
        SdkCall.execute {
            if (inLandmark == null) {
                val text = "Tokyo"
                val coords = Coordinates(35.682838439941406, 139.75946044921875)
                doSearch(text, coords)

                return@execute
            }

            doStart(inLandmark)
        }
    }

    override fun doStop() {
        wikiView?.stopRequesting()

        SdkCall.execute {
            searchService.cancelSearch()
        }
    }

    private fun doStart(landmark: Landmark) {
        SdkCall.checkCurrentThread()
        this.landmark = landmark

        ignoreWikiErrorsCount = 3 // reset

        val mainMap = GEMApplication.getMainMapView()
        SdkCall.execute {
            val animation = Animation()
            animation.setType(EAnimation.AnimationLinear)
            val coords = landmark.getCoordinates() ?: return@execute

            val areaIsEmpty = landmark.getContourGeograficArea()?.isEmpty() ?: true

            if (areaIsEmpty) {
                val simplePinLandmark = SdkCall.execute { Landmark("", coords) }
                simplePinLandmark?.run {
                    ImageDatabase().getImageById(SdkIcons.Other_UI.Search_Results_Pin.value)?.let {
                        setImage(it)
                    }
                    mainMap?.activateHighlightLandmarks(arrayListOf(simplePinLandmark))
                }
            } else {
                val settings = HighlightRenderSettings()
                settings.setOptions(EHighlightOptions.ShowContour.value)
                mainMap?.activateHighlightLandmarks(arrayListOf(landmark), settings)
            }

            GEMApplication.addLandmarkToHistory(landmark)

            mainMap?.centerOnCoordinates(coords, -1, Xy(), animation)
        }

        wikiView.onWikiFetchCompleteCallback = callback@{ reason: Int, _: String ->
            val gemError = SdkError.fromInt(reason)
            if (gemError != SdkError.NoError) {
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

        showCloseButton()
    }

    @Suppress("SameParameterValue")
    private fun doSearch(text: String, coords: Coordinates) {
        SdkCall.execute {
            searchService.preferences.setSearchMapPOIs(true)
            searchService.searchByFilter(text, coords)
        }
    }


    private fun showCloseButton() {
        ButtonsDecorator.buttonAsStop(context, closeButton) {
            doBackPressed()
        }

        closeButton?.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_LANDMARK = "landmark"
    }
}
