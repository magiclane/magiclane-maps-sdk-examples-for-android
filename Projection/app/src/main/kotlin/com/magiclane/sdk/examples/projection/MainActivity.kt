/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.projection

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.examples.projection.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.projection.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.EAddressField
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.projection.EHemisphere
import com.magiclane.sdk.projection.EProjectionType
import com.magiclane.sdk.projection.Projection
import com.magiclane.sdk.projection.ProjectionBNG
import com.magiclane.sdk.projection.ProjectionGK
import com.magiclane.sdk.projection.ProjectionLAM
import com.magiclane.sdk.projection.ProjectionMGRS
import com.magiclane.sdk.projection.ProjectionService
import com.magiclane.sdk.projection.ProjectionUTM
import com.magiclane.sdk.projection.ProjectionW3W
import com.magiclane.sdk.projection.ProjectionWGS84
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var freeSpacePaddingPx = 0
    private lateinit var projectionAdapter: ProjectionAdapter

    // Captured once at the initial (portrait) layout; used by applyOrientationLayout() to
    // restore the baseline before applying landscape overrides on each rotation.
    private lateinit var portraitConstraintSet: ConstraintSet

    companion object {
        private const val ANIMATION_DURATION_MS = 900
        private const val HIGHLIGHT_ALPHA = 0.75
        private const val HIGHLIGHT_IMAGE_SIZE = 6.0
        private const val UNKNOWN_LANDMARK_NAME = "Unknown"

        // Fraction of screen width occupied by the projection panel in landscape mode.
        private const val LANDSCAPE_PANEL_WIDTH_FRACTION = 0.45f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status bar icons white on the dark toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        EspressoIdlingResource.increment()

        // Clone the layout before any orientation-specific changes so applyOrientationLayout()
        // always has a clean portrait baseline to start from.
        portraitConstraintSet = ConstraintSet().apply { clone(binding.rootView) }
        applyOrientationLayout()

        projectionAdapter = ProjectionAdapter(mutableListOf())
        binding.projectionsList.also {
            it.layoutManager = LinearLayoutManager(this)
            it.addItemDecoration(
                DividerItemDecoration(
                    this,
                    (it.layoutManager as LinearLayoutManager).orientation,
                ),
            )
            it.adapter = projectionAdapter
            it.itemAnimator = null
        }

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        freeSpacePaddingPx = resources.getDimension(R.dimen.padding_40).toInt()

        onBackPressedDispatcher.addCallback(this) {
            if (binding.projectionContainer.isVisible) {
                binding.projectionContainer.isVisible = false
                updateFocusViewport()
                deactivateHighlights()
                return@addCallback
            }
            finish()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout()
        updateFocusViewport()
    }

    private fun applyOrientationLayout() {
        val rootLayout = binding.rootView
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() resets every view's visibility to the state captured at clone
        // time (all views were GONE then), so save and restore the live visibility state first.
        val containerVis = binding.projectionContainer.visibility
        val hintVis = binding.hint.visibility

        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                // Move the panel to the left side, spanning the full height from the toolbar
                // bottom to the screen bottom, at LANDSCAPE_PANEL_WIDTH_FRACTION of screen width.
                val panelWidth = (resources.displayMetrics.widthPixels * LANDSCAPE_PANEL_WIDTH_FRACTION).toInt()
                constrainWidth(R.id.projection_container, panelWidth)
                connect(R.id.projection_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                clear(R.id.projection_container, ConstraintSet.END)
                connect(R.id.projection_container, ConstraintSet.TOP, R.id.toolbar, ConstraintSet.BOTTOM, 0)
                connect(
                    R.id.projection_container,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                    0,
                )
            }
            // Portrait: the baseline ConstraintSet already places the panel in the lower half.
        }.applyTo(rootLayout)

        binding.projectionContainer.visibility = containerVis
        binding.hint.visibility = hintVis

        // Replace the binding-adapter inset listener so that in landscape the panel's right edge
        // — which faces the map interior, not the screen border — gets no right inset applied.
        ViewCompat.setOnApplyWindowInsetsListener(binding.projectionContainer) { v, insets ->
            val sys = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            v.updatePadding(
                left = sys.left,
                right = if (isLandscape) 0 else sys.right,
                bottom = sys.bottom,
            )
            insets
        }
        binding.projectionContainer.requestApplyInsets()
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnUiThread { showDialog(errorMessage) { finish() } }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = { updateFocusViewport() }

        binding.gemSurfaceView.onSurfaceChanged = { _, _ -> updateFocusViewport() }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                // Clear immediately to avoid repeat invocations on subsequent status events.
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }

                EspressoIdlingResource.decrement()
                binding.hint.visibility = View.VISIBLE

                binding.gemSurfaceView.mapView?.let { mapView ->
                    mapView.onTouch = { xy ->
                        EspressoIdlingResource.increment()
                        SdkCall.execute {
                            mapView.cursorScreenPosition = xy
                            val landmark = getSelectedLandmark(mapView)
                            landmark?.let {
                                Util.postOnMain { binding.hint.visibility = View.GONE }
                                showProjectionsForLandmark(
                                    it,
                                    onViewCreated = { highlightLandmarkOnMap(it) },
                                    onViewClosed = { deactivateHighlights() },
                                )
                            }
                        }
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // Returns the landmark at the cursor position, preferring a named map feature over a
    // generic overlay item. Returns null when nothing is selected.
    private fun getSelectedLandmark(mapView: MapView): Landmark? {
        mapView.cursorSelectionLandmarks?.firstOrNull()?.let { return it }

        val selectedOverlay = mapView.cursorSelectionOverlayItems?.firstOrNull() ?: return null
        val overlayCoordinates = selectedOverlay.coordinates ?: return null

        val overlayName = selectedOverlay.name?.takeIf { it.isNotEmpty() }
            ?: selectedOverlay.overlayInfo?.name?.takeIf { it.isNotEmpty() }
            ?: UNKNOWN_LANDMARK_NAME

        return Landmark(
            name = overlayName,
            latitude = overlayCoordinates.latitude,
            longitude = overlayCoordinates.longitude,
        ).apply {
            image = selectedOverlay.image
            description = getLandmarkDescription(mapView, overlayCoordinates)
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        GemSdk.release()
        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        exitProcess(0)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showProjectionsForLandmark(
        landmark: Landmark,
        onViewCreated: (() -> Unit)? = null,
        onViewClosed: (() -> Unit)? = null,
    ) {
        if (landmark.coordinates == null) return

        val details = GemUtil.pairFormatLandmarkDetails(landmark, true)

        // Seed the list with WGS-84 immediately; other projections are appended asynchronously
        // as each conversion completes.
        val wgs84Projection = ProjectionWGS84(landmark.coordinates!!)
        projectionAdapter.dataSet.apply {
            clear()
            add(wgs84Projection)
        }

        for (type in EProjectionType.entries) {
            if (type == EProjectionType.EPR_Wgs84 || type == EProjectionType.EPR_Undefined) continue

            val projection: Projection = when (type) {
                EProjectionType.EPR_WhatThreeWords -> ProjectionW3W().also {
                    // Replace the string resource value with a valid What3Words API token.
                    val token = getString(R.string.what_3_words_token)
                    if (token.isNotEmpty()) it.setToken(token)
                }
                EProjectionType.EPR_Bng -> ProjectionBNG()
                EProjectionType.EPR_Lam -> ProjectionLAM()
                EProjectionType.EPR_Utm -> ProjectionUTM()
                EProjectionType.EPR_Mgrs -> ProjectionMGRS()
                EProjectionType.EPR_Gk -> ProjectionGK()
                else -> continue // unknown future enum value — skip gracefully
            }

            val progressListener = ProgressListener.create(
                onCompleted = onCompleted@{ errorCode, _ ->
                    if (GemError.isError(errorCode)) return@onCompleted
                    projectionAdapter.run {
                        dataSet.add(projection)
                        notifyItemInserted(dataSet.size - 1)
                    }
                },
            )

            ProjectionService.convert(wgs84Projection, projection, progressListener)
        }

        Util.postOnMain {
            binding.landmarkName.text = details.first
            binding.landmarkDescription.text = details.second
            binding.landmarkDescription.isVisible = details.second.isNotEmpty()

            binding.projectionContainer.visibility = View.VISIBLE
            projectionAdapter.notifyDataSetChanged()

            // Update the logo viewport only after the panel has been laid out so its
            // dimensions are known, then notify the caller that the view is ready.
            binding.root.post {
                updateFocusViewport()
                onViewCreated?.invoke()
            }

            binding.closeButton.setOnClickListener {
                binding.projectionContainer.visibility = View.GONE
                updateFocusViewport()
                onViewClosed?.invoke()
            }
        }

        EspressoIdlingResource.decrement()
    }

    private fun highlightLandmarkOnMap(landmark: Landmark) = SdkCall.execute {
        binding.gemSurfaceView.mapView?.let { mapView ->
            val rect = getFreeSpaceRect()

            mapView.deactivateAllHighlights()
            landmark.image = ImageDatabase().getImageById(SdkImages.Core.Search_Results_Pin.value)

            val contour = landmark.getContourGeographicArea()

            val highlightSettings = if ((contour != null) && !contour.isEmpty()) {
                // Landmark has an area (e.g. a country or city boundary) — fit the map to it.
                mapView.centerOnRectArea(
                    contour,
                    zoomLevel = -1,
                    viewRc = rect,
                    Animation(EAnimation.Linear, ANIMATION_DURATION_MS),
                )
                HighlightRenderSettings(
                    EHighlightOptions.ShowContour.value or EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                    Rgba(255, 98, 0, 255),
                    Rgba(255, 98, 0, 255),
                    HIGHLIGHT_ALPHA,
                ).apply { imageSize = HIGHLIGHT_IMAGE_SIZE }
            } else {
                // Point landmark — center the map on its coordinates.
                landmark.coordinates?.let {
                    mapView.centerOnCoordinates(
                        it,
                        -1,
                        rect.center,
                        Animation(EAnimation.Linear, ANIMATION_DURATION_MS),
                        0.0,
                        0.0,
                    )
                }
                HighlightRenderSettings(
                    EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                ).apply { imageSize = HIGHLIGHT_IMAGE_SIZE }
            }

            mapView.activateHighlightLandmarks(landmark, highlightSettings)
        }
    }

    private fun deactivateHighlights() = SdkCall.execute {
        binding.gemSurfaceView.mapView?.deactivateAllHighlights()
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        if (!isActivityAlive()) return

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

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    // Returns the visible map area not covered by UI panels, used to center landmarks
    // so they remain in the unobstructed part of the screen.
    private fun getFreeSpaceRect(): Rect {
        val root = binding.rootView
        val insets =
            ViewCompat.getRootWindowInsets(root)?.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            ) ?: Insets.NONE

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val left: Int
        val top: Int
        val right: Int
        val bottom: Int

        if (isLandscape) {
            // Panel is on the left: shift the free-space left boundary past its right edge.
            left = if (binding.projectionContainer.isVisible) binding.projectionContainer.right else insets.left
            top = maxOf(insets.top, binding.toolbar.bottom)
            right = maxOf(left, root.width - insets.right)
            bottom = maxOf(top, root.height - insets.bottom)
        } else {
            // Panel is at the bottom: clamp the free-space bottom boundary above it.
            left = insets.left
            top = maxOf(insets.top, binding.toolbar.bottom)
            right = maxOf(left, root.width - insets.right)
            val bottomFromInsets = root.height - insets.bottom
            val bottomFromPanel = if (binding.projectionContainer.isVisible) {
                binding.projectionContainer.top
            } else {
                Int.MAX_VALUE
            }
            bottom = maxOf(top, minOf(bottomFromInsets, bottomFromPanel))
        }

        val paddedLeft = (left + freeSpacePaddingPx).coerceAtMost(right)
        val paddedTop = (top + freeSpacePaddingPx).coerceAtMost(bottom)
        val paddedRight = (right - freeSpacePaddingPx).coerceAtLeast(paddedLeft)
        val paddedBottom = (bottom - freeSpacePaddingPx).coerceAtLeast(paddedTop)

        return Rect(paddedLeft, paddedTop, paddedRight, paddedBottom)
    }

    // Adjusts the Magic Lane logo position so it stays inside the visible map area.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurfaceView.mapView?.preferences?.focusViewport = getFocusViewport()
        }
    }

    private fun getFocusViewport(): Rect {
        val root = binding.rootView
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        // root.width/height may be 0 before the first layout pass; fall back to display metrics.
        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return if (isLandscape) {
            // During a layout transition root.width/height can still report portrait values,
            // so normalise: w = long side, h = short side.
            val w = max(width, height)
            val h = min(width, height)

            // Panel is on the left: start the viewport past its right edge.
            val left = if (binding.projectionContainer.isVisible) binding.projectionContainer.right else insets?.left ?: 0
            val top = maxOf(insets?.top ?: 0, binding.toolbar.bottom)
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = if (binding.hint.isVisible) {
                binding.hint.top.coerceAtLeast(top)
            } else {
                (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            Rect(left, top, right, bottom)
        } else {
            val w = min(width, height)
            val h = max(width, height)

            val left = insets?.left ?: 0
            val top = maxOf(insets?.top ?: 0, binding.toolbar.bottom)
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            // Both the projection panel and the hint bar occupy the bottom; use the highest boundary.
            val bottom = when {
                binding.projectionContainer.isVisible -> binding.projectionContainer.top.coerceAtLeast(top)
                binding.hint.isVisible -> binding.hint.top.coerceAtLeast(top)
                else -> (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            Rect(left, top, right, bottom)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun getLandmarkDescription(
        mapView: MapView,
        coordinates: Coordinates,
        isMyPosition: Boolean = false,
    ): String {
        var description = ""
        var descriptionContainsLatLon = false

        // Try progressively larger search radii until a usable address is found.
        val fullAddress = mapView.getClosestAddress(coordinates, 50, false)
        if (fullAddress != null) {
            description = GemUtil.formatLandmarkDetails(fullAddress, true)
        }

        if (description.isEmpty()) {
            val nearbyAddress = mapView.getClosestAddress(coordinates, 300, false)
            description = nearbyAddress?.addressInfo?.getField(EAddressField.City) ?: ""
        }

        if (description.isEmpty()) {
            val farAddress = mapView.getClosestAddress(coordinates, 2500, true)
            val city = farAddress?.addressInfo?.getField(EAddressField.City) ?: ""
            if (city.isNotEmpty()) description = "Near $city"
        }

        if (description.isEmpty()) {
            description = String.format("%.5f, %.5f", coordinates.latitude, coordinates.longitude)
            descriptionContainsLatLon = true
        }

        if (isMyPosition) {
            if (!descriptionContainsLatLon) {
                description += "\nLatitude: ${String.format("%.5f", coordinates.latitude)}"
                description += "\nLongitude: ${String.format("%.5f", coordinates.longitude)}"
            }
            description += "\nAltitude: ${coordinates.altitude.toInt()}m"
        }

        return description
    }

    inner class ProjectionAdapter(val dataSet: MutableList<Projection>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        // Extracts the human-readable name from an enum constant (e.g. "EPR_Utm" → "Utm").
        private fun EProjectionType.displayName(): String = toString().substringAfter("_")

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layout = when (viewType) {
                EProjectionType.EPR_WhatThreeWords.ordinal -> R.layout.one_param_list_item
                EProjectionType.EPR_Lam.ordinal,
                EProjectionType.EPR_Wgs84.ordinal,
                -> R.layout.two_params_list_item

                EProjectionType.EPR_Mgrs.ordinal,
                EProjectionType.EPR_Utm.ordinal,
                -> R.layout.four_params_list_item

                EProjectionType.EPR_Bng.ordinal,
                EProjectionType.EPR_Gk.ordinal,
                -> R.layout.three_params_list_item

                else -> R.layout.two_params_list_item
            }

            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)

            return when (viewType) {
                EProjectionType.EPR_WhatThreeWords.ordinal -> WhatThreeWordsViewHolder(view)
                EProjectionType.EPR_Bng.ordinal -> BngViewHolder(view)
                EProjectionType.EPR_Lam.ordinal -> LamViewHolder(view)
                EProjectionType.EPR_Utm.ordinal -> UtmViewHolder(view)
                EProjectionType.EPR_Mgrs.ordinal -> MgrsViewHolder(view)
                EProjectionType.EPR_Gk.ordinal -> GkViewHolder(view)
                EProjectionType.EPR_Wgs84.ordinal -> Wgs84ViewHolder(view)
                else -> Wgs84ViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder.itemViewType) {
                EProjectionType.EPR_WhatThreeWords.ordinal -> (holder as WhatThreeWordsViewHolder).bind(position)
                EProjectionType.EPR_Bng.ordinal -> (holder as BngViewHolder).bind(position)
                EProjectionType.EPR_Lam.ordinal -> (holder as LamViewHolder).bind(position)
                EProjectionType.EPR_Utm.ordinal -> (holder as UtmViewHolder).bind(position)
                EProjectionType.EPR_Mgrs.ordinal -> (holder as MgrsViewHolder).bind(position)
                EProjectionType.EPR_Gk.ordinal -> (holder as GkViewHolder).bind(position)
                EProjectionType.EPR_Wgs84.ordinal -> (holder as Wgs84ViewHolder).bind(position)
            }
        }

        override fun getItemViewType(position: Int): Int = dataSet[position].type.ordinal

        override fun getItemCount(): Int = dataSet.size

        inner class WhatThreeWordsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val words: TextView = view.findViewById(R.id.words)

            fun bind(position: Int) {
                val item = dataSet[position] as ProjectionW3W
                var name = ""
                var wordsStr = ""
                SdkCall.execute {
                    name = item.type.displayName()
                    wordsStr = String.format("%s %s", getString(R.string.words), item.getWords())
                }
                projectionName.text = name.uppercase()
                words.text = wordsStr
            }
        }

        inner class BngViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val easting: TextView = view.findViewById(R.id.x)
            private val northing: TextView = view.findViewById(R.id.y)
            private val gridReference: TextView = view.findViewById(R.id.zone)

            fun bind(position: Int) {
                val item = dataSet[position] as ProjectionBNG
                var name = ""
                var eastingStr = ""
                var northingStr = ""
                var gridReferenceStr = ""
                SdkCall.execute {
                    name = item.type.displayName()
                    eastingStr = String.format(
                        Locale.getDefault(),
                        "%s %f",
                        getString(R.string.easting),
                        item.getEasting(),
                    )
                    northingStr = String.format(
                        Locale.getDefault(),
                        "%s %f",
                        getString(R.string.northing),
                        item.getNorthing(),
                    )
                    gridReferenceStr = String.format("%s %s", getString(R.string.grid_reference), item.gridReference)
                }
                projectionName.text = name.uppercase()
                easting.text = eastingStr
                northing.text = northingStr
                gridReference.text = gridReferenceStr
            }
        }

        inner class LamViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val x: TextView = view.findViewById(R.id.x)
            private val y: TextView = view.findViewById(R.id.y)

            fun bind(position: Int) {
                val item = dataSet[position] as ProjectionLAM
                var name = ""
                var xStr = ""
                var yStr = ""
                SdkCall.execute {
                    name = item.type.displayName()
                    xStr = String.format(Locale.getDefault(), "%s %f", getString(R.string.x), item.getX())
                    yStr = String.format(Locale.getDefault(), "%s %f", getString(R.string.y), item.getY())
                }
                projectionName.text = name.uppercase()
                x.text = xStr
                y.text = yStr
            }
        }

        inner class UtmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val x: TextView = view.findViewById(R.id.x)
            private val y: TextView = view.findViewById(R.id.y)
            private val zone: TextView = view.findViewById(R.id.zone)
            private val hemisphere: TextView = view.findViewById(R.id.hemisphere)

            fun bind(position: Int) {
                val item = dataSet[position] as ProjectionUTM
                var name = ""
                var xStr = ""
                var yStr = ""
                var zoneStr = ""
                var hemisphereStr = ""
                SdkCall.execute {
                    name = item.type.displayName()
                    xStr = String.format(Locale.getDefault(), "%s %f", getString(R.string.x), item.getX())
                    yStr = String.format(Locale.getDefault(), "%s %f", getString(R.string.y), item.getY())
                    zoneStr = String.format(Locale.getDefault(), "%s %d", getString(R.string.zone), item.getZone())
                    hemisphereStr = String.format(
                        "%s %s",
                        getString(R.string.hemisphere),
                        EHemisphere.entries[item.getHemisphere()],
                    )
                }
                projectionName.text = name.uppercase()
                x.text = xStr
                y.text = yStr
                zone.text = zoneStr
                hemisphere.text = hemisphereStr
            }
        }

        inner class MgrsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val easting: TextView = view.findViewById(R.id.x)
            private val northing: TextView = view.findViewById(R.id.y)
            private val zone: TextView = view.findViewById(R.id.zone)
            private val letters: TextView = view.findViewById(R.id.hemisphere)

            fun bind(position: Int) {
                val item = dataSet[position] as ProjectionMGRS
                var name = ""
                var eastingStr = ""
                var northingStr = ""
                var zoneStr = ""
                var lettersStr = ""
                SdkCall.execute {
                    name = item.type.displayName()
                    eastingStr = String.format(
                        Locale.getDefault(),
                        "%s %06d",
                        getString(R.string.easting),
                        item.getEasting(),
                    )
                    northingStr = String.format(
                        Locale.getDefault(),
                        "%s %06d",
                        getString(R.string.northing),
                        item.getNorthing(),
                    )
                    zoneStr = String.format("%s %s", getString(R.string.zone), item.getZone())
                    lettersStr = String.format("%s %s", getString(R.string.letters), item.getSq100kIdentifier())
                }
                projectionName.text = name.uppercase()
                easting.text = eastingStr
                northing.text = northingStr
                zone.text = zoneStr
                letters.text = lettersStr
            }
        }

        inner class GkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val easting: TextView = view.findViewById(R.id.x)
            private val northing: TextView = view.findViewById(R.id.y)
            private val zone: TextView = view.findViewById(R.id.zone)

            fun bind(position: Int) {
                val item = dataSet[position] as ProjectionGK
                var name = ""
                var eastingStr = ""
                var northingStr = ""
                var zoneStr = ""
                SdkCall.execute {
                    name = item.type.displayName()
                    eastingStr = String.format(
                        Locale.getDefault(),
                        "%s %f",
                        getString(R.string.easting),
                        item.getEasting(),
                    )
                    northingStr = String.format(
                        Locale.getDefault(),
                        "%s %f",
                        getString(R.string.northing),
                        item.getNorthing(),
                    )
                    zoneStr = String.format("%s %s", getString(R.string.zone), item.getZone())
                }
                projectionName.text = name.uppercase()
                easting.text = eastingStr
                northing.text = northingStr
                zone.text = zoneStr
            }
        }

        inner class Wgs84ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val latitude: TextView = view.findViewById(R.id.x)
            private val longitude: TextView = view.findViewById(R.id.y)

            fun bind(position: Int) {
                val item = dataSet[position] as ProjectionWGS84
                var name = ""
                var latitudeStr = ""
                var longitudeStr = ""
                SdkCall.execute {
                    name = item.type.displayName()
                    latitudeStr = String.format(
                        Locale.getDefault(),
                        "%s %f",
                        getString(R.string.lat),
                        item.coordinates.latitude,
                    )
                    longitudeStr = String.format(
                        Locale.getDefault(),
                        "%s %f",
                        getString(R.string.lon),
                        item.coordinates.longitude,
                    )
                }
                projectionName.text = name.uppercase()
                latitude.text = latitudeStr
                longitude.text = longitudeStr
            }
        }
    }
}

object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("ProjectionInstrumentedTestsIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
