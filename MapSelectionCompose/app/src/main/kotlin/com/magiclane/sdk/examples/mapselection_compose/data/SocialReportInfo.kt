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

data class SocialReportInfo(
    val image: ImageBitmap? = null,
    val description: String = "",
    val address: String = "",
    val date: String = "",
    val score: String = ""
)
