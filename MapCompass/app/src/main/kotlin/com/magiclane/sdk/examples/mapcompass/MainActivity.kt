/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapcompass

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.examples.mapcompass.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.mapcompass.databinding.DialogLayoutBinding
import com.magiclane.sdk.sensordatasource.CompassData
import com.magiclane.sdk.sensordatasource.DataSource
import com.magiclane.sdk.sensordatasource.DataSourceFactory
import com.magiclane.sdk.sensordatasource.DataSourceListener
import com.magiclane.sdk.sensordatasource.SenseData
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val isLiveHeadingEnabled = AtomicBoolean(false)

    private var dataSource: DataSource? = null

    private val dataSourceListener = object : DataSourceListener() {
        override fun onNewData(data: SenseData) {
            SdkCall.postAsync {
                // smooth new compass data
                val heading = headingSmoother.update(CompassData(data).heading)

                // update map view based on the recent changes
                binding.surfaceView.mapView?.preferences?.rotationAngle = heading
            }
        }
    }

    private val headingSmoother = HeadingSmoother()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUi()
        registerSdkListeners()
    }

    override fun onStop() {
        super.onStop()
        stopLiveHeading()
    }

    override fun onDestroy() {
        clearSdkListeners()

        // Deinitialize the SDK.
        GemSdk.release()

        super.onDestroy()
        exitProcess(0)
    }

    private fun setupUi() {
        binding.btnEnableLiveHeading.setOnClickListener {
            toggleLiveHeading()
        }
        renderLiveHeadingState(isLiveHeadingEnabled.get())
    }

    private fun registerSdkListeners() {
        binding.surfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showErrorDialog(errorMessage) { finish() }
            }
        }

        binding.surfaceView.onDefaultMapViewCreated = { mapView ->
            runOnAliveUi {
                showMapControls()

                // Change the compass icon rotation based on the map rotation at rendering.
                mapView.onMapAngleUpdated = {
                    binding.compass.rotation = -it.toFloat()
                }

                // Align the map to north if the compass icon is pressed.
                binding.compass.setOnClickListener {
                    SdkCall.execute {
                        if (!isLiveHeadingEnabled.get()) {
                            mapView.alignNorthUp(Animation(EAnimation.Linear, 300))
                        }
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi {
                showErrorDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
    }

    private fun toggleLiveHeading() {
        val shouldEnable = !isLiveHeadingEnabled.get()
        isLiveHeadingEnabled.set(shouldEnable)
        renderLiveHeadingState(shouldEnable)

        if (shouldEnable) {
            startLiveHeading()
        } else {
            stopLiveHeading()
        }
    }

    private fun renderLiveHeadingState(enabled: Boolean) {
        if (enabled) {
            buttonAsStop(this, binding.btnEnableLiveHeading)
            binding.statusText.text = getString(R.string.live_heading_enabled)
        } else {
            buttonAsStart(this, binding.btnEnableLiveHeading)
            binding.statusText.text = getString(R.string.live_heading_disabled)
        }
    }

    private fun showMapControls() {
        binding.compass.visibility = View.VISIBLE
        binding.btnEnableLiveHeading.visibility = View.VISIBLE
        binding.statusText.visibility = View.VISIBLE
    }

    /**
     * Will start listening for compass data. Compass's data needs to be smoothed by [HeadingSmoother].
     * The result, as rotation angle, will be applied to the map view.
     */
    private fun startLiveHeading() = SdkCall.execute {
        if (dataSource != null) return@execute

        dataSource = DataSourceFactory.produceLive()

        // start listening for compass data
        dataSource?.addListener(dataSourceListener, EDataType.Compass, critical = false)
    }

    /**
     * Will stop listening for compass data.
     */
    private fun stopLiveHeading() = SdkCall.execute {
        dataSource?.let {
            it.removeListener(dataSourceListener)
            it.release()
            dataSource = null
        }
    }

    private fun buttonAsStart(context: Context, button: FloatingActionButton?) {
        button ?: return

        val tag = "start"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.primary)
        val drawable = ContextCompat.getDrawable(
            context,
            android.R.drawable.ic_media_play,
        )

        button.tag = tag
        button.setImageDrawable(drawable)
        button.imageTintList = AppCompatResources.getColorStateList(context, R.color.on_primary)
        button.backgroundTintList = backgroundTintList
    }

    private fun buttonAsStop(context: Context, button: FloatingActionButton?) {
        button ?: return

        val tag = "stop"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.surface)
        val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_media_pause)

        button.tag = tag
        button.setImageDrawable(drawable)
        button.imageTintList = AppCompatResources.getColorStateList(context, R.color.on_surface)
        button.backgroundTintList = backgroundTintList
    }

    @SuppressLint("InflateParams")
    private fun showErrorDialog(text: String, onDismiss: (() -> Unit)? = null) {
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

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain {
            if (isActivityAlive()) {
                block()
            }
        }
    }

    private fun isActivityAlive(): Boolean {
        return !isFinishing && !isDestroyed
    }

    //region TESTING
    companion object {
        const val RESOURCE = "GLOBAL"
    }

    private var mainActivityIdlingResource = CountingIdlingResource(RESOURCE, true)

    @VisibleForTesting
    fun getActivityIdlingResource(): IdlingResource {
        return mainActivityIdlingResource
    }
}
