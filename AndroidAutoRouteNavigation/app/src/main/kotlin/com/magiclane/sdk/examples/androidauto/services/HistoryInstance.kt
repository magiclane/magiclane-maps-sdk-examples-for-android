/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

package com.magiclane.sdk.examples.androidauto.services

import com.magiclane.sdk.examples.androidauto.util.TripModel
import com.magiclane.sdk.examples.androidauto.util.TripsHistory

object HistoryInstance {
    val service = TripsHistory()
    val trips: ArrayList<TripModel>
        get() = service.trips

    // ---------------------------------------------------------------------------------------------
    fun init() {
        service.init()
    }

    // ---------------------------------------------------------------------------------------------
}