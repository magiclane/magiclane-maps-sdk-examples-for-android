/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.flytoarea

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.examples.flytoarea.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.flytoarea.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        // Combines status bar, navigation bar, and display cutout insets for logo placement.
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private val demoSearchQuery = "Statue of Liberty New York"
    private val flyToAnimationDurationMs = 900

    private lateinit var binding: ActivityMainBinding

    private var mapInsetPaddingPx = 0

    private val searchService = SearchService(
        onStarted = {
            runOnAliveUi {
                binding.progressBar.visibility = View.VISIBLE
                showStatusMessage(getString(R.string.searching))
            }
        },

        onCompleted = { results, errorCode, _ ->
            runOnAliveUi {
                binding.progressBar.visibility = View.GONE

                when (errorCode) {
                    GemError.NoError -> {
                        if (results.isNotEmpty()) {
                            flyTo(results[0])
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
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        mapInsetPaddingPx = resources.getDimension(R.dimen.padding_40).toInt()

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

    private fun flyTo(landmark: Landmark) = SdkCall.execute {
        landmark.geographicArea?.let { area ->
            binding.gemSurfaceView.mapView?.let { mapView ->
                // Center the map on the landmark's geographic area using a linear animation.
                mapView.centerOnRectArea(
                    area,
                    zoomLevel = -1,
                    viewRc = getFreeScreenRect(),
                    Animation(EAnimation.Linear, flyToAnimationDurationMs, onStarted = {
                        showStatusMessage(getString(R.string.fly_to_area_started))
                    }, onCompleted = { _, _ ->
                        showStatusMessage(getString(R.string.fly_to_area_completed))
                    }),
                )

                // Highlight the landmark's area contour on the map.
                mapView.activateHighlightLandmarks(landmark, HighlightRenderSettings(EHighlightOptions.ShowContour))
            }
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

    private fun showStatusMessage(text: String) {
        if (!isActivityAlive()) return
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = text
        // Re-position the logo now that the status panel has become visible.
        updateFocusViewport()
    }

    // Computes the usable map rect excluding the toolbar above and the status text overlay below.
    private fun getFreeScreenRect(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val left = insets?.left ?: 0
        val right = (width - (insets?.right ?: 0)).coerceAtLeast(left)

        val topInset = insets?.top ?: 0
        val toolbarBottom = binding.toolbar.bottom.takeIf { it > 0 } ?: 0
        val top = maxOf(topInset, toolbarBottom)

        val insetBottom = height - (insets?.bottom ?: 0)
        val statusTop = if (binding.statusText.isVisible && binding.statusText.top > 0) {
            binding.statusText.top
        } else {
            insetBottom
        }
        val bottom = minOf(insetBottom, statusTop).coerceAtLeast(top)

        val paddedLeft = left + mapInsetPaddingPx
        val paddedTop = top + mapInsetPaddingPx
        val paddedRight = (right - mapInsetPaddingPx).coerceAtLeast(paddedLeft)
        val paddedBottom = (bottom - mapInsetPaddingPx).coerceAtLeast(paddedTop)

        return Rect(paddedLeft, paddedTop, paddedRight, paddedBottom)
    }

    // Registers all SDK surface and settings callbacks.
    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(errorMessage) { finish() } }
        }

        // Align the Magic Lane logo with system window insets on first map creation.
        binding.gemSurfaceView.onDefaultMapViewCreated = { _ ->
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. device rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                // One-shot: clear after map data is confirmed up to date, then trigger search.
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                SdkCall.execute {
                    val demoSearchCenter = Coordinates(40.68925476, -74.04456329)
                    val errorCode = searchService.searchByFilter(demoSearchQuery, demoSearchCenter)

                    // A non-NoError result means the search never started, so onCompleted
                    // won't fire to clear the progress bar — surface the error here instead.
                    if (errorCode != GemError.NoError) {
                        val errorMessage = GemError.getMessage(errorCode, this)
                        runOnAliveUi {
                            binding.progressBar.visibility = View.GONE
                            showStatusMessage(
                                getString(R.string.search_completed_with_error, errorMessage),
                            )
                        }
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // Clears SDK-level listeners to prevent callbacks from reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = null
        }
    }

    // Adjusts the Magic Lane logo position to respect system window insets and the status text overlay.
    // Uses post{} so the status panel's top coordinate is settled before we read it.
    private fun updateFocusViewport() {
        binding.root.post {
            SdkCall.runSynced {
                val mapView = binding.gemSurfaceView.mapView ?: return@runSynced
                val viewport = mapView.viewport ?: return@runSynced
                val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

                val w = viewport.width
                val h = viewport.height
                val left = insets?.left ?: 0
                val top = insets?.top ?: 0
                val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)

                val insetBottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
                val statusTop = if (binding.statusText.isVisible && binding.statusText.top > 0) {
                    binding.statusText.top
                } else {
                    insetBottom
                }
                val bottom = minOf(insetBottom, statusTop).coerceAtLeast(top)

                mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
            }
        }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
