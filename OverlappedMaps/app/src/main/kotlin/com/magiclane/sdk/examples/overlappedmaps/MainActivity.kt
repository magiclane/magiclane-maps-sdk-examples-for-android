/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.overlappedmaps

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.RectF
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.examples.overlappedmaps.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.overlappedmaps.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.SdkCall
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var secondMapView: MapView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureSystemBars()

        setupSdkCallbacks()
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.gemSurfaceView.onSdkInitFailed = {}
        }
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
        secondMapView = null

        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun setupSdkCallbacks() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            showErrorDialogOnUiThread(errorMessage) { finish() }
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                SdkCall.execute {
                    binding.gemSurfaceView.gemScreen?.let { screen ->
                        val secondViewRect = RectF(0.0f, 0.0f, 0.5f, 0.5f)
                        secondMapView = MapView.produce(screen, secondViewRect, null, true)
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            showErrorDialogOnUiThread(getString(R.string.token_rejected_message))
        }
    }

    private fun configureSystemBars() {
        // Keep status bar symbols white even with edge-to-edge content.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
    }

    private fun showErrorDialogOnUiThread(text: String, onDismiss: (() -> Unit)? = null) {
        runOnUiThread {
            showDialog(text, onDismiss)
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

    private fun isActivityAlive(): Boolean {
        return !isFinishing && !isDestroyed
    }
}
