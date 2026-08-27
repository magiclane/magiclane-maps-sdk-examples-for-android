/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselectioncompose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.magiclane.sdk.compose.components.transit.PTTripScreen
import com.magiclane.sdk.compose.components.transit.PTTripScreenData
import com.magiclane.sdk.compose.components.transit.ptPalette
import com.magiclane.sdk.d3scene.PTTrip
import com.magiclane.sdk.examples.mapselectioncompose.PTUi.lineName
import kotlinx.coroutines.delay

// Collects the trips shown as pages: the tapped trip plus other trips of the same line — first
// the upcoming ones, then, if fewer than the maximum, the previous ones (which shift the initial
// page index so the tapped trip stays selected). Returns the pages and that initial index.
private fun buildTripPages(trips: List<PTTrip>, tappedIndex: Int): Pair<List<PTTrip>, Int> {
    val pages = mutableListOf<PTTrip>()
    var currentPageIndex = 0
    val tappedLine = trips[tappedIndex].lineName

    for (i in tappedIndex until trips.size) {
        if (trips[i].lineName == tappedLine) {
            pages.add(trips[i])
            if (pages.size == PTUi.MAX_TRIP_PAGES) break
        }
    }

    if (pages.size < PTUi.MAX_TRIP_PAGES) {
        for (i in tappedIndex - 1 downTo 0) {
            if (trips[i].lineName == tappedLine) {
                pages.add(0, trips[i])
                currentPageIndex++
                if (pages.size == PTUi.MAX_TRIP_PAGES) break
            }
        }
    }

    return pages to currentPageIndex
}

// The public transport vehicle stations view, mirroring the native public transport vehicle
// controller. The UI is the library's PTTripScreen; this composable owns what is SDK- and
// model-coupled: collecting the trips of the tapped line as pages, the realtime refresh loop
// (with the fresh trips swapped into the pages), and the mapping of the SDK trips to the
// library's presentation data.
@Composable
fun PublicTransportTripScreen(
    modifier: Modifier = Modifier,
    viewModel: MapSelectionModel,
    trips: List<PTTrip>,
    tappedIndex: Int,
    stationUtcOffsetMs: Long?,
) {
    if (trips.isEmpty()) return
    val context = LocalContext.current
    val palette = ptPalette()

    val safeTappedIndex = tappedIndex.coerceIn(trips.indices)
    val tappedTrip = trips[safeTappedIndex]

    // One page per trip of the tapped line; pages[initialPageIndex] is the tapped trip.
    val initial = remember(trips, safeTappedIndex) { buildTripPages(trips, safeTappedIndex) }
    val pages = remember(trips, safeTappedIndex) { initial.first.toMutableStateList() }

    // "Now" is advanced together with the realtime refresh so the statuses, recomputed at
    // composition time, stay fresh even for unmatched (finished) trips.
    var now by remember { mutableStateOf(PTUi.wallClockNow(stationUtcOffsetMs)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PTUi.REFRESH_INTERVAL_MS)
            now = PTUi.wallClockNow(stationUtcOffsetMs)
            viewModel.refreshPublicTransportStation()
        }
    }

    // Swaps in the fresh trips after every refresh, matched by route id and trip index.
    val stopInfo = viewModel.ptStopInfo
    LaunchedEffect(stopInfo) {
        stopInfo ?: return@LaunchedEffect
        for (i in pages.indices) {
            stopInfo.trips.firstOrNull {
                it.route.routeId == pages[i].route.routeId && it.tripIndex == pages[i].tripIndex
            }?.let { pages[i] = it }
        }
    }

    PTTripScreen(
        data = PTTripScreenData(
            vehicleType = PTUi.vehicleType(tappedTrip.route.routeType),
            line = PTUi.line(tappedTrip.route, palette),
            heading = tappedTrip.route.heading ?: "",
            // The View-based MapSelection example shows the agency's website in an in-app web
            // view — here the link is handed over to the browser instead.
            agencyName = tappedTrip.agency.name,
            agencyUrl = tappedTrip.agency.url,
            pages = pages.map { PTUi.tripPageData(context, it, now, palette) },
            initialPage = initial.second,
        ),
        onClose = { viewModel.closePublicTransportTripView() },
        modifier = modifier,
    )
}
