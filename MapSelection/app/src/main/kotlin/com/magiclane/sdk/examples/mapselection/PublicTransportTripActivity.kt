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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.magiclane.sdk.d3scene.PTTrip
import com.magiclane.sdk.examples.mapselection.PTUi.lineName
import com.magiclane.sdk.examples.mapselection.databinding.ActivityPtTripBinding
import com.magiclane.sdk.examples.mapselection.databinding.ItemPtAlertBinding
import com.magiclane.sdk.examples.mapselection.databinding.ItemPtStopBinding
import com.magiclane.sdk.util.SdkCall

// The public transport vehicle stations view: shows the stations of the tapped trip in a
// tabbed pager, together with up to four other trips of the same line (previous and upcoming),
// mirroring the native public transport vehicle controller.
class PublicTransportTripActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPtTripBinding

    // One page per trip of the tapped line; pages[currentPageIndex] is the tapped trip.
    private val pages = mutableListOf<PTTrip>()
    private var currentPageIndex = 0

    private lateinit var pagesAdapter: PagesAdapter

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshTrips()
            refreshHandler.postDelayed(this, PTUi.REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val trips = PTStationStore.tripsForTripView
        if (!PTStationStore.isValid || trips.isEmpty()) {
            finish()
            return
        }

        val tappedIndex = intent.getIntExtra(EXTRA_TRIP_INDEX, 0).coerceIn(trips.indices)
        buildPages(trips, tappedIndex)

        binding = ActivityPtTripBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindHeader(trips[tappedIndex])
        bindPager()

        refreshHandler.postDelayed(refreshRunnable, PTUi.REFRESH_INTERVAL_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshHandler.removeCallbacksAndMessages(null)
    }

    // Collects the trips shown as pages: the tapped trip plus other trips of the same line —
    // first the upcoming ones, then, if fewer than the maximum, the previous ones (which shift
    // the current page index so the tapped trip stays selected).
    private fun buildPages(trips: List<PTTrip>, tappedIndex: Int) {
        val tappedLine = trips[tappedIndex].lineName

        for (i in tappedIndex until trips.size) {
            if (trips[i].lineName == tappedLine) {
                pages.add(trips[i])
                if (pages.size == PTUi.MAX_TRIP_PAGES) break
            }
        }

        if (pages.size < PTUi.MAX_TRIP_PAGES) {
            for (i in tappedIndex - 1 downTo 0) {
                if (trips[i].lineName == tappedLine) {
                    pages.add(0, trips[i])
                    currentPageIndex++
                    if (pages.size == PTUi.MAX_TRIP_PAGES) break
                }
            }
        }
    }

    private fun bindHeader(trip: PTTrip) = binding.apply {
        vehicleIcon.setImageResource(PTUi.vehicleIconRes(trip.route.routeType))

        val background = PTUi.parseColor(
            trip.route.routeColor,
            ContextCompat.getColor(this@PublicTransportTripActivity, R.color.gray),
        )
        lineBadge.apply {
            text = trip.lineName
            setTextColor(
                PTUi.contrastingTextColor(
                    background,
                    PTUi.parseColor(trip.route.routeTextColor, ContextCompat.getColor(context, R.color.on_secondary)),
                ),
            )
            this.background = PTUi.badgeBackground(context, background)
        }

        destination.text = trip.route.heading ?: ""
        closeButton.setOnClickListener { finish() }

        val agencyName = trip.agency.name
        val agencyUrl = trip.agency.url
        if (agencyName.isNotEmpty()) {
            agencyButton.text = agencyName
            agencyButton.isVisible = true
            if (!agencyUrl.isNullOrEmpty()) {
                agencyButton.setOnClickListener {
                    startActivity(
                        Intent(this@PublicTransportTripActivity, WebActivity::class.java)
                            .putExtra("url", agencyUrl),
                    )
                }
            }
        }
    }

    private fun bindPager() = binding.apply {
        // Accessibility, crowding and alerts differ between the trips of the line, so they
        // follow the currently visible page rather than the tapped trip.
        stationsPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bindTripDetails(pages[position])
            }
        })

        pagesAdapter = PagesAdapter()
        stationsPager.adapter = pagesAdapter
        stationsPager.setCurrentItem(currentPageIndex, false)

        TabLayoutMediator(tabLayout, stationsPager) { tab, _ ->
            tab.setIcon(R.drawable.pt_tab_dot)
        }.attach()
        tabLayout.isVisible = pages.size > 1
    }

    // The per-trip header parts: accessibility icons, live crowding and the service alerts note.
    private fun bindTripDetails(trip: PTTrip) = binding.apply {
        wheelchairIcon.isVisible = trip.isWheelchairAccessible
        bikeIcon.isVisible = trip.isBikeAllowed

        // Live crowding of the vehicle, when the realtime producer reports it.
        val crowding = PTUi.crowdingLevel(trip)
        crowdingIcon.isVisible = crowding != null
        if (crowding != null) {
            crowdingIcon.imageTintList = ColorStateList.valueOf(
                PTUi.crowdingColor(this@PublicTransportTripActivity, crowding),
            )
            crowdingIcon.contentDescription = PTUi.crowdingLabel(this@PublicTransportTripActivity, crowding)
        }

        // The alerts note also carries the cancellation: isCancelled is the authoritative flag,
        // shown even when the feed delivers no explaining NoService alert.
        val alerts = PTUi.activeAlerts(trip.alerts, PTUi.wallClockNow())
        val isCancelled = trip.isCancelled == true

        tripAlerts.removeAllViews()
        tripAlerts.isVisible = isCancelled || alerts.isNotEmpty()

        val color = if (isCancelled) {
            ContextCompat.getColor(this@PublicTransportTripActivity, R.color.pt_status_late)
        } else {
            PTUi.alertColor(this@PublicTransportTripActivity, alerts)
        }
        if (isCancelled) addAlertRow(getString(R.string.pt_cancelled), null, color)
        for (alert in alerts) {
            addAlertRow(PTUi.alertName(alert), PTUi.alertDescription(alert), color)
        }
    }

    // One row of the alerts note. An alert carrying a description gets an info icon after its
    // name; tapping it opens the description in a popup. The description arrives as HTML
    // (<p>, <strong>, ...), so it is rendered rather than shown verbatim.
    private fun addAlertRow(name: String, description: String?, color: Int) {
        val row = ItemPtAlertBinding.inflate(layoutInflater, binding.tripAlerts, true)
        row.alertName.text = name
        row.alertName.setTextColor(color)
        row.alertIcon.imageTintList = ColorStateList.valueOf(color)
        row.infoIcon.isVisible = description != null
        if (description != null) {
            val showDescription = View.OnClickListener {
                val dialog = MaterialAlertDialogBuilder(this)
                    .setTitle(name)
                    .setMessage(HtmlCompat.fromHtml(description, HtmlCompat.FROM_HTML_MODE_COMPACT))
                    .setPositiveButton(R.string.ok, null)
                    .show()

                // Links of the description open in the browser.
                dialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
                    LinkMovementMethod.getInstance()
            }
            row.alertName.setOnClickListener(showDescription)
            row.infoIcon.setOnClickListener(showDescription)
        }
    }

    // Re-requests the station data and swaps in the fresh trips, matched by route id and trip
    // index. Statuses are recomputed at bind time, so unmatched (finished) trips still update.
    private fun refreshTrips() = SdkCall.execute {
        PTStationStore.overlayItem?.getPTStopInfo { stopInfo ->
            // The SDK delivers this callback on the main thread.
            if (isFinishing || isDestroyed) return@getPTStopInfo

            if (stopInfo != null) {
                PTStationStore.stopInfo = stopInfo
                for (i in pages.indices) {
                    stopInfo.trips.firstOrNull {
                        it.route.routeId == pages[i].route.routeId && it.tripIndex == pages[i].tripIndex
                    }?.let { pages[i] = it }
                }
            }

            pagesAdapter.notifyItemRangeChanged(0, pages.size)
            bindTripDetails(pages[binding.stationsPager.currentItem])
        }
    }

    // One page per trip: a vertical list of its stations.
    private inner class PagesAdapter : RecyclerView.Adapter<PagesAdapter.PageViewHolder>() {
        inner class PageViewHolder(val list: RecyclerView) : RecyclerView.ViewHolder(list)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PageViewHolder(
            RecyclerView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                layoutManager = LinearLayoutManager(parent.context)
                clipToPadding = false
            },
        )

        override fun getItemCount() = pages.size

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.list.adapter = StopsAdapter(pages[position])
        }
    }

    // The stations of a single trip, drawn along a timeline tinted with the route color.
    private inner class StopsAdapter(private val trip: PTTrip) : RecyclerView.Adapter<StopsAdapter.ViewHolder>() {
        inner class ViewHolder(val itemBinding: ItemPtStopBinding) : RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            ItemPtStopBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

        override fun getItemCount() = trip.stopTimes.size

        @SuppressLint("PrivateResource")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val stop = trip.stopTimes[position]
            val now = PTUi.wallClockNow()
            val isLastStop = position == trip.stopTimes.size - 1
            val departed = stop.departureTime?.before(now) == true

            holder.itemBinding.apply {
                stopName.text = stop.stopName
                stopStatus.text = PTUi.stopStatus(root.context, departed, isLastStop)
                stopStatus.setTextColor(PTUi.statusColor(root.context, stop.hasRealtime, stop.delay))
                stopDepartureTime.text = PTUi.clockTime(stop.departureTime)

                timeline.setBackgroundResource(
                    when (position) {
                        0 -> R.drawable.pt_line_top
                        trip.stopTimes.size - 1 -> R.drawable.pt_line_bottom
                        else -> R.drawable.pt_line_middle
                    },
                )
                timeline.backgroundTintList = ColorStateList.valueOf(
                    PTUi.parseColor(trip.route.routeColor, ContextCompat.getColor(root.context, R.color.primary)),
                )
                timelineCircle.setBackgroundResource(
                    if (stop.isBefore) R.drawable.pt_stop_circle_translucent else R.drawable.pt_stop_circle,
                )

                // Stations already passed are dimmed; their timeline segment is faded even more
                // so the crossed part of the line clearly stands apart from the remaining route.
                val alpha = if (stop.isBefore) 0.5f else 1f
                timeline.alpha = if (stop.isBefore) 0.25f else 1f
                stopName.alpha = alpha
                stopStatus.alpha = alpha
                stopDepartureTime.alpha = alpha
            }
        }
    }

    companion object {
        const val EXTRA_TRIP_INDEX = "trip_index"
    }
}
