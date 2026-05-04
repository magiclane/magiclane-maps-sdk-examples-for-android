/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemo

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.examples.bikedemo.databinding.SliderSettingsItemBinding
import com.magiclane.sdk.examples.bikedemo.databinding.SwitchSettingsItemBinding

class SettingsAdapter : ListAdapter<SettingsItem, RecyclerView.ViewHolder>(settingsDiffUtil) {

    companion object {
        val settingsDiffUtil = object : DiffUtil.ItemCallback<SettingsItem>() {
            override fun areItemsTheSame(oldItem: SettingsItem, newItem: SettingsItem): Boolean =
                oldItem.title == newItem.title

            override fun areContentsTheSame(oldItem: SettingsItem, newItem: SettingsItem): Boolean = false
        }
    }

    enum class ESettingsItemType {
        SWITCH,
        SLIDER,
    }

    class SwitchItemView(private val binding: SwitchSettingsItemBinding) : RecyclerView.ViewHolder(binding.root) {
        private var mCallback: ((Boolean) -> Unit)? = null
        init {
            binding.settingItemSwitch.setOnCheckedChangeListener { _, isChecked ->
                @Suppress("DEPRECATION")
                if (adapterPosition == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
                mCallback?.invoke(isChecked)
            }
        }

        fun bind(item: SettingsSwitchItem) {
            mCallback = item.callback
            binding.settingItemText.text = item.title
            binding.settingItemSwitch.isChecked = item.itIs
        }
    }

    @SuppressLint("DefaultLocale")
    class SliderItemView(private val binding: SliderSettingsItemBinding) : RecyclerView.ViewHolder(binding.root) {
        private var mUnit = ""
        private var mCallback: ((Float) -> Unit)? = null

        init {
            binding.itemSlider.addOnChangeListener { _, value, fromUser ->
                if (!fromUser) return@addOnChangeListener
                @Suppress("DEPRECATION")
                if (adapterPosition == RecyclerView.NO_POSITION) return@addOnChangeListener
                binding.valueText.text = String.format("%.1f %s", value, mUnit)
                mCallback?.invoke(value)
            }
        }

        fun bind(item: SettingsSliderItem) {
            item.run {
                mCallback = callback
                mUnit = unit
                binding.settingItemText.text = title
                binding.valueFromText.text = String.format("%d %s", valueFrom.toInt(), unit).trim()
                binding.valueText.text = String.format("%.1f %s", value, unit).trim()
                binding.valueToText.text = String.format("%d %s", valueTo.toInt(), unit).trim()
                binding.itemSlider.valueFrom = valueFrom
                binding.itemSlider.value = value
                binding.itemSlider.valueTo = valueTo
                binding.itemSlider.setLabelFormatter { value ->
                    String.format("%.1f", value).trim()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType < 0) return object : RecyclerView.ViewHolder(View(parent.context)) {}
        val type = ESettingsItemType.entries[viewType]
        return when (type) {
            ESettingsItemType.SWITCH -> SwitchItemView(
                SwitchSettingsItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                ),
            )
            ESettingsItemType.SLIDER -> SliderItemView(
                SliderSettingsItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                ),
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val type = ESettingsItemType.entries[getItemViewType(position)]
        when (type) {
            ESettingsItemType.SWITCH -> (holder as SwitchItemView).bind(
                getItem(position) as SettingsSwitchItem,
            )
            ESettingsItemType.SLIDER -> (holder as SliderItemView).bind(
                getItem(position) as SettingsSliderItem,
            )
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        if (item is SettingsSwitchItem) return ESettingsItemType.SWITCH.ordinal
        if (item is SettingsSliderItem) return ESettingsItemType.SLIDER.ordinal
        return -1
    }
}
