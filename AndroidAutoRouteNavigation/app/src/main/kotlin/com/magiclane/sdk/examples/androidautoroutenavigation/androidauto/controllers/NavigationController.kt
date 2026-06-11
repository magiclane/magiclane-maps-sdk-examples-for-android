/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("unused")

package com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.d3scene.MapCamera
import com.magiclane.sdk.examples.androidautoroutenavigation.R
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.Service
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.UIActionModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.screens.NavigationScreen
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.util.CarAppNotifications
import com.magiclane.sdk.examples.androidautoroutenavigation.app.AppProcess
import com.magiclane.sdk.examples.androidautoroutenavigation.services.NavigationInstance
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.PermissionsListener
import com.magiclane.sdk.util.SdkCall

class NavigationController(context: CarContext, private val startNavWithRoute: Route?) :
    NavigationScreen(context) {

    private var permissionListener: PermissionsListener? = null

    override fun onCreate() {
        super.onCreate()

        SdkCall.execute { NavigationInstance.stopNavigation() }

        val permissions = NavigationInstance.permissionsRequired

        val onPermissionsGranted = {
            SdkCall.execute {
                startNavWithRoute?.let {
                    NavigationInstance.startNavigation(it)
                    // NavigationInstance.startSimulation(it)
                }
            }
        }

        val alreadyHave = PermissionsHelper.hasPermissions(context, permissions, true)
        if (!alreadyHave) {
            // Wait for the user to grant permissions before starting navigation.
            permissionListener = object : PermissionsListener() {
                override fun onPermissionsStatusChanged() {
                    val have = PermissionsHelper.hasPermissions(context, permissions, true)
                    if (have) {
                        PermissionsHelper.instance?.removeListener(this)
                        onPermissionsGranted()
                    }
                }
            }
            PermissionsHelper.instance?.addListener(permissionListener!!)
        } else {
            onPermissionsGranted()
        }
    }

    override fun onDestroy() {
        permissionListener?.let { PermissionsHelper.instance?.removeListener(it) }
        Service.session?.navigationManager?.navigationEnded()
        CarAppNotifications.eraseNotification()
    }

    override fun onResume() {
        super.onResume()
        SdkCall.execute {
            mapView?.let { if (!it.isFollowingPosition()) it.followPosition() }
            updateActionStrips(isFollowing = true)
        }
    }

    // Mirrors the "×" button in the bottom navigation panel (handled by Service.onStopNavigation).
    override fun onBackPressed() {
        NavigationInstance.stopNavigation()
        Service.pop(true)
    }

    override fun updateData() {}

    override fun updateMapView() {
        SdkCall.execute {
            val sharedCamera: MapCamera? = AppProcess.sharedCamera
            if (sharedCamera != null) {
                mapView?.camera = sharedCamera
                return@execute
            }

            mapView?.hideRoutes()
            NavigationInstance.currentRoute?.let { route ->
                mapView?.preferences?.routes?.add(route, true)
            }
        }
    }

    override fun onMapFollowChanged(following: Boolean) {
        updateActionStrips(isFollowing = following)
        invalidate()
    }

    fun onNavigationStarted() {
        SdkCall.execute {
            mapView?.let { if (!it.isFollowingPosition()) it.followPosition() }
        }
        Service.session?.navigationManager?.navigationStarted()
    }

    // Rebuilds both action strips based on whether the map is currently following the user's position.
    // When following: show the report button. When not following: show the re-centre button.
    private fun updateActionStrips(isFollowing: Boolean) {
        actionStripModelList.clear()
        mapActionStripModelList.clear()

        // Top-right strip: back button stops navigation and pops to root.
        actionStripModelList.add(UIActionModel.backModel())

        // Side strip: pan toggle + context-sensitive action.
        mapActionStripModelList.add(UIActionModel.panModel())
        mapActionStripModelList.add(
            if (isFollowing) {
                UIActionModel(
                    iconId = R.drawable.report_24px,
                    onClicked = onClicked@{
                        if (Service.topScreen != this) return@onClicked
                        Service.pushScreen(ReportCategoriesController(context))
                    },
                )
            } else {
                UIActionModel(
                    iconId = R.drawable.ic_gps_fixed_white_24dp,
                    onClicked = onClicked@{
                        if (Service.topScreen != this) return@onClicked
                        SdkCall.execute { mapView?.followPosition() }
                    },
                )
            },
        )

        invalidate()
    }
}
