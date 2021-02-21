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

import android.content.Intent
import android.graphics.Bitmap
import android.widget.EditText
import com.generalmagic.apihelper.EnumHelp
import com.generalmagic.gemsdk.GuidedAddressSearchService
import com.generalmagic.gemsdk.MapDetails
import com.generalmagic.gemsdk.ProgressListener
import com.generalmagic.gemsdk.TAddressDetailLevel
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.app.Tutorials
import com.generalmagic.gemsdk.demo.util.Utils
import com.generalmagic.gemsdk.extensions.StringIds
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.models.TAddressField
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMList
import com.generalmagic.gemsdk.util.GEMSdkCall

object GEMAddressSearchView {

    private val addressSearchActivitiesMap: HashMap<Long, SearchAddressActivity> = HashMap()
    var shouldChangeText = true

    private var fieldEnabledMap = sortedMapOf(
        Pair(TAddressField.ECountry, true),
        Pair(TAddressField.EState, true),
        Pair(TAddressField.ECity, false),
        Pair(TAddressField.EStreetName, false),
        Pair(TAddressField.EStreetNumber, false),
        Pair(TAddressField.ECrossing1, false)
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

    fun isEnabled(field: TAddressField): Boolean {
        return fieldEnabledMap[field] ?: false
    }


    fun setEnabledState(field: TAddressField, value: Boolean) {
        fieldEnabledMap[field] = value
    }

    fun indexIsValid(index: Int): Boolean {
        return ((index >= 0) && (index < mItems.size))
    }

    fun didTapItem(viewId: Long, index: Int) {
        if (indexIsValid(index)) {
            if ((mDetailLevel == TAddressDetailLevel.EAD_Crossing) ||
                (mDetailLevel == TAddressDetailLevel.EAD_HouseNumber)
            ) {
                val landmark = mItems[index].mLandmark
                landmark?.let {
                    showOnMap(viewId, it)
                }

            } else {
                when (mDetailLevel) {
                    TAddressDetailLevel.EAD_State -> m_state = mItems[index].mLandmark
                    TAddressDetailLevel.EAD_City -> m_city = mItems[index].mLandmark
                    TAddressDetailLevel.EAD_Street -> m_street = mItems[index].mLandmark

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
        val field = EnumHelp.fromInt<TAddressField>(iField)

        if ((mFilter != filter) || (mField != field)) {
            if (mField != field) {
                mLastSuccessfulFilter = ""
            }

            mFilter = filter
            mField = field

            mListener?.let {
                GEMSdkCall.execute { GuidedAddressSearchService().cancelSearch(it) }
            }

            search(getParentLandmark(field), filter, getDetailLevel(field))
        }
    }

    private fun getParentLandmark(field: TAddressField): Landmark? {
        when (field) {
            TAddressField.EState -> {
                return m_country
            }
            TAddressField.ECity -> {
                return if (hasState()) m_state else m_country
            }
            TAddressField.EStreetName -> {
                return m_city
            }
            TAddressField.EStreetNumber -> {
                return m_street
            }
            TAddressField.ECrossing1 -> {
                return m_street
            }
            else -> {
                return null
            }
        }
    }

    private fun getDetailLevel(field: TAddressField): TAddressDetailLevel {
        when (field) {
            TAddressField.ECountry -> {
                return TAddressDetailLevel.EAD_Country
            }
            TAddressField.EState -> {
                return TAddressDetailLevel.EAD_State
            }
            TAddressField.ECity -> {
                return TAddressDetailLevel.EAD_City
            }
            TAddressField.EStreetName -> {
                return TAddressDetailLevel.EAD_Street
            }
            TAddressField.EStreetNumber -> {
                return TAddressDetailLevel.EAD_HouseNumber
            }
            TAddressField.ECrossing1 -> {
                return TAddressDetailLevel.EAD_Crossing
            }
            else -> {
                return TAddressDetailLevel.EAD_NoDetail
            }
        }
    }

    fun didTapSearchButton(viewId: Long) {
        var landmark: Landmark? = null
        when (mField) {
            TAddressField.ECountry,
            TAddressField.EState -> {
                return
            }
            TAddressField.ECity -> {
                landmark = m_city
            }
            TAddressField.EStreetName -> {
                landmark = m_street
            }
            else -> {
            }
        }

        GEMSdkCall.execute {
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
        var mAddressDetailLevel: TAddressDetailLevel,
        var anywhere: Boolean
    )

    private var mDetailLevel = TAddressDetailLevel.EAD_NoDetail
    private var mField = TAddressField.ECountry

    private var mLandmarks = ArrayList<Landmark>()
    private var mFilter = ""
    private var mLastSuccessfulFilter = ""
    private var mItems = ArrayList<AddressSearchModelItem>()
    private var mListener: ProgressListener? = null

    private fun search(
        landmark: Landmark?,
        filter: String = "",
        detailLevel: TAddressDetailLevel = TAddressDetailLevel.EAD_NoDetail
    ) {
        landmark ?: return
        mFilter = filter

        if (detailLevel != TAddressDetailLevel.EAD_NoDetail) {
            mDetailLevel = detailLevel
        } else {
            GEMSdkCall.execute {
                GuidedAddressSearchService().getNextAddressDetailLevel(landmark)?.let {
                    val nextDetailLevel = it
                    if (nextDetailLevel.size > 0) {
                        mDetailLevel = EnumHelp.fromInt(nextDetailLevel[0])
                    }
                }
            }
        }

        if (mDetailLevel != TAddressDetailLevel.EAD_NoDetail) {
            GEMSdkCall.execute {
                val results = GEMList(Landmark::class)

                val listener = object : ProgressListener() {
                    override fun notifyComplete(reason: Int, hint: String) {
                        val gemReason = GEMError.fromInt(reason)
                        val bNoError =
                            gemReason == GEMError.KNoError || gemReason == GEMError.KReducedResult

                        if (bNoError) {
                            mLandmarks = results.asArrayList()
                            if (mLandmarks.size > 0 || mDetailLevel == TAddressDetailLevel.EAD_Crossing) {
                                mLastSuccessfulFilter = mFilter
                                mItems.clear()

                                if (mDetailLevel == TAddressDetailLevel.EAD_Crossing) {
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

                            } else if (mField == TAddressField.EStreetNumber) {
                                selectIntersectionField(0)
                            } else {
                                setFilter(0, mField.value, mLastSuccessfulFilter)
                                hideBusyIndicator(0)
                            }
                            refreshSearchResultsList(0)
                        } else if (gemReason != GEMError.KCancel) {
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
        GEMSdkCall.checkCurrentThread()

        m_state = Landmark()
        m_city = Landmark()
        m_street = Landmark()
    }

    private fun reset() {
        GEMSdkCall.checkCurrentThread()

        m_state = null
        m_city = null
        m_street = null
    }

    fun onCountrySelected(country: Landmark) {
        GEMSdkCall.checkCurrentThread()

        val isoCodeOld = m_country?.getAddress()?.getField(TAddressField.ECountryCode)
        val isoCodeNew = country.getAddress()?.getField(TAddressField.ECountryCode)

        if (isoCodeOld != isoCodeNew) {
            m_country = country
            reset()
            search(m_country)
            refresh(0)
        }
    }

    fun hasState(): Boolean {
        val country = this.m_country ?: return false
        val list = GEMSdkCall.execute {
            return@execute GuidedAddressSearchService().getNextAddressDetailLevel(country)
        } ?: return false
        return list.isNotEmpty() && list[0] == TAddressDetailLevel.EAD_State.value
    }

    fun getCountryFlag(width: Int, height: Int): Bitmap? {
        GEMSdkCall.checkCurrentThread()
        val isoCode = m_country?.getAddress()?.getField(TAddressField.ECountryCode)
        if (isoCode?.isNotEmpty() == true) {
            val image = MapDetails().getCountryFlag(isoCode)
            return Utils.getImageAsBitmap(image, width, height)
        }

        return null
    }

    fun getHint(field: Int): String {
        when (EnumHelp.fromInt<TAddressField>(field)) {
            TAddressField.EState -> {
                return Utils.getUIString(StringIds.eStrState)
            }
            TAddressField.ECity -> {
                return Utils.getUIString(StringIds.eStrCity)
            }
            TAddressField.EStreetName -> {
                return Utils.getUIString(StringIds.eStrStreet)
            }
            TAddressField.EStreetNumber -> {
                return Utils.getUIString(StringIds.eStrNumberAbbv)
            }
            TAddressField.ECrossing1 -> {
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

        if (detailLevel == TAddressDetailLevel.EAD_State) {
            text =
                landmark?.getAddress()?.getField(TAddressField.EStateCode)
        } else if (detailLevel == TAddressDetailLevel.EAD_HouseNumber) {
            text = GEMSdkCall.execute { landmark?.getName() } ?: ""

            var pos: Int = text.indexOf(">")
            if (pos > 0) {
                text = text.substring(0, pos)
            }

            pos = text.indexOf("<")
            if (pos >= 0) {
                text = text.substring(pos + 1, text.length)
            }
        } else if (detailLevel == TAddressDetailLevel.EAD_Crossing && item.anywhere) {
            text = Utils.getUIString(StringIds.eStrTempAnywhere)
        } else {
            text = GEMSdkCall.execute { landmark?.getName() }
        }

        return text ?: ""
    }

    fun getItemDescription(index: Int): String {
        if (index > mItems.size || index < 0) return ""
        val item = mItems[index]
        val landmark = item.mLandmark
        val detailLevel = item.mAddressDetailLevel

        var description: String? = null

        if (detailLevel == TAddressDetailLevel.EAD_State) {
            description = GEMSdkCall.execute { landmark?.getName() }
        }

        return description ?: ""
    }

    fun getItemImage(index: Int, width: Int, height: Int): Bitmap? {
        if (index > mItems.size || index < 0) return null

        val item = mItems[index]
        val landmark = item.mLandmark
        if (item.mAddressDetailLevel == TAddressDetailLevel.EAD_City) {
            return GEMSdkCall.execute {
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
