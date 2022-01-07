/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.settings

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.adapters.GEMGenericAdapter
import com.generalmagic.sdk.examples.demo.adapters.sectionedlist.GenericSectionedAdapter
import com.generalmagic.sdk.examples.demo.app.BaseActivity
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.app.Tutorials
import com.generalmagic.sdk.util.SdkCall

class SettingsActivity : BaseActivity(), ISettingsView {

    private var viewId: Long = 0
    private var settingsListAdapter: SettingsListAdapter? = null
    
    lateinit var toolbar: Toolbar
    lateinit var root_view: ConstraintLayout
    lateinit var settingsList: RecyclerView
    lateinit var settingsToolbarTitle: TextView

    data class SettingsListItem(
        var itemType: Int = 0,
        var text: String = "",
        var description: String = "",
        var isSwitchChecked: Boolean = false,
        var seekBarMinInt: Int = 0,
        var seekBarProgressInt: Int = 0,
        var seekBarMaxInt: Int = 0,
        var seekBarMinIntText: String = "",
        var seekBarProgressIntText: String = "",
        var seekBarMaxIntText: String = "",
        var seekBarMinDouble: Double = 0.0,
        var seekBarProgressDouble: Double = 0.0,
        var seekBarMaxDouble: Double = 0.0,
        var seekBarMinDoubleText: String = "",
        var seekBarProgressDoubleText: String = "",
        var seekBarMaxDoubleText: String = "",
        var optionsListCount: Int = 0,
        var optionsList: MutableList<String> = mutableListOf(),
        var optionsListSelectedItemIndex: Int = 0,
        var isEnabled: Boolean = true,
        var chapterIndex: Int = 0
    )

    inner class SettingsListChapter(
        index: Int,
        itemArrayList: MutableList<SettingsListItem>,
        var chapterTitle: String
    ) : GenericSectionedAdapter.SectionModel(index, itemArrayList) {
        constructor() : this(0, mutableListOf(), "")
    }

    inner class SettingsListAdapter(
        context: Context,
        recyclerViewType: SectionedListType
    ) : GenericSectionedAdapter<SettingsListChapter>(context, recyclerViewType) {

        init {
            load()
        }

        inner class ChapterItemsAdapter(itemsList: MutableList<SettingsListItem?>) :
            GEMGenericAdapter<SettingsListItem>(itemsList) {

            inner class SimpleItemViewHolder(view: View) :
                GEMGenericViewHolder<SettingsListItem>(view) {

                val text: TextView = view.findViewById(R.id.settingsText)
                val description: TextView = view.findViewById(R.id.settingsDescription)
                private val optionsListSelected: TextView =
                    view.findViewById(R.id.settingsOptionListSelected)

                override fun bind(data: SettingsListItem?, position: Int) {
                    if (data != null) {
                        text.text = data.text
                        description.text = data.description
                        optionsListSelected.text =
                            data.optionsList[data.optionsListSelectedItemIndex]

                        if (!data.isEnabled) {
                            text.setTextColor(
                                ContextCompat.getColor(
                                    this@SettingsActivity,
                                    R.color.map_styles_list_view_bgnd_color
                                )
                            )
                            description.setTextColor(
                                ContextCompat.getColor(
                                    this@SettingsActivity,
                                    R.color.map_styles_list_view_bgnd_color
                                )
                            )
                            optionsListSelected.setTextColor(
                                ContextCompat.getColor(
                                    this@SettingsActivity,
                                    R.color.map_styles_list_view_bgnd_color
                                )
                            )
                        } else {
                            text.setTextColor(Color.BLACK)
                            description.setTextColor(Color.BLACK)
                            optionsListSelected.setTextColor(Color.BLACK)
                        }

                        if (data.description.isEmpty()) {
                            description.visibility = View.GONE
                        } else {
                            description.visibility = View.VISIBLE
                        }

                        if (position == (listItems.size - 1)) {
                            itemView.setBackgroundResource(R.drawable.list_item_background)
                        } else {
                            itemView.setBackgroundResource(R.drawable.list_first_item_background)
                        }

                        itemView.setOnClickListener {
                            when (data.itemType) {
                                TSettingItemType.EText.ordinal -> {
                                    GEMSettingsView.didTapItem(viewId, data.chapterIndex, position)
                                }
                                TSettingItemType.EOptionsList.ordinal -> {
                                    val builder = AlertDialog.Builder(
                                        this@SettingsActivity,
                                        R.style.MaterialThemeDialog
                                    )
                                    builder.setTitle(data.text)
                                    builder.setSingleChoiceItems(
                                        data.optionsList.toTypedArray(),
                                        data.optionsListSelectedItemIndex
                                    ) { dialog, which ->
                                        SdkCall.execute {
                                            GEMSettingsView.didTapOptionsListItem(
                                                viewId,
                                                data.chapterIndex,
                                                position,
                                                which
                                            )
                                            data.optionsListSelectedItemIndex =
                                                GEMSettingsView.getOptionsListSelectedItemIndex(
                                                    viewId,
                                                    data.chapterIndex,
                                                    position
                                                )
                                        }
                                        optionsListSelected.text = data.optionsList[which]
                                        dialog.dismiss()
                                    }

                                    val dialog = builder.create()
                                    dialog.show()
                                }
                            }
                        }
                    }
                }
            }

            inner class SwitchItemViewHolder(view: View) :
                GEMGenericViewHolder<SettingsListItem>(view) {

                val text: TextView = view.findViewById(R.id.settingsText)
                private val switch: SwitchCompat = view.findViewById(R.id.settingsSwitch)
                val description: TextView = view.findViewById(R.id.settingsDescription)

                override fun bind(data: SettingsListItem?, position: Int) {
                    if (data != null) {
                        text.text = data.text
                        description.text = data.description
                        switch.isChecked = data.isSwitchChecked

                        switch.isEnabled = data.isEnabled
                        if (!data.isEnabled) {
                            text.setTextColor(
                                ContextCompat.getColor(
                                    this@SettingsActivity,
                                    R.color.map_styles_list_view_bgnd_color
                                )
                            )
                        } else {
                            text.setTextColor(Color.BLACK)
                        }

                        switch.setOnCheckedChangeListener { _, isChecked ->
                            SdkCall.execute {
                                GEMSettingsView.didChooseNewBoolValue(
                                    viewId,
                                    data.chapterIndex,
                                    position,
                                    isChecked
                                )
                            }
                            data.isSwitchChecked = isChecked
                        }

                        if (data.description.isEmpty()) {
                            description.visibility = View.GONE
                        } else {
                            description.visibility = View.VISIBLE
                        }

                        if (position == (listItems.size - 1)) {
                            itemView.setBackgroundResource(R.drawable.list_item_background)
                        } else {
                            itemView.setBackgroundResource(R.drawable.list_first_item_background)
                        }
                    }
                }
            }

            inner class SeekBarItemViewHolder(view: View) :
                GEMGenericViewHolder<SettingsListItem>(view) {

                val text: TextView = view.findViewById(R.id.settingsText)
                private val seekBar: AppCompatSeekBar = view.findViewById(R.id.settingsSeekBar)
                private val minValueText: TextView = view.findViewById(R.id.minValueText)
                private val currentValueText: TextView = view.findViewById(R.id.currentValueText)
                private val maxValueText: TextView = view.findViewById(R.id.maxValueText)

                override fun bind(data: SettingsListItem?, position: Int) {
                    if (data != null) {
                        text.text = data.text

                        var stepsNumber = 0
                        when (data.itemType) {

                            TSettingItemType.EInt.ordinal -> {
                                minValueText.text = data.seekBarMinIntText
                                currentValueText.text = data.seekBarProgressIntText
                                maxValueText.text = data.seekBarMaxIntText

                                stepsNumber = data.seekBarMaxInt - data.seekBarMinInt
                                seekBar.max = stepsNumber
                                val progressValue =
                                    ((data.seekBarProgressInt.toFloat() - data.seekBarMinInt.toFloat()) / (data.seekBarMaxInt.toFloat() - data.seekBarMinInt.toFloat())) * stepsNumber
                                seekBar.progress = progressValue.toInt()
                            }

                            TSettingItemType.EDouble.ordinal -> {
                                minValueText.text = data.seekBarMinDoubleText
                                currentValueText.text = data.seekBarProgressDoubleText
                                maxValueText.text = data.seekBarMaxDoubleText

                                stepsNumber =
                                    (data.seekBarMaxDouble - data.seekBarMinDouble).toInt()
                                seekBar.max = stepsNumber
                                val progressValue =
                                    ((data.seekBarProgressDouble - data.seekBarMinDouble) / (data.seekBarMaxDouble - data.seekBarMinDouble)) * stepsNumber
                                seekBar.progress = progressValue.toInt()
                            }
                        }

                        seekBar.isEnabled = data.isEnabled
                        if (!data.isEnabled) {
                            text.setTextColor(
                                ContextCompat.getColor(
                                    this@SettingsActivity,
                                    R.color.map_styles_list_view_bgnd_color
                                )
                            )
                        } else {
                            text.setTextColor(Color.BLACK)
                        }

                        seekBar.setOnSeekBarChangeListener(object :
                            SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(
                                seekBar: SeekBar?,
                                progress: Int,
                                fromUser: Boolean
                            ) {
                                var text = ""
                                SdkCall.execute {
                                    when (data.itemType) {
                                        TSettingItemType.EInt.ordinal -> {
                                            val progressValue =
                                                (data.seekBarMinInt + progress.toFloat()) * (1.0f / stepsNumber) * (data.seekBarMaxInt - data.seekBarMinInt)
                                            GEMSettingsView.didChooseNewIntValue(
                                                viewId,
                                                data.chapterIndex,
                                                position,
                                                progressValue.toInt()
                                            )
                                        }
                                        TSettingItemType.EDouble.ordinal -> {
                                            val progressValue =
                                                (data.seekBarMinDouble + progress.toFloat()) * (1.0f / stepsNumber) * (data.seekBarMaxDouble - data.seekBarMinDouble)
                                            GEMSettingsView.didChooseNewDoubleValue(
                                                viewId,
                                                data.chapterIndex,
                                                position,
                                                progressValue
                                            )
                                        }
                                    }
                                    text = GEMSettingsView.getIntTextValue(
                                        viewId,
                                        data.chapterIndex,
                                        position
                                    )
                                }

                                currentValueText.text = text
                            }

                            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                            }

                            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                            }

                        })

                        if (position == (listItems.size - 1)) {
                            itemView.setBackgroundResource(R.drawable.list_item_background)
                        } else {
                            itemView.setBackgroundResource(R.drawable.list_first_item_background)
                        }
                    }
                }
            }

            override fun getLayoutId(position: Int): Int {
                return if ((position >= 0) && (position < listItems.size)) {
                    when (listItems[position]?.itemType) {
                        TSettingItemType.EOptionsList.ordinal,
                        TSettingItemType.EText.ordinal -> R.layout.settings_list_item_simple
                        TSettingItemType.EBoolean.ordinal -> R.layout.settings_list_item_switch
                        TSettingItemType.EInt.ordinal,
                        TSettingItemType.EDouble.ordinal -> R.layout.settings_list_item_seekbar
                        else -> R.layout.settings_list_item_simple
                    }
                } else {
                    R.layout.settings_list_item_simple
                }
            }

            override fun getViewHolder(
                view: View,
                viewType: Int
            ): GEMGenericViewHolder<SettingsListItem> {
                return when (viewType) {
                    R.layout.settings_list_item_simple -> SimpleItemViewHolder(view)
                    R.layout.settings_list_item_switch -> SwitchItemViewHolder(view)
                    R.layout.settings_list_item_seekbar -> SeekBarItemViewHolder(view)
                    else -> SimpleItemViewHolder(view)
                }
            }
        }

        inner class ChapterViewHolder(view: View) :
            GenericSectionedViewHolder<SettingsListChapter>(view) {

            override fun bind(data: SettingsListChapter?, position: Int) {
                if (data != null) {
                    text.text = data.chapterTitle

                    val sections = data.itemArrayList.filterIsInstance<SettingsListItem?>().takeIf {
                        it.size == data.itemArrayList.size
                    }

                    val adapter = ChapterItemsAdapter(sections as MutableList<SettingsListItem?>)
                    setAdapterToSectionRecyclerView(data.sectionIndex, adapter)
                }
            }
        }

        override fun getViewHolder(
            view: View,
            viewType: Int
        ): GEMGenericViewHolder<SettingsListChapter?> {
            return ChapterViewHolder(view)
        }

        fun refresh() {
            load()
            notifyDataSetChanged()
        }

        fun refreshItemState(chapter: Int, index: Int, enabled: Boolean) {
            if ((chapter >= 0) && (chapter < listItems.size) &&
                (index >= 0) && (index < listItems[chapter]?.itemArrayList?.size!!)
            ) {
                (listItems[chapter]?.itemArrayList?.get(index) as SettingsListItem).isEnabled =
                    enabled
                notifyItemChanged(chapter, index)
            }
        }

        private fun load() {
            SdkCall.execute {
                val chaptersCount = GEMSettingsView.getChaptersCount(viewId)
                if (chaptersCount > 0) {
                    listItems = MutableList(chaptersCount) { SettingsListChapter() }
                    for (i in 0 until chaptersCount) {
                        val itemsCount = GEMSettingsView.getItemsCount(viewId, i)
                        listItems[i]?.apply {
                            itemArrayList = MutableList(itemsCount) { SettingsListItem() }
                            sectionIndex = i
                            chapterTitle = GEMSettingsView.getChapterText(viewId, i)
                        }

                        for (j in 0 until itemsCount) {
                            listItems[i]?.apply {
                                (this.itemArrayList[j] as SettingsListItem).apply {
                                    itemType = GEMSettingsView.getItemType(viewId, i, j)
                                    text = GEMSettingsView.getItemText(viewId, i, j)
                                    description = GEMSettingsView.getItemDescription(viewId, i, j)
                                    isSwitchChecked = GEMSettingsView.getBoolValue(viewId, i, j)
                                    seekBarMinInt = GEMSettingsView.getIntMinValue(viewId, i, j)
                                    seekBarProgressInt = GEMSettingsView.getIntValue(viewId, i, j)
                                    seekBarMaxInt = GEMSettingsView.getIntMaxValue(viewId, i, j)
                                    seekBarMinIntText =
                                        GEMSettingsView.getIntMinTextValue(viewId, i, j)
                                    seekBarProgressIntText =
                                        GEMSettingsView.getIntTextValue(viewId, i, j)
                                    seekBarMaxIntText =
                                        GEMSettingsView.getIntMaxTextValue(viewId, i, j)
                                    seekBarMinDouble =
                                        GEMSettingsView.getDoubleMinValue(viewId, i, j)
                                    seekBarProgressDouble =
                                        GEMSettingsView.getDoubleValue(viewId, i, j)
                                    seekBarMaxDouble =
                                        GEMSettingsView.getDoubleMaxValue(viewId, i, j)
                                    seekBarMinDoubleText =
                                        GEMSettingsView.getDoubleMinTextValue(viewId, i, j)
                                    seekBarProgressDoubleText =
                                        GEMSettingsView.getDoubleTextValue(viewId, i, j)
                                    seekBarMaxDoubleText =
                                        GEMSettingsView.getDoubleMaxTextValue(viewId, i, j)
                                    optionsListCount =
                                        GEMSettingsView.getOptionsListCount(viewId, i, j)
                                    optionsList = MutableList(optionsListCount) { "" }
                                    for (k in 0 until optionsListCount) {
                                        optionsList[k] =
                                            GEMSettingsView.getOptionsListText(viewId, i, j, k)
                                    }
                                    optionsListSelectedItemIndex =
                                        GEMSettingsView.getOptionsListSelectedItemIndex(
                                            viewId,
                                            i,
                                            j
                                        )
                                    chapterIndex = i
                                    isEnabled = GEMSettingsView.isItemEnabled(viewId, i, j)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.settings_activity)
        
        toolbar = findViewById(R.id.toolbar)
        root_view = findViewById(R.id.root_view)
        settingsList = findViewById(R.id.settingsList)
        settingsToolbarTitle = findViewById(R.id.settingsToolbarTitle)

        setSupportActionBar(toolbar)

        // display back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewId = intent.getLongExtra("viewId", 0)

        GEMSettingsView.registerActivity(viewId, this)

        // set layout manager
        settingsList.layoutManager = LinearLayoutManager(this)

        settingsListAdapter =
            SettingsListAdapter(this, GenericSectionedAdapter.SectionedListType.LINEAR_VERTICAL)

        settingsList.adapter = settingsListAdapter

        // set root view background (we used grouped style for list view)
        root_view.setBackgroundResource(R.color.list_view_bgnd_color)
        val lateralPadding =
            GEMApplication.appResources().getDimension(R.dimen.listItemPadding).toInt()
        settingsList.setPadding(lateralPadding, 0, lateralPadding, 0)

        val title = GEMSettingsView.getTitle(viewId)
        if (title.isNotEmpty()) {
            // show title
            settingsToolbarTitle.visibility = View.VISIBLE

            // set title
            settingsToolbarTitle.text = title
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GEMSettingsView.onViewClosed(viewId)

        Tutorials.openHelloWorldTutorial()
    }

    override fun doBackPressed(): Boolean {
        finish()
        return true
    }

    override fun refreshItemState(chapter: Int, index: Int, enabled: Boolean) {
        settingsListAdapter?.refreshItemState(chapter, index, enabled)
    }

    override fun refresh() {
        super.refresh()
        settingsListAdapter?.refresh()
    }
}
