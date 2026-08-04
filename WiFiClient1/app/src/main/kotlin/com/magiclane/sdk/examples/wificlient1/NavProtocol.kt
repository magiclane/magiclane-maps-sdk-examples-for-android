/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.wificlient1

/**
 * Wire protocol shared by the WiFiServer1 and WiFiClient1 examples.
 *
 * Navigation data is received from the server as newline-delimited JSON messages over a plain
 * TCP socket. Each message has a "type" field and a payload:
 *  - instruction: the next-turn instruction text.
 *  - distance:    the formatted distance to the next turn.
 *  - turnImage:   the next-turn icon itself, as a Base64-encoded PNG — the WiFi analog of
 *                 the raw pixel bytes the BLEClient1 example receives over GATT (unlike the
 *                 WiFiClient example, which receives a turn event id and maps it to its own
 *                 bundled icons). An empty image means "no active navigation".
 *  - route:       the remaining travel time (seconds) and distance (meters) to the
 *                 destination; the bottom panel (ETA, remaining time and distance) is
 *                 derived from them.
 */
object NavProtocol {
    /** DNS-SD service type the server advertises and the client discovers. */
    const val SERVICE_TYPE = "_magiclane-nav._tcp."

    const val KEY_TYPE = "type"
    const val KEY_TEXT = "text"
    const val KEY_IMAGE = "image"
    const val KEY_REMAINING_TIME = "remainingTime"
    const val KEY_REMAINING_DISTANCE = "remainingDistance"

    const val TYPE_INSTRUCTION = "instruction"
    const val TYPE_DISTANCE = "distance"
    const val TYPE_TURN_IMAGE = "turnImage"
    const val TYPE_ROUTE = "route"
}
