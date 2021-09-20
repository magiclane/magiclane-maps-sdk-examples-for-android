/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("SameParameterValue")

package com.generalmagic.sdk.examples.demo.activities.searchaddress

import android.content.Intent
import android.graphics.Bitmap
import android.widget.EditText
import com.generalmagic.sdk.core.MapDetails
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.app.Tutorials
import com.generalmagic.sdk.examples.demo.util.Utils
import com.generalmagic.sdk.places.EAddressDetailLevel
import com.generalmagic.sdk.places.EAddressField
import com.generalmagic.sdk.places.GuidedAddressSearchService
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.util.EnumHelp
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.StringIds
import com.generalmagic.sdk.util.SdkUtil.getUIString
import com.generalmagic.sdk.util.UtilGemImages

object GEMAddressSearchView {

    private val addressSearchActivitiesMap: HashMap<Long, SearchAddressActivity> = HashMap()
    var shouldChangeText = true

    private val service = GuidedAddressSearchService()

    private var fieldEnabledMap = sortedMapOf(
        Pair(EAddressField.Country, true),
        Pair(EAddressField.State, true),
        Pair(EAddressField.City, false),
        Pair(EAddressField.StreetName, false),
        Pair(EAddressField.StreetNumber, false),
        Pair(EAddressField.Crossing1, false)
    )

    fun registerActivity(viewId: Long, searchAddressActivity: SearchAddressActivity) {
        addressSearchActivitiesMap[viewId] = searchAddressActivity
        search(mCountry)
    }

    private fun unregisterActivity(viewId: Long) {
        addressSearchActivitiesMap.remove(viewId)
    }

    fun onViewClosed(viewId: Long) {
        unregisterActivity(viewId)
        reset()
    }

    private fun showBusyIndicator(viewId: Long) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.showProgress()
        }
    }

    private fun hideBusyIndicator(viewId: Long) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.hideProgress()
        }
    }

    private fun refresh(viewId: Long) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.refresh()
        }
    }

    private fun refreshSearchResultsList(viewId: Long) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.refreshSearchResultsList()
        }
    }

    private fun selectIntersectionField(viewId: Long) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.selectIntersectionField()
        }
    }

    private fun setFilter(viewId: Long, field: Int, filter: String) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.setField(field, filter)
        }
    }

    fun isEnabled(field: EAddressField): Boolean {
        return fieldEnabledMap[field] ?: false
    }


    fun setEnabledState(field: EAddressField, value: Boolean) {
        fieldEnabledMap[field] = value
    }

    private fun indexIsValid(index: Int): Boolean {
        return ((index >= 0) && (index < mItems.size))
    }

    fun didTapItem(viewId: Long, index: Int) {
        if (indexIsValid(index)) {
            if ((mDetailLevel == EAddressDetailLevel.Crossing) ||
                (mDetailLevel == EAddressDetailLevel.HouseNumber)
            ) {
                val landmark = mItems[index].mLandmark
                showOnMap(viewId, landmark)

            } else {
                when (mDetailLevel) {
                    EAddressDetailLevel.State -> mState = mItems[index].mLandmark
                    EAddressDetailLevel.City -> mCity = mItems[index].mLandmark
                    EAddressDetailLevel.Street -> mStreet = mItems[index].mLandmark

                    else -> {
                    }
                }
            }
        }
    }

    fun didTapCountryFlag(viewId: Long) {
        val activity = addressSearchActivitiesMap[viewId] ?: return

        val intent = Intent(activity, CountriesSearchActivity::class.java)
        activity.startActivity(intent)
    }

    fun didChangeFilter(iField: Int, filter: String) {
        filter.trim()
        val field = EnumHelp.fromInt<EAddressField>(iField)

        if ((mFilter != filter) || (mField != field)) {
            if (mField != field) {
                mLastSuccessfulFilter = ""
            }

            mFilter = filter
            mField = field

            SdkCall.execute { service.cancelSearch() }

            search(getParentLandmark(field), filter, getDetailLevel(field))
        }
    }

    private fun getParentLandmark(field: EAddressField): Landmark? {
        when (field) {
            EAddressField.State -> {
                return mCountry
            }
            EAddressField.City -> {
                return if (hasState()) mState else mCountry
            }
            EAddressField.StreetName -> {
                return mCity
            }
            EAddressField.StreetNumber -> {
                return mStreet
            }
            EAddressField.Crossing1 -> {
                return mStreet
            }
            else -> {
                return null
            }
        }
    }

    private fun getDetailLevel(field: EAddressField): EAddressDetailLevel {
        when (field) {
            EAddressField.Country -> {
                return EAddressDetailLevel.Country
            }
            EAddressField.State -> {
                return EAddressDetailLevel.State
            }
            EAddressField.City -> {
                return EAddressDetailLevel.City
            }
            EAddressField.StreetName -> {
                return EAddressDetailLevel.Street
            }
            EAddressField.StreetNumber -> {
                return EAddressDetailLevel.HouseNumber
            }
            EAddressField.Crossing1 -> {
                return EAddressDetailLevel.Crossing
            }
            else -> {
                return EAddressDetailLevel.NoDetail
            }
        }
    }

    fun didTapSearchButton(viewId: Long) {
        var landmark: Landmark? = null
        when (mField) {
            EAddressField.Country,
            EAddressField.State -> {
                return
            }
            EAddressField.City -> {
                landmark = mCity
            }
            EAddressField.StreetName -> {
                landmark = mStreet
            }
            else -> {
            }
        }

        SdkCall.execute {
            if (landmark != null) {
                showOnMap(viewId, landmark)
            } else if (mItems.size > 0) {
                showOnMap(viewId, mItems[0].mLandmark)
            }
        }
    }

    private fun showOnMap(viewId: Long, landmark: Landmark?) {
        if (landmark == null) return

        var name = landmark.name ?: ""
        name = name.replace("<", "").replace(">", "")
        landmark.name = name

        GEMApplication.postOnMain {
            Tutorials.openWikiTutorial(landmark)
            hideKeyboard(0)
            addressSearchActivitiesMap[viewId]?.finish()
        }
    }

    fun getTitle(): String {
        return getUIString(StringIds.eStrAddress)
    }

    data class AddressSearchModelItem(
        var mLandmark: Landmark?,
        var mAddressDetailLevel: EAddressDetailLevel,
        var anywhere: Boolean
    )

    private var mDetailLevel = EAddressDetailLevel.NoDetail
    private var mField = EAddressField.Country

    private var mLandmarks = ArrayList<Landmark>()
    private var mFilter = ""
    private var mLastSuccessfulFilter = ""
    private var mItems = ArrayList<AddressSearchModelItem>()

    private fun search(
        landmark: Landmark?,
        filter: String = "",
        detailLevel: EAddressDetailLevel = EAddressDetailLevel.NoDetail
    ) {
        landmark ?: return
        mFilter = filter

        if (detailLevel != EAddressDetailLevel.NoDetail) {
            mDetailLevel = detailLevel
        } else {
            SdkCall.execute {
                service.getNextAddressDetailLevel(landmark)?.let {
                    val nextDetailLevel = it
                    if (nextDetailLevel.size > 0) {
                        mDetailLevel = EnumHelp.fromInt(nextDetailLevel[0])
                    }
                }
            }
        }

        if (mDetailLevel != EAddressDetailLevel.NoDetail) {
            SdkCall.execute {
                service.search(landmark, mFilter, mDetailLevel) { landmarks, gemReason, _ ->
                    val bNoError =
                        gemReason == SdkError.NoError || gemReason == SdkError.ReducedResult

                    if (bNoError) {
                        mLandmarks = landmarks
                        if (mLandmarks.size > 0 || mDetailLevel == EAddressDetailLevel.Crossing) {
                            mLastSuccessfulFilter = mFilter
                            mItems.clear()

                            if (mDetailLevel == EAddressDetailLevel.Crossing) {
                                mItems.add(
                                    AddressSearchModelItem(
                                        mStreet,
                                        mDetailLevel,
                                        true
                                    )
                                )
                            }

                            for (item in mLandmarks) {
                                mItems.add(AddressSearchModelItem(item, mDetailLevel, false))
                            }

                        } else if (mField == EAddressField.StreetNumber) {
                            selectIntersectionField(0)
                        } else {
                            setFilter(0, mField.value, mLastSuccessfulFilter)
                            hideBusyIndicator(0)
                        }
                        refreshSearchResultsList(0)
                    } else if (gemReason != SdkError.Cancel) {
                        mItems.clear()
                    }

                    hideBusyIndicator(0)
                }
            }

            showBusyIndicator(0)
        }
    }

    private var mState: Landmark? = null
    var mCountry: Landmark? = null
    private var mCity: Landmark? = null
    private var mStreet: Landmark? = null

    fun init() {
        SdkCall.checkCurrentThread()

        mState = Landmark()
        mCity = Landmark()
        mStreet = Landmark()
    }

    private fun reset() {
        SdkCall.checkCurrentThread()

        mState = null
        mCity = null
        mStreet = null
    }

    fun onCountrySelected(country: Landmark) {
        SdkCall.checkCurrentThread()

        val isoCodeOld = mCountry?.addressInfo?.getField(EAddressField.CountryCode)
        val isoCodeNew = country.addressInfo?.getField(EAddressField.CountryCode)

        if (isoCodeOld != isoCodeNew) {
            mCountry = country
            reset()
            search(mCountry)
            refresh(0)
        }
    }

    fun hasState(): Boolean {
        val country = this.mCountry ?: return false
        val list = SdkCall.execute {
            return@execute GuidedAddressSearchService().getNextAddressDetailLevel(country)
        } ?: return false
        return list.isNotEmpty() && list[0] == EAddressDetailLevel.State.value
    }

    fun getCountryFlag(width: Int, height: Int): Bitmap? {
        SdkCall.checkCurrentThread()
        val isoCode = mCountry?.addressInfo?.getField(EAddressField.CountryCode)
        if (isoCode?.isNotEmpty() == true) {
            val image = MapDetails().getCountryFlag(isoCode)
            return UtilGemImages.asBitmap(image, width, height)
        }

        return null
    }

    fun getHint(field: Int): String {
        when (EnumHelp.fromInt<EAddressField>(field)) {
            EAddressField.State -> {
                return getUIString(StringIds.eStrState)
            }
            EAddressField.City -> {
                return getUIString(StringIds.eStrCity)
            }
            EAddressField.StreetName -> {
                return getUIString(StringIds.eStrStreet)
            }
            EAddressField.StreetNumber -> {
                return getUIString(StringIds.eStrNumberAbbv)
            }
            EAddressField.Crossing1 -> {
                return getUIString(StringIds.eStrCrossing)
            }
            else -> {
                return String()
            }
        }
    }

    fun getItemsCount(): Int {
        return mItems.size
    }

    fun getItemText(index: Int): String {
        if (index > mItems.size || index < 0) return ""
        val item = mItems[index]
        val landmark = item.mLandmark
        val detailLevel = item.mAddressDetailLevel

        var text: String?

        if (detailLevel == EAddressDetailLevel.State) {
            text =
                landmark?.addressInfo?.getField(EAddressField.StateCode)
        } else if (detailLevel == EAddressDetailLevel.HouseNumber) {
            text = SdkCall.execute { landmark?.name } ?: ""

            var pos: Int = text.indexOf(">")
            if (pos > 0) {
                text = text.substring(0, pos)
            }

            pos = text.indexOf("<")
            if (pos >= 0) {
                text = text.substring(pos + 1, text.length)
            }
        } else if (detailLevel == EAddressDetailLevel.Crossing && item.anywhere) {
            text = getUIString(StringIds.eStrTempAnywhere)
        } else {
            text = SdkCall.execute { landmark?.name }
        }

        return text ?: ""
    }

    fun getItemDescription(index: Int): String {
        if (index > mItems.size || index < 0) return ""
        val item = mItems[index]
        val landmark = item.mLandmark
        val detailLevel = item.mAddressDetailLevel

        var description: String? = null

        if (detailLevel == EAddressDetailLevel.State) {
            description = SdkCall.execute { landmark?.name }
        }

        return description ?: ""
    }

    fun getItemImage(index: Int, width: Int, height: Int): Bitmap? {
        if (index > mItems.size || index < 0) return null

        val item = mItems[index]
        val landmark = item.mLandmark
        if (item.mAddressDetailLevel == EAddressDetailLevel.City) {
            return SdkCall.execute {
                return@execute UtilGemImages.asBitmap(landmark?.image, width, height)
            }
        }

        return null
    }

    private fun hideKeyboard(viewId: Long) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.hideKeyboard()
        }
    }

    @Suppress("unused")
    fun showKeyboard(viewId: Long, fieldView: EditText) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.showKeyboard(fieldView)
        }
    }
}
