/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

package com.magiclane.sdk.examples.androidauto.androidAuto.model

import androidx.car.app.navigation.model.Maneuver
import com.magiclane.sdk.routesandnavigation.EDriveSide
import com.magiclane.sdk.routesandnavigation.ETurnEvent

object ManeuverExtension {
    fun from(value: ETurnEvent, driverSide: EDriveSide): Int {
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
