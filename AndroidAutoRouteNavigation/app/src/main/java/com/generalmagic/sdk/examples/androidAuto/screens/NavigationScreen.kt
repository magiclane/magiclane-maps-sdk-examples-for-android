/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.generalmagic.sdk.examples.androidAuto.screens

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.car.app.CarContext
import androidx.car.app.model.CarColor
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.Lane
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.Maneuver.Type
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.NavigationTemplate.NavigationInfo
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import com.generalmagic.sdk.examples.androidAuto.util.Util
import com.generalmagic.sdk.examples.androidAuto.model.UIActionModel
import com.generalmagic.sdk.examples.androidAuto.model.UIManeuverData
import com.generalmagic.sdk.examples.androidAuto.model.UINavigationData
import com.generalmagic.sdk.examples.androidAuto.model.UIStepData
import com.generalmagic.sdk.routesandnavigation.EDriveSide
import com.generalmagic.sdk.routesandnavigation.ETurnEvent
import java.time.Duration
import java.util.TimeZone
import kotlin.math.roundToInt

abstract class NavigationScreen(context: CarContext) : GemScreen(context) {
    var backgroundColor = CarColor.GREEN

    var navigationData = UINavigationData()
    val actionStripModelList: ArrayList<UIActionModel> = arrayListOf()
    val mapActionStripModelList: ArrayList<UIActionModel> = arrayListOf()

    // misc

    // -------------------------------

    override fun onGetTemplate(): Template {
        updateData()

        val builder = NavigationTemplate.Builder()
        builder.setBackgroundColor(backgroundColor)

        getNavigationInfo(context, navigationData)?.let { builder.setNavigationInfo(it) }

        getTravelEstimate(navigationData)?.let {
            builder.setDestinationTravelEstimate(it)
        }

        Util.getActionStrip(context, actionStripModelList)?.let { builder.setActionStrip(it) }
        Util.getActionStrip(context, mapActionStripModelList)?.let { builder.setMapActionStrip(it) }

        return builder.build()
    }

    companion object {
        // Travel Estimate

        private fun getTravelEstimate(navData: UINavigationData): TravelEstimate? {
            val remainingTimeInSeconds = navData.remainingTimeInSeconds
            val remainingDistanceInMeters = navData.remainingDistanceInMeters
            val timeMillis = navData.etaTimeMillis

            if (remainingTimeInSeconds <= 0L || remainingDistanceInMeters <= 0L || timeMillis <= 0L)
                return null

            val arrivalTime = DateTimeWithZone.create(timeMillis, TimeZone.getTimeZone("UTC"))
            val remainingDistance = if (remainingDistanceInMeters >= 1000) {
                Distance.create(
                    remainingDistanceInMeters.toDouble() / 1000,
                    Distance.UNIT_KILOMETERS
                )
            } else
                Distance.create(remainingDistanceInMeters.toDouble(), Distance.UNIT_METERS)

            val builder = TravelEstimate.Builder(remainingDistance, arrivalTime)
            if (VERSION.SDK_INT >= VERSION_CODES.O) {
                val remainingTime = Duration.ofSeconds(remainingTimeInSeconds)
                builder.setRemainingTime(remainingTime)
            }
            navData.remainingDistanceColor?.let {
                builder.setRemainingDistanceColor(it)
            }
            navData.remainingTimeColor?.let {
                builder.setRemainingTimeColor(it)
            }

            return builder.build()
        }

        // NavigationInfo

        private fun getNavigationInfo(
            context: CarContext,
            navData: UINavigationData
        ): NavigationInfo? {
            val builder = RoutingInfo.Builder()

            val currentStep = createStep(context, navData.currentStep)
            val nextStep = createStep(context, navData.nextStep)

            val isLoadingNavInfo = currentStep == null && nextStep == null

            currentStep?.let { builder.setCurrentStep(it.first, it.second) }
            nextStep?.let { builder.setNextStep(it.first) }
            navData.junctionImage?.let {
                val carIcon = Util.asCarIcon(context, it) ?: return@let
                builder.setJunctionImage(carIcon)
            }
            builder.setLoading(isLoadingNavInfo)

            try {
                return builder.build()
            } catch (e: Exception) {

            }
            return null
        }

        fun createStep(context: CarContext, data: UIStepData?): Pair<Step, Distance>? {
            data ?: return null

            try {
                val builder = Step.Builder()
                val distanceToNextTurnInMeters = data.distanceToNextTurnInMeters ?: 0L
                data.turnInstruction?.let { builder.setCue(it) }
                data.roadName?.let { builder.setRoad(it) }
                createManeuver(context, data.maneuver)?.let { builder.setManeuver(it) }

                Util.asCarIcon(context, data.lanesImage)?.let {
                    builder.setLanesImage(it)

                    val laneBuilder = Lane.Builder()
//            laneBuilder.addDirection()

                    builder.addLane(laneBuilder.build())
                }

                val remainingDistance = if (distanceToNextTurnInMeters >= 1000) {
                    Distance.create(
                        distanceToNextTurnInMeters.toDouble() / 1000,
                        Distance.UNIT_KILOMETERS
                    )
                } else {
                    val roundedDist = (distanceToNextTurnInMeters.toDouble() / 50).roundToInt() * 50
                    Distance.create(roundedDist.toDouble(), Distance.UNIT_METERS)
                }

                return Pair(builder.build(), remainingDistance)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return null
        }

        fun createManeuver(context: CarContext, navData: UIManeuverData?): Maneuver? {
            navData ?: return null

            val icon = Util.asCarIcon(context, navData.turnImage)

            val turnEvent = navData.turnEvent ?: return null
            val driverSide = navData.driveSide ?: EDriveSide.Right
            val maneuverType = turnEventToManeuver(turnEvent, driverSide)

            try {
                val builder = Maneuver.Builder(maneuverType)
                icon?.let { builder.setIcon(it) }

                if (isValidTypeWithExitNumber(maneuverType)) {
//            builder.setRoundaboutExitAngle()

                    navData.roundaboutExitNumber?.let {
                        if (it != -1) {
                            builder.setRoundaboutExitNumber(it)
                        }
                    }
                }
                return builder.build()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return null
        }

        fun isValidTypeWithExitNumber(@Type type: Int): Boolean {
            return type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE
        }

        fun turnEventToManeuver(value: ETurnEvent, driverSide: EDriveSide): Int {
            return when (value) {
                ETurnEvent.NotAvailable -> Maneuver.TYPE_UNKNOWN
//            ETurnEvent.Straight ->
                ETurnEvent.Right -> Maneuver.TYPE_TURN_NORMAL_RIGHT
//            ETurnEvent.Right1 ->
//            ETurnEvent.Right2 ->
                ETurnEvent.Left -> Maneuver.TYPE_TURN_NORMAL_LEFT
//            ETurnEvent.Left1 ->
//            ETurnEvent.Left2 ->
                ETurnEvent.LightLeft -> Maneuver.TYPE_TURN_SLIGHT_LEFT
//            ETurnEvent.LightLeft1 ->
//            ETurnEvent.LightLeft2 ->
                ETurnEvent.LightRight -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
//            ETurnEvent.LightRight1 ->
//            ETurnEvent.LightRight2 ->
                ETurnEvent.SharpRight -> Maneuver.TYPE_TURN_SHARP_RIGHT
//            ETurnEvent.SharpRight1 ->
//            ETurnEvent.SharpRight2 ->
                ETurnEvent.SharpLeft -> Maneuver.TYPE_TURN_SHARP_LEFT
//            ETurnEvent.SharpLeft1 ->
//            ETurnEvent.SharpLeft2 ->
                ETurnEvent.RoundaboutExitRight -> Maneuver.TYPE_ROUNDABOUT_EXIT_CW // TODO: is this ok?
                ETurnEvent.Roundabout ->
                    if (driverSide == EDriveSide.Left)
                        Maneuver.TYPE_ROUNDABOUT_ENTER_CW
                    else
                        Maneuver.TYPE_ROUNDABOUT_ENTER_CCW
                ETurnEvent.RoundRight -> Maneuver.TYPE_ON_RAMP_U_TURN_RIGHT
                ETurnEvent.RoundLeft -> Maneuver.TYPE_ON_RAMP_U_TURN_LEFT
//            ETurnEvent.ExitRight ->
//            ETurnEvent.ExitRight1 ->
//            ETurnEvent.ExitRight2 ->
//            ETurnEvent.InfoGeneric ->
//            ETurnEvent.DriveOn ->
//            ETurnEvent.ExitNo ->
                ETurnEvent.ExitLeft -> Maneuver.TYPE_TURN_NORMAL_LEFT
//            ETurnEvent.ExitLeft1 ->
//            ETurnEvent.ExitLeft2 ->
                ETurnEvent.RoundaboutExitLeft -> Maneuver.TYPE_ROUNDABOUT_EXIT_CCW // TODO: is this ok?
                ETurnEvent.IntoRoundabout ->
                    if (driverSide == EDriveSide.Left)
                        Maneuver.TYPE_ROUNDABOUT_EXIT_CW
                    else
                        Maneuver.TYPE_ROUNDABOUT_EXIT_CCW
//            ETurnEvent.StayOn ->
                ETurnEvent.BoatFerry -> Maneuver.TYPE_FERRY_BOAT
                ETurnEvent.RailFerry -> Maneuver.TYPE_FERRY_TRAIN
//            ETurnEvent.InfoLane ->
//            ETurnEvent.InfoSign ->
//            ETurnEvent.LeftRight ->
//            ETurnEvent.RightLeft ->
                ETurnEvent.KeepLeft -> Maneuver.TYPE_KEEP_LEFT
                ETurnEvent.KeepRight -> Maneuver.TYPE_KEEP_RIGHT
                ETurnEvent.Start -> Maneuver.TYPE_DEPART
//            ETurnEvent.Intermediate ->
                ETurnEvent.Stop -> Maneuver.TYPE_DESTINATION

                else -> Maneuver.TYPE_UNKNOWN
            }
        }
    }
}
