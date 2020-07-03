// Copyright (C) 2019-2020, General Magic B.V.
// All rights reserved.
//
// This software is confidential and proprietary information of General Magic
// ("Confidential Information"). You shall not disclose such Confidential
// Information and shall use it only in accordance with the terms of the
// license agreement you entered into with General Magic.

package com.generalmagic.gemsdkdemo.activities.searchaddress

import android.content.Intent
import android.graphics.Bitmap
import com.generalmagic.gemsdk.GuidedAddressSearchService
import com.generalmagic.gemsdk.MapDetails
import com.generalmagic.gemsdk.ProgressListener
import com.generalmagic.gemsdk.TAddressDetailLevel
import com.generalmagic.gemsdk.magicearth.StringIds
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.models.TAddressField
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMList
import com.generalmagic.gemsdkdemo.util.GEMApplication
import com.generalmagic.gemsdkdemo.util.Utils

// -------------------------------------------------------------------------------------------------

object GEMAddressSearchView {
	// ---------------------------------------------------------------------------------------------

//	enum class TAddressField(val value: Int) {
//		EAFCountry(0),
//		EAFState(1),
//		EAFCity(2),
//		EAFStreet(3),
//		EAFStreetNumber(4),
//		EAFIntersection(5);
//
//		companion object {
//			fun fromInt(value: Int) = values().first { it.value == value }
//		}
//	}

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
//		GEMSdkCall.execute { didCreateView(viewId) }
	}

	// ---------------------------------------------------------------------------------------------

	private fun unregisterActivity(viewId: Long) {
		addressSearchActivitiesMap.remove(viewId)
	}

	// ---------------------------------------------------------------------------------------------

	private fun open(viewId: Long) {
		GEMApplication.uiHandler.post()
		{
			val intent =
				Intent(GEMApplication.getApplicationContext(), SearchAddressActivity::class.java)
			intent.putExtra("viewId", viewId)
			GEMApplication.topActivity?.startActivity(intent)
		}
	}

	// ---------------------------------------------------------------------------------------------

	private fun close(viewId: Long) {
		GEMApplication.uiHandler.post {
			addressSearchActivitiesMap[viewId]?.finish()
		}
	}

	// ---------------------------------------------------------------------------------------------

	fun onViewClosed(viewId: Long) {
		unregisterActivity(viewId)

//		GEMSdkCall.execute {
//			didCloseView(viewId)
//		}
	}

	// ---------------------------------------------------------------------------------------------

	private fun showBusyIndicator(viewId: Long) {
		GEMApplication.uiHandler.post {
			addressSearchActivitiesMap[viewId]?.showProgress()
		}
	}

	// ---------------------------------------------------------------------------------------------

	private fun hideBusyIndicator(viewId: Long) {
		GEMApplication.uiHandler.post {
			addressSearchActivitiesMap[viewId]?.hideProgress()
		}
	}

	// ---------------------------------------------------------------------------------------------

	private fun refresh(viewId: Long) {
		GEMApplication.uiHandler.post {
			addressSearchActivitiesMap[viewId]?.refresh()
		}
	}

	// ---------------------------------------------------------------------------------------------

	private fun refreshSearchResultsList(viewId: Long) {
		GEMApplication.uiHandler.post {
			addressSearchActivitiesMap[viewId]?.refreshSearchResultsList()
		}
	}

	// ---------------------------------------------------------------------------------------------

	private fun selectIntersectionField(viewId: Long) {
		GEMApplication.uiHandler.post {
			addressSearchActivitiesMap[viewId]?.selectIntersectionField()
		}
	}

	// ---------------------------------------------------------------------------------------------

	private fun setFilter(viewId: Long, field: Int, filter: String) {
		GEMApplication.uiHandler.post {
			addressSearchActivitiesMap[viewId]?.setField(field, filter)
		}
	}

	// ---------------------------------------------------------------------------------------------

	fun isEnabled(field: TAddressField): Boolean {
		return fieldEnabledMap[field]?: false
	}

	// ---------------------------------------------------------------------------------------------

	fun setEnabledState(field: TAddressField, value: Boolean) {
		fieldEnabledMap[field] = value
	}

	// ---------------------------------------------------------------------------------------------

//	external fun didCreateView(viewId: Long)

	// ---------------------------------------------------------------------------------------------

//	external fun didCloseView(viewId: Long)

	// ---------------------------------------------------------------------------------------------

	external fun didTapItem(viewId: Long, index: Int)

	// ---------------------------------------------------------------------------------------------

	fun didTapCountryFlag(viewId: Long) {

	}

	// ---------------------------------------------------------------------------------------------

	external fun didChangeFilter(viewId: Long, field: Int, text: String)

	// ---------------------------------------------------------------------------------------------

	external fun didTapSearchButton(viewId: Long)

	// ---------------------------------------------------------------------------------------------

	fun getTitle(viewId: Long): String {
		return Utils.getUIString(StringIds.eStrAddress)
	}

	// ---------------------------------------------------------------------------------------------

	data class AddressSearchModelItem(
		var m_landmark: Landmark,
		var m_addressDetailLevel: TAddressDetailLevel,
		var anywhere: Boolean
	)

	var m_detailLevel = TAddressDetailLevel.EAD_NoDetail
	var m_field = TAddressField.ECountry

	var m_landmarks = ArrayList<Landmark>()
	var m_filter = ""
	var m_lastSuccessfulFilter = ""
	var m_items = ArrayList<AddressSearchModelItem>()

	fun search(
		landmark: Landmark?, filter: String = "",
		detailLevel: TAddressDetailLevel = TAddressDetailLevel.EAD_NoDetail
	) {
		landmark ?: return
		m_filter = filter

		if (detailLevel != TAddressDetailLevel.EAD_NoDetail) {
			m_detailLevel = detailLevel
		} else {
			GuidedAddressSearchService().getNextAddressDetailLevel(landmark)
				?.let { nextDetailLevel ->
					if (nextDetailLevel.size > 0) {
						m_detailLevel = TAddressDetailLevel.fromInt(nextDetailLevel[0])
					}
				}
		}

		if (m_detailLevel != TAddressDetailLevel.EAD_NoDetail) {
			val results = GEMList(Landmark::class)

			val listener = object : ProgressListener() {
				override fun notifyComplete(reasonInt: Int, hint: String) {
					val reason = GEMError.fromInt(reasonInt)
					val bNoError = reason == GEMError.KNoError || reason == GEMError.KReducedResult

					if (bNoError) {
						m_landmarks = results.asArrayList()
						if (m_landmarks.size > 0 || m_detailLevel == TAddressDetailLevel.EAD_Crossing) {
							m_lastSuccessfulFilter = m_filter
							m_items.clear()

							if (m_detailLevel == TAddressDetailLevel.EAD_Crossing) {
								m_items.add(AddressSearchModelItem(m_street, m_detailLevel, true))
							}

							for (item in m_landmarks) {
								m_items.add(AddressSearchModelItem(item, m_detailLevel, false))
							}
						} else if (m_lastSuccessfulFilter.isNotEmpty()) {
							setFilter(0, m_field.value, m_lastSuccessfulFilter)
							hideBusyIndicator(0)
						} else if (m_field == TAddressField.EStreetNumber) {
//							selectIntersectionsField()
						}
					} else if (reason != GEMError.KCancel) {
						m_items.clear()
					}

					hideBusyIndicator(0)
				}
			}
			GuidedAddressSearchService().search(
				results, landmark, m_filter, m_detailLevel, listener
			)

			showBusyIndicator(0)
		}
	}

	var m_country = Landmark()
	var m_street = Landmark()
	fun onCountrySelected(country: Landmark) {
		val isoCodeOld = m_country.getAddress()
			?.getField(com.generalmagic.gemsdk.models.TAddressField.ECountryCode)
		val isoCodeNew = country.getAddress()
			?.getField(com.generalmagic.gemsdk.models.TAddressField.ECountryCode)

		if (isoCodeOld != isoCodeNew) {
			m_country = country
			search(m_country)
		}
	}

	fun hasState(viewId: Long): Boolean {
		val list = GuidedAddressSearchService().getNextAddressDetailLevel(m_country) ?: return false
		return list.isNotEmpty() && list[0] == TAddressDetailLevel.EAD_State.value
	}

	// ---------------------------------------------------------------------------------------------

	fun getCountryFlag(viewId: Long, width: Int, height: Int): Bitmap? {
		val isoCode = m_country.getAddress()
			?.getField(com.generalmagic.gemsdk.models.TAddressField.ECountryCode)
		if (isoCode?.isNotEmpty() == true) {
			val image = MapDetails().getCountryFlag(isoCode)
			return Utils.getImageAsBitmap(image, width, height)
		}

		return null
	}

	// ---------------------------------------------------------------------------------------------

	fun getHint(viewId: Long, field: Int): String {
		when (TAddressField.fromInt(field)) {
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

	fun getItemsCount(viewId: Long): Int {
		return m_items.size
	}

	// ---------------------------------------------------------------------------------------------

	fun getItemText(viewId: Long, index: Int): String {
		if (index > m_items.size || index < 0) return ""
		val item = m_items[index]
		val landmark = item.m_landmark
		val detailLevel = item.m_addressDetailLevel

		var m_text: String?

		if (detailLevel == TAddressDetailLevel.EAD_State) {
			m_text = landmark.getAddress()
				?.getField(com.generalmagic.gemsdk.models.TAddressField.EStateCode)
		} else if (detailLevel == TAddressDetailLevel.EAD_HouseNumber) {
			m_text = landmark.getName() ?: ""

			var pos: Int = m_text.indexOf(">")
			if (pos > 0)
				m_text = m_text.substring(0, pos)

			pos = m_text.indexOf("<")
			if (pos >= 0)
				m_text = m_text.substring(pos + 1, m_text.length - pos - 1)
		} else if (detailLevel == TAddressDetailLevel.EAD_Crossing && item.anywhere) {
			m_text = Utils.getUIString(StringIds.eStrTempAnywhere)
		} else {
			m_text = landmark.getName()
		}

		return m_text ?: ""
	}

	// ---------------------------------------------------------------------------------------------

	fun getItemDescription(viewId: Long, index: Int): String {
		if (index > m_items.size || index < 0) return ""
		val item = m_items[index]
		val landmark = item.m_landmark
		val detailLevel = item.m_addressDetailLevel

		var m_description: String? = null

		if (detailLevel == TAddressDetailLevel.EAD_State) {
			m_description = landmark.getName()
		}

		return m_description ?: ""
	}

	// ---------------------------------------------------------------------------------------------

	fun getItemImage(viewId: Long, index: Int, width: Int, height: Int): Bitmap? {
		if (index > m_items.size || index < 0) return null

		val item = m_items[index]
		val landmark = item.m_landmark
		if (item.m_addressDetailLevel == TAddressDetailLevel.EAD_City) {
			return Utils.getImageAsBitmap(landmark.getImage(), width, height)
		}

		return null
	}

	// ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
