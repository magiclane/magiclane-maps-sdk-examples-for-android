/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.sdk.content.ContentStoreItem
import com.generalmagic.sdk.content.EContentStoreItemStatus
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.activities.history.Trip
import com.generalmagic.sdk.examples.demo.activities.history.TripsHistory
import com.generalmagic.sdk.examples.demo.app.BaseActivity
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.util.Util
import com.generalmagic.sdk.examples.demo.util.UtilUITexts
import com.generalmagic.sdk.examples.demo.util.Utils
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.ERouteTransportMode
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkIcons
import com.generalmagic.sdk.util.SdkUtil.getDistText
import io.github.luizgrp.sectionedrecyclerviewadapter.Section
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionParameters
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionedRecyclerViewAdapter
import io.github.luizgrp.sectionedrecyclerviewadapter.utils.EmptyViewHolder

abstract class BaseListItem

open class SearchListItem : BaseListItem() {
    var mOnLongClick: (holder: SLIViewHolder) -> Boolean = { false }
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

    open fun getId(): Long {
        return 0L
    }

    open fun deleteContent(): Boolean {
        return true
    }

    open fun canDeleteContent(): Boolean {
        return false
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

open class StylesListItem : BaseListItem() {
    var mOnClick: (holder: StylesViewHolder) -> Unit = {}
    var progressVisible: Boolean = false

    open fun getIcon(width: Int, height: Int): Bitmap? {
        return null
    }

    open fun getText(): String {
        return ""
    }

    open fun getStatusIcon(width: Int, height: Int): Bitmap? {
        return null
    }

    open fun getStatusIconColor(): Int {
        return 0
    }

    open fun getImagePreview(): Bitmap? {
        return null
    }

    open fun canDeleteContent(): Boolean {
        return true
    }

    open fun isSelectedStyle(): Boolean {
        return true
    }
}

open class GenericListActivity : BaseActivity() {
    private var statusIconSize: Int = 0
    private var iconSize: Int = 0

    lateinit var listView: RecyclerView
    lateinit var rootView: ConstraintLayout
    lateinit var filterView: ConstraintLayout
    lateinit var searchInput: SearchView
    lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_view)

        progressBar = findViewById(R.id.progressBar)
        listView = findViewById(R.id.list_view)
        rootView = findViewById(R.id.root_view)
        filterView = findViewById(R.id.filterView)
        toolbar = findViewById(R.id.toolbar)
        searchInput = findViewById(R.id.searchInput)

        statusIconSize = resources.getDimension(R.dimen.statusIconSize).toInt()
        iconSize = resources.getDimension(R.dimen.listIconSize).toInt()

        val layoutManager = LinearLayoutManager(this)
        listView.layoutManager = layoutManager

        val separator = DividerItemDecoration(applicationContext, layoutManager.orientation)
        listView.addItemDecoration(separator)

        // set root view background (we used grouped style for list view)
        rootView.setBackgroundResource(R.color.list_view_bgnd_color)
        val lateralPadding = resources.getDimension(R.dimen.bigPadding).toInt()
        listView.setPadding(lateralPadding, 0, lateralPadding, 0)

        filterView.visibility = View.GONE

        setSupportActionBar(toolbar)
        // no title
        supportActionBar?.title = ""

        // display back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    fun hideListView() {
        listView.adapter = SectionedRecyclerViewAdapter()
    }
}

open class StylesListActivity : GenericListActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        listView.layoutManager = GridLayoutManager(
            this,
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 2 else 1
        )

        val separator = listView.getItemDecorationAt(0)
        listView.removeItemDecoration(separator)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        listView.layoutManager = GridLayoutManager(
            this,
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) 2 else 1
        )
        super.onConfigurationChanged(newConfig)
    }
}

open class MapsListActivity : GenericListActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        filterView.visibility = View.VISIBLE

        // set search field hint
        searchInput.queryHint = "Search"

        // set focus on search bar and open keyboard
        searchInput.requestFocus()

        searchInput.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchInput.clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    applyFilter(newText ?: "")
                    return true
                }
            }
        )

        listView.setBackgroundColor(ContextCompat.getColor(this, R.color.maps_view_bgnd_color))
    }

    open fun applyFilter(filter: String) {}
}

open class SearchListActivity : GenericListActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        filterView.visibility = View.VISIBLE

        // set search field hint
        searchInput.queryHint = "Search"

        // set focus on search bar and open keyboard
        searchInput.requestFocus()

        searchInput.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchInput.clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    applyFilter(newText ?: "")
                    return true
                }
            }
        )
    }

    open fun applyFilter(filter: String) {}
}

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
        if (addHeaderRes) {
            builder.headerResourceId(LISIViewHolder.headerResId)
        }

        val sectionParams = builder.build()

        for (i in 0 until dataChapters.size) {
            val section = object : LISIViewHolder.Chapter<ListItemStatusImage>(
                dataChapters[i],
                provideHolder,
                sectionParams
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
        private val nItems: ArrayList<T>,
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
            if (position < 0 || position >= nItems.size) {
                return
            }

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

class ChapterStylesAdapter : SectionedRecyclerViewAdapter {
    var onStatusIconTapped: (chapterIndex: Int, item: BaseListItem, holder: StylesViewHolder) -> Unit =
        { _: Int, _: BaseListItem, _: StylesViewHolder -> }

    var onDeleteIconTapped: (chapterIndex: Int, item: BaseListItem, holder: StylesViewHolder) -> Unit =
        { _: Int, _: BaseListItem, _: StylesViewHolder -> }

    constructor(dataChapters: ArrayList<StylesListItem>) : super() {
        val wrap = ArrayList<ArrayList<StylesListItem>>()
        wrap.add(dataChapters)
        init(wrap, false)
    }

    @Suppress("unused")
    constructor(
        dataChapters: ArrayList<ArrayList<StylesListItem>>,
        addHeaderRes: Boolean = true
    ) : super() {
        init(dataChapters, addHeaderRes)
    }

    private fun init(
        dataChapters: ArrayList<ArrayList<StylesListItem>>,
        addHeaderRes: Boolean
    ) {
        val provideHolder = provideHolder@{ view: View ->
            return@provideHolder StylesViewHolder(view)
        }

        val builder = SectionParameters.builder()
        builder.itemResourceId(StylesViewHolder.resId)
        if (addHeaderRes) {
            builder.headerResourceId(StylesViewHolder.headerResId)
        }

        val sectionParams = builder.build()

        for (i in 0 until dataChapters.size) {
            val section = object : StylesViewHolder.Chapter<StylesListItem>(
                dataChapters[i],
                provideHolder,
                sectionParams
            ) {
                override fun onStatusIconTapped(item: BaseListItem, holder: StylesViewHolder) {
                    onStatusIconTapped(i, item, holder)
                }

                override fun onDeleteIconTapped(item: BaseListItem, holder: StylesViewHolder) {
                    onDeleteIconTapped(i, item, holder)
                }
            }
            addSection(section)
        }
        notifyDataSetChanged()
    }
}

open class StylesViewHolder(val parent: View) : RecyclerView.ViewHolder(parent) {
    companion object {
        const val resId = R.layout.list_item_preview_status_image
        const val headerResId = R.layout.list_chapter_header
    }

    private val previewImage: ImageView = parent.findViewById(R.id.preview_image)
    val text: TextView = parent.findViewById(R.id.text)
    val statusIcon: ImageView = parent.findViewById(R.id.status_icon)
    val statusProgress: ProgressBar = parent.findViewById(R.id.item_progress_bar)
    val deleteIcon: ImageView = parent.findViewById(R.id.delete_icon)

    private val statusIconSize = parent.resources.getDimension(R.dimen.statusIconSize).toInt()

    open fun updateViews(it: StylesListItem) {
        val previewBitmap = it.getImagePreview()
        if (previewBitmap != null) {
            val emptyBitmap =
                Bitmap.createBitmap(previewBitmap.width, previewBitmap.height, previewBitmap.config)
            if (!previewBitmap.sameAs(emptyBitmap)) {
                previewImage.setImageBitmap(previewBitmap)
            }
        }

        text.text = it.getText()

        val statusBmp = it.getStatusIcon(statusIconSize, statusIconSize)
        if (statusBmp != null) {
            statusIcon.visibility = View.VISIBLE
            statusIcon.setImageBitmap(statusBmp)
        } else {
            statusIcon.visibility = View.GONE
        }

        when (it.canDeleteContent()) {
            true -> deleteIcon.visibility = View.VISIBLE
            false -> deleteIcon.visibility = View.GONE
        }

        if (it.isSelectedStyle()) {
            deleteIcon.visibility = View.GONE
        }

        val uiColor = it.getStatusIconColor()
        if (uiColor != 0) {
            statusIcon.setColorFilter(uiColor)
        } else statusIcon.colorFilter = null

        statusProgress.visibility = View.GONE

        parent.setOnClickListener { _ ->
            it.mOnClick(this)
        }
    }

    open class Chapter<T : StylesListItem>(
        val nItems: ArrayList<T>,
        val holderProvider: (View) -> RecyclerView.ViewHolder,
        params: SectionParameters
    ) : Section(params) {
        open fun onStatusIconTapped(item: BaseListItem, holder: StylesViewHolder) {}

        open fun onDeleteIconTapped(item: BaseListItem, holder: StylesViewHolder) {}

        override fun getContentItemsTotal(): Int {
            return nItems.size
        }

        override fun getItemViewHolder(view: View): RecyclerView.ViewHolder {
            return holderProvider(view)
        }

        override fun onBindItemViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (position < 0 || position >= nItems.size) {
                return
            }

            (holder as StylesViewHolder).let { stylesHolder ->
                holder.statusIcon.setOnClickListener {
                    onStatusIconTapped(nItems[position], stylesHolder)
                }

                holder.deleteIcon.setOnClickListener {
                    onDeleteIconTapped(nItems[position], stylesHolder)
                }

                stylesHolder.updateViews(nItems[position])
            }
        }

        override fun getHeaderViewHolder(view: View?): RecyclerView.ViewHolder {
            return EmptyViewHolder(view)
        }
    }
}

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

/** HistoryListItem */
class HLIAdapter(data: ArrayList<SearchListItem>) : SectionedRecyclerViewAdapter() {
    init {
        val provideHolder = provideHolder@{ view: View ->
            return@provideHolder SLIViewHolder(view, true)
        }

        addSection(SLIViewHolder.Chapter(data, provideHolder))

        notifyDataSetChanged()
    }
}

open class SLIViewHolder(val parent: View, private val isHistory: Boolean = false) :
    RecyclerView.ViewHolder(parent) {
    companion object {
        const val resId = R.layout.search_list_item
    }

    val icon: ImageView = parent.findViewById(R.id.icon)
    val text: TextView = parent.findViewById(R.id.text)
    val description: TextView = parent.findViewById(R.id.description)
    private val status: TextView = parent.findViewById(R.id.status_text)
    private val statusDescription: TextView = parent.findViewById(R.id.status_description)

    private var iconSize: Int = parent.resources.getDimension(R.dimen.listIconSize).toInt()

    open fun updateViews(it: SearchListItem) {
        icon.setImageBitmap(it.getIcon(iconSize, iconSize))
        text.text = it.getText()

        val descriptionText = it.getDescription()
        if (descriptionText.isEmpty()) {
            description.visibility = View.GONE
        } else {
            description.text = descriptionText
        }

        val statusText = it.getStatus()
        if (statusText.isEmpty() || isHistory) {
            status.visibility = View.GONE
        } else {
            status.text = statusText
        }

        val statusDescriptionText = it.getStatusDescription()
        if (statusDescriptionText.isEmpty() || isHistory) {
            statusDescription.visibility = View.GONE
        } else {
            statusDescription.text = statusDescriptionText
        }

        parent.setOnClickListener { _ ->
            it.mOnClick(this)
        }

        if (it.canDeleteContent()) {
            parent.setOnLongClickListener { _ ->
                val menu = PopupMenu(GEMApplication.applicationContext(), itemView)
                menu.menu.add("Delete")
                menu.show()

                menu.setOnMenuItemClickListener { _ ->
                    it.mOnLongClick(this)

                    true
                }

                true
            }
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
            if (position < 0 || position >= nItems.size) {
                return
            }

            (holder as SLIViewHolder).updateViews(nItems[position])
        }

        override fun getHeaderViewHolder(view: View?): RecyclerView.ViewHolder {
            return EmptyViewHolder(view)
        }
    }
}

// ContentStoreItem

open class ContentStoreItemViewModel(val it: ContentStoreItem) : ListItemStatusImage() {
    override fun getIcon(width: Int, height: Int): Bitmap? = SdkCall.execute {
        val countryCodes = it.getCountryCodes() ?: return@execute null

        var countryFlagImage: Image? = null
        if (countryCodes.size > 0) {
            val countryCode = countryCodes[0]
            if (countryCode.isNotEmpty()) {
                countryFlagImage = MapDetails().getCountryFlag(countryCode)
            }
        }

        return@execute Util.createBitmap(countryFlagImage, width, height)
    }

    override fun getStatusIcon(width: Int, height: Int): Bitmap? = SdkCall.execute {
        return@execute Util.getContentStoreStatusAsBitmap(it.getStatus(), width, height)
    }

    override fun getText(): String = SdkCall.execute {
        return@execute it.getName()
    } ?: ""

    override fun getDescription(): String =
        UtilUITexts.formatSizeAsText(SdkCall.execute { it.getTotalSize() } ?: 0)

    override fun getStatusIconColor(): Int = SdkCall.execute {
        return@execute Util.getColor(
            when (it.getStatus()) {
                EContentStoreItemStatus.Completed -> 0
                EContentStoreItemStatus.DownloadQueued -> 0
                else -> {
                    Rgba(0, 0, 255, 255).value()
                }
            }
        )
    } ?: 0
}

open class StylesContentStoreItemViewModel(val it: ContentStoreItem) : StylesListItem() {
    override fun getIcon(width: Int, height: Int): Bitmap? = SdkCall.execute {
        val countryCodes = it.getCountryCodes()

        var countryFlagImage: Image? = null
        if (countryCodes != null && countryCodes.size > 0) {
            val countryCode = countryCodes[0]
            if (countryCode.isNotEmpty()) {
                countryFlagImage = MapDetails().getCountryFlag(countryCode)
            }
        }

        return@execute Util.createBitmap(countryFlagImage, width, height)
    }

    override fun getStatusIcon(width: Int, height: Int): Bitmap? = SdkCall.execute {
        return@execute Util.getContentStoreStatusAsBitmap(it.getStatus(), width, height)
    }


    override fun getText(): String = SdkCall.execute {
        return@execute it.getName()
    } ?: ""


    override fun getStatusIconColor(): Int = SdkCall.execute {
        return@execute Util.getColor(
            when (it.getStatus()) {
                EContentStoreItemStatus.Completed -> 0
                EContentStoreItemStatus.DownloadQueued -> 0
                else -> {
                    Rgba(0, 0, 255, 255).value()
                }
            }
        )
    } ?: 0

    override fun getImagePreview(): Bitmap? = SdkCall.execute {
        val previewImage = it.getImagePreview()
        val height = previewImage?.getSize()?.height() ?: 0
        val width = previewImage?.getSize()?.width() ?: 0
        return@execute Util.createBitmap(previewImage, width, height)
    }


    override fun canDeleteContent(): Boolean = SdkCall.execute {
        return@execute it.canDeleteContent()
    } ?: false


    override fun isSelectedStyle(): Boolean = SdkCall.execute {
        return@execute it.getId() == GEMApplication.getMainMapView()?.preferences()?.getMapStyleId()
    } ?: false
}

open class LandmarkViewModel(val it: Landmark?, reference: Coordinates?) : SearchListItem() {
    private val dist: Pair<String, String>

    init {
        val meters = SdkCall.execute {
            reference ?: return@execute 0
            return@execute it?.getCoordinates()?.getDistance(reference)?.toInt()
        } ?: 0

        dist = SdkCall.execute { getDistText(meters, EUnitSystem.Metric, true) } ?: Pair("", "")
    }

    override fun getIcon(width: Int, height: Int): Bitmap? = SdkCall.execute {
        if (it != null) {
            return@execute Utils.getLandmarkIcon(it, width)
        }
        return@execute null
    }

    override fun getStatus(): String {
        return dist.first
    }

    override fun getStatusDescription(): String {
        return dist.second
    }

    override fun getText(): String = SdkCall.execute {
        return@execute it?.getName()
    } ?: ""


    override fun getDescription(): String = SdkCall.execute {
        return@execute UtilUITexts.pairFormatLandmarkDetails(it).second
    } ?: ""

    override fun getId(): Long = SdkCall.execute {
        return@execute it?.getTimestamp()?.asInt()
    } ?: 0

}

class HistoryLandmarkViewModel(it: Landmark?, reference: Coordinates?) :
    LandmarkViewModel(it, reference) {
    override fun deleteContent(): Boolean {
        if (it != null) {
            return GEMApplication.removeLandmarkFromHistory(it)
        }
        return false
    }

    override fun canDeleteContent(): Boolean {
        return true
    }
}

class FavouritesLandmarkViewModel(it: Landmark?, reference: Coordinates?) :
    LandmarkViewModel(it, reference) {
    override fun deleteContent(): Boolean {
        if (it != null) {
            return GEMApplication.removeFavourite(it)
        }
        return false
    }

    override fun canDeleteContent(): Boolean {
        return true
    }
}

open class TripViewModel(val it: Trip, private val tripIndex: Int) : SearchListItem() {
    override fun getIcon(width: Int, height: Int): Bitmap? = SdkCall.execute {
        return@execute it.mPreferences?.getTransportMode().let { transportMode ->
            return@let when (transportMode) {
                ERouteTransportMode.Car -> {
                    Utils.getImageAsBitmap(SdkIcons.Other_UI.DriveTo_v2.value, width, height)
                }

                ERouteTransportMode.Pedestrian -> {
                    Utils.getImageAsBitmap(SdkIcons.Other_UI.WalkTo.value, width, height)
                }

                ERouteTransportMode.Bicycle -> {
                    Utils.getImageAsBitmap(SdkIcons.Other_UI.BikeTo.value, width, height)
                }

                ERouteTransportMode.Public -> {
                    Utils.getImageAsBitmap(SdkIcons.Other_UI.PublicTransportTo.value, width, height)
                }

                else -> {
                    null
                }
            }
        }
    }

    override fun getId(): Long {
        return it.mTimeStamp
    }

    override fun getText(): String {
        val waypoints = it.mWaypoints
        if (waypoints != null) {
            return TripsHistory.getDefaultTripName(waypoints, it.mIsFromAToB, false).second
        }

        return ""
    }

    override fun deleteContent(): Boolean {
        return GEMApplication.mTripsHistory?.removeTrip(tripIndex) ?: false
    }

    override fun canDeleteContent(): Boolean {
        return true
    }
}

open class StyleItemViewModel(item: ContentStoreItem) : StylesContentStoreItemViewModel(item) {
    var mChecked = false

    override fun getIcon(width: Int, height: Int): Bitmap? = SdkCall.execute {
        val mText = it.getName() ?: ""
        var iconId = SdkIcons.Other_UI.ShowMapStandard
        if ((mText.indexOf("Cloud") >= 0) ||
            (mText.indexOf("Temperature") >= 0) ||
            (mText.indexOf("Wind") >= 0) ||
            (mText.indexOf("Radar") >= 0) ||
            (mText.indexOf("Humidity") >= 0) ||
            (mText.indexOf("Pressure") >= 0)
        ) {
            iconId = SdkIcons.Other_UI.ShowWeather
        } else if (mText.indexOf("Terrain") >= 0) {
            iconId = if (mText.indexOf("Satellite") >= 0) {
                SdkIcons.Other_UI.ShowMapTerrainAndSatellite
            } else {
                SdkIcons.Other_UI.ShowMapTerrain
            }
        } else if (mText.indexOf("Satellite") >= 0) {
            iconId = SdkIcons.Other_UI.ShowMapTerrainAndSatellite
        }

        return@execute Util.getImageIdAsBitmap(iconId.value, width, height)
    }

    override fun getStatusIcon(width: Int, height: Int): Bitmap? = SdkCall.execute {
        return@execute if (mChecked) {
            Util.getImageIdAsBitmap(
                SdkIcons.Other_UI.Button_DownloadCheckmark.value, width, height
            )
        } else {
            Util.getContentStoreStatusAsBitmap(it.getStatus(), width, height)
        }
    }

    override fun getText(): String = SdkCall.execute {
        return@execute it.getName()
    } ?: ""

    override fun getStatusIconColor(): Int = SdkCall.execute {
        return@execute Util.getColor(
            when (it.getStatus()) {
                EContentStoreItemStatus.Completed -> 0
                EContentStoreItemStatus.DownloadQueued -> 0
                else -> {
                    Rgba(0, 0, 255, 255).value()
                }
            }
        )
    } ?: 0
}
