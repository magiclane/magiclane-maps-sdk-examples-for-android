/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.searchalongroute

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EGenericCategoriesIDs
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GenericCategories
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.examples.searchalongroute.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.searchalongroute.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.GEMLog
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// Demonstrates searching for points of interest along a pre-calculated route.
// Flow: map starts full-screen → route is calculated → search panel slides in →
// user picks a POI category → results appear in the list → tapping a result highlights it on the map.
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var resultAdapter: ResultAdapter

    private var mainRoute = Route()

    // Tracks whether the search panel is visible so onConfigurationChanged can re-apply
    // the correct layout split without relying on view state (which may not survive rotation).
    private var panelShown = false

    private companion object {
        private const val HIGHLIGHT_IMAGE_SIZE = 6.0

        // Shared animation duration (ms) used when presenting and re-centering the route.
        private const val ROUTE_ANIMATION_DURATION_MS = 900

        // System insets (status/navigation bars plus display cutout) used both to pad the search
        // panel and to keep the Magic Lane logo clear of system UI via updateFocusViewport().
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

        // Always return false so that every new submitList call fully redraws the list.
        // Search results change completely between queries, so item identity is meaningless.
        val resultDiffCallback = object : DiffUtil.ItemCallback<ResultItem>() {
            override fun areItemsTheSame(oldItem: ResultItem, newItem: ResultItem) = false
            override fun areContentsTheSame(oldItem: ResultItem, newItem: ResultItem) = false
        }
    }

    // SearchService fires onCompleted on a background callback thread, not on the SDK thread.
    // Landmark properties (name, image, extra info) require the SDK thread, so buildResultItems
    // must be wrapped in SdkCall.execute even though we are already inside a callback.
    private val searchService = SearchService(
        onStarted = {
            binding.searchProgressBar.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ results, errorCode, _ ->
            if (errorCode != GemError.Cancel) {
                binding.searchProgressBar.visibility = View.INVISIBLE

                val items = SdkCall.execute {
                    buildResultItems(results)
                } ?: listOf()
                resultAdapter.submitList(items)

                binding.noResultText.isVisible = items.isEmpty()

                if (errorCode != GemError.NoError) {
                    GEMLog.error(
                        this,
                        getString(R.string.search_error, SdkCall.runSynced { GemError.getMessage(errorCode, this) }),
                    )
                }
            }
        },
    )

    private val routingService = RoutingService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { routes, errorCode, _ ->
            when (errorCode) {
                GemError.NoError -> {
                    if (routes.isNotEmpty()) {
                        mainRoute = routes[0]
                        binding.progressBar.visibility = View.GONE
                        // showSearchPanel resizes the map area and then presents the route
                        // via View.post so that presentRoute sees the final view dimensions.
                        showSearchPanel()
                    }
                }
                else -> {
                    if (errorCode != GemError.Cancel) {
                        runOnAliveUi {
                            binding.progressBar.visibility = View.GONE
                            showDialog(
                                getString(
                                    R.string.routing_service_error,
                                    SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                                ),
                            )
                        }
                    }
                }
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        categoryAdapter = CategoryAdapter()
        resultAdapter = ResultAdapter()

        // A single listener handles both orientations: in portrait the panel sits at the bottom
        // (no top inset needed, right inset absorbs nav bar); in landscape it spans the left half
        // (no right inset needed, top inset absorbs status bar). The listener re-fires on rotation
        // with fresh insets, so orientation is read at fire time rather than at registration time.
        ViewCompat.setOnApplyWindowInsetsListener(binding.searchPanel) { view, insets ->
            val bars = insets.getInsets(SYSTEM_INSET_TYPES)
            val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            view.setPadding(bars.left, if (isPortrait) 0 else bars.top, if (isPortrait) bars.right else 0, bars.bottom)
            insets
        }

        binding.categoriesView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
            itemAnimator = null
        }

        binding.listView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(
                DividerItemDecoration(applicationContext, LinearLayoutManager.VERTICAL),
            )
            adapter = resultAdapter
            itemAnimator = null
        }

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        clearSdkListeners()
        GemSdk.release()
        super.onDestroy()
        exitProcess(0)
    }

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Folkestone", 51.0814, 1.1695),
            Landmark("Paris", 48.8566932, 2.3514616),
        )
        routingService.calculateRoute(waypoints)
    }

    private fun registerSdkListeners() {
        // onSdkInitFailed fires off the SDK thread, so GemError.getMessage is called directly
        // here — no enclosing SdkCall block is needed (or available) at this point.
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) { finish() }
            }
        }

        // Align the Magic Lane logo with the system window insets as soon as the map is created.
        binding.gemSurfaceView.onDefaultMapViewCreated = {
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. on rotation or when the
        // search panel slides in and shrinks the map area).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        // Wait for the worldwide road map to be ready before starting the demo.
        // The listener is cleared immediately once triggered to avoid repeat calls.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                setupTouchHandler()
                loadCategories()
                calculateRoute()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // Registers the map touch listener: tapping the route re-centers the map on it within the
    // current visible rect (kept clear of the search panel and system bars).
    private fun setupTouchHandler() {
        binding.gemSurfaceView.mapView?.onTouch = { xy ->
            SdkCall.execute {
                binding.gemSurfaceView.mapView?.cursorScreenPosition = xy
                val tappedRoutes = binding.gemSurfaceView.mapView?.cursorSelectionRoutes
                if (!tappedRoutes.isNullOrEmpty()) {
                    binding.gemSurfaceView.mapView?.centerOnRoutes(
                        arrayListOf(mainRoute),
                        animation = Animation(EAnimation.Linear, ROUTE_ANIMATION_DURATION_MS),
                        viewRc = getRouteViewRect(),
                    )
                }
            }
        }
    }

    // Positions the Magic Lane logo (and other map UI) inside the area left free by the system
    // bars and display cutout, so it is never hidden behind system UI.
    // While the map is full-screen (before the search panel appears) all four insets apply.
    // Once the panel is shown it shares the screen with the map, so the inset on the side the
    // panel covers is dropped — mirroring resolveMapPadding: bottom in portrait, left in landscape.
    private fun updateFocusViewport() = SdkCall.runSynced {
        val mapView = binding.gemSurfaceView.mapView ?: return@runSynced
        val viewport = mapView.viewport ?: return@runSynced
        val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        val left = if (panelShown && !isPortrait) 0 else (insets?.left ?: 0)
        val top = insets?.top ?: 0
        val right = (viewport.width - (insets?.right ?: 0)).coerceAtLeast(left)
        val bottomInset = if (panelShown && isPortrait) 0 else (insets?.bottom ?: 0)
        val bottom = (viewport.height - bottomInset).coerceAtLeast(top)
        mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
    }

    private fun loadCategories() {
        val items = SdkCall.execute {
            GenericCategories().categories?.map { cat ->
                val iconSize = resources.getDimensionPixelSize(R.dimen.category_icon_size)
                CategoryItem(
                    name = cat.name.orEmpty(),
                    icon = cat.image?.asBitmap(iconSize, iconSize),
                    id = cat.id,
                )
            } ?: emptyList()
        } ?: emptyList()
        Util.postOnMain {
            if (isActivityAlive()) {
                categoryAdapter.submitItems(items)
            }
        }
    }

    private fun searchAlongRouteForCategory(category: CategoryItem) {
        binding.searchHint.isVisible = false
        SdkCall.execute {
            val enumVal = EGenericCategoriesIDs.entries.firstOrNull { it.value == category.id } ?: return@execute
            searchService.cancelSearch()
            searchService.preferences.maxMatches = 25
            searchService.searchAlongRoute(mainRoute, enumVal)
        }
    }

    // Must be called inside SdkCall.execute: Landmark properties (name, image, extra info)
    // are backed by native SDK objects that require the SDK thread for safe access.
    private fun buildResultItems(landmarks: ArrayList<Landmark>): List<ResultItem> {
        val imageSize = resources.getDimensionPixelSize(R.dimen.list_item_image_size)
        val sideIconSize = resources.getDimensionPixelSize(R.dimen.side_icon_size)
        return landmarks.map { lm ->
            val distRaw = lm.findExtraInfo("gm_search_result_dist = ")?.trim().orEmpty()
            val dist = GemUtil.getDistText(distRaw.toIntOrNull() ?: 0, SdkSettings.unitSystem)
            val side = lm.findExtraInfo("gm_search_result_side = ")?.trim().orEmpty()
            val sideIconId = when {
                side.equals("Left side", ignoreCase = true) -> SdkImages.Engine_Misc.Poi_ToLeft.value
                side.equals("Right side", ignoreCase = true) -> SdkImages.Engine_Misc.Poi_ToRight.value
                else -> -1
            }
            ResultItem(
                image = lm.imageAsBitmap(imageSize),
                name = lm.name.orEmpty(),
                description = GemUtil.getLandmarkDescription(lm, true),
                distanceText = dist.first,
                distanceUnit = dist.second,
                sideImage = if (sideIconId >= 0) {
                    GemUtilImages.asBitmap(
                        sideIconId,
                        sideIconSize,
                        sideIconSize,
                    )
                } else {
                    null
                },
                landmark = lm,
            )
        }
    }

    // Highlights a selected search result on the map using the standard search-result pin icon.
    // Uses the same view rect as the route zoom so the pin stays within the visible map area.
    // POIs with a geographic contour (e.g. parks, buildings) are centered on that area;
    // point POIs are centered on their coordinate.
    private fun highlightLandmarkOnMap(landmark: Landmark) = SdkCall.execute {
        binding.gemSurfaceView.mapView?.let { mapView ->
            val rect = getRouteViewRect()
            mapView.deactivateAllHighlights()
            // Assign the SDK's built-in search-result pin as the landmark's display image.
            landmark.image = ImageDatabase().getImageById(SdkImages.Core.Search_Results_Pin.value)
            val contour = landmark.getContourGeographicArea()
            if (contour != null && !contour.isEmpty()) {
                mapView.centerOnRectArea(
                    contour,
                    zoomLevel = 75,
                    viewRc = rect,
                    animation = Animation(EAnimation.Linear, ROUTE_ANIMATION_DURATION_MS),
                )
                mapView.activateHighlightLandmarks(
                    landmark,
                    HighlightRenderSettings(
                        EHighlightOptions.ShowContour.value or EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                    ).apply { imageSize = HIGHLIGHT_IMAGE_SIZE },
                )
            } else {
                landmark.coordinates?.let {
                    mapView.centerOnCoordinates(
                        it,
                        75,
                        rect.center,
                        Animation(EAnimation.Linear, ROUTE_ANIMATION_DURATION_MS),
                        0.0,
                        0.0,
                    )
                }
                mapView.activateHighlightLandmarks(
                    landmark,
                    HighlightRenderSettings(
                        EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                    ).apply { imageSize = HIGHLIGHT_IMAGE_SIZE },
                )
            }
        }
    }

    private fun deactivateHighlights() = SdkCall.execute {
        binding.gemSurfaceView.mapView?.deactivateAllHighlights()
    }

    private fun showSearchPanel() {
        panelShown = true
        applyLayoutConstraints()
        binding.searchPanel.visibility = View.VISIBLE
        // Defer presentRoute until the next layout pass so that gemSurfaceView has been
        // measured to its new (half-screen) size. Calling it synchronously here would pass
        // stale dimensions to edgeAreaInsets and cause the route to be framed incorrectly.
        binding.searchPanel.post {
            SdkCall.execute {
                binding.gemSurfaceView.mapView?.presentRoute(
                    mainRoute,
                    animation = Animation(EAnimation.Linear, ROUTE_ANIMATION_DURATION_MS),
                    edgeAreaInsets = getEdgeAreaInsets(),
                    displayBubble = true,
                )
            }
        }
    }

    // Rebuilds the constraint set for the current orientation and panel state.
    // While the panel is hidden the map fills the screen; once it is shown the screen splits
    // (map on top in portrait / right in landscape, panel opposite, divided by split_guideline).
    // Both views are forced to MATCH_CONSTRAINT and given anchors that are valid for the current
    // guideline orientation — including the hidden panel, so it never keeps a connection to the
    // guideline's perpendicular anchor, which would crash the next measure pass after a rotation.
    private fun applyLayoutConstraints() {
        val constraintLayout = binding.root as? ConstraintLayout ?: return
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val set = ConstraintSet()
        set.clone(constraintLayout)

        // The XML variants size these with match_parent on one axis, which ignores start/end (or
        // top/bottom) constraints. Force MATCH_CONSTRAINT so the anchors below actually take effect.
        set.constrainWidth(R.id.gem_surface_view, ConstraintSet.MATCH_CONSTRAINT)
        set.constrainHeight(R.id.gem_surface_view, ConstraintSet.MATCH_CONSTRAINT)
        set.constrainWidth(R.id.search_panel, ConstraintSet.MATCH_CONSTRAINT)
        set.constrainHeight(R.id.search_panel, ConstraintSet.MATCH_CONSTRAINT)

        // Map fills the parent by default; when the panel is shown, the edge adjacent to it is
        // pulled to the guideline instead (bottom in portrait, start in landscape).
        set.connect(R.id.gem_surface_view, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        set.connect(R.id.gem_surface_view, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        set.connect(R.id.gem_surface_view, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(R.id.gem_surface_view, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        if (panelShown) {
            if (isPortrait) {
                set.connect(R.id.gem_surface_view, ConstraintSet.BOTTOM, R.id.split_guideline, ConstraintSet.BOTTOM)
            } else {
                set.connect(R.id.gem_surface_view, ConstraintSet.START, R.id.split_guideline, ConstraintSet.START)
            }
        }

        // Panel sits opposite the map across the guideline. Always given orientation-valid anchors
        // (even while hidden) so no stale guideline connection survives a rotation.
        if (isPortrait) {
            set.connect(R.id.search_panel, ConstraintSet.TOP, R.id.split_guideline, ConstraintSet.TOP)
            set.connect(R.id.search_panel, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            set.connect(R.id.search_panel, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            set.connect(R.id.search_panel, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        } else {
            set.connect(R.id.search_panel, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            set.connect(R.id.search_panel, ConstraintSet.END, R.id.split_guideline, ConstraintSet.END)
            set.connect(R.id.search_panel, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            set.connect(R.id.search_panel, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        }
        set.applyTo(constraintLayout)
    }

    // The manifest declares configChanges="orientation|screenSize" so the activity is never
    // recreated on rotation. The layout variant (layout-land) is only used on first creation,
    // so we must manually flip the guideline and re-apply all constraints here.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        // The guideline direction must match the split axis: horizontal divides top/bottom in
        // portrait, vertical divides left/right in landscape.
        (binding.splitGuideline.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
            params.orientation = if (isPortrait) ConstraintLayout.LayoutParams.HORIZONTAL else ConstraintLayout.LayoutParams.VERTICAL
            binding.splitGuideline.layoutParams = params
        }
        // Always re-apply: flipping the guideline invalidates any view still anchored to its old
        // (perpendicular) edge, so the constraints must be rebuilt even when the panel is hidden.
        applyLayoutConstraints()
    }

    // Inset amounts (px) for presentRoute: keeps the route clear of system bars, cutouts,
    // and the panel boundary, with an extra 20 dp breathing room on all sides.
    private fun getEdgeAreaInsets(): Rect {
        val p = resolveMapPadding()
        return Rect(p[0], p[1], p[2], p[3])
    }

    // Absolute screen rectangle (px, relative to GemSurfaceView) for centerOnRoutes.
    private fun getRouteViewRect(): Rect {
        val p = resolveMapPadding()
        val w = binding.gemSurfaceView.width.takeIf { it > 0 } ?: binding.gemSurfaceView.measuredWidth
        val h = binding.gemSurfaceView.height.takeIf { it > 0 } ?: binding.gemSurfaceView.measuredHeight
        return Rect(p[0], p[1], (w - p[2]).coerceAtLeast(p[0]), (h - p[3]).coerceAtLeast(p[1]))
    }

    // [left, top, right, bottom] padding in px for the map view, accounting for system bars,
    // cutouts, the panel boundary, and a fixed 20 dp margin.
    // Portrait: map fills top 50% — nav bar lives under the panel so bottom = margin only.
    // Landscape: map fills right 50% — panel absorbs left system insets so left = margin only.
    private fun resolveMapPadding(): IntArray {
        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val pad = resources.getDimensionPixelSize(R.dimen.route_padding)
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return intArrayOf(
            if (isPortrait) (insets?.left ?: 0) + pad else pad,
            (insets?.top ?: 0) + pad,
            (insets?.right ?: 0) + pad,
            if (isPortrait) pad else (insets?.bottom ?: 0) + pad,
        )
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

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain {
            if (isActivityAlive()) {
                block()
            }
        }
    }

    private fun isActivityAlive(): Boolean {
        return !isFinishing && !isDestroyed
    }

    data class CategoryItem(val name: String, val icon: Bitmap?, val id: Int)

    data class ResultItem(
        val image: Bitmap?,
        val name: String,
        val description: String,
        val distanceText: String,
        val distanceUnit: String,
        val sideImage: Bitmap?,
        val landmark: Landmark,
    )

    inner class CategoryAdapter : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {
        private val items: MutableList<CategoryItem> = mutableListOf()
        private var selectedIndex = -1

        fun submitItems(newItems: List<CategoryItem>) {
            val oldCount = items.size
            items.clear()
            items.addAll(newItems)
            selectedIndex = -1
            notifyItemRangeRemoved(0, oldCount)
            notifyItemRangeInserted(0, items.size)
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val container: View = view.findViewById(R.id.category_container)
            val icon: ImageView = view.findViewById(R.id.category_icon)
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.list_item_category, viewGroup, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.icon.setImageBitmap(item.icon)
            val isSelected = position == selectedIndex
            if (isSelected) {
                holder.container.setBackgroundResource(R.drawable.rounded_background_primary)
            } else {
                holder.container.background = null
            }
            holder.container.setOnClickListener {
                val previousIndex = selectedIndex
                // bindingAdapterPosition is used instead of position to guard against stale
                // captures when the list updates between bind and click.
                val tappedIndex = holder.bindingAdapterPosition
                if (selectedIndex == tappedIndex) {
                    // Tapping the already-selected category deselects it and clears results.
                    selectedIndex = -1
                    searchService.cancelSearch()
                    resultAdapter.submitList(emptyList())
                } else {
                    selectedIndex = tappedIndex
                    searchAlongRouteForCategory(items[selectedIndex])
                }
                if (previousIndex >= 0) notifyItemChanged(previousIndex)
                if (selectedIndex >= 0 && selectedIndex != previousIndex) notifyItemChanged(selectedIndex)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    inner class ResultAdapter : ListAdapter<ResultItem, ResultAdapter.ViewHolder>(resultDiffCallback) {
        // Index of the row whose landmark is currently highlighted on the map, so the list can
        // mark it and the user can see which result the on-map pin corresponds to.
        private var selectedIndex = -1

        // Clear any active map highlight whenever the result list changes (new search or deselect),
        // so the pin from a previous selection doesn't linger on the map. The list selection is
        // reset too — the incoming items are a fresh result set with no row highlighted yet.
        override fun submitList(list: List<ResultItem>?) {
            deactivateHighlights()
            selectedIndex = -1
            super.submitList(list)
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.image)
            val sideView: ImageView = view.findViewById(R.id.side)
            val name: TextView = view.findViewById(R.id.text)
            val description: TextView = view.findViewById(R.id.description)
            val distanceText: TextView = view.findViewById(R.id.status_text)
            val distanceUnit: TextView = view.findViewById(R.id.status_description)
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.list_item, viewGroup, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.image.setImageBitmap(item.image)
            holder.name.text = item.name
            holder.description.text = item.description
            holder.distanceText.text = item.distanceText
            holder.distanceUnit.text = item.distanceUnit
            if (item.sideImage != null) {
                holder.sideView.setImageBitmap(item.sideImage)
                holder.sideView.visibility = View.VISIBLE
            } else {
                holder.sideView.visibility = View.GONE
            }
            // Tint the currently selected row so it is clear which result the map pin belongs to.
            holder.itemView.setBackgroundColor(
                if (position == selectedIndex) {
                    holder.itemView.context.getColor(R.color.selected_item)
                } else {
                    0
                },
            )
            holder.itemView.setOnClickListener {
                // bindingAdapterPosition guards against stale captures if the list updates
                // between bind and click.
                val tappedIndex = holder.bindingAdapterPosition
                if (tappedIndex == RecyclerView.NO_POSITION) return@setOnClickListener
                val previousIndex = selectedIndex
                selectedIndex = tappedIndex
                if (previousIndex >= 0) notifyItemChanged(previousIndex)
                notifyItemChanged(selectedIndex)
                highlightLandmarkOnMap(item.landmark)
            }
        }
    }
}
