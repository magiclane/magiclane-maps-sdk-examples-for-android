/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
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