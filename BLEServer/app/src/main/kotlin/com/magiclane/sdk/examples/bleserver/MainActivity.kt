// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.bleserver

// -------------------------------------------------------------------------------------------------------------------------------

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
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.TAG
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ETurnEvent
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity: AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------    

    class TSameImage(var value: Boolean = false)
    
    // ---------------------------------------------------------------------------------------------------------------------------

    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var followCursorButton: FloatingActionButton

    private lateinit var topPanel: ConstraintLayout
    private lateinit var navInstruction: TextView
    private lateinit var navInstructionDistance: TextView
    private lateinit var navInstructionIcon: ImageView

    private lateinit var bottomPanel: ConstraintLayout
    private lateinit var eta: TextView
    private lateinit var rtt: TextView
    private lateinit var rtd: TextView
    private var lastTurnImageId: Long = Long.MAX_VALUE
    private var turnEvent = byteArrayOf(0, 0, 0, 0)
    private var turnImageSize: Int = 0

    // ---------------------------------------------------------------------------------------------------------------------------

    private val permissionsRequestCode = 1

    // ---------------------------------------------------------------------------------------------------------------------------

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    // ---------------------------------------------------------------------------------------------------------------------------

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    // ---------------------------------------------------------------------------------------------------------------------------

    /* 
    Define a navigation listener that will receive notifications from the
    navigation service.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(onNavigationStarted = {
        SdkCall.execute {
            gemSurfaceView.mapView?.let { mapView ->
                mapView.preferences?.enableCursor = false
                navRoute?.let { route ->
                    mapView.presentRoute(route)
                }

                enableGPSButton()
                mapView.followPosition()
            }
        }

        topPanel.visibility = View.VISIBLE
        bottomPanel.visibility = View.VISIBLE
    }, onNavigationInstructionUpdated = { instr ->
        var instrText = ""
        var instrDistance = ""

        var etaText = ""
        var rttText = ""
        var rtdText = ""

        SdkCall.execute { // Fetch data for the navigation top panel (instruction related info).
            instrText = instr.nextStreetName ?: ""

            if (instrText.isEmpty())
            {
                instrText = instr.nextTurnInstruction ?: ""
            }

            instrDistance = instr.getDistanceInMeters()

            // Fetch data for the navigation bottom panel (route related info).
            navRoute?.apply {
                etaText = getEta() // estimated time of arrival
                rttText = getRtt() // remaining travel time
                rtdText = getRtd() // remaining travel distance
            }
        }

        // Update the navigation panels info.
        // -------------------------------------

        val sameTurnImage = TSameImage()
        val newTurnImage = getNextTurnImage(instr, turnImageSize, turnImageSize, sameTurnImage)
        if (!sameTurnImage.value)
        {
            SdkCall.execute {
                for (i in turnEvent.indices)
                {
                    turnEvent[i] = 0
                }

                instr.nextTurnDetails?.let {
                    turnEvent[0] = it.event.value.toByte()

                    if (it.event.value == ETurnEvent.IntoRoundabout.value)
                    {
                        it.abstractGeometry?.let { abstractGeometry ->
                            turnEvent[3] = abstractGeometry.driveSide.value.toByte()

                            abstractGeometry.items?.let { items ->
                                if (items.size > 1)
                                {
                                    turnEvent[1] = items.last().beginSlot.toByte()
                                    turnEvent[2] = items.last().endSlot.toByte()
                                }
                            }
                        }
                    }
                }
            }

            navInstructionIcon.setImageBitmap(newTurnImage)
            notifyRegisteredDevices(turnEvent, TURN_IMAGE)
        }

        if (instrText != navInstruction.text)
        {
            navInstruction.text = instrText
            sendTurnInstruction()
        }

        if (instrDistance != navInstructionDistance.text)
        {
            navInstructionDistance.text = instrDistance
            sendTurnDistance()
        }

        eta.text = etaText
        rtt.text = rttText
        rtd.text = rtdText
    },
    onDestinationReached = {
        onNavigationEnded()
    },
    onNavigationError = { error ->
        onNavigationEnded(error)
    })

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError)
    {
        runOnUiThread {
            if (errorCode != GemError.NoError)
            {
                showDialog(GemError.getMessage(errorCode))
            }

            topPanel.visibility = View.GONE
            bottomPanel.visibility = View.GONE

            for (i in turnEvent.indices)
            {
                turnEvent[i] = 0
            }

            notifyRegisteredDevices(turnEvent, TURN_IMAGE)
        }

        SdkCall.execute {
            gemSurfaceView.mapView?.hideRoutes()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun getNextTurnImage(
        navInstr: NavigationInstruction,
        width: Int,
        height: Int,
        bSameImage: TSameImage
    ): Bitmap? 
    {
        return SdkCall.execute {
            if (!navInstr.hasNextTurnInfo()) return@execute null
            if ((navInstr.nextTurnDetails?.abstractGeometryImage?.uid ?: 0) == lastTurnImageId)
            {
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
                iOuter
            )
        }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = { errorCode, _ ->
            progressBar.visibility = View.GONE
            if (errorCode != GemError.NoError)
            {
                showDialog(GemError.getMessage(errorCode))
            }
        },

        postOnMain = true
    )

    // ---------------------------------------------------------------------------------------------------------------------------

    /* Bluetooth API */
    private lateinit var bluetoothManager: BluetoothManager

    private var bluetoothGattServer: BluetoothGattServer? = null

    /* Collection of notification subscribers */
    private val registeredDevices = mutableSetOf<BluetoothDevice>()

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private val bluetoothReceiver = object : BroadcastReceiver()
    {
        override fun onReceive(context: Context, intent: Intent)
        {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF))
            {
                BluetoothAdapter.STATE_ON ->
                {
                    startAdvertising()
                    startServer()
                }
                BluetoothAdapter.STATE_OFF ->
                {
                    stopServer()
                    stopAdvertising()
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Callback to receive information about the advertisement process.
     */
    private val advertiseCallback = object : AdvertiseCallback()
    {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings)
        {
            Log.i(TAG, "LE Advertise Started.")
        }

        override fun onStartFailure(errorCode: Int)
        {
            Log.w(TAG, "LE Advertise Failed: $errorCode")
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private val gattServerCallback = object : BluetoothGattServerCallback()
    {
        // -----------------------------------------------------------------------------------------------------------------------

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int)
        {
            if (newState == BluetoothProfile.STATE_CONNECTED)
            {
                Log.i(TAG, "BluetoothDevice CONNECTED: $device")
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED)
            {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: $device") //Remove device from any active subscriptions
                registeredDevices.remove(device)
            }
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic)
        {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED))
            {
                when
                {
                    TURN_INSTRUCTION == characteristic.uuid ->
                    {
                        Log.i(TAG, "Read turn instruction")

                        bluetoothGattServer?.sendResponse(device,
                                                          requestId,
                                                          BluetoothGatt.GATT_SUCCESS,
                                                          0,
                                                          byteArrayOf(0))
                    }
                    TURN_IMAGE == characteristic.uuid ->
                    {
                        Log.i(TAG, "Read turn image, turnEvent[0] = ${turnEvent[0]}")
                        bluetoothGattServer?.sendResponse(device,
                                                          requestId,
                                                          BluetoothGatt.GATT_SUCCESS,
                                                          0,
                                                          turnEvent)
                    }
                    TURN_DISTANCE == characteristic.uuid ->
                    {
                        Log.i(TAG, "Read turn distance")

                        val turnDistance = navInstructionDistance.text ?: " "

                        bluetoothGattServer?.sendResponse(device,
                                                          requestId,
                                                          BluetoothGatt.GATT_SUCCESS,
                                                          0,
                                                          turnDistance.toString().toByteArray())
                    }
                    else ->
                    {
                        // Invalid characteristic
                        Log.w(TAG, "Invalid Characteristic Read: " + characteristic.uuid)
                        bluetoothGattServer?.sendResponse(device,
                                                          requestId,
                                                          BluetoothGatt.GATT_FAILURE,
                                                          0,
                                                          null)
                    }
                }
            }
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor)
        {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED))
            {
                if (CLIENT_CONFIG == descriptor.uuid)
                {
                    Log.d(TAG, "Config descriptor read")
                    val returnValue = if (registeredDevices.contains(device))
                    {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    }
                    else
                    {
                        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    }

                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, returnValue)
                }
                else
                {
                    Log.w(TAG, "Unknown descriptor read request")
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun onDescriptorWriteRequest(device: BluetoothDevice,
                                              requestId: Int,
                                              descriptor: BluetoothGattDescriptor,
                                              preparedWrite: Boolean,
                                              responseNeeded: Boolean,
                                              offset: Int,
                                              value: ByteArray)
        {
            if (registeredDevices.isEmpty())
            {
                if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                    (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED))
                {
                    if (CLIENT_CONFIG == descriptor.uuid)
                    {
                        if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value))
                        {
                            Log.d(TAG, "Subscribe device to notifications: $device")
                            registeredDevices.add(device)

                            Util.postOnMain {
                                notifyRegisteredDevices(turnEvent, TURN_IMAGE)

                                sendTurnInstruction()

                                sendTurnDistance()
                            }
                        }
                        else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value))
                        {
                            Log.d(TAG, "Unsubscribe device from notifications: $device")
                            registeredDevices.remove(device)
                        }

                        if (responseNeeded)
                        {
                            bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                        }
                    }
                    else
                    {
                        Log.w(TAG, "Unknown descriptor write request")
                        if (responseNeeded)
                        {
                            bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------------------------------------------------------------------------

        // -----------------------------------------------------------------------------------------------------------------------
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     * @param bluetoothAdapter System [BluetoothAdapter].
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private fun checkBluetoothSupport(bluetoothAdapter: BluetoothAdapter?): Boolean
    {
        if (bluetoothAdapter == null)
        {
            Log.w(TAG, "Bluetooth is not supported")
            return false
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            Log.w(TAG, "Bluetooth LE is not supported")
            return false
        }

        return true
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun registerForSystemBluetoothEvents()
    {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED))
        {
            bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

            // We can't continue without proper Bluetooth support
            if (checkBluetoothSupport(bluetoothAdapter))
            {
                // Register for system Bluetooth events
                val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                registerReceiver(bluetoothReceiver, filter)
                if (!bluetoothAdapter.isEnabled)
                {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                    {
                        Log.d(TAG, "Bluetooth is currently disabled...enabling")
                        bluetoothAdapter.enable()
                    }
                }
                else
                {
                    Log.d(TAG, "Bluetooth enabled...starting services")
                    startAdvertising()
                    startServer()
                }
            }
            else
            {
                showDialog("Missing Bluetooth support!")
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Current Time Service.
     */
    private fun startAdvertising()
    {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED))
        {
            val bluetoothLeAdvertiser: BluetoothLeAdvertiser? = bluetoothManager.adapter.bluetoothLeAdvertiser

            bluetoothLeAdvertiser?.let {
                val settings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                                                          .setConnectable(true)
                                                          .setTimeout(0)
                                                          .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                                                          .build()

                val data = AdvertiseData.Builder().setIncludeDeviceName(true)
                                                  .setIncludeTxPowerLevel(false)
                                                  .addServiceUuid(ParcelUuid(NAVIGATION_SERVICE))
                                                  .build()

                it.startAdvertising(settings, data, advertiseCallback)
            } ?: Log.w(TAG, "Failed to create advertiser")
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Stop Bluetooth advertisements.
     */
    private fun stopAdvertising()
    {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED))
        {
            val bluetoothLeAdvertiser: BluetoothLeAdvertiser? = bluetoothManager.adapter.bluetoothLeAdvertiser
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) ?: Log.w(TAG, "Failed to create advertiser")
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    private fun startServer()
    {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED))
        {
            bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)

            bluetoothGattServer?.addService(createNavigationService()) ?: Log.w(TAG, "Unable to create GATT server")
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Return a configured [BluetoothGattService] instance for the
     * Current Time Service.
     */
    private fun createNavigationService(): BluetoothGattService
    {
        val service = BluetoothGattService(NAVIGATION_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val turnInstruction = BluetoothGattCharacteristic(TURN_INSTRUCTION, // Read-only characteristic, supports notifications
                                                          BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                                                          BluetoothGattCharacteristic.PERMISSION_READ)

        val turnInstructionDescriptor = BluetoothGattDescriptor(CLIENT_CONFIG, // Read/write descriptor
                                                                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        turnInstruction.addDescriptor(turnInstructionDescriptor)

        val turnImage = BluetoothGattCharacteristic(TURN_IMAGE, // Read-only characteristic, supports notifications
                                                    BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                                                    BluetoothGattCharacteristic.PERMISSION_READ)

        val turnImageDescriptor = BluetoothGattDescriptor(CLIENT_CONFIG, // Read/write descriptor
                                  BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        turnImage.addDescriptor(turnImageDescriptor)

        val turnDistance = BluetoothGattCharacteristic(TURN_DISTANCE, // Read-only characteristic, supports notifications
                                                       BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                                                       BluetoothGattCharacteristic.PERMISSION_READ)

        val turnDistanceDescriptor = BluetoothGattDescriptor(CLIENT_CONFIG, // Read/write descriptor
                                                             BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        turnDistance.addDescriptor(turnDistanceDescriptor)

        service.addCharacteristic(turnInstruction)
        service.addCharacteristic(turnImage)
        service.addCharacteristic(turnDistance)

        return service
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Shut down the GATT server.
     */
    private fun stopServer()
    {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED))
        {
            bluetoothGattServer?.close()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun notifyRegisteredDevices(data: ByteArray, uuid: UUID)
    {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED))
        {
            if (registeredDevices.isNotEmpty())
            {
                Log.i(TAG, "Sending update to ${registeredDevices.size} subscribers")
                for (device in registeredDevices)
                {
                    bluetoothGattServer?.getService(NAVIGATION_SERVICE)?.getCharacteristic(uuid)?.let {
                        @Suppress("DEPRECATION")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        {
                            bluetoothGattServer?.notifyCharacteristicChanged(device, it, false, data)
                        }
                        else
                        {
                            it.value = data
                            bluetoothGattServer?.notifyCharacteristicChanged(device, it, false)
                        }
                    }
                }
            }
            else
            {
                Log.i(TAG, "No subscribers registered")
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()

        gemSurfaceView = findViewById(R.id.gem_surface)
        progressBar = findViewById(R.id.progressBar)
        followCursorButton = findViewById(R.id.followCursor)

        topPanel = findViewById(R.id.top_panel)
        navInstruction = findViewById(R.id.nav_instruction)
        navInstructionDistance = findViewById(R.id.instr_distance)
        navInstructionIcon = findViewById(R.id.nav_icon)

        bottomPanel = findViewById(R.id.bottom_panel)
        eta = findViewById(R.id.eta)
        rtt = findViewById(R.id.rtt)
        rtd = findViewById(R.id.rtd)

        // Devices with a display should not go to sleep
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        /// MAGIC LANE
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            startSimulation()
        }

        SdkSettings.onApiTokenRejected = {
            /*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one.
             */
            showDialog("TOKEN REJECTED")
        }

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE), permissionsRequestCode)
        }
        else
        {
            registerForSystemBluetoothEvents()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter.isEnabled)
        {
            stopServer()
            stopAdvertising()
        }

        unregisterReceiver(bluetoothReceiver)

        // Deinitialize the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    @Deprecated("Deprecated in Java")
    override fun onBackPressed()
    {
        finish()
        exitProcess(0)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == permissionsRequestCode)
        {
            if ((grantResults.size > 1) &&
                (grantResults[0] == PackageManager.PERMISSION_GRANTED) &&
                (grantResults[1] == PackageManager.PERMISSION_GRANTED))
            {
                registerForSystemBluetoothEvents()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun enableGPSButton()
    {
        // Set actions for entering / exiting following position mode.
        gemSurfaceView.mapView?.apply {
            onExitFollowingPosition = {
                followCursorButton.visibility = View.VISIBLE
            }

            onEnterFollowingPosition = {
                followCursorButton.visibility = View.GONE
            }

            // Set on click action for the GPS button.
            followCursorButton.setOnClickListener {
                SdkCall.execute { followPosition() }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun NavigationInstruction.getDistanceInMeters(): String
    {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0, EUnitSystem.Metric
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun Route.getEta(): String
    {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0

        val time = Time()
        time.setLocalTime()
        time.longValue = time.longValue + etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun Route.getRtt(): String
    {
        return GemUtil.getTimeText(
            this.getTimeDistance(true)?.totalTime ?: 0
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun Route.getRtd(): String
    {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0, EUnitSystem.Metric
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun sendTurnInstruction()
    {
        var turnInstruction = navInstruction.text.toString()
        if (turnInstruction.length > 128)
        {
            turnInstruction = turnInstruction.substring(0, 125).plus("...")
        }

        if (turnInstruction.isEmpty())
        {
            turnInstruction = " "
        }

        val byteArray = turnInstruction.toByteArray()

        notifyRegisteredDevices(byteArrayOf(byteArray.size.toByte()), TURN_INSTRUCTION)

        val n = byteArray.size / 20
        val r = byteArray.size % 20
        var tmp = ByteArray(20)
        var index = 0

        for (i in 1..n)
        {
            System.arraycopy(byteArray, index, tmp, 0, 20)
            index += 20

            notifyRegisteredDevices(tmp, TURN_INSTRUCTION)
        }

        if (r > 0)
        {
            tmp = ByteArray(r)
            System.arraycopy(byteArray, index, tmp, 0, r)

            notifyRegisteredDevices(tmp, TURN_INSTRUCTION)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun sendTurnDistance()
    {
        val turnDistance = navInstructionDistance.text ?: " "
        notifyRegisteredDevices(turnDistance.toString().toByteArray(), TURN_DISTANCE)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(Landmark("Amsterdam", 52.3585050, 4.8803423), Landmark("Paris", 48.8566932, 2.3514616))
        // val waypoints = arrayListOf(Landmark("one", 44.41972, 26.08907), Landmark("two", 44.42479, 26.09076))
        // val waypoints = arrayListOf(Landmark("one", 44.42125, 26.09267), Landmark("two", 44.42233, 26.09205))
        // val waypoints = arrayListOf(Landmark("Brasov", 45.65231, 25.61027), Landmark("Codlea", 45.69252, 25.44899))

        val errorCode = navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
        if (errorCode != GemError.NoError)
        {
            runOnUiThread {
                showDialog(GemError.getMessage(errorCode))
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    @SuppressLint("InflateParams")
    private fun showDialog(text: String)
    {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_layout, null).apply {
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

    companion object
    {
        /* Navigation Service UUID */
        val NAVIGATION_SERVICE: UUID = UUID.fromString("00011805-0000-1000-8000-00805f9b34fb")

        /* Mandatory Client Characteristic Config Descriptor */
        val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /* Mandatory Current Turn Instruction Characteristic */
        val TURN_INSTRUCTION: UUID = UUID.fromString("00012a2b-0000-1000-8000-00805f9b34fb")

        /* Mandatory Current Turn Image Characteristic */
        val TURN_IMAGE: UUID = UUID.fromString("00012a0f-0000-1000-8000-00805f9b34fb")

        /* Mandatory Current Turn DISTANCE Characteristic */
        val TURN_DISTANCE: UUID = UUID.fromString("00012a2f-0000-1000-8000-00805f9b34fb")
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
