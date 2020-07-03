package com.generalmagic.gemsdkdemo.activities.searchaddress

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
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdkdemo.activities.SLIAdapter
import com.generalmagic.gemsdkdemo.activities.SearchListActivity
import com.generalmagic.gemsdkdemo.activities.SearchListItem
import com.generalmagic.gemsdkdemo.activities.searchaddress.GEMAddressSearchView.onCountrySelected
import com.generalmagic.gemsdkdemo.util.Utils
import kotlinx.android.synthetic.main.activity_list_view.*

class CountriesSearchActivity : SearchListActivity() {
	var results = GEMList(Landmark::class)
	var m_filter = ""

	var m_items = ArrayList<CountryModelItem>()

	val listener = object : ProgressListener() {
		override fun notifyComplete(reason: Int, hint: String) {
			val gemError = GEMError.fromInt(reason)
			if (gemError != GEMError.KCancel) {
				m_items.clear()
			}

			val m_landmarks = results.asArrayList()

			if (gemError == GEMError.KNoError) {
				for (landmark in m_landmarks) {
					m_items.add(CountryModelItem(landmark))
				}
			}

			if (gemError != GEMError.KCancel) {
				hideBusyIndicator()
				refresh()

				if (gemError != GEMError.KNoError) {
					showErrorMessage(gemError)
				}
			}
		}
	}

	override fun applyFilter(filter: String) {
		filter.trim()

		if (m_filter != filter) {
			m_filter = filter
			cancel()
			search(filter)
		}
	}

	fun search(filter: String = "") {
		m_filter = filter

		results = GEMList(Landmark::class)
		GEMSdkCall.execute {
			GuidedAddressSearchService().search(
				results, Landmark(), m_filter, TAddressDetailLevel.EAD_Country, listener
			)
		}

		showProgress()
	}

	fun cancel() {
		GEMSdkCall.execute { GuidedAddressSearchService().cancelRequest(listener) }
	}

	fun didTapItem(item: CountryModelItem) {
		onCountrySelected(item.m_landmark)
		finish()
	}

	override fun refresh() {
		val result = ArrayList<SearchListItem>()
		for (item in m_items) {
			item.mOnClick = { didTapItem(item) }
			result.add(item)
		}

		val adapter = SLIAdapter(result)
		list_view.adapter = adapter
	}

	fun getFilterHint(): String {
		return Utils.getUIString(StringIds.eStrSearch)
	}

	class CountryModelItem(val m_landmark: Landmark) : SearchListItem() {
		private val m_text: String

		init {
			m_text = m_landmark.getName() ?: ""
		}

		override fun getIcon(width: Int, height: Int): Bitmap? {
			val isoCode = m_landmark.getAddress()?.getField(TAddressField.ECountryCode)
			if (isoCode?.isNotEmpty() == true) {
				val image = MapDetails().getCountryFlag(isoCode)
				return Utils.getImageAsBitmap(image, width, height)
			}

			return null
		}

		override fun getText(): String {
			return m_text
		}
	}
}
