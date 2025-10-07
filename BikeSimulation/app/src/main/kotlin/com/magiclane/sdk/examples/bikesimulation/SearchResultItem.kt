// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.bikesimulation

// -------------------------------------------------------------------------------------------------

import android.graphics.Bitmap

// -------------------------------------------------------------------------------------------------

data class SearchResultItem (
    var bmp : Bitmap? = null,
    var text : String? = null,
    val lat : Double?,
    val lon : Double?
)
// -------------------------------------------------------------------------------------------------
