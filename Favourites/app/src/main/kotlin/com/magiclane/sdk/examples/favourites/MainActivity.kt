/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.favourites

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.RectangleGeographicArea
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.examples.favourites.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.favourites.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkStore
import com.magiclane.sdk.places.LandmarkStoreService
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // Portrait ConstraintSet captured once at creation; landscape constraints are derived from it.
    private lateinit var portraitConstraintSet: ConstraintSet

    // Landmark store for persisting favourite locations.
    private var store: LandmarkStore? = null

    private lateinit var landmark: Landmark

    private val searchService = SearchService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
            showStatusMessage(getString(R.string.searching))
        },

        onCompleted = { results, errorCode, _ ->
            binding.progressBar.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> {
                    if (results.isNotEmpty()) {
                        landmark = results[0]

                        showLocationDetailsPanel(
                            GemUtil.formatName(landmark),
                            GemUtil.getLandmarkDescription(landmark, true),
                        ) {
                            highlightLandmarkOnMap(
                                landmark,
                                getMapFreeRect(mapFreeSpacePadding()),
                                isFavourite(landmark),
                            )
                        }

                        binding.statusText.visibility = View.GONE
                    } else {
                        showStatusMessage(getString(R.string.no_search_results))
                    }
                }
                else -> {
                    showStatusMessage(
                        getString(
                            R.string.search_completed_with_error,
                            SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                        ),
                    )
                }
            }
            EspressoIdlingResource.decrement()
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        // Clone portrait constraints before any runtime changes are applied.
        portraitConstraintSet = ConstraintSet().also { it.clone(binding.root as ConstraintLayout) }

        // Apply orientation-specific layout once the root view is measured.
        binding.root.post { applyOrientationLayout() }

        EspressoIdlingResource.increment()

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
        binding.root.post { applyOrientationLayout() }
    }

    // Registers all SDK surface and settings callbacks.
    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = { mapView ->
            store = LandmarkStoreService().createLandmarkStore("Favourites")?.first
            store?.let { mapView.preferences?.landmarkStores?.addAllStoreCategories(it.id) }
            // Position the Magic Lane logo respecting system insets and any visible panels.
            updateFocusViewport()
        }

        // Re-align the Magic Lane logo whenever the surface is resized (e.g. on rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
                SdkCall.execute {
                    searchService.searchByFilter("Statue of Liberty New York", Coordinates(40.68925476, -74.04456329))
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // Clears SDK-level listeners to avoid callbacks reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        binding.gemSurfaceView.apply {
            onDefaultMapViewCreated = {}
            onSdkInitFailed = {}
            onSurfaceChanged = null
        }
    }

    // Re-applies constraints for the current orientation and fixes panel inset padding.
    // Landscape: panel is wrap_content at bottom-left, 40% screen width.
    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() resets view visibility; save and restore.
        val panelVis = binding.locationDetailsPanel.root.visibility
        val statusVis = binding.statusText.visibility
        val progressVis = binding.progressBar.visibility

        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                val panelWidth = (resources.displayMetrics.widthPixels * 0.4f).toInt()
                constrainWidth(R.id.location_details_panel, panelWidth)
                // WRAP_CONTENT height: panel grows with its content, no link to the toolbar.
                constrainHeight(R.id.location_details_panel, ConstraintSet.WRAP_CONTENT)
                connect(
                    R.id.location_details_panel,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START,
                    0,
                )
                connect(
                    R.id.location_details_panel,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                    0,
                )
                // No TOP constraint: panel sits in the bottom-left corner, wrap_content tall.
                clear(R.id.location_details_panel, ConstraintSet.TOP)
                clear(R.id.location_details_panel, ConstraintSet.END)
            }
        }.applyTo(rootLayout)

        binding.locationDetailsPanel.root.visibility = panelVis
        binding.statusText.visibility = statusVis
        binding.progressBar.visibility = progressVis

        // Override the binding-adapter insets listener with one that is orientation-aware.
        updatePanelInsets(isLandscape)
        updateFocusViewport()
    }

    // Replaces the binding adapter's inset listener on the details panel with an
    // orientation-aware version. In landscape the panel is on the left, so the right
    // system bar inset must not be applied as right padding.
    private fun updatePanelInsets(isLandscape: Boolean) {
        val panel = binding.locationDetailsPanel.root
        val bigPadding = resources.getDimensionPixelSize(R.dimen.big_padding)

        ViewCompat.setOnApplyWindowInsetsListener(panel) { v, insets ->
            val sys = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            v.updatePadding(
                left = bigPadding + sys.left,
                top = bigPadding,
                right = if (isLandscape) bigPadding else bigPadding + sys.right,
                bottom = bigPadding + sys.bottom,
            )
            insets
        }
        panel.requestApplyInsets()
    }

    // Sets the Magic Lane logo viewport to the visible map area (no padding).
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurfaceView.mapView?.preferences?.focusViewport = getMapFreeRect()
        }
    }

    // Returns the visible map area, accounting for toolbar, system bars, and any visible panels.
    // An optional padding deflates the rect on all sides (useful for camera-centering animations).
    // Portrait: panel at bottom → restricts bottom edge; landscape: panel at left → restricts left edge.
    // Status text is considered in both orientations.
    private fun getMapFreeRect(padding: Int = 0): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val top = binding.toolbar.bottom

        val left: Int
        val right: Int
        val bottom: Int

        if (isLandscape) {
            left = if (binding.locationDetailsPanel.root.isVisible) {
                binding.locationDetailsPanel.root.right
            } else {
                insets?.left ?: 0
            }
            right = (width - (insets?.right ?: 0)).coerceAtLeast(left)
            bottom = when {
                binding.statusText.isVisible -> binding.statusText.top.coerceAtLeast(top)
                else -> (height - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
        } else {
            left = insets?.left ?: 0
            right = (width - (insets?.right ?: 0)).coerceAtLeast(left)
            bottom = when {
                binding.locationDetailsPanel.root.isVisible ->
                    binding.locationDetailsPanel.root.top.coerceAtLeast(top)
                binding.statusText.isVisible ->
                    binding.statusText.top.coerceAtLeast(top)
                else ->
                    (height - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
        }

        // Apply symmetric padding (for camera animations) while keeping the rect valid.
        val paddedLeft = (left + padding).coerceAtMost(right - 1)
        val paddedRight = (right - padding).coerceAtLeast(paddedLeft + 1)
        val paddedTop = (top + padding).coerceAtMost(bottom - 1)
        val paddedBottom = (bottom - padding).coerceAtLeast(paddedTop + 1)

        return Rect(paddedLeft, paddedTop, paddedRight, paddedBottom)
    }

    private fun mapFreeSpacePadding() = resources.getDimensionPixelSize(R.dimen.map_free_space_padding)

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

    private fun showStatusMessage(text: String) {
        binding.apply {
            if (!statusText.isVisible) statusText.visibility = View.VISIBLE
            statusText.text = text
            // Post so the status text is laid out before we recompute the viewport.
            statusText.post { updateFocusViewport() }
        }
    }

    private fun getFavouriteId(landmark: Landmark): Int = SdkCall.execute {
        // Search a small area around the landmark to find its store ID.
        val radius = 5.0 // meters
        val area = landmark.coordinates?.let { RectangleGeographicArea(it, radius, radius) }
        val landmarks = area?.let { store?.getLandmarksByArea(it) } ?: return@execute -1

        val threshold = 0.00001
        landmarks.forEach {
            val itCoordinates = it.coordinates
            val landmarkCoordinates = landmark.coordinates

            if (itCoordinates != null && landmarkCoordinates != null) {
                if ((itCoordinates.latitude - landmarkCoordinates.latitude < threshold) &&
                    (itCoordinates.longitude - landmarkCoordinates.longitude < threshold)
                ) {
                    return@execute it.id
                }
            } else {
                return@execute -1
            }
        }
        -1
    } ?: -1

    private fun isFavourite(landmark: Landmark): Boolean = getFavouriteId(landmark) != -1

    private fun addToFavourites(landmark: Landmark) = SdkCall.execute {
        val lmk = Landmark()
        lmk.assign(landmark)
        ImageDatabase().getImageById(SdkImages.Engine_Misc.LocationDetails_FavouritePushPin.value)
            ?.let { lmk.image = it }
        store?.addLandmark(lmk)
    }

    private fun deleteFromFavourites(landmarkId: Int) = SdkCall.execute {
        store?.removeLandmark(landmarkId)
    }

    private fun setFavouriteButtonIcon(button: MaterialButton, isFavourite: Boolean) {
        button.icon = ContextCompat.getDrawable(
            this,
            if (isFavourite) R.drawable.baseline_star_24 else R.drawable.baseline_star_border_24,
        )
    }

    private fun highlightLandmarkOnMap(
        landmark: Landmark,
        rect: Rect,
        isFavorite: Boolean,
        flyToLandmark: Boolean = true,
    ) = SdkCall.execute {
        binding.gemSurfaceView.mapView?.let { mapView ->
            mapView.deactivateAllHighlights()

            landmark.image = ImageDatabase().getImageById(SdkImages.Core.Search_Results_Pin.value)

            val contour = landmark.getContourGeographicArea()
            var highlightSettings: HighlightRenderSettings

            @Suppress("VerboseNullabilityAndEmptiness")
            if ((contour != null) && !contour.isEmpty()) {
                if (flyToLandmark) {
                    mapView.centerOnRectArea(
                        contour,
                        zoomLevel = -1,
                        viewRc = rect,
                        Animation(EAnimation.Linear, 900),
                    )
                }

                val highlightOptions = if (isFavorite) {
                    EHighlightOptions.ShowContour.value
                } else {
                    EHighlightOptions.ShowContour.value or EHighlightOptions.ShowLandmark.value
                }

                highlightSettings = HighlightRenderSettings(
                    highlightOptions,
                    Rgba(255, 98, 0, 255),
                    Rgba(255, 98, 0, 255),
                    0.75,
                ).also { it.imageSize = 6.0 }

                mapView.activateHighlightLandmarks(landmark, highlightSettings)
            } else {
                if (flyToLandmark) {
                    landmark.coordinates?.let {
                        mapView.centerOnCoordinates(
                            it,
                            -1,
                            rect.center,
                            Animation(EAnimation.Linear, 900),
                            0.0,
                            0.0,
                        )
                    }
                }

                if (!isFavorite) {
                    highlightSettings = HighlightRenderSettings(EHighlightOptions.ShowLandmark)
                        .also { it.imageSize = 6.0 }
                    mapView.activateHighlightLandmarks(landmark, highlightSettings)
                }
            }
        }
    }

    private fun showLocationDetailsPanel(title: String, message: String, onViewCreated: (() -> Unit)? = null) {
        binding.locationDetailsPanel.apply {
            this.title.text = title
            this.message.text = message

            setFavouriteButtonIcon(favoritesButton, isFavourite(landmark))

            favoritesButton.setOnClickListener {
                val landmarkId = getFavouriteId(landmark)
                val isFavourite = if (landmarkId != -1) {
                    deleteFromFavourites(landmarkId)
                    setFavouriteButtonIcon(favoritesButton, false)
                    false
                } else {
                    addToFavourites(landmark)
                    setFavouriteButtonIcon(favoritesButton, true)
                    true
                }
                highlightLandmarkOnMap(landmark, getMapFreeRect(mapFreeSpacePadding()), isFavourite, false)
            }

            root.visibility = View.VISIBLE

            // After the panel is laid out, update the logo position then fly to the landmark.
            root.post {
                updateFocusViewport()
                onViewCreated?.invoke()
            }
        }
    }

    private fun isActivityAlive() = !isFinishing && !isDestroyed

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }
}

//region TESTING
@VisibleForTesting
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("FavouritesIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
