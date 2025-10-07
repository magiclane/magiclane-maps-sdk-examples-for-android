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

data class SafetyCameraInfo(
    val image: ImageBitmap? = null,
    val type: String = "",
    val speedLimitValue: String = "",
    val speedLimitUnit: String = "",
    val cameraStatusText: String = "",
    val cameraStatusValue: String = "",
    val drivingDirectionText: String = "",
    val drivingDirectionValue: String = "",
    val locationText: String = "",
    val locationValue: String = "",
    val towardsText: String = "",
    val towardsValue: MutableList<ECardinalDirections> = mutableListOf(),
    val addedToDatabaseText: String = "",
    val addedToDatabaseValue: String = ""
)
