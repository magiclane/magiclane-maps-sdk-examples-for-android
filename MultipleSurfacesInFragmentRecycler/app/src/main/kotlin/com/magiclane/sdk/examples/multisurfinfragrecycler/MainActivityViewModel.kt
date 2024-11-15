// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
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
