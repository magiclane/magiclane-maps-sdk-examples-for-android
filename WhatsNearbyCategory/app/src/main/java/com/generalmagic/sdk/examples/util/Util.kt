/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.util

import com.generalmagic.sdk.core.CommonSettings
import com.generalmagic.sdk.core.EUnitSystem
import com.generalmagic.sdk.core.LocalizationManager
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.sensordatasource.PositionService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.StringIds
import java.util.*

object Util {
    private const val MILES_TO_METERS = 1609.26939169
    private const val METERS_TO_MILES = 0.0006214
    private const val METERS_TO_FT = 3.2808
    private const val MILES_TO_YARDS = 1760

    fun getMyPosition(): Coordinates? = SdkCall.execute {
        val position = PositionService().getPosition()
        val latitude = position?.getLatitude() ?: 0.0
        val longitude = position?.getLongitude() ?: 0.0

        return@execute Coordinates(latitude, longitude)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun getUIString(strId: StringIds): String {
        return SdkCall.execute { LocalizationManager().getString(strId) } ?: ""
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun getDistText(
        mmeters: Int,
        unitsSystem: EUnitSystem,
        bHighResolution: Boolean = false,
        bCapitalizeUnit: Boolean = false
    ): Pair<String, String> {
        var meters = mmeters
        var distance = ""
        var unit: String

        if (unitsSystem == EUnitSystem.Metric) {
            val upperLimit: Double = if (bHighResolution) 100000.0 else 20000.0

            if (meters >= upperLimit) {
                // >20 km - 1 km accuracy - KM
                meters = ((meters + 500) / 1000) * 1000

                distance = "${meters.toFloat() / 1000}"
                unit = getUIString(StringIds.eStrKm)
            } else if ((meters >= 1000) && (meters < upperLimit)) {
                // 1 - 20 km - 0.1 km accuracy - KM
                meters = ((meters + 50) / 100) * 100

                distance = String.format("%.1f", meters.toFloat() / 1000)
                unit = getUIString(StringIds.eStrKm)
            } else if ((meters >= 500) && (meters < 1000)) {
                // 500 - 1,000 m - 50 m accuracy - M
                meters = ((meters + 25) / 50) * 50

                if (meters == 1000) {
                    distance = String.format("%.1f", meters.toFloat() / 1000)
                    unit = getUIString(StringIds.eStrKm)
                } else {
                    distance = String.format("%d", meters)
                    unit = getUIString(StringIds.eStrMeter)
                }
            } else if ((meters >= 200) && (meters < 500)) {
                // 200 - 500 m - 25 m accuracy - M
                meters = ((meters + 12) / 25) * 25

                distance = String.format("%d", meters)
                unit = getUIString(StringIds.eStrMeter)
            } else if ((meters >= 100) && (meters < 200)) {
                // 100 - 200 m - 10 m accuracy - M
                meters = ((meters + 5) / 10) * 10

                distance = String.format("%d", meters)
                unit = getUIString(StringIds.eStrMeter)
            } else {
                // 0 - 100 m - 5 m accuracy - M
                meters = ((meters + 2) / 5) * 5

                distance = String.format("%d", meters)
                unit = getUIString(StringIds.eStrMeter)
            }
        } else {
            val upperLimit: Double = if (bHighResolution) 100.0 else 20.0

            var miles = meters.toDouble() * METERS_TO_MILES
            var fMilesThreshold = 0.25f
            var yardsOrFeet: Double

            unit = getUIString(StringIds.eStrYd)

            if (unitsSystem == EUnitSystem.ImperialUk) {
                yardsOrFeet = miles * MILES_TO_YARDS
            } else {
                fMilesThreshold = 0.1f
                yardsOrFeet = miles * MILES_TO_METERS * METERS_TO_FT
                unit = getUIString(StringIds.eStrFt)
            }

            if (miles >= upperLimit) {
                // >20 m - 1 m accuracy - M
                distance = String.format("%d", (miles + 0.5f).toInt())
                unit = getUIString(StringIds.eStrMi)
            } else if ((miles >= 1) && (miles < upperLimit)) {
                // 1 - 20 m - 0.1 m accuracy - M
                miles = (miles / 0.1f + 0.5f)
                miles *= 0.1

                distance = String.format("%.1f", miles)
                unit = getUIString(StringIds.eStrMi)
            } else if ((miles >= fMilesThreshold) && (miles < 1)) {
                // 0.25 - 1.00 m - 0.05 m accuracy - M
                miles = (miles / 0.05f + 0.5f)
                miles *= 0.05f

                distance = String.format("%.2f", miles)
                unit = getUIString(StringIds.eStrMi)
            } else if (miles < fMilesThreshold) {
                if (yardsOrFeet >= 250) {
                    // 250 - 400 y - 25 y accuracy - YD
                    yardsOrFeet = ((yardsOrFeet + 12) / 25)
                    yardsOrFeet *= 25

                    distance = String.format("%d", yardsOrFeet)
                } else if (yardsOrFeet >= 100 && yardsOrFeet < 250) {
                    // 100 - 250 y - 10 y accuracy - YD
                    yardsOrFeet = ((yardsOrFeet + 5) / 10)
                    yardsOrFeet *= 10

                    distance = String.format("%d", yardsOrFeet)
                } else {
                    // 0 - 100 y - 5 m accuracy - YD
                    yardsOrFeet = ((yardsOrFeet + 2) / 5)
                    yardsOrFeet *= 5

                    distance = String.format("%d", yardsOrFeet)
                }
            }
        }

        if (bCapitalizeUnit) {
            unit.toUpperCase(Locale.getDefault())
        }

        SdkCall.execute {
            val decimalSeparator = CommonSettings.getDecimalSeparator()
            val groupingSeparator = CommonSettings.getDigitGroupSeparator()

            if (distance.indexOf(',') >= 0) {
                distance.replace(',', decimalSeparator)
            } else // if (distance.find(".") >= 0)
            {
                distance.replace('.', decimalSeparator)
            }

            var index: Int = distance.indexOf(decimalSeparator)
            if (index < 0) {
                index = distance.length
            }

            for ((j, i) in (index - 1 downTo 0).withIndex()) {
                val nRemaining: Int = j % 3
                if (nRemaining == 0) {
                    distance.format(
                        "${
                            distance.substring(0, i)
                        }${groupingSeparator}${distance.subSequence(i, distance.length)}"
                    )
                }
            }
        }

        return Pair(distance, unit)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
