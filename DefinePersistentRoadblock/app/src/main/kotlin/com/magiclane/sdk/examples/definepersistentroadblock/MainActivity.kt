/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.definepersistentroadblock

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.examples.definepersistentroadblock.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.definepersistentroadblock.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.Traffic
import com.magiclane.sdk.routesandnavigation.TrafficEvent
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var gemSurfaceView: GemSurfaceView

    // The currently active roadblock; kept so it can be removed before placing a new one.
    private var roadblock: TrafficEvent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        gemSurfaceView = binding.gemSurfaceView

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.no_internet_message))
        }
    }

    override fun onDestroy() {
        clearSdkListeners()
        super.onDestroy()
        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        GemSdk.release()
        exitProcess(0)
    }

    // ---- SDK listener registration -------------------------------------------

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
            updateFocusViewport()

            mapView.onTouch = { xy ->
                SdkCall.execute {
                    // Tell the map view where the touch happened so hit-testing is accurate.
                    gemSurfaceView.mapView?.cursorScreenPosition = xy

                    // Ignore taps on existing roadblocks; only bare street taps place new ones.
                    val trafficEvents = gemSurfaceView.mapView?.cursorSelectionTrafficEvents
                    if (!trafficEvents.isNullOrEmpty() && trafficEvents[0].isRoadblock) {
                        return@execute
                    }

                    val streets = gemSurfaceView.mapView?.cursorSelectionStreets
                    if (!streets.isNullOrEmpty()) {
                        streets[0].coordinates?.let { addPersistentRoadblock(it) }
                    }
                }
            }

            runOnAliveUi {
                binding.hint.visibility = View.VISIBLE
                // Defer until after the layout pass so binding.hint.top is valid.
                binding.hint.post { updateFocusViewport() }
            }
        }

        // Keep the Magic Lane logo viewport in sync whenever the map surface is resized.
        binding.gemSurfaceView.onSurfaceChanged = { _, _ -> updateFocusViewport() }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // ---- Logo viewport -------------------------------------------------------

    /** Updates the Magic Lane logo viewport to avoid overlapping with the toolbar. */
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurfaceView.mapView?.preferences?.focusViewport = getFocusViewport()
        }
    }

    private fun getFocusViewport(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val left = insets?.left ?: 0
        val top = binding.toolbar.bottom
        val right = (width - (insets?.right ?: 0)).coerceAtLeast(left)
        // When the hint banner is visible it covers the bottom of the map, so shrink the
        // viewport upward to keep the Magic Lane logo above it.
        val bottom = if (binding.hint.isVisible) {
            binding.hint.top.coerceAtLeast(top)
        } else {
            (height - (insets?.bottom ?: 0)).coerceAtLeast(top)
        }

        return Rect(left, top, right, bottom)
    }

    // ---- Roadblock placement --------------------------------------------------

    private fun addPersistentRoadblock(coordinates: Coordinates) {
        val startTime = Time.getUniversalTime()
        // The roadblock expires after 1 minute; incrementing the minute field is how the SDK API works.
        val endTime = Time.getUniversalTime().also { it?.minute += 1 }

        if (startTime == null || endTime == null) return

        val traffic = Traffic()

        // Remove the previous roadblock before placing the new one at the tapped location.
        roadblock?.referencePoint?.let { traffic.removePersistentRoadblock(it) }

        roadblock = traffic.addPersistentRoadblock(
            coords = arrayListOf(coordinates),
            startUTC = startTime,
            expireUTC = endTime,
            transportMode = ERouteTransportMode.Car.value,
        )

        if (roadblock?.referencePoint?.valid() == true) {
            roadblock?.boundingBox?.let {
                gemSurfaceView.mapView?.centerOnRectArea(
                    area = it,
                    zoomLevel = -1,
                    viewRc = getFreeSpaceRectangle(),
                    animation = Animation(EAnimation.Linear, duration = 900),
                )
            }
            runOnAliveUi {
                binding.hint.visibility = View.GONE
                // Hint gone; restore the full-height viewport.
                updateFocusViewport()
            }
        }
    }

    /** Returns the map area not covered by the toolbar or system bars, inset by extra padding. */
    private fun getFreeSpaceRectangle(): Rect {
        val padding = resources.getDimensionPixelSize(R.dimen.padding_40)
        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val left = (insets?.left ?: 0) + padding
        val right = (binding.root.width - (insets?.right ?: 0) - padding).coerceAtLeast(left + 1)
        val top = binding.toolbar.bottom + padding
        val bottom = (binding.root.height - (insets?.bottom ?: 0) - padding).coerceAtLeast(top + 1)

        return Rect(left, top, right, bottom)
    }

    // ---- Dialog --------------------------------------------------------------

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

    // ---- Utilities -----------------------------------------------------------

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain {
            if (!isFinishing && !isDestroyed) block()
        }
    }
}
