/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidautoroutenavigation.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.examples.androidautoroutenavigation.R
import com.magiclane.sdk.examples.androidautoroutenavigation.activities.controllers.MainActivityController
import com.magiclane.sdk.examples.androidautoroutenavigation.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.androidautoroutenavigation.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : BaseActivity() {
    companion object {
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private val controller = MainActivityController(this)
    lateinit var binding: ActivityMainBinding

    val mapView: MapView?
        get() = binding.gemSurface.mapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        controller.onCreate()
        registerSdkListeners()

        onBackPressedDispatcher.addCallback(this) {
            controller.onBackPressed()
        }
    }

    override fun onDestroy() {
        clearSdkListeners()
        super.onDestroy()
        controller.onDestroy()
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }

        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        binding.gemSurface.onDefaultMapViewCreated = {
            updateFocusViewport()
            controller.onDefaultMapViewCreated()
        }

        binding.gemSurface.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurface.apply {
            onSdkInitFailed = null
            onDefaultMapViewCreated = null
            onSurfaceChanged = null
        }
    }

    // Sets mapView.preferences.focusViewport to the screen area that is not covered by system bars
    // (status bar, navigation bar, display cutout), so SDK overlays (e.g. the logo) are positioned
    // within the truly visible region. Must be called whenever the map view or surface size changes.
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        controller.onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent ?: return
        controller.onNewIntent(intent)
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

    // Posts a UI action to the main thread only when the activity is still alive,
    // preventing crashes from callbacks arriving after onDestroy.
    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain {
            if (isActivityAlive()) block()
        }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
