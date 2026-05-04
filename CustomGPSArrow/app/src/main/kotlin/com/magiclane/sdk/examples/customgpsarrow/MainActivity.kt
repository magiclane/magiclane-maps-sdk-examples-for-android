/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.customgpsarrow

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
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
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     * We will use just the onNavigationStarted method, but for more available
     * methods you should check the documentation.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                binding.gemSurface.mapView?.let { mapView ->
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }

                    enableGPSButton()
                    mapView.followPosition()
                }
            }
            EspressoIdlingResource.decrement()
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

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        EspressoIdlingResource.increment()

        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = "SDK initialization failed: ${GemError.getMessage(error, this)}"
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        binding.gemSurface.onDefaultMapViewCreated = {
            val objList = getSceneObjs(Pair("quad.glb", ESceneObjectFileFormat.Gltf))
            if (objList.isNotEmpty()) {
                val (obj, err) = MapSceneObject.getDefPositionTracker()
                if (GemError.isError(err)) {
                    Util.postOnMain { showDialog(GemError.getMessage(err)) }
                } else {
                    MapSceneObject.customizeDefPositionTracker(objList)
                    obj?.let {
                        it.scaleFactor = 1.0 // 0.0 - 5.0
                    }
                }
            }
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                startSimulation()
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
            }
        }

        SdkSettings.onApiTokenRejected = {
            showDialog(
                "The token you provided was rejected. " +
                    "Make sure you provide the correct value, or if you don't have a token, " +
                    "check the magiclane.com website, sign up / in and generate one. Then input it in the AndroidManifest.xml file.",
            )
        }

        if (!Util.isInternetConnected(this)) {
            showDialog("You must be connected to the internet!")
        }
    }

    private fun getSceneObjs(vararg filesData: Pair<String, ESceneObjectFileFormat>): SceneObjectDataList {
        val list: SceneObjectDataList = arrayListOf()

        try {
            for (fileData in filesData) {
                val (fileName, format) = fileData
                val inputStream: InputStream = assets.open(fileName)
                var len: Int
                val data = ByteArray(1024)
                val buffer = ByteArrayOutputStream()

                while (inputStream.read(data, 0, data.size).also { len = it } > 0) {
                    buffer.write(data, 0, len)
                }

                buffer.flush()
                inputStream.close()

                if (buffer.size() > 0) {
                    list.add(SceneObjectData(DataBuffer(buffer.toByteArray()), format))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }

    override fun onDestroy() {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        binding.gemSurface.mapView?.apply {
            onExitFollowingPosition = {
                binding.followCursor.visibility = View.VISIBLE
            }

            onEnterFollowingPosition = {
                binding.followCursor.visibility = View.GONE
            }

            // Set on click action for the GPS button.
            binding.followCursor.setOnClickListener {
                SdkCall.execute { followPosition() }
            }
        }
    }

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616),
        )

        navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
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
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource =
        CountingIdlingResource("ApplyMapStyleInstrumentedTestsIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
