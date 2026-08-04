/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.publictransitrouting

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.publictransitrouting.databinding.ActivityRoutesBinding

/**
 * Stand-alone "Routes" view: the full list of calculated public-transit routes with the
 * currently displayed route selected, plus Earlier / Later actions that trigger a recalculation.
 */
class PTRoutesActivity : AppCompatActivity(), PTRouteSession.CalculationListener {

    /** List orderings offered by the sorting menu (as in Magic Earth). */
    private enum class SortMode(val menuItemId: Int) {
        Arrival(R.id.sort_arrival),
        Departure(R.id.sort_departure),
        Duration(R.id.sort_duration),
    }

    private lateinit var binding: ActivityRoutesBinding
    private lateinit var adapter: RouteRowAdapter
    private var sortMode = SortMode.Arrival

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityRoutesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.sortButton.setOnClickListener { showSortMenu() }

        adapter = RouteRowAdapter(
            onRouteTap = { routeIndex ->
                // Selecting a route shows it on the map and returns there (as in Magic Earth).
                PTRouteSession.controller?.selectRoute(routeIndex)
                finish()
            },
            onEarlierTap = {
                if (!PTRouteSession.isCalculating) PTRouteSession.controller?.requestEarlier()
            },
            onLaterTap = {
                if (!PTRouteSession.isCalculating) PTRouteSession.controller?.requestLater()
            },
        )
        binding.routesList.adapter = adapter

        PTRouteSession.addListener(this)
        if (PTRouteSession.isCalculating) onCalculationStarted()

        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        PTRouteSession.removeListener(this)
    }

    override fun onCalculationStarted() {
        binding.progressBar.visibility = View.VISIBLE
        binding.routesList.alpha = 0.4f
    }

    override fun onCalculationCompleted(errorCode: Int) {
        binding.progressBar.visibility = View.GONE
        binding.routesList.alpha = 1f

        if (errorCode == GemError.NoError) {
            refresh()
        } else {
            // The map screen shows the error dialog; close this view (as in Magic Earth).
            finish()
        }
    }

    private fun refresh() {
        adapter.submit(sortedItems(), PTRouteSession.selectedRouteIndex)
        binding.timeAnchorText.text = timeAnchorText()
    }

    private fun sortedItems(): List<PTRouteItem> = when (sortMode) {
        SortMode.Arrival -> PTRouteSession.items.sortedBy { it.arrivalMillis }
        SortMode.Departure -> PTRouteSession.items.sortedBy { it.departureMillis }
        SortMode.Duration -> PTRouteSession.items.sortedBy { it.durationSeconds }
    }

    private fun showSortMenu() {
        PopupMenu(this, binding.sortButton).apply {
            menuInflater.inflate(R.menu.menu_routes_sort, menu)
            menu.findItem(sortMode.menuItemId)?.isChecked = true
            setOnMenuItemClickListener { item ->
                val selected = SortMode.entries.firstOrNull { it.menuItemId == item.itemId }
                    ?: return@setOnMenuItemClickListener false
                sortMode = selected
                refresh()
                true
            }
            show()
        }
    }

    /** "Depart now" / "Depart at 9:35" / "Arrive at 10:00, Aug 1". */
    private fun timeAnchorText(): String {
        val millis = PTSettings.customTimeMillis
            ?: return getString(R.string.depart_now)

        return if (PTSettings.timeMode == PTSettings.TimeMode.Depart) {
            getString(R.string.depart_at, WallClock.timeAndDateText(millis))
        } else {
            getString(R.string.arrive_at, WallClock.timeAndDateText(millis))
        }
    }
}
