/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselectioncompose.data

import androidx.compose.ui.graphics.ImageBitmap
import com.magiclane.sdk.d3scene.OverlayItem

// The tapped public transport station shown by the half screen station panel. The overlay item
// is kept so the realtime stop data (lines and departures) can be re-requested every minute.
data class PublicTransportStationInfo(
    val overlayItem: OverlayItem,
    val name: String = "",
    val address: String = "",
    val icon: ImageBitmap? = null,
    // Offset between the station's wall clock and UTC (null when the timezone lookup failed),
    // needed to compare the SDK's wall-clock-encoded departure times with "now".
    val utcOffsetMs: Long? = null,
)
