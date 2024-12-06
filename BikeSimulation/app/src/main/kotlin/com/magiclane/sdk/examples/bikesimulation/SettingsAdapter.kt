// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.bikesimulation

// -------------------------------------------------------------------------------------------------
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import com.magiclane.sdk.examples.bikesimulation.R

// -------------------------------------------------------------------------------------------------

class SettingsAdapter : ListAdapter<SettingsItem, RecyclerView.ViewHolder>(settingsDiffUtil) {
    
    // -------------------------------------------------------------------------------------------------
    
    companion object {
        val settingsDiffUtil = object : DiffUtil.ItemCallback<SettingsItem>() {
            override fun areItemsTheSame(oldItem: SettingsItem, newItem: SettingsItem): Boolean = oldItem.title == newItem.title

            override fun areContentsTheSame(oldItem: SettingsItem, newItem: SettingsItem): Boolean = false
        }
    }
    
    // -------------------------------------------------------------------------------------------------

    enum class ESettingsItemType {
        SWITCH,
        SLIDER
    }
    
    // -------------------------------------------------------------------------------------------------
    
    class SwitchItemView(view: View) : RecyclerView.ViewHolder(view) {
        private val titleTxt = view.findViewById<MaterialTextView>(R.id.setting_item_text)
        private val switch = view.findViewById<MaterialSwitch>(R.id.setting_item_switch)
        private var mCallback: ((Boolean) -> Unit)? = null
        init {
            switch.setOnCheckedChangeListener { _, isChecked ->
                if (adapterPosition == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
                mCallback?.invoke(isChecked)
            }
        }

        fun bind(item: SettingsSwitchItem) {
            mCallback = item.callback
            titleTxt.text = item.title
            switch.isChecked = item.itIs
        }
    }

    // -------------------------------------------------------------------------------------------------
    
    @SuppressLint("DefaultLocale")
    class SliderItemView(view: View) : RecyclerView.ViewHolder(view) {
        private val titleTxt = view.findViewById<MaterialTextView>(R.id.setting_item_text)
        private val valueFromTxt = view.findViewById<MaterialTextView>(R.id.value_from_text)
        private val valueTxt = view.findViewById<MaterialTextView>(R.id.value_text)
        private val valueToTxt = view.findViewById<MaterialTextView>(R.id.value_to_text)
        private val slider = view.findViewById<Slider>(R.id.item_slider)
        private var mUnit = ""
        private var mCallback: ((Float) -> Unit)? = null
        
        // -------------------------------------------------------------------------------------------------
        
        init {
            slider.addOnChangeListener { _, value, fromUser ->
                if (!fromUser) return@addOnChangeListener
                if (adapterPosition == RecyclerView.NO_POSITION) return@addOnChangeListener
                valueTxt.text = String.format("%.2f %s", value, mUnit)
                mCallback?.invoke(value)
            }
        }

        // -------------------------------------------------------------------------------------------------
        
        fun bind(item: SettingsSliderItem) {
            item.run {
                mCallback = callback
                mUnit = unit
                titleTxt.text = title
                valueFromTxt.text = String.format("%.2f %s", valueFrom, unit)
                valueTxt.text = String.format("%.2f %s", value, unit)
                valueToTxt.text = String.format("%.2f %s", valueTo, unit)
                slider.valueFrom = valueFrom
                slider.value = value
                slider.valueTo = valueTo
            }
        }
        // -------------------------------------------------------------------------------------------------
    }
    
    // -------------------------------------------------------------------------------------------------

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType < 0) return object: RecyclerView.ViewHolder(View(parent.context)){}
        val type = ESettingsItemType.entries[viewType]
        return when (type) {
            ESettingsItemType.SWITCH -> SwitchItemView(LayoutInflater.from(parent.context).inflate(R.layout.switch_settings_item, parent, false))
            ESettingsItemType.SLIDER -> SliderItemView(LayoutInflater.from(parent.context).inflate(R.layout.slider_settings_item, parent, false))
        }
    }

    // -------------------------------------------------------------------------------------------------
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val type = ESettingsItemType.entries[getItemViewType(position)]
        when (type) {
            ESettingsItemType.SWITCH -> (holder as SwitchItemView).bind(getItem(position) as SettingsSwitchItem)
            ESettingsItemType.SLIDER -> (holder as SliderItemView).bind(getItem(position) as SettingsSliderItem)
        }
    }

    // -------------------------------------------------------------------------------------------------
    
    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        if (item is SettingsSwitchItem) return ESettingsItemType.SWITCH.ordinal
        if (item is SettingsSliderItem) return ESettingsItemType.SLIDER.ordinal
        return -1
    }
    // -------------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------
