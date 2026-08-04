/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselectioncompose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magiclane.sdk.d3scene.PTTrip
import com.magiclane.sdk.examples.mapselectioncompose.PTUi.lineName
import com.magiclane.sdk.examples.mapselectioncompose.data.PublicTransportStationInfo
import kotlinx.coroutines.delay
import java.util.Date

// Line chips crossing a station (empty when the station is served by a single line).
private data class PTLineItem(val name: String, val backgroundColor: Color, val textColor: Color)

// Half screen panel of a public transport station, shown alongside the map: a full-width bottom
// panel in portrait, a half-width card spanning the full screen height in landscape. The header
// keeps the usual location details look (name on the first line, address on the second),
// followed by a horizontal selectable list of the lines crossing the station and the list of
// upcoming departures. The realtime data is refreshed every minute, mirroring the native
// location details controller. Every change of the line chips selection (an empty set means
// "all lines") is reported to the model, which mirrors the filter on the map's route shapes.
@Composable
fun PublicTransportStationScreen(
    modifier: Modifier = Modifier,
    viewModel: MapSelectionModel,
    station: PublicTransportStationInfo,
    isLandscape: Boolean,
) {
    val stopInfo = viewModel.ptStopInfo ?: return
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
            .map { route ->
                val background = PTUi.parseColor(route.routeColor, palette.gray)
                PTLineItem(
                    name = route.lineName,
                    backgroundColor = background,
                    textColor = PTUi.contrastingTextColor(
                        background,
                        PTUi.parseColor(route.routeTextColor, Color.White),
                    ),
                )
            }
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

    Surface(
        modifier = modifier.onGloballyPositioned { coordinates ->
            // Report the panel extent so the model keeps the logo / centering clear of it.
            val width = coordinates.size.width
            val height = coordinates.size.height
            val changed = if (isLandscape) {
                viewModel.panelWidthPx != width
            } else {
                viewModel.panelHeightPx != height
            }
            if (changed) {
                viewModel.panelWidthPx = width
                viewModel.panelHeightPx = height
                viewModel.updateMapAreas()
            }
        },
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        // Content must clear the system bars on the sides the panel touches: both sides in
        // portrait (full-width bottom panel), only the left in landscape (the panel hugs the
        // left edge); there it also reaches the top of the screen, so the header additionally
        // clears the status bar.
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout)
                        .only(if (isLandscape) WindowInsetsSides.Start else WindowInsetsSides.Horizontal),
                ),
        ) {
            // Header: same composition as the map details panel — icon, station name on the
            // first line, address on the second line, close button.
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (isLandscape) {
                                Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        station.icon?.let {
                            Image(
                                it,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(40.dp),
                            )
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            Text(
                                station.name,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (station.address.isNotEmpty()) {
                                Text(station.address, color = palette.gray, fontSize = 16.sp)
                            }
                        }
                        IconButton(
                            onClick = { viewModel.closePublicTransportStationView() },
                            modifier = Modifier
                                .padding(10.dp)
                                .size(48.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.cancel_24px),
                                contentDescription = stringResource(R.string.close),
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }

                    // Service alerts scoped to the station itself; text and icon are tinted by
                    // the most severe alert.
                    if (stationAlerts.isNotEmpty()) {
                        val alertColor = PTUi.alertColor(palette, stationAlerts)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_pt_alert_24),
                                contentDescription = stringResource(R.string.pt_service_alert),
                                tint = alertColor,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stationAlerts.joinToString("\n") { PTUi.alertText(it) },
                                color = alertColor,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // Subheader: horizontal, selectable list of the lines crossing this station.
            // Unselected chips are dimmed while a selection is active.
            if (lines.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyRow(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp),
                    ) {
                        items(lines) { line ->
                            Box(Modifier.padding(4.dp)) {
                                PTLineBadge(
                                    name = line.name,
                                    backgroundColor = line.backgroundColor,
                                    textColor = line.textColor,
                                    minWidth = 56.dp,
                                    modifier = Modifier
                                        .alpha(
                                            if (activeSelection.isEmpty() || line.name in activeSelection) 1f else 0.5f,
                                        )
                                        .clickable {
                                            selectedLines = if (line.name in selectedLines) {
                                                selectedLines - line.name
                                            } else {
                                                selectedLines + line.name
                                            }
                                        },
                                )
                            }
                        }
                    }
                    if (activeSelection.isNotEmpty()) {
                        IconButton(
                            onClick = { selectedLines = emptySet() },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(40.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.cancel_24px),
                                contentDescription = stringResource(R.string.pt_clear_line_selection),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Upcoming departures at this station.
            if (trips.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.pt_no_departures),
                        color = palette.gray,
                        fontSize = 16.sp,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = WindowInsets.systemBars
                        .only(WindowInsetsSides.Bottom)
                        .asPaddingValues(),
                ) {
                    itemsIndexed(trips) { index, trip ->
                        PTDepartureRow(
                            trip = trip,
                            now = now,
                            palette = palette,
                            // The current (filtered) departures are handed over so the trip
                            // view can page through the other trips of the same line.
                            onClick = { viewModel.openPublicTransportTripView(trips, index) },
                        )
                    }
                }
            }
        }
    }
}

// One upcoming departure: vehicle icon, line badge and destination on the first row, status and
// accessibility / crowding / alert icons on the second, the departure time at the trailing edge.
@Composable
private fun PTDepartureRow(
    trip: PTTrip,
    now: Date,
    palette: PTPalette,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    // isCancelled is the authoritative "don't ride this" flag; the departure keeps its slot but
    // is marked red with a struck-through time.
    val isCancelled = trip.isCancelled == true
    val statusColor = if (isCancelled) {
        palette.statusLate
    } else {
        PTUi.statusColor(
            palette,
            MaterialTheme.colorScheme.onBackground,
            trip.hasRealtime,
            trip.delayMinutes ?: 0,
        )
    }
    val statusText = PTUi.tripStatus(context, trip.departureTime, now, trip.stopPlatformCode, isCancelled)
    val (time, unit) = PTUi.departureLabel(context, trip.departureTime, now)

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(PTUi.vehicleIconRes(trip.route.routeType)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(28.dp),
                )
                val background = PTUi.parseColor(trip.route.routeColor, palette.gray)
                PTLineBadge(
                    name = trip.lineName,
                    backgroundColor = background,
                    textColor = PTUi.contrastingTextColor(
                        background,
                        PTUi.parseColor(trip.route.routeTextColor, Color.White),
                    ),
                    modifier = Modifier.padding(start = 10.dp),
                )
                Text(
                    trip.route.heading ?: "",
                    modifier = Modifier.padding(start = 10.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (statusText.isNotEmpty()) {
                    Text(statusText, color = statusColor, fontSize = 13.sp)
                }
                if (trip.isWheelchairAccessible) {
                    Icon(
                        painter = painterResource(R.drawable.ic_wheelchair_24),
                        contentDescription = stringResource(R.string.pt_wheelchair_accessible),
                        tint = palette.gray,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(22.dp),
                    )
                }
                if (trip.isBikeAllowed) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bike_24),
                        contentDescription = stringResource(R.string.pt_bikes_allowed),
                        tint = palette.gray,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(22.dp),
                    )
                }
                // Live crowding of the vehicle, when the realtime producer reports it.
                PTUi.crowdingLevel(trip)?.let { crowding ->
                    Icon(
                        painter = painterResource(R.drawable.ic_pt_crowding_24),
                        contentDescription = PTUi.crowdingLabel(context, crowding),
                        tint = PTUi.crowdingColor(palette, crowding),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(22.dp),
                    )
                }
                // Service alerts applying to this departure; the note itself is on the trip view.
                val alerts = PTUi.activeAlerts(trip.alerts, now)
                if (alerts.isNotEmpty()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pt_alert_24),
                        contentDescription = stringResource(R.string.pt_service_alert),
                        tint = PTUi.alertColor(palette, alerts),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(22.dp),
                    )
                }
            }
        }
        Column(
            Modifier.padding(start = 10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                time,
                color = statusColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = if (isCancelled) TextDecoration.LineThrough else null,
            )
            if (unit.isNotEmpty()) {
                Text(unit, color = statusColor, fontSize = 13.sp)
            }
        }
    }
}

// Rounded rectangle chip used for line names and badges. A subtle outline is added when the
// badge color would blend into the surface it sits on.
@Composable
fun PTLineBadge(
    name: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    minWidth: Dp = 40.dp,
) {
    val shape = RoundedCornerShape(12.dp)
    val needsOutline = backgroundColor == MaterialTheme.colorScheme.surface ||
        backgroundColor == MaterialTheme.colorScheme.background

    Text(
        name,
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .then(if (needsOutline) Modifier.border(1.dp, ptPalette().gray, shape) else Modifier)
            .defaultMinSize(minWidth = minWidth)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = textColor,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}
