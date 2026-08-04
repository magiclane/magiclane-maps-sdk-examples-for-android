/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.recents

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.examples.recents.databinding.ActivityRecentsBinding
import com.magiclane.sdk.examples.recents.databinding.ItemRecentLandmarkBinding
import com.magiclane.sdk.places.LandmarkStoreService
import com.magiclane.sdk.util.SdkCall

class RecentsActivity : AppCompatActivity() {

    // Plain copy of the landmark data, safe to use outside the SDK thread.
    data class RecentItem(val name: String, val latitude: Double, val longitude: Double)

    private lateinit var binding: ActivityRecentsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityRecentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding.toolbar.setNavigationOnClickListener { finish() }

        applyWindowInsets()

        val items = loadRecents()
        if (items.isEmpty()) {
            binding.recentsList.visibility = View.GONE
            binding.emptyText.visibility = View.VISIBLE
        } else {
            binding.recentsList.apply {
                layoutManager = LinearLayoutManager(this@RecentsActivity)
                addItemDecoration(
                    DividerItemDecoration(this@RecentsActivity, DividerItemDecoration.VERTICAL),
                )
                adapter = RecentLandmarksAdapter(items)
            }
        }
    }

    // Keeps the list content clear of system bars and display cutouts.
    // The toolbar handles the top inset itself, through its binding adapters.
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.recentsList) { view, insets ->
            val systemInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.updatePadding(
                left = systemInsets.left,
                right = systemInsets.right,
                bottom = systemInsets.bottom,
            )
            insets
        }
    }

    // Reads the "Recents" store landmarks, most recently added first.
    private fun loadRecents(): List<RecentItem> = SdkCall.execute {
        val store = LandmarkStoreService().createLandmarkStore(MainActivity.RECENTS_STORE_NAME)?.first
        store?.getLandmarks()?.map { landmark ->
            RecentItem(
                landmark.name ?: "",
                landmark.coordinates?.latitude ?: 0.0,
                landmark.coordinates?.longitude ?: 0.0,
            )
        }?.reversed()
    } ?: emptyList()

    private class RecentLandmarksAdapter(
        private val items: List<RecentItem>,
    ) : RecyclerView.Adapter<RecentLandmarksAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemRecentLandmarkBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            ItemRecentLandmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.name.text = item.name
            holder.binding.coordinates.text = holder.itemView.context.getString(
                R.string.coordinates_format,
                item.latitude,
                item.longitude,
            )
        }
    }
}
