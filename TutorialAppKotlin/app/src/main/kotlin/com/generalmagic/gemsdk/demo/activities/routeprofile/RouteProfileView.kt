package com.generalmagic.gemsdk.demo.activities.routeprofile

import android.content.res.Configuration
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.generalmagic.gemsdk.TRectF
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.app.IMapControllerActivity
import com.generalmagic.gemsdk.demo.util.AppUtils
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.app_bar_layout.*
import kotlinx.android.synthetic.main.route_profile.view.*

class RouteProfileView(val parent: IMapControllerActivity) {
    private val routeProfilePanelFactor = 0.5
    private val routeProfileLandscapePanelFactor = 1.0
    private val fullMapViewCoords = TRectF(0.0f, 0.0f, 1.0f, 1.0f)
    private val topUpMapViewCoords = TRectF(0.0f, 0.5f, 1.0f, 1.0f)
    private val rightMapViewCoords = TRectF(0.5f, 0.0f, 1.0f, 1.0f)
    private val mShowHideAnimationDelay = 300L

    private lateinit var routeProfile: ElevationProfile

    fun showRouteProfile() {
        parent.run {
            getRouteProfileView()?.let { routeProfileView ->
                routeProfileView.visibility = View.VISIBLE
                routeProfile = ElevationProfile(
                    this as AppCompatActivity,
                    routeProfileView,
                    AppUtils.getSizeInPixelsFromMM(40)
                )

                val title = GEMSdkCall.execute { GEMRouteProfileView.getTitle() }
                route_profile.header_title_fragment.text = title

                adjustViewForOrientation(GEMApplication.appResources().configuration.orientation)
            }
        }
    }

    fun hideRouteProfile() {
        parent.run {
            GEMApplication.getMainMapView()?.let { mapView ->
                GEMSdkCall.execute {
                    mapView.resize(fullMapViewCoords)
                    GEMRouteProfileView.flyToRoute(mapView.preferences()?.routes()?.getMainRoute())
                }
            }
            GEMApplication.postOnMainDelayed({
                getRouteProfileView()?.let { routeProfileView ->
                    routeProfileView.visibility = View.GONE
                }
            }, mShowHideAnimationDelay)
        }
    }

    fun refreshRouteProfile() {
        routeProfile.refresh()
    }

    fun updateElevationChartPinPosition(position: Double) {
        routeProfile.didUpdatePinPosition(position)
    }

    fun updateElevationChartInterval(minX: Double, maxX: Double) {
        routeProfile.updateElevationChartInterval(minX, maxX)
    }

    fun getRouteProfilePanelFactor(): Double {
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
                        routeProfileView.elevation_chart.layoutParams.height =
                            AppUtils.getSizeInPixelsFromMM(33)

                        GEMApplication.postOnMainDelayed({
                            GEMApplication.getMainMapView()?.let { mapView ->
                                GEMSdkCall.execute {
                                    mapView.resize(rightMapViewCoords)
                                    GEMRouteProfileView.flyToRoute(
                                        GEMApplication.getMainMapView()?.preferences()?.routes()
                                            ?.getMainRoute()
                                    )
                                }
                            }
                        }, mShowHideAnimationDelay)
                    }

                    Configuration.ORIENTATION_PORTRAIT -> {
                        routeProfileView.layoutParams.width =
                            ConstraintLayout.LayoutParams.MATCH_PARENT
                        routeProfileView.elevation_chart.layoutParams.height =
                            AppUtils.getSizeInPixelsFromMM(40)

                        GEMApplication.postOnMainDelayed({
                            GEMApplication.getMainMapView()?.let { mapView ->
                                GEMSdkCall.execute {
                                    mapView.resize(topUpMapViewCoords)
                                    GEMRouteProfileView.flyToRoute(
                                        GEMApplication.getMainMapView()?.preferences()?.routes()
                                            ?.getMainRoute()
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
