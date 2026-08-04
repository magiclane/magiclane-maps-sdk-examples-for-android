/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselectioncompose

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.magiclane.sdk.d3scene.PTTrip
import com.magiclane.sdk.examples.mapselectioncompose.PTUi.lineName
import kotlinx.coroutines.delay
import java.util.Date

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

// The public transport vehicle stations view: shows the stations of the tapped trip in a pager
// with dot indicators, together with up to four other trips of the same line (previous and
// upcoming), mirroring the native public transport vehicle controller.
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
    val pagerState = rememberPagerState(initialPage = initial.second) { pages.size }

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

    // Accessibility, crowding and alerts differ between the trips of the line, so they follow
    // the currently visible page rather than the tapped trip.
    val currentTrip = pages[pagerState.currentPage.coerceIn(pages.indices)]

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal),
                ),
        ) {
            // Header: vehicle icon, line badge, destination, accessibility icons, close.
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                        .padding(bottom = 10.dp),
                ) {
                    Row {
                        Row(
                            Modifier
                                .weight(1f)
                                .padding(start = 10.dp, top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(PTUi.vehicleIconRes(tappedTrip.route.routeType)),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(28.dp),
                            )
                            val background = PTUi.parseColor(tappedTrip.route.routeColor, palette.gray)
                            PTLineBadge(
                                name = tappedTrip.lineName,
                                backgroundColor = background,
                                textColor = PTUi.contrastingTextColor(
                                    background,
                                    PTUi.parseColor(tappedTrip.route.routeTextColor, Color.White),
                                ),
                                modifier = Modifier.padding(start = 10.dp),
                            )
                            Text(
                                tappedTrip.route.heading ?: "",
                                modifier = Modifier.padding(start = 10.dp),
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { viewModel.closePublicTransportTripView() },
                            modifier = Modifier
                                .padding(4.dp)
                                .size(48.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.cancel_24px),
                                contentDescription = stringResource(R.string.close),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }

                    // Accessibility and live crowding of the trip shown by the current page.
                    val crowding = PTUi.crowdingLevel(currentTrip)
                    if (currentTrip.isWheelchairAccessible || currentTrip.isBikeAllowed || crowding != null) {
                        Row(
                            Modifier.padding(start = 48.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (currentTrip.isWheelchairAccessible) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_wheelchair_24),
                                    contentDescription = stringResource(R.string.pt_wheelchair_accessible),
                                    tint = palette.gray,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            if (currentTrip.isBikeAllowed) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_bike_24),
                                    contentDescription = stringResource(R.string.pt_bikes_allowed),
                                    tint = palette.gray,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .size(22.dp),
                                )
                            }
                            crowding?.let {
                                Icon(
                                    painter = painterResource(R.drawable.ic_pt_crowding_24),
                                    contentDescription = PTUi.crowdingLabel(context, it),
                                    tint = PTUi.crowdingColor(palette, it),
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .size(22.dp),
                                )
                            }
                        }
                    }

                    // The alerts note also carries the cancellation: isCancelled is the
                    // authoritative flag, shown even when the feed delivers no explaining
                    // NoService alert.
                    val alerts = PTUi.activeAlerts(currentTrip.alerts, now)
                    val isCancelled = currentTrip.isCancelled == true
                    if (isCancelled || alerts.isNotEmpty()) {
                        val noteColor = if (isCancelled) {
                            palette.statusLate
                        } else {
                            PTUi.alertColor(palette, alerts)
                        }
                        Column(Modifier.padding(top = 4.dp)) {
                            if (isCancelled) {
                                PTAlertRow(stringResource(R.string.pt_cancelled), null, noteColor)
                            }
                            for (alert in alerts) {
                                PTAlertRow(PTUi.alertName(alert), PTUi.alertDescription(alert), noteColor)
                            }
                        }
                    }
                }
            }

            // One dot per trip of this line; visible only when there is more than one trip.
            if (pages.size > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pages.size) { index ->
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(8.dp)
                                .background(
                                    if (index == pagerState.currentPage) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        palette.gray
                                    },
                                    CircleShape,
                                ),
                        )
                    }
                }
            }

            HorizontalDivider()

            // One page per trip: a vertical list of its stations.
            val agencyName = tappedTrip.agency.name
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                PTStopsList(
                    trip = pages[page],
                    now = now,
                    palette = palette,
                    // The bottom inset is carried by the agency button when it is shown.
                    applyBottomInset = agencyName.isEmpty(),
                )
            }

            // The operating agency; tapping it opens the agency's website. The View-based
            // MapSelection example shows it in an in-app web view — here the link is handed
            // over to the browser instead.
            if (agencyName.isNotEmpty()) {
                val agencyUrl = tappedTrip.agency.url
                Text(
                    agencyName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !agencyUrl.isNullOrEmpty()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, agencyUrl!!.toUri()))
                        }
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                        .padding(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// One row of the alerts note. An alert carrying a description gets an info icon after its
// name; tapping it (or the name) opens the description in a popup. The description arrives
// as HTML (<p>, <strong>, ...), so it is rendered rather than shown verbatim — fromHtml turns
// its links into LinkAnnotation.Url entries, which Compose opens in the browser by default.
@Composable
private fun PTAlertRow(name: String, description: String?, color: Color) {
    var showDescription by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_pt_alert_24),
            contentDescription = stringResource(R.string.pt_service_alert),
            tint = color,
        )
        // weight(fill = false) keeps the info icon right after the name, yet a long name
        // never pushes it off-screen.
        Text(
            name,
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f, fill = false)
                .clickable(enabled = description != null) { showDescription = true },
            color = color,
            fontSize = 13.sp,
        )
        if (description != null) {
            IconButton(
                onClick = { showDescription = true },
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(28.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pt_info_24),
                    contentDescription = stringResource(R.string.pt_alert_details),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }

    if (showDescription && description != null) {
        AlertDialog(
            onDismissRequest = { showDescription = false },
            confirmButton = {
                TextButton(onClick = { showDescription = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text(name) },
            text = {
                Text(
                    AnnotatedString.fromHtml(
                        description,
                        TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary)),
                    ),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
        )
    }
}

// The stations of a single trip, drawn along a timeline tinted with the route color.
@Composable
private fun PTStopsList(
    trip: PTTrip,
    now: Date,
    palette: PTPalette,
    applyBottomInset: Boolean,
) {
    val contentPadding = if (applyBottomInset) {
        WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues()
    } else {
        PaddingValues(0.dp)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(trip.stopTimes.size) { index ->
            PTStopRow(trip, index, now, palette)
        }
    }
}

@Composable
private fun PTStopRow(trip: PTTrip, index: Int, now: Date, palette: PTPalette) {
    val context = LocalContext.current
    val stop = trip.stopTimes[index]
    val isLastStop = index == trip.stopTimes.size - 1
    val departed = stop.departureTime?.before(now) == true
    val routeColor = PTUi.parseColor(trip.route.routeColor, MaterialTheme.colorScheme.primary)

    // Stations already passed are dimmed; their timeline segment is faded even more so the
    // crossed part of the line clearly stands apart from the remaining route.
    val contentAlpha = if (stop.isBefore) 0.5f else 1f
    val timelineAlpha = if (stop.isBefore) 0.25f else 1f

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .height(IntrinsicSize.Min),
    ) {
        // Vertical timeline tinted with the route color; a circle marks each station. The line
        // starts at the first station's circle and ends at the last one's. Each row's segment
        // is a rect tiling exactly edge to edge — a round-capped line would overhang the row
        // bounds, and on the translucent crossed part the overlapping neighbors double up into
        // darker marks at the row boundaries. Rounded corners keep the line's terminal ends.
        Canvas(
            Modifier
                .padding(start = 10.dp)
                .width(14.dp)
                .fillMaxHeight(),
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val cornerRadius = CornerRadius(centerX, centerX)
            drawPath(
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(
                                left = 0f,
                                top = if (index == 0) centerY - centerX else 0f,
                                right = size.width,
                                bottom = if (isLastStop) centerY + centerX else size.height,
                            ),
                            topLeft = if (index == 0) cornerRadius else CornerRadius.Zero,
                            topRight = if (index == 0) cornerRadius else CornerRadius.Zero,
                            bottomLeft = if (isLastStop) cornerRadius else CornerRadius.Zero,
                            bottomRight = if (isLastStop) cornerRadius else CornerRadius.Zero,
                        ),
                    )
                },
                color = routeColor.copy(alpha = routeColor.alpha * timelineAlpha),
            )
            drawCircle(
                color = if (stop.isBefore) Color.White.copy(alpha = 0.5f) else Color.White,
                radius = 5.dp.toPx(),
                center = Offset(centerX, centerY),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
                    .alpha(contentAlpha),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            stop.stopName,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            PTUi.stopStatus(context, departed, isLastStop),
                            color = PTUi.statusColor(
                                palette,
                                MaterialTheme.colorScheme.onBackground,
                                stop.hasRealtime,
                                stop.delay,
                            ),
                            fontSize = 13.sp,
                        )
                    }
                    Text(
                        PTUi.clockTime(stop.departureTime),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}
