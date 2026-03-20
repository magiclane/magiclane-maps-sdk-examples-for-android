/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bleserver1

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
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.get
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.TAG
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.examples.bleserver1.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.bleserver1.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    class TSameImage(var value: Boolean = false)

    private lateinit var binding: ActivityMainBinding
    private var lastTurnImageId: Long = Long.MAX_VALUE
    private var turnImageSize: Int = 64
    private var turnImage: Bitmap? = null
    private var padding: Int = 0

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

            binding.topPanel.visibility = View.VISIBLE
            binding.bottomPanel.visibility = View.VISIBLE
        },
        onNavigationInstructionUpdated = { instr ->
            var instrText = ""
            var instrDistance = ""

            var etaText = ""
            var rttText = ""
            var rtdText = ""

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
            }

            // Update the navigation panels info.

            val sameTurnImage = TSameImage()
            val newTurnImage = getNextTurnImage(instr, turnImageSize, turnImageSize, sameTurnImage)
            if (!sameTurnImage.value) {
                /**
                 * newTurnImage?.let {
                 * turnImage = getGrayscaledBitmap(it)
                 * } ?: run { turnImage = null }
                 */
                turnImage = newTurnImage

                binding.navIcon.setImageBitmap(newTurnImage)

                if (registeredDevices.isNotEmpty()) {
                    Util.postOnMain { sendTurnImage() }
                }
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
        },
        onDestinationReached = {
            onNavigationEnded()
        },
        onNavigationError = { error ->
            onNavigationEnded(error)
        },
    )

    /*
    fun getGrayscaledBitmap(
        src: Bitmap,
        redVal: Float = 0.299f,
        greenVal: Float = 0.587f,
        blueVal: Float = 0.114f,
    ): Bitmap {
        // create output bitmap
        val bmOut = createBitmap(src.width, src.height, Bitmap.Config.RGB_565)
        // pixel information
        var A: Int
        var R: Int
        var G: Int
        var B: Int
        var pixel: Int
        // get image size
        val width = src.width
        val height = src.height
        // scan through every single pixel
        for (x in 0 until width) {
            for (y in 0 until height) {
                // get one pixel color
                pixel = src[x, y]
                // retrieve color of all channels
                A = Color.alpha(pixel)
                R = Color.red(pixel)
                G = Color.green(pixel)
                B = Color.blue(pixel)
                // take conversion up to one single value
                B = (redVal * R + greenVal * G + blueVal * B).toInt()
                G = B
                R = G
                // set new pixel color to output bitmap
                bmOut[x, y] = Color.argb(A, R, G, B)
            }
        }
        // return final image
        return bmOut
    }
     */

    private fun transformBitmapToGrayscale(
        src: Bitmap,
        redVal: Float = 0.299f,
        greenVal: Float = 0.587f,
        blueVal: Float = 0.114f,
    ): ByteBuffer {
        val size: Int = src.width * src.height
        val byteBuffer: ByteBuffer = ByteBuffer.allocate(size)
        var alpha: Int
        var pixel: Int
        val width = src.width
        val height = src.height

        for (y in 0 until height) {
            for (x in 0 until width) {
                pixel = src[x, y]
                alpha = Color.alpha(pixel)

                if (alpha == 0) {
                    byteBuffer.put(0)
                } else {
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    var value = (((redVal * r + greenVal * g + blueVal * b) * alpha) / 255).toInt()

                    if ((r == 255) && (r > g) && (r > b)) {
                        value = if (alpha % 2 == 0) {
                            alpha + 1
                        } else {
                            alpha
                        }
                    } else if ((r > 200) && (r > g) && (r > b) && (alpha == 255)) {
                        value = if (r < 255) {
                            r
                        } else {
                            r - g
                        }

                        if (value % 2 == 0) {
                            value += 1
                        }
                    } else {
                        if (value % 2 == 1) {
                            value++
                            if (value == 256) {
                                value = 254
                            }
                        }
                    }

                    byteBuffer.put(value.toByte())
                }
            }
        }

        // return final image
        return byteBuffer
    }

    private fun sendTurnInstruction() {
        var turnInstruction = binding.navInstruction.text.toString()
        if (turnInstruction.length > 128) {
            turnInstruction = turnInstruction.substring(0, 125).plus("...")
        }

        if (turnInstruction.isEmpty()) {
            turnInstruction = " "
        }

        val byteArray = turnInstruction.toByteArray()

        notifyRegisteredDevices(byteArrayOf(byteArray.size.toByte()), TURN_INSTRUCTION)

        val n = byteArray.size / 20
        val r = byteArray.size % 20
        var tmp = ByteArray(20)

        for (i in 1..n) {
            System.arraycopy(byteArray, (i - 1) * 20, tmp, 0, 20)

            notifyRegisteredDevices(tmp, TURN_INSTRUCTION)
        }

        if (r > 0) {
            tmp = ByteArray(r)
            System.arraycopy(byteArray, n * 20, tmp, 0, r)

            notifyRegisteredDevices(tmp, TURN_INSTRUCTION)
        }
    }

    private fun sendTurnDistance() {
        val turnDistance = binding.instrDistance.text ?: " "
        notifyRegisteredDevices(turnDistance.toString().toByteArray(), TURN_DISTANCE)
    }

    private fun sendTurnImage() {
        turnImage?.let {
            val byteBuffer: ByteBuffer = transformBitmapToGrayscale(it)
            val array = byteBuffer.array()

            val tmp = ByteArray(20)
            var sum = 1

            val sendRange = { offset: Int, itemsCount: Int ->
                val n = itemsCount / 20
                val r = itemsCount % 20

                sum += itemsCount

                for (i in 1..n) {
                    System.arraycopy(array, offset + (i - 1) * 20, tmp, 0, 20)

                    notifyRegisteredDevices(tmp, TURN_IMAGE)
                    // Thread.sleep(1)
                }

                if (r > 0) {
                    val tmp1 = ByteArray(r)
                    System.arraycopy(array, offset + n * 20, tmp1, 0, r)

                    notifyRegisteredDevices(tmp1, TURN_IMAGE)
                    // Thread.sleep(1)
                }
            }

            notifyRegisteredDevices(byteArrayOf(1), TURN_IMAGE)

            for (y in 0 until turnImageSize) {
                var minX = 0
                var maxX = 0
                var minX1 = 0
                var maxX1 = 0
                var maxDist = 0
                var minX1Tmp = 0
                var maxX1Tmp = 0
                var maxDistTmp: Int
                val offset = y * turnImageSize

                for (x in 0 until turnImageSize) {
                    if (array[offset + x].toInt() != 0) {
                        minX = x
                        break
                    }
                }

                for (x in (turnImageSize - 1) downTo 0) {
                    if (array[offset + x].toInt() != 0) {
                        maxX = x
                        break
                    }
                }

                if (maxX > minX) {
                    for (i in minX..maxX) {
                        if (array[offset + i].toUByte().toInt() == 254) {
                            if (minX1Tmp == 0) {
                                minX1Tmp = i
                            } else {
                                maxX1Tmp = i
                            }
                        } else if (minX1Tmp > 0) {
                            maxDistTmp = maxX1Tmp - minX1Tmp
                            if (maxDistTmp > maxDist) {
                                maxDist = maxDistTmp
                                minX1 = minX1Tmp
                                maxX1 = maxX1Tmp
                            }

                            minX1Tmp = 0
                            maxX1Tmp = 0
                        }
                    }

                    if (maxX1 > minX1) {
                        notifyRegisteredDevices(
                            byteArrayOf(
                                minX.toByte(),
                                minX1.toByte(),
                                maxX1.toByte(),
                                maxX.toByte(),
                            ),
                            TURN_IMAGE,
                        )
                        sum += 4

                        if (minX1 > minX) {
                            sendRange(offset + minX, minX1 - minX)
                        }
                        if (maxX > maxX1) {
                            sendRange(offset + maxX1 + 1, maxX - maxX1)
                        }
                    } else {
                        notifyRegisteredDevices(
                            byteArrayOf(minX.toByte(), maxX.toByte()),
                            TURN_IMAGE,
                        )
                        sum += 2

                        sendRange(offset + minX, maxX - minX + 1)
                    }
                } else {
                    notifyRegisteredDevices(byteArrayOf(minX.toByte(), maxX.toByte()), TURN_IMAGE)
                    sum += 2
                }
            }

            Log.d(TAG, "sendTurnImage, size = $sum")

            /**
             * Log.d(TAG, "sendTurnImage, size = ${it.width * it.height}")
             *
             * val array = byteBuffer.array()
             * val n = array.size / 20
             * val r = array.size % 20
             *
             * var offset = 0
             * var tmp = ByteArray(20)
             *
             * notifyRegisteredDevices(byteArrayOf(1), TURN_IMAGE)
             * // Thread.sleep(1)
             *
             * for (i in 1..n)
             * {
             * System.arraycopy(array, offset, tmp, 0, 20)
             * offset += 20
             *
             * notifyRegisteredDevices(tmp, TURN_IMAGE)
             * // Thread.sleep(1)
             * }
             *
             * if (r > 0)
             * {
             * tmp = ByteArray(r)
             * System.arraycopy(array, offset, tmp, 0, r)
             *
             * notifyRegisteredDevices(tmp, TURN_IMAGE)
             * // Thread.sleep(1)
             * }
             */
        }
    }

    /**
     * private fun sendTurnImage()
     * {
     * turnImage?.let {
     * val size: Int = it.rowBytes * it.height
     * val byteBuffer: ByteBuffer = ByteBuffer.allocate(size)
     *
     * Log.d(TAG, "sendTurnImage, size = $size")
     *
     * it.copyPixelsToBuffer(byteBuffer)
     *
     * val array = byteBuffer.array()
     * val n = array.size / 20
     * val r = array.size % 20
     *
     * var offset = 0
     * var tmp = ByteArray(20)
     *
     * notifyRegisteredDevices(byteArrayOf(1), TURN_IMAGE)
     * // Thread.sleep(1)
     *
     * for (i in 1..n)
     * {
     * System.arraycopy(array, offset, tmp, 0, 20)
     * offset += 20
     *
     * notifyRegisteredDevices(tmp, TURN_IMAGE)
     * // Thread.sleep(1)
     * }
     *
     * if (r > 0)
     * {
     * tmp = ByteArray(r)
     * System.arraycopy(array, offset, tmp, 0, r)
     *
     * notifyRegisteredDevices(tmp, TURN_IMAGE)
     * // Thread.sleep(1)
     * }
     * }
     * }
     */

    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if (errorCode != GemError.NoError) {
                showDialog(GemError.getMessage(errorCode))
            }

            binding.topPanel.visibility = View.GONE
            binding.bottomPanel.visibility = View.GONE
            binding.followGpsButton.visibility = View.GONE

            notifyRegisteredDevices(byteArrayOf(0), TURN_IMAGE)
        }

        SdkCall.execute {
            binding.gemSurface.mapView?.hideRoutes()
            disableGPSButton()
        }
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
                showDialog(GemError.getMessage(errorCode))
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

                    TURN_IMAGE == characteristic.uuid -> {
                        Log.i(TAG, "Read turn image")
                        val intToBytes = { i: Int, j: Int ->
                            ByteBuffer.allocate(
                                Int.SIZE_BYTES * 2,
                            ).putInt(i).putInt(j).array()
                        }

                        bluetoothGattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            intToBytes(
                                turnImageSize,
                                turnImageSize * turnImageSize,
                            ),
                        )
                    }

                    TURN_DISTANCE == characteristic.uuid -> {
                        Log.i(TAG, "Read turn distance")

                        val turnDistance = binding.instrDistance.text ?: " "

                        bluetoothGattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            turnDistance.toString().toByteArray(),
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
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                (
                    ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    ) == PackageManager.PERMISSION_GRANTED
                    )
            ) {
                if (CLIENT_CONFIG == descriptor.uuid) {
                    if (BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE.contentEquals(value)) {
                        Log.d(TAG, "Subscribe device to notifications: $device")
                        registeredDevices.add(device)
                        Util.postOnMain {
                            sendTurnImage()
                            sendTurnInstruction()
                            sendTurnDistance()
                        }
                    } else if (BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE.contentEquals(value)) {
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
                showDialog("Missing Bluetooth support!")
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
        val turnInstruction =
            BluetoothGattCharacteristic(
                TURN_INSTRUCTION,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )

        val configDescriptor = BluetoothGattDescriptor(
            CLIENT_CONFIG, // Read/write descriptor
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        turnInstruction.addDescriptor(configDescriptor)

        // Read-only characteristic, supports notifications
        val turnImage =
            BluetoothGattCharacteristic(
                TURN_IMAGE,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            )

        val turnDistance =
            BluetoothGattCharacteristic(
                TURN_DISTANCE, // Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            )

        service.addCharacteristic(turnInstruction)
        service.addCharacteristic(turnImage)
        service.addCharacteristic(turnDistance)

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

    private fun notifyRegisteredDevices(data: ByteArray, uuid: UUID) {
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
                    )?.getCharacteristic(uuid)?.let {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        padding = resources.getDimension(R.dimen.big_padding).toInt()

        // Devices with a display should not go to sleep
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = "SDK initialization failed: ${GemError.getMessage(error, this)}"
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
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
            showDialog("The token you provided in the AndroidManifest.xml file was rejected. " +
                "If you don't have a token, check the magiclane.com website, " +
                "sign up / in and generate one. Then input it in the AndroidManifest.xml file.")
        }

        if (!Util.isInternetConnected(this)) {
            showDialog("You must be connected to the internet!")
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

    override fun onDestroy() {
        super.onDestroy()

        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter.isEnabled) {
            stopServer()
            stopAdvertising()
        }

        unregisterReceiver(bluetoothReceiver)

        // Deinitialize the SDK.
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

    private fun enableGPSButton() {
        // Set actions for entering / exiting following position mode.
        binding.gemSurface.mapView?.apply {
            onExitFollowingPosition = {
                Util.postOnMain { binding.followGpsButton.visibility = View.VISIBLE }
            }

            onEnterFollowingPosition = {
                Util.postOnMain { binding.followGpsButton.visibility = View.GONE }
            }

            // Set on click action for the GPS button.
            binding.followGpsButton.setOnClickListener {
                SdkCall.execute { followPosition() }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun disableGPSButton() {
        binding.gemSurface.mapView?.apply {
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

    private fun startSimulation() = SdkCall.execute {
        val waypoints =
            arrayListOf(
                Landmark("Amsterdam", 52.3585050, 4.8803423),
                Landmark("Paris", 48.8566932, 2.3514616),
            )
        // val waypoints = arrayListOf(Landmark("one", 44.41972, 26.08907), Landmark("two", 44.42479, 26.09076))
        // val waypoints = arrayListOf(Landmark("one", 44.42125, 26.09267), Landmark("two", 44.42233, 26.09205))
        // val waypoints = arrayListOf(Landmark("Brasov", 45.65231, 25.61027), Landmark("Codlea", 45.69252, 25.44899))

        val errorCode = navigationService.startSimulation(
            waypoints,
            navigationListener,
            routingProgressListener,
        )
        if (errorCode != GemError.NoError) {
            runOnUiThread {
                showDialog(GemError.getMessage(errorCode))
            }
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

    companion object {
        /* Navigation Service UUID */
        val NAVIGATION_SERVICE: UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")

        /* Mandatory Client Characteristic Config Descriptor */
        val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /* Mandatory Current Turn Instruction Characteristic */
        val TURN_INSTRUCTION: UUID = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb")

        /* Mandatory Current Turn Image Characteristic */
        val TURN_IMAGE: UUID = UUID.fromString("00002a0f-0000-1000-8000-00805f9b34fb")

        /* Mandatory Current Turn DISTANCE Characteristic */
        val TURN_DISTANCE: UUID = UUID.fromString("00002a2f-0000-1000-8000-00805f9b34fb")
    }
}
