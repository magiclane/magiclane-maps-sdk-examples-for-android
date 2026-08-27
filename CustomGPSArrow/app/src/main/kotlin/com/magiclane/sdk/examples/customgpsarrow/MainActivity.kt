/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.customgpsarrow

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.ESceneObjectFileFormat
import com.magiclane.sdk.d3scene.MapSceneObject
import com.magiclane.sdk.d3scene.SceneObjectData
import com.magiclane.sdk.d3scene.SceneObjectDataList
import com.magiclane.sdk.examples.customgpsarrow.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.customgpsarrow.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val navigationService = NavigationService()

    // NavigationListener exposes additional callbacks beyond onNavigationStarted;
    // see the SDK documentation for the full list.
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                binding.gemSurface.mapView?.let { mapView ->
                    enableGPSButton()
                    mapView.followPosition()
                }
            }
            // Signals to Espresso that the async navigation-start operation is complete.
            EspressoIdlingResource.decrement()
        },
        onDestinationReached = { onNavigationEnded() },
        onNavigationError = { error -> onNavigationEnded(error) },
    )

    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { errorCode, _ ->
            binding.progressBar.visibility = View.GONE
            if (errorCode != GemError.NoError) {
                showDialog(
                    getString(
                        R.string.start_simulation_error,
                        SdkCall.runSynced { GemError.getMessage(errorCode, this@MainActivity) },
                    ),
                )
            }
        },

        postOnMain = true,
    )

    // ---- Lifecycle -----------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        EspressoIdlingResource.increment()

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkListeners()

        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        GemSdk.release()
        exitProcess(0)
    }

    // ---- SDK listener registration -------------------------------------------

    /** Registers all SDK surface and settings callbacks. Paired with [clearSdkListeners] in [onDestroy]. */
    private fun registerSdkListeners() {
        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_init_failed, GemError.getMessage(error, this))
            runOnUiThread { showDialog(errorMessage) { finish() } }
        }

        binding.gemSurface.onDefaultMapViewCreated = {
            // Position the logo before loading scene objects so it never overlaps the toolbar.
            updateFocusViewport()
            loadCustomArrow()
        }

        // Surface dimensions can change mid-session (e.g. entering split-screen), so re-apply
        // the logo viewport every time the surface is resized.
        binding.gemSurface.onSurfaceChanged = { _, _ -> updateFocusViewport() }

        // The callback is cleared before starting the simulation to prevent it from firing
        // a second time if the road-map status is updated again during the session.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
                startSimulation()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    /** Clears global SDK callbacks to prevent stale references after the Activity is destroyed. */
    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurface.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // ---- Custom arrow --------------------------------------------------------

    /** Loads the GLTF model from assets and applies it as the position-tracker arrow. */
    private fun loadCustomArrow() {
        val objList = getSceneObjs("quad.glb" to ESceneObjectFileFormat.Gltf)
        if (objList.isEmpty()) return

        val (obj, err) = MapSceneObject.getDefPositionTracker()
        if (GemError.isError(err)) {
            val message = GemError.getMessage(err, this)
            if (!message.isEmpty()) {
                runOnUiThread { showDialog(message) }
            }

            return
        }

        MapSceneObject.customizeDefPositionTracker(objList)
        obj?.scaleFactor = 1.0 // valid range: 0.0 – 5.0
    }

    /**
     * Reads asset files and wraps them as [SceneObjectDataList].
     * Each file is loaded independently so one failure does not abort the rest.
     */
    private fun getSceneObjs(vararg filesData: Pair<String, ESceneObjectFileFormat>): SceneObjectDataList {
        val list: SceneObjectDataList = arrayListOf()
        for ((fileName, format) in filesData) {
            try {
                val bytes = assets.open(fileName).use { it.readBytes() }
                if (bytes.isNotEmpty()) {
                    list.add(SceneObjectData(DataBuffer(bytes), format))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list
    }

    // ---- Simulation ----------------------------------------------------------

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616),
        )

        val errorCode = navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
        if (errorCode != GemError.NoError) {
            runOnUiThread {
                showDialog(
                    getString(
                        R.string.start_simulation_error,
                        SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                    ),
                )
            }
        }
    }

    /** Tears down the GPS follow button when navigation ends and surfaces any terminal error. */
    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if ((errorCode != GemError.NoError) && (errorCode != GemError.Cancel)) {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) } ?: ""
                if (message.isNotEmpty()) {
                    showDialog(message)
                }
            }
            disableGPSButton()
        }
    }

    // ---- Logo viewport -------------------------------------------------------

    /** Pushes the current focus viewport to the map so the Magic Lane logo stays below the toolbar. */
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurface.mapView?.preferences?.focusViewport = getFocusViewport()
        }
    }

    private fun getFocusViewport(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        // Use max/min to guard against the brief window after rotation where root dimensions
        // haven't yet reflected the new orientation.
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val w = if (isLandscape) max(width, height) else min(width, height)
        val h = if (isLandscape) min(width, height) else max(width, height)

        val left = insets?.left ?: 0
        val top = binding.toolbar.bottom
        val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
        val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
        return Rect(left, top, right, bottom)
    }

    // ---- GPS follow button ---------------------------------------------------

    private fun enableGPSButton() {
        binding.gemSurface.mapView?.apply {
            onExitFollowingPosition = { binding.followCursor.visibility = View.VISIBLE }
            onEnterFollowingPosition = { binding.followCursor.visibility = View.GONE }
            binding.followCursor.setOnClickListener { SdkCall.execute { followPosition() } }
        }
    }

    private fun disableGPSButton() {
        binding.gemSurface.mapView?.apply {
            onExitFollowingPosition = null
            onEnterFollowingPosition = null
            binding.followCursor.setOnClickListener(null)
            binding.followCursor.visibility = View.GONE
        }
    }

    // ---- Dialog --------------------------------------------------------------

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

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource =
        CountingIdlingResource("ApplyMapStyleInstrumentedTestsIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
