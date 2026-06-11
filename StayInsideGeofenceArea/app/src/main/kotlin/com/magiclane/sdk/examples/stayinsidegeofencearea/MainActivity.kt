/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.stayinsidegeofencearea

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Geofence
import com.magiclane.sdk.core.GeofenceArea
import com.magiclane.sdk.core.GeofenceAreaList
import com.magiclane.sdk.core.Login
import com.magiclane.sdk.core.PolygonGeographicArea
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.examples.stayinsidegeofencearea.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.stayinsidegeofencearea.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private companion object {
        // Shared between the geofence area definition and the routing preference.
        const val GEOFENCE_AREA_NAME = "My Polygon Area 4"
    }

    private lateinit var binding: ActivityMainBinding

    private var padding = 0

    // Presents the calculated route fitted within the geofence-constrained viewport.
    private val routingService = RoutingService(
        onCompleted = { routes, errorCode, _ ->
            binding.progressBar.visibility = View.GONE
            when (errorCode) {
                GemError.NoError -> SdkCall.execute {
                    binding.gemSurfaceView.mapView?.presentRoutes(
                        routes,
                        edgeAreaInsets = getEdgeAreaInsets(),
                    )
                }
                GemError.Cancel -> {}
                else -> showDialog(
                    getString(
                        R.string.routing_service_error,
                        SdkCall.runSynced {
                            GemError.getMessage(errorCode, this)
                        },
                    ),
                )
            }
        },
    )

    // Verifies the app token; on success registers the external login to unlock geofence APIs.
    private val checkAuthorizationListener = ProgressListener.create(
        onCompleted = { errorCode, _ ->
            if (errorCode != GemError.NoError) {
                binding.progressBar.visibility = View.GONE
                showInvalidTokenDialog()
            } else {
                binding.progressBar.visibility = View.VISIBLE
                SdkCall.execute {
                    val error = Login.registerExternalLogin(
                        "__my_spceial_login_id__",
                        loginProgressListener,
                    )
                    if (error != GemError.NoError) {
                        Util.postOnMain {
                            showDialog(
                                getString(
                                    R.string.register_external_login_error,
                                    SdkCall.runSynced { GemError.getMessage(error, this) },
                                ),
                            )
                        }
                    }
                }
            }
        },
    )

    private val geofence = Geofence()

    private var geofenceAreas: GeofenceAreaList = arrayListOf()

    // Renders the polygon boundary on the map, then triggers route calculation.
    private val addAreasProgressListener = ProgressListener.create(onCompleted = { error, _ ->
        if (error != GemError.NoError) {
            binding.progressBar.visibility = View.GONE
            showDialog(
                getString(R.string.add_geofence_area_error, SdkCall.runSynced { GemError.getMessage(error, this) }),
            )
        } else {
            SdkCall.execute {
                binding.gemSurfaceView.mapView?.let { mapView ->
                    val polygonSettings = MarkerCollectionRenderSettings(
                        polylineInnerColor = Rgba.magenta(),
                        polygonFillColor = Rgba(255, 0, 0, 128),
                    )
                    polygonSettings.polylineInnerSize = 1.0 // mm
                    val polygonCollection = MarkerCollection(EMarkerType.Polygon, "Polygon")

                    for (geofenceArea in geofenceAreas) {
                        if (geofenceArea.area is PolygonGeographicArea) {
                            val polygonArea = geofenceArea.area as PolygonGeographicArea
                            polygonArea.coordinates?.let { coordinates ->
                                polygonCollection.add(Marker(coordinates))
                            }
                        }
                    }

                    mapView.preferences?.markers?.add(polygonCollection, polygonSettings)

                    calculateRoute()
                }
            }
        }
    })

    // Defines the polygon geofence area and registers it with the SDK.
    private val loginProgressListener = ProgressListener.create(onCompleted = { error, _ ->
        if (error != GemError.NoError) {
            binding.progressBar.visibility = View.GONE
            showDialog(getString(R.string.login_error, SdkCall.runSynced { GemError.getMessage(error, this) }))
        } else {
            SdkCall.execute {
                geofenceAreas = arrayListOf(
                    GeofenceArea(
                        PolygonGeographicArea(
                            arrayListOf(
                                Coordinates(45.650094, 25.610541),
                                Coordinates(45.670987, 25.598290),
                                Coordinates(45.674495, 25.522401),
                                Coordinates(45.676566, 25.495187),
                                Coordinates(45.629350, 25.467848),
                                Coordinates(45.594964, 25.440387),
                                Coordinates(45.566928, 25.432997),
                                Coordinates(45.503191, 25.489674),
                                Coordinates(45.469958, 25.574339),
                                Coordinates(45.489716, 25.596981),
                                Coordinates(45.522181, 25.559891),
                                Coordinates(45.519453, 25.525565),
                                Coordinates(45.557287, 25.506421),
                                Coordinates(45.582583, 25.486980),
                                Coordinates(45.621080, 25.503182),
                                Coordinates(45.638192, 25.536653),
                                Coordinates(45.652250, 25.567659),
                                Coordinates(45.661346, 25.585036),
                                Coordinates(45.655824, 25.593762),
                                Coordinates(45.648166, 25.599659),
                                Coordinates(45.650094, 25.610541),
                            ),
                        ),
                        GEOFENCE_AREA_NAME,
                    ),
                )

                val addAreasError = geofence.addAreas(geofenceAreas, addAreasProgressListener)
                if (addAreasError != GemError.NoError) {
                    Util.postOnMain {
                        showDialog(
                            getString(
                                R.string.add_geofence_area_error,
                                SdkCall.runSynced { GemError.getMessage(addAreasError, this) },
                            ),
                        )
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

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        padding = resources.getDimensionPixelSize(R.dimen.map_padding)

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            binding.progressBar.visibility = View.GONE
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkListeners()

        GemSdk.release()
        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            runOnUiThread {
                showDialog(getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this)))
            }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = {
            updateFocusViewport()
        }

        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onApiTokenRejected = { showInvalidTokenDialog() }

        // Self-clearing: verifies the app token on the first successful internet connection.
        SdkSettings.onConnectionStatusUpdated = { isConnected ->
            if (isConnected) {
                SdkSettings.appAuthorization?.let {
                    SdkCall.execute { SdkSettings.verifyAppAuthorization(it, checkAuthorizationListener) }
                } ?: showInvalidTokenDialog()
                SdkSettings.onConnectionStatusUpdated = {}
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        SdkSettings.onConnectionStatusUpdated = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // this adjusts Magic Lane logo position on map
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurfaceView.mapView?.preferences?.focusViewport = getFocusViewport()
        }
    }

    private fun getEdgeAreaInsets(): Rect {
        val root = binding.root
        val sysInsets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val left = (sysInsets?.left ?: 0) + padding
        val right = (sysInsets?.right ?: 0) + padding
        val bottom = (sysInsets?.bottom ?: 0) + padding
        val top = (binding.toolbar.bottom.takeIf { it > 0 } ?: (sysInsets?.top ?: 0)) + padding
        return Rect(left, top, right, bottom)
    }

    private fun getFocusViewport(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val left = insets?.left ?: 0
        val right = (width - (insets?.right ?: 0)).coerceAtLeast(left)
        val top = if (binding.toolbar.bottom > 0) binding.toolbar.bottom else (insets?.top ?: 0)
        val bottom = (height - (insets?.bottom ?: 0)).coerceAtLeast(top)
        return Rect(left, top, right, bottom)
    }

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Brasov", 45.65094531, 25.60403406),
            Landmark("Predeal", 45.5052, 25.5742),
        )

        // Restrict the route to roads within the registered geofence polygon.
        routingService.preferences.stickInsideGeofenceAreas = arrayListOf(GEOFENCE_AREA_NAME)

        val error = routingService.calculateRoute(waypoints)
        if (error != GemError.NoError) {
            Util.postOnMain {
                showDialog(
                    getString(R.string.calculate_route_error, SdkCall.runSynced { GemError.getMessage(error, this) }),
                )
            }
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

    private fun showInvalidTokenDialog() {
        runOnAliveUi { showDialog(getString(R.string.invalid_token)) { finish() } }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
