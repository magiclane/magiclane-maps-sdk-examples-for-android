/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("unused")

package com.generalmagic.sdk.examples.androidauto.androidAuto.controllers

import androidx.car.app.CarContext
import com.generalmagic.sdk.d3scene.MapCamera
import com.generalmagic.sdk.examples.androidauto.R
import com.generalmagic.sdk.examples.androidauto.androidAuto.Service
import com.generalmagic.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.generalmagic.sdk.examples.androidauto.androidAuto.screens.NavigationScreen
import com.generalmagic.sdk.examples.androidauto.androidAuto.util.CarAppNotifications
import com.generalmagic.sdk.examples.androidauto.androidAuto.util.Icons
import com.generalmagic.sdk.examples.androidauto.app.AppProcess
import com.generalmagic.sdk.examples.androidauto.services.NavigationInstance
import com.generalmagic.sdk.routesandnavigation.NavigationInstruction
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.util.PermissionsHelper
import com.generalmagic.sdk.util.PermissionsListener
import com.generalmagic.sdk.util.SdkCall

class NavigationController(context: CarContext, private val startNavWithRoute: Route?) :
    NavigationScreen(context) {

    private val instr: NavigationInstruction?
        get() = NavigationInstance.currentInstruction

    private var permissionListener: PermissionsListener? = null

    override fun onCreate() {
        super.onCreate()

        SdkCall.execute {
            NavigationInstance.stopNavigation()
        }

        val permissions = NavigationInstance.permissionsRequired

        val onPermissionsGranted = {
            SdkCall.execute {
                startNavWithRoute?.let {
                    NavigationInstance.startNavigation(startNavWithRoute)
//                NavigationInstance.startSimulation(startNavWithRoute)
                }
            }
        }

        val alreadyHave = PermissionsHelper.hasPermissions(context, permissions, true)

        if (!alreadyHave) {
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
            updateActionsMapFollowing()
            mapView?.followPosition()
        }
    }

    override fun onBackPressed() {
        NavigationInstance.stopNavigation()
        Service.pop(true)
    }

    override fun updateData() {
    }

    override fun updateMapView() {
        SdkCall.execute {
            val sharedCamera: MapCamera? = AppProcess.sharedCamera
            if (sharedCamera != null) {
                mapView?.camera = sharedCamera
                return@execute
            }

            super.updateMapView()

            NavigationInstance.currentRoute?.let { route ->
                mapView?.preferences?.routes?.add(route, true)
            }
        }
    }

    override fun onMapFollowChanged(following: Boolean) {
        if (following)
            updateActionsMapFollowing()
        else
            updateActionsMapNotFollowing()

        invalidate()
    }

    fun onNavigationStarted() {
        SdkCall.execute {
            mapView?.let { mapView ->
                if (!mapView.isFollowingPosition()) {
                    mapView.followPosition()
                }
            }
        }

        Service.session?.navigationManager?.navigationStarted()
    }

    private fun updateActionsMapFollowing() {
        actionStripModelList.clear()
        mapActionStripModelList.clear()

        // top actions
        actionStripModelList.add(UIActionModel.backModel())
        actionStripModelList.add(UIActionModel(
            iconId = R.drawable.ic_baseline_settings_white_24,
            onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                Service.pushScreen(GeneralSettingsController(context))
            }
        ))

        // side actions
        mapActionStripModelList.add(UIActionModel.panModel())
        mapActionStripModelList.add(UIActionModel(
            icon = Icons.getReportIcon(context),
            onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                Service.pushScreen(ReportCategoriesController(context))
            }
        ))

        invalidate()
    }

    private fun updateActionsMapNotFollowing() {
        actionStripModelList.clear()
        mapActionStripModelList.clear()

        // top actions
        actionStripModelList.add(UIActionModel.backModel())
        actionStripModelList.add(UIActionModel(
            iconId = R.drawable.ic_baseline_settings_white_24,
            onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                Service.pushScreen(GeneralSettingsController(context))
            }
        ))

        // side actions
        mapActionStripModelList.add(UIActionModel.panModel())
        mapActionStripModelList.add(UIActionModel(
            iconId = R.drawable.ic_gps_fixed_white_24dp,
            onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                SdkCall.execute { mapView?.followPosition() }
            }
        ))

        invalidate()
    }
}
