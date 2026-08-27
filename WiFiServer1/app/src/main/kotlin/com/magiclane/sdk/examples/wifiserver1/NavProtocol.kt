/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.wifiserver1

import org.json.JSONObject

/**
 * Wire protocol shared by the WiFiServer1 and WiFiClient1 examples.
 *
 * Navigation data is sent from the server to its clients as newline-delimited JSON messages
 * over a plain TCP socket. Each message has a "type" field and a payload:
 *  - instruction: the next-turn instruction text.
 *  - distance:    the formatted distance to the next turn.
 *  - turnImage:   the next-turn icon itself, as a Base64-encoded PNG — the WiFi analog of
 *                 the raw pixel bytes the BLEServer1 example streams over GATT (unlike the
 *                 WiFiServer example, which sends a turn event id that the client maps to
 *                 its own bundled icons). An empty image means "no active navigation".
 *  - route:       the remaining travel time (seconds) and distance (meters) to the
 *                 destination; the client derives its bottom panel (ETA, remaining time
 *                 and distance) from them.
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

    fun instructionMessage(text: String): String =
        JSONObject().put(KEY_TYPE, TYPE_INSTRUCTION).put(KEY_TEXT, text).toString()

    fun distanceMessage(text: String): String = JSONObject().put(KEY_TYPE, TYPE_DISTANCE).put(KEY_TEXT, text).toString()

    fun turnImageMessage(base64Png: String): String =
        JSONObject().put(KEY_TYPE, TYPE_TURN_IMAGE).put(KEY_IMAGE, base64Png).toString()

    fun routeMessage(remainingTimeSeconds: Int, remainingDistanceMeters: Int): String = JSONObject()
        .put(KEY_TYPE, TYPE_ROUTE)
        .put(KEY_REMAINING_TIME, remainingTimeSeconds)
        .put(KEY_REMAINING_DISTANCE, remainingDistanceMeters)
        .toString()
}
