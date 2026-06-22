/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.displaycurrentstreetinfo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.displaycurrentstreetinfo.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.displaycurrentstreetinfo.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.EAddressField
import com.magiclane.sdk.sensordatasource.DataSource
import com.magiclane.sdk.sensordatasource.DataSourceFactory
import com.magiclane.sdk.sensordatasource.DataSourceListener
import com.magiclane.sdk.sensordatasource.ImprovedPositionData
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.SenseData
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

@Suppress("SameParameterValue")
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PERMISSIONS = 110
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: ActivityMainBinding

    private val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    lateinit var positionListener: PositionListener

    private var shouldCheckLocationPermissionOnResume = false
    private var dataSource: DataSource? = null

    // Fires whenever GPS data arrives; extracts street/city/speed info and posts it to the UI.
    private val dataSourceListener = object : DataSourceListener() {
        override fun onNewData(data: SenseData) {
            val mapView = binding.gemSurface.mapView ?: return

            if (!mapView.isFollowingPosition()) {
                runOnAliveUi { handleCurrentStreetNameInfo("", "", "") }
                return
            }

            val improvedPositionData = ImprovedPositionData(data)
            val speedLimitInt = (improvedPositionData.roadSpeedLimit * 3.6).toInt()
            val roadAddress = improvedPositionData.roadAddress
            val speedLimit = if (speedLimitInt > 0) "$speedLimitInt" else ""
            var streetName = roadAddress?.getField(EAddressField.StreetName) ?: ""
            var cityName = roadAddress?.getField(EAddressField.City) ?: ""

            // If no city from the GPS fix, query the nearest address for a fallback city name.
            if (cityName.isEmpty()) {
                mapView.getClosestAddress(improvedPositionData.coordinates, 10000, true)
                    ?.addressInfo?.getField(EAddressField.City)?.let { city ->
                        cityName = "Near $city"
                    }
            }

            runOnAliveUi { handleCurrentStreetNameInfo(streetName, cityName, speedLimit) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showErrorDialog(getString(R.string.not_connected))
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldCheckLocationPermissionOnResume) {
            shouldCheckLocationPermissionOnResume = false
            if (isLocationEnabled()) {
                requestPermissions(this)
            } else {
                showErrorDialog(getString(R.string.location_services_required)) { finish() }
            }
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
        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_init_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showErrorDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        binding.gemSurface.onDefaultMapViewCreated = { mapView ->
            // Align the Magic Lane logo with system window insets on first map creation.
            updateFocusViewport()
            mapView.followPosition()
            enableGPSButton()

            val hasPermissions = PermissionsHelper.hasPermissions(this, permissions)
            if (hasPermissions && isLocationEnabled()) {
                startImprovedPositionListener()
            } else {
                runOnAliveUi {
                    if (checkLocationStatus()) requestPermissions(this)
                }
            }

            if (PositionService.position?.isValid() == true) {
                showStartupInfoDialog()
            } else {
                positionListener = PositionListener {
                    if (!it.isValid()) return@PositionListener
                    PositionService.removeListener(positionListener)
                    showStartupInfoDialog()
                }
                PositionService.addListener(positionListener)
            }
        }

        // Re-align the logo whenever the surface is resized (e.g. on rotation).
        binding.gemSurface.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showErrorDialog(getString(R.string.api_token_rejected)) }
        }
    }

    // Clears SDK-level listeners to avoid callbacks reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurface.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // Adjusts the Magic Lane logo position to respect system window insets.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView = binding.gemSurface.mapView ?: return@runSynced
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

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun showStartupInfoDialog() {
        runOnAliveUi {
            showDialog(
                getString(R.string.info),
                getString(R.string.startup_info_message),
            )
        }
    }

    private fun showDialog(dialogTitle: String, dialogMessage: String, onDismiss: (() -> Unit)? = null) {
        if (!isActivityAlive()) return
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = dialogTitle
            message.text = dialogMessage
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

    private fun showErrorDialog(errorMessage: String, onDismiss: (() -> Unit)? = null) {
        showDialog(getString(R.string.error), errorMessage, onDismiss)
    }

    private fun startImprovedPositionListener() {
        if (dataSource == null) {
            dataSource = DataSourceFactory.produceLive()
            dataSource?.addListener(dataSourceListener, EDataType.ImprovedPosition)
        }
    }

    private fun enableGPSButton() {
        binding.gemSurface.mapView?.apply {
            onExitFollowingPosition = { binding.followCursorButton.isVisible = true }
            onEnterFollowingPosition = { binding.followCursorButton.isVisible = false }
            binding.followCursorButton.setOnClickListener {
                SdkCall.execute { followPosition() }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                showErrorDialog(getString(R.string.location_permission_required_restart)) {
                    finish()
                    exitProcess(0)
                }
                return
            }
        }

        PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)
        SdkCall.execute { startImprovedPositionListener() }
    }

    private fun requestPermissions(activity: Activity): Boolean {
        return PermissionsHelper.requestPermissions(REQUEST_PERMISSIONS, activity, permissions)
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }

    private fun checkLocationStatus(): Boolean {
        if (!isLocationEnabled()) {
            showLocationDialog(
                message = getString(R.string.location_disabled),
                settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            )
            return false
        }
        return true
    }

    private fun showLocationDialog(message: String, settingsIntent: Intent) {
        if (!isActivityAlive()) return
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.location_status)
            this.message.text = message
            button.text = getString(R.string.open_settings)
            button.setOnClickListener {
                dialog.dismiss()
                startActivity(settingsIntent)
                shouldCheckLocationPermissionOnResume = true
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

    private fun getSizeInPixels(dpi: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpi.toFloat(), resources.displayMetrics).toInt()
    }

    private fun handleCurrentStreetNameInfo(streetName: String, cityName: String, speedLimit: String) {
        binding.apply {
            if (streetName.isNotEmpty()) {
                if (streetName != currentStreetText.text) {
                    currentStreetText.text = streetName
                }

                if (cityName.isNotEmpty()) {
                    currentCityText.visibility = View.VISIBLE
                    if (cityName != currentCityText.text) {
                        currentCityText.text = cityName
                    }
                    (currentStreetText.layoutParams as ConstraintLayout.LayoutParams).bottomMargin = getSizeInPixels(1)
                } else {
                    currentCityText.visibility = View.GONE
                    (currentStreetText.layoutParams as ConstraintLayout.LayoutParams).bottomMargin =
                        resources.getDimension(R.dimen.text_padding).toInt()
                }

                currentStreetTextContainer.visibility = View.VISIBLE
            } else {
                currentStreetTextContainer.visibility = View.GONE
            }

            speedLimitSign.isVisible = speedLimit.isNotEmpty()
            if (speedLimit.isNotEmpty()) {
                val defaultTextSize = resources.getDimensionPixelSize(R.dimen.speed_panel_text_size).toFloat()
                val textSize = if (speedLimit.length >= 3) defaultTextSize * 0.8f else defaultTextSize
                currentSpeedLimit.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
                currentSpeedLimit.text = speedLimit
            }
        }
    }
}

//region TESTING
@VisibleForTesting
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("DisplayCurrentStreetInfoIdlingResource")
    // fun increment() = espressoIdlingResource.increment()
    // fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
