/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemo

data class SettingsSwitchItem(
    override val title: String = "",
    val itIs: Boolean = false,
    val callback: (Boolean) -> Unit,
) : SettingsItem(title)
