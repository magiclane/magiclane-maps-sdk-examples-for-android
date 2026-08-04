/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.wificlient

/**
 * Wire protocol shared by the WiFiServer and WiFiClient examples.
 *
 * Navigation data is received from the server as newline-delimited JSON messages over a plain
 * TCP socket. Each message has a "type" field and a payload:
 *  - instruction: the next-turn instruction text.
 *  - distance:    the formatted distance to the next turn.
 *  - turn:        the turn event id plus roundabout entrance/exit slots and drive side,
 *                 mirroring the 4 bytes the BLE example pair packs into its TURN_IMAGE
 *                 characteristic. An event of 0 means "no active navigation".
 *  - route:       the remaining travel time (seconds) and distance (meters) to the
 *                 destination; the bottom panel (ETA, remaining time and distance) is
 *                 derived from them, like the BLEClient2 example does.
 *  - speed:       the current speed in meters per second.
 */
object NavProtocol {
    /** DNS-SD service type the server advertises and the client discovers. */
    const val SERVICE_TYPE = "_magiclane-nav._tcp."

    const val KEY_TYPE = "type"
    const val KEY_TEXT = "text"
    const val KEY_EVENT = "event"
    const val KEY_ENTRANCE = "entrance"
    const val KEY_EXIT = "exit"
    const val KEY_DRIVE_SIDE = "driveSide"
    const val KEY_REMAINING_TIME = "remainingTime"
    const val KEY_REMAINING_DISTANCE = "remainingDistance"
    const val KEY_SPEED = "speed"

    const val TYPE_INSTRUCTION = "instruction"
    const val TYPE_DISTANCE = "distance"
    const val TYPE_TURN = "turn"
    const val TYPE_ROUTE = "route"
    const val TYPE_SPEED = "speed"
}
