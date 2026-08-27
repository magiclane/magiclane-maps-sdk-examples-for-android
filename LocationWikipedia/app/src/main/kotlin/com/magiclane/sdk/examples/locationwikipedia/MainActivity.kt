/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.locationwikipedia

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EExternalImageQuality
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.ExternalInfo
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.examples.locationwikipedia.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.locationwikipedia.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.math.max
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    companion object {
        private const val BOTTOM_SHEET_HEIGHT_RATIO = 0.5
        private const val DEFAULT_SEARCH_NAME = "Statue Of Liberty"
        private const val FLY_TO_ANIMATION_DURATION_MS = 900

        // In landscape the panel becomes a side column taking this fraction of the screen width.
        private const val LANDSCAPE_PANEL_WIDTH_RATIO = 0.45f

        // System bars + display cutout: the screen regions the map should stay clear of.
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: ActivityMainBinding

    // Portrait ConstraintSet captured once at creation; landscape constraints are derived from it.
    private lateinit var portraitConstraintSet: ConstraintSet

    private var wikipediaImagesList = mutableListOf<WikipediaImageModel>()

    private val externalInfoService = ExternalInfo()

    private var standardHeight = 0
    private val imageQuality = EExternalImageQuality.Medium

    private var wikipediaListAdapter: WikipediaListAdapter? = null

    private var mapInsetPaddingPx = 0
    private lateinit var currentLandmark: Landmark

    private val searchService = SearchService(
        onStarted = {
            runOnAliveUi {
                binding.progressBar.visibility = View.VISIBLE
            }
        },

        onCompleted = { results, errorCode, _ ->
            runOnAliveUi {
                binding.progressBar.visibility = View.GONE

                when (errorCode) {
                    GemError.NoError -> {
                        if (results.isNotEmpty()) {
                            currentLandmark = results[0]
                            val name = SdkCall.execute { currentLandmark.name }
                            binding.locationName.text = name
                            requestWiki(currentLandmark)
                        } else {
                            showDialog(getString(R.string.no_search_results))
                        }
                    }
                    else -> {
                        val errorMessage = SdkCall.runSynced { GemError.getMessage(errorCode, this@MainActivity) }
                        showDialog(
                            getString(R.string.search_completed_with_error, errorMessage),
                        )
                    }
                }
            }
        },
    )

    private val wikipediaProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { _, _ ->
            displayWikipediaInfo()
            binding.progressBar.visibility = View.GONE
            EspressoIdlingResource.decrement()
        },

        postOnMain = true,
    )

    private val wikipediaImagesProgressListener = WikipediaImagesProgressListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        EspressoIdlingResource.increment()

        mapInsetPaddingPx = resources.getDimension(R.dimen.padding_40).toInt()

        // Clone the portrait constraints before any runtime (landscape) changes are applied.
        portraitConstraintSet = ConstraintSet().also { it.clone(binding.root as ConstraintLayout) }

        // Apply orientation-specific layout once the root view has been measured.
        binding.root.post { applyOrientationLayout() }

        standardHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_MM,
            30f,
            resources.displayMetrics,
        ).toInt()

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    private fun registerSdkListeners() {
        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) { finish() }
            }
        }

        binding.gemSurface.onDefaultMapViewCreated = {
            // Align the Magic Lane logo with the system window insets on first map creation.
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. rotation).
        binding.gemSurface.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }

                SdkCall.execute {
                    val searchCenter = Coordinates(40.68925476, -74.04456329)
                    searchService.searchByFilter(DEFAULT_SEARCH_NAME, searchCenter)
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurface.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    override fun onDestroy() {
        clearSdkListeners()
        super.onDestroy()

        // Release the SDK before the activity is fully destroyed.
        GemSdk.release()
        exitProcess(0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Re-flow the panel for the new orientation once the root has been re-measured.
        binding.root.post { applyOrientationLayout() }
    }

    private fun requestWiki(value: Landmark) = SdkCall.execute {
        externalInfoService.requestWikiInfo(value, wikipediaProgressListener)
    }

    private fun fetchItemAtIndex(index: Int) = SdkCall.execute {
        if (index < 0 || index >= wikipediaImagesList.size) return@execute

        wikipediaImagesProgressListener.apply {
            retryCount = 3
            this.index = index

            image?.let {
                externalInfoService.requestWikiImage(
                    wikipediaImagesProgressListener,
                    it,
                    index,
                    imageQuality,
                )
            }
        }
    }

    private fun flyTo(landmark: Landmark) = SdkCall.execute {
        landmark.geographicArea?.let { area ->
            binding.gemSurface.mapView?.let { mapView ->
                mapView.centerOnRectArea(
                    area,
                    // Deflate the free area so the highlighted landmark isn't framed edge-to-edge.
                    viewRc = getFreeScreenRect(mapInsetPaddingPx),
                    animation = Animation(EAnimation.Linear, FLY_TO_ANIMATION_DURATION_MS),
                )

                val settings = HighlightRenderSettings(EHighlightOptions.ShowContour)
                mapView.activateHighlightLandmarks(landmark, settings)
            }
        }
    }

    // Re-flows the Wikipedia panel for the current orientation.
    // Portrait: full-width sheet pinned to the bottom, half the screen tall.
    // Landscape: a wider side column anchored to the start edge, filling the height below the toolbar.
    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() resets view visibility; save and restore it around the change.
        val panelVisibility = binding.wikipediaContainer.visibility
        val progressVisibility = binding.progressBar.visibility

        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                val panelWidth = (resources.displayMetrics.widthPixels * LANDSCAPE_PANEL_WIDTH_RATIO).toInt()
                constrainWidth(R.id.wikipedia_container, panelWidth)
                // MATCH_CONSTRAINT height: fill from below the toolbar down to the bottom edge.
                constrainHeight(R.id.wikipedia_container, ConstraintSet.MATCH_CONSTRAINT)
                connect(R.id.wikipedia_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                connect(R.id.wikipedia_container, ConstraintSet.TOP, R.id.toolbar, ConstraintSet.BOTTOM)
                connect(R.id.wikipedia_container, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                // Drop the END constraint so the fixed-width panel stays left-aligned.
                clear(R.id.wikipedia_container, ConstraintSet.END)
            } else {
                // Bottom sheet keeps its full width (from the cloned set); only the height is fixed.
                constrainHeight(R.id.wikipedia_container, portraitPanelHeightPx())
            }
        }.applyTo(rootLayout)

        binding.wikipediaContainer.visibility = panelVisibility
        binding.progressBar.visibility = progressVisibility

        // Override the binding-adapter inset listener with an orientation-aware one.
        updatePanelInsets(isLandscape)
        updateFocusViewport()
    }

    // Half the portrait screen height, computed from the larger display dimension so it stays
    // correct regardless of the orientation the activity was created in.
    private fun portraitPanelHeightPx(): Int {
        val dm = resources.displayMetrics
        return (max(dm.widthPixels, dm.heightPixels) * BOTTOM_SHEET_HEIGHT_RATIO).toInt()
    }

    // Applies the system-bar / cutout insets as padding on the panel so its background stays
    // edge-to-edge while only the content is offset. Every edge is set explicitly so padding from
    // the previous orientation is never carried over on rotation.
    private fun updatePanelInsets(isLandscape: Boolean) {
        val panel = binding.wikipediaContainer
        ViewCompat.setOnApplyWindowInsetsListener(panel) { view, insets ->
            val systemInsets = insets.getInsets(SYSTEM_INSET_TYPES)
            view.updatePadding(
                // Landscape: the panel hugs the start edge, so clear the left system bar / cutout.
                left = if (isLandscape) systemInsets.left else 0,
                top = 0,
                right = 0,
                bottom = systemInsets.bottom,
            )
            insets
        }
        panel.requestApplyInsets()
    }

    // Anchors the Magic Lane logo to the visible map area (no padding).
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurface.mapView?.preferences?.focusViewport = getFreeScreenRect()
            binding.gemSurface.mapView?.invalidate()
        }
    }

    // Returns the portion of the map not covered by the toolbar, system bars or the Wikipedia panel.
    // Portrait: the panel sits at the bottom, so it restricts the bottom edge.
    // Landscape: the panel sits on the start edge, so it restricts the left edge.
    // An optional padding deflates the rect on every side (used for camera-centering animations).
    private fun getFreeScreenRect(padding: Int = 0): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsets(SYSTEM_INSET_TYPES)

        val width = root.width.takeIf { it > 0 } ?: Resources.getSystem().displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: Resources.getSystem().displayMetrics.heightPixels
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // Push the top below the toolbar so the map content (and logo) never sit behind it.
        val topInset = insets?.top ?: 0
        val toolbarBottom = binding.toolbar.bottom.takeIf { it > 0 } ?: 0
        val top = max(topInset, toolbarBottom)

        val insetRight = width - (insets?.right ?: 0)
        val insetBottom = height - (insets?.bottom ?: 0)
        val panelVisible = binding.wikipediaContainer.isVisible

        val left: Int
        val right: Int
        val bottom: Int
        if (isLandscape) {
            // Free area starts at the right edge of the side panel.
            left = if (panelVisible && binding.wikipediaContainer.right > 0) {
                binding.wikipediaContainer.right
            } else {
                insets?.left ?: 0
            }
            right = insetRight.coerceAtLeast(left)
            bottom = insetBottom.coerceAtLeast(top)
        } else {
            // Free area ends at the top edge of the bottom sheet.
            left = insets?.left ?: 0
            right = insetRight.coerceAtLeast(left)
            bottom = if (panelVisible && binding.wikipediaContainer.top > 0) {
                binding.wikipediaContainer.top.coerceAtMost(insetBottom)
            } else {
                insetBottom
            }.coerceAtLeast(top)
        }

        // Deflate by padding while keeping the rect non-degenerate.
        val paddedLeft = (left + padding).coerceAtMost(right - 1)
        val paddedRight = (right - padding).coerceAtLeast(paddedLeft + 1)
        val paddedTop = (top + padding).coerceAtMost(bottom - 1)
        val paddedBottom = (bottom - padding).coerceAtLeast(paddedTop + 1)

        return Rect(paddedLeft, paddedTop, paddedRight, paddedBottom)
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

    private fun displayWikipediaInfo() {
        var wikipediaTitleString = ""
        var wikipediaDescriptionString = ""
        var wikipediaUrl = ""
        var wikipediaImagesCount = 0

        SdkCall.execute {
            wikipediaTitleString = getString(R.string.wikipedia)
            wikipediaDescriptionString = externalInfoService.wikiPageDescription.toString()
            wikipediaUrl = externalInfoService.wikiPageURL.toString()
            wikipediaImagesCount = externalInfoService.wikiImagesCount
        }

        binding.wikipediaTitle.apply {
            text = wikipediaTitleString
            setOnClickListener {
                if (isActivityAlive()) {
                    startActivity(Intent(Intent.ACTION_VIEW, wikipediaUrl.toUri()))
                }
            }
        }
        binding.wikipediaDescription.text = wikipediaDescriptionString

        if (wikipediaImagesCount > 0) {
            wikipediaImagesList = MutableList(wikipediaImagesCount) { WikipediaImageModel() }

            binding.wikipediaImageListView.apply {
                itemAnimator = null
                layoutManager = LinearLayoutManager(
                    this@MainActivity,
                    LinearLayoutManager.HORIZONTAL,
                    false,
                )
                adapter = WikipediaListAdapter(
                    wikipediaImagesList,
                ).also { wikipediaListAdapter = it }
            }

            fetchItemAtIndex(0)
        }

        binding.wikipediaContainer.visibility = View.VISIBLE

        binding.wikipediaContainer.post {
            // Now that the panel is laid out, lift the logo above it and fly to the landmark.
            updateFocusViewport()
            flyTo(currentLandmark)
        }
    }

    enum class TLoadState {
        ENotRequested,
        ELoading,
        EPendingReloading,
        EFailed,
        ELoaded,
    }

    data class WikipediaImageModel(
        var status: TLoadState = TLoadState.ENotRequested,
        var bitmap: Bitmap? = null,
    )

    inner class WikipediaListAdapter(private val dataSet: MutableList<WikipediaImageModel>) :
        RecyclerView.Adapter<WikipediaListAdapter.ImageViewHolder>() {
        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(
                viewGroup.context,
            ).inflate(R.layout.wiki_image_list_item, viewGroup, false)
            return ImageViewHolder(view)
        }

        override fun getItemCount(): Int = dataSet.size

        inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val imageView: ImageView = view.findViewById(R.id.wiki_image)
            private val progressBar: ProgressBar = view.findViewById(R.id.wiki_image_progress)

            fun bind(position: Int) {
                dataSet[position].apply {
                    if (status == TLoadState.ELoaded) {
                        imageView.apply {
                            layoutParams.apply {
                                width = ConstraintLayout.LayoutParams.WRAP_CONTENT
                                height = ConstraintLayout.LayoutParams.WRAP_CONTENT
                            }

                            maxHeight = standardHeight
                            setImageBitmap(bitmap)
                        }

                        progressBar.visibility = View.GONE
                    } else {
                        imageView.apply {
                            layoutParams.apply {
                                height = standardHeight
                                width = standardHeight
                            }

                            setImageBitmap(null)
                        }

                        progressBar.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    inner class WikipediaImagesProgressListener : ProgressListener() {
        var index = 0
        var retryCount = 0
        var image: Image? = SdkCall.execute { Image() }

        override fun notifyComplete(errorCode: ErrorCode, hint: String) {
            if (errorCode != GemError.NoError) {
                if (retryCount > 0) {
                    // retry
                    retryCount--
                    wikipediaImagesList[index].status = TLoadState.EPendingReloading
                    image?.let {
                        externalInfoService.requestWikiImage(
                            this,
                            it,
                            index,
                            imageQuality,
                        )
                    }
                } else {
                    // fail
                    wikipediaImagesList[index].status = TLoadState.EFailed

                    // start fetching next item
                    if (index + 1 < wikipediaImagesList.size) {
                        fetchItemAtIndex(index + 1)
                    }
                }

                return
            }

            // success
            wikipediaImagesList[index].status = TLoadState.ELoaded
            SdkCall.execute {
                val imageWidth =
                    image?.size?.let { (it.width.toFloat() / it.height * standardHeight).toInt() }
                        ?: 0
                wikipediaImagesList[index].bitmap = image?.asBitmap(imageWidth, standardHeight)
            }

            Util.postOnMain { wikipediaListAdapter?.notifyItemRangeChanged(0, index + 1) }

            // start fetching next item
            if (index + 1 < wikipediaImagesList.size) {
                fetchItemAtIndex(index + 1)
            }
        }

        override fun notifyStart(hasProgress: Boolean) {
            wikipediaImagesList[index].status = TLoadState.ELoading
        }
    }
}

object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("LocationWikiResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
