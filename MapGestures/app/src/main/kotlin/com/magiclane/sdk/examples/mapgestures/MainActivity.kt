/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapgestures

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.examples.mapgestures.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.mapgestures.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.SdkCall
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        private const val GESTURE_TAG = "Gesture"
    }

    @VisibleForTesting
    lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EspressoIdlingResource.increment()
        gemSurfaceView = binding.gemSurface

        gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }

        gemSurfaceView.onDefaultMapViewCreated = { mapView ->
            mapView.onDoubleTouch = {
                logGesture("onDoubleTouch at (${it.x}, ${it.y}).")
            }

            mapView.onLongDown = {
                logGesture("onLongDown at (${it.x}, ${it.y}).")
            }

            mapView.onMove = { start: Xy, end: Xy ->
                logGesture("onMove from (${start.x}, ${start.y}) to (${end.x}, ${end.y}).")
            }

            mapView.onPinch = { start1: Xy, start2: Xy, end1: Xy, end2: Xy, center: Xy ->
                logGesture(
                    "onPinch from " +
                        "(${start1.x}, ${start1.y}) and (${start2.x}, ${start2.y}) " +
                        "to " +
                        "(${end1.x}, ${end1.y}) and (${end2.x}, ${end2.y}) " +
                        "center (${center.x}, ${center.y}).",
                )
            }

            mapView.onSwipe = { distX: Int, distY: Int, speedMMPerSec: Double ->
                logGesture(
                    "onSwipe with " +
                        "$distX pixels on X and " +
                        "$distY pixels on Y and " +
                        "the speed of $speedMMPerSec mm/s.",
                )
            }

            mapView.onTouch = {
                logGesture("onTouch at (${it.x}, ${it.y}).")
            }

            mapView.onTwoTouches = {
                logGesture("onTwoTouches with middle point (${it.x}, ${it.y}).")
            }

            EspressoIdlingResource.decrement()
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    override fun onDestroy() {
        SdkSettings.onApiTokenRejected = {}
        super.onDestroy()

        // Release the SDK.
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

    private fun logGesture(message: String) {
        SdkCall.execute {
            Log.i(GESTURE_TAG, message)
        }
    }
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("MapGesturesIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
