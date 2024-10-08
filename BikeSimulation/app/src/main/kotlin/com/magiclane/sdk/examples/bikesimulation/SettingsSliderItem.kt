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

package com.magiclane.sdk.examples.bikesimulation

data class SettingsSliderItem(
    override val title: String = "",
    val valueFrom: Float = 0f,
    var value: Float = 0f,
    val valueTo: Float = 0f,
    val unit: String = "",
    val callback: (Float) -> Unit
) : SettingsItem(title)
