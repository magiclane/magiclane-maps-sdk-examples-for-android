/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.monitorgeofencearea

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.CircleGeographicArea
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Geofence
import com.magiclane.sdk.core.GeofenceArea
import com.magiclane.sdk.core.GeofenceAreaList
import com.magiclane.sdk.core.GeofenceListener
import com.magiclane.sdk.core.Login
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.examples.monitorgeofencearea.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.monitorgeofencearea.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.sensordatasource.PositionPublishingPreferences
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    companion object {
        private const val CIRCLE_RADIUS_METERS = 25
        private const val POSITION_PUBLISH_INTERVAL_SECONDS = 1
        private const val POLYLINE_INNER_SIZE_MM = 1.0
        private const val POLYGON_COLLECTION_NAME = "Polygon"

        // Window insets that the Magic Lane logo must stay clear of.
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: ActivityMainBinding

    private val navigationService = NavigationService()

    private val geofence = Geofence()

    private var geofenceAreas: GeofenceAreaList = arrayListOf()

    private val addAreasProgressListener = ProgressListener.create(onCompleted = { error, _ ->
        onAddAreasCompleted(error)
    })

    private val loginProgressListener = ProgressListener.create(onCompleted = { error, _ ->
        onLoginCompleted(error)
    })

    private val monitoringAreasListener = GeofenceListener.create(onEnterArea = { userId, areaId ->
        showToast(getString(R.string.enter_area_message, userId.toString(), areaId))
    }, onExitArea = { userId, areaId ->
        showToast(getString(R.string.exit_area_message, userId.toString(), areaId))
    })

    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            onNavigationStarted()
        },
        onDestinationReached = {
            onNavigationEnded()
        },
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { _, _ ->
            binding.progressBar.visibility = View.GONE
        },

        postOnMain = true,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        registerSdkListeners()
        validateInternetConnection()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Detach listeners before releasing the SDK so no callback reaches a destroyed activity.
        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    // Registers all SDK surface and settings callbacks.
    private fun registerSdkListeners() {
        // SDK failed to initialize: the SDK is not running yet, so resolve the message directly.
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(errorMessage) { finish() } }
        }

        // Align the Magic Lane logo with the system insets once the map view exists.
        binding.gemSurfaceView.onDefaultMapViewCreated = { _ ->
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. on rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        // Once worldwide road map data is up to date, log in to enable geofencing.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                registerExternalLogin()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // Clears SDK-level listeners to avoid callbacks reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    private fun registerExternalLogin() = SdkCall.execute {
        val error = Login.registerExternalLogin(
            getString(R.string.external_login_id),
            loginProgressListener,
        )
        if (error != GemError.NoError) {
            showGemErrorDialog(R.string.register_external_login_error, error)
        }
    }

    private fun validateInternetConnection() {
        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    // Adjusts the Magic Lane logo position to respect system window insets.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView = binding.gemSurfaceView.mapView ?: return@runSynced
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

    private fun onAddAreasCompleted(error: Int) {
        if (error != GemError.NoError) {
            showGemErrorDialog(R.string.add_area_progress_listener_error, error)
            return
        }

        SdkCall.execute {
            binding.gemSurfaceView.mapView?.let { mapView ->
                val polygonSettings = createPolygonRenderSettings()
                val polygonCollection = createPolygonCollection()

                mapView.preferences?.markers?.add(polygonCollection, polygonSettings)

                startAreaMonitoring()
                startSimulation()
            }
        }
    }

    private fun onLoginCompleted(error: Int) {
        if (error != GemError.NoError) {
            showGemErrorDialog(R.string.login_progress_listener_error, error)
            return
        }

        SdkCall.execute {
            geofenceAreas = createGeofenceAreas()
            val addAreasError = geofence.addAreas(geofenceAreas, addAreasProgressListener)
            if (addAreasError != GemError.NoError) {
                showGemErrorDialog(R.string.add_areas_error, addAreasError)
            }
        }
    }

    private fun onNavigationStarted() {
        SdkCall.execute {
            binding.gemSurfaceView.mapView?.let { mapView ->
                setFollowCursorButton()
                mapView.followPosition()
            }
        }
    }

    private fun onNavigationEnded() {
        binding.followCursorButton.visibility = View.GONE

        SdkCall.execute {
            binding.gemSurfaceView.mapView?.hideRoutes()
        }
    }

    private fun setFollowCursorButton() {
        // Set actions for entering/ exiting following position mode.
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followCursorButton.visibility = View.VISIBLE
                }

                onEnterFollowingPosition = {
                    followCursorButton.visibility = View.GONE
                }

                // Set on click action for the GPS button.
                followCursorButton.setOnClickListener {
                    SdkCall.execute { followPosition() }
                }
            }
        }
    }

    private fun startSimulation() = SdkCall.execute {
        val waypoints = createSimulationWaypoints()
        val error = navigationService.startSimulation(
            waypoints,
            navigationListener,
            routingProgressListener,
        )
        if (error != GemError.NoError) {
            showGemErrorDialog(R.string.start_simulation_error, error)
        }
    }

    private fun startAreaMonitoring() {
        PositionService.positionPublishingPreferences = PositionPublishingPreferences(
            true,
            POSITION_PUBLISH_INTERVAL_SECONDS,
            false,
            EDataType.ImprovedPosition,
        )
        geofence.startMonitoringAreas(monitoringAreasListener, getGeofenceAreaIds())
    }

    private fun createPolygonRenderSettings() = MarkerCollectionRenderSettings(
        polylineInnerColor = Rgba.magenta(),
        polygonFillColor = Rgba(255, 0, 0, 128),
    ).apply {
        polylineInnerSize = POLYLINE_INNER_SIZE_MM // mm
    }

    private fun createPolygonCollection(): MarkerCollection {
        val polygonCollection = MarkerCollection(EMarkerType.Polygon, POLYGON_COLLECTION_NAME)
        for (geofenceArea in geofenceAreas) {
            if (geofenceArea.area is CircleGeographicArea) {
                geofenceArea.area?.centerPoint?.let { centerPoint ->
                    polygonCollection.add(Marker(centerPoint, CIRCLE_RADIUS_METERS))
                }
            }
        }

        return polygonCollection
    }

    private fun createGeofenceAreas(): GeofenceAreaList = arrayListOf(
        GeofenceArea(
            CircleGeographicArea(Coordinates(45.65189844, 25.60438562), CIRCLE_RADIUS_METERS),
            getString(R.string.circle_area_1_id),
        ),
        GeofenceArea(
            CircleGeographicArea(
                Coordinates(45.65264, 25.60697719),
                CIRCLE_RADIUS_METERS,
            ),
            getString(R.string.circle_area_2_id),
        ),
    )

    private fun createSimulationWaypoints() = arrayListOf(
        Landmark(getString(R.string.waypoint_brasov), 45.65094531, 25.60403406),
        Landmark(getString(R.string.waypoint_predeal), 45.5052, 25.5742),
    )

    private fun getGeofenceAreaIds(): ArrayList<String> = arrayListOf(
        getString(R.string.circle_area_1_id),
        getString(R.string.circle_area_2_id),
    )

    // Resolves a GemError code on the SDK thread, then shows it in an error dialog on the UI thread.
    private fun showGemErrorDialog(@StringRes messageResId: Int, error: Int) {
        runOnAliveUi {
            val errorMessage = SdkCall.runSynced { GemError.getMessage(error, this) }
            showDialog(getString(messageResId, errorMessage))
        }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
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

    private fun showToast(message: String, length: Int = Toast.LENGTH_SHORT) {
        Util.postOnMain {
            Toast.makeText(this, message, length).show()
        }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
