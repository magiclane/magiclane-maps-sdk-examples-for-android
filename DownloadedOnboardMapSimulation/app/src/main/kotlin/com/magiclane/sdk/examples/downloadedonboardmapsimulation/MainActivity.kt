/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("SameParameterValue")

package com.magiclane.sdk.examples.downloadedonboardmapsimulation

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.examples.downloadedonboardmapsimulation.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.downloadedonboardmapsimulation.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import java.util.Locale
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    private lateinit var binding: ActivityMainBinding

    private var emptyApiToken = false

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                binding.gemSurfaceView.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }

                    enableGPSButton()
                    mapView.followPosition()
                }
            }

            binding.topPanel.isVisible = true
            binding.bottomPanel.isVisible = true
            binding.statusText.isVisible = false
        },
        onNavigationInstructionUpdated = { instr ->
            var instrText = ""
            var instrIcon: Bitmap? = null
            var instrDistance = ""

            var etaText = ""
            var rttText = ""
            var rtdText = ""

            SdkCall.execute {
                // Fetch data for the navigation top panel (instruction related info).
                instrText = instr.nextStreetName ?: (instr.nextTurnInstruction ?: "")
                instrIcon = instr.nextTurnImage?.asBitmap(100, 100)
                instrDistance = instr.getDistanceInMeters()

                // Fetch data for the navigation bottom panel (route related info).
                navRoute?.apply {
                    etaText = getEta() // estimated time of arrival
                    rttText = getRtt() // remaining travel time
                    rtdText = getRtd() // remaining travel distance
                }
            }

            // Update the navigation panels info.
            binding.apply {
                navInstruction.text = instrText
                navInstructionIcon.setImageBitmap(instrIcon)
                navInstructionDistance.text = instrDistance

                eta.text = etaText
                rtt.text = rttText
                rtd.text = rtdText
            }
            EspressoIdlingResource.decrement()
        },
        onDestinationReached = {
            binding.topPanel.isVisible = false
            binding.bottomPanel.isVisible = false
            binding.followGpsButton.isVisible = false

            disableGPSButton()

            SdkCall.execute {
                binding.gemSurfaceView.mapView?.hideRoutes()
            }

            showStatusMessage(getString(R.string.destination_reached))
        },
        onNavigationSound = { sound ->
            SdkCall.execute {
                SoundPlayingService.play(sound, playingListener, soundPreference)
            }
        },
        canPlayNavigationSound = true
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.isVisible = true
            showStatusMessage(getString(R.string.routing_process_started))
        },
        onCompleted = { errorCode, _ ->
            binding.progressBar.isVisible = false
            if (errorCode != GemError.NoError) {
                showDialog(
                    getString(
                        R.string.routing_process_failed,
                        GemError.getMessage(errorCode, this),
                    ),
                )
            }
        },
        postOnMain = true
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        SoundUtils.addTTSPlayerInitializationListener(this)
        EspressoIdlingResource.init(this)

        binding.gemSurfaceView.onSdkInitSucceeded = {
           if (SdkSettings.appAuthorization.isNullOrEmpty()) {
               emptyApiToken = true
            }
        }

        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_init_failed, GemError.getMessage(error, this))
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = {
            if (emptyApiToken) {
                Util.postOnMainDelayed( {
                    showDialog(getString(R.string.missing_app_authorization)) {
                        finish()
                        exitProcess(0)
                    }
                }, 500)
            }
            else {
                startSimulation()
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
        EspressoIdlingResource.increment()
    }

    override fun onStop() {
        super.onStop()
        // Release the SDK.
        if (isFinishing) {
            SoundUtils.removeTTSPlayerInitializationListener(this)
            GemSdk.release()
        }
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

    private fun showStatusMessage(text: String) {
        binding.statusText.isVisible = true
        binding.statusText.text = text
    }

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followGpsButton.isVisible = true
                }

                onEnterFollowingPosition = {
                    followGpsButton.isVisible = false
                }

                // Set on click action for the GPS button.
                followGpsButton.setOnClickListener {
                    SdkCall.execute { followPosition() }
                }
            }
        }
    }

    private fun disableGPSButton() = SdkCall.execute {
        binding.gemSurfaceView.mapView?.apply {
            onExitFollowingPosition = null
            onEnterFollowingPosition = null
            binding.followGpsButton.setOnClickListener(null)
        }
    }

    private fun NavigationInstruction.getDistanceInMeters(): String {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    private fun Route.getEta(): String {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0

        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format(Locale.getDefault(), getString(R.string.time_format_hh_mm), time.hour, time.minute)
    }

    private fun Route.getRtt(): String {
        return GemUtil.getTimeText(
            this.getTimeDistance(true)?.totalTime ?: 0,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    private fun Route.getRtd(): String {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    private fun startSimulation() {
        val waypoints = arrayListOf(
            Landmark("Luxembourg", 49.61588784436375, 6.135843869736401),
            Landmark("Mersch", 49.74785494642988, 6.103323786692679),
        )
        val error = navigationService.startSimulation(
            waypoints,
            navigationListener,
            routingProgressListener,
        )

        if (error != GemError.NoError) {
            showDialog(
                getString(
                    R.string.failed_to_start_simulation,
                    GemError.getMessage(error, this),
                ),
            )
        }
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage(getString(R.string.tts_language_eng_usa))
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }
}

//region TESTING
object EspressoIdlingResource {
    private var resource: CountingIdlingResource? = null

    fun init(context: android.content.Context) {
        if (resource == null) {
            resource = CountingIdlingResource(context.getString(R.string.idling_resource_name))
        }
    }

    val espressoIdlingResource: CountingIdlingResource
        get() = checkNotNull(resource)

    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
