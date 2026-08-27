/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.definepersistentroadblock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.core.Parameter
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.definepersistentroadblock.databinding.RoadblockListItemBinding
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.TrafficEvent
import com.magiclane.sdk.util.GemList

/**
 * UI model for one active persistent roadblock, mirroring the fields shown by the
 * Magic Earth roadblocks view (name, length, from/to, validity interval, transport mode).
 */
class RoadblockItem(val event: TrafficEvent) {
    var name = ""
    var lengthText = ""
    var lengthValue = ""
    var fromText = ""
    var fromValue = ""
    var toText = ""
    var toValue = ""
    var validFromText = ""
    var validFromValue = ""
    var validUntilText = ""
    var validUntilValue = ""
    var transportMode = ERouteTransportMode.Car

    // Kept as fields so the native objects stay alive while the async preview data request runs.
    val parameters = GemList(Parameter::class)
    var previewDataListener: ProgressListener? = null
}

class RoadblocksAdapter(
    private val onDeleteTapped: (RoadblockItem) -> Unit,
    private val onItemTapped: (RoadblockItem) -> Unit,
) : RecyclerView.Adapter<RoadblocksAdapter.ViewHolder>() {

    private val items = mutableListOf<RoadblockItem>()

    class ViewHolder(val binding: RoadblockListItemBinding) : RecyclerView.ViewHolder(binding.root)

    fun submitItems(newItems: List<RoadblockItem>) {
        items.clear()
        items.addAll(newItems)
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    fun refreshItem(item: RoadblockItem) {
        val index = items.indexOf(item)
        if (index >= 0) notifyItemChanged(index)
    }

    fun removeItem(item: RoadblockItem): Int {
        val index = items.indexOf(item)
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        RoadblockListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.bindRoadblock(item, onDeleteTapped)
        holder.binding.root.setOnClickListener { onItemTapped(item) }
    }
}

/** Fills one roadblock card; shared between the list rows and the on-map info panel. */
fun RoadblockListItemBinding.bindRoadblock(item: RoadblockItem, onDeleteTapped: (RoadblockItem) -> Unit) {
    transportMode.setImageResource(
        when (item.transportMode) {
            ERouteTransportMode.Lorry -> R.drawable.ic_directions_truck_24
            ERouteTransportMode.Bicycle -> R.drawable.ic_directions_bike_24
            else -> R.drawable.ic_directions_car_24
        },
    )

    roadblockName.text = item.name
    roadblockLength.text = item.lengthValue

    fromText.text = item.fromText
    fromValue.text = item.fromValue
    toText.text = item.toText
    toValue.text = item.toValue

    validFromText.text = item.validFromText
    validFromValue.text = item.validFromValue
    validUntilText.text = item.validUntilText
    validUntilValue.text = item.validUntilValue

    val hasValidity = item.validFromValue.isNotEmpty()
    validFromDivider.isVisible = hasValidity
    validFromText.isVisible = hasValidity
    validFromValue.isVisible = hasValidity

    val hasValidUntil = item.validUntilValue.isNotEmpty()
    validUntilText.isVisible = hasValidUntil
    validUntilValue.isVisible = hasValidUntil

    deleteButton.setOnClickListener { onDeleteTapped(item) }
}
