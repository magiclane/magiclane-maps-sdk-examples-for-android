/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.ptroutingonmap

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.ptroutingonmap.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.ptroutingonmap.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mapInsetPaddingPx = 0

    // Waypoint constants for the example route
    private companion object {
        private const val SAN_FRANCISCO_NAME = "San Francisco"
        private const val SAN_FRANCISCO_LAT = 37.77903
        private const val SAN_FRANCISCO_LON = -122.41991

        private const val SAN_JOSE_NAME = "San Jose"
        private const val SAN_JOSE_LAT = 37.33619
        private const val SAN_JOSE_LON = -121.89058
    }

    private val routingService = RoutingService(
        onStarted = { onRoutingStarted() },
        onCompleted = { routes, errorCode, unused -> onRoutingCompleted(routes, errorCode, unused) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeUI()
        registerSdkListeners()
        checkInternetConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    private fun initializeUI() {
        // Set status bar icons to white
        WindowCompat.getInsetsController(window, binding.root).isAppearanceLightStatusBars = false

        // Load map inset padding
        mapInsetPaddingPx = resources.getDimension(R.dimen.padding_40).toInt()
    }

    private fun checkInternetConnection() {
        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    private fun onRoutingStarted() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun onRoutingCompleted(
        routes: ArrayList<Route>,
        errorCode: Int,
        @Suppress("UNUSED_PARAMETER") unused: Any?,
    ) {
        binding.progressBar.visibility = View.GONE

        when (errorCode) {
            GemError.NoError -> displayRoutesOnMap(routes)
            GemError.Cancel -> showDialog("The routing action was cancelled.")
            else -> showDialog("Routing service error: ${GemError.getMessage(errorCode)}")
        }
    }

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = listOf(
            Landmark(SAN_FRANCISCO_NAME, SAN_FRANCISCO_LAT, SAN_FRANCISCO_LON),
            Landmark(SAN_JOSE_NAME, SAN_JOSE_LAT, SAN_JOSE_LON),
        )

        routingService.preferences.transportMode = ERouteTransportMode.Public
        routingService.calculateRoute(ArrayList(waypoints))
    }

    private fun displayRoutesOnMap(routes: ArrayList<Route>) = SdkCall.execute {
        binding.gemSurfaceView.mapView?.presentRoutes(routes, edgeAreaInsets = getFreeSpaceInsetsRect())
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        if (!isActivityAlive()) return

        val dialog = BottomSheetDialog(this)
        DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.error)
            message.text = text
            button.setOnClickListener {
                onDismiss?.invoke()
                dialog.dismiss()
            }
            dialog.apply {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isDraggable = false
                setCancelable(false)
                setContentView(root)
                show()
            }
        }
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                calculateRoute()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
    }

    private fun getFreeSpaceInsetsRect(): Rect {
        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val left = (insets?.left ?: 0) + mapInsetPaddingPx
        val right = (insets?.right ?: 0) + mapInsetPaddingPx
        val bottom = (insets?.bottom ?: 0) + mapInsetPaddingPx
        val top = (binding.toolbar.bottom.takeIf { it > 0 } ?: (insets?.top ?: 0)) + mapInsetPaddingPx

        return Rect(left, top, right, bottom)
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
