/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.gpxthumbnailimage

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemOffscreenSurfaceView
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.EViewCameraTransitionStatus
import com.magiclane.sdk.d3scene.EViewDataTransitionStatus
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.OverlayService
import com.magiclane.sdk.examples.gpxthumbnailimage.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.gpxthumbnailimage.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var gemOffscreenSurfaceView: GemOffscreenSurfaceView

    private var screenshotTaken = false

    private val thumbnailWidth by lazy {
        resources.getDimension(R.dimen.thumbnail_width).toInt()
    }

    private val thumbnailHeight by lazy {
        resources.getDimension(R.dimen.thumbnail_height).toInt()
    }

    private val padding by lazy {
        resources.getDimension(R.dimen.padding).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val error = GemSdk.initSdkWithDefaults(this)
        if (error != GemError.NoError) {
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        gemOffscreenSurfaceView = GemOffscreenSurfaceView(thumbnailWidth, thumbnailHeight, resources.displayMetrics.densityDpi, onDefaultMapViewCreated = { mapView ->
            mapView.preferences?.apply {
                mapLabelsFading = false
                trafficVisibility = false
            }
        })

        binding.statusText.text = getString(R.string.waiting_for_data)

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                binding.statusText.text = getString(R.string.map_data_ready)

                SdkCall.execute {
                    val gpxAssetsFileName = "gpx/test_route.gpx"

                    // Opens GPX input stream.
                    val input = applicationContext.resources.assets.open(gpxAssetsFileName)

                    // Produce a Path based on the data in the buffer.
                    val path = Path.produceWithGpx(input) ?: return@execute

                    showPath(path)
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

    private fun showPath(path: Path) {
        gemOffscreenSurfaceView.mapView?.let { mapView ->
            val coordinatesList = path.coordinates
            if (!coordinatesList.isNullOrEmpty()) {
                val departureLmk = Landmark("", coordinatesList.first()).also {
                    it.image = ImageDatabase().getImageById(SdkImages.Core.Waypoint_Start.value)
                }
                val destinationLmk = Landmark("", coordinatesList.last()).also {
                    it.image = ImageDatabase().getImageById(SdkImages.Core.Waypoint_Finish.value)
                }

                val highlightSettings = HighlightRenderSettings(EHighlightOptions.ShowLandmark).also { it.imageSize = 4.0 }
                mapView.activateHighlightLandmarks(arrayListOf(departureLmk, destinationLmk), highlightSettings)
            }

            val pathCollection = mapView.preferences?.paths
            pathCollection?.add(path,
                                colorBorder = Rgba.black(),
                                colorInner = Rgba.orange(),
                                szBorder = 0.5,
                                szInner = 1.0)

            path.area?.let { area ->
                mapView.centerOnRectArea(
                    area = area,
                    viewRc = Rect(padding, padding, thumbnailWidth - padding, thumbnailHeight - padding),
                    animation = Animation(EAnimation.Linear,
                                         10,
                                          onCompleted = onCompleted@{ errorCode, _ ->
                                                if (errorCode != GemError.NoError) return@onCompleted

                                                SdkCall.execute {
                                                    OverlayService().apply {
                                                        disableOverlay(ECommonOverlayId.SocialReports.value)
                                                        disableOverlay(ECommonOverlayId.Safety.value)
                                                    }

                                                    gemOffscreenSurfaceView.screen?.needsRender()

                                                    mapView.onViewRendered = onViewRendered@{ tivStatus, camStatus ->
                                                        if (screenshotTaken) return@onViewRendered

                                                        if ((tivStatus == EViewDataTransitionStatus.Complete) &&
                                                            (camStatus == EViewCameraTransitionStatus.Stationary)) {
                                                            Util.postOnMain {
                                                                binding.statusText.text = getString(R.string.taking_screenshot)
                                                            }

                                                            gemOffscreenSurfaceView.takeScreenshot { bitmap ->
                                                                Util.postOnMain {
                                                                    binding.apply {
                                                                        mapThumbnailImage.setImageBitmap(bitmap)
                                                                        progressBar.isVisible = false
                                                                        statusText.text = getString(R.string.screenshot_taken)
                                                                    }
                                                                }

                                                                screenshotTaken = true
                                                                gemOffscreenSurfaceView.destroy()
                                                            }

                                                            mapView.onViewRendered = null
                                                        }
                                                    }
                                                }
                                          })
                )
            }
        }
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
}
