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

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.compose.ui.graphics.asImageBitmap
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.search_compose.data.SearchResult
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util

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

    // Initialize SearchService with callback handling
    private var searchService = SearchService(onCompleted =  { results, errorCode, _ ->
        if (errorCode != GemError.Cancel) {
            displayProgress = false
        }

        when (errorCode) {
            GemError.NoError -> {
                // No error encountered, we can handle the results.
                refresh(results)
                if (results.isEmpty()) {
                    statusMessage = "No search results"
                } else {
                    statusMessage = ""
                }
            }
            GemError.Cancel -> {
                // The search action was cancelled.
            }
            GemError.Busy -> {
                refresh(results)
                statusMessage = "Requested operation cannot be performed. Internal limit reached. Please use an API token in order to avoid this error."
            }
            GemError.NotFound -> {
                refresh(results)
                statusMessage = "No search results"
            }
            else -> {
                // There was a problem at computing the search operation.
                refresh(results)
                statusMessage = GemError.getMessage(errorCode)
            }
        }
    })

    fun refresh(landmarks: ArrayList<Landmark>) {
        _searchItems.clear()
        SdkCall.execute {
            for (landmark in landmarks) {
                val imageBitmap = landmark.imageAsBitmap(size)?.asImageBitmap() ?: ImageBitmap(1, 1)
                val text = landmark.name ?: ""
                val description = GemUtil.getLandmarkDescription(landmark, true)

                val meters = reference?.let { landmark.coordinates?.getDistance(it)?.toInt() ?: 0 } ?: 0
                val dist = GemUtil.getDistText(meters, EUnitSystem.Metric, true)

                _searchItems.add(SearchResult(imageBitmap, text, description, dist.first, dist.second))
            }
        }
    }

    // Moved from MainActivity - Business logic for performing search
    private fun search(filter: String, context: Context) = SdkCall.postAsync {
        // Cancel any search that is in progress now.
        Log.d("SEARCH", filter)
        searchService.cancelSearch()

        if (filter.isBlank()) {
            refresh(arrayListOf())
            if (!Util.isInternetConnected(context)) {
                statusMessage = "Please connect to the internet!"
            } else {
                statusMessage = ""
            }
        } else {
            val position = PositionService.position
            reference = if (position?.isValid() == true) {
                position.coordinates
            } else {
                Coordinates(51.5072, 0.1276) // center London
            }

            searchService.searchByFilter(filter, reference)
        }
    }

    // Moved from MainActivity - Business logic for applying filter
    fun applyFilter(filter: String, context: Context) {
        val newFilter = filter.trim()
        if (newFilter != this.filter) {
            this.filter = newFilter
            displayProgress = newFilter.isNotBlank() && connected

            // Search the requested filter.
            search(newFilter, context)
        }
    }

    override fun onCleared() {
        super.onCleared()
        searchService.cancelSearch()
    }
}

// --------------------------------------------------------------------------------------------------------------------------------------------------
