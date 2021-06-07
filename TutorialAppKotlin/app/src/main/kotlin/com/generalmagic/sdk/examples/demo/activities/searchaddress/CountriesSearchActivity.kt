/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.searchaddress

import android.graphics.Bitmap
import android.os.Bundle
import com.generalmagic.sdk.core.MapDetails
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.examples.demo.activities.SLIAdapter
import com.generalmagic.sdk.examples.demo.activities.SearchListActivity
import com.generalmagic.sdk.examples.demo.activities.SearchListItem
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.util.Utils
import com.generalmagic.sdk.places.EAddressDetailLevel
import com.generalmagic.sdk.places.EAddressField
import com.generalmagic.sdk.places.GuidedAddressSearchService
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.StringIds
import com.generalmagic.sdk.util.UtilUiTexts.getUIString
import kotlinx.android.synthetic.main.activity_list_view.*

class CountriesSearchActivity : SearchListActivity() {
    private var mFilter = ""

    private val service = GuidedAddressSearchService()

    private var mItems = ArrayList<CountryModelItem>()

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

    private fun search(filter: String = "") {
        mFilter = filter

        SdkCall.execute {
            service.search(
                Landmark(), mFilter, EAddressDetailLevel.Country
            ) { landmarks, gemError, _ ->
                if (gemError != SdkError.Cancel) {
                    mItems.clear()
                }

                if (gemError == SdkError.NoError) {
                    for (landmark in landmarks) {
                        mItems.add(CountryModelItem(landmark))
                    }
                }

                if (gemError != SdkError.Cancel) {
                    GEMApplication.hideBusyIndicator()
                    refresh()

                    if (gemError != SdkError.NoError) {
                        GEMApplication.showErrorMessage(gemError)
                    }
                }
            }
        }

        GEMApplication.showBusyIndicator()
    }

    fun cancel() {
        SdkCall.execute { service.cancelSearch() }
    }

    private fun didTapItem(item: CountryModelItem) {
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

    @Suppress("unused")
    fun getFilterHint(): String {
        return getUIString(StringIds.eStrSearch)
    }
}

class CountryModelItem(val m_landmark: Landmark) : SearchListItem() {
    private val mText: String = SdkCall.execute { m_landmark.getName() } ?: ""

    override fun getIcon(width: Int, height: Int): Bitmap? = SdkCall.execute {
        val isoCode = m_landmark.getAddress()?.getField(EAddressField.CountryCode)
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
