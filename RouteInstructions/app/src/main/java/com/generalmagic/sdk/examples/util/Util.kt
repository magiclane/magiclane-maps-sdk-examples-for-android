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
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.StringIds
import java.util.*

class Util {
    companion object {
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
            var unit = getUIString(StringIds.eStrMeter)

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
}
