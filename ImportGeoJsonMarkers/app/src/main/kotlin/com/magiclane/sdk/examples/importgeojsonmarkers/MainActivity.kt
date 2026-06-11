/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.importgeojsonmarkers

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.EMarkerLabelingMode
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.d3scene.MarkerMatchList
import com.magiclane.sdk.examples.importgeojsonmarkers.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.importgeojsonmarkers.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.importgeojsonmarkers.databinding.ItemMarkerBinding
import com.magiclane.sdk.examples.importgeojsonmarkers.databinding.MarkerInfoDialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.EImageFileFormat
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import java.io.ByteArrayOutputStream
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    // -----------------------------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------------------------

    companion object {
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

        private const val MARKER_IMAGE_SIZE_MM = 6.0
        private const val LABEL_GROUP_TEXT_SIZE_MM = 2.4

        // Maximum fraction of screen height the marker list occupies in portrait before scrolling.
        private const val PORTRAIT_MAX_HEIGHT_RATIO = 0.4

        // Minimum item count before the portrait list becomes scrollable.
        private const val SCROLLABLE_THRESHOLD = 3

        // In landscape, panels with more items than this become full-height and scrollable.
        private const val LANDSCAPE_WRAP_THRESHOLD = 2

        // Fraction of screen width occupied by the marker panel in landscape.
        private const val LANDSCAPE_PANEL_WIDTH_RATIO = 0.45
    }

    // -----------------------------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------------------------

    private lateinit var binding: ActivityMainBinding
    private val markerAdapter = MarkerAdapter()

    // Extra properties (description, image URL) keyed by "lat,lon" — populated from the
    // GeoJSON file because the SDK only exposes marker name / coordinates at selection time.
    private var markerExtraPropsMap: Map<String, MarkerExtraProperties> = emptyMap()

    private data class MarkerExtraProperties(val description: String?, val imageUrl: String?)

    // Resolved once from the theme; used as the list-item selection highlight colour.
    private val selectionColor: Int by lazy {
        TypedValue().let { tv ->
            theme.resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, tv, true)
            tv.data
        }
    }

    // -----------------------------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding.markerList.layoutManager = LinearLayoutManager(this)
        binding.markerList.adapter = markerAdapter
        // Disable the crossfade animation on notifyItemChanged so the selection highlight
        // updates instantly without a ghost view flickering at the wrong width in portrait.
        (binding.markerList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        markerAdapter.onItemClick = { item -> highlightMarkerOnMap(item) }
        markerAdapter.onInfoClick = { item -> showInfoDialog(item) }

        setupPanelInsetsListener()

        onBackPressedDispatcher.addCallback(this) {
            if (binding.markerPanel.isVisible) {
                binding.markerPanel.isVisible = false
                markerAdapter.clearSelection()
                SdkCall.execute { binding.gemSurface.mapView?.deactivateAllHighlights() }
                updateFocusViewport()
            } else {
                finish()
            }
        }

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!binding.markerPanel.isVisible) return

        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        val count = markerAdapter.itemCount
        applyPanelConstraints(isLandscape, count)
        if (!isLandscape) updatePortraitListHeight(count)
        binding.markerPanel.post { if (isActivityAlive) updateFocusViewport() }
    }

    // -----------------------------------------------------------------------------------------
    // SDK listeners
    // -----------------------------------------------------------------------------------------

    private fun registerSdkListeners() {
        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(errorMessage) { finish() } }
        }

        binding.gemSurface.onDefaultMapViewCreated = { mapView ->
            updateFocusViewport()

            mapView.onTouch = { xy ->
                SdkCall.execute {
                    mapView.cursorScreenPosition = xy
                    val markers = mapView.cursorSelectionMarkers
                    if (!markers.isNullOrEmpty()) {
                        handleSelectedMarkers(markers)
                    } else {
                        mapView.deactivateAllHighlights()
                        runOnAliveUi {
                            binding.markerPanel.isVisible = false
                            markerAdapter.clearSelection()
                            updateFocusViewport()
                        }
                    }
                }
            }
        }

        binding.gemSurface.onDrawFrameCustom = {
            binding.gemSurface.mapView?.let {
                importGeoJsonMarkers(it)
            }
            binding.gemSurface.onDrawFrameCustom = null
        }

        binding.gemSurface.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
    }

    // -----------------------------------------------------------------------------------------
    // Map viewport
    // -----------------------------------------------------------------------------------------

    // Shrinks the SDK's focus rect to the portion of the map not hidden by UI panels, so the
    // Magic Lane logo, scale bar, and auto-zoom stay within the visible area.
    // Called from the SDK thread (SdkCall.runSynced); the UI property reads are intentional
    // read-only accesses that don't require the main thread.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView = binding.gemSurface.mapView ?: return@runSynced
            val viewport = mapView.viewport ?: return@runSynced
            val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

            val w = viewport.width
            val h = viewport.height
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val panelVisible = binding.markerPanel.isVisible

            val left = if (isLandscape && panelVisible) {
                binding.markerPanel.right.takeIf { it > 0 } ?: (insets?.left ?: 0)
            } else {
                insets?.left ?: 0
            }
            val top = insets?.top ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left + 1)
            val bottom = if (!isLandscape && panelVisible) {
                binding.markerPanel.top.takeIf { it > 0 } ?: ((h - (insets?.bottom ?: 0)).coerceAtLeast(top + 1))
            } else {
                (h - (insets?.bottom ?: 0)).coerceAtLeast(top + 1)
            }
            mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
            // Force an immediate redraw so logo/overlay positions update without a map gesture.
            mapView.invalidate()
        }
    }

    // -----------------------------------------------------------------------------------------
    // GeoJSON import
    // -----------------------------------------------------------------------------------------

    private fun importGeoJsonMarkers(mapView: MapView?) {
        mapView ?: return

        // File reading, JSON parsing, and bitmap rendering are all blocking/CPU-heavy; run them
        // on the IO dispatcher so the SDK thread (caller) and the main thread stay unblocked.
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { binding.progressBar.isVisible = true }

            try {
                val geoJsonBytes = applicationContext.assets.open("campsites.geojson").readBytes()
                if (geoJsonBytes.isEmpty()) return@launch

                // Pre-parse the GeoJSON for description / image URL keyed by coordinates.
                // These fields are not forwarded by the SDK's Marker object, so we look them up
                // when a marker is selected using its geographic position as the key.
                markerExtraPropsMap = buildMarkerExtraPropsMap(geoJsonBytes)

                val brownColor = ResourcesCompat.getColor(resources, R.color.camping_icon_brown, theme)
                val markerImage = loadVectorImage(R.drawable.tent_icon, brownColor)
                val clusterImage = loadVectorImage(R.drawable.red_circle_shape_icon)

                // Hand off to the SDK thread for map registration once all assets are ready.
                SdkCall.runSynced {
                    val renderingSettings = MarkerCollectionRenderSettings(markerImage).apply {
                        imageSize = MARKER_IMAGE_SIZE_MM

                        lowDensityPointsGroupImage = clusterImage
                        mediumDensityPointsGroupImage = clusterImage
                        highDensityPointsGroupImage = clusterImage

                        labelGroupTextColor = Rgba.white()
                        labelGroupTextSize = LABEL_GROUP_TEXT_SIZE_MM

                        labelingMode = EMarkerLabelingMode.Group.value or
                            EMarkerLabelingMode.GroupCenter.value or
                            EMarkerLabelingMode.TextCentered.value
                        buildPointsGroupConfig = true
                    }

                    val result = mapView.addGeoJsonAsMarkerCollection(
                        data = DataBuffer(geoJsonBytes),
                        name = "Campsites",
                        listener = ProgressListener(),
                        importPolygonAsArea = false,
                    )

                    result?.first?.forEach { markerCollection ->
                        mapView.preferences?.markers?.add(markerCollection, renderingSettings)
                    }
                }
            } finally {
                withContext(Dispatchers.Main) { binding.progressBar.isVisible = false }
            }
        }
    }

    private fun buildMarkerExtraPropsMap(geoJsonBytes: ByteArray): Map<String, MarkerExtraProperties> {
        val map = mutableMapOf<String, MarkerExtraProperties>()
        try {
            val features = org.json.JSONObject(String(geoJsonBytes)).optJSONArray("features")
                ?: return map
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                if (coords.length() < 2) continue
                // GeoJSON coordinate order is [longitude, latitude].
                val lon = coords.optDouble(0, Double.NaN)
                val lat = coords.optDouble(1, Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue
                val props = feature.optJSONObject("properties") ?: continue
                val description = props.optString("description").takeIf { it.isNotEmpty() && it != "null" }
                val imageUrl = props.optString("image").takeIf { it.isNotEmpty() && it != "null" }
                if (description != null || imageUrl != null) {
                    // 5 decimal places ≈ 1 m precision — sufficient to uniquely identify a marker.
                    val key = String.format(java.util.Locale.US, "%.5f,%.5f", lat, lon)
                    map[key] = MarkerExtraProperties(description, imageUrl)
                }
            }
        } catch (_: Exception) { }
        return map
    }

    private fun loadVectorImage(drawableRes: Int, tintColor: Int? = null): Image? {
        val bitmap = renderVectorToBitmap(drawableRes, tintColor) ?: return null
        return Image.produceWithDataBuffer(DataBuffer(bitmapToPngBytes(bitmap)), EImageFileFormat.Png)
    }

    private fun renderVectorToBitmap(drawableRes: Int, tintColor: Int? = null): Bitmap? {
        val drawable = ResourcesCompat.getDrawable(resources, drawableRes, theme) ?: return null
        if (tintColor != null) {
            drawable.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        }
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    // -----------------------------------------------------------------------------------------
    // Touch / marker selection
    // -----------------------------------------------------------------------------------------

    private fun handleSelectedMarkers(markers: MarkerMatchList) {
        val match = markers.firstOrNull() ?: return
        val marker = match.marker ?: return
        val collection = match.markerCollection ?: return

        // A tapped group may be a cluster: gather the representative head and all its components.
        val candidates = buildList {
            collection.getPointsGroupHead(marker.id)?.let(::add)
            collection.getPointsGroupComponents(marker.id)?.let(::addAll)
        }

        // Deduplicate by marker ID (head and components can overlap) and build display items.
        val seenIds = mutableSetOf<Long>()
        val items = candidates.mapNotNull { m ->
            if (seenIds.add(m.id)) MarkerInfo.from(m, markerExtraPropsMap) else null
        }

        binding.gemSurface.mapView?.deactivateAllHighlights()
        showMarkerPanel(items)
    }

    private fun highlightMarkerOnMap(item: MarkerInfo) {
        val lat = item.latitude ?: return
        val lon = item.longitude ?: return
        val mapView = binding.gemSurface.mapView ?: return
        // Capture free-space center on the main thread before crossing into the SDK thread.
        val center = getFreeSpaceCenter()

        SdkCall.execute {
            mapView.deactivateAllHighlights()

            val coordinates = Coordinates(lat, lon)
            val landmark = Landmark("", coordinates).also { lm ->
                lm.image = ImageDatabase().getImageById(SdkImages.Core.Search_Results_Pin.value)
            }
            val highlightSettings = HighlightRenderSettings(
                EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
            ).also { it.imageSize = 6.0 }

            mapView.centerOnCoordinates(coordinates, -1, center, Animation(EAnimation.Linear, 900), 0.0, 0.0)
            mapView.activateHighlightLandmarks(landmark, highlightSettings)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Marker panel — layout & visibility
    // -----------------------------------------------------------------------------------------

    // One-time setup: a single WindowInsets listener that re-runs whenever orientation changes
    // so both portrait (bottom-only) and landscape (left + bottom) insets are always correct.
    private fun setupPanelInsetsListener() {
        val listPadV = binding.markerList.paddingTop // 8 dp from XML, preserved across resets

        ViewCompat.setOnApplyWindowInsetsListener(binding.markerPanel) { view, insets ->
            val bars = insets.getInsets(SYSTEM_INSET_TYPES)
            val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            // In landscape the panel background extends edge-to-edge; nav-bar padding lives on the list.
            view.setPadding(0, 0, 0, if (landscape) 0 else bars.bottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.markerList) { view, insets ->
            val bars = insets.getInsets(SYSTEM_INSET_TYPES)
            val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            view.setPadding(
                if (landscape) bars.left else 0,
                listPadV,
                0,
                listPadV + if (landscape) bars.bottom else 0,
            )
            insets
        }
    }

    // Applies ConstraintLayout rules that depend on orientation. Called when the panel is first
    // shown and again whenever the orientation changes while it is visible.
    private fun applyPanelConstraints(isLandscape: Boolean, itemCount: Int) {
        val root = binding.root as ConstraintLayout
        val cs = ConstraintSet().also { it.clone(root) }

        if (isLandscape) {
            val panelWidth = (resources.displayMetrics.widthPixels * LANDSCAPE_PANEL_WIDTH_RATIO).toInt()
            cs.constrainWidth(R.id.markerPanel, panelWidth)
            cs.connect(R.id.markerPanel, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            cs.connect(R.id.markerPanel, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            cs.clear(R.id.markerPanel, ConstraintSet.END)

            if (itemCount > LANDSCAPE_WRAP_THRESHOLD) {
                cs.constrainHeight(R.id.markerPanel, ConstraintSet.MATCH_CONSTRAINT)
                cs.connect(R.id.markerPanel, ConstraintSet.TOP, R.id.toolbar, ConstraintSet.BOTTOM)
                binding.markerList.updateLayoutParams { height = ViewGroup.LayoutParams.MATCH_PARENT }
            } else {
                cs.constrainHeight(R.id.markerPanel, ConstraintSet.WRAP_CONTENT)
                cs.clear(R.id.markerPanel, ConstraintSet.TOP)
                binding.markerList.updateLayoutParams { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            }
        } else {
            cs.constrainWidth(R.id.markerPanel, ConstraintSet.MATCH_CONSTRAINT)
            cs.connect(R.id.markerPanel, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            cs.connect(R.id.markerPanel, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            cs.constrainHeight(R.id.markerPanel, ConstraintSet.WRAP_CONTENT)
            cs.clear(R.id.markerPanel, ConstraintSet.TOP)
            cs.connect(R.id.markerPanel, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        }

        cs.applyTo(root)
        // Re-dispatch insets so the listeners update padding for the new orientation.
        binding.markerPanel.requestApplyInsets()
    }

    private fun showMarkerPanel(items: List<MarkerInfo>) {
        runOnAliveUi {
            if (items.isEmpty()) {
                binding.markerPanel.isVisible = false
                return@runOnAliveUi
            }

            markerAdapter.submitList(items)

            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            applyPanelConstraints(isLandscape, items.size)
            if (!isLandscape) updatePortraitListHeight(items.size)

            binding.markerPanel.isVisible = true
            binding.markerPanel.post { if (isActivityAlive) updateFocusViewport() }

            // For a single-marker result, jump straight to highlighting it on the map.
            if (items.size == 1) {
                binding.markerPanel.post { if (isActivityAlive) highlightMarkerOnMap(items[0]) }
            }
        }
    }

    private fun updatePortraitListHeight(itemCount: Int) {
        val maxHeight = (resources.displayMetrics.heightPixels * PORTRAIT_MAX_HEIGHT_RATIO).toInt()
        binding.markerList.updateLayoutParams {
            height = if (itemCount > SCROLLABLE_THRESHOLD) maxHeight else ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    // Must be called on the main thread — reads live view dimensions.
    private fun getFreeSpaceCenter(): Xy {
        val gemW = binding.gemSurface.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val gemH = binding.gemSurface.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)
        val rightInset = insets?.right ?: 0
        val toolbarBottom = binding.toolbar.bottom
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return if (isLandscape) {
            val panelRight = if (binding.markerPanel.isVisible) binding.markerPanel.right else 0
            val freeRight = (gemW - rightInset).coerceAtLeast(panelRight + 1)
            Xy((panelRight + freeRight) / 2, (toolbarBottom + gemH) / 2)
        } else {
            val panelTop = if (binding.markerPanel.isVisible) binding.markerPanel.top else gemH
            val freeRight = (gemW - rightInset).coerceAtLeast(1)
            Xy(freeRight / 2, toolbarBottom + (panelTop - toolbarBottom) / 2)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Dialogs
    // -----------------------------------------------------------------------------------------

    private fun showInfoDialog(item: MarkerInfo) {
        if (!isActivityAlive) return
        val dialog = BottomSheetDialog(this)
        val dialogBinding = MarkerInfoDialogLayoutBinding.inflate(layoutInflater).apply {
            infoTitle.text = item.displayName
            item.description?.let { infoDescription.text = it.toHtmlSpanned() }

            closeButton.setOnClickListener { dialog.dismiss() }

            // Load the campsite photo on a background thread; update the view when it arrives.
            item.imageUrl?.let { url ->
                loadImageFromUrl(url) { bitmap ->
                    if (bitmap != null && isActivityAlive) {
                        infoImage.setImageBitmap(bitmap)
                        infoImage.isVisible = true
                    }
                }
            }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = true
            setCancelable(true)
            setContentView(dialogBinding.root)
            show()
        }
    }

    private fun loadImageFromUrl(url: String, onLoaded: (Bitmap?) -> Unit) {
        Thread {
            val bitmap = try {
                java.net.URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
            Util.postOnMain { onLoaded(bitmap) }
        }.start()
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

    // -----------------------------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------------------------

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive) block() }
    }

    private val isActivityAlive: Boolean
        get() = !isFinishing && !isDestroyed

    @Suppress("DEPRECATION")
    private fun String.toHtmlSpanned(): CharSequence = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(this, Html.FROM_HTML_MODE_COMPACT)
    } else {
        Html.fromHtml(this)
    }

    // -----------------------------------------------------------------------------------------
    // Data model
    // -----------------------------------------------------------------------------------------

    private data class MarkerInfo(
        val id: Long,
        val name: String,
        val address: String?,
        val latitude: Double?,
        val longitude: Double?,
        val description: String?,
        val imageUrl: String?,
    ) {
        val displayName: String
            get() = name.ifEmpty { "Marker #$id" }

        val detail: String?
            get() = if (latitude != null && longitude != null) {
                String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude)
            } else {
                null
            }

        companion object {
            // Compiled once to avoid allocating a new Regex on every marker parse.
            private val NAME_REGEX = Regex(""""name"\s*:\s*"([^"]+)"""")
            private val ADDRESS_REGEX = Regex(""""address"\s*:\s*"([^"]+)"""")

            fun from(marker: Marker, extraPropsMap: Map<String, MarkerExtraProperties>): MarkerInfo {
                val coordinate = marker.getCoordinates()?.firstOrNull()
                val coordKey = coordinate?.let { c ->
                    String.format(java.util.Locale.US, "%.5f,%.5f", c.latitude, c.longitude)
                }
                val props = parseProperties(marker.name, coordKey, extraPropsMap)
                return MarkerInfo(
                    id = marker.id,
                    name = props.name,
                    address = props.address,
                    latitude = coordinate?.latitude,
                    longitude = coordinate?.longitude,
                    description = props.description,
                    imageUrl = props.imageUrl,
                )
            }

            private data class ParsedProperties(
                val name: String,
                val address: String?,
                val description: String?,
                val imageUrl: String?,
            )

            // The SDK stores GeoJSON feature properties in marker.name as a JSON blob, but this
            // is not guaranteed — it may also be a plain name string. We try JSON first, then
            // fall back to a regex scan, and in both cases overlay the richer description/imageUrl
            // from the pre-parsed coordinate-keyed map.
            private fun parseProperties(
                raw: String,
                coordKey: String?,
                extraPropsMap: Map<String, MarkerExtraProperties>,
            ): ParsedProperties {
                val extra = coordKey?.let { extraPropsMap[it] }

                if (!raw.startsWith("{")) {
                    return ParsedProperties(raw, null, extra?.description, extra?.imageUrl)
                }

                try {
                    val root = org.json.JSONObject(raw)
                    val src = if (root.has("properties")) root.getJSONObject("properties") else root
                    val name = src.optString("name").takeIf { it.isNotEmpty() && it != "null" }
                    val address = src.optString("address").takeIf { it.isNotEmpty() && it != "null" }
                    if (name != null) return ParsedProperties(name, address, extra?.description, extra?.imageUrl)
                } catch (_: Exception) { /* fall through to regex */ }

                val name = NAME_REGEX.find(raw)?.groupValues?.get(1)
                val address = ADDRESS_REGEX.find(raw)?.groupValues?.get(1)
                return ParsedProperties(
                    name = name ?: raw,
                    address = address?.takeIf { it.isNotEmpty() },
                    description = extra?.description,
                    imageUrl = extra?.imageUrl,
                )
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Adapter
    // -----------------------------------------------------------------------------------------

    private inner class MarkerAdapter : RecyclerView.Adapter<MarkerAdapter.ViewHolder>() {

        private var items: List<MarkerInfo> = emptyList()
        private var selectedIndex: Int = RecyclerView.NO_POSITION
        var onItemClick: ((MarkerInfo) -> Unit)? = null
        var onInfoClick: ((MarkerInfo) -> Unit)? = null

        @SuppressLint("NotifyDataSetChanged")
        fun submitList(newItems: List<MarkerInfo>) {
            items = newItems
            selectedIndex = RecyclerView.NO_POSITION
            notifyDataSetChanged()
        }

        fun clearSelection() {
            val prev = selectedIndex
            selectedIndex = RecyclerView.NO_POSITION
            if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev)
        }

        inner class ViewHolder(val binding: ItemMarkerBinding) : RecyclerView.ViewHolder(binding.root) {
            init {
                binding.root.setOnClickListener {
                    // Single-item panels have no meaningful selection — the marker is auto-highlighted.
                    if (itemCount <= 1) return@setOnClickListener
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                    val prev = selectedIndex
                    selectedIndex = pos
                    if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev)
                    notifyItemChanged(pos)
                    onItemClick?.invoke(items[pos])
                }
                binding.infoButton.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onInfoClick?.invoke(items[pos])
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = ItemMarkerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            itemBinding.root.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            // Set the selection background programmatically so we can reference the theme-resolved
            // selectionColor, which isn't available in XML at inflation time.
            itemBinding.root.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_activated), selectionColor.toDrawable())
                addState(intArrayOf(), Color.TRANSPARENT.toDrawable())
            }
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.markerName.text = item.displayName
            val detailText = item.detail
            holder.binding.markerDetail.text = detailText ?: ""
            holder.binding.markerDetail.isVisible = detailText != null
            holder.binding.infoButton.isVisible = item.description != null
            holder.itemView.isActivated = (position == selectedIndex)
        }

        override fun getItemCount(): Int = items.size
    }
}
