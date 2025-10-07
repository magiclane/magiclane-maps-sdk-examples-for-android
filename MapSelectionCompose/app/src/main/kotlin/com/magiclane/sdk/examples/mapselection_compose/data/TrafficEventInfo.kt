// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.mapselection_compose.data

import androidx.compose.ui.graphics.ImageBitmap

data class TrafficEventInfo(
    val image: ImageBitmap? = null,
    val description: String = "",
    val delayText: String = "",
    val delayValue: String = "",
    val delayUnit: String = "",
    val lengthText: String = "",
    val lengthValue: String = "",
    val lengthUnit: String = "",
    val fromText: String = "",
    val fromValue: String = "",
    val toText: String = "",
    val toValue: String = "",
    val validFromText: String = "",
    val validFromValue: String = "",
    val validUntilText: String = "",
    val validUntilValue: String = ""
)
