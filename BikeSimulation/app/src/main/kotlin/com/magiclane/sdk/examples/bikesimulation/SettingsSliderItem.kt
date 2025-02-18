// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.bikesimulation

// -------------------------------------------------------------------------------------------------

data class SettingsSliderItem(
    override val title: String = "",
    val valueFrom: Float = 0f,
    var value: Float = 0f,
    val valueTo: Float = 0f,
    val unit: String = "",
    val callback: (Float) -> Unit
) : SettingsItem(title)
// -------------------------------------------------------------------------------------------------
