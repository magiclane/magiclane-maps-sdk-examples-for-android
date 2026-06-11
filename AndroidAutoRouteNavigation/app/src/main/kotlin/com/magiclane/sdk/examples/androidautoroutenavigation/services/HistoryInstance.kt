/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidautoroutenavigation.services

import com.magiclane.sdk.examples.androidautoroutenavigation.util.TripModel
import com.magiclane.sdk.examples.androidautoroutenavigation.util.TripsHistory

object HistoryInstance {
    val service = TripsHistory()
    val trips: ArrayList<TripModel>
        get() = service.trips

    fun init() {
        service.init()
    }
}
