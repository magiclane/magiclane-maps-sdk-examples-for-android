/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("unused")

package com.generalmagic.sdk.examples.androidAuto.controllers

import android.graphics.Bitmap
import androidx.car.app.CarContext
import com.generalmagic.sdk.core.Rgba
import com.generalmagic.sdk.d3scene.MapCamera
import com.generalmagic.sdk.examples.R
import com.generalmagic.sdk.examples.androidAuto.Service
import com.generalmagic.sdk.examples.androidAuto.model.UIActionModel
import com.generalmagic.sdk.examples.androidAuto.model.UIManeuverData
import com.generalmagic.sdk.examples.androidAuto.model.UINavigationData
import com.generalmagic.sdk.examples.androidAuto.model.UIStepData
import com.generalmagic.sdk.examples.androidAuto.screens.NavigationScreen
import com.generalmagic.sdk.examples.androidAuto.util.Icons
import com.generalmagic.sdk.examples.app.AppProcess
import com.generalmagic.sdk.examples.services.NavigationInstance
import com.generalmagic.sdk.routesandnavigation.ETurnEvent
import com.generalmagic.sdk.routesandnavigation.NavigationInstruction
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.TurnDetails
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
        navigationData = UINavigationData()
        SdkCall.execute {
            navigationData.remainingDistanceInMeters =
                NavigationInstance.remainingDistance.toLong()
            navigationData.remainingTimeInSeconds =
                NavigationInstance.remainingTime.toLong()
            navigationData.etaTimeMillis = NavigationInstance.eta?.longValue ?: 0L
            navigationData.currentStep = currentStep()
            navigationData.nextStep = nextStep()
        }
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
    }

    private fun updateActionsMapFollowing() {
        actionStripModelList.clear()
        mapActionStripModelList.clear()

        // top actions
        actionStripModelList.add(UIActionModel.backModel())
        actionStripModelList.add(UIActionModel(
            iconId = R.drawable.ic_baseline_settings_white_24,
            onClicked = {
                Service.show(GeneralSettingsController(context))
            }
        ))

        // side actions
        mapActionStripModelList.add(UIActionModel.panModel())
        mapActionStripModelList.add(UIActionModel(
            icon = Icons.getReportIcon(context),
            onClicked = {
                Service.show(ReportCategoriesController(context))
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
            onClicked = {
                Service.show(GeneralSettingsController(context))
            }
        ))

        // side actions
        mapActionStripModelList.add(UIActionModel.panModel())
        mapActionStripModelList.add(UIActionModel(
            iconId = R.drawable.ic_gps_fixed_white_24dp,
            onClicked = {
                SdkCall.execute { mapView?.followPosition() }
            }
        ))

        invalidate()
    }

    /// gather nav data.

    private fun currentStep(): UIStepData {
        val roadInfo = instr?.nextRoadInformation
        val roadName = if (roadInfo != null && roadInfo.size > 0)
            roadInfo.first().roadName
        else null

        val width = 500
        val height = 74
        val lanesImage = NavUtil.getLanesImage(instr, width, height)?.second

        return UIStepData(
            turnInstruction = NavUtil.getNextTurnInstruction(instr),
            roadName = roadName,
            lanesImage = lanesImage,
            instr?.timeDistanceToNextTurn?.totalDistance?.toLong(),
            maneuver = UIManeuverData(
                instr?.nextTurnDetails?.event,
                NavUtil.getTurnImage(instr?.nextTurnDetails, 128, 128),
                instr?.nextTurnDetails?.abstractGeometry?.driveSide,
                instr?.nextTurnDetails?.roundaboutExitNumber
            )
        )
    }

    private fun nextStep() = UIStepData(
        maneuver = UIManeuverData(
            turnEvent = instr?.nextNextTurnDetails?.event,
            turnImage = NavUtil.getTurnImage(
                instr?.nextNextTurnDetails,
                128,
                128
            ),
            driveSide = instr?.nextNextTurnDetails?.abstractGeometry?.driveSide,
            roundaboutExitNumber = instr?.nextNextTurnDetails?.roundaboutExitNumber
        )
    )

    private object NavUtil {
        fun getNextTurnInstruction(instr: NavigationInstruction?): String? {
            instr ?: return null

            val turnDetails = instr.nextTurnDetails
            var turnInstruction: String

            val bHasNextRoadCode = (instr.nextRoadInformation?.size ?: 0) > 0
            if (turnDetails != null &&
                (turnDetails.event == ETurnEvent.Stop || turnDetails.event == ETurnEvent.Intermediate)
            ) {
                turnInstruction = instr.nextTurnInstruction ?: ""
            } else if (instr.hasSignpostInfo()) {
                turnInstruction = instr.signpostInstruction ?: ""
                if (turnInstruction.isNotEmpty()) {
                    turnInstruction = instr.nextStreetName ?: ""
                }
            } else {
                turnInstruction = instr.nextStreetName ?: ""
            }

            if (turnInstruction.isNotEmpty() && !bHasNextRoadCode) {
                turnInstruction = instr.nextTurnInstruction ?: ""
            }

            return turnInstruction
        }

        fun getTurnImage(
            turnDetails: TurnDetails?,
            width: Int,
            height: Int
        ): Bitmap? {
            turnDetails ?: return null

            val aInner = Rgba(255, 255, 255, 255)
            val aOuter = Rgba(0, 0, 0, 255)
            val iInner = Rgba(128, 128, 128, 255)
            val iOuter = Rgba(128, 128, 128, 255)

            return turnDetails.abstractGeometryImage?.asBitmap(
                width, height, aInner, aOuter, iInner, iOuter
            )
        }

        fun getLanesImage(
            instr: NavigationInstruction?,
            width: Int,
            height: Int
        ): Pair<Int, Bitmap?>? {
            instr ?: return null
            val bkColor = Rgba(0, 0, 0, 0)
            val activeColor = Rgba(255, 255, 255, 255)
            val inactiveColor = Rgba(100, 100, 100, 255)

            return instr.laneImage?.asBitmap(
                width,
                height,
                bkColor,
                activeColor,
                inactiveColor
            ) ?: return null
        }
    }

}
