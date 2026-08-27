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
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
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

    // Where the lane guidance is displayed; pick the placement here at compile time.
    val laneInfoPlacement = LaneInfoPlacement.NearEtaPanel

    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            progressBarIsVisible = true
        },
        onCompleted = { errorCode, _ ->
            progressBarIsVisible = false

            if (errorCode != GemError.NoError) {
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

    fun onNavigationEnded(errorCode: Int) {
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
