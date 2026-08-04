/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.publictransitrouting

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.examples.publictransitrouting.databinding.ItemDescriptionAgencyBinding
import com.magiclane.sdk.examples.publictransitrouting.databinding.ItemDescriptionArrivalBinding
import com.magiclane.sdk.examples.publictransitrouting.databinding.ItemDescriptionHeaderBinding
import com.magiclane.sdk.examples.publictransitrouting.databinding.ItemDescriptionSegmentBinding
import com.magiclane.sdk.examples.publictransitrouting.databinding.ItemWalkInstructionBinding

/**
 * List adapter of the route description view: header, one row per segment, arrival row and,
 * when the SDK reports agencies, an "Agency Info" footer.
 */
class DescriptionAdapter(
    private val onSegmentTap: (segmentIndex: Int) -> Unit,
    private val onAgencyTap: (agencies: List<AgencyItem>) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_SEGMENT = 1
        const val TYPE_ARRIVAL = 2
        const val TYPE_AGENCY = 3
    }

    private var rows: List<DescriptionRow> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(rows: List<DescriptionRow>) {
        this.rows = rows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is DescriptionRow.Header -> TYPE_HEADER
        is DescriptionRow.Segment -> TYPE_SEGMENT
        is DescriptionRow.Arrival -> TYPE_ARRIVAL
        is DescriptionRow.AgencyInfo -> TYPE_AGENCY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(ItemDescriptionHeaderBinding.inflate(inflater, parent, false))
            TYPE_SEGMENT -> SegmentViewHolder(ItemDescriptionSegmentBinding.inflate(inflater, parent, false))
            TYPE_ARRIVAL -> ArrivalViewHolder(ItemDescriptionArrivalBinding.inflate(inflater, parent, false))
            else -> AgencyViewHolder(ItemDescriptionAgencyBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is DescriptionRow.Header -> (holder as HeaderViewHolder).bind(row)
            is DescriptionRow.Segment -> (holder as SegmentViewHolder).bind(row)
            is DescriptionRow.Arrival -> (holder as ArrivalViewHolder).bind(row)
            is DescriptionRow.AgencyInfo -> (holder as AgencyViewHolder).bind(row)
        }
    }

    /** Applies the walk/transit style to a rail line, or hides it when there is no leg. */
    private fun bindRailLine(view: RailLineView, legIsWalk: Boolean?) {
        view.visibility = if (legIsWalk == null) View.INVISIBLE else View.VISIBLE
        legIsWalk?.let { view.isWalkStyle = it }
    }

    /** Y center of [textView]'s first text line, in its parent's coordinates. */
    private fun firstLineCenter(textView: TextView): Int {
        val layout = textView.layout ?: return textView.top + textView.height / 2
        return textView.top + textView.totalPaddingTop +
            (layout.getLineTop(0) + layout.getLineBottom(0)) / 2
    }

    /** Sets an exact height on a rail line piece (no-op when already right). */
    private fun setRailHeight(view: View, height: Int) {
        val params = view.layoutParams
        if (params.height != height) {
            params.height = height
            view.layoutParams = params
        }
    }

    private fun bindOptionalText(view: TextView, text: String) {
        view.text = text
        view.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    inner class HeaderViewHolder(private val binding: ItemDescriptionHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: DescriptionRow.Header) {
            binding.timeInterval.text = row.item.timeIntervalText
            binding.duration.text = row.item.durationText
            // Same scrollable strip of the route's legs (walk / transit lines) as a routes-view
            // row; taps stay reserved for the segment rows below.
            SegmentStrip.populate(binding.segmentsContainer, row.item)
            bindOptionalText(binding.walkingInfo, row.item.walkingInfoText)
            bindOptionalText(binding.fare, row.item.fareText)
            bindOptionalText(binding.frequency, row.item.frequencyText)
            bindOptionalText(binding.transfers, row.item.transfersText)
            bindOptionalText(binding.warning, row.item.warningText)
        }
    }

    inner class SegmentViewHolder(private val binding: ItemDescriptionSegmentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: DescriptionRow.Segment) {
            val context = binding.root.context

            binding.departTime.text = row.departTimeText
            binding.segmentIcon.setImageResource(SegmentStrip.iconRes(row.transitType))
            binding.stationName.text = row.stationName

            // Rail: the line above the station ring belongs to the arriving leg, the one
            // below it to the leg this row describes.
            bindRailLine(binding.railTop, row.prevIsWalk)
            bindRailLine(binding.railBottom, row.isWalk)

            if (!row.isWalk && row.badgeText.isNotEmpty()) {
                binding.lineRow.visibility = View.VISIBLE
                binding.lineBadge.text = " ${row.badgeText} "
                binding.lineBadge.background = GradientDrawable().apply {
                    cornerRadius = context.resources.getDimension(R.dimen.badge_corner_radius)
                    setColor(
                        if (row.badgeColor != 0) {
                            row.badgeColor
                        } else {
                            ContextCompat.getColor(context, R.color.default_line_badge)
                        },
                    )
                }
                binding.lineBadge.setTextColor(
                    if (row.badgeTextColor != 0) {
                        row.badgeTextColor
                    } else {
                        ContextCompat.getColor(context, R.color.on_surface)
                    },
                )
                binding.towards.text = row.towardsText
                binding.towards.visibility = if (row.towardsText.isEmpty()) View.GONE else View.VISIBLE
            } else {
                binding.lineRow.visibility = View.GONE
            }

            bindOptionalText(binding.platform, row.platformText)

            // Walk rows show "distance (time)" in the stops slot, tappable to expand the
            // turn-by-turn instructions; transit rows show "N stops (time)", tappable to
            // expand the intermediate stop names.
            val stopsText = if (row.isWalk) row.walkInfoText else row.stopsText
            bindOptionalText(binding.stopsText, stopsText)
            val hasStops = row.intermediateStops.isNotEmpty()
            val expandable = hasStops || row.walkInstructions.isNotEmpty()
            binding.stopsText.setTextColor(
                ContextCompat.getColor(context, if (expandable) R.color.primary else R.color.on_background),
            )
            if (expandable) {
                binding.stopsText.setOnClickListener {
                    row.expanded = !row.expanded
                    notifyItemChanged(bindingAdapterPosition)
                }
            } else {
                binding.stopsText.setOnClickListener(null)
                // Otherwise a non-expandable label would swallow the row tap.
                binding.stopsText.isClickable = false
            }
            binding.stopsList.visibility = if (hasStops && row.expanded) View.VISIBLE else View.GONE
            if (hasStops) binding.stopsList.text = row.intermediateStops.joinToString("\n")
            bindWalkInstructions(row)

            binding.stayOnVehicle.visibility = if (row.stayOnVehicle) View.VISIBLE else View.GONE
            binding.stayOnVehicle.setText(R.string.stay_on_same_vehicle)

            binding.segmentRow.setOnClickListener { onSegmentTap(row.segmentIndex) }

            binding.railBottom.dotCenters = emptyList()
            binding.root.doOnPreDraw { alignRail(row) }
        }

        /** Rebuilds the expanded turn-by-turn instruction rows of a pedestrian segment. */
        private fun bindWalkInstructions(row: DescriptionRow.Segment) {
            val container = binding.instructionsList
            container.removeAllViews()
            val show = row.expanded && row.walkInstructions.isNotEmpty()
            container.visibility = if (show) View.VISIBLE else View.GONE
            if (!show) return

            val inflater = LayoutInflater.from(container.context)
            row.walkInstructions.forEachIndexed { index, instruction ->
                val item = ItemWalkInstructionBinding.inflate(inflater, container, false)
                item.divider.visibility = if (index == 0) View.GONE else View.VISIBLE
                item.turnIcon.setImageBitmap(instruction.icon)
                bindOptionalText(item.instructionText, instruction.text)
                bindOptionalText(item.instructionDescription, instruction.descriptionText)
                item.distanceText.text = instruction.distanceText
                // Tapping an instruction presents the walk on the map (as in Magic Earth,
                // where the whole segment is flown to, not the single instruction).
                item.instructionRow.setOnClickListener { onSegmentTap(row.segmentIndex) }
                container.addView(item.root)
            }
        }

        /**
         * Once the texts are laid out: centers the station ring on the station name's first
         * line and places one dot on the rail per expanded intermediate stop name.
         */
        private fun alignRail(row: DescriptionRow.Segment) {
            val textColumn = binding.stationName.parent as View
            val ringSize = binding.stationRing.layoutParams.width

            val ringCenter = textColumn.top + firstLineCenter(binding.stationName)
            val railTopHeight = (ringCenter - ringSize / 2).coerceAtLeast(0)
            setRailHeight(binding.railTop, railTopHeight)

            // The rail piece below the ring starts right after it (also after the relayout
            // that the height change above may trigger).
            val railBottomTop = railTopHeight + ringSize

            val stopsLayout = binding.stopsList.layout
            binding.railBottom.dotCenters =
                if (row.expanded && row.intermediateStops.isNotEmpty() && stopsLayout != null) {
                    var characterOffset = 0
                    row.intermediateStops.map { stop ->
                        val line = stopsLayout.getLineForOffset(characterOffset)
                        characterOffset += stop.length + 1 // the stop name and its "\n"
                        val centerInList = binding.stopsList.totalPaddingTop +
                            (stopsLayout.getLineTop(line) + stopsLayout.getLineBottom(line)) / 2f
                        textColumn.top + binding.stopsList.top + centerInList - railBottomTop
                    }
                } else {
                    emptyList()
                }
        }
    }

    inner class ArrivalViewHolder(private val binding: ItemDescriptionArrivalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: DescriptionRow.Arrival) {
            binding.arriveTime.text = row.arriveTimeText
            binding.destinationName.text = row.destinationName
            bindRailLine(binding.railTop, row.prevIsWalk)

            binding.root.doOnPreDraw {
                val ringSize = binding.root.resources.getDimensionPixelSize(R.dimen.station_ring_size)
                val ringCenter = firstLineCenter(binding.destinationName)
                setRailHeight(binding.railTop, (ringCenter - ringSize / 2).coerceAtLeast(0))
            }
        }
    }

    inner class AgencyViewHolder(private val binding: ItemDescriptionAgencyBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: DescriptionRow.AgencyInfo) {
            binding.agencyInfo.setOnClickListener { onAgencyTap(row.agencies) }
        }
    }
}
