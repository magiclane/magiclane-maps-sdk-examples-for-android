// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.bleclient

// -------------------------------------------------------------------------------------------------------------------------------

import android.Manifest
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import com.magiclane.sdk.examples.bleclient.SampleGattAttributes.CLIENT_CONFIG
import com.magiclane.sdk.examples.bleclient.SampleGattAttributes.TURN_DISTANCE
import com.magiclane.sdk.examples.bleclient.SampleGattAttributes.TURN_IMAGE
import com.magiclane.sdk.examples.bleclient.SampleGattAttributes.TURN_INSTRUCTION

// -------------------------------------------------------------------------------------------------------------------------------

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
class BLEService : Service()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    interface IBLEServiceObserver
    {
        fun onCharacteristicRead(characteristic: BluetoothGattCharacteristic)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothDeviceAddress: String? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = STATE_DISCONNECTED
    private lateinit var context: Context
    private lateinit var tag: String
    private var bleServiceObserver: IBLEServiceObserver? = null
    private var turnInstructionSize = 0
    private var turnInstructionDataOffset = 0
    private var turnInstructionData = byteArrayOf()

    // ---------------------------------------------------------------------------------------------------------------------------

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback()
    {
        // -----------------------------------------------------------------------------------------------------------------------

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int)
        {
            Log.i(
                tag,
                "BluetoothGattCallback.onConnectionStateChange(): newState = $newState, status = $status"
            )

            val intentAction: String
            if (newState == BluetoothProfile.STATE_CONNECTED)
            {
                intentAction = ACTION_GATT_CONNECTED
                connectionState = STATE_CONNECTED
                broadcastUpdate(intentAction)
                Log.i(
                    tag,
                    "Connected to GATT server."
                ) // Attempts to discover services after successful connection.
                if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                    (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED)
                )
                {
                    bluetoothGatt?.let {
                        val result = it.discoverServices()
                        Log.i(
                            tag,
                            "BluetoothGattCallback.onConnectionStateChange(): attempting to start service discovery:" + result
                        )
                    }
                }
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED)
            {
                intentAction = ACTION_GATT_DISCONNECTED
                connectionState = STATE_DISCONNECTED
                Log.i(
                    tag,
                    "BluetoothGattCallback.onConnectionStateChange(): disconnected from GATT server, status = $status"
                )
                broadcastUpdate(intentAction)
            }
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int)
        {
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
            }
            else
            {
                Log.d(tag, "onServicesDiscovered received: $status")
            }
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        )
        {
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
            }

            /*
            if ((characteristic.properties or BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0)
            {
                setCharacteristicNotification(characteristic, true)
            }
            */

            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                bleServiceObserver?.onCharacteristicRead(characteristic)
            }
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        )
        {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun onServiceChanged(gatt: BluetoothGatt)
        {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED)
            )
            {
                bluetoothGatt?.let {
                    Log.i(tag, "BluetoothGattCallback.onServiceChanged(): try to reconnect")

                    it.disconnect()
                    Handler(Looper.getMainLooper()).postDelayed(
                        { connect(bluetoothDeviceAddress) },
                        1000
                    )
                }
            }
        }

        // -----------------------------------------------------------------------------------------------------------------------
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun broadcastUpdate(action: String)
    {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic)
    {
        val intent = Intent(action)

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml

        val data = characteristic.value
        if ((data != null) && data.isNotEmpty())
        {
            if (TURN_INSTRUCTION == characteristic.uuid)
            {
                if ((turnInstructionSize == 0) && (data.size == 1))
                {
                    turnInstructionDataOffset = 0
                    turnInstructionSize = data[0].toInt()
                    turnInstructionData = ByteArray(turnInstructionSize)

                    return
                }
                else
                {
                    if ((turnInstructionDataOffset + data.size) <= turnInstructionData.size)
                    {
                        System.arraycopy(
                            data,
                            0,
                            turnInstructionData,
                            turnInstructionDataOffset,
                            data.size
                        )
                        turnInstructionDataOffset += data.size

                        if (turnInstructionDataOffset == turnInstructionData.size)
                        {
                            turnInstructionSize = 0

                            intent.putExtra(EXTRA_TYPE, 0)
                            intent.putExtra(EXTRA_DATA, String(turnInstructionData))
                        }
                        else
                        {
                            return
                        }
                    }
                    else
                    {
                        return
                    }
                }
            }
            else if (TURN_DISTANCE == characteristic.uuid)
            {
                intent.putExtra(EXTRA_TYPE, 1)
                intent.putExtra(EXTRA_DATA, String(data))
            }
            else if (TURN_IMAGE == characteristic.uuid)
            {
                intent.putExtra(EXTRA_TYPE, 2)
                intent.putExtra(EXTRA_DATA, data)
            }
            else
            {
                return
            }

            sendBroadcast(intent)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    inner class LocalBinder : Binder()
    {
        val service: BLEService
            get() = this@BLEService
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onBind(intent: Intent): IBinder
    {
        return mBinder
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onUnbind(intent: Intent): Boolean
    {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close()
        return super.onUnbind(intent)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private val mBinder: IBinder = LocalBinder()

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    fun initialize(ctx: Context, observer: IBLEServiceObserver): Boolean
    {
        context = ctx
        bleServiceObserver = observer
        tag = context.getString(R.string.app_name)

        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.

        if (bluetoothManager == null)
        {
            bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            if (bluetoothManager == null)
            {
                Log.e(tag, "Unable to initialize BluetoothManager.")
                return false
            }
        }

        bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null)
        {
            Log.e(tag, "Unable to obtain a BluetoothAdapter.")
            return false
        }

        return true
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * `BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)`
     * callback.
     */
    fun connect(address: String?): Boolean
    {
        if ((bluetoothAdapter == null) || (address == null))
        {
            Log.w(tag, "BluetoothAdapter not initialized or unspecified address.")
            return false
        }

        // Previously connected device.  Try to reconnect.
        if ((bluetoothDeviceAddress != null) &&
            (address == bluetoothDeviceAddress) &&
            (bluetoothGatt != null)
        )
        {
            Log.d(tag, "Trying to use an existing mBluetoothGatt for connection.")

            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED)
            )
            {
                return if (bluetoothGatt?.connect() == true)
                {
                    connectionState = STATE_CONNECTING
                    true
                }
                else
                {
                    false
                }
            }

            return false
        }

        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null)
        {
            Log.w(tag, "Device not found.  Unable to connect.")
            return false
        }

        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        bluetoothGatt = device.connectGatt(this, false, mGattCallback, BluetoothDevice.TRANSPORT_LE)
        Log.d(tag, "Trying to create a new connection.")
        bluetoothDeviceAddress = address
        connectionState = STATE_CONNECTING

        return true
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * `BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)`
     * callback.
     */
    fun disconnect()
    {
        if (bluetoothAdapter == null || bluetoothGatt == null)
        {
            Log.w(tag, "BluetoothAdapter not initialized")
            return
        }

        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED)
        )
        {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    fun close()
    {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED)
        )
        {
            bluetoothGatt?.close()
        }

        bluetoothGatt = null
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Request a read on a given `BluetoothGattCharacteristic`. The read result is reported
     * asynchronously through the `BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)`
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic?)
    {
        if ((bluetoothAdapter == null) || (bluetoothGatt == null))
        {
            Log.e(tag, "readCharacteristic(): bluetoothAdapter not initialized")
            return
        }

        if (characteristic == null)
        {
            Log.e(tag, "readCharacteristic(): characteristic is null")
            return
        }

        Log.d(
            tag,
            "readCharacteristic(): uuid = " + SampleGattAttributes.lookup(
                characteristic.uuid.toString(),
                characteristic.uuid.toString()
            )
        )

        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED)
        )
        {
            bluetoothGatt?.readCharacteristic(characteristic)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic, enabled: Boolean)
    {
        if ((bluetoothAdapter == null) || (bluetoothGatt == null))
        {
            Log.w(tag, "BluetoothAdapter not initialized")
            return
        }

        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED)
        )
        {
            bluetoothGatt?.setCharacteristicNotification(characteristic, enabled)
        }

        val descriptor = characteristic.getDescriptor(CLIENT_CONFIG)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt?.writeDescriptor(descriptor)
        }
        else
            bluetoothGatt?.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            )
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after `BluetoothGatt#discoverServices()` completes successfully.
     *
     * @return A `List` of supported services.
     */
    val supportedGattServices: List<BluetoothGattService>?
        get() = bluetoothGatt?.services

    // ---------------------------------------------------------------------------------------------------------------------------

    companion object
    {
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2

        const val ACTION_GATT_CONNECTED =
            "com.magiclane.sdk.examples.bleclient.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED =
            "com.magiclane.sdk.examples.bleclient.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "com.magiclane.sdk.examples.bleclient.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE =
            "com.magiclane.sdk.examples.bleclient.ACTION_DATA_AVAILABLE"

        const val EXTRA_DATA = "com.magiclane.sdk.examples.bleclient.EXTRA_DATA"
        const val EXTRA_TYPE = "com.magiclane.sdk.examples.bleclient.EXTRA_TYPE"
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------