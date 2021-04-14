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
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkIcons
import com.generalmagic.sdk.util.StringIds
import java.util.*

class Util {
    companion object {
        ////////////////////////////////////////////////////////////////////////////////////////////////

        fun formatRouteName(route: Route): String {
            val timeDistance = route.getTimeDistance() ?: return ""
            val distInMeters = timeDistance.getTotalDistance()
            val timeInSeconds = timeDistance.getTotalTime()

            val distTextPair = getDistText(
                distInMeters,
                CommonSettings.getUnitSystem(),
                bHighResolution = true
            )

            val timeTextPair = getTimeText(timeInSeconds)

            return String.format(
                "${distTextPair.first} ${distTextPair.second} \n " +
                    "${timeTextPair.first} ${timeTextPair.second} %%%%0%%%% %%%%1%%%%"
            )
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////

        private fun getDistText(
            meters: Int,
            unitsSystem: EUnitSystem,
            bHighResolution: Boolean = false,
            bCapitalizeUnit: Boolean = false
        ): Pair<String, String> {
            var mmmeters = meters
            var distance = ""
            val unit = getUIString(StringIds.eStrKm)

            if (unitsSystem == EUnitSystem.Metric) {
                val upperLimit: Double = if (bHighResolution) 100000.0 else 20000.0

                if (mmmeters >= upperLimit) {
                    // >20 km - 1 km accuracy - KM
                    mmmeters = ((mmmeters + 500) / 1000) * 1000

                    distance = "${mmmeters.toFloat() / 1000}"
                } else if ((mmmeters >= 1000) && (mmmeters < upperLimit)) {
                    // 1 - 20 km - 0.1 km accuracy - KM
                    mmmeters = ((mmmeters + 50) / 100) * 100

                    distance = String.format("%.1f", mmmeters.toFloat() / 1000)
                } else if ((mmmeters >= 500) && (mmmeters < 1000)) {
                    // 500 - 1,000 m - 50 m accuracy - M
                    mmmeters = ((mmmeters + 25) / 50) * 50

                    distance = if (mmmeters == 1000) {
                        String.format("%.1f", mmmeters.toFloat() / 1000)
                    } else {
                        String.format("%d", mmmeters)
                    }
                } else if ((mmmeters >= 200) && (mmmeters < 500)) {
                    // 200 - 500 m - 25 m accuracy - M
                    mmmeters = ((mmmeters + 12) / 25) * 25

                    distance = String.format("%d", mmmeters)
                } else if ((mmmeters >= 100) && (mmmeters < 200)) {
                    // 100 - 200 m - 10 m accuracy - M
                    mmmeters = ((mmmeters + 5) / 10) * 10

                    distance = String.format("%d", mmmeters)
                } else {
                    // 0 - 100 m - 5 m accuracy - M
                    mmmeters = ((mmmeters + 2) / 5) * 5

                    distance = String.format("%d", mmmeters)
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

        private fun getTimeText(
            timeInSeconds: Int,
            bForceHours: Boolean = false,
            bCapitalizeResult: Boolean = false
        ): Pair<String, String> {
            return SdkCall.execute {
                val time: String
                val unit: String
                if (timeInSeconds == 0) {
                    time = "0"
                    unit = getUIString(StringIds.eStrTimeMin)

                    return@execute Pair(time, unit)
                }

                var nMin: Int
                val nHour: Int

                if ((timeInSeconds >= 3600) || bForceHours) {
                    nMin = timeInSeconds / 60
                    nHour = nMin / 60
                    nMin -= (nHour * 60)

                    time = String.format("%d:%02d", nHour, nMin)
                    unit = getUIString(StringIds.eStrHoursAbbrev)
                } else if (timeInSeconds >= 60) {
                    nMin = timeInSeconds / 60
                    time = String.format("%d", nMin)

                    unit = getUIString(StringIds.eStrTimeMin)
                } else {
                    time = String.format("%d", 1)
                    unit = getUIString(StringIds.eStrTimeMin)
                }

                if (bCapitalizeResult) {
                    time.toUpperCase(Locale.getDefault())
                    unit.toUpperCase(Locale.getDefault())
                }

                return@execute Pair(time, unit)
            } ?: Pair("", "")
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////

        private fun getUIString(strId: StringIds): String {
            return SdkCall.execute { LocalizationManager().getString(strId) } ?: ""
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////

        fun getFerryIconId(): Int {
            return SdkIcons.RoutePreviewBubble.Icon_Ferry.value
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////

        fun getTollIconId(): Int {
            return SdkIcons.RoutePreviewBubble.Icon_Toll.value
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////
    }
}

