/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselectioncompose

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.core.graphics.toColorInt
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
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.PTAlertWarningDark
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.PTAlertWarningLight
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.PTGrayDark
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.PTGrayLight
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.PTStatusEarlyDark
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.PTStatusEarlyLight
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.PTStatusLateDark
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.PTStatusLateLight
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.PTStatusOnTimeDark
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.PTStatusOnTimeLight
import com.magiclane.sdk.places.Coordinates
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Fixed colors of the public transport screens, resolved for the current theme with [ptPalette].
data class PTPalette(
    val gray: Color,
    val statusEarly: Color,
    val statusOnTime: Color,
    val statusLate: Color,
    val alertWarning: Color,
)

@Composable
fun ptPalette(): PTPalette = if (isSystemInDarkTheme()) {
    PTPalette(PTGrayDark, PTStatusEarlyDark, PTStatusOnTimeDark, PTStatusLateDark, PTAlertWarningDark)
} else {
    PTPalette(PTGrayLight, PTStatusEarlyLight, PTStatusOnTimeLight, PTStatusLateLight, PTAlertWarningLight)
}

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
    fun contrastingTextColor(backgroundColor: Color, textColor: Color): Color =
        if (backgroundColor != textColor) {
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
    fun stopStatus(context: Context, departed: Boolean, isLastStop: Boolean): String =
        context.getString(
            when {
                departed && isLastStop -> R.string.pt_arrived
                departed -> R.string.pt_departed
                else -> R.string.pt_scheduled
            },
        )

    fun clockTime(date: Date?): String = date?.let { clockFormat.format(it) } ?: ""

    // Realtime status color: blue = running early, red = delayed, green = on time,
    // default text color = no realtime data (scheduled only).
    fun statusColor(palette: PTPalette, defaultColor: Color, hasRealtime: Boolean, delayMinutes: Int): Color =
        when {
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

    fun crowdingLabel(context: Context, level: CrowdingLevel): String = context.getString(
        when (level) {
            CrowdingLevel.Low -> R.string.pt_crowding_low
            CrowdingLevel.Medium -> R.string.pt_crowding_medium
            CrowdingLevel.High -> R.string.pt_crowding_high
        },
    )

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

    // Icon resource of the vehicle type. PTRoute.routeType is the Kotlin counterpart of reading
    // gem::opid::kPT_route_type from the native trip details (ERouteType has the same values).
    @DrawableRes
    fun vehicleIconRes(routeType: PTRouteType): Int = when (routeType) {
        PTRouteType.Bus -> R.drawable.ic_pt_bus_24
        PTRouteType.Underground -> R.drawable.ic_pt_underground_24
        PTRouteType.Railway -> R.drawable.ic_pt_railway_24
        PTRouteType.Tram -> R.drawable.ic_pt_tram_24
        PTRouteType.WaterTransport -> R.drawable.ic_pt_water_24
        PTRouteType.Misc -> R.drawable.ic_pt_misc_24
    }
}
