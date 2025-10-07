// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.search_compose

// --------------------------------------------------------------------------------------------------------------------------------------------------

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.compose.ui.graphics.asImageBitmap
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall

// --------------------------------------------------------------------------------------------------------------------------------------------------

class SearchViewModel : ViewModel() {
    private val _searchItems: MutableList<SearchResult> = mutableStateListOf()
    var size: Int = 0

    val searchItems: List<SearchResult>
        get() = _searchItems

    var displayProgress by mutableStateOf(false)

    var filter = ""

    var connected = false

    var reference: Coordinates? = null

    var statusMessage by mutableStateOf("")

    var errorMessage by mutableStateOf("")

    fun refresh(landmarks: ArrayList<Landmark>){
        _searchItems.clear()
        SdkCall.execute {
            for (landmark in landmarks)
            {
                val imageBitmap = landmark.imageAsBitmap(size)?.asImageBitmap() ?: ImageBitmap(1, 1)
                val text = landmark.name ?: ""
                val description = GemUtil.getLandmarkDescription(landmark, true)

                val meters = reference?.let { landmark.coordinates?.getDistance(it)?.toInt() ?: 0 } ?: 0
                val dist = GemUtil.getDistText(meters, EUnitSystem.Metric, true)

                _searchItems.add(SearchResult(imageBitmap, text, description, dist.first, dist.second))
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------------------------------------------------------
