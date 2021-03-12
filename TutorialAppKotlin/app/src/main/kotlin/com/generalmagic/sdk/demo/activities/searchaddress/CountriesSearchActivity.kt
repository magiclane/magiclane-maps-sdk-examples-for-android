/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.demo.activities.searchaddress

import android.graphics.Bitmap
import android.os.Bundle
import com.generalmagic.sdk.core.EAddressField
import com.generalmagic.sdk.core.Landmark
import com.generalmagic.sdk.core.MapDetails
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.demo.activities.SLIAdapter
import com.generalmagic.sdk.demo.activities.SearchListActivity
import com.generalmagic.sdk.demo.activities.SearchListItem
import com.generalmagic.sdk.demo.app.GEMApplication
import com.generalmagic.sdk.demo.util.Utils
import com.generalmagic.sdk.searching.EAddressDetailLevel
import com.generalmagic.sdk.searching.GuidedAddressSearchService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import com.generalmagic.sdk.util.SdkList
import com.generalmagic.sdk.util.StringIds
import kotlinx.android.synthetic.main.activity_list_view.*

class CountriesSearchActivity : SearchListActivity() {
    var results = SdkList(Landmark::class)
    var mFilter = ""

    var mItems = ArrayList<CountryModelItem>()

    val listener = object : ProgressListener() {
        override fun notifyComplete(reason: Int, hint: String) {
            SdkCall.checkCurrentThread()
            val gemError = SdkError.fromInt(reason)
            if (gemError != SdkError.KCancel) {
                mItems.clear()
            }

            val landmarks = results.asArrayList()

            if (gemError == SdkError.KNoError) {
                for (landmark in landmarks) {
                    mItems.add(CountryModelItem(landmark))
                }
            }

            if (gemError != SdkError.KCancel) {
                GEMApplication.postOnMain {
                    GEMApplication.hideBusyIndicator()
                    refresh()

                    if (gemError != SdkError.KNoError) {
                        GEMApplication.showErrorMessage(gemError)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // display all countries
        search(mFilter)
    }

    override fun applyFilter(filter: String) {
        filter.trim()

        if (mFilter != filter) {
            mFilter = filter
            cancel()
            search(filter)
        }
    }

    fun search(filter: String = "") {
        mFilter = filter

        SdkCall.execute {
            results = SdkList(Landmark::class)
            GuidedAddressSearchService().search(
                results,
                Landmark(),
                mFilter,
                EAddressDetailLevel.EAD_Country,
                listener
            )
        }

        GEMApplication.showBusyIndicator()
    }

    fun cancel() {
        SdkCall.execute { GuidedAddressSearchService().cancelSearch(listener) }
    }

    fun didTapItem(item: CountryModelItem) {
        SdkCall.execute { GEMAddressSearchView.onCountrySelected(item.m_landmark) }
        finish()
    }

    override fun refresh() {
        val result = ArrayList<SearchListItem>()
        for (item in mItems) {
            item.mOnClick = { didTapItem(item) }
            result.add(item)
        }

        val adapter = SLIAdapter(result)
        list_view.adapter = adapter
    }

    fun getFilterHint(): String {
        return Utils.getUIString(StringIds.eStrSearch)
    }
}

class CountryModelItem(val m_landmark: Landmark) : SearchListItem() {
    private val mText: String = SdkCall.execute { m_landmark.getName() } ?: ""

    override fun getIcon(width: Int, height: Int): Bitmap? = SdkCall.execute {
        val isoCode = m_landmark.getAddress()?.getField(EAddressField.ECountryCode)
        if (isoCode?.isNotEmpty() == true) {
            val image = MapDetails().getCountryFlag(isoCode)
            return@execute Utils.getImageAsBitmap(image, width, height)
        }

        return@execute null
    }

    override fun getText(): String {
        return mText
    }
}
