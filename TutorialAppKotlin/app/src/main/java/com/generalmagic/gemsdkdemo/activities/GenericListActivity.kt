// Copyright (C) 2019-2020, General Magic B.V.
// All rights reserved.
//
// This software is confidential and proprietary information of General Magic
// ("Confidential Information"). You shall not disclose such Confidential
// Information and shall use it only in accordance with the terms of the
// license agreement you entered into with General Magic.

package com.generalmagic.gemsdkdemo.activities

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SearchView
import android.widget.TextView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.gemsdk.MapDetails
import com.generalmagic.gemsdk.TRgba
import com.generalmagic.gemsdk.TUnitSystem
import com.generalmagic.gemsdk.models.*
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdk.util.GemIcons
import com.generalmagic.gemsdkdemo.R
import com.generalmagic.gemsdkdemo.util.Util
import com.generalmagic.gemsdkdemo.util.UtilUITexts
import com.generalmagic.gemsdkdemo.util.Utils
import io.github.luizgrp.sectionedrecyclerviewadapter.Section
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionParameters
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionedRecyclerViewAdapter
import io.github.luizgrp.sectionedrecyclerviewadapter.utils.EmptyViewHolder
import kotlinx.android.synthetic.main.activity_list_view.*
import kotlinx.android.synthetic.main.filter_view.*

abstract class BaseListItem {}

open class SearchListItem : BaseListItem() {
	var mOnClick: (holder: SLIViewHolder) -> Unit = {}

	open fun getIcon(width: Int, height: Int): Bitmap? {
		return null
	}

	open fun getStatus(): String {
		return ""
	}

	open fun getStatusDescription(): String {
		return ""
	}

	open fun getText(): String {
		return ""
	}

	open fun getDescription(): String {
		return ""
	}
}

open class ListItemStatusImage : BaseListItem() {
	var mOnLongClick: (holder: LISIViewHolder) -> Boolean = { false }
	var mOnClick: (holder: LISIViewHolder) -> Unit = {}

	open fun getIcon(width: Int, height: Int): Bitmap? {
		return null
	}

	open fun getText(): String {
		return ""
	}

	open fun getDescription(): String {
		return ""
	}

	open fun getStatusIcon(width: Int, height: Int): Bitmap? {
		return null
	}

	open fun getStatusIconColor(): Int {
		return 0
	}
}

// -------------------------------------------------------------------------------------------------

open class GenericListActivity : BaseActivity() {
	private var statusIconSize: Int = 0
	private var iconSize: Int = 0

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_list_view)

		statusIconSize = resources.getDimension(R.dimen.statusIconSize).toInt()
		iconSize = resources.getDimension(R.dimen.listIconSize).toInt()

		val layoutManager = LinearLayoutManager(this)
		list_view.layoutManager = layoutManager

		val separator = DividerItemDecoration(applicationContext, layoutManager.orientation)
		list_view.addItemDecoration(separator)

		// set root view background (we used grouped style for list view)
		root_view.setBackgroundResource(R.color.list_view_bgnd_color)
		val lateralPadding = resources.getDimension(R.dimen.bigPadding).toInt()
		list_view.setPadding(lateralPadding, 0, lateralPadding, 0)

		filterView.visibility = View.GONE

		setSupportActionBar(toolbar)
		// no title
		supportActionBar?.title = ""

		// display back button
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
	}

	fun hideListView() {
		list_view?.adapter = SectionedRecyclerViewAdapter()
	}
}

open class SearchListActivity : GenericListActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		filterView.visibility = View.VISIBLE

		// set search field hint
//		searchInput.queryHint = "Search"

		// set focus on search bar and open keyboard
//		searchInput.requestFocus()

		searchInput.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
			override fun onQueryTextSubmit(query: String?): Boolean {
				searchInput.clearFocus()
				return true
			}

			override fun onQueryTextChange(newText: String?): Boolean {
				applyFilter(newText ?: "")
				return true
			}
		})
	}

	open fun applyFilter(filter: String) {}
}

// -----------------------------------------------------------------------------------------
/**ListItemStatusImageViewHolder*/
class ChapterLISIAdapter : SectionedRecyclerViewAdapter {
	var onStatusIconTapped: (chapterIndex: Int, item: BaseListItem, holder: LISIViewHolder) -> Unit =
		{ _: Int, _: BaseListItem, _: LISIViewHolder -> }

	constructor(dataChapters: ArrayList<ListItemStatusImage>) : super() {
		val wrap = ArrayList<ArrayList<ListItemStatusImage>>()
		wrap.add(dataChapters)
		init(wrap, false)
	}

	constructor(
		dataChapters: ArrayList<ArrayList<ListItemStatusImage>>,
		addHeaderRes: Boolean = true
	) : super() {
		init(dataChapters, addHeaderRes)
	}

	private fun init(
		dataChapters: ArrayList<ArrayList<ListItemStatusImage>>,
		addHeaderRes: Boolean
	) {
		val provideHolder = provideHolder@{ view: View ->
			return@provideHolder LISIViewHolder(view)
		}

		val builder = SectionParameters.builder()
		builder.itemResourceId(LISIViewHolder.resId)
		if (addHeaderRes)
			builder.headerResourceId(LISIViewHolder.headerResId)

		val sectionParams = builder.build()

		for (i in 0 until dataChapters.size) {
			val section = object : LISIViewHolder.Chapter<ListItemStatusImage>(
				dataChapters[i], provideHolder, sectionParams
			) {
				override fun onStatusIconTapped(item: BaseListItem, holder: LISIViewHolder) {
					onStatusIconTapped(i, item, holder)
				}
			}
			addSection(section)
		}
		notifyDataSetChanged()
	}
}

open class LISIViewHolder(val parent: View) : RecyclerView.ViewHolder(parent) {
	companion object {
		const val resId = R.layout.list_item_status_image
		const val headerResId = R.layout.list_chapter_header
	}

	val icon: ImageView = parent.findViewById(R.id.icon)
	val text: TextView = parent.findViewById(R.id.text)
	val description: TextView = parent.findViewById(R.id.description)
	val statusIcon: ImageView = parent.findViewById(R.id.status_icon)
	val statusProgress: ProgressBar = parent.findViewById(R.id.item_progress_bar)

	private val iconSize = parent.resources.getDimension(R.dimen.listIconSize).toInt()
	private val statusIconSize = parent.resources.getDimension(R.dimen.statusIconSize).toInt()

	open fun updateViews(it: ListItemStatusImage) {
		icon.setImageBitmap(it.getIcon(iconSize, iconSize))
		text.text = it.getText()
		description.text = it.getDescription()
		statusIcon.setImageBitmap(it.getStatusIcon(statusIconSize, statusIconSize))

		val uiColor = it.getStatusIconColor()
		if (uiColor != 0) {
			statusIcon.setColorFilter(uiColor)
		} else statusIcon.colorFilter = null

		statusProgress.visibility = View.GONE

		parent.setOnLongClickListener { _ ->
			it.mOnLongClick(this)
		}
		parent.setOnClickListener { _ ->
			it.mOnClick(this)
		}
	}

	open class Chapter<T : ListItemStatusImage>(
		val nItems: ArrayList<T>,
		val holderProvider: (View) -> RecyclerView.ViewHolder,
		params: SectionParameters
	) : Section(params) {
		open fun onStatusIconTapped(item: BaseListItem, holder: LISIViewHolder) {}

		override fun getContentItemsTotal(): Int {
			return nItems.size
		}

		override fun getItemViewHolder(view: View): RecyclerView.ViewHolder {
			return holderProvider(view)
		}

		override fun onBindItemViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			if (position < 0 || position >= nItems.size)
				return

			(holder as LISIViewHolder).let { lisiHolder ->
				holder.statusIcon.setOnClickListener {
					onStatusIconTapped(nItems[position], lisiHolder)
				}

				lisiHolder.updateViews(nItems[position])
			}
		}

		override fun getHeaderViewHolder(view: View?): RecyclerView.ViewHolder {
			return EmptyViewHolder(view)
		}
	}
}
// -------------------------------------------------------------------------------------------------
/** SearchListItem */
class SLIAdapter(data: ArrayList<SearchListItem>) : SectionedRecyclerViewAdapter() {
	init {
		val provideHolder = provideHolder@{ view: View ->
			return@provideHolder SLIViewHolder(view)
		}

		addSection(SLIViewHolder.Chapter(data, provideHolder))

		notifyDataSetChanged()
	}
}

open class SLIViewHolder(val parent: View) : RecyclerView.ViewHolder(parent) {
	companion object {
		const val resId = R.layout.search_list_item
	}

	val icon: ImageView = parent.findViewById(R.id.icon)
	val text: TextView = parent.findViewById(R.id.text)
	val description: TextView = parent.findViewById(R.id.description)
	val status: TextView = parent.findViewById(R.id.status_text)
	val statusDescription: TextView = parent.findViewById(R.id.status_description)

	private var iconSize: Int = parent.resources.getDimension(R.dimen.listIconSize).toInt()

	open fun updateViews(it: SearchListItem) {
		icon.setImageBitmap(it.getIcon(iconSize, iconSize))
		text.text = it.getText()
		description.text = it.getDescription()
		status.text = it.getStatus()
		statusDescription.text = it.getStatusDescription()

		parent.setOnClickListener { _ ->
			it.mOnClick(this)
		}
	}


	open class Chapter<T : SearchListItem>(
		private val nItems: ArrayList<T>,
		val holderProvider: (View) -> RecyclerView.ViewHolder
	) : Section(
		SectionParameters.builder().itemResourceId(resId).build()
	) {
		override fun getContentItemsTotal(): Int {
			return nItems.size
		}

		override fun getItemViewHolder(view: View): RecyclerView.ViewHolder {
			return holderProvider(view)
		}

		override fun onBindItemViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			if (position < 0 || position >= nItems.size)
				return

			(holder as SLIViewHolder).updateViews(nItems[position])
		}

		override fun getHeaderViewHolder(view: View?): RecyclerView.ViewHolder {
			return EmptyViewHolder(view)
		}
	}
}
// -------------------------------------------------------------------------------------------------

// -----------------------------------------------------------------------------------------
// -----------------------------------------------------------------------------------------
// ContentStoreItem
// -----------------------------------------------------------------------------------------

open class ContentStoreItemViewModel(val it: ContentStoreItem) : ListItemStatusImage() {
	override fun getIcon(width: Int, height: Int): Bitmap? {
		val countryCodes = it.getCountryCodes()

		var countryFlagImage: Image? = null
		if (countryCodes != null && countryCodes.size > 0) {
			val countryCode = countryCodes[0]
			if (countryCode.isNotEmpty()) {
				countryFlagImage = MapDetails().getCountryFlag(countryCode)
			}
		}

		return GEMSdkCall.execute {
			Util.createBitmap(countryFlagImage, width, height)
		}
	}

	override fun getStatusIcon(width: Int, height: Int): Bitmap? {
		return GEMSdkCall.execute {
			Util.getContentStoreStatusAsBitmap(it.getStatus(), width, height)
		}
	}

	override fun getText(): String {
		return it.getName() ?: ""
	}

	override fun getDescription(): String {
		return UtilUITexts.formatSizeAsText(it.getTotalSize())
	}

	override fun getStatusIconColor(): Int {
		return Util.getColor(
			when (it.getStatus()) {
				TContentStoreItemStatus.ECIS_Completed -> 0
				TContentStoreItemStatus.ECIS_DownloadQueued -> 0
				else -> {
					TRgba(0, 0, 255, 255).value()
				}
			}
		)
	}
}

// -----------------------------------------------------------------------------------------
// -----------------------------------------------------------------------------------------

open class LandmarkViewModel(val it: Landmark?, reference: Coordinates?) : SearchListItem() {
	private val dist: Pair<String, String>

	init {
		val coords = it?.getCoordinates()?.coordinates()
		val meters: Int = if (reference != null) coords?.getDistance(reference)?.toInt() ?: 0 else 0

		dist = Utils.getDistText(meters, TUnitSystem.EMetric, true)
	}

	override fun getIcon(width: Int, height: Int): Bitmap? {
		return GEMSdkCall.execute { Util.createBitmap(it?.getImage(), width, height) }
	}

	override fun getStatus(): String {
		return dist.first
	}

	override fun getStatusDescription(): String {
		return dist.second
	}

	override fun getText(): String {
		return it?.getName() ?: ""
	}

	override fun getDescription(): String {
		return UtilUITexts.pairFormatLandmarkDetails(it).second
	}
}

// -----------------------------------------------------------------------------------------
open class StyleItemViewModel(item: ContentStoreItem) : ContentStoreItemViewModel(item) {
	var m_checked = false

	override fun getIcon(width: Int, height: Int): Bitmap? {
		val m_text = it.getName() ?: ""
		var iconId = GemIcons.Other_UI.ShowMapStandard
		if ((m_text.indexOf("Cloud") >= 0) ||
			(m_text.indexOf("Temperature") >= 0) ||
			(m_text.indexOf("Wind") >= 0) ||
			(m_text.indexOf("Radar") >= 0) ||
			(m_text.indexOf("Humidity") >= 0) ||
			(m_text.indexOf("Pressure") >= 0)
		) {
			iconId = GemIcons.Other_UI.ShowWeather
		} else if (m_text.indexOf("Terrain") >= 0) {
			if (m_text.indexOf("Satellite") >= 0) {
				iconId = GemIcons.Other_UI.ShowMapTerrainAndSatellite
			} else {
				iconId = GemIcons.Other_UI.ShowMapTerrain
			}
		} else if (m_text.indexOf("Satellite") >= 0) {
			iconId = GemIcons.Other_UI.ShowMapTerrainAndSatellite
		}

		return GEMSdkCall.execute {
			Util.getImageIdAsBitmap(iconId.value, width, height)
		}
	}

	override fun getStatusIcon(width: Int, height: Int): Bitmap? {
		return GEMSdkCall.execute {
			if (m_checked)
				Util.getImageIdAsBitmap(
					GemIcons.Other_UI.Button_DownloadCheckmark.value, width, height
				)
			else
				Util.getContentStoreStatusAsBitmap(it.getStatus(), width, height)
		}
	}

	override fun getText(): String {
		return it.getName() ?: ""
	}

	override fun getStatusIconColor(): Int {
		return Util.getColor(
			when (it.getStatus()) {
				TContentStoreItemStatus.ECIS_Completed -> 0
				TContentStoreItemStatus.ECIS_DownloadQueued -> 0
				else -> {
					TRgba(0, 0, 255, 255).value()
				}
			}
		)
	}
}
// -----------------------------------------------------------------------------------------


