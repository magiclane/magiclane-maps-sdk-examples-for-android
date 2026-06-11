/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.fingerroute

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.EPathFileFormat
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerRenderSettings
import com.magiclane.sdk.examples.fingerroute.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.fingerroute.databinding.DialogLayoutBinding
import com.magiclane.sdk.routesandnavigation.ERouteStatus
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.GEMLog
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.io.File
import java.io.FileOutputStream
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        private const val EMAIL_ADDRESS = "support@magiclane.com"
        private const val EMAIL_SUBJECT = "Finger route GPX"

        // Combines status bar, navigation bar, and display cutout insets for logo placement.
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    enum class TopLeftButtonState {
        ROUTING_ON,
        ROUTING_OFF,
        CANCEL_ROUTING,
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var mapSurface: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var path: Path

    private val fingerLineMarker = Marker()
    private val fingerLineRenderSettings = MarkerRenderSettings()
    private var fingerLineMarkerIndex = -1

    private var appBarHeight = 0

    // Fires once on the first internet connection to validate the API token.
    private val checkAuthorizationListener = ProgressListener.create(onCompleted = { errorCode, _ ->
        if (errorCode != GemError.NoError) {
            runOnAliveUi { showInvalidTokenDialog() }
        }
    })

    private var routingIsActive = false
        set(value) {
            field = value
            setupTopLeftButton(
                if (value) TopLeftButtonState.CANCEL_ROUTING else TopLeftButtonState.ROUTING_OFF,
            )
            if (!value) {
                fingerRouteMode = false
                binding.topRightButton.isVisible = false
                binding.bottomLeftButton.isVisible = false

                SdkCall.execute {
                    val mapRoutes = mapSurface.mapView?.preferences?.routes
                    mapRoutes?.let {
                        if (it.size > 0) {
                            it.clear()
                        } else {
                            routingService.cancelRoute()
                        }
                    }

                    fingerLineMarker.delPart(0)

                    if (fingerLineMarkerIndex > 0) {
                        mapSurface.mapView?.preferences?.markers
                            ?.sketches(EMarkerType.Polyline)
                            ?.del(fingerLineMarkerIndex)
                        fingerLineMarkerIndex = -1
                    }
                }
            }
        }

    private var fingerRouteMode = false
    private var fingerRouteIsVisible = true

    private var mapButtonSize = 0
    private var mapButtonMargin = 0
    private var leftInset = 0
    private var rightInset = 0
    private var bottomInset = 0
    private var inflate = 0

    private var transportMode = ERouteTransportMode.Bicycle

    private val routingService = RoutingService(
        onStarted = {
            progressBar.isVisible = true
            routingIsActive = true
        },

        onCompleted = { routes, error, _ ->
            progressBar.isVisible = false

            when (error) {
                GemError.NoError -> {
                    SdkCall.execute {
                        if (routes.isNotEmpty()) {
                            mapSurface.mapView?.presentRoute(
                                routes[0],
                                edgeAreaInsets = Rect(
                                    leftInset,
                                    getAppBarHeight() + mapButtonSize + mapButtonMargin + inflate,
                                    rightInset,
                                    bottomInset,
                                ),
                                displayBubble = true,
                                displayRouteName = true,
                                displayTrafficIcon = false,
                            )
                        }
                    }

                    fingerRouteIsVisible = true
                    binding.topRightButton.isVisible = true
                    setupLiningButton(true)
                    binding.bottomLeftButton.isVisible = true
                }

                GemError.Cancel -> routingIsActive = false

                else -> {
                    val message = SdkCall.runSynced { GemError.getMessage(error, this) } ?: ""
                    if (message.isNotEmpty()) {
                        showDialog(message)
                    }

                    routingIsActive = false
                }
            }
        },

        onStatusChanged = { status ->
            if (status == ERouteStatus.WaitingInternetConnection.value) {
                showDialog(getString(R.string.internet_required))
            }
        },
    )

    private class ShareGPXTask(
        val activity: Activity,
        val email: String,
        val subject: String,
        val gpxFile: File,
    ) : CoroutinesAsyncTask<Void, Void, Intent>() {
        override fun doInBackground(vararg params: Void?): Intent {
            val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }

            val uris = ArrayList<Uri>()
            try {
                uris.add(
                    FileProvider.getUriForFile(
                        activity,
                        activity.packageName + ".provider",
                        gpxFile,
                    ),
                )
            } catch (e: Exception) {
                GEMLog.error(this, "ShareGPXTask.doInBackground(): error = ${e.message}")
            }

            sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            return sendIntent
        }

        override fun onPostExecute(result: Intent?) {
            result?.let { activity.startActivity(it) }
        }
    }

    private fun shareGPXFile(activity: Activity, gpxFile: File) {
        ShareGPXTask(activity, EMAIL_ADDRESS, EMAIL_SUBJECT, gpxFile).execute(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setSupportActionBar(binding.toolbar)

        mapSurface = binding.gemSurface
        progressBar = binding.progressBar
        inflate = resources.getDimension(R.dimen.padding_40).toInt()
        mapButtonSize = resources.getDimension(R.dimen.map_button_size).toInt()
        mapButtonMargin = resources.getDimension(R.dimen.map_buttons_margin).toInt()

        // Capture system bar insets used later for map edge padding calculations.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            leftInset = systemBars.left + inflate
            rightInset = systemBars.right + inflate
            bottomInset = systemBars.bottom + inflate + mapButtonSize + mapButtonMargin
            insets
        }

        registerSdkListeners()
        setupButtonListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_bike -> {
                item.isChecked = true
                transportMode = ERouteTransportMode.Bicycle
                true
            }

            R.id.action_pedestrian -> {
                item.isChecked = true
                transportMode = ERouteTransportMode.Pedestrian
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    // Registers all SDK surface and settings callbacks.
    private fun registerSdkListeners() {
        mapSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        mapSurface.onDefaultMapViewCreated = { mapView ->
            fingerLineRenderSettings.apply {
                polylineInnerColor = Rgba.magenta()
                polylineInnerSize = 1.5
            }

            routingService.preferences.apply {
                ignoreRestrictionsOverTrack = true
                accurateTrackMatch = false
            }

            // Align the Magic Lane logo with system window insets on first map creation.
            updateFocusViewport()
            applyCustomAssetStyle(mapView)
        }

        // Re-align the logo whenever the surface is resized (e.g. device rotation).
        mapSurface.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        mapSurface.onPreHandleTouchListener = { event ->
            if (fingerRouteMode) {
                event?.let {
                    SdkCall.execute {
                        mapSurface.mapView?.let { mapView ->
                            mapView.transformScreenToWgs(Xy(it.x, it.y))?.let { coordinates ->
                                when (it.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        fingerLineMarker.delPart(0)

                                        if (fingerLineMarkerIndex < 0) {
                                            fingerLineMarkerIndex = mapView.preferences?.markers
                                                ?.sketches(EMarkerType.Polyline)
                                                ?.add(fingerLineMarker, fingerLineRenderSettings) ?: -1
                                        }

                                        fingerLineMarker.add(coordinates)
                                    }

                                    MotionEvent.ACTION_MOVE -> {
                                        val n = fingerLineMarker.getCoordinates()?.size ?: 0
                                        if (n > 0) {
                                            fingerLineMarker.getCoordinates()?.get(n - 1)?.let { last ->
                                                // Only record points at least 5 m apart to avoid noise.
                                                if (last.getDistance(coordinates) >= 5) {
                                                    fingerLineMarker.add(coordinates)
                                                }
                                            }
                                        }
                                    }

                                    else -> {
                                        fingerLineMarker.add(coordinates)

                                        fingerLineMarker.getCoordinates()?.let { coordList ->
                                            path = Path.produceWithCoords(coordList)
                                            val error = routingService.calculateRoute(path, transportMode)
                                            if (error != GemError.NoError) {
                                                val message = GemError.getMessage(error, this)
                                                runOnAliveUi {
                                                    showDialog(message)
                                                    setupTopLeftButton(TopLeftButtonState.ROUTING_OFF)
                                                }
                                            }
                                            fingerRouteMode = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Consume the event so the map does not pan while drawing.
                false
            } else {
                true
            }
        }

        // Validate the API token as soon as internet becomes available.
        SdkSettings.onConnectionStatusUpdated = { isConnected ->
            if (isConnected) {
                SdkSettings.appAuthorization?.let {
                    SdkCall.execute {
                        SdkSettings.verifyAppAuthorization(it, checkAuthorizationListener)
                    }
                } ?: runOnAliveUi { showInvalidTokenDialog() }

                // One-shot listener: clear after the first connection event.
                SdkSettings.onConnectionStatusUpdated = {}
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showInvalidTokenDialog() }
        }
    }

    // Clears SDK-level listeners to prevent callbacks from reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onConnectionStatusUpdated = {}
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurface.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
            onPreHandleTouchListener = { _ -> true }
        }
    }

    // Adjusts the Magic Lane logo position to respect system window insets.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView = mapSurface.mapView ?: return@runSynced
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

    // Wires up the three action buttons: draw route, toggle finger line, export GPX.
    private fun setupButtonListeners() {
        binding.topLeftButton.setOnClickListener {
            if (routingIsActive) {
                routingIsActive = false
            } else {
                fingerRouteMode = !fingerRouteMode
                setupTopLeftButton(
                    if (fingerRouteMode) TopLeftButtonState.ROUTING_ON else TopLeftButtonState.ROUTING_OFF,
                )
            }
        }

        binding.topRightButton.setOnClickListener {
            fingerRouteIsVisible = !fingerRouteIsVisible
            setupLiningButton(fingerRouteIsVisible)
            SdkCall.execute {
                mapSurface.mapView?.preferences?.markers?.sketches(EMarkerType.Polyline)
                    ?.let { polylineMarkers ->
                        if (fingerRouteIsVisible) {
                            fingerLineMarkerIndex = polylineMarkers.add(fingerLineMarker, fingerLineRenderSettings)
                        } else if (fingerLineMarkerIndex >= 0) {
                            polylineMarkers.del(fingerLineMarkerIndex)
                            fingerLineMarkerIndex = -1
                        }
                    }
            }
        }

        binding.bottomLeftButton.setOnClickListener {
            SdkCall.execute {
                path.exportAs(EPathFileFormat.Gpx)?.bytes?.let { bytes ->
                    val file = File(GemSdk.internalStoragePath, "route.gpx")
                    FileOutputStream(file).use { it.write(bytes) }
                    shareGPXFile(this@MainActivity, file)
                }
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
        showDialog(getString(R.string.invalid_token)) {
            finish()
            exitProcess(0)
        }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    // Reads the bundled .style asset and applies it to the map as a custom style.
    private fun applyCustomAssetStyle(mapView: MapView?) {
        val filename = "CustomBasic.style"
        val data = applicationContext.resources.assets.open(filename).readBytes()
        if (data.isEmpty()) return
        mapView?.preferences?.setMapStyleByDataBuffer(DataBuffer(data))
    }

    private fun setupTopLeftButton(state: TopLeftButtonState) {
        binding.topLeftButton.icon = ResourcesCompat.getDrawable(
            resources,
            when (state) {
                TopLeftButtonState.ROUTING_ON, TopLeftButtonState.ROUTING_OFF -> R.drawable.touch
                TopLeftButtonState.CANCEL_ROUTING -> R.drawable.ic_close_24
            },
            theme,
        )

        binding.topLeftButton.iconTint = ContextCompat.getColorStateList(
            this,
            when (state) {
                TopLeftButtonState.ROUTING_ON, TopLeftButtonState.ROUTING_OFF -> R.color.white
                TopLeftButtonState.CANCEL_ROUTING -> R.color.red
            },
        )

        binding.topLeftButton.setBackgroundColor(
            ContextCompat.getColor(
                this,
                when (state) {
                    TopLeftButtonState.ROUTING_ON -> R.color.green
                    TopLeftButtonState.ROUTING_OFF -> R.color.gray
                    TopLeftButtonState.CANCEL_ROUTING -> R.color.white
                },
            ),
        )
    }

    private fun setupLiningButton(isActive: Boolean) {
        binding.topRightButton.setBackgroundColor(
            ContextCompat.getColor(
                this,
                if (isActive) R.color.green else R.color.gray,
            ),
        )
    }

    fun getAppBarHeight(): Int {
        if (appBarHeight > 0) return appBarHeight

        val currentHeight = binding.appBar.height
        if (currentHeight > 0) {
            appBarHeight = currentHeight
            return appBarHeight
        }

        // Fallback: measure the app bar when it hasn't been laid out yet.
        binding.appBar.measure(
            View.MeasureSpec.makeMeasureSpec(binding.appBar.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return binding.appBar.measuredHeight
    }
}
