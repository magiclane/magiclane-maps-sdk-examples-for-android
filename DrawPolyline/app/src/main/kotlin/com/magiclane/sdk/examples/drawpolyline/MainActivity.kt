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
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.examples.drawpolyline.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.drawpolyline.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    private lateinit var binding: ActivityMainBinding

    private var toolbarHeight = 0

    private var leftInset = 0

    private var rightInset = 0

    private var bottomInset = 0

    private var inflate = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inflate = resources.getDimension(R.dimen.padding_40).toInt()

        // Measure app bar height after layout
        binding.toolbar.post {
            toolbarHeight = binding.toolbar.height
        }

        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = String.format(getString(R.string.sdk_initialization_failed), GemError.getMessage(error, this))
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        EspressoIdlingResource.increment()
        binding.gemSurface.onDefaultMapViewCreated = {
            flyToPolyline()

            lifecycleScope.launch {
                delay(3000)
                EspressoIdlingResource.decrement()
            }
        }

        SdkSettings.onApiTokenRejected = {
            showDialog(getString(R.string.token_rejected_message))
        }

        // Set up window insets listener
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            leftInset = systemBars.left + inflate
            rightInset = systemBars.right + inflate
            bottomInset = systemBars.bottom + inflate
            insets
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
        exitProcess(0)
    }

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

    private fun flyToPolyline() {
        binding.gemSurface.mapView?.let { mapView ->
            /**
             * Make a MarkerCollection and a Marker item that will be stored in the collection.
             * You can create multiple Marker items that can be added in the same collection.
             */
            val markerCollection = MarkerCollection(EMarkerType.Polyline, "My marker collection")

            // Define a market item and add the necessary coordinates to it.
            val marker = Marker().apply {
                add(52.360234, 4.886782)
                add(52.360495, 4.886266)
                add(52.360854, 4.885539)
                add(52.361184, 4.884849)
                add(52.361439, 4.884344)
                add(52.361593, 4.883986)
            }

            // Add the marker item to the collection.
            markerCollection.add(marker)

            // Make a list of settings that will decide how each marker collection will be displayed on the map.
            val settings = MarkerCollectionRenderSettings(
                polylineInnerColor = Rgba.magenta(),
                polylineOuterColor = Rgba.black(),
            ).apply {
                polylineInnerSize = 1.25
                polylineOuterSize = 0.75
            }

            // Add the collection to the desired map view so it can be displayed.
            mapView.preferences?.markers?.add(markerCollection, settings)

            // Center the map on this marker collection's area.
            markerCollection.area?.let {
                mapView.centerOnRectArea(
                    area = it,
                    zoomLevel = -1,
                    getFreeSpaceRectangle(),
                    animation = Animation(EAnimation.Linear, duration = 900),
                )
            }
        }
    }

    private fun getFreeSpaceRectangle(): Rect {
        return Rect(
            leftInset,
            toolbarHeight + inflate,
            binding.root.width - rightInset,
            binding.root.height - bottomInset,
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
