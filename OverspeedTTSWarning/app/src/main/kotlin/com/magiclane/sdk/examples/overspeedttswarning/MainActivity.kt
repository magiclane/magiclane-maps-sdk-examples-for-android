/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.overspeedttswarning

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.examples.overspeedttswarning.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.overspeedttswarning.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.overspeedttswarning.databinding.NavigationSpeedPanelBinding
import com.magiclane.sdk.sensordatasource.ImprovedPositionData
import com.magiclane.sdk.sensordatasource.PositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.EStringIds
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var speedPanelBinding: NavigationSpeedPanelBinding

    companion object {
        private const val REQUEST_PERMISSIONS = 110
        const val RESOURCE = "GLOBAL"
        private const val SPEED_WARNING_INTERVAL_MS = 5 * 60 * 1000L
        private val OVERSPEED_PANEL_COLOR = Color.rgb(225, 55, 55)
    }

    // Signals to Espresso tests that the SDK is ready; incremented on start, decremented once
    // the position listener is registered and position updates can flow.
    private var mainActivityIdlingResource = CountingIdlingResource(RESOURCE, true)

    private var speedLimit = 0.0
    private var lastSpeedWarningTime = 0L

    // Guards against double-registration since tryStartPositionListener() is called from
    // both onRequestPermissionsResult() and requestPermissionsAndStart().
    private var positionListenerAdded = false
    private lateinit var followPositionListener: PositionListener

    private val soundPlayingListener = object : SoundPlayingListener() {}
    private val soundPlayingPreferences = SoundPlayingPreferences()

    // Prevents attempting TTS playback before the engine has finished initializing.
    private var ttsEngineIsInitialized = false

    private val positionListener = object : PositionListener() {
        override fun onNewPosition(value: PositionData) {
            val improvedPos = ImprovedPositionData(value)
            speedLimit = improvedPos.roadSpeedLimit

            val speedText = GemUtil.getSpeedText(value.speed, EUnitSystem.Metric)
            val isOverSpeeding = speedLimit > 0 && value.speed > speedLimit
            val currentSpeedLimitText = if (speedLimit > 0.0) {
                GemUtil.getSpeedText(
                    speedLimit,
                    SdkSettings.unitSystem,
                ).first
            } else {
                ""
            }

            if (isOverSpeeding && ttsEngineIsInitialized) {
                val now = System.currentTimeMillis()
                if (now - lastSpeedWarningTime >= SPEED_WARNING_INTERVAL_MS) {
                    SoundPlayingService.playText(
                        GemUtil.getTTSString(EStringIds.eStrMindYourSpeed),
                        soundPlayingListener,
                        soundPlayingPreferences,
                    )
                    lastSpeedWarningTime = now
                }
            }

            runOnAliveUi {
                speedPanelBinding.apply {
                    root.isVisible = speedText.first.isNotEmpty()
                    if (root.isVisible) {
                        navSpeedLimitSign.root.isVisible = currentSpeedLimitText.isNotEmpty()
                        if (currentSpeedLimitText.isNotEmpty()) {
                            val defaultTextSize = resources.getDimensionPixelSize(
                                R.dimen.nav_speed_panel_text_size,
                            ).toFloat()
                            val textSize = if (currentSpeedLimitText.length >= 3) defaultTextSize * 0.8f else defaultTextSize
                            navSpeedLimitSign.navCurrentSpeedLimit.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
                            navSpeedLimitSign.navCurrentSpeedLimit.text = currentSpeedLimitText
                        }
                        navCurrentSpeed.text = speedText.first
                        navCurrentSpeedUnit.text = speedText.second
                        val textColor = if (isOverSpeeding) Color.WHITE else Color.BLACK
                        setBackgroundColor(
                            root.background,
                            if (isOverSpeeding) OVERSPEED_PANEL_COLOR else Color.WHITE,
                        )
                        navCurrentSpeed.setTextColor(textColor)
                        navCurrentSpeedUnit.setTextColor(textColor)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        speedPanelBinding = binding.navigationSpeedPanel

        ViewCompat.setOnApplyWindowInsetsListener(speedPanelBinding.root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = sys.left
                rightMargin = sys.right
            }
            insets
        }

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        SoundUtils.addTTSPlayerInitializationListener(this)

        increment()

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        clearSdkListeners()
        SdkCall.execute { PositionService.removeListener(positionListener) }
        GemSdk.release()
        super.onDestroy()
        // Required: the SDK holds native threads that do not stop when the Activity is destroyed.
        exitProcess(0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                finish()
                exitProcess(0)
            }
        }

        SdkCall.execute {
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)
        }

        tryStartPositionListener()
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) { finish() }
            }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = { mapView ->
            updateFocusViewport()
            if (PositionService.position?.isValid() == true) {
                mapView.followPosition()
                enableGpsButton(mapView)
            } else {
                followPositionListener = PositionListener {
                    if (!it.isValid()) return@PositionListener
                    mapView.followPosition()
                    PositionService.removeListener(followPositionListener)
                    enableGpsButton(mapView)
                }
                PositionService.addListener(followPositionListener, EDataType.Position)
            }
        }

        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                runOnAliveUi { requestPermissionsAndStart() }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        if (::followPositionListener.isInitialized) {
            PositionService.removeListener(followPositionListener)
        }
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}

        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
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

    private fun requestPermissionsAndStart() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        PermissionsHelper.requestPermissions(REQUEST_PERMISSIONS, this, permissions)
        tryStartPositionListener()
    }

    private fun tryStartPositionListener() {
        if (!PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) return
        SdkCall.execute {
            if (!positionListenerAdded) {
                positionListenerAdded = true
                PositionService.addListener(positionListener, EDataType.ImprovedPosition)
                decrement()
            }
        }
    }

    // Keeps the Magic Lane logo within the visible map area,
    // accounting for the toolbar and system bar insets.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurfaceView.mapView?.preferences?.focusViewport = getFocusViewport()
        }
    }

    private fun getFocusViewport(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.systemBars())

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val w = if (isLandscape) max(width, height) else min(width, height)
        val h = if (isLandscape) min(width, height) else max(width, height)

        val left = insets?.left ?: 0
        val top = if (binding.toolbar.isVisible) binding.toolbar.bottom else insets?.top ?: 0
        val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
        val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
        return Rect(left, top, right, bottom)
    }

    private fun enableGpsButton(mapView: MapView) = Util.postOnMain {
        mapView.onExitFollowingPosition = { binding.followGpsButton.visibility = View.VISIBLE }
        mapView.onEnterFollowingPosition = { binding.followGpsButton.visibility = View.GONE }
        binding.followGpsButton.setOnClickListener {
            SdkCall.execute { mapView.followPosition() }
        }
    }

    private fun setBackgroundColor(background: Drawable, color: Int) {
        val bgnd = if (background is LayerDrawable) background.getDrawable(1) else background
        when (bgnd) {
            is ShapeDrawable -> bgnd.paint.color = color
            is GradientDrawable -> bgnd.setColor(color)
            is ColorDrawable -> bgnd.color = color
            is InsetDrawable -> (bgnd.drawable as GradientDrawable).setColor(color)
        }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain {
            if (isActivityAlive()) {
                block()
            }
        }
    }

    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage("eng-USA")
        ttsEngineIsInitialized = true
    }

    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
        ttsEngineIsInitialized = true
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    private fun increment() = mainActivityIdlingResource.increment()
    private fun decrement() = mainActivityIdlingResource.decrement()
}
