/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselection

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.d3scene.PTTrip
import com.magiclane.sdk.examples.mapselection.PTUi.lineName
import com.magiclane.sdk.examples.mapselection.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.mapselection.databinding.ItemPtLineBinding
import com.magiclane.sdk.examples.mapselection.databinding.ItemPtTripBinding
import com.magiclane.sdk.util.SdkCall

// Half screen view of a public transport station, hosted by the map screen: the header keeps the
// usual location details look (name on the first line, address on the second), followed by a
// horizontal selectable list of the lines crossing the station and the list of upcoming
// departures. The realtime data is refreshed every minute, mirroring the native location details
// controller. The host owns the panel's visibility (and everything drawn on the map alongside
// it); this controller owns the panel's content, its refresh timer and the station store.
// [onLinesSelectionChanged] reports every change of the line chips selection (an empty set means
// "all lines") so the host can mirror the filter on the map's route shapes; the initial "all
// lines" state of a freshly opened station is not reported — the host draws that itself.
class PublicTransportStationPanel(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    onCloseRequested: () -> Unit,
    private val onLinesSelectionChanged: (Set<String>) -> Unit,
) {
    // Line chips crossing this station (empty when the station is served by a single line).
    private data class LineItem(val name: String, val backgroundColor: Int, val textColor: Int)

    private val lines = mutableListOf<LineItem>()
    private val selectedLines = mutableSetOf<String>()

    // Selection last reported through onLinesSelectionChanged, to notify real changes only —
    // onSelectionChanged also runs for rebuilds (open, periodic refresh) that don't alter it.
    private var lastNotifiedSelection: Set<String> = emptySet()

    // Departures currently displayed (after applying the selected lines filter).
    private var trips = listOf<PTTrip>()

    private val linesAdapter = LinesAdapter()
    private val vehiclesAdapter = VehiclesAdapter()

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStationInfo()
            refreshHandler.postDelayed(this, PTUi.REFRESH_INTERVAL_MS)
        }
    }

    init {
        binding.apply {
            ptLinesList.adapter = linesAdapter
            ptVehiclesList.adapter = vehiclesAdapter

            ptCloseButton.setOnClickListener { onCloseRequested() }

            ptClearLinesButton.setOnClickListener {
                selectedLines.clear()
                onSelectionChanged()
            }
        }
    }

    // Populates the panel from the current PTStationStore content and starts the periodic
    // realtime refresh. Called by the host every time a station is opened.
    fun show() {
        binding.apply {
            ptImage.setImageBitmap(PTStationStore.stationIcon)
            ptName.text = PTStationStore.stationName
            ptDescription.text = PTStationStore.stationAddress
            ptDescription.isVisible = PTStationStore.stationAddress.isNotEmpty()
            ptLinesList.scrollToPosition(0)
            ptVehiclesList.scrollToPosition(0)
        }

        selectedLines.clear()
        lastNotifiedSelection = emptySet()
        rebuildContent()

        refreshHandler.removeCallbacksAndMessages(null)
        refreshHandler.postDelayed(refreshRunnable, PTUi.REFRESH_INTERVAL_MS)
    }

    // Stops the refresh and releases the tapped station's data. Called by the host when the
    // panel is dismissed.
    fun hide() {
        refreshHandler.removeCallbacksAndMessages(null)
        PTStationStore.clear()
    }

    // Re-requests the station's extended data so the realtime departures stay fresh. The shapes
    // are not requested again — they are static and already drawn, and they are the largest part
    // of the payload.
    private fun refreshStationInfo() = SdkCall.execute {
        PTStationStore.overlayItem?.getPTStopInfo { stopInfo ->
            // The SDK delivers this callback on the main thread.
            if (activity.isFinishing || activity.isDestroyed || stopInfo == null ||
                !binding.ptStationContainer.isVisible
            ) {
                return@getPTStopInfo
            }

            PTStationStore.stopInfo = stopInfo
            rebuildContent()
        }
    }

    // Builds the line chips and the filtered departures list from the current station info.
    private fun rebuildContent() {
        val stopInfo = PTStationStore.stopInfo ?: return

        // Alerts scoped to the station itself (closure, elevator outage, ...) are shown as a
        // note under the header. Alert instances are shared within a response, so distinct()
        // deduplicates the ones referenced by several stops.
        val stationAlerts = PTUi.activeAlerts(
            stopInfo.stops.flatMap { it.alerts }.distinct(),
            PTUi.wallClockNow(),
        )
        binding.ptStationAlerts.isVisible = stationAlerts.isNotEmpty()
        if (stationAlerts.isNotEmpty()) {
            binding.ptStationAlerts.text = stationAlerts.joinToString("\n") { PTUi.alertText(it) }

            val color = PTUi.alertColor(activity, stationAlerts)
            binding.ptStationAlerts.setTextColor(color)
            TextViewCompat.setCompoundDrawableTintList(
                binding.ptStationAlerts,
                ColorStateList.valueOf(color),
            )
        }

        // One chip per distinct line crossing the station; a single-line station gets no
        // chips at all, just like the native location details controller.
        lines.clear()
        stopInfo.stops
            .flatMap { it.routes }
            .distinctBy { it.lineName }
            .filter { it.lineName.isNotEmpty() }
            .mapTo(lines) { route ->
                val background = PTUi.parseColor(route.routeColor, ContextCompat.getColor(activity, R.color.gray))
                LineItem(
                    name = route.lineName,
                    backgroundColor = background,
                    textColor = PTUi.contrastingTextColor(
                        background,
                        PTUi.parseColor(route.routeTextColor, ContextCompat.getColor(activity, R.color.on_secondary)),
                    ),
                )
            }
        if (lines.size == 1) lines.clear()
        selectedLines.retainAll(lines.map { it.name }.toSet())

        onSelectionChanged()
    }

    // Applies the chip selection to the departures list and refreshes both lists.
    @SuppressLint("NotifyDataSetChanged")
    private fun onSelectionChanged() {
        val allTrips = PTStationStore.stopInfo?.trips ?: emptyList()
        trips = if (selectedLines.isEmpty()) {
            allTrips
        } else {
            allTrips.filter { it.lineName in selectedLines }
        }

        binding.apply {
            ptLinesList.isVisible = lines.isNotEmpty()
            ptClearLinesButton.isVisible = selectedLines.isNotEmpty()
            ptEmptyView.isVisible = trips.isEmpty()
        }

        linesAdapter.notifyDataSetChanged()
        vehiclesAdapter.notifyDataSetChanged()

        // Mirror the chip selection on the map: only the selected lines' shapes stay drawn.
        val selection = selectedLines.toSet()
        if (selection != lastNotifiedSelection) {
            lastNotifiedSelection = selection
            onLinesSelectionChanged(selection)
        }
    }

    // Opens the trip view for the tapped departure: the current (filtered) departures are
    // handed over so the trip view can page through the other trips of the same line.
    private fun openTripView(position: Int) {
        PTStationStore.tripsForTripView = trips
        activity.startActivity(
            Intent(activity, PublicTransportTripActivity::class.java)
                .putExtra(PublicTransportTripActivity.EXTRA_TRIP_INDEX, position),
        )
    }

    // Horizontal list of selectable line chips.
    private inner class LinesAdapter : RecyclerView.Adapter<LinesAdapter.ViewHolder>() {
        inner class ViewHolder(val itemBinding: ItemPtLineBinding) : RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            ItemPtLineBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

        override fun getItemCount() = lines.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val line = lines[position]

            holder.itemBinding.lineName.apply {
                text = line.name
                setTextColor(line.textColor)
                background = PTUi.badgeBackground(context, line.backgroundColor)

                // Unselected chips are dimmed while a selection is active.
                alpha = if (selectedLines.isEmpty() || line.name in selectedLines) 1f else 0.5f

                setOnClickListener {
                    if (!selectedLines.remove(line.name)) {
                        selectedLines.add(line.name)
                    }
                    onSelectionChanged()
                }
            }
        }
    }

    // Vertical list of the upcoming departures.
    private inner class VehiclesAdapter : RecyclerView.Adapter<VehiclesAdapter.ViewHolder>() {
        inner class ViewHolder(val itemBinding: ItemPtTripBinding) : RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            ItemPtTripBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

        override fun getItemCount() = trips.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val trip = trips[position]
            val now = PTUi.wallClockNow()

            holder.itemBinding.apply {
                vehicleIcon.setImageResource(PTUi.vehicleIconRes(trip.route.routeType))

                val background = PTUi.parseColor(trip.route.routeColor, ContextCompat.getColor(root.context, R.color.gray))
                lineBadge.apply {
                    text = trip.lineName
                    setTextColor(
                        PTUi.contrastingTextColor(
                            background,
                            PTUi.parseColor(trip.route.routeTextColor, ContextCompat.getColor(root.context, R.color.on_secondary)),
                        ),
                    )
                    this.background = PTUi.badgeBackground(context, background)
                }

                destination.text = trip.route.heading ?: ""

                // isCancelled is the authoritative "don't ride this" flag; the departure keeps
                // its slot but is marked red with a struck-through time.
                val isCancelled = trip.isCancelled == true

                val statusText = PTUi.tripStatus(root.context, trip.departureTime, now, trip.stopPlatformCode, isCancelled)
                status.text = statusText
                status.isVisible = statusText.isNotEmpty()

                val (time, unit) = PTUi.departureLabel(root.context, trip.departureTime, now)
                departureTime.text = time
                departureTimeUnit.text = unit
                departureTimeUnit.isVisible = unit.isNotEmpty()

                val statusColor = if (isCancelled) {
                    ContextCompat.getColor(root.context, R.color.pt_status_late)
                } else {
                    PTUi.statusColor(root.context, trip.hasRealtime, trip.delayMinutes ?: 0)
                }
                status.setTextColor(statusColor)
                departureTime.setTextColor(statusColor)
                departureTimeUnit.setTextColor(statusColor)
                departureTime.paintFlags = if (isCancelled) {
                    departureTime.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    departureTime.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }

                wheelchairIcon.isVisible = trip.isWheelchairAccessible
                bikeIcon.isVisible = trip.isBikeAllowed

                // Live crowding of the vehicle, when the realtime producer reports it.
                val crowding = PTUi.crowdingLevel(trip)
                crowdingIcon.isVisible = crowding != null
                if (crowding != null) {
                    crowdingIcon.imageTintList = ColorStateList.valueOf(PTUi.crowdingColor(root.context, crowding))
                    crowdingIcon.contentDescription = PTUi.crowdingLabel(root.context, crowding)
                }

                // Service alerts applying to this departure; the note itself is on the trip view.
                val alerts = PTUi.activeAlerts(trip.alerts, now)
                alertIcon.isVisible = alerts.isNotEmpty()
                if (alerts.isNotEmpty()) {
                    alertIcon.imageTintList = ColorStateList.valueOf(PTUi.alertColor(root.context, alerts))
                }

                root.setOnClickListener { openTripView(holder.bindingAdapterPosition) }
            }
        }
    }
}
