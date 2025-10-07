// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.multisurfinfragrecycler

import androidx.lifecycle.ViewModel
import com.magiclane.sdk.examples.multisurfinfragrecycler.data.MapItem
import java.util.Date

class MainActivityViewModel: ViewModel()
{
    val list = (0..4).map { MapItem(it, Date()) }.toMutableList()
}
