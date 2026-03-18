/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemo

import android.graphics.Bitmap
import com.magiclane.sdk.places.Landmark

data class SearchResultItem(
    val bmp: Bitmap? = null,
    val text: String? = null,
    val subText: String? = null,
    val landmark: Landmark
)
