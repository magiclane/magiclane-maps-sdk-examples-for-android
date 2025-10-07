// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.bleclient

// -------------------------------------------------------------------------------------------------------------------------------

import java.util.*

// -------------------------------------------------------------------------------------------------------------------------------

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
object SampleGattAttributes
{
    private val attributes: HashMap<String, String> = hashMapOf()
    var NAVIGATION_SERVICE: UUID = UUID.fromString("00011805-0000-1000-8000-00805f9b34fb")
    var TURN_INSTRUCTION: UUID = UUID.fromString("00012a2b-0000-1000-8000-00805f9b34fb")
    val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    val TURN_IMAGE: UUID = UUID.fromString("00012a0f-0000-1000-8000-00805f9b34fb")
    val TURN_DISTANCE: UUID = UUID.fromString("00012a2f-0000-1000-8000-00805f9b34fb")

    fun lookup(uuid: String?, defaultName: String): String
    {
        val name = attributes[uuid]
        return name ?: defaultName
    }

    init
    {
        attributes["00011805-0000-1000-8000-00805f9b34fb"] = "Navigation service"
        attributes["00012a2b-0000-1000-8000-00805f9b34fb"] = "Turn instruction characteristic"
        attributes["00012a0f-0000-1000-8000-00805f9b34fb"] = "Turn image characteristic"
        attributes["00012a2f-0000-1000-8000-00805f9b34fb"] = "Turn distance characteristic"
    }
}

// -------------------------------------------------------------------------------------------------------------------------------
