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
                            getString(R.string.search_completed_with_error, GemError.getMessage(errorCode, this)),
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

        mapInsetPaddingPx = resources.getDimension(R.dimen.padding_40).toInt()

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        clearSdkListeners()

        // Release the SDK before the activity is fully destroyed.
        GemSdk.release()

        super.onDestroy()
        exitProcess(0)
    }

    private fun flyTo(landmark: Landmark) = SdkCall.execute {
        landmark.geographicArea?.let { area ->
            binding.gemSurfaceView.mapView?.let { mapView ->
                // Center the map on a specific area using the provided animation.
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

                // Define highlight settings for displaying the area contour on map.
                val settings = HighlightRenderSettings(EHighlightOptions.ShowContour)

                // Highlights a specific area on the map using the provided settings.
                mapView.activateHighlightLandmarks(landmark, settings)
            }
        }
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

    private fun showStatusMessage(text: String) {
        if (!isActivityAlive()) return

        binding.apply {
            if (!statusText.isVisible) {
                statusText.visibility = View.VISIBLE
            }
            statusText.text = text
        }
    }

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

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) { finish() }
            }
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                SdkCall.execute {
                    val demoSearchCenter = Coordinates(40.68925476, -74.04456329)
                    searchService.searchByFilter(demoSearchQuery, demoSearchCenter)
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
}
