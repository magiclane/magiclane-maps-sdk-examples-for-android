/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers

import android.os.Bundle
import android.view.View
import android.widget.SearchView
import android.widget.Toast
import com.generalmagic.sdk.*
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.examples.demo.activities.*
import com.generalmagic.sdk.examples.demo.activities.history.Trip
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.app.Tutorials
import com.generalmagic.sdk.examples.demo.util.KeyboardUtil
import com.generalmagic.sdk.places.*
import com.generalmagic.sdk.routesandnavigation.ERouteTransportMode
import com.generalmagic.sdk.sensordatasource.PositionService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import kotlinx.android.synthetic.main.activity_list_view.*
import kotlinx.android.synthetic.main.filter_view.*
import java.util.*
import kotlin.collections.ArrayList

open class BaseSearchTutorialActivity : SearchListActivity() {
    protected val searchService = SearchService()
    protected var referencePoint = Coordinates()

    override fun doStart() {
        searchService.onStarted = {
            showProgress()
            hideListView()
        }

        searchService.onCompleted = onCompleted@{ results, reason, _ ->
            val gemError = SdkError.fromInt(reason)
            if (gemError == SdkError.Cancel) return@onCompleted

            hideProgress()

            if (gemError != SdkError.NoError) {
                Toast.makeText(
                    this@BaseSearchTutorialActivity, "Search failed: $gemError", Toast.LENGTH_SHORT
                ).show()
                return@onCompleted
            }

            val wrapped = wrapLandmarkList(results, referencePoint)
            val adapter = SLIAdapter(wrapped)

            list_view.adapter = adapter
            if (reason == SdkError.NoError.value && results.isEmpty()) {
                Toast.makeText(
                    this@BaseSearchTutorialActivity, "No result!", Toast.LENGTH_SHORT
                ).show()
            }
        }

    }

    override fun doStop() {
        hideProgress()

        SdkCall.execute {
            searchService.cancelSearch()
        }
    }

    private fun wrapLandmarkList(
        list: ArrayList<Landmark>, reference: Coordinates?
    ): ArrayList<SearchListItem> {
        val result = ArrayList<SearchListItem>()
        for (item in list) {
            val wrappedItem = LandmarkViewModel(item, reference)
            wrappedItem.mOnClick = { onLandmarkItemClicked(wrappedItem) }
            result.add(wrappedItem)
        }
        return result
    }

    protected fun wrapHistoryLandmarkList(
        list: ArrayList<Landmark>, reference: Coordinates?
    ): ArrayList<SearchListItem> {
        val result = ArrayList<SearchListItem>()
        for (item in list) {
            val wrappedItem = HistoryLandmarkViewModel(item, reference)
            wrappedItem.mOnClick = { onLandmarkItemClicked(wrappedItem) }
            wrappedItem.mOnLongClick = {
                wrappedItem.deleteContent()
                doStart()
                true
            }
            result.add(wrappedItem)
        }
        return result
    }

    protected fun wrapFavouritesLandmarkList(
        list: ArrayList<Landmark>, reference: Coordinates?
    ): ArrayList<SearchListItem> {
        val result = ArrayList<SearchListItem>()
        for (item in list) {
            val wrappedItem = FavouritesLandmarkViewModel(item, reference)
            wrappedItem.mOnClick = { onLandmarkItemClicked(wrappedItem) }
            wrappedItem.mOnLongClick = {
                wrappedItem.deleteContent()
                doStart()
                true
            }
            result.add(wrappedItem)
        }
        return result
    }

    protected fun wrapTripList(list: ArrayList<Trip>): ArrayList<SearchListItem> {
        val result = ArrayList<SearchListItem>()
        for ((index, item) in list.withIndex()) {
            val wrappedItem = TripViewModel(item, index)
            wrappedItem.mOnClick = { onTripItemClicked(wrappedItem) }
            wrappedItem.mOnLongClick = {
                wrappedItem.deleteContent()
                doStart()
                true
            }
            result.add(wrappedItem)
        }
        return result
    }

    protected open fun onLandmarkItemClicked(item: LandmarkViewModel) {
        KeyboardUtil.hideKeyboard(this)
        val itLandmark = item.it ?: return

        finish()

        /*DEFAULT*/
        Tutorials.openWikiTutorial(itLandmark)
    }

    protected open fun onTripItemClicked(item: TripViewModel) {
        KeyboardUtil.hideKeyboard(this)
        val itTrip = item.it

        finish()
        itTrip.mWaypoints?.let { waypoints ->

            if (!itTrip.mIsFromAToB) {
                val list = arrayListOf<Landmark>()
                val position = PositionService().getPosition()
                if (position != null) {
                    list.add(
                        Landmark(
                            "",
                            Coordinates(position.getLatitude(), position.getLongitude())
                        )
                    )
                }

                list.addAll(waypoints)
                Tutorials.openCustomRouteTutorial(list)
                return@let
            }

            if (itTrip.mPreferences?.getTransportMode() != ERouteTransportMode.Public) {
                Tutorials.openCustomRouteTutorial(waypoints)
            } else {
                Tutorials.openCustomPublicNavTutorial(waypoints)
            }
        }
    }

    protected fun getCurrentCoords(): Coordinates? = SdkCall.execute {
        GEMApplication.getMainMapView()?.getCursorWgsPosition() ?: return@execute null
    }
}

class SearchTextActivity : BaseSearchTutorialActivity() {
    private var lastFilter = ""
    private val searchViewListener = object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(text: String?): Boolean {
            doSearch(text)
            return true
        }

        override fun onQueryTextChange(text: String?): Boolean {
            doSearch(text)
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filterView.visibility = View.VISIBLE
        searchInput.requestFocus()
        searchInput.setOnQueryTextListener(searchViewListener)
    }

    private fun doSearch(text: String?) {
        doStop() // if there is any in progress
        if (text.isNullOrEmpty()) {
            hideListView()
            return
        }

        if (text.trim() == lastFilter) return

        lastFilter = text

        SdkCall.execute {
            val reference = getCurrentCoords() ?: return@execute

            searchService.preferences.setSearchAddresses(true)
            searchService.preferences.setSearchMapPOIs(true)

            referencePoint = reference
            searchService.searchByFilter(text, reference)
        }
    }
}

class SearchNearbyActivity : BaseSearchTutorialActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filterView.visibility = View.GONE
    }

    override fun doStart() {
        doStop()
        super.doStart()

        SdkCall.execute {
            val reference = getCurrentCoords() ?: return@execute

            searchService.preferences.setSearchMapPOIs(true)

            referencePoint = reference
            searchService.searchAroundPosition(reference, "")
        }
    }
}

class SearchHistoryActivity : BaseSearchTutorialActivity() {
    private var listToDisplay = arrayListOf<SearchListItem>()
    private val searchViewListener = object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(text: String?): Boolean {
            if (text != null) {
                doSearch(text)
            }
            searchInput?.clearFocus()
            return true
        }

        override fun onQueryTextChange(text: String?): Boolean {
            if (text != null) {
                doSearch(text)
            }
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filterView.visibility = View.VISIBLE
        searchInput.requestFocus()
        searchInput.setOnQueryTextListener(searchViewListener)
    }

    override fun doStart() {
        doStop()

        SdkCall.execute {
            val reference = getCurrentCoords() ?: return@execute

            searchService.preferences.setSearchMapPOIs(true)

            listToDisplay = arrayListOf()
            GEMApplication.getHistoryLandmarkStore()?.getLandmarks()?.let { list ->
                val wrapped = wrapHistoryLandmarkList(list, reference)
                listToDisplay.addAll(wrapped)
            }

            GEMApplication.getTripsHistory()?.let { tripsHistory ->
                val nItems = tripsHistory.getTripsCount()
                val list = arrayListOf<Trip>()
                for (index in 0 until nItems) {
                    val trip = tripsHistory.loadTrip(index).second
                    if (trip != null) {
                        list.add(trip)
                    }
                }
                val wrapped = wrapTripList(list)
                listToDisplay.addAll(wrapped)
            }

            listToDisplay.sortByDescending { it.getId() }
            val adapter = HLIAdapter(listToDisplay)

            GEMApplication.postOnMain { list_view.adapter = adapter }

        }
    }

    private fun doSearch(filter: String) {
        val resultList = if (filter.isNotEmpty()) {
            val lowerFilter = filter.toLowerCase(Locale.getDefault())
            val filterTokens = lowerFilter.split(' ', '-')

            ArrayList(
                listToDisplay.filter {
                    val arg = SdkCall.execute { it.getText() }
                    val lowerArg = arg?.toLowerCase(Locale.getDefault()) ?: ""
                    val argTokens = lowerArg.split(' ', '-')

                    for (filterWord in filterTokens) {
                        var found = false
                        for (token in argTokens) {
                            if (token.contains(filterWord)) {
                                found = true
                                break
                            }
                        }
                        if (!found) {
                            return@filter false
                        }
                    }

                    true
                }
            )
        } else {
            listToDisplay
        }

        displayList(resultList)
    }

    private fun displayList(items: ArrayList<SearchListItem>) {
        val adapter = HLIAdapter(items)

        list_view.adapter = adapter
    }
}

class SearchFavouritesActivity : BaseSearchTutorialActivity() {
    private var listToDisplay = arrayListOf<SearchListItem>()
    private val searchViewListener = object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(text: String?): Boolean {
            if (text != null) {
                doSearch(text)
            }
            searchInput?.clearFocus()
            return true
        }

        override fun onQueryTextChange(text: String?): Boolean {
            if (text != null) {
                doSearch(text)
            }
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filterView.visibility = View.VISIBLE
        searchInput.requestFocus()
        searchInput.setOnQueryTextListener(searchViewListener)
    }

    override fun doStart() {
        doStop()

        SdkCall.execute {
            val reference = getCurrentCoords() ?: return@execute

            searchService.preferences.setSearchMapPOIs(true)

            listToDisplay = arrayListOf()
            GEMApplication.getFavouritesLandmarkStore()?.getLandmarks()?.let { list ->
                val wrapped = wrapFavouritesLandmarkList(list, reference)
                listToDisplay.addAll(wrapped)
            }

            listToDisplay.sortByDescending { it.getId() }
            val adapter = SLIAdapter(listToDisplay)

            GEMApplication.postOnMain { list_view.adapter = adapter }
        }
    }

    private fun doSearch(filter: String) {
        val resultList = if (filter.isNotEmpty()) {
            val lowerFilter = filter.toLowerCase(Locale.getDefault())
            val filterTokens = lowerFilter.split(' ', '-')

            ArrayList(
                listToDisplay.filter {
                    val arg = SdkCall.execute { it.getText() }
                    val lowerArg = arg?.toLowerCase(Locale.getDefault()) ?: ""
                    val argTokens = lowerArg.split(' ', '-')

                    for (filterWord in filterTokens) {
                        var found = false
                        for (token in argTokens) {
                            if (token.contains(filterWord)) {
                                found = true
                                break
                            }
                        }
                        if (!found) {
                            return@filter false
                        }
                    }

                    true
                }
            )
        } else {
            listToDisplay
        }

        displayList(resultList)
    }

    private fun displayList(items: ArrayList<SearchListItem>) {
        val adapter = SLIAdapter(items)

        list_view.adapter = adapter
    }
}

class SearchPoiActivity : BaseSearchTutorialActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filterView.visibility = View.GONE
    }

    private fun getGasCategory(): LandmarkCategory? = SdkCall.execute {
        val categoriesList = GenericCategories().getCategories() ?: return@execute null

        var category: LandmarkCategory? = null
        if (categoriesList.size > 0) {
            category = categoriesList[0]
        }

        return@execute category
    }

    override fun doStart() {
        doStop()
        super.doStart()

        SdkCall.execute {
            val reference = getCurrentCoords() ?: return@execute
            val category = getGasCategory()

            category?.let {
                searchService.preferences.lmks()
                    ?.addStoreCategoryId(it.getLandmarkStoreId(), it.getId())
            }

            referencePoint = reference
            searchService.searchAroundPosition(reference, "")
        }
    }
}
