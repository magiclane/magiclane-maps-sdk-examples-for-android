/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.androidauto.services

import com.generalmagic.sdk.examples.androidauto.util.TripModel
import com.generalmagic.sdk.examples.androidauto.util.TripsHistory

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