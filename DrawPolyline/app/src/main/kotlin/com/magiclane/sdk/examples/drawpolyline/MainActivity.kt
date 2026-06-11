/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.drawpolyline

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.examples.drawpolyline.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.drawpolyline.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    private lateinit var binding: ActivityMainBinding

    private var toolbarHeight = 0

    // Resolved once, after the view is inflated.
    private val inflate by lazy { resources.getDimension(R.dimen.padding_40).toInt() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        // Measure app bar height after layout, used for camera framing.
        binding.toolbar.post { toolbarHeight = binding.toolbar.height }

        EspressoIdlingResource.increment()

        registerSdkListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    // Registers all SDK surface and settings callbacks.
    private fun registerSdkListeners() {
        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        binding.gemSurface.onDefaultMapViewCreated = { mapView ->
            // Position the Magic Lane logo and draw the polyline on first map creation.
            updateFocusViewport()
            flyToPolyline(mapView)

            lifecycleScope.launch {
                delay(3000)
                EspressoIdlingResource.decrement()
            }
        }

        // Re-align the Magic Lane logo whenever the surface is resized (e.g. on rotation).
        binding.gemSurface.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // Clears SDK-level listeners to avoid callbacks reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurface.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // Adjusts the Magic Lane logo position to respect system window insets.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView = binding.gemSurface.mapView ?: return@runSynced
            val viewport = mapView.viewport ?: return@runSynced
            val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

            val w = viewport.width
            val h = viewport.height
            val left = insets?.left ?: 0
            val top = insets?.top ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
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

    private fun flyToPolyline(mapView: MapView) {
        /**
         * Make a MarkerCollection and a Marker item that will be stored in the collection.
         * You can create multiple Marker items that can be added in the same collection.
         */
        val markerCollection = MarkerCollection(EMarkerType.Polyline, "My marker collection")

        // Define a marker item and add the necessary coordinates to it.
        val marker = Marker().apply {
            add(52.360234, 4.886782)
            add(52.360495, 4.886266)
            add(52.360854, 4.885539)
            add(52.361184, 4.884849)
            add(52.361439, 4.884344)
            add(52.361593, 4.883986)
        }
        markerCollection.add(marker)

        // Configure how the polyline is rendered on the map.
        val settings = MarkerCollectionRenderSettings(
            polylineInnerColor = Rgba.magenta(),
            polylineOuterColor = Rgba.black(),
        ).apply {
            polylineInnerSize = 1.25
            polylineOuterSize = 0.75
        }

        mapView.preferences?.markers?.add(markerCollection, settings)

        // Animate the camera to frame the polyline, respecting UI chrome.
        markerCollection.area?.let {
            mapView.centerOnRectArea(
                area = it,
                zoomLevel = -1,
                getFreeSpaceRectangle(),
                animation = Animation(EAnimation.Linear, duration = 900),
            )
        }
    }

    // Returns the screen rect not covered by toolbars or system insets, for camera framing.
    private fun getFreeSpaceRectangle(): Rect {
        val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)
        return Rect(
            (insets?.left ?: 0) + inflate,
            toolbarHeight + inflate,
            binding.root.width - (insets?.right ?: 0) - inflate,
            binding.root.height - (insets?.bottom ?: 0) - inflate,
        )
    }
}

//region TESTING
@VisibleForTesting
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("DrawPolylineIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
