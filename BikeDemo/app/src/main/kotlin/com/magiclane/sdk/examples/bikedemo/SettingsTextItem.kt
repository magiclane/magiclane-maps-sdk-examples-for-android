/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemo

data class SettingsTextItem(
    override val title: String = "",
    val value: String = "",
    val callback: () -> Unit,
) : SettingsItem(title)
