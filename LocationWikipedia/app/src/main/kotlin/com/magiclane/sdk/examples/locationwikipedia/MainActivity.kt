/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.locationwikipedia

import android.content.Intent
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
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
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
    }

    private lateinit var binding: ActivityMainBinding

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
                        showDialog(
                            getString(
                                R.string.search_completed_with_error,
                                GemError.getMessage(errorCode, this@MainActivity),
                            ),
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
        EspressoIdlingResource.increment()

        mapInsetPaddingPx = resources.getDimension(R.dimen.padding_40).toInt()

        binding.wikipediaContainer.layoutParams.height =
            (Resources.getSystem().displayMetrics.heightPixels * BOTTOM_SHEET_HEIGHT_RATIO).toInt()

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

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

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
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
    }

    override fun onDestroy() {
        clearSdkListeners()
        super.onDestroy()

        // Release the SDK before the activity is fully destroyed.
        GemSdk.release()
        exitProcess(0)
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
                    viewRc = getFreeScreenRect(),
                    animation = Animation(EAnimation.Linear, FLY_TO_ANIMATION_DURATION_MS),
                )

                val settings = HighlightRenderSettings(EHighlightOptions.ShowContour)
                mapView.activateHighlightLandmarks(landmark, settings)
            }
        }
    }

    private fun getFreeScreenRect(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val width = root.width.takeIf { it > 0 } ?: Resources.getSystem().displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: Resources.getSystem().displayMetrics.heightPixels

        val left = insets?.left ?: 0
        val right = (width - (insets?.right ?: 0)).coerceAtLeast(left)

        val topInset = insets?.top ?: 0
        val toolbarBottom = binding.toolbar.bottom.takeIf { it > 0 } ?: 0
        val top = max(topInset, toolbarBottom)

        val insetBottom = height - (insets?.bottom ?: 0)

        // Account for visible Wikipedia panel at the bottom
        val bottom = if (binding.wikipediaContainer.isVisible && binding.wikipediaContainer.top > 0) {
            binding.wikipediaContainer.top.coerceAtMost(insetBottom)
        } else {
            insetBottom
        }.coerceAtLeast(top)

        val mapFocusPadding = mapInsetPaddingPx.takeIf { it > 0 } ?: 0
        val paddedLeft = left + mapFocusPadding
        val paddedTop = top + mapFocusPadding
        val paddedRight = (right - mapFocusPadding).coerceAtLeast(paddedLeft)
        val paddedBottom = (bottom - mapFocusPadding).coerceAtLeast(paddedTop)

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
            // Fly to the landmark now that the panel is displayed
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
