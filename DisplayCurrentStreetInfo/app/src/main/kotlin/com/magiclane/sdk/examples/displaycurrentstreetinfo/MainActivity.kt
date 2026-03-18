/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
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
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
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
import kotlin.math.min
import kotlin.system.exitProcess

@Suppress("SameParameterValue")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    lateinit var positionListener: PositionListener

    private var shouldCheckLocationPermissionOnResume = false

    private var dataSource: DataSource? = null

    private val dataSourceListener = object : DataSourceListener() {
        override fun onNewData(data: SenseData) {
            binding.gemSurface.mapView?.let { mapView ->
                if (mapView.isFollowingPosition()) {
                    val improvedPositionData = ImprovedPositionData(data)
                    val speedLimitInt = (improvedPositionData.roadSpeedLimit * 3.6).toInt()
                    val roadAddress = improvedPositionData.roadAddress
                    val speedLimit = if (speedLimitInt > 0) "$speedLimitInt" else ""
                    var streetName = roadAddress?.getField(EAddressField.StreetName) ?: ""
                    var cityName = roadAddress?.getField(EAddressField.City) ?: ""

                    if (cityName.isEmpty()) {
                        binding.gemSurface.mapView?.let { mapView ->
                            mapView.getClosestAddress(improvedPositionData.coordinates, 10000, true)?.let {
                                it.addressInfo?.getField(EAddressField.City)?.let { city ->
                                    cityName = "Near $city"
                                }
                            }
                        }
                    }

                    Util.postOnMain {
                        handleCurrentStreetNameInfo(streetName, cityName, speedLimit)
                    }
                }
                else {
                    Util.postOnMain {
                        handleCurrentStreetNameInfo("", "", "")
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

        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_init_failed, GemError.getMessage(error, this))
            Util.postOnMain {
                showErrorDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        binding.gemSurface.onDefaultMapViewCreated = { mapView ->
            mapView.followPosition()

            enableGPSButton()

            val hasPermissions = PermissionsHelper.hasPermissions(this, permissions)

            if (hasPermissions && isLocationEnabled()) {
                startImprovedPositionListener()
            } else {
                Util.postOnMain {
                    if (checkLocationStatus()) {
                        requestPermissions(this)
                    }
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

        SdkSettings.onApiTokenRejected = {
            showErrorDialog(getString(R.string.api_token_rejected))
        }

        if (!Util.isInternetConnected(this)) {
            showErrorDialog(getString(R.string.not_connected))
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldCheckLocationPermissionOnResume) {
            shouldCheckLocationPermissionOnResume = false
            if (isLocationEnabled()) {
                requestPermissions(this)
            }
            else {
                showErrorDialog(getString(R.string.location_services_required)) {
                    finish()
                    exitProcess(0)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }

    private fun showStartupInfoDialog() {
        Util.postOnMain {
            showDialog(
                getString(R.string.info),
                getString(R.string.startup_info_message)
            )
        }
    }

    private fun showDialog(dialogTitle: String, dialogMessage: String, onDismiss: (() -> Unit)? = null) {
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
        // Set actions for entering/ exiting following position mode.
        binding.gemSurface.mapView?.apply {
            onExitFollowingPosition = {
                binding.followCursorButton.isVisible = true
            }

            onEnterFollowingPosition = {
                binding.followCursorButton.isVisible = false
            }

            // Set on click action for the GPS button.
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

        // Notice permission status had changed
        PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)

        SdkCall.execute {
            startImprovedPositionListener()
        }
    }

    private fun requestPermissions(activity: Activity): Boolean {
        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS,
            activity,
            permissions
        )
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }

    private fun checkLocationStatus(): Boolean {
        if (!isLocationEnabled()) {
            showLocationDialog(
                message = getString(R.string.location_disabled),
                settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            )
            return false
        }

        return true
    }

    private fun showLocationDialog(message: String, settingsIntent: Intent) {
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

    private fun getSizeInPixels(dpi: Int): Int
    {
        val metrics = resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpi.toFloat(), metrics).toInt()
    }

    private fun handleCurrentStreetNameInfo(streetName: String, cityName: String, speedLimit: String) {
        binding.apply {
            if (streetName.isNotEmpty()) {
                if (streetName != currentStreetText.text) {
                    currentStreetText.text = streetName
                    val maxTextSize = resources.getDimension(R.dimen.max_current_street_text_size)
                    currentStreetText.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        min(currentStreetText.textSize, maxTextSize)
                    )
                }

                if (cityName.isNotEmpty()) {
                    currentCityText.visibility = View.VISIBLE
                    if (cityName != currentCityText.text) {
                        currentCityText.text = cityName
                        val maxTextSize = resources.getDimension(R.dimen.max_current_street_text_size)
                        currentCityText.setTextSize(TypedValue.COMPLEX_UNIT_PX, min(currentCityText.textSize, maxTextSize))
                    }
                    (currentStreetText.layoutParams as ConstraintLayout.LayoutParams).bottomMargin = getSizeInPixels(1)
                }
                else
                {
                    currentCityText.visibility = View.GONE
                    (currentStreetText.layoutParams as ConstraintLayout.LayoutParams).bottomMargin = resources.getDimension(R.dimen.text_padding).toInt()
                }

                currentStreetTextContainer.visibility = View.VISIBLE
            }
            else {
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

    companion object {
        private const val REQUEST_PERMISSIONS = 110
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
