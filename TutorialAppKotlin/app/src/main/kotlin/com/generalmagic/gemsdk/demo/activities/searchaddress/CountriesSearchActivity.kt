/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.activities.searchaddress

import android.graphics.Bitmap
import android.os.Bundle
import com.generalmagic.gemsdk.GuidedAddressSearchService
import com.generalmagic.gemsdk.MapDetails
import com.generalmagic.gemsdk.ProgressListener
import com.generalmagic.gemsdk.TAddressDetailLevel
import com.generalmagic.gemsdk.demo.activities.SLIAdapter
import com.generalmagic.gemsdk.demo.activities.SearchListActivity
import com.generalmagic.gemsdk.demo.activities.SearchListItem
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.util.Utils
import com.generalmagic.gemsdk.extensions.StringIds
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.models.TAddressField
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMList
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.activity_list_view.*

class CountriesSearchActivity : SearchListActivity() {
    var results = GEMList(Landmark::class)
    var mFilter = ""

    var mItems = ArrayList<CountryModelItem>()

    val listener = object : ProgressListener() {
        override fun notifyComplete(reason: Int, hint: String) {
            GEMSdkCall.checkCurrentThread()
            val gemError = GEMError.fromInt(reason)
            if (gemError != GEMError.KCancel) {
                mItems.clear()
            }

            val landmarks = results.asArrayList()

            if (gemError == GEMError.KNoError) {
                for (landmark in landmarks) {
                    mItems.add(CountryModelItem(landmark))
                }
            }

            if (gemError != GEMError.KCancel) {
                GEMApplication.postOnMain {
                    GEMApplication.hideBusyIndicator()
                    refresh()

                    if (gemError != GEMError.KNoError) {
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

        GEMSdkCall.execute {
            results = GEMList(Landmark::class)
            GuidedAddressSearchService().search(
                results,
                Landmark(),
                mFilter,
                TAddressDetailLevel.EAD_Country,
                listener
            )
        }

        GEMApplication.showBusyIndicator()
    }

    fun cancel() {
        GEMSdkCall.execute { GuidedAddressSearchService().cancelSearch(listener) }
    }

    fun didTapItem(item: CountryModelItem) {
        GEMSdkCall.execute { GEMAddressSearchView.onCountrySelected(item.m_landmark) }
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
    private val mText: String = GEMSdkCall.execute { m_landmark.getName() } ?: ""

    override fun getIcon(width: Int, height: Int): Bitmap? = GEMSdkCall.execute {
        val isoCode = m_landmark.getAddress()?.getField(TAddressField.ECountryCode)
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
