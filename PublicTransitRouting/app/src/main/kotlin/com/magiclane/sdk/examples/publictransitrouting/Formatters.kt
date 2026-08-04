/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.publictransitrouting

import android.content.Context
import android.graphics.Color
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.Time
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formatting helpers for public-transit values.
 *
 * Public-transit times reported by the SDK are station wall-clock times encoded as UTC epochs,
 * so custom timestamps are built/formatted through UTC calendars (see [WallClock]).
 */
object Formatters {

    /** "9:35" from an SDK [Time]; must be called on the SDK thread. */
    fun timeText(time: Time?): String {
        time ?: return ""
        if (!time.isValid()) return ""
        return "%d:%02d".format(time.hour, time.minute)
    }

    /** "1 h 25 min" / "25 min" from a duration in seconds. */
    fun durationText(context: Context, seconds: Int): String {
        val totalMinutes = (seconds + 30) / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            context.getString(R.string.duration_hours_minutes, hours, minutes)
        } else {
            context.getString(R.string.duration_minutes, totalMinutes.coerceAtLeast(1))
        }
    }

    /** Whole minutes (rounded, at least 1) of a duration in seconds. */
    fun minutesValue(seconds: Int): Int = ((seconds + 30) / 60).coerceAtLeast(1)

    /** "1.2 km" / "850 m" from a distance in meters. */
    fun distanceText(context: Context, meters: Int): String = if (meters >= 1000) {
        context.getString(R.string.distance_km, "%.1f".format(meters / 1000.0))
    } else {
        context.getString(R.string.distance_m, meters)
    }

    /** ARGB color of an SDK [Rgba], or [fallback] when unset; must be called on the SDK thread. */
    fun colorOf(rgba: Rgba?, fallback: Int = 0): Int {
        rgba ?: return fallback
        if (rgba.alpha == 0) return fallback
        val color = Color.argb(rgba.alpha, rgba.red, rgba.green, rgba.blue)
        // Pure white badges are remapped to light gray so they stay visible (as in Magic Earth).
        return if (color == Color.WHITE) 0xFFE4E4E4.toInt() else color
    }
}

/**
 * Wall-clock timestamps encoded as UTC epoch milliseconds — the encoding used by the SDK for
 * public-transit times. All the calendar math below is intentionally done in UTC.
 */
object WallClock {

    private fun utcCalendar(millis: Long? = null): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { millis?.let { timeInMillis = it } }

    /** The local wall-clock "now", encoded as a UTC epoch. */
    fun nowMillis(): Long {
        val local = Calendar.getInstance()
        val utc = utcCalendar()
        utc.clear()
        utc.set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH),
            local.get(Calendar.HOUR_OF_DAY),
            local.get(Calendar.MINUTE),
        )
        return utc.timeInMillis
    }

    /** Today's local date at 23:59, encoded as a UTC epoch (the "Last" departure option). */
    fun endOfTodayMillis(): Long = utcCalendar(nowMillis()).apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
    }.timeInMillis

    fun withTime(millis: Long, hour: Int, minute: Int): Long = utcCalendar(millis).apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }.timeInMillis

    fun withDate(millis: Long, year: Int, month: Int, day: Int): Long = utcCalendar(millis).apply {
        set(year, month, day)
    }.timeInMillis

    fun hour(millis: Long): Int = utcCalendar(millis).get(Calendar.HOUR_OF_DAY)

    fun minute(millis: Long): Int = utcCalendar(millis).get(Calendar.MINUTE)

    fun isToday(millis: Long): Boolean {
        val value = utcCalendar(millis)
        val today = utcCalendar(nowMillis())
        return value.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            value.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }

    /** "9:35" */
    fun timeText(millis: Long): String = "%d:%02d".format(hour(millis), minute(millis))

    /** "7/31/26" */
    fun dateText(millis: Long): String = SimpleDateFormat("M/d/yy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))

    /** "9:35" today, "9:35, Jul 31" otherwise. */
    fun timeAndDateText(millis: Long): String =
        if (isToday(millis)) timeText(millis) else "${timeText(millis)}, ${dateText(millis)}"
}
