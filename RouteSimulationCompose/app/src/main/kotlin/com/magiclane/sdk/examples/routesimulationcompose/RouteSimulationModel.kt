/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routesimulationcompose

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.util.SdkCall

/** Where the lane guidance is displayed. */
enum class LaneInfoPlacement {
    /** Along the bottom side of the top navigation instruction panel. */
    TopPanel,

    /** Standalone panel near the ETA panel: above it in portrait, right of it in landscape. */
    NearEtaPanel,
}

// The whole navigation UI pipeline (instruction panel, ETA, traffic banner, images)
// is provided by the maps-compose library (rememberNavigationUiState); this model only
// owns the navigation service and the simulation bootstrap.
class RouteSimulationModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    val navigationService = NavigationService()

    var errorMessage by mutableStateOf("")
    var progressBarIsVisible by mutableStateOf(false)

    // Whether the "Define roadblock" panel is on screen; raised by a tap on the route.
    var roadblockPanelIsVisible by mutableStateOf(false)

    // The unit system the roadblock lengths are offered in, read when the panel opens.
    var roadblockUnitSystem by mutableStateOf(EUnitSystem.Metric)
        private set

    // Where the lane guidance is displayed; pick the placement here at compile time.
    val laneInfoPlacement = LaneInfoPlacement.NearEtaPanel

    // The overlay only covers the initial route calculation. Later recalculations (a roadblock,
    // a route deviation) are already announced by the "Calculating..." status in the navigation
    // instruction panel, which the overlay would only cover.
    private var initialRouteCalculated = false

    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            progressBarIsVisible = !initialRouteCalculated
        },
        onCompleted = { errorCode, _ ->
            progressBarIsVisible = false

            if (errorCode == GemError.NoError) {
                initialRouteCalculated = true
            } else {
                errorMessage = app.getString(
                    R.string.route_simulation_error,
                    SdkCall.runSynced { GemError.getMessage(errorCode, app) },
                )
            }
        },
        postOnMain = true,
    )

    fun startSimulation(navigationListener: NavigationListener) = SdkCall.execute {
        if (navigationService.isSimulationActive(navigationListener)) return@execute

        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616),
        )

        val error = navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
        if (error != GemError.NoError) {
            errorMessage = app.getString(
                R.string.route_simulation_error,
                GemError.getMessage(error, app),
            )
        }
    }

    /**
     * Opens the "Define roadblock" panel when a tap lands on the navigated route. The cursor
     * selection has to run on the SDK thread, which is where the panel state is raised from.
     */
    fun registerRouteTouchHandler(mapState: GemMapState) {
        mapState.mapView?.onTouch = { xy ->
            SdkCall.execute {
                val mapView = mapState.mapView ?: return@execute
                mapView.cursorScreenPosition = xy

                if (!mapView.cursorSelectionRoutes.isNullOrEmpty()) {
                    roadblockUnitSystem = SdkSettings.unitSystem
                    roadblockPanelIsVisible = true
                }
            }
        }
    }

    /**
     * Blocks the given length of the route ahead of the current position, which makes the
     * navigation service recalculate the route around it. Ignored when the simulation is no longer
     * running by the time the choice is made.
     */
    fun setNavigationRoadblock(navigationListener: NavigationListener, lengthInMeters: Int) = SdkCall.execute {
        if (!navigationService.isSimulationActive(navigationListener)) return@execute

        navigationService.setNavigationRoadBlock(lengthInMeters)
    }

    fun onNavigationEnded(errorCode: Int) {
        // The route is gone, so is any chance to block it.
        roadblockPanelIsVisible = false

        if (errorCode != GemError.NoError && errorCode != GemError.Cancel) {
            errorMessage = app.getString(
                R.string.route_simulation_error,
                SdkCall.runSynced { GemError.getMessage(errorCode, app) },
            )
        }
    }

    // Keeps the followed position in the lower(-right in landscape) part of the free map
    // area. In landscape the bottom lane panel overlaps the resting position, so while
    // it is present the position is elevated above it. The horizontal landscape focus is
    // computed by the UI (landscapeCameraFocusX()), which knows the panel layout and insets.
    fun applyCameraFocus(mapState: GemMapState, isLandscape: Boolean, hasLanePanel: Boolean, landscapeFocusX: Float) {
        mapState.postToMap { mapView ->
            mapView.preferences?.followPositionPreferences?.cameraFocus = when {
                !isLandscape -> XyF(0.5f, 0.75f)
                hasLanePanel -> XyF(landscapeFocusX, 0.63f)
                else -> XyF(landscapeFocusX, 0.75f)
            }
        }
    }
}
