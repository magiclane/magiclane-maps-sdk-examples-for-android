/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.avoidgeofencearea

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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

    companion object {
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        private const val FREE_SPACE_INFLATE_DP = 80
    }

    private lateinit var binding: ActivityMainBinding

    // Routing service that handles route calculation with geofence avoidance.
    private val routingService = RoutingService(
        onStarted = {
            showStatusMessage(getString(R.string.calculating_route), withProgress = true)
        },
        onCompleted = onCompleted@{ routes, errorCode, _ ->
            showStatusMessage(getString(R.string.route_calculation_completed))

            if (errorCode != GemError.NoError) {
                runOnAliveUi {
                    showDialog(
                        getString(
                            R.string.route_calculation_failed,
                            SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                        ),
                    ) {
                        finish()
                        exitProcess(0)
                    }
                }
                return@onCompleted
            }

            SdkCall.execute {
                if (routes.isNotEmpty()) {
                    binding.gemSurface.mapView?.presentRoute(
                        routes[0],
                        edgeAreaInsets = getEdgeAreaInsets(),
                    )
                }
            }
        },
    )

    private val geofence = Geofence()

    private var geofenceAreas: GeofenceAreaList = arrayListOf()

    // Listener invoked once geofence areas have been registered; renders them on the map and starts routing.
    private val addAreasProgressListener = ProgressListener.create(onCompleted = { error, _ ->
        if (error != GemError.NoError) {
            runOnAliveUi {
                showDialog(
                    getString(R.string.add_area_failed, SdkCall.runSynced { GemError.getMessage(error, this) }),
                ) {
                    finish()
                    exitProcess(0)
                }
            }
        } else {
            SdkCall.execute {
                binding.gemSurface.mapView?.let { mapView ->
                    val polygonSettings = MarkerCollectionRenderSettings(
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

    // Listener invoked after external login completes; sets up geofence areas on success.
    private val loginProgressListener = ProgressListener.create(onCompleted = { error, _ ->
        if (error != GemError.NoError) {
            runOnAliveUi {
                showDialog(getString(R.string.login_failed, SdkCall.runSynced { GemError.getMessage(error, this) })) {
                    finish()
                    exitProcess(0)
                }
            }
        } else {
            SdkCall.execute {
                geofenceAreas = arrayListOf(
                    GeofenceArea(
                        CircleGeographicArea(Coordinates(45.5950875, 25.6359825), 1000),
                        getString(R.string.area_to_avoid),
                    ),
                )
                val addError = geofence.addAreas(geofenceAreas, addAreasProgressListener)
                if (addError != GemError.NoError) {
                    runOnAliveUi {
                        showDialog(
                            getString(
                                R.string.cant_add_area,
                                SdkCall.runSynced { GemError.getMessage(addError, this) },
                            ),
                        ) {
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

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        registerSdkListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_init_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        // Adjust the Magic Lane logo position once the map view is ready.
        binding.gemSurface.onDefaultMapViewCreated = { _ ->
            updateFocusViewport()
            if (!Util.isInternetConnected(this)) {
                showStatusMessage(getString(R.string.internet_required))
            } else {
                showStatusMessage(getString(R.string.waiting_for_map_data))
            }
        }

        // Re-adjust after rotation or other surface size changes.
        binding.gemSurface.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkCall.execute {
                    val error = Login.registerExternalLogin(
                        "__my_spceial_login_id__",
                        loginProgressListener,
                    )
                    if (error != GemError.NoError) {
                        runOnAliveUi {
                            showDialog(
                                getString(
                                    R.string.external_login_error,
                                    SdkCall.runSynced { GemError.getMessage(error, this) },
                                ),
                            ) {
                                finish()
                                exitProcess(0)
                            }
                        }
                    }
                }
                // Self-clear: only the first map-ready event matters.
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected)) }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurface.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // Adjusts the Magic Lane logo position to respect system window insets and the status panel.
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
            // Use the status panel height when visible, system bar inset otherwise.
            val bottom = if (binding.statusText.isVisible) {
                val panelHeight = binding.statusText.height.takeIf { it > 0 } ?: binding.statusText.measuredHeight
                (h - panelHeight).coerceAtLeast(top)
            } else {
                (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
        }
    }

    // Returns edge insets (px) for presentRoute: top uses the toolbar bottom, bottom uses the
    // status panel height when visible, all other sides use system bar / cutout insets,
    // with FREE_SPACE_INFLATE_DP added to every edge.
    private fun getEdgeAreaInsets(): Rect {
        val (left, top, right, bottom) = resolveMapPadding()
        return Rect(left, top, right, bottom)
    }

    // Shared padding values (px) for getEdgeAreaInsets.
    // Top is toolbar.bottom (which already absorbs the status-bar height); bottom uses the status
    // panel height when visible, system bar inset otherwise. FREE_SPACE_INFLATE_DP on every edge.
    private fun resolveMapPadding(): Array<Int> {
        val sysInsets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(SYSTEM_INSET_TYPES)
        val inflate = (FREE_SPACE_INFLATE_DP * resources.displayMetrics.density).toInt()
        val toolbarBottom = binding.toolbar.bottom.takeIf { it > 0 } ?: 0
        // Use the status panel height when visible, system bar inset otherwise.
        val bottomInset = if (binding.statusText.isVisible) {
            binding.statusText.height.takeIf { it > 0 } ?: binding.statusText.measuredHeight
        } else {
            sysInsets?.bottom ?: 0
        }
        return arrayOf(
            (sysInsets?.left ?: 0) + inflate,
            toolbarBottom + inflate,
            (sysInsets?.right ?: 0) + inflate,
            bottomInset + inflate,
        )
    }

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Brasov", 45.65094531, 25.60403406),
            Landmark("Predeal", 45.5052, 25.5742),
        )

        routingService.preferences.avoidGeofenceAreas = arrayListOf(getString(R.string.area_to_avoid))

        val error = routingService.calculateRoute(waypoints)
        if (error != GemError.NoError) {
            runOnAliveUi {
                showDialog(
                    getString(
                        R.string.route_calculation_failed,
                        SdkCall.runSynced { GemError.getMessage(error, this) },
                    ),
                ) {
                    finish()
                    exitProcess(0)
                }
            }
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

    private fun showStatusMessage(text: String, withProgress: Boolean = false) {
        Util.postOnMain {
            binding.apply {
                if (!statusText.isVisible) {
                    statusText.visibility = View.VISIBLE
                }
                statusText.text = text
                statusProgressBar.visibility = if (withProgress) View.VISIBLE else View.GONE
                // Re-run after layout so the logo clears the panel's new height.
                statusText.post { updateFocusViewport() }
            }
        }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
