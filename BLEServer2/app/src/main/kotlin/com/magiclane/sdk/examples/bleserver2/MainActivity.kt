/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bleserver2

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.TAG
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.examples.bleserver2.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.bleserver2.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ETurnEvent
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    class TSameImage(var value: Boolean = false)

    private lateinit var binding: ActivityMainBinding
    private var lastTurnImageId: Long = Long.MAX_VALUE
    private var turnEvent = byteArrayOf(0, 0, 0, 0, 0)
    private var turnImageSize: Int = 0
    private var padding: Int = 0

    // Captured once at portrait orientation; all subsequent constraint updates are applied on top
    // of this baseline so portrait layout is never recomputed from scratch.
    private lateinit var portraitConstraintSet: ConstraintSet

    private val permissionsRequestCode = 1

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                binding.gemSurface.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                    }
                    enableGPSButton()
                    mapView.followPosition()
                }
            }
            applyCameraFocus()
            setNavigationPanelsVisible(isVisible = true)
        },
        onNavigationInstructionUpdated = { instr ->
            var instrText = ""
            var instrDistance = ""

            var etaText = ""
            var rttText = ""
            var rtdText = ""

            var remainingTravelTime = -1
            var remainingTravelDistance = -1
            var speed = -1

            SdkCall.execute { // Fetch data for the navigation top panel (instruction related info).
                instrText = instr.nextStreetName ?: ""

                if (instrText.isEmpty()) {
                    instrText = instr.nextTurnInstruction ?: ""
                }

                instrDistance = instr.getDistanceInMeters()

                // Fetch data for the navigation bottom panel (route related info).
                navRoute?.apply {
                    etaText = getEta() // estimated time of arrival
                    rttText = getRtt() // remaining travel time
                    rtdText = getRtd() // remaining travel distance
                }

                instr.remainingTravelTimeDistance?.let {
                    remainingTravelTime = it.totalTime
                    remainingTravelDistance = it.totalDistance
                }

                PositionService.improvedPosition?.let {
                    if (it.isValid()) {
                        speed = (it.speed * 100000).toInt()
                    }
                }
            }

            // Update the navigation panels info.

            val sameTurnImage = TSameImage()
            val newTurnImage = getNextTurnImage(instr, turnImageSize, turnImageSize, sameTurnImage)
            if (!sameTurnImage.value) {
                SdkCall.execute {
                    for (i in turnEvent.indices) {
                        turnEvent[i] = 0
                    }

                    instr.nextTurnDetails?.let {
                        turnEvent[1] = it.event.value.toByte()

                        if (it.event.value == ETurnEvent.IntoRoundabout.value) {
                            it.abstractGeometry?.let { abstractGeometry ->
                                turnEvent[4] = abstractGeometry.driveSide.value.toByte()

                                abstractGeometry.items?.let { items ->
                                    if (items.size > 1) {
                                        turnEvent[2] = items.last().beginSlot.toByte()
                                        turnEvent[3] = items.last().endSlot.toByte()
                                    }
                                }
                            }
                        }
                    }
                }

                binding.navIcon.setImageBitmap(newTurnImage)
                notifyRegisteredDevices(turnEvent)
            }

            if (instrText != binding.navInstruction.text) {
                binding.navInstruction.text = instrText
                sendTurnInstruction()
            }

            if (instrDistance != binding.instrDistance.text) {
                binding.instrDistance.text = instrDistance
                sendTurnDistance()
            }

            binding.eta.text = etaText
            binding.rtt.text = rttText
            binding.rtd.text = rtdText

            if (remainingTravelTime >= 0) {
                sendRemainingTravelTime(remainingTravelTime)
            }

            if (remainingTravelDistance >= 0) {
                sendRemainingTravelDistance(remainingTravelDistance)
            }

            if (speed >= 0) {
                sendSpeed(speed)
            }
        },
        onDestinationReached = {
            onNavigationEnded()
        },
        onNavigationError = { error ->
            onNavigationEnded(error)
        },
    )

    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if (errorCode != GemError.NoError) {
                val message = SdkCall.runSynced { GemError.getMessage(errorCode, this) }
                if (message?.isEmpty() == false) {
                    showDialog(message)
                }
            }
            setNavigationPanelsVisible(isVisible = false)
            disableGPSButton()

            turnEvent.fill(0)
            notifyRegisteredDevices(turnEvent)
        }

        SdkCall.execute { binding.gemSurface.mapView?.hideRoutes() }
    }

    private fun getNextTurnImage(
        navInstr: NavigationInstruction,
        width: Int,
        height: Int,
        bSameImage: TSameImage,
    ): Bitmap? {
        return SdkCall.execute {
            if (!navInstr.hasNextTurnInfo()) return@execute null
            if ((navInstr.nextTurnDetails?.abstractGeometryImage?.uid ?: 0) == lastTurnImageId) {
                bSameImage.value = true
                return@execute null
            }

            val image = navInstr.nextTurnDetails?.abstractGeometryImage
            if (image != null) {
                lastTurnImageId = image.uid
            }

            val aInner = Rgba(255, 255, 255, 255)
            val aOuter = Rgba(0, 0, 0, 255)
            val iInner = Rgba(128, 128, 128, 255)
            val iOuter = Rgba(128, 128, 128, 255)

            GemUtilImages.asBitmap(
                image,
                width,
                height,
                aInner,
                aOuter,
                iInner,
                iOuter,
            )
        }
    }

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { errorCode, _ ->
            binding.progressBar.visibility = View.GONE
            if (errorCode != GemError.NoError) {
                showDialog(
                    getString(
                        R.string.start_simulation_error,
                        SdkCall.runSynced { GemError.getMessage(errorCode, this@MainActivity) },
                    ),
                )
            }
        },

        postOnMain = true,
    )

    /* Bluetooth API */
    private lateinit var bluetoothManager: BluetoothManager

    private var bluetoothGattServer: BluetoothGattServer? = null

    /* Collection of notification subscribers */
    private val registeredDevices = mutableSetOf<BluetoothDevice>()

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)) {
                BluetoothAdapter.STATE_ON -> {
                    startAdvertising()
                    startServer()
                }

                BluetoothAdapter.STATE_OFF -> {
                    stopServer()
                    stopAdvertising()
                }
            }
        }
    }

    /**
     * Callback to receive information about the advertisement process.
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "LE Advertise Started.")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "LE Advertise Failed: $errorCode")
        }
    }

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: $device")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: $device")
                // Remove device from any active subscriptions
                registeredDevices.remove(device)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                (
                    ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    ) == PackageManager.PERMISSION_GRANTED
                    )
            ) {
                when {
                    TURN_INSTRUCTION == characteristic.uuid -> {
                        Log.i(TAG, "Read turn instruction")

                        bluetoothGattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            byteArrayOf(0),
                        )
                    }

                    else -> {
                        // Invalid characteristic
                        Log.w(TAG, "Invalid Characteristic Read: " + characteristic.uuid)
                        bluetoothGattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null,
                        )
                    }
                }
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                (
                    ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    ) == PackageManager.PERMISSION_GRANTED
                    )
            ) {
                if (CLIENT_CONFIG == descriptor.uuid) {
                    Log.d(TAG, "Config descriptor read")
                    val returnValue = if (registeredDevices.contains(device)) {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else {
                        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    }

                    bluetoothGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        returnValue,
                    )
                } else {
                    Log.w(TAG, "Unknown descriptor read request")
                    bluetoothGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null,
                    )
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (registeredDevices.isEmpty()) {
                if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                    (
                        ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        ) == PackageManager.PERMISSION_GRANTED
                        )
                ) {
                    if (CLIENT_CONFIG == descriptor.uuid) {
                        if (BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE.contentEquals(value)
                        ) {
                            Log.d(TAG, "Subscribe device to notifications: $device")
                            registeredDevices.add(device)

                            Util.postOnMain {
                                notifyRegisteredDevices(turnEvent)

                                sendTurnInstruction()

                                sendTurnDistance()
                            }
                        } else if (BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE.contentEquals(value)
                        ) {
                            Log.d(TAG, "Unsubscribe device from notifications: $device")
                            registeredDevices.remove(device)
                        }

                        if (responseNeeded) {
                            bluetoothGattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                0,
                                null,
                            )
                        }
                    } else {
                        Log.w(TAG, "Unknown descriptor write request")
                        if (responseNeeded) {
                            bluetoothGattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_FAILURE,
                                0,
                                null,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     * @param bluetoothAdapter System [BluetoothAdapter].
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private fun checkBluetoothSupport(bluetoothAdapter: BluetoothAdapter?): Boolean {
        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported")
            return false
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported")
            return false
        }

        return true
    }

    private fun registerForSystemBluetoothEvents() {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
                )
        ) {
            bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

            // We can't continue without proper Bluetooth support
            if (checkBluetoothSupport(bluetoothAdapter)) {
                // Register for system Bluetooth events
                val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                ContextCompat.registerReceiver(this, bluetoothReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
                if (!bluetoothAdapter.isEnabled) {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Log.d(TAG, "Bluetooth is currently disabled...enabling")
                        bluetoothAdapter.enable()
                    }
                } else {
                    Log.d(TAG, "Bluetooth enabled...starting services")
                    startAdvertising()
                    startServer()
                }
            } else {
                showDialog(getString(R.string.missing_bluetooth_support))
            }
        }
    }

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Current Time Service.
     */
    private fun startAdvertising() {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                ) == PackageManager.PERMISSION_GRANTED
                )
        ) {
            val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
                bluetoothManager.adapter.bluetoothLeAdvertiser

            bluetoothLeAdvertiser?.let {
                val settings = AdvertiseSettings.Builder().setAdvertiseMode(
                    AdvertiseSettings.ADVERTISE_MODE_BALANCED,
                )
                    .setConnectable(true)
                    .setTimeout(0)
                    .setTxPowerLevel(
                        AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM,
                    )
                    .build()

                val data = AdvertiseData.Builder().setIncludeDeviceName(true)
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(ParcelUuid(NAVIGATION_SERVICE))
                    .build()

                it.startAdvertising(settings, data, advertiseCallback)
            } ?: Log.w(TAG, "Failed to create advertiser")
        }
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private fun stopAdvertising() {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                ) == PackageManager.PERMISSION_GRANTED
                )
        ) {
            val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
                bluetoothManager.adapter.bluetoothLeAdvertiser
            bluetoothLeAdvertiser?.stopAdvertising(
                advertiseCallback,
            ) ?: Log.w(TAG, "Failed to create advertiser")
        }
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    private fun startServer() {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
                )
        ) {
            bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)

            bluetoothGattServer?.addService(
                createNavigationService(),
            ) ?: Log.w(TAG, "Unable to create GATT server")
        }
    }

    /**
     * Return a configured [BluetoothGattService] instance for the
     * Current Time Service.
     */
    private fun createNavigationService(): BluetoothGattService {
        val service =
            BluetoothGattService(NAVIGATION_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // Read-only characteristic, supports notifications
        val turnInstruction = BluetoothGattCharacteristic(
            TURN_INSTRUCTION,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )

        val turnInstructionDescriptor = BluetoothGattDescriptor(
            CLIENT_CONFIG, // Read/write descriptor
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        turnInstruction.addDescriptor(turnInstructionDescriptor)

        service.addCharacteristic(turnInstruction)

        return service
    }

    /**
     * Shut down the GATT server.
     */
    private fun stopServer() {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
                )
        ) {
            bluetoothGattServer?.close()
        }
    }

    private fun notifyRegisteredDevices(data: ByteArray) {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
                )
        ) {
            if (registeredDevices.isNotEmpty()) {
                Log.i(TAG, "Sending update to ${registeredDevices.size} subscribers")
                for (device in registeredDevices) {
                    bluetoothGattServer?.getService(
                        NAVIGATION_SERVICE,
                    )?.getCharacteristic(TURN_INSTRUCTION)?.let {
                        @Suppress("DEPRECATION")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            bluetoothGattServer?.notifyCharacteristicChanged(
                                device,
                                it,
                                false,
                                data,
                            )
                        } else {
                            it.value = data
                            bluetoothGattServer?.notifyCharacteristicChanged(device, it, false)
                        }
                    }
                }
            } else {
                Log.i(TAG, "No subscribers registered")
            }
        }
    }

    // ---- Lifecycle -----------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()
        padding = resources.getDimension(R.dimen.big_padding).toInt()

        // Devices with a display should not go to sleep
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Snapshot portrait constraints so landscape adjustments always start from a clean baseline.
        portraitConstraintSet = ConstraintSet().apply { clone(binding.root as ConstraintLayout) }
        applyOrientationLayout()

        // Re-apply orientation layout when window insets first arrive so landscape cold-starts
        // and orientation changes both get the correct left / bottom system bar offsets.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyOrientationLayout()
            insets
        }

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                ),
                permissionsRequestCode,
            )
        } else {
            registerForSystemBluetoothEvents()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout()
        applyCameraFocus()
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkListeners()

        // Guard against the case where onDestroy fires before Bluetooth was initialized
        // (e.g. the SDK init failed before permissions were granted).
        if (::bluetoothManager.isInitialized) {
            if (bluetoothManager.adapter.isEnabled) {
                stopServer()
                stopAdvertising()
            }
            unregisterReceiver(bluetoothReceiver)
        }

        // Deinitialize the SDK.
        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        GemSdk.release()
        exitProcess(0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == permissionsRequestCode) {
            if ((grantResults.size > 1) &&
                (grantResults[0] == PackageManager.PERMISSION_GRANTED) &&
                (grantResults[1] == PackageManager.PERMISSION_GRANTED)
            ) {
                registerForSystemBluetoothEvents()
            }
        }
    }

    // ---- SDK listener registration -------------------------------------------

    private fun registerSdkListeners() {
        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_init_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }

        // Update the Magic Lane logo viewport whenever the map surface is created or resized.
        binding.gemSurface.onDefaultMapViewCreated = { updateFocusViewport() }
        binding.gemSurface.onSurfaceChanged = { _, _ -> updateFocusViewport() }

        // Delay simulation start until the worldwide road map is fully ready;
        // the callback is cleared immediately after firing to prevent repeat invocations.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
                startSimulation()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurface.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // ---- Camera / viewport ---------------------------------------------------

    /** Shifts the camera focus point to keep the GPS arrow in the visible map area in landscape. */
    private fun applyCameraFocus() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        SdkCall.execute {
            // In landscape the navigation panel occupies the left 40 % of the screen, so shift
            // the camera focus point right (0.7) to keep the arrow in the visible map area.
            binding.gemSurface.mapView?.preferences?.followPositionPreferences?.cameraFocus =
                if (isLandscape) XyF(0.7f, 0.75f) else XyF(0.5f, 0.75f)
        }
    }

    /** Updates the Magic Lane logo viewport to avoid overlapping with navigation panels. */
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurface.mapView?.preferences?.focusViewport = getFocusViewport()
        }
    }

    private fun getFocusViewport(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return if (isLandscape) {
            val w = max(width, height)
            val h = min(width, height)

            // In landscape the panels are on the left, so exclude that area from the logo viewport.
            val left = if (binding.topPanel.isVisible) binding.topPanel.right else insets?.left ?: 0
            val top = insets?.top ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            Rect(left, top, right, bottom)
        } else {
            val w = min(width, height)
            val h = max(width, height)

            val left = insets?.left ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            // In portrait, account for the toolbar even when the nav panel is hidden.
            val top = if (binding.topPanel.isVisible) binding.topPanel.bottom else binding.toolbar.bottom
            val bottom = if (binding.bottomPanel.isVisible) {
                binding.bottomPanel.top.coerceAtLeast(top)
            } else {
                (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            Rect(left, top, right, bottom)
        }
    }

    // ---- Layout orientation --------------------------------------------------

    /**
     * Adjusts panel constraint widths and horizontal positions for portrait/landscape.
     * In landscape, panels occupy the left 40 % of the screen so the map remains visible
     * on the right. Restores live visibility state after ConstraintSet.applyTo(), which
     * would otherwise reset all views to their cloned (GONE) visibility.
     */
    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() restores visibility from the time of clone (all panels
        // were GONE at that point), so we must save and restore the live visibility state.
        val topVis = binding.topPanel.visibility
        val bottomVis = binding.bottomPanel.visibility
        val fabVis = binding.followGpsButton.visibility
        val progressVis = binding.progressBar.visibility

        val panelMargin = resources.getDimensionPixelSize(R.dimen.nav_panel_margin)

        // Read current window insets to account for system bars and display cutouts.
        // All panel insets are applied here (not via binding adapters) to keep ConstraintSet
        // as the sole owner of panel margins and avoid margin resets on orientation change.
        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val sysLeft = insets?.left ?: 0
        val sysBottom = insets?.bottom ?: 0

        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                // Panels occupy the left 40 % of the screen; offset by the left system bar / cutout.
                val panelWidth = (resources.displayMetrics.widthPixels * 0.4f).toInt()
                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    constrainWidth(id, panelWidth)
                    connect(
                        id,
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START,
                        sysLeft + panelMargin,
                    )
                    clear(id, ConstraintSet.END)
                }
                // In landscape the bottom panel only covers the left 40 %, so the FAB (right-aligned)
                // must be anchored to the screen bottom rather than the panel top.
                connect(
                    R.id.follow_gps_button,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                    padding,
                )
            } else {
                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    // Restore MATCH_CONSTRAINT width explicitly so it is not left at the
                    // absolute pixel value that was set in the landscape branch.
                    constrainWidth(id, ConstraintSet.MATCH_CONSTRAINT)
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, panelMargin)
                    connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, panelMargin)
                }
                // Restore the FAB to sit just above the bottom panel, ignoring system bar insets
                // (the bottom panel itself already accounts for them).
                connect(
                    R.id.follow_gps_button,
                    ConstraintSet.BOTTOM,
                    R.id.bottom_panel,
                    ConstraintSet.TOP,
                    padding,
                )
            }
            // Bottom panel always needs clearance for the bottom system bar / gesture indicator.
            connect(
                R.id.bottom_panel,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
                panelMargin + sysBottom,
            )
        }.applyTo(rootLayout)

        // Restore live visibility after ConstraintSet.applyTo() overwrote it.
        binding.topPanel.visibility = topVis
        binding.bottomPanel.visibility = bottomVis
        binding.followGpsButton.visibility = fabVis
        binding.progressBar.visibility = progressVis
    }

    // ---- Navigation panel visibility -----------------------------------------

    private fun setNavigationPanelsVisible(isVisible: Boolean) {
        binding.topPanel.isVisible = isVisible
        binding.bottomPanel.isVisible = isVisible
        if (!isVisible) {
            updateFocusViewport()
        } else {
            // Post so the viewport update runs after the panels are measured and laid out.
            binding.root.post { updateFocusViewport() }
        }
    }

    // ---- GPS follow button ---------------------------------------------------

    private fun enableGPSButton() {
        // Set actions for entering / exiting following position mode.
        binding.gemSurface.mapView?.apply {
            onExitFollowingPosition = {
                binding.followGpsButton.visibility = View.VISIBLE
            }

            onEnterFollowingPosition = {
                binding.followGpsButton.visibility = View.GONE
            }

            // Set on click action for the GPS button.
            binding.followGpsButton.setOnClickListener {
                SdkCall.execute { followPosition() }
            }
        }
    }

    private fun disableGPSButton() {
        binding.gemSurface.mapView?.apply {
            onExitFollowingPosition = null
            onEnterFollowingPosition = null

            binding.followGpsButton.setOnClickListener(null)
            binding.followGpsButton.isVisible = false
        }
    }

    // ---- Navigation data helpers ---------------------------------------------

    private fun NavigationInstruction.getDistanceInMeters(): String {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    @SuppressLint("DefaultLocale")
    private fun Route.getEta(): String {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0

        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
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

    // ---- BLE notification helpers --------------------------------------------

    private fun sendTurnInstruction() {
        var turnInstruction = binding.navInstruction.text.toString()
        if (turnInstruction.length > 128) {
            turnInstruction = turnInstruction.substring(0, 125).plus("...")
        }

        if (turnInstruction.isEmpty()) {
            turnInstruction = " "
        }

        val byteArray = turnInstruction.toByteArray()

        notifyRegisteredDevices(byteArrayOf(2, byteArray.size.toByte()))

        val n = byteArray.size / 20
        val r = byteArray.size % 20
        var tmp = ByteArray(20)

        for (i in 1..n) {
            System.arraycopy(byteArray, (i - 1) * 20, tmp, 0, 20)

            notifyRegisteredDevices(tmp)
        }

        if (r > 0) {
            tmp = ByteArray(r)
            System.arraycopy(byteArray, n * 20, tmp, 0, r)

            notifyRegisteredDevices(tmp)
        }
    }

    private fun sendTurnDistance() {
        val turnDistance = (binding.instrDistance.text ?: " ").toString()
        val byteArray = turnDistance.toByteArray()
        val data = ByteArray(byteArray.size + 1)

        data[0] = 1
        System.arraycopy(byteArray, 0, data, 1, byteArray.size)
        notifyRegisteredDevices(data)
    }

    private fun sendRemainingTravelTime(timeInSeconds: Int) {
        val byteArray = timeInSeconds.toString().toByteArray()
        val data = ByteArray(byteArray.size + 1)

        data[0] = 3
        System.arraycopy(byteArray, 0, data, 1, byteArray.size)
        notifyRegisteredDevices(data)
    }

    private fun sendRemainingTravelDistance(distanceInMeters: Int) {
        val byteArray = distanceInMeters.toString().toByteArray()
        val data = ByteArray(byteArray.size + 1)

        data[0] = 4
        System.arraycopy(byteArray, 0, data, 1, byteArray.size)
        notifyRegisteredDevices(data)
    }

    private fun sendSpeed(speed: Int) {
        val byteArray = speed.toString().toByteArray()
        val data = ByteArray(byteArray.size + 1)

        data[0] = 5
        System.arraycopy(byteArray, 0, data, 1, byteArray.size)
        notifyRegisteredDevices(data)
    }

    // ---- Simulation ----------------------------------------------------------

    private fun startSimulation() = SdkCall.execute {
        // val waypoints = arrayListOf(Landmark("Amsterdam", 52.3585050, 4.8803423), Landmark("Paris", 48.8566932, 2.3514616))
        // val waypoints = arrayListOf(Landmark("one", 44.41972, 26.08907), Landmark("two", 44.42479, 26.09076))
        // val waypoints = arrayListOf(Landmark("one", 44.42125, 26.09267), Landmark("two", 44.42233, 26.09205))
        // val waypoints = arrayListOf(Landmark("Brasov", 45.65231, 25.61027), Landmark("Codlea", 45.69252, 25.44899))
        val waypoints = arrayListOf(
            Landmark("Departure", 22.28647156, 114.14718250),
            Landmark("Via 1", 22.28453719, 114.14851594),
            Landmark("Via 2", 22.28489937, 114.14924812),
            Landmark("Via 3", 22.28112188, 114.15277469),
            Landmark("Via 4", 22.27804281, 114.15818563),
            Landmark("Destination", 22.28408813, 114.15513875),
        )

        val errorCode = navigationService.startSimulation(
            waypoints,
            navigationListener,
            routingProgressListener,
        )
        if (errorCode != GemError.NoError) {
            runOnUiThread {
                showDialog(
                    getString(
                        R.string.start_simulation_error,
                        SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                    ),
                )
            }
        }
    }

    // ---- Dialog --------------------------------------------------------------

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

    companion object {
        /* Navigation Service UUID */
        val NAVIGATION_SERVICE: UUID = UUID.fromString("00011805-0000-1000-8000-00805f9b34fb")

        /* Mandatory Client Characteristic Config Descriptor */
        val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /* Mandatory Current Turn Instruction Characteristic */
        val TURN_INSTRUCTION: UUID = UUID.fromString("00012a2b-0000-1000-8000-00805f9b34fb")
    }
}
