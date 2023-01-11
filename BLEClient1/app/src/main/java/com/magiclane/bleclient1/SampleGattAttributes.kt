// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.bleclient1

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