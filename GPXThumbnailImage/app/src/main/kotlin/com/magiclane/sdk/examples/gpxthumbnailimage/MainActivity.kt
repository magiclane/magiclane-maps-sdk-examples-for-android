/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.gpxthumbnailimage

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
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

    // Guards against taking more than one screenshot per lifecycle.
    private var screenshotTaken = false

    private val thumbnailWidth by lazy { resources.getDimension(R.dimen.thumbnail_width).toInt() }
    private val thumbnailHeight by lazy { resources.getDimension(R.dimen.thumbnail_height).toInt() }
    private val padding by lazy { resources.getDimension(R.dimen.padding).toInt() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val error = GemSdk.initSdkWithDefaults(this)
        if (error != GemError.NoError) {
            val msg = getString(
                R.string.sdk_initialization_failed,
                SdkCall.runSynced { GemError.getMessage(error, this) },
            )
            runOnAliveUi {
                showDialog(msg) {
                    finish()
                    exitProcess(0)
                }
            }
            return
        }

        gemOffscreenSurfaceView = GemOffscreenSurfaceView(
            thumbnailWidth,
            thumbnailHeight,
            resources.displayMetrics.densityDpi,
            onDefaultMapViewCreated = { mapView ->
                // Position the Magic Lane logo within the offscreen render viewport.
                updateFocusViewport()
                mapView.preferences?.apply {
                    mapLabelsFading = false
                    trafficVisibility = false
                }
            },
        )

        binding.statusText.text = getString(R.string.waiting_for_data)
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

    // Registers all SDK settings callbacks for map data and token events.
    private fun registerSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                // Clear the listener after the first UpToDate event — no need to react again.
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                runOnAliveUi { binding.statusText.text = getString(R.string.map_data_ready) }

                SdkCall.execute {
                    val input = applicationContext.resources.assets.open("gpx/test_route.gpx")
                    val path = Path.produceWithGpx(input) ?: return@execute
                    showPath(path)
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // Clears SDK-level listeners to avoid callbacks reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
    }

    // Adjusts the Magic Lane logo position to fill the full offscreen render viewport.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView = gemOffscreenSurfaceView.mapView ?: return@runSynced
            val viewport = mapView.viewport ?: return@runSynced
            mapView.preferences?.focusViewport = Rect(0, 0, viewport.width, viewport.height)
        }
    }

    // Called from SdkCall.execute — already on the SDK thread; no additional runSynced needed.
    private fun showPath(path: Path) {
        val mapView = gemOffscreenSurfaceView.mapView ?: return

        // Place start/finish landmark icons at the first and last GPX coordinates.
        val coordinatesList = path.coordinates
        if (!coordinatesList.isNullOrEmpty()) {
            val departureLmk = Landmark("", coordinatesList.first()).also {
                it.image = ImageDatabase().getImageById(SdkImages.Core.Waypoint_Start.value)
            }
            val destinationLmk = Landmark("", coordinatesList.last()).also {
                it.image = ImageDatabase().getImageById(SdkImages.Core.Waypoint_Finish.value)
            }
            val highlightSettings = HighlightRenderSettings(EHighlightOptions.ShowLandmark)
                .also { it.imageSize = 4.0 }
            mapView.activateHighlightLandmarks(arrayListOf(departureLmk, destinationLmk), highlightSettings)
        }

        mapView.preferences?.paths?.add(
            path,
            colorBorder = Rgba.black(),
            colorInner = Rgba.orange(),
            szBorder = 0.5,
            szInner = 1.0,
        )

        path.area?.let { area ->
            mapView.centerOnRectArea(
                area = area,
                viewRc = Rect(padding, padding, thumbnailWidth - padding, thumbnailHeight - padding),
                animation = Animation(
                    EAnimation.Linear,
                    10,
                    // onCompleted is invoked on the main thread, so SDK calls need SdkCall.execute/runSynced.
                    onCompleted = onCompleted@{ errorCode, _ ->
                        if (errorCode != GemError.NoError) {
                            val msg = SdkCall.runSynced { GemError.getMessage(errorCode, this@MainActivity) }.orEmpty()
                            runOnAliveUi { showDialog(msg) }
                            return@onCompleted
                        }

                        SdkCall.execute {
                            OverlayService().apply {
                                disableOverlay(ECommonOverlayId.SocialReports.value)
                                disableOverlay(ECommonOverlayId.Safety.value)
                            }

                            gemOffscreenSurfaceView.screen?.needsRender()

                            // Wait for the map to finish loading and the camera to settle.
                            mapView.onViewRendered = onViewRendered@{ tivStatus, camStatus ->
                                if (screenshotTaken) return@onViewRendered

                                if (tivStatus == EViewDataTransitionStatus.Complete &&
                                    camStatus == EViewCameraTransitionStatus.Stationary
                                ) {
                                    runOnAliveUi {
                                        binding.statusText.text = getString(R.string.taking_screenshot)
                                    }

                                    gemOffscreenSurfaceView.takeScreenshot { bitmap ->
                                        runOnAliveUi {
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
                    },
                ),
            )
        }
    }

    /** Shows a non-dismissable bottom-sheet error dialog. */
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
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
