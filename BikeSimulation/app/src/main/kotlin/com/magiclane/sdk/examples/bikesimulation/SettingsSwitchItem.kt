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

data class SettingsSwitchItem(
    override val title: String = "",
    val itIs : Boolean = false,
    val callback: (Boolean) -> Unit
):SettingsItem(title)
// -------------------------------------------------------------------------------------------------
