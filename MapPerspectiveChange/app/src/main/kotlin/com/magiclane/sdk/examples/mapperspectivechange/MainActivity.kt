/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapperspectivechange

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EMapViewPerspective
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.examples.mapperspectivechange.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.mapperspectivechange.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // Tracks the perspective currently applied to the map so a tap toggles to the other one.
    private var currentPerspective: EMapViewPerspective = EMapViewPerspective.TwoDimensional

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        setupUi()
        registerSdkListeners()
    }

    override fun onDestroy() {
        clearSdkListeners()

        // Deinitialize the SDK.
        GemSdk.release()

        super.onDestroy()
        exitProcess(0)
    }

    private fun setupUi() {
        binding.button.setOnClickListener {
            togglePerspective()
        }
    }

    // Registers all SDK surface and settings callbacks.
    private fun registerSdkListeners() {
        binding.surfaceView.onSdkInitFailed = { error ->
            // This call is synchronized, so resolve the error message directly (no SdkCall wrapping is required).
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showErrorDialog(errorMessage) { finish() }
            }
        }

        binding.surfaceView.onDefaultMapViewCreated = {
            // Align the Magic Lane logo with the system window insets on first map creation.
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. on rotation).
        binding.surfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi {
                showErrorDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    // Clears SDK-level listeners to avoid callbacks reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        binding.surfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    /**
     * Toggles the map between the 2D and 3D perspectives, updating the button icon and its
     * content description to reflect the action the next tap will perform.
     */
    private fun togglePerspective() {
        val mapView = binding.surfaceView.mapView ?: return

        // Pick the perspective to switch to and reflect the opposite action on the button.
        currentPerspective = if (currentPerspective == EMapViewPerspective.TwoDimensional) {
            binding.button.setIconResource(R.drawable.ic_perspective_2d)
            binding.button.contentDescription = getString(R.string.switch_to_two_dimensional)
            EMapViewPerspective.ThreeDimensional
        } else {
            binding.button.setIconResource(R.drawable.ic_perspective_3d)
            binding.button.contentDescription = getString(R.string.switch_to_three_dimensional)
            EMapViewPerspective.TwoDimensional
        }

        SdkCall.execute {
            // Animate the transition to the newly selected perspective.
            mapView.preferences?.setMapViewPerspective(
                currentPerspective,
                Animation(EAnimation.Linear, 300),
            )
        }
    }

    /**
     * Adjusts the map's focus viewport so the Magic Lane logo (anchored to the viewport)
     * respects the system window insets instead of being hidden behind the toolbar or
     * system bars. Re-run whenever the surface size or insets may have changed.
     */
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView: MapView = binding.surfaceView.mapView ?: return@runSynced
            val viewport = mapView.viewport ?: return@runSynced
            val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

            val left = insets?.left ?: 0
            val top = insets?.top ?: 0
            val right = (viewport.width - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (viewport.height - (insets?.bottom ?: 0)).coerceAtLeast(top)
            mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
        }
    }

    /** Shows a non-dismissable bottom-sheet error dialog. */
    @SuppressLint("InflateParams")
    private fun showErrorDialog(text: String, onDismiss: (() -> Unit)? = null) {
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

    // Posts the block to the main thread, running it only while the activity is still alive.
    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain {
            if (isActivityAlive()) {
                block()
            }
        }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    companion object {
        // Window insets that the map's focus viewport should stay clear of.
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }
}
