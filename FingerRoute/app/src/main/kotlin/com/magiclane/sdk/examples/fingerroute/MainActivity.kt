/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
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
import androidx.activity.addCallback
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

    private val checkAuthorizationListener = ProgressListener.create(onCompleted = { errorCode, _ ->
        if (errorCode != GemError.NoError) {
            showInvalidTokenDialog()
        }
    })

    private var routingIsActive = false
        set(value) {
            field = value
            setupTopLeftButton(
                if (value) {
                    TopLeftButtonState.CANCEL_ROUTING
                } else {
                    TopLeftButtonState.ROUTING_OFF
                },
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
                        mapSurface.mapView?.preferences?.markers?.sketches(EMarkerType.Polyline)?.del(fingerLineMarkerIndex)
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

        onCompleted = onCompleted@{ routes, error, _ ->
            progressBar.isVisible = false

            when (error) {
                GemError.NoError -> {
                    SdkCall.execute {
                        if (routes.isNotEmpty()) {
                            mapSurface.mapView?.presentRoute(
                                routes[0],
                                edgeAreaInsets = Rect(leftInset, getAppBarHeight() + mapButtonSize + mapButtonMargin + inflate, rightInset, bottomInset),
                                displayBubble = true,
                                displayRouteName = true,
                                displayTrafficIcon = false
                            )
                        }
                    }

                    fingerRouteIsVisible = true
                    binding.topRightButton.isVisible = true
                    setupLiningButton(true)
                    binding.bottomLeftButton.isVisible = true
                }

                GemError.Cancel -> {
                    routingIsActive = false
                }

                else -> {
                    // There was a problem at computing the routing operation.
                    showDialog(GemError.getMessage(error, this))
                    routingIsActive = false
                }
            }
        },
        onStatusChanged = { status ->
           if (status == ERouteStatus.WaitingInternetConnection.value) {
                showDialog(getString(R.string.internet_required))
           }
        }
    )

    private class ShareGPXTask(
        val activity: Activity,
        val email: String,
        val subject: String,
        val gpxFile: File,
    ) : CoroutinesAsyncTask<Void, Void, Intent>() {
        override fun doInBackground(vararg params: Void?): Intent {
            val subjectText = subject
            val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
            sendIntent.type = "message/rfc822"
            sendIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, subjectText)

            val uris = ArrayList<Uri>()

            try {
                uris.add(
                    FileProvider.getUriForFile(
                        activity,
                        activity.packageName + ".provider",
                        gpxFile,
                    )
                )
            } catch (e: Exception) {
                GEMLog.error(this, "ShareGPXTask.doInBackground(): error = ${e.message}")
            }

            sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            return sendIntent
        }

        override fun onPostExecute(result: Intent?) {
            if (result == null) {
                return
            }

            activity.startActivity(result)
        }
    }

    private fun shareGPXFile(a: Activity, gpxFile: File) {
        ShareGPXTask(a, EMAIL_ADDRESS, EMAIL_SUBJECT, gpxFile).execute(null)
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

        // Set up window insets listener
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            leftInset = systemBars.left + inflate
            rightInset = systemBars.right + inflate
            bottomInset = systemBars.bottom + inflate + mapButtonSize + mapButtonMargin
            insets
        }

        SdkSettings.onConnectionStatusUpdated = { isConnected ->
            if (isConnected) {
                SdkSettings.appAuthorization?.let {
                    SdkCall.execute {
                        SdkSettings.verifyAppAuthorization(it, checkAuthorizationListener)
                    }
                } ?: run {
                    showInvalidTokenDialog()
                }

                SdkSettings.onConnectionStatusUpdated = {}
            }
        }

        mapSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        mapSurface.onDefaultMapViewCreated = {
            fingerLineRenderSettings.apply {
                polylineInnerColor = Rgba.magenta()
                polylineInnerSize = 1.5
            }

            routingService.preferences.apply {
                ignoreRestrictionsOverTrack = true
                accurateTrackMatch = false
            }

            applyCustomAssetStyle(mapSurface.mapView)
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
                                            fingerLineMarkerIndex = mapView.preferences?.markers?.sketches(EMarkerType.Polyline)?.add(fingerLineMarker, fingerLineRenderSettings) ?: -1
                                        }

                                        fingerLineMarker.add(coordinates)
                                    }

                                    MotionEvent.ACTION_MOVE -> {
                                        val n = fingerLineMarker.getCoordinates()?.size ?: 0
                                        if (n > 0) {
                                            fingerLineMarker.getCoordinates()?.get(n - 1)?.let { lastCoordinates ->
                                                if (lastCoordinates.getDistance(coordinates) >= 5) {
                                                    fingerLineMarker.add(coordinates)
                                                }
                                            }
                                        }
                                    }

                                    else -> {
                                        fingerLineMarker.add(coordinates)

                                        val coordinatesList = fingerLineMarker.getCoordinates()
                                        coordinatesList?.let { coordinates ->
                                            path = Path.produceWithCoords(coordinates)
                                            val error = routingService.calculateRoute(path, transportMode)
                                            if (error != GemError.NoError) {
                                                showDialog(GemError.getMessage(error, this))
                                                setupTopLeftButton(TopLeftButtonState.ROUTING_OFF)
                                            }
                                            fingerRouteMode = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                false
            } else {
                true
            }
        }

        binding.topLeftButton.setOnClickListener {
            if (routingIsActive) {
                routingIsActive = false
            } else {
                fingerRouteMode = !fingerRouteMode
                setupTopLeftButton(
                    if (fingerRouteMode) {
                        TopLeftButtonState.ROUTING_ON
                    } else {
                        TopLeftButtonState.ROUTING_OFF
                    }
                )
            }
        }

        binding.topRightButton.setOnClickListener {
            fingerRouteIsVisible = !fingerRouteIsVisible
            setupLiningButton(fingerRouteIsVisible)
            SdkCall.execute {
                mapSurface.mapView?.preferences?.markers?.sketches(EMarkerType.Polyline)?.let { polylineMarkers ->
                    if (fingerRouteIsVisible) {
                        fingerLineMarkerIndex = polylineMarkers.add(fingerLineMarker, fingerLineRenderSettings)
                    } else {
                        if (fingerLineMarkerIndex >= 0) {
                            polylineMarkers.del(fingerLineMarkerIndex)
                            fingerLineMarkerIndex = -1
                        }
                    }
                }
            }
        }

        binding.bottomLeftButton.setOnClickListener {
            SdkCall.execute {
                path.exportAs(EPathFileFormat.Gpx)?.let { dataBuffer ->
                    dataBuffer.bytes?.let {
                        val file = File(GemSdk.internalStoragePath, "route.gpx")
                        val fileOutputStream = FileOutputStream(file)

                        fileOutputStream.use { fos ->
                            fos.write(it, 0, it.size)
                        }

                        shareGPXFile(
                            this@MainActivity,
                            file,
                        )
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            showInvalidTokenDialog()
        }

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_bike ->
                {
                    item.isChecked = true
                    transportMode = ERouteTransportMode.Bicycle
                    true
                }

            R.id.action_pedestrian ->
                {
                    item.isChecked = true
                    transportMode = ERouteTransportMode.Pedestrian
                    true
                }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
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

    private fun showInvalidTokenDialog() {
        showDialog(
            getString(R.string.invalid_token),
        ) {
            finish()
            exitProcess(0)
        }
    }

    private fun applyCustomAssetStyle(mapView: MapView?) {
        val filename = "CustomBasic.style"

        // Opens style input stream.
        val inputStream = applicationContext.resources.assets.open(filename)

        // Take bytes.
        val data = inputStream.readBytes()
        if (!data.isEmpty()) {
            // Apply style.
            mapView?.preferences?.setMapStyleByDataBuffer(DataBuffer(data))
        }
    }

    private fun setupTopLeftButton(state: TopLeftButtonState) {
        binding.topLeftButton.icon = ResourcesCompat.getDrawable(
            resources,
            when (state) {
                TopLeftButtonState.ROUTING_ON, TopLeftButtonState.ROUTING_OFF -> R.drawable.touch
                TopLeftButtonState.CANCEL_ROUTING -> R.drawable.ic_close_24
            },
            theme
        )

        binding.topLeftButton.iconTint = ContextCompat.getColorStateList(
            this@MainActivity,
            when (state) {
                TopLeftButtonState.ROUTING_ON, TopLeftButtonState.ROUTING_OFF -> R.color.white
                TopLeftButtonState.CANCEL_ROUTING -> R.color.red
            }
        )

        binding.topLeftButton.setBackgroundColor(
            ContextCompat.getColor(
                this@MainActivity,
                when (state) {
                    TopLeftButtonState.ROUTING_ON -> R.color.green
                    TopLeftButtonState.ROUTING_OFF -> R.color.gray
                    TopLeftButtonState.CANCEL_ROUTING -> R.color.white
                }
            )
        )
    }

    private fun setupLiningButton(isActive: Boolean) {
        binding.topRightButton.setBackgroundColor(
            ContextCompat.getColor(
                this@MainActivity,
                if (isActive) R.color.green else R.color.gray,
            ),
        )
    }

    fun getAppBarHeight(): Int {
        // Return stored value if available
        if (appBarHeight > 0) {
            return appBarHeight
        }

        // Try to get current height
        val currentHeight = binding.appBar.height
        if (currentHeight > 0) {
            appBarHeight = currentHeight
            return appBarHeight
        }

        // Fallback: measure the app bar layout
        binding.appBar.measure(
            View.MeasureSpec.makeMeasureSpec(binding.appBar.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return binding.appBar.measuredHeight
    }
}
