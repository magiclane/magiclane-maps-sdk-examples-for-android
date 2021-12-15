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

package com.generalmagic.sdk.examples.androidAuto.model

import android.graphics.Bitmap
import androidx.car.app.model.CarColor
import com.generalmagic.sdk.routesandnavigation.EDriveSide
import com.generalmagic.sdk.routesandnavigation.ETurnEvent

class UINavigationData {
    var remainingDistanceInMeters: Long = 0L
    var remainingTimeInSeconds: Long = 0L
    var etaTimeMillis: Long = 0L
    var currentStep: UIStepData? = null
    var nextStep: UIStepData? = null
    var junctionImage: Bitmap? = null

    var remainingDistanceColor: CarColor? = CarColor.GREEN
    var remainingTimeColor: CarColor? = CarColor.GREEN
}

data class UIRouteModel(
    var title: String,
    var totalDistance: Int,
    var totalTime: Long,
    var descriptionColor: Int
)

data class UIStepData(
    var turnInstruction: String? = null,
    var roadName: String? = null,
    var lanesImage: Bitmap? = null,
    var distanceToNextTurnInMeters: Long? = null,
    var maneuver: UIManeuverData? = null
)

data class UIManeuverData(
    var turnEvent: ETurnEvent? = null,
    var turnImage: Bitmap? = null,
    var driveSide: EDriveSide? = null,
    var roundaboutExitNumber: Int? = null
)