/*
 * SPDX-FileCopyrightText: 2025-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.searchcompose

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GenericCategories
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.util.GEMLog
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Holds search state and business logic, surviving configuration changes.
// The Activity is responsible only for SDK lifecycle and UI binding.
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    // Represents a single search result displayed in the list.
    data class SearchItem(
        val image: Bitmap? = null,
        val name: String = "",
        val description: String = "",
        val distance: String = "",
        val unit: String = "",
    )

    // Represents a POI category chip in the horizontal bar.
    data class CategoryItem(
        val name: String,
        val icon: Bitmap?,
        val landmarkStoreId: Int,
        val categoryId: Int,
    )

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 500L
        const val NO_CATEGORY = -1
    }

    private val searchService = SearchService()
    private var reference: Coordinates? = null
    private var searchJob: Job? = null

    private var imageSize: Int = 0
    private var iconSize: Int = 0
    private var categoriesLoaded = false
    private var isSearchingOnSdk = false

    var results by mutableStateOf(emptyList<SearchItem>())
        private set
    var categories by mutableStateOf(emptyList<CategoryItem>())
        private set

    // Index of the selected category chip, or NO_CATEGORY when none is selected.
    var selectedCategory by mutableIntStateOf(NO_CATEGORY)
        private set

    // True while a search coroutine is running (used to drive the progress bar).
    var isSearching by mutableStateOf(false)
        private set

    // True once the SDK map data is ready (controls loading overlay and search enablement).
    var isSdkReady by mutableStateOf(false)
        private set

    // Non-empty when an error dialog should be shown.
    var errorMessage by mutableStateOf("")
        private set

    // When true the error is fatal and the Activity should finish on dismiss.
    var isFatalError by mutableStateOf(false)
        private set

    // Drives the TextField value; updated by both user input and category selection.
    var searchText by mutableStateOf("")
        private set

    var currentFilter by mutableStateOf("")
        private set

    // Call once from the Activity after resources are available.
    fun initialize(imageSize: Int, iconSize: Int) {
        this.imageSize = imageSize
        this.iconSize = iconSize
    }

    // Called by MainActivity once SDK map data is ready.
    fun onSdkReady() {
        isSdkReady = true
        loadCategories()
    }

    fun showFatalError(message: String) {
        errorMessage = message
        isFatalError = true
    }

    fun showInfoError(message: String) {
        errorMessage = message
        isFatalError = false
    }

    fun dismissError() {
        errorMessage = ""
        isFatalError = false
    }

    // Call from the Activity when the SDK map data is ready.
    fun loadCategories() {
        if (categoriesLoaded) return
        categoriesLoaded = true
        viewModelScope.launch(Dispatchers.IO) {
            val items = SdkCall.execute {
                GenericCategories().categories?.mapNotNull { cat ->
                    CategoryItem(
                        name = cat.name ?: return@mapNotNull null,
                        icon = cat.image?.asBitmap(iconSize, iconSize),
                        landmarkStoreId = cat.landmarkStoreId,
                        categoryId = cat.id,
                    )
                } ?: emptyList()
            } ?: emptyList()
            withContext(Dispatchers.Main) {
                categories = items
            }
        }
    }

    private fun setSearchReferencePoint() {
        val position = PositionService.position
        reference = if (position?.isValid() == true) {
            position.coordinates
        } else {
            // London
            Coordinates(51.5072, 0.1276)
        }
    }

    // Cancels any in-flight search, waits for the debounce window, then runs a new search.
    fun search(filter: String) {
        selectedCategory = NO_CATEGORY
        currentFilter = filter

        cancelSearch()

        // Only skip when the filter is blank AND no category is active.
        if (filter.isBlank()) {
            results = emptyList()
            isSearching = false
            searchText = filter
            return
        }

        filter.trim()
        if (searchText == filter) {
            return
        }

        searchText = filter

        isSearching = true
        EspressoIdlingResource.increment()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(SEARCH_DEBOUNCE_MS)

            Util.postOnMain {
                SdkCall.postAsync {
                    if (!searchService.preferences.searchAddressesEnabled) {
                        searchService.preferences.removeAllCategoryFilters()
                        searchService.preferences.searchAddressesEnabled = true
                    }

                    setSearchReferencePoint()

                    isSearchingOnSdk = true

                    searchService.searchByFilter(
                        textFilter = filter,
                        reference = reference,
                        onCompleted = { landmarks, errorCode, _ ->
                            isSearchingOnSdk = false
                            if (errorCode == GemError.Cancel) return@searchByFilter
                            SdkCall.execute {
                                val items = buildSearchItems(landmarks)
                                viewModelScope.launch(Dispatchers.Main) {
                                    results = items
                                    isSearching = false
                                }
                                if (errorCode != GemError.NoError) {
                                    GEMLog.error(
                                        this,
                                        getApplication<Application>().getString(
                                            R.string.search_error,
                                            GemError.getMessage(errorCode, getApplication()),
                                        ),
                                    )
                                }
                            }
                            EspressoIdlingResource.decrement()
                        },
                    )
                }
            }
        }
    }

    // Selects a category chip; tapping the already-selected chip does nothing.
    // Cancels any in-flight search and re-runs with the updated filter.
    fun selectCategory(index: Int) {
        if (selectedCategory == index) return
        selectedCategory = index

        searchText = ""
        cancelSearch()

        currentFilter = categories.getOrNull(index)?.name ?: ""

        // Apply the category filter on the search preferences.
        SdkCall.postAsync {
            searchService.preferences.removeAllCategoryFilters()
            searchService.preferences.searchAddressesEnabled = false
            setSearchReferencePoint()

            categories.getOrNull(index)?.let { cat ->
                searchService.preferences.landmarkStores?.addStoreCategoryId(cat.landmarkStoreId, cat.categoryId)

                isSearching = true
                isSearchingOnSdk = true

                searchService.searchAroundPosition(
                    reference = reference,
                    onCompleted = { landmarks, errorCode, _ ->
                        if (errorCode != GemError.Cancel) {
                            SdkCall.execute {
                                val items = buildSearchItems(landmarks)
                                viewModelScope.launch(Dispatchers.Main) {
                                    results = items
                                    isSearching = false
                                }
                                if (errorCode != GemError.NoError) {
                                    GEMLog.error(
                                        this,
                                        getApplication<Application>().getString(
                                            R.string.search_error,
                                            GemError.getMessage(errorCode, getApplication()),
                                        ),
                                    )
                                }
                            }
                            EspressoIdlingResource.decrement()
                        }
                    },
                )
            }
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        if (isSearchingOnSdk) {
            isSearchingOnSdk = false
            SdkCall.postAsync { searchService.cancelSearch() }
        }
    }

    // Cancel any pending search when the ViewModel is destroyed alongside the Activity.
    override fun onCleared() = cancelSearch()

    private fun buildSearchItems(landmarks: ArrayList<Landmark>): List<SearchItem> {
        return landmarks.map { landmark ->
            val meters = reference?.let { landmark.coordinates?.getDistance(it)?.toInt() ?: 0 } ?: 0
            val dist = GemUtil.getDistText(meters, EUnitSystem.Metric, true)
            SearchItem(
                image = landmark.imageAsBitmap(imageSize),
                name = landmark.name.toString(),
                description = GemUtil.getLandmarkDescription(landmark, true),
                distance = dist.first,
                unit = dist.second,
            )
        }
    }
}
