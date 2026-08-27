/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.markerclustering

import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.RectangleGeographicArea
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EMarkerMatchType
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerMatch
import com.magiclane.sdk.d3scene.MarkerMatchList
import com.magiclane.sdk.examples.markerclustering.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.markerclustering.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.markerclustering.databinding.ItemMarkerBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.util.Locale
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    companion object {
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

        // Continental-US start view, so the whole campground spread is visible.
        // NOTE: kept as primitives, not a Coordinates instance — a native-backed
        // Coordinates must not be constructed in <clinit> (before the SDK is up).
        private const val US_LAT = 39.5
        private const val US_LON = -98.35
        private const val US_ZOOM = 12

        // eurocampings scheme: bookable = red, info-only = green.
        private const val BOOKABLE_COLOR = 0xFFDD3137.toInt()
        private const val INFO_ONLY_COLOR = 0xFF007228.toInt()

        // Below this lat/lon span (~200 m) a cluster's members can't be visually
        // separated by zooming (their pins would overlap), so they're listed instead.
        private const val CO_LOCATED_SPAN_DEG = 0.002
    }

    private lateinit var binding: ActivityMainBinding

    // Two-collection clustering renderer (built lazily; needs the app context for icons).
    private val renderer: CampsitePOIRenderer by lazy {
        CampsitePOIRenderer(CampsiteMarkerIcons(applicationContext))
    }

    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // No toolbar — the map runs edge-to-edge under the status bar, so use dark
        // status-bar icons that read against the light map.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        applyBottomInsets(binding.loadButton)
        setupInfoCardLayout()

        binding.loadButton.setOnClickListener { loadCampsites() }
        binding.closeButton.setOnClickListener { binding.infoCard.isVisible = false }

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SdkSettings.onApiTokenRejected = {}
        GemSdk.release()
        exitProcess(0)
    }

    // The activity handles rotation itself (configChanges), so re-place the info
    // panel for the new orientation instead of relying on a layout-land variant.
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        layoutInfoCard(currentInsets())
    }

    private fun setupInfoCardLayout() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.infoCard) { _, insets ->
            layoutInfoCard(insets.getInsets(SYSTEM_INSET_TYPES))
            insets
        }
        layoutInfoCard(currentInsets())
    }

    private fun currentInsets(): androidx.core.graphics.Insets =
        ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)
            ?: androidx.core.graphics.Insets.NONE

    /** Bottom, full width and symmetric in portrait; bottom-left with a constrained
     *  width in landscape. Only landscape adds the left inset (so portrait stays centred). */
    private fun layoutInfoCard(insets: androidx.core.graphics.Insets) {
        val landscape =
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val base = dp(12)
        binding.infoCard.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
            bottomMargin = base + insets.bottom
            if (landscape) {
                endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                width = dp(340)
                marginStart = base + insets.left
            } else {
                endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                width = 0 // MATCH_CONSTRAINT → fills start..end
                marginStart = base
                marginEnd = base
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // SDK listeners
    // -----------------------------------------------------------------------------------------

    private fun registerSdkListeners() {
        binding.gemSurface.onSdkInitFailed = { error ->
            val message = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(message) { finish() } }
        }

        binding.gemSurface.onDefaultMapViewCreated = { mapView ->
            // This callback already runs on the SDK thread (the native call lock is
            // held), so map methods are invoked DIRECTLY here — wrapping them in
            // SdkCall.execute { } would try to re-acquire that non-reentrant lock
            // and deadlock the SDK thread (black map + input ANR).
            mapView.centerOnCoordinates(Coordinates(US_LAT, US_LON), US_ZOOM)

            mapView.onTouch = { xy ->
                SdkCall.execute {
                    mapView.cursorScreenPosition = xy
                    val markers = mapView.cursorSelectionMarkers
                    if (!markers.isNullOrEmpty()) {
                        handleSelectedMarkers(mapView, markers)
                    } else {
                        runOnAliveUi { binding.infoCard.isVisible = false }
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Loading & rendering
    // -----------------------------------------------------------------------------------------

    private fun loadCampsites() {
        if (loaded) return
        val mapView = binding.gemSurface.mapView ?: return
        loaded = true

        binding.loadButton.isVisible = false
        binding.progressBar.isVisible = true

        // Parse the GeoJSON off the main thread, then hand the descriptors to the
        // renderer on the SDK thread via execute (fire-and-forget, so map rendering
        // and native finalizers are never starved by a held call lock).
        lifecycleScope.launch(Dispatchers.IO) {
            val descriptors = CampsiteGeoJSONLoader.loadFromAssets(applicationContext)
            SdkCall.execute {
                renderer.syncPOIs(mapView, descriptors)
                runOnAliveUi { binding.progressBar.isVisible = false }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Touch / selection
    // -----------------------------------------------------------------------------------------

    // Called on the SDK thread.
    private fun handleSelectedMarkers(mapView: MapView, markers: MarkerMatchList) {
        // A tap can hit several overlapping collections (the clustered layer plus the
        // detail layer at the same spot). A cluster is a CoordinateGroup match → fit
        // the map to the cluster's members to split it apart, instead of opening the card.
        val groupMatch = markers.firstOrNull { it.type == EMarkerMatchType.CoordinateGroup }
        if (groupMatch != null) {
            // The clustered layer carries buildPointsGroupConfig, so prefer it for
            // enumerating the cluster's members; fall back to the group match.
            val clusteredMatch = markers.firstOrNull {
                it.markerCollection?.name?.endsWith("-clustered") == true
            } ?: groupMatch
            zoomIntoGroup(mapView, clusteredMatch, groupMatch.coordinates)
            runOnAliveUi { binding.infoCard.isVisible = false }
            return
        }

        // Otherwise a single campsite → centre the map on it and show the info panel.
        // Prefer the match carrying our JSON metadata (the detail-layer pin) over the
        // transparent cluster-layer marker.
        val marker = (
            markers.firstOrNull { it.marker?.name?.contains("\"bookable\"") == true }
                ?: markers.firstOrNull()
            )?.marker ?: return
        // We're already on the SDK thread here, so centre directly.
        marker.getCoordinates()?.firstOrNull()?.let { coordinates -> centerOn(mapView, coordinates) }
        runOnAliveUi { showInfoPanel(CampsiteInfo.from(marker)) }
    }

    /** Animates the map so [coordinates] sits at the centre of the screen, keeping
     *  the current zoom by default. Must run on the SDK thread. Passing an explicit
     *  screen centre point avoids centring on the last-tapped cursor position. */
    private fun centerOn(mapView: MapView, coordinates: Coordinates, zoom: Int = mapView.zoomLevel) {
        val screenCenter = Xy(binding.gemSurface.width / 2, binding.gemSurface.height / 2)
        mapView.centerOnCoordinates(coordinates, zoom, screenCenter, Animation(EAnimation.Linear, 450))
    }

    /** Fits the map to the bounding box of the cluster's child markers, zooming
     *  exactly enough to break the cluster apart. Falls back to a fixed zoom-in
     *  step toward the cluster centre when the members can't be enumerated or are
     *  co-located. Runs on the SDK thread (called from the onTouch handler). */
    private fun zoomIntoGroup(mapView: MapView, match: MarkerMatch, fallbackCenter: Coordinates?) {
        // Enumerate the cluster's member markers (the representative head + its components).
        val members = ArrayList<Marker>()
        val marker = match.marker
        val collection = match.markerCollection
        if (marker != null && collection != null) {
            val head = collection.getPointsGroupHead(marker.id) ?: marker
            members.add(head)
            collection.getPointsGroupComponents(head.id)?.let { members.addAll(it) }
        }
        val coords = members.flatMap { it.getCoordinates() ?: emptyList() }

        if (coords.size < 2) {
            val target = (mapView.zoomLevel + 8).coerceAtMost(100)
            val center = match.coordinates ?: fallbackCenter ?: coords.firstOrNull() ?: return
            mapView.centerOnCoordinates(center, target, null, Animation(EAnimation.Linear, 450))
            return
        }

        val lats = coords.map { it.latitude }
        val lons = coords.map { it.longitude }
        val latSpan = lats.max() - lats.min()
        val lonSpan = lons.max() - lons.min()

        // Co-located members can't be pulled apart by zooming (their pins overlap
        // even at max zoom), so present them as a pickable list instead.
        if (latSpan < CO_LOCATED_SPAN_DEG && lonSpan < CO_LOCATED_SPAN_DEG) {
            val infos = members.map { CampsiteInfo.from(it) }.distinctBy { it.id }
            runOnAliveUi { showClusterList(mapView, infos) }
            return
        }

        // Otherwise fit the map to the members' bounding box to split the cluster.
        val padLat = latSpan * 0.15 + 0.0005
        val padLon = lonSpan * 0.15 + 0.0005
        val area = RectangleGeographicArea(
            Coordinates(lats.max() + padLat, lons.min() - padLon), // topLeft  (maxLat, minLon)
            Coordinates(lats.min() - padLat, lons.max() + padLon), // bottomRight (minLat, maxLon)
        )
        // zoomLevel -1 = automatic zoom-to-fit.
        mapView.centerOnRectArea(area, -1, viewportRect(), Animation(EAnimation.Linear, 600))
    }

    private fun viewportRect(): Rect = Rect(0, 0, binding.gemSurface.width, binding.gemSurface.height)

    /** Presents a co-located cluster's members in a bottom sheet. Picking one
     *  centres the map on it and opens its info card. Runs on the UI thread. */
    private fun showClusterList(mapView: MapView, campsites: List<CampsiteInfo>) {
        if (!isActivityAlive || campsites.isEmpty()) return

        val dialog = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        container.addView(
            com.google.android.material.textview.MaterialTextView(this).apply {
                text = getString(R.string.campsites_here, campsites.size)
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(16), dp(4), dp(16), dp(8))
            },
        )

        val rowBackground = TypedValue().let {
            theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
            it.resourceId
        }

        for (info in campsites) {
            val row = ItemMarkerBinding.inflate(layoutInflater, container, false)
            row.markerIcon.setColorFilter(if (info.isBookable) BOOKABLE_COLOR else INFO_ONLY_COLOR)
            row.markerName.text = info.displayName
            row.markerDetail.text = buildString {
                append(getString(if (info.isBookable) R.string.bookable else R.string.info_only))
                info.coordinateText?.let { append("  ·  ").append(it) }
            }
            row.markerDetail.isVisible = true
            row.infoButton.isVisible = false
            row.root.setBackgroundResource(rowBackground)
            row.root.setOnClickListener {
                dialog.dismiss()
                showInfoPanel(info)
                val lat = info.latitude
                val lon = info.longitude
                if (lat != null && lon != null) {
                    SdkCall.execute { centerOn(mapView, Coordinates(lat, lon), zoom = 80) }
                }
            }
            container.addView(row.root)
        }

        dialog.setContentView(ScrollView(this).apply { addView(container) })
        dialog.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Shows a tapped campsite's details in the left-side info panel. */
    private fun showInfoPanel(info: CampsiteInfo) {
        binding.infoTitle.text = info.displayName
        binding.infoStatus.text = getString(if (info.isBookable) R.string.bookable else R.string.info_only)
        binding.infoCoords.text = info.coordinateText ?: ""
        binding.infoCoords.isVisible = info.coordinateText != null
        binding.bookableDot.backgroundTintList =
            android.content.res.ColorStateList.valueOf(if (info.isBookable) BOOKABLE_COLOR else INFO_ONLY_COLOR)
        binding.infoCard.isVisible = true
    }

    // -----------------------------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------------------------

    // Adds the bottom system-bar inset as extra bottom margin so edge-to-edge content
    // clears the navigation bar.
    private fun applyBottomInsets(view: android.view.View) {
        val baseMargin = (view.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bottom = insets.getInsets(SYSTEM_INSET_TYPES).bottom
            v.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> { bottomMargin = baseMargin + bottom }
            insets
        }
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        if (!isActivityAlive) return
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.error)
            message.text = text
            button.setOnClickListener {
                onDismiss?.invoke()
                dialog.dismiss()
            }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            show()
        }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive) block() }
    }

    private val isActivityAlive: Boolean
        get() = !isFinishing && !isDestroyed

    // -----------------------------------------------------------------------------------------
    // Data model
    // -----------------------------------------------------------------------------------------

    private data class CampsiteInfo(
        val id: Long,
        val name: String,
        val isBookable: Boolean,
        val latitude: Double?,
        val longitude: Double?,
    ) {
        val displayName: String
            get() = name.ifEmpty { "Campsite #$id" }

        val coordinateText: String?
            get() = if (latitude != null && longitude != null) {
                String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
            } else {
                null
            }

        companion object {
            // Marker names are stringified JSON carrying id / name / bookable
            // (see CampsiteMarkerDescriptor.markerName).
            fun from(marker: Marker): CampsiteInfo {
                val coordinate = marker.getCoordinates()?.firstOrNull()
                var name = ""
                var bookable = false
                runCatching {
                    val root = JSONObject(marker.name)
                    val src = if (root.has("properties")) root.getJSONObject("properties") else root
                    name = src.optString("name").takeIf { it.isNotEmpty() && it != "null" } ?: ""
                    bookable = src.optBoolean("bookable", false)
                }
                return CampsiteInfo(
                    id = marker.id,
                    name = name,
                    isBookable = bookable,
                    latitude = coordinate?.latitude,
                    longitude = coordinate?.longitude,
                )
            }
        }
    }
}
