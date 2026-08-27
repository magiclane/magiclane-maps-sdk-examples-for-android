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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.magiclane.sdk.compose.components.transit.PTStationPanel
import com.magiclane.sdk.compose.components.transit.PTStationPanelData
import com.magiclane.sdk.compose.components.transit.ptPalette
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.compose.map.ObstructionEdge
import com.magiclane.sdk.compose.map.mapObstruction
import com.magiclane.sdk.examples.mapselectioncompose.PTUi.lineName
import com.magiclane.sdk.examples.mapselectioncompose.data.PublicTransportStationInfo
import kotlinx.coroutines.delay

// Half screen panel of a public transport station, shown alongside the map. The UI is the
// library's PTStationPanel; this composable owns what is SDK- and model-coupled: the realtime
// refresh loop, the line chips selection (mirrored on the map's route shapes), the mapping of
// the SDK stop data to the library's presentation data, and the map obstruction registration
// that keeps the logo / centering clear of the panel. Every change of the chips selection (an
// empty set means "all lines") is reported to the model.
@Composable
fun PublicTransportStationScreen(
    modifier: Modifier = Modifier,
    viewModel: MapSelectionModel,
    mapState: GemMapState,
    station: PublicTransportStationInfo,
    isLandscape: Boolean,
) {
    val stopInfo = viewModel.ptStopInfo ?: return
    val context = LocalContext.current
    val palette = ptPalette()

    // "Now" is advanced together with the realtime refresh so the relative departure labels
    // ("5 min") stay fresh even when a refresh delivers no new data.
    var now by remember { mutableStateOf(PTUi.wallClockNow(station.utcOffsetMs)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PTUi.REFRESH_INTERVAL_MS)
            now = PTUi.wallClockNow(station.utcOffsetMs)
            viewModel.refreshPublicTransportStation()
        }
    }

    // Keyed on the station: tapping another station on the (still visible) map replaces the
    // panel's content, and the fresh station starts with the "all lines" selection.
    var selectedLines by remember(station) { mutableStateOf(setOf<String>()) }

    // One chip per distinct line crossing the station; a single-line station gets no chips at
    // all, just like the native location details controller.
    val lines = remember(stopInfo, palette) {
        stopInfo.stops
            .flatMap { it.routes }
            .distinctBy { it.lineName }
            .filter { it.lineName.isNotEmpty() }
            .map { route -> PTUi.line(route, palette) }
            .let { if (it.size == 1) emptyList() else it }
    }

    // Lines that disappeared from the refreshed data no longer count as selected.
    val activeSelection = selectedLines intersect lines.map { it.name }.toSet()

    // Mirror the chip selection on the map: only the selected lines' shapes stay drawn. The
    // model ignores the initial "all lines" report — it draws that itself when the station opens.
    LaunchedEffect(activeSelection) {
        viewModel.onPublicTransportLinesSelectionChanged(activeSelection)
    }

    // Departures after applying the selected lines filter.
    val allTrips = stopInfo.trips
    val trips = if (activeSelection.isEmpty()) allTrips else allTrips.filter { it.lineName in activeSelection }

    // Alerts scoped to the station itself (closure, elevator outage, ...) are shown as a note
    // under the header. Alert instances are shared within a response, so distinct()
    // deduplicates the ones referenced by several stops.
    val stationAlerts = remember(stopInfo, now) {
        PTUi.activeAlerts(stopInfo.stops.flatMap { it.alerts }.distinct(), now)
    }

    PTStationPanel(
        data = PTStationPanelData(
            name = station.name,
            address = station.address,
            icon = station.icon,
            alertsText = stationAlerts.joinToString("\n") { PTUi.alertText(it) },
            alertsColor = PTUi.alertColor(palette, stationAlerts),
            lines = lines,
            departures = trips.map { PTUi.departureData(context, it, now, palette) },
        ),
        onClose = { viewModel.closePublicTransportStationView() },
        // The panel obstructs the map along its bottom edge in portrait and, as a left-side
        // card, along the start edge in landscape — GemMap keeps the Magic Lane logo and the
        // camera centering clear of it.
        modifier = modifier.mapObstruction(
            mapState,
            if (isLandscape) ObstructionEdge.Start else ObstructionEdge.Bottom,
        ),
        selectedLines = activeSelection,
        onLineSelectionChange = { selectedLines = it },
        // The current (filtered) departures are handed over so the trip view can page through
        // the other trips of the same line.
        onDepartureClick = { index -> viewModel.openPublicTransportTripView(trips, index) },
        isLandscape = isLandscape,
    )
}
