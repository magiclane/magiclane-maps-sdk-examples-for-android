/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.whatsnearby

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.whatsnearby.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.whatsnearby.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.whatsnearby.databinding.ListItemBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtil.getDistText
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var imageSize: Int = 0

    // Prevents re-triggering the permission check until the user returns from the settings screen.
    private var shouldCheckLocationPermissionOnResume = false

    // Anchor point for distance calculations; set from GPS once location is available.
    private var reference: Coordinates? = null

    private lateinit var positionListener: PositionListener

    // Handles all search interaction with the SDK; callbacks run on the calling thread.
    private val searchService = SearchService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { results, errorCode, _ ->
            binding.progressBar.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> {
                    if (results.isNotEmpty()) {
                        reference?.let {
                            binding.listView.adapter = CustomAdapter(it, results, imageSize)
                        }
                    } else {
                        runOnAliveUi { showDialog(message = getString(R.string.no_results)) }
                    }
                }
                else -> {
                    if (errorCode != GemError.Cancel) {
                        runOnAliveUi {
                            showDialog(
                                message = getString(
                                    R.string.search_error,
                                    SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                                ),
                            )
                        }
                    }
                }
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        imageSize = resources.getDimension(R.dimen.landmark_image_size).toInt()
        binding.listView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(
                DividerItemDecoration(applicationContext, (layoutManager as LinearLayoutManager).orientation),
            )
            setBackgroundResource(R.color.background)
        }

        // Mandatory SDK init step when using the SDK without a map view.
        val initResult = GemSdk.initSdkWithDefaults(this)
        if (initResult != GemError.NoError) {
            showDialog(
                message = getString(
                    R.string.sdk_initialization_failed,
                    SdkCall.runSynced { GemError.getMessage(initResult, this) },
                ),
                onDismiss = { finish() },
            )
            return
        }

        // Location permission is required for the search to return relevant nearby results.
        if (checkLocationStatus()) {
            requestPermissions()
        }

        if (!Util.isInternetConnected(this)) {
            runOnAliveUi { showDialog(message = getString(R.string.internet_required)) }
        }

        registerSdkListeners()
    }

    override fun onResume() {
        super.onResume()
        // Re-check after the user returns from the system location settings screen.
        if (shouldCheckLocationPermissionOnResume) {
            shouldCheckLocationPermissionOnResume = false
            if (isLocationEnabled()) {
                requestPermissions()
            } else {
                showDialog(message = getString(R.string.location_services_required)) { finish() }
            }
        }
    }

    override fun onDestroy() {
        clearSdkListeners()
        GemSdk.release()
        super.onDestroy()
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        // Self-clearing listener: fires once when the SDK map data is ready, then removes itself.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }

                SdkCall.execute {
                    val currentPosition = PositionService.getCurrentPosition()
                    if (currentPosition?.valid() == true) {
                        // GPS fix is already available — search immediately.
                        reference = currentPosition
                        search()
                    } else {
                        // No fix yet; wait for the first valid position update.
                        positionListener = PositionListener {
                            if (!it.isValid()) return@PositionListener
                            PositionService.removeListener(positionListener)
                            reference = it.coordinates
                            search()
                        }
                        PositionService.addListener(positionListener, EDataType.Position)
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = { showInvalidTokenDialog() }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
    }

    private fun search() {
        searchService.searchAroundPosition(reference)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.none { it == PackageManager.PERMISSION_GRANTED }) {
            showDialog(message = getString(R.string.location_permission_required)) { finish() }
            return
        }

        PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)
    }

    private fun requestPermissions(): Boolean = PermissionsHelper.requestPermissions(
        REQUEST_PERMISSIONS,
        this,
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
    )

    // Shared builder: configures the modal bottom sheet and inflates the dialog binding.
    // Callers populate the binding fields and call show() on the returned dialog.
    private fun buildDialog(): Pair<BottomSheetDialog, DialogLayoutBinding> {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater)
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
        }
        return dialog to dialogBinding
    }

    private fun showDialog(
        title: String = getString(R.string.error),
        message: String,
        onDismiss: (() -> Unit)? = null,
    ) {
        if (!isActivityAlive()) return
        val (dialog, dialogBinding) = buildDialog()
        dialogBinding.apply {
            this.title.text = title
            this.message.text = message
            button.setOnClickListener {
                onDismiss?.invoke()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showInvalidTokenDialog() {
        runOnAliveUi {
            showDialog(message = getString(R.string.invalid_token)) { finish() }
        }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

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
        val (dialog, dialogBinding) = buildDialog()
        dialogBinding.apply {
            title.text = getString(R.string.location_status)
            this.message.text = message
            button.text = getString(R.string.open_settings)
            button.setOnClickListener {
                dialog.dismiss()
                startActivity(settingsIntent)
                shouldCheckLocationPermissionOnResume = true
            }
        }
        dialog.show()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }
}

/**
 * RecyclerView adapter that binds a list of [Landmark] results to list item views.
 * Each item shows the landmark's icon, name, address description, and distance from [reference].
 */
class CustomAdapter(
    private val reference: Coordinates,
    private val dataSet: ArrayList<Landmark>,
    private val imageSize: Int,
) : RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

    class ViewHolder(val binding: ListItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        SdkCall.execute {
            val landmark = dataSet[position]
            val meters = landmark.coordinates?.getDistance(reference)?.toInt() ?: 0
            val dist = getDistText(meters, EUnitSystem.Metric, true)

            viewHolder.binding.run {
                image.setImageBitmap(landmark.imageAsBitmap(imageSize))
                listItemText.text = landmark.name
                listItemDescription.text = GemUtil.getLandmarkDescription(landmark, true)
                statusText.text = dist.first
                statusDescription.text = dist.second
            }
        }
    }

    override fun getItemCount() = dataSet.size
}
