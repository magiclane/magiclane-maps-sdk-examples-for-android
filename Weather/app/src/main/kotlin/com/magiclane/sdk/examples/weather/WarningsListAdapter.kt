/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.weather

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.WeatherWarning
import com.magiclane.sdk.examples.weather.databinding.WarningItemBinding

data class WarningItem(
    val title: String,
    val severity: String,
    val period: String,
    val description: String,
    val color: Int,
    val warning: WeatherWarning,
)

class WarningsListAdapter(
    private val onWarningSelected: (WarningItem) -> Unit,
) : ListAdapter<WarningItem, WarningsListAdapter.WarningItemViewHolder>(diffUtilCallback) {

    companion object {
        private const val SELECTED_BACKGROUND_ALPHA = 40

        // used by the adapter for calculating the optimum number of changes to be made when the list is being updated
        val diffUtilCallback = object : DiffUtil.ItemCallback<WarningItem>() {
            override fun areItemsTheSame(oldItem: WarningItem, newItem: WarningItem): Boolean =
                oldItem.title == newItem.title && oldItem.period == newItem.period

            override fun areContentsTheSame(oldItem: WarningItem, newItem: WarningItem): Boolean = oldItem == newItem
        }
    }

    private var selectedPosition = RecyclerView.NO_POSITION

    fun clearSelection() {
        val previous = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
    }

    inner class WarningItemViewHolder(val binding: WarningItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                // Re-tapping the selected item skips the rebinds but still notifies the
                // listener, so the map re-centers on the warning every time.
                if (position != selectedPosition) {
                    val previous = selectedPosition
                    selectedPosition = position
                    if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
                    notifyItemChanged(position)
                }
                onWarningSelected(getItem(position))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WarningItemViewHolder = WarningItemViewHolder(
        WarningItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: WarningItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            colorIndicator.setBackgroundColor(item.color)
            warningName.text = item.title
            warningSeverity.also {
                it.text = item.severity
                it.isVisible = item.severity.isNotEmpty()
            }
            warningPeriod.also {
                it.text = item.period
                it.isVisible = item.period.isNotEmpty()
            }
            warningDescription.also {
                it.text = item.description
                it.isVisible = item.description.isNotEmpty()
            }
            root.setBackgroundColor(
                if (position == selectedPosition) {
                    ColorUtils.setAlphaComponent(item.color, SELECTED_BACKGROUND_ALPHA)
                } else {
                    Color.TRANSPARENT
                },
            )
        }
    }
}
