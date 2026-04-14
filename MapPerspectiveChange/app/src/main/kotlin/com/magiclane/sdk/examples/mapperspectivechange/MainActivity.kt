/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapperspectivechange

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EMapViewPerspective
import com.magiclane.sdk.examples.mapperspectivechange.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.mapperspectivechange.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.SdkCall
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var currentPerspective: EMapViewPerspective = EMapViewPerspective.TwoDimensional

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.surfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }

        binding.button.setOnClickListener {
            // Get the map view.
            binding.surfaceView.mapView?.let { mapView ->
                // Establish the current map view perspective.
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
                    // Change the map view perspective.
                    mapView.preferences?.setMapViewPerspective(
                        currentPerspective,
                        Animation(EAnimation.Linear, 300),
                    )
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        SdkSettings.onApiTokenRejected = {}

        // Deinitialize the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        if (isFinishing || isDestroyed) return

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
