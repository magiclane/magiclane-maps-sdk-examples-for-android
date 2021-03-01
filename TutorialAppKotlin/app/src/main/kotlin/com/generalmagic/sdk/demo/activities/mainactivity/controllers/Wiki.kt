/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.demo.activities.mainactivity.controllers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import com.generalmagic.sdk.*
import com.generalmagic.sdk.core.Coordinates
import com.generalmagic.sdk.core.ImageDatabase
import com.generalmagic.sdk.core.Landmark
import com.generalmagic.sdk.core.Xy
import com.generalmagic.sdk.d3scene.Animation
import com.generalmagic.sdk.d3scene.EAnimation
import com.generalmagic.sdk.d3scene.EHighlightOptions
import com.generalmagic.sdk.d3scene.HighlightRenderSettings
import com.generalmagic.sdk.demo.app.GEMApplication
import com.generalmagic.sdk.demo.app.MapLayoutController
import com.generalmagic.sdk.demo.app.elements.ButtonsDecorator
import com.generalmagic.sdk.demo.util.IntentHelper
import com.generalmagic.sdk.searching.SearchPreferences
import com.generalmagic.sdk.util.GEMSdkCall
import com.generalmagic.sdk.util.SdkError
import com.generalmagic.sdk.util.SdkIcons
import kotlinx.android.synthetic.main.location_details_panel.view.*

class WikiController(context: Context, attrs: AttributeSet?) :
    MapLayoutController(context, attrs) {
    private lateinit var landmark: Landmark
    private var ignoreWikiErrorsCount = 0

    private val preferences = SearchPreferences()
    private val search = object : SearchServiceWrapper() {
        override fun onSearchStarted() {
            showProgress()
        }

        override fun onSearchCompleted(reason: Int) {
            val gemError = SdkError.fromInt(reason)
            if (gemError == SdkError.KCancel) return

            hideProgress()

            if (gemError != SdkError.KNoError) {
                Toast.makeText(context, "Search failed: $gemError", Toast.LENGTH_SHORT).show()
                return
            }

            val results = GEMSdkCall.execute { this.results.asArrayList() }
            if (results == null || results.size == 0) return

            val value = results[0]

            doStart(value)
        }
    }

    override fun onCreated() {
        super.onCreated()
        hideAllButtons()
    }

    override fun onMapFollowStatusChanged(following: Boolean) {}

    override fun doStart() {
        val inLandmark = IntentHelper.getObjectForKey(EXTRA_LANDMARK) as Landmark?
        if (inLandmark == null) {
            val text = "Tokyo"
            val coords = Coordinates(35.682838439941406, 139.75946044921875)
            doSearch(text, coords)

            return
        }

        doStart(inLandmark)
    }

    override fun doStop() {
        wikiView?.stopRequesting()

        GEMSdkCall.execute {
            search.service.cancelSearch(search.listener)
        }
    }

    fun doStart(landmark: Landmark) {
        this.landmark = landmark

        ignoreWikiErrorsCount = 3 // reset

        val mainMap = GEMApplication.getMainMapView()
        GEMSdkCall.execute {
            val animation = Animation()
            animation.setMethod(EAnimation.EAnimationFly)
            val coords = landmark.getCoordinates() ?: return@execute

            val areaIsEmpty = landmark.getContourGeograficArea()?.isEmpty() ?: true

            if (areaIsEmpty) {
                val simplePinLandmark = GEMSdkCall.execute { Landmark("", coords) }
                simplePinLandmark?.run {
                    ImageDatabase().getImageById(SdkIcons.Other_UI.Search_Results_Pin.value)?.let {
                        setImage(it)
                    }
                    mainMap?.activateHighlightLandmarks(arrayListOf(simplePinLandmark))
                }
            } else {
                val settings = HighlightRenderSettings()
                settings.setOptions(EHighlightOptions.EHO_ShowContour.value)
                mainMap?.activateHighlightLandmarks(arrayListOf(landmark), settings)
            }

            GEMApplication.addLandmarkToHistory(landmark)

            mainMap?.centerOnCoordinates(coords, -1, Xy(), animation)
        }

        wikiView.onWikiFetchCompleteCallback = callback@{ reason: Int, _: String ->
            val gemError = SdkError.fromInt(reason)
            if (gemError != SdkError.KNoError) {
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

    private fun doSearch(text: String, coords: Coordinates) {
        GEMSdkCall.execute {
            preferences.setReferencePoint(coords)
            preferences.setSearchMapPOIs(true)
            search.service.searchByFilter(search.results, search.listener, text, preferences)
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
