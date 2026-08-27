/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselectioncompose

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.core.graphics.toColorInt
import com.magiclane.sdk.compose.components.transit.PTAlertData
import com.magiclane.sdk.compose.components.transit.PTDepartureData
import com.magiclane.sdk.compose.components.transit.PTLine
import com.magiclane.sdk.compose.components.transit.PTPalette
import com.magiclane.sdk.compose.components.transit.PTStopData
import com.magiclane.sdk.compose.components.transit.PTTripPageData
import com.magiclane.sdk.compose.components.transit.PTVehicleType
import com.magiclane.sdk.compose.format.ValueWithUnit
import com.magiclane.sdk.core.ETZStatus
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.TimezoneResult
import com.magiclane.sdk.core.TimezoneService
import com.magiclane.sdk.d3scene.PTAlertInfo
import com.magiclane.sdk.d3scene.PTAlertSeverityLevel
import com.magiclane.sdk.d3scene.PTOccupancyStatus
import com.magiclane.sdk.d3scene.PTRouteInfo
import com.magiclane.sdk.d3scene.PTRouteType
import com.magiclane.sdk.d3scene.PTTrip
import com.magiclane.sdk.places.Coordinates
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Presentation helpers shared by the public transport station and trip screens.
object PTUi {
    // Interval between two refreshes of the realtime station data.
    const val REFRESH_INTERVAL_MS = 60_000L

    // At most this many trips of the same line are shown as pages in the trip view.
    const val MAX_TRIP_PAGES = 5

    // The SDK delivers the station's wall-clock times encoded as UTC epochs (the native location
    // details controller prints their UTC fields directly, without any timezone conversion), so
    // they must be formatted in UTC — the device timezone would shift them a second time.
    private val clockFormat
        get() = SimpleDateFormat("H:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    // "Now" in the same wall-clock-encoded-as-UTC form as the SDK's departure times, so the two
    // are directly comparable. Uses the station's own timezone (resolved when the station was
    // opened), falling back to the device timezone when that lookup failed.
    fun wallClockNow(stationUtcOffsetMs: Long?): Date {
        val nowMs = System.currentTimeMillis()
        val offsetMs = stationUtcOffsetMs ?: TimeZone.getDefault().getOffset(nowMs).toLong()
        return Date(nowMs + offsetMs)
    }

    // Offset between the wall clock at the given coordinates and UTC, resolved through the SDK's
    // timezone service — the same lookup the native location details controller performs to get
    // the station's local time. Returns null when the timezone is unknown (e.g. no offline data).
    // Must be called on the SDK thread.
    fun stationUtcOffsetMs(coordinates: Coordinates): Long? {
        val result = TimezoneResult()
        val nowMs = System.currentTimeMillis()
        TimezoneService.getTimezoneInfoWithCoordinates(
            result,
            coordinates,
            Time().apply { setUniversalTime() },
            ProgressListener(),
            false,
        )

        val localTime = result.localTime
        if (result.status != ETZStatus.Success || localTime == null || !localTime.isValid()) return null

        return localTime.asLong() - nowMs
    }

    // The line label: route short name, falling back to the long name.
    val PTRouteInfo.lineName: String
        get() = routeShortName?.takeIf { it.isNotEmpty() } ?: routeLongName ?: ""

    val PTTrip.lineName: String
        get() = route.lineName

    // Parses a "#RRGGBB"/"RRGGBB" route color delivered by the overlay data.
    fun parseColor(hex: String?, fallback: Color): Color {
        if (hex.isNullOrEmpty()) return fallback

        return try {
            Color((if (hex.startsWith("#")) hex else "#$hex").toColorInt())
        } catch (_: IllegalArgumentException) {
            fallback
        }
    }

    // Guarantees the badge text is readable: when the delivered text color equals the
    // background color, the inverted background color is used instead.
    fun contrastingTextColor(backgroundColor: Color, textColor: Color): Color = if (backgroundColor != textColor) {
        textColor
    } else {
        Color(
            red = 1f - backgroundColor.red,
            green = 1f - backgroundColor.green,
            blue = 1f - backgroundColor.blue,
        )
    }

    // "Now" / "5 min" for departures within the next hour, absolute "H:MM" otherwise.
    // Returns the value and its unit ("min" or empty).
    fun departureLabel(context: Context, departure: Date?, now: Date): Pair<String, String> {
        departure ?: return "" to ""

        val diffSeconds = (departure.time - now.time) / 1000
        return if (diffSeconds in -30..3569) {
            when (val minutes = ((diffSeconds + 30) / 60).toInt()) {
                0 -> context.getString(R.string.pt_now) to ""
                else -> minutes.toString() to context.getString(R.string.pt_minutes_short)
            }
        } else {
            clockTime(departure) to ""
        }
    }

    // "Scheduled • 14:05 • Platform 2" status line for an upcoming departure. isCancelled is
    // the authoritative "don't ride this" flag — a NoService alert only explains why.
    fun tripStatus(context: Context, departure: Date?, now: Date, platformCode: String?, isCancelled: Boolean): String {
        departure ?: return ""

        val status = context.getString(
            when {
                isCancelled -> R.string.pt_cancelled
                departure.before(now) -> R.string.pt_departed
                else -> R.string.pt_scheduled
            },
        )
        val platform = platformCode
            ?.takeIf { it.isNotEmpty() }
            ?.let { " • " + context.getString(R.string.pt_platform, it) }
            ?: ""

        return "$status • ${clockTime(departure)}$platform"
    }

    // "Arrived" (last station) / "Departed" / "Scheduled" status of a station along a trip.
    fun stopStatus(context: Context, departed: Boolean, isLastStop: Boolean): String = context.getString(
        when {
            departed && isLastStop -> R.string.pt_arrived
            departed -> R.string.pt_departed
            else -> R.string.pt_scheduled
        },
    )

    fun clockTime(date: Date?): String = date?.let { clockFormat.format(it) } ?: ""

    // Realtime status color: blue = running early, red = delayed, green = on time,
    // default text color = no realtime data (scheduled only).
    fun statusColor(palette: PTPalette, defaultColor: Color, hasRealtime: Boolean, delayMinutes: Int): Color = when {
        !hasRealtime -> defaultColor
        delayMinutes < 0 -> palette.statusEarly
        delayMinutes > 0 -> palette.statusLate
        else -> palette.statusOnTime
    }

    // How crowded a vehicle is, bucketed from the producer's occupancy states. The GTFS-RT
    // occupancy scale is not linear, so states are grouped instead of interpolated.
    enum class CrowdingLevel { Low, Medium, High }

    // Statuses above NotAcceptingPassengers carry no usable crowding information.
    private fun usableOccupancy(status: PTOccupancyStatus?): PTOccupancyStatus? =
        status?.takeIf { it != PTOccupancyStatus.NoDataAvailable && it != PTOccupancyStatus.NotBoardable }

    // Crowding bucket of a departure: the live occupancy of the running vehicle when reported,
    // else the predicted occupancy after this stop. Null when neither is usable — every
    // crowding field is optional, older realtime producers deliver only a subset.
    fun crowdingLevel(trip: PTTrip): CrowdingLevel? = when (
        usableOccupancy(trip.vehicle?.occupancyStatus) ?: usableOccupancy(trip.departureOccupancyStatus)
    ) {
        PTOccupancyStatus.Empty, PTOccupancyStatus.ManySeatsAvailable -> CrowdingLevel.Low

        PTOccupancyStatus.FewSeatsAvailable, PTOccupancyStatus.StandingRoomOnly -> CrowdingLevel.Medium

        PTOccupancyStatus.CrushedStandingRoomOnly, PTOccupancyStatus.Full,
        PTOccupancyStatus.NotAcceptingPassengers,
        -> CrowdingLevel.High

        else -> null
    }

    fun crowdingColor(palette: PTPalette, level: CrowdingLevel): Color = when (level) {
        CrowdingLevel.Low -> palette.statusOnTime
        CrowdingLevel.Medium -> palette.alertWarning
        CrowdingLevel.High -> palette.statusLate
    }

    private fun severityRank(level: PTAlertSeverityLevel?): Int = when (level) {
        PTAlertSeverityLevel.Severe -> 2
        PTAlertSeverityLevel.Warning -> 1
        else -> 0
    }

    // The alerts currently in effect, most severe first. No active periods means always active;
    // a missing bound means open-ended. The period bounds are wall-clock-encoded like every
    // other Date of the stop response, so they are compared against wallClockNow().
    fun activeAlerts(alerts: List<PTAlertInfo>, now: Date): List<PTAlertInfo> = alerts
        .filter { alert ->
            alert.activePeriods.isEmpty() || alert.activePeriods.any { period ->
                period.start?.after(now) != true && period.end?.before(now) != true
            }
        }
        .sortedByDescending { severityRank(it.severityLevel) }

    // Color of an alert note / icon: the most severe alert of the list wins.
    fun alertColor(palette: PTPalette, alerts: List<PTAlertInfo>): Color =
        when (alerts.maxOfOrNull { severityRank(it.severityLevel) }) {
            2 -> palette.statusLate
            1 -> palette.alertWarning
            else -> palette.gray
        }

    // "SignificantDelays" -> "Significant delays".
    private fun humanizedEffect(alert: PTAlertInfo): String =
        alert.effect.name.replace(Regex("(?<=[a-z])[A-Z]")) { " " + it.value.lowercase() }

    // One-line note of an alert: feed header, else description, else the effect name humanized.
    // The ...For(language) helpers already fall back from an exact language match to the first
    // translation — and the feed's language tags are only hints anyway, sometimes plain wrong.
    fun alertText(alert: PTAlertInfo): String {
        val language = Locale.getDefault().language

        return alert.headerTextFor(language).orEmpty().ifBlank {
            alert.descriptionTextFor(language).orEmpty().ifBlank { humanizedEffect(alert) }
        }
    }

    // Short name of an alert: feed header, else the effect name humanized — never the
    // description, which is shown on demand behind the row's info icon instead.
    fun alertName(alert: PTAlertInfo): String =
        alert.headerTextFor(Locale.getDefault().language).orEmpty().ifBlank { humanizedEffect(alert) }

    // Long message of an alert, displayed in a popup when its info icon is tapped. The feeds
    // deliver it as HTML (<p>, <strong>, ...), so it is kept raw for AnnotatedString.fromHtml
    // to render, but judged on its rendered text: null when the feed delivers none (or
    // markup-only blanks), or when it merely repeats the name.
    fun alertDescription(alert: PTAlertInfo): String? = alert
        .descriptionTextFor(Locale.getDefault().language)
        ?.takeIf { html ->
            val text = AnnotatedString.fromHtml(html).text.trim()
            text.isNotEmpty() && text != alertName(alert).trim()
        }

    // Library vehicle kind of the route type. PTRoute.routeType is the Kotlin counterpart of
    // reading gem::opid::kPT_route_type from the native trip details (ERouteType has the same
    // values).
    fun vehicleType(routeType: PTRouteType): PTVehicleType = when (routeType) {
        PTRouteType.Bus -> PTVehicleType.Bus
        PTRouteType.Underground -> PTVehicleType.Underground
        PTRouteType.Railway -> PTVehicleType.Railway
        PTRouteType.Tram -> PTVehicleType.Tram
        PTRouteType.WaterTransport -> PTVehicleType.WaterTransport
        PTRouteType.Misc -> PTVehicleType.Misc
    }

    // Library line badge of a route: the delivered brand colors, guarded for readability.
    fun line(route: PTRouteInfo, palette: PTPalette): PTLine {
        val background = parseColor(route.routeColor, palette.gray)
        return PTLine(
            name = route.lineName,
            backgroundColor = background,
            textColor = contrastingTextColor(background, parseColor(route.routeTextColor, Color.White)),
        )
    }

    // Library departure row data of an upcoming trip at the station. isCancelled is the
    // authoritative "don't ride this" flag; the departure keeps its slot but is marked red
    // with a struck-through time. A Color.Unspecified status resolves to the theme's default
    // text color inside the library row.
    fun departureData(context: Context, trip: PTTrip, now: Date, palette: PTPalette): PTDepartureData {
        val isCancelled = trip.isCancelled == true
        val alerts = activeAlerts(trip.alerts, now)
        val (time, unit) = departureLabel(context, trip.departureTime, now)

        return PTDepartureData(
            vehicleType = vehicleType(trip.route.routeType),
            line = line(trip.route, palette),
            heading = trip.route.heading ?: "",
            statusText = tripStatus(context, trip.departureTime, now, trip.stopPlatformCode, isCancelled),
            statusColor = if (isCancelled) {
                palette.statusLate
            } else {
                statusColor(palette, Color.Unspecified, trip.hasRealtime, trip.delayMinutes ?: 0)
            },
            time = ValueWithUnit(time, unit),
            isCancelled = isCancelled,
            isWheelchairAccessible = trip.isWheelchairAccessible,
            isBikeAllowed = trip.isBikeAllowed,
            crowdingColor = crowdingLevel(trip)?.let { crowdingColor(palette, it) },
            alertColor = if (alerts.isEmpty()) null else alertColor(palette, alerts),
        )
    }

    // Library trip page data of one trip of the line: its accessibility/crowding badges, its
    // alert notes (the cancellation first — isCancelled is authoritative, shown even when the
    // feed delivers no explaining NoService alert) and its stations along the timeline.
    fun tripPageData(context: Context, trip: PTTrip, now: Date, palette: PTPalette): PTTripPageData {
        val alerts = activeAlerts(trip.alerts, now)
        val isCancelled = trip.isCancelled == true
        val timelineColor = parseColor(trip.route.routeColor, Color.Unspecified)

        return PTTripPageData(
            isWheelchairAccessible = trip.isWheelchairAccessible,
            isBikeAllowed = trip.isBikeAllowed,
            crowdingColor = crowdingLevel(trip)?.let { crowdingColor(palette, it) },
            notes = buildList {
                if (isCancelled) add(PTAlertData(context.getString(R.string.pt_cancelled)))
                alerts.forEach { add(PTAlertData(alertName(it), alertDescription(it))) }
            },
            noteColor = if (isCancelled) palette.statusLate else alertColor(palette, alerts),
            stops = trip.stopTimes.mapIndexed { index, stop ->
                val departed = stop.departureTime?.before(now) == true
                val isLastStop = index == trip.stopTimes.size - 1
                PTStopData(
                    name = stop.stopName,
                    statusText = stopStatus(context, departed, isLastStop),
                    statusColor = statusColor(palette, Color.Unspecified, stop.hasRealtime, stop.delay),
                    time = clockTime(stop.departureTime),
                    isPassed = stop.isBefore,
                    isFirst = index == 0,
                    isLast = isLastStop,
                    timelineColor = timelineColor,
                )
            },
        )
    }
}
