/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.multisurfinfragrecycler.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MapItem(
    val id: Int,
    val date: Date
){
    val timestamp : String
        get() = SimpleDateFormat("dd.MM.yyyy HH:mm::ss", Locale("UK")).format(date)
}
