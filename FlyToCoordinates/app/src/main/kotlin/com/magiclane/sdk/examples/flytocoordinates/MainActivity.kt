/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.flytocoordinates

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.examples.flytocoordinates.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.flytocoordinates.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

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

    // Registers all SDK surface and settings callbacks.
    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(errorMessage) { finish() } }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = { _ ->
            // Align the Magic Lane logo with system window insets on first map creation.
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                // Fire once; clear itself to avoid repeated triggers.
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                SdkCall.runSynced {
                    val landmark = Landmark("Magic Lane", 45.65112176095828, 25.60473923113322)
                    highlightLandmarkOnMap(landmark, getFreeSpaceRect())
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
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // Adjusts the Magic Lane logo position to respect system window insets and toolbar height.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView = binding.gemSurfaceView.mapView ?: return@runSynced
            val viewport = mapView.viewport ?: return@runSynced
            val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

            val w = viewport.width
            val h = viewport.height
            val left = insets?.left ?: 0
            val topInset = insets?.top ?: 0
            val toolbarBottom = binding.toolbar.bottom.takeIf { it > 0 } ?: 0
            val top = maxOf(topInset, toolbarBottom)
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
        }
    }

    private fun highlightLandmarkOnMap(landmark: Landmark, freeSpaceRect: Rect) {
        binding.gemSurfaceView.mapView?.let { mapView ->
            mapView.deactivateAllHighlights()

            landmark.image = ImageDatabase().getImageById(SdkImages.Core.Search_Results_Pin.value)

            val highlightSettings = HighlightRenderSettings(
                EHighlightOptions.ShowLandmark,
            ).also {
                it.imageSize = 6.0
            }

            landmark.coordinates?.let { coords ->
                mapView.centerOnCoordinates(
                    coords,
                    -1,
                    freeSpaceRect.center,
                    Animation(EAnimation.Linear, 900),
                    0.0,
                    0.0,
                )
            }

            mapView.activateHighlightLandmarks(
                landmark,
                highlightSettings,
            )
        }
    }

    // Returns the visible map area excluding system bars and toolbar — used for coordinate centering.
    private fun getFreeSpaceRect(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsets(SYSTEM_INSET_TYPES)

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val left = insets?.left ?: 0
        val right = (width - (insets?.right ?: 0)).coerceAtLeast(left)

        val topInset = insets?.top ?: 0
        val toolbarBottom = binding.toolbar.bottom.takeIf { it > 0 } ?: 0
        val top = maxOf(topInset, toolbarBottom)
        val bottom = (height - (insets?.bottom ?: 0)).coerceAtLeast(top)

        return Rect(left, top, right, bottom)
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
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
