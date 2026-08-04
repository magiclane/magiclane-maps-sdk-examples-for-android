/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.markerclustering

import kotlin.math.max
import kotlin.math.min

/**
 * Converts between the standard 0–22 web-map zoom scale and Magic Lane zoom
 * levels, so the clustering thresholds map onto familiar web-map zooms.
 */
object MapZoomConverter {
    /** Converts a 0–22 web-map zoom to a Magic Lane zoom level. */
    fun toMagicLaneZoom(appZoom: Double): Int {
        val clamped = min(max(appZoom, 0.0), 22.0)
        return ((clamped * 7.0) + 4.0).toInt()
    }

    /** Converts a Magic Lane zoom level back to a 0–22 web-map zoom. */
    fun fromMagicLaneZoom(magicLaneZoom: Int): Double = (magicLaneZoom - 4) / 7.0
}
