/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemo

import android.graphics.Bitmap

// Represents a POI category chip in the horizontal bar.
data class CategoryItem(
    val name: String,
    val icon: Bitmap?,
    val landmarkStoreId: Int,
    val categoryId: Int,
)
