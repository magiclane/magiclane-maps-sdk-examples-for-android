/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidauto.androidAuto.util

import android.graphics.Bitmap
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.examples.androidauto.androidAuto.model.CarNavigationData
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIManeuverData
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIStepData
import com.magiclane.sdk.examples.androidauto.services.NavigationInstance
import com.magiclane.sdk.routesandnavigation.ETurnEvent
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.TurnDetails
import com.magiclane.sdk.util.GemUtil


/// gather nav data.

@Suppress("MemberVisibilityCanBePrivate")
object CarNavigationDataFiller {
    fun fillNavData(navigationData: CarNavigationData) {
        navigationData.remainingDistanceInMeters =
            NavigationInstance.remainingDistance.toLong()
        navigationData.remainingTimeInSeconds =
            NavigationInstance.remainingTime.toLong()
        navigationData.etaTimeMillis = NavigationInstance.eta?.longValue ?: 0L

        navigationData.step = currentStep(NavigationInstance.currentInstruction)
        navigationData.nextStep = nextStep(NavigationInstance.currentInstruction)

        navigationData.step?.remainingTimeInSeconds = navigationData.remainingTimeInSeconds
    }

    fun currentStep(instr: NavigationInstruction?): UIStepData {
        val roadInfo = instr?.nextRoadInformation
        val roadName = if (roadInfo != null && roadInfo.size > 0)
            roadInfo.first().roadName
        else null

        val width = 500
        val height = 74
        val lanesImage = getLanesImage(instr, width, height)?.second

        val distanceInMeters = instr?.timeDistanceToNextTurn?.totalDistance?: 0

        val distText = if (distanceInMeters != -1)
            GemUtil.getDistText(distanceInMeters, EUnitSystem.Metric, true)
        else null

        return UIStepData(
            turnInstruction = getNextTurnInstruction(instr),
            roadName = roadName,
            lanesImage = lanesImage,
            distanceToStepInMeters = distanceInMeters.toLong(),
            maneuver = UIManeuverData(
                instr?.nextTurnDetails?.event,
                getTurnImage(instr?.nextTurnDetails, 128, 128),
                instr?.nextTurnDetails?.abstractGeometry?.driveSide,
                instr?.nextTurnDetails?.roundaboutExitNumber
            ),
            distanceToNextTurn = distText?.first,
            distanceToNextTurnUnit = distText?.second
        )
    }

    fun nextStep(instr: NavigationInstruction?) = UIStepData(
        maneuver = UIManeuverData(
            turnEvent = instr?.nextNextTurnDetails?.event,
            turnImage = getTurnImage(
                instr?.nextNextTurnDetails,
                128,
                128
            ),
            driveSide = instr?.nextNextTurnDetails?.abstractGeometry?.driveSide,
            roundaboutExitNumber = instr?.nextNextTurnDetails?.roundaboutExitNumber
        )
    )

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
