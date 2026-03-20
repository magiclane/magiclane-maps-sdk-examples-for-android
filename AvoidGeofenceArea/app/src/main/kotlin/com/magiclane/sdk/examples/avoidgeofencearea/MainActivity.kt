/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.avoidgeofencearea

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.CircleGeographicArea
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Geofence
import com.magiclane.sdk.core.GeofenceArea
import com.magiclane.sdk.core.GeofenceAreaList
import com.magiclane.sdk.core.Login
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.examples.avoidgeofencearea.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.avoidgeofencearea.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

@Suppress("SameParameterValue")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var inset = 0

    private val routingService = RoutingService(
        onStarted = {
            showStatusMessage(getString(R.string.calculating_route), withProgress = true)
        },
        onCompleted = onCompleted@{ routes, errorCode, _ ->
            showStatusMessage(getString(R.string.route_calculation_completed))

            if (errorCode != GemError.NoError) {
                showDialog(getString(R.string.route_calculation_failed, GemError.getMessage(errorCode, this))) {
                    finish()
                    exitProcess(0)
                }
                return@onCompleted
            } else {
                SdkCall.execute {
                    if (routes.isNotEmpty()) {
                        binding.gemSurface.mapView?.presentRoute(
                            routes[0],
                            edgeAreaInsets = Rect(inset, inset, inset, inset),
                        )
                    }
                }
            }
        },
    )

    private val geofence = Geofence()

    private var geofenceAreas: GeofenceAreaList = arrayListOf()

    private val addAreasProgressListener = ProgressListener.create(onCompleted = { error, _ ->
        if (error != GemError.NoError) {
            showDialog(getString(R.string.add_area_failed, GemError.getMessage(error, this))) {
                finish()
                exitProcess(0)
            }
        } else {
            SdkCall.execute {
                binding.gemSurface.mapView?.let { mapView ->
                    val polygonSettings =
                        MarkerCollectionRenderSettings(
                            polylineInnerColor = Rgba.magenta(),
                            polygonFillColor = Rgba(255, 0, 0, 128),
                        )
                    polygonSettings.polylineInnerSize = 1.0 // mm
                    val polygonCollection = MarkerCollection(EMarkerType.Polygon, "Polygon")

                    for (geofenceArea in geofenceAreas) {
                        if (geofenceArea.area is CircleGeographicArea) {
                            geofenceArea.area?.centerPoint?.let {
                                val marker = Marker(it, 1000)
                                polygonCollection.add(marker)
                            }
                        }
                    }

                    mapView.preferences?.markers?.add(polygonCollection, polygonSettings)

                    calculateRoute()
                }
            }
        }
    })

    private val loginProgressListener = ProgressListener.create(onCompleted = { error, _ ->
        if (error != GemError.NoError) {
            showDialog(getString(R.string.login_failed, GemError.getMessage(error, this))) {
                finish()
                exitProcess(0)
            }
        } else {
            SdkCall.execute {
                geofenceAreas = arrayListOf(
                    GeofenceArea(
                        CircleGeographicArea(Coordinates(45.5950875, 25.6359825), 1000),
                        getString(R.string.area_to_avoid),
                    ),
                )
                val error = geofence.addAreas(geofenceAreas, addAreasProgressListener)
                if (error != GemError.NoError) {
                    Util.postOnMain {
                        showDialog(getString(R.string.cant_add_area, GemError.getMessage(error, this))) {
                            finish()
                            exitProcess(0)
                        }
                    }
                }
            }
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        inset = getSizeInPixels(85)

        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_init_failed, GemError.getMessage(error, this))
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        binding.gemSurface.onDefaultMapViewCreated = { _ ->
            Util.postOnMain {
                if (!Util.isInternetConnected(this)) {
                    showStatusMessage(getString(R.string.internet_required))
                }
                else {
                    showStatusMessage(getString(R.string.waiting_for_map_data))
                }
            }
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkCall.execute {
                    val error = Login.registerExternalLogin(
                        "__my_spceial_login_id__",
                        loginProgressListener,
                    )
                    if (error != GemError.NoError) {
                        Util.postOnMain {
                            showDialog(getString(R.string.external_login_error, GemError.getMessage(error, this))) {
                                finish()
                                exitProcess(0)
                            }
                        }
                    }
                }
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
            }
        }

        SdkSettings.onApiTokenRejected = {
            showDialog(getString(R.string.token_rejected))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Brasov", 45.65094531, 25.60403406),
            Landmark("Predeal", 45.5052, 25.5742),
        )

        routingService.preferences.avoidGeofenceAreas = arrayListOf(getString(R.string.area_to_avoid))

        val error = routingService.calculateRoute(waypoints)
        if (error != GemError.NoError) {
            Util.postOnMain {
                showDialog(getString(R.string.route_calculation_failed, GemError.getMessage(error, this))) {
                    finish()
                    exitProcess(0)
                }
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

    private fun getSizeInPixels(dpi: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dpi.toFloat(),
            resources.displayMetrics,
        ).toInt()
    }

    private fun showStatusMessage(text: String, withProgress: Boolean = false) {
        Util.postOnMain {
            binding.apply {
                if (!statusText.isVisible) {
                    statusText.visibility = View.VISIBLE
                }
                statusText.text = text

                if (withProgress) {
                    statusProgressBar.visibility = View.VISIBLE
                } else {
                    statusProgressBar.visibility = View.GONE
                }
            }
        }
    }
}
