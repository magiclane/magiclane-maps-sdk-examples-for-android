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

import android.content.Intent
import android.graphics.Bitmap
import android.widget.EditText
import com.generalmagic.apihelper.EnumHelp
import com.generalmagic.sdk.core.EAddressField
import com.generalmagic.sdk.core.Landmark
import com.generalmagic.sdk.core.MapDetails
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.demo.app.GEMApplication
import com.generalmagic.sdk.demo.app.Tutorials
import com.generalmagic.sdk.demo.util.Utils
import com.generalmagic.sdk.searching.EAddressDetailLevel
import com.generalmagic.sdk.searching.GuidedAddressSearchService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import com.generalmagic.sdk.util.SdkList
import com.generalmagic.sdk.util.StringIds

object GEMAddressSearchView {

    private val addressSearchActivitiesMap: HashMap<Long, SearchAddressActivity> = HashMap()
    var shouldChangeText = true

    private var fieldEnabledMap = sortedMapOf(
        Pair(EAddressField.ECountry, true),
        Pair(EAddressField.EState, true),
        Pair(EAddressField.ECity, false),
        Pair(EAddressField.EStreetName, false),
        Pair(EAddressField.EStreetNumber, false),
        Pair(EAddressField.ECrossing1, false)
    )

    fun registerActivity(viewId: Long, searchAddressActivity: SearchAddressActivity) {
        addressSearchActivitiesMap[viewId] = searchAddressActivity
        search(m_country)
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

    fun indexIsValid(index: Int): Boolean {
        return ((index >= 0) && (index < mItems.size))
    }

    fun didTapItem(viewId: Long, index: Int) {
        if (indexIsValid(index)) {
            if ((mDetailLevel == EAddressDetailLevel.EAD_Crossing) ||
                (mDetailLevel == EAddressDetailLevel.EAD_HouseNumber)
            ) {
                val landmark = mItems[index].mLandmark
                landmark.let { it ->
                    showOnMap(viewId, it)
                }

            } else {
                when (mDetailLevel) {
                    EAddressDetailLevel.EAD_State -> m_state = mItems[index].mLandmark
                    EAddressDetailLevel.EAD_City -> m_city = mItems[index].mLandmark
                    EAddressDetailLevel.EAD_Street -> m_street = mItems[index].mLandmark

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

            mListener?.let {
                SdkCall.execute { GuidedAddressSearchService().cancelSearch(it) }
            }

            search(getParentLandmark(field), filter, getDetailLevel(field))
        }
    }

    private fun getParentLandmark(field: EAddressField): Landmark? {
        when (field) {
            EAddressField.EState -> {
                return m_country
            }
            EAddressField.ECity -> {
                return if (hasState()) m_state else m_country
            }
            EAddressField.EStreetName -> {
                return m_city
            }
            EAddressField.EStreetNumber -> {
                return m_street
            }
            EAddressField.ECrossing1 -> {
                return m_street
            }
            else -> {
                return null
            }
        }
    }

    private fun getDetailLevel(field: EAddressField): EAddressDetailLevel {
        when (field) {
            EAddressField.ECountry -> {
                return EAddressDetailLevel.EAD_Country
            }
            EAddressField.EState -> {
                return EAddressDetailLevel.EAD_State
            }
            EAddressField.ECity -> {
                return EAddressDetailLevel.EAD_City
            }
            EAddressField.EStreetName -> {
                return EAddressDetailLevel.EAD_Street
            }
            EAddressField.EStreetNumber -> {
                return EAddressDetailLevel.EAD_HouseNumber
            }
            EAddressField.ECrossing1 -> {
                return EAddressDetailLevel.EAD_Crossing
            }
            else -> {
                return EAddressDetailLevel.EAD_NoDetail
            }
        }
    }

    fun didTapSearchButton(viewId: Long) {
        var landmark: Landmark? = null
        when (mField) {
            EAddressField.ECountry,
            EAddressField.EState -> {
                return
            }
            EAddressField.ECity -> {
                landmark = m_city
            }
            EAddressField.EStreetName -> {
                landmark = m_street
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

        var name = landmark.getName() ?: ""
        name = name.replace("<", "").replace(">", "")
        landmark.setName(name)

        GEMApplication.postOnMain {
            Tutorials.openWikiTutorial(landmark)
            hideKeyboard(0)
            addressSearchActivitiesMap[viewId]?.finish()
        }
    }

    fun getTitle(): String {
        return Utils.getUIString(StringIds.eStrAddress)
    }

    data class AddressSearchModelItem(
        var mLandmark: Landmark?,
        var mAddressDetailLevel: EAddressDetailLevel,
        var anywhere: Boolean
    )

    private var mDetailLevel = EAddressDetailLevel.EAD_NoDetail
    private var mField = EAddressField.ECountry

    private var mLandmarks = ArrayList<Landmark>()
    private var mFilter = ""
    private var mLastSuccessfulFilter = ""
    private var mItems = ArrayList<AddressSearchModelItem>()
    private var mListener: ProgressListener? = null

    private fun search(
        landmark: Landmark?,
        filter: String = "",
        detailLevel: EAddressDetailLevel = EAddressDetailLevel.EAD_NoDetail
    ) {
        landmark ?: return
        mFilter = filter

        if (detailLevel != EAddressDetailLevel.EAD_NoDetail) {
            mDetailLevel = detailLevel
        } else {
            SdkCall.execute {
                GuidedAddressSearchService().getNextAddressDetailLevel(landmark)?.let {
                    val nextDetailLevel = it
                    if (nextDetailLevel.size > 0) {
                        mDetailLevel = EnumHelp.fromInt(nextDetailLevel[0])
                    }
                }
            }
        }

        if (mDetailLevel != EAddressDetailLevel.EAD_NoDetail) {
            SdkCall.execute {
                val results = SdkList(Landmark::class)

                val listener = object : ProgressListener() {
                    override fun notifyComplete(reason: Int, hint: String) {
                        val gemReason = SdkError.fromInt(reason)
                        val bNoError =
                            gemReason == SdkError.KNoError || gemReason == SdkError.KReducedResult

                        if (bNoError) {
                            mLandmarks = results.asArrayList()
                            if (mLandmarks.size > 0 || mDetailLevel == EAddressDetailLevel.EAD_Crossing) {
                                mLastSuccessfulFilter = mFilter
                                mItems.clear()

                                if (mDetailLevel == EAddressDetailLevel.EAD_Crossing) {
                                    mItems.add(
                                        AddressSearchModelItem(
                                            m_street,
                                            mDetailLevel,
                                            true
                                        )
                                    )
                                }

                                for (item in mLandmarks) {
                                    mItems.add(AddressSearchModelItem(item, mDetailLevel, false))
                                }

                            } else if (mField == EAddressField.EStreetNumber) {
                                selectIntersectionField(0)
                            } else {
                                setFilter(0, mField.value, mLastSuccessfulFilter)
                                hideBusyIndicator(0)
                            }
                            refreshSearchResultsList(0)
                        } else if (gemReason != SdkError.KCancel) {
                            mItems.clear()
                        }

                        hideBusyIndicator(0)
                    }
                }
                mListener = listener

                GuidedAddressSearchService().search(
                    results,
                    landmark,
                    mFilter,
                    mDetailLevel,
                    listener
                )
            }

            showBusyIndicator(0)
        }
    }

    var m_state: Landmark? = null
    var m_country: Landmark? = null
    var m_city: Landmark? = null
    var m_street: Landmark? = null

    fun init() {
        SdkCall.checkCurrentThread()

        m_state = Landmark()
        m_city = Landmark()
        m_street = Landmark()
    }

    private fun reset() {
        SdkCall.checkCurrentThread()

        m_state = null
        m_city = null
        m_street = null
    }

    fun onCountrySelected(country: Landmark) {
        SdkCall.checkCurrentThread()

        val isoCodeOld = m_country?.getAddress()?.getField(EAddressField.ECountryCode)
        val isoCodeNew = country.getAddress()?.getField(EAddressField.ECountryCode)

        if (isoCodeOld != isoCodeNew) {
            m_country = country
            reset()
            search(m_country)
            refresh(0)
        }
    }

    fun hasState(): Boolean {
        val country = this.m_country ?: return false
        val list = SdkCall.execute {
            return@execute GuidedAddressSearchService().getNextAddressDetailLevel(country)
        } ?: return false
        return list.isNotEmpty() && list[0] == EAddressDetailLevel.EAD_State.value
    }

    fun getCountryFlag(width: Int, height: Int): Bitmap? {
        SdkCall.checkCurrentThread()
        val isoCode = m_country?.getAddress()?.getField(EAddressField.ECountryCode)
        if (isoCode?.isNotEmpty() == true) {
            val image = MapDetails().getCountryFlag(isoCode)
            return Utils.getImageAsBitmap(image, width, height)
        }

        return null
    }

    fun getHint(field: Int): String {
        when (EnumHelp.fromInt<EAddressField>(field)) {
            EAddressField.EState -> {
                return Utils.getUIString(StringIds.eStrState)
            }
            EAddressField.ECity -> {
                return Utils.getUIString(StringIds.eStrCity)
            }
            EAddressField.EStreetName -> {
                return Utils.getUIString(StringIds.eStrStreet)
            }
            EAddressField.EStreetNumber -> {
                return Utils.getUIString(StringIds.eStrNumberAbbv)
            }
            EAddressField.ECrossing1 -> {
                return Utils.getUIString(StringIds.eStrCrossing)
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

        if (detailLevel == EAddressDetailLevel.EAD_State) {
            text =
                landmark?.getAddress()?.getField(EAddressField.EStateCode)
        } else if (detailLevel == EAddressDetailLevel.EAD_HouseNumber) {
            text = SdkCall.execute { landmark?.getName() } ?: ""

            var pos: Int = text.indexOf(">")
            if (pos > 0) {
                text = text.substring(0, pos)
            }

            pos = text.indexOf("<")
            if (pos >= 0) {
                text = text.substring(pos + 1, text.length)
            }
        } else if (detailLevel == EAddressDetailLevel.EAD_Crossing && item.anywhere) {
            text = Utils.getUIString(StringIds.eStrTempAnywhere)
        } else {
            text = SdkCall.execute { landmark?.getName() }
        }

        return text ?: ""
    }

    fun getItemDescription(index: Int): String {
        if (index > mItems.size || index < 0) return ""
        val item = mItems[index]
        val landmark = item.mLandmark
        val detailLevel = item.mAddressDetailLevel

        var description: String? = null

        if (detailLevel == EAddressDetailLevel.EAD_State) {
            description = SdkCall.execute { landmark?.getName() }
        }

        return description ?: ""
    }

    fun getItemImage(index: Int, width: Int, height: Int): Bitmap? {
        if (index > mItems.size || index < 0) return null

        val item = mItems[index]
        val landmark = item.mLandmark
        if (item.mAddressDetailLevel == EAddressDetailLevel.EAD_City) {
            return SdkCall.execute {
                return@execute Utils.getImageAsBitmap(landmark?.getImage(), width, height)
            }
        }

        return null
    }

    fun hideKeyboard(viewId: Long) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.hideKeyboard()
        }
    }

    fun showKeyboard(viewId: Long, fieldView: EditText) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.showKeyboard(fieldView)
        }
    }
}
