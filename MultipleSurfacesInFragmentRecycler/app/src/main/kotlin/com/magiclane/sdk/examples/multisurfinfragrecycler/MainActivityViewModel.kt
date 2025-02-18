// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
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
