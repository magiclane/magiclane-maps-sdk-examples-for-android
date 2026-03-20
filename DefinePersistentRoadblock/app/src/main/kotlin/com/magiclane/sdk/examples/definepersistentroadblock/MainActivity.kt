/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
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
import androidx.core.view.WindowInsetsCompat
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

    private var toolbarHeight = 0
    private var leftInset = 0
    private var rightInset = 0
    private var bottomInset = 0
    private var inflate = 0

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var gemSurfaceView: GemSurfaceView

    private var roadblock: TrafficEvent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        gemSurfaceView = binding.gemSurfaceView

        inflate = resources.getDimension(R.dimen.padding_40).toInt()

        // Measure app bar height after layout
        binding.toolbar.post {
            toolbarHeight = binding.toolbar.height
        }

        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        gemSurfaceView.onDefaultMapViewCreated = { mapView ->
            mapView.onTouch = { xy ->
                SdkCall.execute {
                    // tell the map view where the touch event happened
                    gemSurfaceView.mapView?.cursorScreenPosition = xy

                    val trafficEvents = gemSurfaceView.mapView?.cursorSelectionTrafficEvents
                    if (!trafficEvents.isNullOrEmpty()) {
                        val trafficEvent = trafficEvents[0]
                        if (trafficEvent.isRoadblock) {
                            return@execute
                        }
                    }

                    val streets = gemSurfaceView.mapView?.cursorSelectionStreets
                    if (!streets.isNullOrEmpty()) {
                        streets[0].coordinates?.let { addPersistentRoadblock(it) }
                    }
                }
            }

            Util.postOnMain {
                binding.hint.visibility = View.VISIBLE
            }
        }

        SdkSettings.onApiTokenRejected = {
            showDialog(getString(R.string.token_rejected_message))
        }

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.no_internet_message))
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

        // Release the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun getFreeSpaceRectangle(): Rect {
        return Rect(
            leftInset,
            toolbarHeight + inflate,
            binding.root.width - rightInset,
            binding.root.height - bottomInset,
        )
    }

    private fun addPersistentRoadblock(coordinates: Coordinates) {
        val startTime = Time.getUniversalTime()
        val endTime = Time.getUniversalTime().also { endTime -> endTime?.let { it.minute += 1 } }

        if ((startTime != null) && (endTime != null)) {
            val traffic = Traffic()

            roadblock?.let { roadblock ->
                roadblock.referencePoint?.let { coordinates ->
                    traffic.removePersistentRoadblock(coordinates)
                }
            }

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
                        getFreeSpaceRectangle(),
                        animation = Animation(EAnimation.Linear, duration = 900),
                    )
                }

                Util.postOnMain { binding.hint.visibility = View.GONE }
            }
        }
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
}
