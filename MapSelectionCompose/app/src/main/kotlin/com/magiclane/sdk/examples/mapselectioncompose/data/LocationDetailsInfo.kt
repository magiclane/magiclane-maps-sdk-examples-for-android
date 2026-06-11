/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselectioncompose.data

import androidx.compose.ui.graphics.ImageBitmap

data class LocationDetailsInfo(
    val image: ImageBitmap? = null,
    val text: String = "",
    val description: String = "",
)
