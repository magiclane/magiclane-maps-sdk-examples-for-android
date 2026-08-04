/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.publictransitrouting

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.examples.publictransitrouting.databinding.ItemRouteChipBinding

/**
 * Horizontal pager of route cards displayed at the bottom of the map: each card takes the full
 * width, and the visible one is the route displayed on the map. Tapping a segment icon flies to
 * that segment; transit segments show only their icon (no line name), as in Magic Earth.
 */
class RouteChipAdapter(
    private val onRouteTap: (routeIndex: Int) -> Unit,
    private val onSegmentTap: (routeIndex: Int, segmentIndex: Int) -> Unit,
) : RecyclerView.Adapter<RouteChipAdapter.ChipViewHolder>() {

    private var items: List<PTRouteItem> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(items: List<PTRouteItem>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipViewHolder =
        ChipViewHolder(ItemRouteChipBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ChipViewHolder, position: Int) = holder.bind(items[position])

    inner class ChipViewHolder(private val binding: ItemRouteChipBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PTRouteItem) {
            binding.timeInterval.text = item.timeIntervalText
            binding.duration.text = item.durationText

            binding.chipCard.setOnClickListener { onRouteTap(item.routeIndex) }

            SegmentStrip.populate(binding.segmentsContainer, item, showLineNames = false) { segmentIndex ->
                onSegmentTap(item.routeIndex, segmentIndex)
            }
        }
    }
}
