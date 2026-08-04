/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.markerclustering

import org.json.JSONObject

/**
 * One value per campsite. The two-way icon split is driven by [isBookable]
 * (the `bookable` flag in campsites.geojson): green = bookable, red = not.
 */
data class CampsiteMarkerDescriptor(
    val id: Int, // campsiteId
    val name: String,
    val isBookable: Boolean,
    val latitude: Double,
    val longitude: Double,
) {
    /**
     * Marker name carried into the SDK. Encoded as JSON so the tap handler can
     * recover id / name / bookable straight from the marker's name string — the
     * only per-marker metadata channel the SDK exposes.
     */
    val markerName: String
        get() = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("bookable", isBookable)
        }.toString()
}
