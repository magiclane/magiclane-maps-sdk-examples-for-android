// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.fingerroute

// -------------------------------------------------------------------------------------------------------------------------------

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.EPathFileFormat
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Path
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.examples.fingerroute.databinding.ActivityMainBinding
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.GEMLog
import com.magiclane.sdk.util.SdkCall
import java.io.File
import java.io.FileOutputStream
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    companion object
    {
        private const val emailAddress = "support@magiclane.com"
        private const val emailSubject = "Finger route GPX"
    }

    enum class TopLeftButtonState
    {
        ROUTING_ON,
        ROUTING_OFF,
        CANCEL_ROUTING
    }

    private lateinit var binding: ActivityMainBinding

    private lateinit var mapSurface: GemSurfaceView

    private lateinit var progressBar: ProgressBar

    private lateinit var path: Path

    private var routingIsActive = false
        set(value)
        {
            field = value
            setupTopLeftButton(
                if (value) TopLeftButtonState.CANCEL_ROUTING
                else TopLeftButtonState.ROUTING_OFF
            )
            if (!value)
            {
                fingerRouteMode = false
                binding.topRightButton.isVisible = false
                binding.bottomLeftButton.isVisible = false

                SdkCall.execute {
                    val mapRoutes = mapSurface.mapView?.preferences?.routes
                    mapRoutes?.let {
                        polylineCollection.clear()
                        if (it.size > 0)
                            it.clear()
                        else
                            routingService.cancelRoute()
                    }
                }
            }
        }

    private var fingerRouteMode = false

    private var fingerRouteIsVisible = true

    private val fingerPolyline: ArrayList<Pair<Float, Float>> = arrayListOf()

    private lateinit var polylineCollection: MarkerCollection

    private lateinit var polylineSettings: MarkerCollectionRenderSettings

    private var inset = 0

    private var transportMode = ERouteTransportMode.Bicycle

    private val routingService = RoutingService(
        onStarted = {
            progressBar.isVisible = true
            routingIsActive = true
        },

        onCompleted = onCompleted@{ routes, error, _ ->
            progressBar.isVisible = false

            when (error)
            {
                GemError.NoError ->
                {
                    SdkCall.execute {
                        if (routes.isNotEmpty())
                        {
                            mapSurface.mapView?.presentRoute(
                                routes[0],
                                edgeAreaInsets = Rect(inset, 2 * inset, inset, 2 * inset)
                            )
                        }
                    }

                    fingerRouteIsVisible = true
                    binding.topRightButton.isVisible = true
                    setupLiningButton(true)
                    binding.bottomLeftButton.isVisible = true
                }

                GemError.Cancel ->
                {
                    // The routing action was cancelled.
                    showDialog("The routing action was cancelled.")
                    routingIsActive = false
                }

                else ->
                {
                    // There was a problem at computing the routing operation.
                    showDialog("Routing service error: ${GemError.getMessage(error)}")
                    routingIsActive = false
                }
            }
        }
    )

    // ---------------------------------------------------------------------------------------------------------------------------

    private class ShareGPXTask(
        val activity: Activity,
        val email: String,
        val subject: String,
        val gpxFile: File
    ) : CoroutinesAsyncTask<Void, Void, Intent>()
    {
        override fun doInBackground(vararg params: Void?): Intent
        {
            val subjectText = subject
            val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
            sendIntent.type = "message/rfc822"
            sendIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, subjectText)

            val uris = ArrayList<Uri>()

            try
            {
                uris.add(
                    FileProvider.getUriForFile(
                        activity,
                        activity.packageName + ".provider",
                        gpxFile
                    )
                )
            }
            catch (e: Exception)
            {
                GEMLog.error(this, "ShareGPXTask.doInBackground(): error = ${e.message}")
            }

            sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            return sendIntent
        }

        override fun onPostExecute(result: Intent?)
        {
            if (result == null)
            {
                return
            }

            activity.startActivity(result)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun shareGPXFile(a: Activity, gpxFile: File)
    {
        ShareGPXTask(a, emailAddress, emailSubject, gpxFile).execute(null)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        mapSurface = binding.gemSurface

        progressBar = binding.progressBar

        inset = resources.getDimension(R.dimen.inset).toInt()

        mapSurface.onDefaultMapViewCreated = {
            polylineCollection = MarkerCollection(EMarkerType.Polyline, "Polyline")
            polylineSettings = MarkerCollectionRenderSettings(polylineInnerColor = Rgba.magenta())
            polylineSettings.polylineInnerSize = 1.5 // mm

            mapSurface.mapView?.preferences?.markers?.add(polylineCollection, polylineSettings)

            routingService.preferences.ignoreRestrictionsOverTrack = true
            routingService.preferences.accurateTrackMatch = false

            applyCustomAssetStyle(mapSurface.mapView)
        }

        mapSurface.onPreHandleTouchListener = { event ->
            if (fingerRouteMode)
            {
                event?.let {
                    fingerPolyline.add(Pair(it.x, it.y))
                    if (it.action == MotionEvent.ACTION_UP)
                    {
                        prepareMarker()
                        fingerPolyline.clear()
                    }
                }
                false
            }
            else
            {
                true
            }
        }

        binding.topLeftButton.setOnClickListener {
            if (routingIsActive)
                routingIsActive = false
            else
            {
                fingerRouteMode = !fingerRouteMode
                setupTopLeftButton(
                    if (fingerRouteMode)
                        TopLeftButtonState.ROUTING_ON
                    else
                        TopLeftButtonState.ROUTING_OFF
                )
            }
        }

        binding.topRightButton.setOnClickListener {
            fingerRouteIsVisible = !fingerRouteIsVisible
            setupLiningButton(fingerRouteIsVisible)
            if (fingerRouteIsVisible)
            {
                SdkCall.execute {
                    mapSurface.mapView?.preferences?.markers?.add(
                        polylineCollection,
                        polylineSettings
                    )
                }
            }
            else
            {
                SdkCall.execute {
                    mapSurface.mapView?.preferences?.markers?.removeCollection(
                        polylineCollection
                    )
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
                            file
                        )
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        menuInflater.inflate(R.menu.menu_main, menu)

        return true
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        return when (item.itemId)
        {
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

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun showDialog(text: String)
    {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_layout, binding.root, false).apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.error)
            findViewById<TextView>(R.id.message).text = text
            findViewById<Button>(R.id.button).setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.apply {
            setCancelable(false)
            setContentView(view)
            show()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun applyCustomAssetStyle(mapView: MapView?) = SdkCall.execute {
        val filename = "CustomBasic.style"

        // Opens style input stream.
        val inputStream = applicationContext.resources.assets.open(filename)

        // Take bytes.
        val data = inputStream.readBytes()
        if (data.isEmpty()) return@execute

        // Apply style.
        mapView?.preferences?.setMapStyleByDataBuffer(DataBuffer(data))
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun prepareMarker() = SdkCall.execute {
        mapSurface.mapView?.let { mapView ->
            polylineCollection.clear()
            Marker().run {

                for (point in fingerPolyline)
                    mapView.transformScreenToWgs(Xy(point.first, point.second))
                        ?.let { coordinates -> add(coordinates) }

                polylineCollection.add(this)

                val coordinatesList = getCoordinates(0)
                coordinatesList?.let { coordinates ->
                    path = Path.produceWithCoords(coordinates)
                    val waypoints = arrayListOf(path.toLandmark())
                    val error =
                        routingService.calculateRoute(waypoints, transportMode)
                    if (error != GemError.NoError)
                    {
                        showDialog(
                            "Routing service error: ${
                                GemError.getMessage(
                                    error
                                )
                            }"
                        )
                        setupTopLeftButton(TopLeftButtonState.ROUTING_OFF)
                    }
                    fingerRouteMode = false
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun setupTopLeftButton(state: TopLeftButtonState)
    {
        binding.topLeftButton.icon = ResourcesCompat.getDrawable(
            resources, when (state)
            {
                TopLeftButtonState.ROUTING_ON, TopLeftButtonState.ROUTING_OFF -> R.drawable.touch
                TopLeftButtonState.CANCEL_ROUTING -> R.drawable.ic_close_24
            }, theme
        )

        binding.topLeftButton.iconTint = ContextCompat.getColorStateList(
            this@MainActivity,
            when (state)
            {
                TopLeftButtonState.ROUTING_ON, TopLeftButtonState.ROUTING_OFF -> R.color.white
                TopLeftButtonState.CANCEL_ROUTING -> R.color.red
            }
        )

        binding.topLeftButton.setBackgroundColor(
            ContextCompat.getColor(
                this@MainActivity,
                when (state)
                {
                    TopLeftButtonState.ROUTING_ON -> R.color.green
                    TopLeftButtonState.ROUTING_OFF -> R.color.gray
                    TopLeftButtonState.CANCEL_ROUTING -> R.color.white
                }
            )
        )
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun setupLiningButton(isActive: Boolean)
    {
        binding.topRightButton.setBackgroundColor(
            ContextCompat.getColor(
                this@MainActivity,
                if (isActive) R.color.green else R.color.gray
            )
        )
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
