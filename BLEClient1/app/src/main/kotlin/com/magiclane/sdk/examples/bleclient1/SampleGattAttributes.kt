// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.bleclient1

// -------------------------------------------------------------------------------------------------------------------------------

import java.util.*

// -------------------------------------------------------------------------------------------------------------------------------

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
object SampleGattAttributes
{
    private val attributes: HashMap<String, String> = hashMapOf()
    var NAVIGATION_SERVICE: UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
    var TURN_INSTRUCTION: UUID = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb")
    val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    val TURN_IMAGE: UUID = UUID.fromString("00002a0f-0000-1000-8000-00805f9b34fb")
    val TURN_DISTANCE: UUID = UUID.fromString("00002a2f-0000-1000-8000-00805f9b34fb")

    fun lookup(uuid: String?, defaultName: String): String
    {
        val name = attributes[uuid]
        return name ?: defaultName
    }

    init
    {
        attributes["00001805-0000-1000-8000-00805f9b34fb"] = "Navigation service"
        attributes["00002a2b-0000-1000-8000-00805f9b34fb"] = "Turn instruction characteristic"
        attributes["00002a0f-0000-1000-8000-00805f9b34fb"] = "Turn image characteristic"
        attributes["00002a2f-0000-1000-8000-00805f9b34fb"] = "Turn distance characteristic"
    }
}

// -------------------------------------------------------------------------------------------------------------------------------
