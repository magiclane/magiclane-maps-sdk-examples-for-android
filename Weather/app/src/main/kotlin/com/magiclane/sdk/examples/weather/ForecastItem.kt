// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.weather

import android.graphics.Bitmap

data class ForecastItem(
    val time: String = "",
    val dayOfWeek: String = "",
    val date: String = "",
    val bmp: Bitmap? = null,
    val temperature: String = "",
    val highTemperature: String = "",
    val lowTemperature: String = "",
    val conditionName: String = "",
    val conditionValue: String = "",
    val isDuringDay: Boolean = false
)
