/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
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
import com.magiclane.sdk.routesandnavigation.Route
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
    }

    private lateinit var binding: ActivityMainBinding

    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

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
        }
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
        initView()
        configureSdkInitFailureHandler()
        configureRoadMapStatusHandler()
        configureApiTokenRejectedHandler()
        validateInternetConnection()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun initView() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun configureSdkInitFailureHandler() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }
    }

    private fun configureRoadMapStatusHandler() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                registerExternalLogin()
            }
        }
    }

    private fun registerExternalLogin() = SdkCall.execute {
        val error = Login.registerExternalLogin(
            getString(R.string.external_login_id),
            loginProgressListener,
        )
        if (error != GemError.NoError) {
            postGemErrorDialog(R.string.register_external_login_error, error)
        }
    }

    private fun configureApiTokenRejectedHandler() {
        SdkSettings.onApiTokenRejected = {
            runOnUiThread {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun validateInternetConnection() {
        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
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
                postGemErrorDialog(R.string.add_areas_error, addAreasError)
            }
        }
    }

    private fun onNavigationStarted() {
        SdkCall.execute {
            binding.gemSurfaceView.mapView?.let { mapView ->
                navRoute?.let { route ->
                    mapView.presentRoute(route)
                }

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
            postGemErrorDialog(R.string.start_simulation_error, error)
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

    private fun createPolygonRenderSettings() =
        MarkerCollectionRenderSettings(
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

    private fun createGeofenceAreas(): GeofenceAreaList =
        arrayListOf(
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

    private fun createSimulationWaypoints() =
        arrayListOf(
            Landmark(getString(R.string.waypoint_brasov), 45.65094531, 25.60403406),
            Landmark(getString(R.string.waypoint_predeal), 45.5052, 25.5742),
        )

    private fun getGeofenceAreaIds(): ArrayList<String> =
        arrayListOf(
            getString(R.string.circle_area_1_id),
            getString(R.string.circle_area_2_id),
        )

    private fun postGemErrorDialog(@StringRes messageResId: Int, error: Int) {
        Util.postOnMain {
            showGemErrorDialog(messageResId, error)
        }
    }

    private fun showGemErrorDialog(@StringRes messageResId: Int, error: Int) {
        showDialog(getString(messageResId, GemError.getMessage(error)))
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
