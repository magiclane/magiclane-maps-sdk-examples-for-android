/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.favourites

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
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

    private var imageSize: Int = 0

    private var leftInset = 0

    private var rightInset = 0

    private var bottomInset = 0

    private var inflate = 0

    private var toolbarHeight = 0

    private var bottomDialogHeight = 0

    // Define a Landmark Store so we can write the favourite landmarks in the data folder.
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

                        showLocationDetailsPanel(GemUtil.formatName(landmark), GemUtil.getLandmarkDescription(landmark, true)) {
                            highlightLandmarkOnMap(landmark, getFreeScreenRect(), isFavourite(landmark))
                        }

                        binding.statusText.visibility = View.GONE
                    } else {
                        showStatusMessage(getString(R.string.no_search_results))
                    }
                }
                else -> {
                    showStatusMessage(getString(R.string.search_completed_with_error, GemError.getMessage(errorCode, this)))
                }
            }
            EspressoIdlingResource.decrement()
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageSize = resources.getDimensionPixelSize(R.dimen.image_size)
        inflate = resources.getDimension(R.dimen.padding_40).toInt()

        // Measure app bar height after layout
        binding.toolbar.post {
            toolbarHeight = binding.toolbar.height
        }

        // Set up window insets listener
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            leftInset = systemBars.left + inflate
            rightInset = systemBars.right + inflate
            bottomInset = systemBars.bottom + inflate
            insets
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = { mapView ->
            store = LandmarkStoreService().createLandmarkStore("Favourites")?.first
            store?.let {
                mapView.preferences?.landmarkStores?.addAllStoreCategories(it.id)
            }
        }

        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        EspressoIdlingResource.increment()

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                SdkCall.execute {
                    searchService.searchByFilter("Statue of Liberty New York", Coordinates(40.68925476, -74.04456329))
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            showDialog(getString(R.string.token_rejected_message))
        }

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
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
            if (!statusText.isVisible) {
                statusText.visibility = View.VISIBLE
            }
            statusText.text = text
        }
    }

    private fun getFavouriteId(landmark: Landmark): Int = SdkCall.execute {
        /**
         * Get the ID of the landmark saved in the store so we can use it to remove it
         * or to check if it's already a favourite.
         */
        val radius = 5.0 // meters
        val area = landmark.coordinates?.let { RectangleGeographicArea(it, radius, radius) }
        val landmarks = area?.let { store?.getLandmarksByArea(it) } ?: return@execute -1

        val threshold = 0.00001
        landmarks.forEach {
            val itCoordinates = it.coordinates
            val landmarkCoordinates = landmark.coordinates

            if (itCoordinates != null && landmarkCoordinates != null) {
                if ((itCoordinates.latitude - landmarkCoordinates.latitude < threshold) && (itCoordinates.longitude - landmarkCoordinates.longitude < threshold)) return@execute it.id
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
        ImageDatabase().getImageById(
            SdkImages.Engine_Misc.LocationDetails_FavouritePushPin.value,
        )?.let {
            lmk.image = it
        }

        // Add the landmark to the desired LandmarkStore
        store?.addLandmark(lmk)
    }

    private fun deleteFromFavourites(landmarkId: Int) = SdkCall.execute {
        // Remove the landmark associated to this ID from the LandmarkStore.
        store?.removeLandmark(landmarkId)
    }

    private fun setFavouriteButtonIcon(button: MaterialButton, isFavourite: Boolean) {
        val bmp = SdkCall.execute {
            if (isFavourite) {
                ContextCompat.getDrawable(this, R.drawable.baseline_star_24)
            } else {
                ContextCompat.getDrawable(this, R.drawable.baseline_star_border_24)
            }
        }

        bmp?.let {
            button.icon = bmp
        }
    }

    private fun getFreeScreenRect(): Rect {
        return Rect(
            leftInset,
            toolbarHeight + inflate,
            binding.root.width - rightInset,
            binding.root.height - bottomDialogHeight - inflate,
        )
    }

    private fun highlightLandmarkOnMap(landmark: Landmark, rect: Rect, isFavorite: Boolean, flyToLandmark: Boolean = true) = SdkCall.execute {
        binding.gemSurfaceView.mapView?.let { mapView ->
            mapView.deactivateAllHighlights()

            landmark.image = ImageDatabase().getImageById(SdkImages.Core.Search_Results_Pin.value)

            val contour = landmark.getContourGeographicArea()
            var highlightSettings: HighlightRenderSettings

            @Suppress("VerboseNullabilityAndEmptiness")
            if ((contour != null) && !contour.isEmpty()) {
                if (flyToLandmark) {
                    binding.gemSurfaceView.mapView?.centerOnRectArea(
                        contour,
                        zoomLevel = -1,
                        viewRc = rect,
                        Animation(EAnimation.Linear, 900)
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
                ).also {
                    it.imageSize = 6.0
                }

                mapView.activateHighlightLandmarks(
                    landmark,
                    highlightSettings
                )
            } else {
                if (flyToLandmark) {
                    landmark.coordinates?.let {
                        binding.gemSurfaceView.mapView?.centerOnCoordinates(
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
                    highlightSettings = HighlightRenderSettings(
                        EHighlightOptions.ShowLandmark,
                    ).also {
                        it.imageSize = 6.0
                    }

                    mapView.activateHighlightLandmarks(
                        landmark,
                        highlightSettings
                    )
                }
            }
        }
    }

    private fun showLocationDetailsPanel(
        title: String,
        message: String,
        onViewCreated: (() -> Unit)? = null
    ) {
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

                highlightLandmarkOnMap(landmark, getFreeScreenRect(), isFavourite, false)
            }

            // Show the panel
            root.visibility = View.VISIBLE

            // Measure height after it's shown
            root.post {
                bottomDialogHeight = root.height
                onViewCreated?.invoke()
            }
        }
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
