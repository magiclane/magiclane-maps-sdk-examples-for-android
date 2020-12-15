/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.activities.mainactivity.controllers

import android.os.Bundle
import android.view.View
import android.widget.SearchView
import android.widget.Toast
import com.generalmagic.gemsdk.GenericCategories
import com.generalmagic.gemsdk.ProgressListener
import com.generalmagic.gemsdk.SearchService
import com.generalmagic.gemsdk.demo.activities.LandmarkViewModel
import com.generalmagic.gemsdk.demo.activities.SLIAdapter
import com.generalmagic.gemsdk.demo.activities.SearchListActivity
import com.generalmagic.gemsdk.demo.activities.SearchListItem
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.app.Tutorials
import com.generalmagic.gemsdk.demo.util.KeyboardUtil
import com.generalmagic.gemsdk.models.Coordinates
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.models.LandmarkCategory
import com.generalmagic.gemsdk.models.SearchPreferences
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMList
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.activity_list_view.*
import kotlinx.android.synthetic.main.filter_view.*

open class SearchServiceWrapper {
    val service = SearchService()
    val results = GEMList(Landmark::class)
    val listener = object : ProgressListener() {
        override fun notifyStart(hasProgress: Boolean) = GEMApplication.postOnMain {
            onSearchStarted()
        }

        override fun notifyComplete(reason: Int, hint: String) = GEMApplication.postOnMain {
            onSearchCompleted(reason)
        }
    }

    open fun onSearchStarted() {}
    open fun onSearchCompleted(reason: Int) {}
}

open class BaseSearchTutorialActivity : SearchListActivity() {
    protected val preferences = SearchPreferences()
    val search = object : SearchServiceWrapper() {
        override fun onSearchStarted() {
            showProgress()
            hideListView()
        }

        override fun onSearchCompleted(reason: Int) {
            val gemError = GEMError.fromInt(reason)
            if (gemError == GEMError.KCancel) return

            hideProgress()

            if (gemError != GEMError.KNoError) {
                Toast.makeText(
                    this@BaseSearchTutorialActivity, "Search failed: $gemError", Toast.LENGTH_SHORT
                ).show()
                return
            }

            val list = GEMSdkCall.execute { results.asArrayList() } ?: ArrayList()
            val reference = GEMSdkCall.execute { preferences.getReferencePoint() }

            val wrapped = wrap(list, reference)
            val adapter = SLIAdapter(wrapped)

            list_view.adapter = adapter
            if (reason == GEMError.KNoError.value && list.isEmpty()) {
                Toast.makeText(
                    this@BaseSearchTutorialActivity, "No result!", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun doStart() {}

    override fun doStop() {
        hideProgress()

        GEMSdkCall.execute {
            search.service.cancelRequest(search.listener)
        }
    }

    private fun wrap(
        list: ArrayList<Landmark>, reference: Coordinates?
    ): ArrayList<SearchListItem> {
        val result = ArrayList<SearchListItem>()
        for (item in list) {
            val wrappedItem = LandmarkViewModel(item, reference)
            wrappedItem.mOnClick = { onItemClicked(wrappedItem) }
            result.add(wrappedItem)
        }
        return result
    }

    protected open fun onItemClicked(item: LandmarkViewModel) {
        KeyboardUtil.hideKeyboard(this)
        val itLandmark = item.it ?: return

        finish()

        /*DEFAULT*/
        Tutorials.openWikiTutorial(itLandmark)
    }

    protected fun getCurrentCoords(): Coordinates? = GEMSdkCall.execute {
        GEMApplication.getMainMapView()?.getCursorWgsPosition() ?: return@execute null
    }
}

class SearchTextActivity : BaseSearchTutorialActivity() {
    var lastFilter = ""
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

        GEMSdkCall.execute {
            val coords = getCurrentCoords() ?: return@execute

            preferences.setReferencePoint(coords)
            preferences.setSearchAddresses(true)
            preferences.setSearchMapPOIs(true)
            search.service.searchByFilter(
                search.results, search.listener, text, preferences, null
            )
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

        GEMSdkCall.execute {
            val coords = getCurrentCoords() ?: return@execute

            preferences.setReferencePoint(coords)
            preferences.setSearchMapPOIs(true)

            search.service.searchAroundPosition(
                search.results, search.listener, coords, "", preferences
            )
        }
    }
}

class SearchPoiActivity : BaseSearchTutorialActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filterView.visibility = View.GONE
    }

    private fun getGasCategory(): LandmarkCategory? = GEMSdkCall.execute {
        val categoriesList = GenericCategories().getCategories() ?: return@execute null

        var category: LandmarkCategory? = null
        if (categoriesList.size > 0) {
            category = categoriesList[0]
        }

        return@execute category
    }

    override fun doStart() {
        doStop()

        GEMSdkCall.execute {
            val coords = getCurrentCoords() ?: return@execute
            val category = getGasCategory()

            preferences.setReferencePoint(coords)
            category?.let {
                preferences.lmks()?.addStoreCategoryId(it.getLandmarkStoreId(), it.getId())
            }

            search.service.searchAroundPosition(
                search.results, search.listener, coords, "", preferences
            )
        }
    }
}
