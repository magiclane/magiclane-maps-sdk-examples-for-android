/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

@file:Suppress("unused")

package com.magiclane.sdk.examples.androidauto.androidAuto.model

import android.graphics.Bitmap
import android.os.Build
import androidx.car.app.CarContext
import androidx.car.app.model.CarColor
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.Lane
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import com.magiclane.sdk.examples.androidauto.androidAuto.util.Util
import com.magiclane.sdk.routesandnavigation.EDriveSide
import com.magiclane.sdk.routesandnavigation.ETurnEvent
import java.time.Duration
import java.util.TimeZone
import kotlin.math.roundToInt

data class UIRouteModel(
    var title: String,
    var totalDistance: Int,
    var totalTime: Long,
    var descriptionColor: CarColor? = null
)

class CarNavigationData {
    var remainingDistanceInMeters: Long = 0L
    var remainingTimeInSeconds: Long = 0L
    var etaTimeMillis: Long = 0L

    var step: UIStepData? = null
    var nextStep: UIStepData? = null

    var junctionImage: Bitmap? = null

    var destinationName: String? = null

    var remainingDistanceColor: CarColor? = CarColor.GREEN
    var remainingTimeColor: CarColor? = CarColor.GREEN

    fun getTrip(context: CarContext): Trip? {
        try {
            val builder = Trip.Builder()

            step?.toStep(context)?.let { carStep ->
                step?.getTravelEstimate()?.let { estimate ->
                    builder.addStep(carStep, estimate)
                }
            }

            getTravelEstimate()?.let { destinationTravelEstimate ->
                val destination = Destination.Builder()
                destination.setName(destinationName ?: "-") //TODO:

                builder.addDestination(destination.build(), destinationTravelEstimate)
            }
            builder.setLoading(false)
            step?.roadName?.let {
                builder.setCurrentRoad(it)
            }

            return builder.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getRoutingInfo(context: CarContext): RoutingInfo? {
        try {
            val builder = RoutingInfo.Builder()

            val isLoadingNavInfo = false//currentStep == null && nextStep == null

            step?.toStep(context)?.let { carStep ->
                step?.getTravelEstimate()?.remainingDistance?.let { stepDistance ->
                    builder.setCurrentStep(carStep, stepDistance)
                }
            }

            nextStep?.toStep(context)?.let { builder.setNextStep(it) }

            junctionImage?.let {
                val carIcon = Util.asCarIcon(context, it) ?: return@let
                builder.setJunctionImage(carIcon)
            }
            builder.setLoading(isLoadingNavInfo)

            return builder.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    fun getTravelEstimate(): TravelEstimate? {
        try {
            val remainingTimeInSeconds = remainingTimeInSeconds
            val remainingDistanceInMeters = remainingDistanceInMeters
            val timeMillis = etaTimeMillis

            if (remainingTimeInSeconds <= 0L || remainingDistanceInMeters <= 0L || timeMillis <= 0L)
                return null

            val rawOffset = TimeZone.getDefault().rawOffset
            val noOffsetTimeMillis = timeMillis - rawOffset

            val arrivalTime = DateTimeWithZone.create(noOffsetTimeMillis, TimeZone.getDefault())
            val remainingDistance = if (remainingDistanceInMeters >= 1000) {
                Distance.create(
                    remainingDistanceInMeters.toDouble() / 1000,
                    Distance.UNIT_KILOMETERS
                )
            } else
                Distance.create(remainingDistanceInMeters.toDouble(), Distance.UNIT_METERS)

            val builder = TravelEstimate.Builder(remainingDistance, arrivalTime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val remainingTime = Duration.ofSeconds(remainingTimeInSeconds)
                builder.setRemainingTime(remainingTime)
            }
            remainingDistanceColor?.let {
                builder.setRemainingDistanceColor(it)
            }
            remainingTimeColor?.let {
                builder.setRemainingTimeColor(it)
            }

            return builder.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}

data class UIStepData(
    var turnInstruction: String? = null,
    var roadName: String? = null,
    var lanesImage: Bitmap? = null,
    var distanceToStepInMeters: Long? = null,
    var remainingTimeInSeconds: Long? = null,
    var maneuver: UIManeuverData? = null,
    var distanceToNextTurn: String? = null,
    var distanceToNextTurnUnit: String? = null,
) {
    fun toStep(context: CarContext): Step? {
        try {
            val builder = Step.Builder()
            turnInstruction?.let { builder.setCue(it) }
            roadName?.let { builder.setRoad(it) }

            maneuver?.toManeuver(context)?.let { builder.setManeuver(it) }

            Util.asCarIcon(context, lanesImage)?.let {
                builder.setLanesImage(it)

                val laneBuilder = Lane.Builder()
//            laneBuilder.addDirection()

                builder.addLane(laneBuilder.build())
            }

            return builder.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    fun getTravelEstimate(): TravelEstimate? {
        try {
            val distance = getDistance() ?: return null

            val rawOffset = TimeZone.getDefault().rawOffset
            val noOffsetTimeMillis = remainingTimeInSeconds ?: 0L - rawOffset

            val arrivalTime = DateTimeWithZone.create(noOffsetTimeMillis, TimeZone.getDefault())
            return TravelEstimate.Builder(distance, arrivalTime).build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getDistance(): Distance? {
        try {
            val distanceToNextTurnInMeters = distanceToStepInMeters ?: 0L
            return if (distanceToNextTurnInMeters >= 1000) {
                Distance.create(
                    distanceToNextTurnInMeters.toDouble() / 1000,
                    Distance.UNIT_KILOMETERS
                )
            } else {
                val roundedDist = (distanceToNextTurnInMeters.toDouble() / 50).roundToInt() * 50
                Distance.create(roundedDist.toDouble(), Distance.UNIT_METERS)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}

data class UIManeuverData(
    var turnEvent: ETurnEvent? = null,
    var turnImage: Bitmap? = null,
    var driveSide: EDriveSide? = null,
    var roundaboutExitNumber: Int? = null
) {
    fun toManeuver(context: CarContext): Maneuver? {
        try {
            val icon = Util.asCarIcon(context, turnImage)

            val turnEvent = turnEvent ?: return null
            val driverSide = driveSide ?: EDriveSide.Right
            val maneuverType = ManeuverExtension.from(turnEvent, driverSide)

            val builder = Maneuver.Builder(maneuverType)
            icon?.let { builder.setIcon(it) }

            if (isValidTypeWithExitNumber(maneuverType)) {
//            builder.setRoundaboutExitAngle()

                roundaboutExitNumber?.let {
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

    fun isValidTypeWithExitNumber(@Maneuver.Type type: Int): Boolean {
        return type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE
    }
}