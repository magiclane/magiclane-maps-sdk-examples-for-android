/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.publictransitrouting

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.examples.publictransitrouting.databinding.ItemRouteActionBinding
import com.magiclane.sdk.examples.publictransitrouting.databinding.ItemRouteRowBinding

/**
 * Vertical list of the stand-alone Routes view: an "Earlier" card, one row per route
 * (the selected one is highlighted) and a "Later" card.
 */
class RouteRowAdapter(
    private val onRouteTap: (routeIndex: Int) -> Unit,
    private val onEarlierTap: () -> Unit,
    private val onLaterTap: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_EARLIER = 0
        const val TYPE_ROUTE = 1
        const val TYPE_LATER = 2
    }

    private var items: List<PTRouteItem> = emptyList()
    private var selectedIndex = 0

    @SuppressLint("NotifyDataSetChanged")
    fun submit(items: List<PTRouteItem>, selectedIndex: Int) {
        this.items = items
        this.selectedIndex = selectedIndex
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = if (items.isEmpty()) 0 else items.size + 2

    override fun getItemViewType(position: Int): Int = when (position) {
        0 -> TYPE_EARLIER
        itemCount - 1 -> TYPE_LATER
        else -> TYPE_ROUTE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ROUTE) {
            RouteViewHolder(ItemRouteRowBinding.inflate(inflater, parent, false))
        } else {
            ActionViewHolder(ItemRouteActionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is RouteViewHolder -> holder.bind(items[position - 1])
            is ActionViewHolder -> holder.bind(getItemViewType(position) == TYPE_EARLIER)
        }
    }

    private fun bindOptionalText(view: TextView, text: String) {
        view.text = text
        view.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    inner class RouteViewHolder(private val binding: ItemRouteRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PTRouteItem) {
            val context = binding.root.context

            binding.timeInterval.text = item.timeIntervalText
            binding.duration.text = item.durationText
            bindOptionalText(binding.walkingInfo, item.walkingInfoText)
            bindOptionalText(binding.fare, item.fareText)
            bindOptionalText(binding.frequency, item.frequencyText)
            bindOptionalText(binding.transfers, item.transfersText)
            bindOptionalText(binding.warning, item.warningText)

            val selected = item.routeIndex == selectedIndex
            binding.rowCard.strokeWidth = if (selected) {
                context.resources.getDimensionPixelSize(R.dimen.chip_stroke_width)
            } else {
                0
            }
            binding.rowCard.strokeColor = ContextCompat.getColor(context, R.color.primary)

            binding.rowCard.setOnClickListener { onRouteTap(item.routeIndex) }

            // Segment taps are only active on the map's horizontal list (as in Magic Earth).
            SegmentStrip.populate(binding.segmentsContainer, item)
        }
    }

    inner class ActionViewHolder(private val binding: ItemRouteActionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(isEarlier: Boolean) {
            binding.actionText.setText(if (isEarlier) R.string.earlier else R.string.later)
            binding.actionCard.setOnClickListener { if (isEarlier) onEarlierTap() else onLaterTap() }
        }
    }
}
