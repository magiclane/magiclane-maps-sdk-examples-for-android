// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.bleserver

import android.Manifest
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import com.magiclane.sdk.core.TAG

/**
 * Return a configured [BluetoothGattService] instance for the
 * Current Time Service.
 */
class NavBluetoothManager(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val advertiseCallback: AdvertiseCallback,
    private val gattServerCallback : BluetoothGattServerCallback
)
{
    private val turnInstruction = BluetoothGattCharacteristic(
        MainActivity.TURN_INSTRUCTION, // Read-only characteristic, supports notifications
        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    )
    private val turnInstructionDescriptor = BluetoothGattDescriptor(
        MainActivity.CLIENT_CONFIG, // Read/write descriptor
        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
    )
    private val turnImage = BluetoothGattCharacteristic(
        MainActivity.TURN_IMAGE, // Read-only characteristic, supports notifications
        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    )
    private val turnImageDescriptor = BluetoothGattDescriptor(
        MainActivity.CLIENT_CONFIG, // Read/write descriptor
        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
    )
    private val turnDistance = BluetoothGattCharacteristic(
        MainActivity.TURN_DISTANCE, // Read-only characteristic, supports notifications
        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    )
    private val turnDistanceDescriptor = BluetoothGattDescriptor(
        MainActivity.CLIENT_CONFIG, // Read/write descriptor
        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
    )

    private val gattService = BluetoothGattService(MainActivity.NAVIGATION_SERVICE, SERVICE_TYPE_PRIMARY)

    lateinit var bluetoothGattServer : BluetoothGattServer

    init
    {
        turnInstruction.addDescriptor(turnInstructionDescriptor)
        turnImage.addDescriptor(turnImageDescriptor)
        turnDistance.addDescriptor(turnDistanceDescriptor)
        gattService.apply {
            addCharacteristic(turnInstruction)
            addCharacteristic(turnImage)
            addCharacteristic(turnDistance)
        }
    }

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Current Time Service.
     */
    private fun startAdvertising()
    {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED)
        )
        {
            val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
                bluetoothManager.adapter.bluetoothLeAdvertiser

            bluetoothLeAdvertiser?.let {
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .setConnectable(true)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .build()

                val data = AdvertiseData.Builder().setIncludeDeviceName(true)
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(ParcelUuid(MainActivity.NAVIGATION_SERVICE))
                    .build()

                it.startAdvertising(settings, data, advertiseCallback)
            } ?: Log.w(TAG, "Failed to create advertiser")
        }
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private fun stopAdvertising()
    {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED)
        )
        {
            val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
                bluetoothManager.adapter.bluetoothLeAdvertiser
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) ?: Log.w(
                TAG,
                "Failed to create advertiser"
            )
        }
    }

    /**
     * Shut down the GATT server.
     */
    private fun stopServer()
    {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED)
        )
        {
            bluetoothGattServer.close()
        }
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    private fun startServer()
    {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED)
        )
        {
            bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            bluetoothGattServer.addService(gattService)
        }
    }

    fun start() {
        startAdvertising()
        startServer()
    }

    fun stop(){
        stopServer()
        stopAdvertising()
    }
}