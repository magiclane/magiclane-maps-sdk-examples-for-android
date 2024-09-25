// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
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
