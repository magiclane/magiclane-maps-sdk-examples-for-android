/*
 * Copyright (C) 2019-2020, General Magic B.V.
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
import com.generalmagic.apihelper.EnumHelp
import com.generalmagic.gemsdk.GuidedAddressSearchService
import com.generalmagic.gemsdk.MapDetails
import com.generalmagic.gemsdk.ProgressListener
import com.generalmagic.gemsdk.TAddressDetailLevel
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.util.Utils
import com.generalmagic.gemsdk.extensions.StringIds
import com.generalmagic.gemsdk.models.ImageDatabase
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.models.TAddressField
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMList
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdk.util.GemIcons

// -------------------------------------------------------------------------------------------------

object GEMAddressSearchView {
    // ---------------------------------------------------------------------------------------------

// 	enum class TAddressField(val value: Int) {
// 		EAFCountry(0),
// 		EAFState(1),
// 		EAFCity(2),
// 		EAFStreet(3),
// 		EAFStreetNumber(4),
// 		EAFIntersection(5);
//
// 		companion object {
// 			fun fromInt(value: Int) = values().first { it.value == value }
// 		}
// 	}

    // ---------------------------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------------------------

    fun registerActivity(viewId: Long, searchAddressActivity: SearchAddressActivity) {
        addressSearchActivitiesMap[viewId] = searchAddressActivity
// 		GEMSdkCall.execute { didCreateView(viewId) }
    }

    // ---------------------------------------------------------------------------------------------

    private fun unregisterActivity(viewId: Long) {
        addressSearchActivitiesMap.remove(viewId)
    }

    // ---------------------------------------------------------------------------------------------

    private fun open(viewId: Long) {
        GEMApplication.postOnMain {
            val intent =
                Intent(GEMApplication.applicationContext(), SearchAddressActivity::class.java)
            intent.putExtra("viewId", viewId)
            GEMApplication.topActivity()?.startActivity(intent)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun close(viewId: Long) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.finish()
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun onViewClosed(viewId: Long) {
        unregisterActivity(viewId)

// 		GEMSdkCall.execute {
// 			didCloseView(viewId)
// 		}
    }

    // ---------------------------------------------------------------------------------------------

    private fun showBusyIndicator(viewId: Long) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.showProgress()
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun hideBusyIndicator(viewId: Long) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.hideProgress()
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun refresh(viewId: Long) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.refresh()
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun refreshSearchResultsList(viewId: Long) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.refreshSearchResultsList()
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun selectIntersectionField(viewId: Long) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.selectIntersectionField()
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setFilter(viewId: Long, field: Int, filter: String) {
        GEMApplication.postOnMain {
            addressSearchActivitiesMap[viewId]?.setField(field, filter)
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun isEnabled(field: TAddressField): Boolean {
        return fieldEnabledMap[field] ?: false
    }

    // ---------------------------------------------------------------------------------------------

    fun setEnabledState(field: TAddressField, value: Boolean) {
        fieldEnabledMap[field] = value
    }

    // ---------------------------------------------------------------------------------------------

// 	external fun didCreateView(viewId: Long)

    // ---------------------------------------------------------------------------------------------

// 	external fun didCloseView(viewId: Long)

    // ---------------------------------------------------------------------------------------------

    external fun didTapItem(viewId: Long, index: Int)

    // ---------------------------------------------------------------------------------------------

    fun didTapCountryFlag() {
        // TODO:
    }

    // ---------------------------------------------------------------------------------------------

    fun didChangeFilter(iField: Int, filter: String) {
        filter.trim()
        val field = EnumHelp.fromInt<TAddressField>(iField)

        if ((m_filter != filter) || (m_field != field)) {
            if (m_field != field) {
                m_lastSuccessfulFilter = ""
            }

            m_filter = filter
            m_field = field

            m_listener?.let {
                GEMSdkCall.execute { GuidedAddressSearchService().cancelRequest(it) }
            }

            search(getParentLandmark(field), filter, getDetailLevel(field))
        }
    }

    fun getParentLandmark(field: TAddressField): Landmark? {
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

    fun getDetailLevel(field: TAddressField): TAddressDetailLevel {
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

    // ---------------------------------------------------------------------------------------------

    fun didTapSearchButton() {
        var landmark: Landmark? = null
        when (m_field) {
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
                highlightLandmarkOnMap(landmark)
            } else if (m_items.size > 0) {
                highlightLandmarkOnMap(m_items[0].m_landmark)
            }
        }
    }

    private fun highlightLandmarkOnMap(landmark: Landmark?) {
        GEMSdkCall.checkCurrentThread()

        landmark ?: return
        val mainMapView = GEMApplication.getMainMapView() ?: return

        ImageDatabase().getImageById(GemIcons.Other_UI.Search_Results_Pin.value)?.let {
            landmark.setImage(it)
        }

//        val value = if (landmark.getContourGeograficArea() == null) {
//            THighlightOptions.EHO_ShowLandmark
//        } else {
//            val kHighlightContour = TRgba(255, 98, 0, 255).value()
//            (THighlightOptions.EHO_ShowLandmark.value or THighlightOptions.EHO_ShowContour.value)) or THighlightOptions.EHO_Overlap, kHighlightContour, kHighlightContour
//        }

        mainMapView.activateHighlightLandmarks(arrayListOf(landmark))
//        finish()
    }

    // ---------------------------------------------------------------------------------------------

    fun getTitle(): String {
        return Utils.getUIString(StringIds.eStrAddress)
    }

    // ---------------------------------------------------------------------------------------------

    data class AddressSearchModelItem(
        var m_landmark: Landmark?,
        var m_addressDetailLevel: TAddressDetailLevel,
        var anywhere: Boolean
    )

    var m_detailLevel = TAddressDetailLevel.EAD_NoDetail
    var m_field = TAddressField.ECountry

    var m_landmarks = ArrayList<Landmark>()
    var m_filter = ""
    var m_lastSuccessfulFilter = ""
    var m_items = ArrayList<AddressSearchModelItem>()
    var m_listener: ProgressListener? = null

    fun search(
        landmark: Landmark?,
        filter: String = "",
        detailLevel: TAddressDetailLevel = TAddressDetailLevel.EAD_NoDetail
    ) {
        landmark ?: return
        m_filter = filter

        if (detailLevel != TAddressDetailLevel.EAD_NoDetail) {
            m_detailLevel = detailLevel
        } else {
            GEMSdkCall.execute {
                GuidedAddressSearchService().getNextAddressDetailLevel(landmark)?.let {
                    val nextDetailLevel = it
                    if (nextDetailLevel.size > 0) {
                        m_detailLevel = EnumHelp.fromInt(nextDetailLevel[0])
                    }
                }
            }
        }

        if (m_detailLevel != TAddressDetailLevel.EAD_NoDetail) {
            GEMSdkCall.execute {
                val results = GEMList(Landmark::class)

                val listener = object : ProgressListener() {
                    override fun notifyComplete(reason: Int, hint: String) {
                        val gemReason = GEMError.fromInt(reason)
                        val bNoError =
                            gemReason == GEMError.KNoError || gemReason == GEMError.KReducedResult

                        if (bNoError) {
                            m_landmarks = results.asArrayList()
                            if (m_landmarks.size > 0 || m_detailLevel == TAddressDetailLevel.EAD_Crossing) {
                                m_lastSuccessfulFilter = m_filter
                                m_items.clear()

                                if (m_detailLevel == TAddressDetailLevel.EAD_Crossing) {
                                    m_items.add(
                                        AddressSearchModelItem(
                                            m_street,
                                            m_detailLevel,
                                            true
                                        )
                                    )
                                }

                                for (item in m_landmarks) {
                                    m_items.add(AddressSearchModelItem(item, m_detailLevel, false))
                                }
                            } else if (m_lastSuccessfulFilter.isNotEmpty()) {
                                setFilter(0, m_field.value, m_lastSuccessfulFilter)
                                hideBusyIndicator(0)
                            } else if (m_field == TAddressField.EStreetNumber) {
// 							selectIntersectionsField()
                            }
                        } else if (gemReason != GEMError.KCancel) {
                            m_items.clear()
                        }

                        hideBusyIndicator(0)
                    }
                }
                m_listener = listener

                GuidedAddressSearchService().search(
                    results,
                    landmark,
                    m_filter,
                    m_detailLevel,
                    listener
                )
            }

            showBusyIndicator(0)
        }
    }

    var m_state: Landmark? = null
    var m_country: Landmark? = null
    var m_street: Landmark? = null
    var m_city: Landmark? = null

    init {
        GEMSdkCall.execute {
            m_state = Landmark()
            m_country = Landmark()
            m_street = Landmark()
            m_city = Landmark()
        }
    }

    fun onCountrySelected(country: Landmark) {
        GEMSdkCall.checkCurrentThread()

        val isoCodeOld = m_country?.getAddress()?.getField(TAddressField.ECountryCode)
        val isoCodeNew = country.getAddress()?.getField(TAddressField.ECountryCode)

        if (isoCodeOld != isoCodeNew) {
            m_country = country
            search(m_country)
        }
    }

    fun hasState(): Boolean {
        val m_country = this.m_country ?: return false
        val list = GEMSdkCall.execute {
            return@execute GuidedAddressSearchService().getNextAddressDetailLevel(m_country)
        } ?: return false
        return list.isNotEmpty() && list[0] == TAddressDetailLevel.EAD_State.value
    }

    // ---------------------------------------------------------------------------------------------

    fun getCountryFlag(width: Int, height: Int): Bitmap? {
        GEMSdkCall.checkCurrentThread()
        val isoCode = m_country?.getAddress()?.getField(TAddressField.ECountryCode)
        if (isoCode?.isNotEmpty() == true) {
            val image = MapDetails().getCountryFlag(isoCode)
            return Utils.getImageAsBitmap(image, width, height)
        }

        return null
    }

    // ---------------------------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------------------------

    fun getItemsCount(): Int {
        return m_items.size
    }

    // ---------------------------------------------------------------------------------------------

    fun getItemText(index: Int): String {
        if (index > m_items.size || index < 0) return ""
        val item = m_items[index]
        val landmark = item.m_landmark
        val detailLevel = item.m_addressDetailLevel

        var m_text: String?

        if (detailLevel == TAddressDetailLevel.EAD_State) {
            m_text =
                GEMSdkCall.execute { landmark?.getAddress() }?.getField(TAddressField.EStateCode)
        } else if (detailLevel == TAddressDetailLevel.EAD_HouseNumber) {
            m_text = GEMSdkCall.execute { landmark?.getName() } ?: ""

            var pos: Int = m_text.indexOf(">")
            if (pos > 0) {
                m_text = m_text.substring(0, pos)
            }

            pos = m_text.indexOf("<")
            if (pos >= 0) {
                m_text = m_text.substring(pos + 1, m_text.length - pos - 1)
            }
        } else if (detailLevel == TAddressDetailLevel.EAD_Crossing && item.anywhere) {
            m_text = Utils.getUIString(StringIds.eStrTempAnywhere)
        } else {
            m_text = GEMSdkCall.execute { landmark?.getName() }
        }

        return m_text ?: ""
    }

    // ---------------------------------------------------------------------------------------------

    fun getItemDescription(index: Int): String {
        if (index > m_items.size || index < 0) return ""
        val item = m_items[index]
        val landmark = item.m_landmark
        val detailLevel = item.m_addressDetailLevel

        var m_description: String? = null

        if (detailLevel == TAddressDetailLevel.EAD_State) {
            m_description = GEMSdkCall.execute { landmark?.getName() }
        }

        return m_description ?: ""
    }

    // ---------------------------------------------------------------------------------------------

    fun getItemImage(index: Int, width: Int, height: Int): Bitmap? {
        if (index > m_items.size || index < 0) return null

        val item = m_items[index]
        val landmark = item.m_landmark
        if (item.m_addressDetailLevel == TAddressDetailLevel.EAD_City) {
            return GEMSdkCall.execute {
                return@execute Utils.getImageAsBitmap(landmark?.getImage(), width, height)
            }
        }

        return null
    }

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
