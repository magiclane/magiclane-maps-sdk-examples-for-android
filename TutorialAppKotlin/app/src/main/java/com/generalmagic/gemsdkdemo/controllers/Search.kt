// Copyright (C) 2019-2020, General Magic B.V.
// All rights reserved.
//
// This software is confidential and proprietary information of General Magic
// ("Confidential Information"). You shall not disclose such Confidential
// Information and shall use it only in accordance with the terms of the
// license agreement you entered into with General Magic.

package com.generalmagic.gemsdkdemo.controllers

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SearchView
import android.widget.Toast
import com.generalmagic.gemsdk.GenericCategories
import com.generalmagic.gemsdk.ProgressListener
import com.generalmagic.gemsdk.SearchService
import com.generalmagic.gemsdk.models.Coordinates
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.models.LandmarkCategory
import com.generalmagic.gemsdk.models.SearchPreferences
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMList
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdkdemo.R
import com.generalmagic.gemsdkdemo.activities.LandmarkViewModel
import com.generalmagic.gemsdkdemo.activities.SLIAdapter
import com.generalmagic.gemsdkdemo.activities.SearchListActivity
import com.generalmagic.gemsdkdemo.activities.SearchListItem
import com.generalmagic.gemsdkdemo.util.IntentHelper
import com.generalmagic.gemsdkdemo.util.KeyboardUtil
import com.generalmagic.gemsdkdemo.util.StaticsHolder
import kotlinx.android.synthetic.main.activity_list_view.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.filter_view.*

open class SearchServiceWrapper {
	val service = SearchService()
	val results = GEMList(Landmark::class)
	val listener = object : ProgressListener() {
		override fun notifyStart(hasProgress: Boolean) {
			Handler(Looper.getMainLooper()).post {
				onSearchStarted()
			}
		}

		override fun notifyComplete(reason: Int, hint: String) {
			Handler(Looper.getMainLooper()).post {
				onSearchCompleted(reason)
			}
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
					this@BaseSearchTutorialActivity,
					"Search failed: $gemError",
					Toast.LENGTH_SHORT
				).show()
				return
			}

			val list = GEMSdkCall.execute { results.asArrayList() } ?: ArrayList()
			val reference = preferences.getReferencePoint()

			val wrapped = wrap(list, reference)
			val adapter = SLIAdapter(wrapped)

			list_view.adapter = adapter
			if (reason == GEMError.KNoError.value && list.isEmpty()) {
				Toast.makeText(this@BaseSearchTutorialActivity, "No result!", Toast.LENGTH_SHORT)
					.show()
			}
		}
	}

	open fun doStart() {}

	fun doStop() {
		GEMSdkCall.checkCurrentThread()

		Handler(Looper.getMainLooper()).post {
			hideProgress()
		}

		search.service.cancelRequest(search.listener)
	}

	override fun onBackPressed() {
		val listAdapter = list_view?.adapter
		if (listAdapter != null && listAdapter.itemCount > 0) {
			hideListView()
			return
		}

		finish()
	}


	private fun wrap(
		list: ArrayList<Landmark>,
		reference: Coordinates?
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
		IntentHelper.addObjectForKey(itLandmark, WikiController.EXTRA_LANDMARK)
		StaticsHolder.getMainActivity()?.let {
			it.nav_view.setCheckedItem(R.id.tutorial_wiki)
			it.nav_view.menu.performIdentifierAction(R.id.tutorial_wiki, 0)
		}
	}
}

// -------------------------------------------------------------------------------------------------

class SearchTextActivity : BaseSearchTutorialActivity() {
	private val searchViewListener = object : SearchView.OnQueryTextListener {
		override fun onQueryTextSubmit(text: String?): Boolean {
			GEMSdkCall.execute { doSearch(text) }
			return true
		}

		override fun onQueryTextChange(text: String?): Boolean {
			GEMSdkCall.execute { doSearch(text) }
			return true
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		filterView.visibility = View.VISIBLE
		searchInput.requestFocus()
		searchInput.setOnQueryTextListener(searchViewListener)

		GEMSdkCall.execute { doStart() }
	}

	private fun doSearch(text: String?) {
		GEMSdkCall.checkCurrentThread()

		if (text.isNullOrEmpty()) {
			doStop() // if there is any in progress
			Handler(Looper.getMainLooper()).post {
				hideListView()
			}
			return
		}

		val coords =
			StaticsHolder.getMainMapView()?.getCursorWgsPosition() ?: return

		preferences.setReferencePoint(coords)
		preferences.setSearchAddresses(true)
		preferences.setSearchMapPOIs(true)
		search.service.searchByFilter(
			search.results,
			search.listener,
			text,
			preferences,
			null
		)
	}
}

// -------------------------------------------------------------------------------------------------

class SearchNearbyActivity : BaseSearchTutorialActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		filterView.visibility = View.GONE
		GEMSdkCall.execute { doStart() }
	}

	override fun doStart() {
		GEMSdkCall.checkCurrentThread()
		doStop()

		val coords =
			StaticsHolder.getMainMapView()?.getCursorWgsPosition() ?: return

		preferences.setReferencePoint(coords)
		preferences.setSearchMapPOIs(true)

		search.service.searchAroundPosition(
			search.results, search.listener, coords, "", preferences
		)
	}
}


//--------------------------------------------------------------------------------------------------

class SearchPoiActivity : BaseSearchTutorialActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		filterView.visibility = View.GONE
		GEMSdkCall.execute { doStart() }
	}

	private fun getGasCategory(): LandmarkCategory? {
		GEMSdkCall.checkCurrentThread()
		val categoriesList = GenericCategories().getCategories() ?: return null

		var category: LandmarkCategory? = null
		if (categoriesList.size > 0) {
			category = categoriesList[0]
		}
		return category
	}

	override fun doStart() {
		GEMSdkCall.checkCurrentThread()

		doStop()

		val coords = StaticsHolder.getMainMapView()?.getCursorWgsPosition() ?: return

		val category = getGasCategory()

		preferences.setReferencePoint(coords)
		if (category != null)
			preferences.lmks()?.addStoreCategoryId(category.getLandmarkStoreId(), category.getId())

		search.service.searchAroundPosition(
			search.results, search.listener, coords, "", preferences
		)
	}
}
