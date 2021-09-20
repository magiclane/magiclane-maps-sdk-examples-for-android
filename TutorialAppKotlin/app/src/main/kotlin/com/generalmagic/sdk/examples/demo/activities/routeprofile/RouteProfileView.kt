/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.routeprofile

import android.content.res.Configuration
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.generalmagic.sdk.core.RectF
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.app.IMapControllerActivity
import com.generalmagic.sdk.examples.demo.util.AppUtils
import com.generalmagic.sdk.util.SdkCall
import com.github.mikephil.charting.charts.CombinedChart

class RouteProfileView(val parent: IMapControllerActivity) {
    private val routeProfilePanelFactor = 0.5
    private val routeProfileLandscapePanelFactor = 1.0
    private lateinit var fullMapViewCoords: RectF
    private lateinit var topUpMapViewCoords: RectF
    private lateinit var rightMapViewCoords: RectF
    private val mShowHideAnimationDelay = 300L

    private lateinit var routeProfile: ElevationProfile
    lateinit var route_profile: RelativeLayout

    init {
        SdkCall.execute {
            fullMapViewCoords = RectF(0.0f, 0.0f, 1.0f, 1.0f)
            topUpMapViewCoords = RectF(0.0f, 0.5f, 1.0f, 1.0f)
            rightMapViewCoords = RectF(0.5f, 0.0f, 1.0f, 1.0f)
        }
    }

    fun showRouteProfile() {
        parent.run {
            getRouteProfileView()?.let { routeProfileView ->
                routeProfileView.visibility = View.VISIBLE
                routeProfile = ElevationProfile(
                    this as AppCompatActivity,
                    routeProfileView,
                    AppUtils.getSizeInPixelsFromMM(40)
                )

                val title = SdkCall.execute { GEMRouteProfileView.getTitle() }
                routeProfileView.findViewById<TextView>(R.id.header_title_fragment).text = title

                adjustViewForOrientation(GEMApplication.appResources().configuration.orientation)
            }
        }
    }

    fun hideRouteProfile() {
        parent.run {
            GEMApplication.getMainMapView().let { mapView ->
                SdkCall.execute {
                    mapView?.resize(fullMapViewCoords)
                    GEMRouteProfileView.flyToRoute(mapView?.preferences?.routes?.mainRoute)
                }
            }
            GEMApplication.postOnMainDelayed({
                getRouteProfileView()?.let { routeProfileView ->
                    routeProfileView.visibility = View.GONE
                }
            }, mShowHideAnimationDelay)
        }
    }

    @Suppress("unused")
    fun refreshRouteProfile() {
        routeProfile.refresh()
    }

    fun updateElevationChartPinPosition(position: Double) {
        routeProfile.didUpdatePinPosition(position)
    }

    fun updateElevationChartInterval(minX: Double, maxX: Double) {
        routeProfile.updateElevationChartInterval(minX, maxX)
    }

    private fun getRouteProfilePanelFactor(): Double {
        return if (GEMApplication.appResources().configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            routeProfilePanelFactor
        } else {
            routeProfileLandscapePanelFactor
        }
    }

    fun adjustViewForOrientation(orientation: Int) {
        parent.run {
            val displayMetrics = GEMApplication.appResources().displayMetrics
            val barWidth = (displayMetrics.widthPixels) * routeProfilePanelFactor

            getRouteProfileView()?.let { routeProfileView ->
                val parentView = routeProfileView.parent
                if (parentView is ConstraintLayout) {
                    val constraintSet = ConstraintSet()
                    constraintSet.clone(parentView)

                    val barMaxHeight = (displayMetrics.heightPixels) * getRouteProfilePanelFactor()
                    constraintSet.constrainMaxHeight(R.id.route_profile, barMaxHeight.toInt())
                    constraintSet.applyTo(parentView)
                }

                when (orientation) {

                    Configuration.ORIENTATION_LANDSCAPE -> {
                        routeProfileView.layoutParams.width = barWidth.toInt()
                        routeProfileView.findViewById<CombinedChart>(R.id.elevation_chart).layoutParams.height =
                            AppUtils.getSizeInPixelsFromMM(33)

                        GEMApplication.postOnMainDelayed({
                            GEMApplication.getMainMapView().let { mapView ->
                                SdkCall.execute {
                                    mapView?.resize(rightMapViewCoords)
                                    GEMRouteProfileView.flyToRoute(
                                        GEMApplication.getMainMapView()?.preferences?.routes
                                            ?.mainRoute
                                    )
                                }
                            }
                        }, mShowHideAnimationDelay)
                    }

                    Configuration.ORIENTATION_PORTRAIT -> {
                        routeProfileView.layoutParams.width =
                            ConstraintLayout.LayoutParams.MATCH_PARENT
                        routeProfileView.findViewById<CombinedChart>(R.id.elevation_chart).layoutParams.height =
                            AppUtils.getSizeInPixelsFromMM(40)

                        GEMApplication.postOnMainDelayed({
                            GEMApplication.getMainMapView().let { mapView ->
                                SdkCall.execute {
                                    mapView?.resize(topUpMapViewCoords)
                                    GEMRouteProfileView.flyToRoute(
                                        GEMApplication.getMainMapView()?.preferences?.routes
                                            ?.mainRoute
                                    )
                                }
                            }
                        }, mShowHideAnimationDelay)
                    }

                    else -> {
                    }
                }
            }
        }
    }
}
