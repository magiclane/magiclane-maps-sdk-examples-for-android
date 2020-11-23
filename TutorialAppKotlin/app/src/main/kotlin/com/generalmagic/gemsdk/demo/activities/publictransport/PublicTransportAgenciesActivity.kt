/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.activities.publictransport

// -------------------------------------------------------------------------------------------------

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.activities.WebActivity
import com.generalmagic.gemsdk.demo.app.BaseActivity
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.util.Util
import com.generalmagic.gemsdk.util.GEMSdkCall
import io.github.luizgrp.sectionedrecyclerviewadapter.Section
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionParameters
import io.github.luizgrp.sectionedrecyclerviewadapter.SectionedRecyclerViewAdapter
import kotlinx.android.synthetic.main.agencies_activity.*

// -------------------------------------------------------------------------------------------------

class PublicTransportAgenciesActivity : BaseActivity() {
    // ---------------------------------------------------------------------------------------------

    val iconSize = GEMApplication.appResources().getDimension(R.dimen.listIconSize).toInt()
    private var agenciesListAdapter: AgenciesListAdapter? = null
    var urlBmp: Bitmap? = null
    var fareBmp: Bitmap? = null
    var phoneBmp: Bitmap? = null

    // ---------------------------------------------------------------------------------------------

    data class AgencyListItem(
        var name: String = "",
        var url: String = "",
        var fareUrl: String = "",
        var phone: String = ""
    )

    // ---------------------------------------------------------------------------------------------

    inner class AgenciesListAdapter : SectionedRecyclerViewAdapter() {
        // -----------------------------------------------------------------------------------------

        private var chapters: Array<AgencyListItem> = Array(0) { AgencyListItem() }

        // -----------------------------------------------------------------------------------------

        inner class Chapter(private val index: Int, private val nItems: Int) : Section(
            SectionParameters.builder().itemResourceId(R.layout.list_item_status_image)
                .headerResourceId(R.layout.chapter_header).build()
        ) {

            inner class AgencyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
                val icon: ImageView = view.findViewById(R.id.icon)
                val text: TextView = view.findViewById(R.id.text)
                val parentView = view
            }

            inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
                val text: TextView = view.findViewById(R.id.header_text)
            }

            override fun getContentItemsTotal(): Int {
                return nItems
            }

            override fun getItemViewHolder(view: View): RecyclerView.ViewHolder {
                return AgencyViewHolder(view)
            }

            override fun onBindItemViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
                if ((index >= 0) && (index < chapters.size)) {
                    val itemHolder: AgencyViewHolder = holder as AgencyViewHolder

                    chapters[index].let {
                        itemHolder.apply {
                            if (it.url.isNotEmpty() && position == 0) {
                                text.text = it.url
                                icon.setImageBitmap(urlBmp)
                                parentView.setBackgroundResource(R.drawable.list_first_item_background)

                                itemHolder.parentView.setOnClickListener { _ ->
                                    val intent = Intent(
                                        this@PublicTransportAgenciesActivity,
                                        WebActivity::class.java
                                    )
                                    intent.putExtra("url", it.url)
                                    startActivity(intent)
                                }

                                return
                            }

                            if (it.fareUrl.isNotEmpty() && position == 1) {
                                text.text = it.fareUrl
                                icon.setImageBitmap(fareBmp)
                                parentView.setBackgroundResource(R.drawable.list_first_item_background)

                                itemHolder.parentView.setOnClickListener { _ ->
                                    val intent = Intent(
                                        this@PublicTransportAgenciesActivity,
                                        WebActivity::class.java
                                    )
                                    intent.putExtra("url", it.fareUrl)
                                    startActivity(intent)
                                }

                                return
                            }

                            if (it.phone.isNotEmpty() && position > 0) {
                                text.text = it.phone
                                icon.setImageBitmap(phoneBmp)
                                parentView.setBackgroundResource(R.drawable.list_first_item_background)

                                itemHolder.parentView.setOnClickListener { _ ->
                                    val intent = Intent(Intent.ACTION_DIAL)
                                    intent.data = Uri.parse("tel:${it.phone}")
                                    startActivity(intent)
                                }

                                return
                            }
                        }
                    }
                }
            }

            override fun getHeaderViewHolder(view: View): RecyclerView.ViewHolder {
                return HeaderViewHolder(view)
            }

            override fun onBindHeaderViewHolder(holder: RecyclerView.ViewHolder?) {
                val itemHolder: HeaderViewHolder = holder as HeaderViewHolder

                chapters[index].let {
                    itemHolder.apply {
                        text.text = it.name
                    }
                }
            }
        }

        // -----------------------------------------------------------------------------------------

        init {
            load()
        }

        // -----------------------------------------------------------------------------------------

        private fun load() {
            GEMSdkCall.execute {
                var data = GEMPublicTransportRouteDescriptionView.getAgencyURLImage()
                urlBmp = Util.createBitmap(data, iconSize, iconSize)

                data = GEMPublicTransportRouteDescriptionView.getAgencyFareImage()
                fareBmp = Util.createBitmap(data, iconSize, iconSize)

                data = GEMPublicTransportRouteDescriptionView.getAgencyPhoneImage()
                phoneBmp = Util.createBitmap(data, iconSize, iconSize)

                val chaptersCount = GEMPublicTransportRouteDescriptionView.getAgenciesCount()
                if (chaptersCount > 0) {
                    chapters = Array(chaptersCount) { AgencyListItem() }
                    for (i in 0 until chaptersCount) {
                        chapters[i].run {
                            var items = 0
                            name = GEMPublicTransportRouteDescriptionView.getAgencyName(i)

                            url = GEMPublicTransportRouteDescriptionView.getAgencyURL(i)
                            if (url.isNotEmpty()) {
                                items++
                            }

                            fareUrl = GEMPublicTransportRouteDescriptionView.getAgencyFareURL(i)
                            if (fareUrl.isNotEmpty()) {
                                items++
                            }

                            phone = GEMPublicTransportRouteDescriptionView.getAgencyPhone(i)
                            if (phone.isNotEmpty()) {
                                items++
                            }

                            addSection(Chapter(i, items))
                        }
                    }
                }
            }
        }
        // -----------------------------------------------------------------------------------------
    }

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.agencies_activity)

        setSupportActionBar(toolbar)

        // display back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // set layout manager
        mapsList.layoutManager = LinearLayoutManager(this)

        agenciesListAdapter = AgenciesListAdapter()
        mapsList.adapter = agenciesListAdapter

        // set root view background (we used grouped style for list view)
        root_view.setBackgroundResource(R.color.list_view_bgnd_color)
        val lateralPadding =
            GEMApplication.appResources().getDimension(R.dimen.bigPadding).toInt()
        mapsList.setPadding(lateralPadding, 0, lateralPadding, 0)

        val title = GEMSdkCall.execute { GEMPublicTransportRouteDescriptionView.getAgencyText() }

        supportActionBar?.title = title
    }
}
