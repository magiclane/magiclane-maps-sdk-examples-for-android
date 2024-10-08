// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.bikesimulation

import android.graphics.Bitmap

// -------------------------------------------------------------------------------------------------
data class SearchResultItem (
    var bmp : Bitmap? = null,
    var text : String? = null,
    val lat : Double?,
    val lon : Double?
)

// -------------------------------------------------------------------------------------------------
