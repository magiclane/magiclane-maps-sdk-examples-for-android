// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.bleclient1

// -------------------------------------------------------------------------------------------------------------------------------

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.*
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.magiclane.sdk.examples.bleclient1.BLEService.LocalBinder

// -------------------------------------------------------------------------------------------------------------------------------

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with `BluetoothLeService`, which in turn interacts with the
 * Bluetooth LE API.
 */
class NavigationActivity : AppCompatActivity(), BLEService.IBLEServiceObserver
{
    // ---------------------------------------------------------------------------------------------------------------------------

    private lateinit var topPanel: ConstraintLayout
    private lateinit var navInstruction: TextView
    private lateinit var navInstructionDistance: TextView
    private lateinit var navInstructionIcon: ImageView
    private lateinit var tag: String
    private var deviceAddress: String? = null
    private var bluetoothLeService: BLEService? = null
    private var characteristics: List<BluetoothGattCharacteristic> = listOf()
    
    // ---------------------------------------------------------------------------------------------------------------------------

    // Code to manage Service lifecycle.
    private val mServiceConnection: ServiceConnection = object : ServiceConnection
    {
        // -----------------------------------------------------------------------------------------------------------------------

        override fun onServiceConnected(componentName: ComponentName, service: IBinder)
        {
            bluetoothLeService = (service as LocalBinder).service
            bluetoothLeService?.let {
                if (it.initialize(this@NavigationActivity, this@NavigationActivity))
                {
                    // Automatically connects to the device upon successful start-up initialization.
                    Handler(Looper.getMainLooper()).post{
                        it.connect(deviceAddress)
                    }
                }
                else
                {
                    showError("Unable to initialize Bluetooth")
                }
            }
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun onServiceDisconnected(componentName: ComponentName)
        {
            bluetoothLeService = null
        }

        // -----------------------------------------------------------------------------------------------------------------------
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /*
    fun createBitmap(img: ByteArray?, width: Int, height: Int): Bitmap?
    {
        if ((img == null) ||
            (width <= 0) ||
            (height <= 0) ||
            (img.size < (width * height * 4)))
        {
            return null
        }

        val byteBuffer: ByteBuffer = ByteBuffer.wrap(img)
        byteBuffer.order(ByteOrder.nativeOrder())
        val buffer: IntBuffer = byteBuffer.asIntBuffer()
        val imgArray = IntArray(width * height)
        buffer.get(imgArray)
        val result = Bitmap.createBitmap(imgArray, width, height, Bitmap.Config.ARGB_8888)
        result.density = DisplayMetrics.DENSITY_MEDIUM
        return result
    }
     */

    // ---------------------------------------------------------------------------------------------------------------------------

    /*
    fun createBitmap(img: ByteArray?, width: Int, height: Int): Bitmap?
    {
        if ((img == null) ||
            (width <= 0) ||
            (height <= 0) ||
            (img.size < (width * height * 2)))
        {
            return null
        }

        val byteBuffer: ByteBuffer = ByteBuffer.wrap(img)
        byteBuffer.order(ByteOrder.nativeOrder())

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        result.copyPixelsFromBuffer(byteBuffer)
        result.density = DisplayMetrics.DENSITY_MEDIUM
        return result
    }
    */

    // ---------------------------------------------------------------------------------------------------------------------------

    fun createBitmap(img: ByteArray?, width: Int, height: Int): Bitmap?
    {
        if ((img == null) ||
            (width <= 0) ||
            (height <= 0) ||
            (img.size < (width * height)))
        {
            return null
        }

        var index = 0
        var g: Int

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height)
        {
            for (x in 0 until width)
            {
                g = img[index++].toUByte().toInt()
                if (g % 2 == 1)
                {
                    result.setPixel(x, y, Color.argb(g, 255, 0, 0))
                }
                else
                {
                    result.setPixel(x, y, Color.rgb(g, g, g))
                }
            }
        }

        result.density = DisplayMetrics.DENSITY_MEDIUM
        return result
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver()
    {
        override fun onReceive(context: Context, intent: Intent)
        {
            val action = intent.action
            if (BLEService.ACTION_GATT_CONNECTED == action)
            {
                updateConnectionState(R.string.connected)
            }
            else if (BLEService.ACTION_GATT_DISCONNECTED == action)
            {
                updateConnectionState(R.string.disconnected)
                clearUI()
            }
            else if (BLEService.ACTION_GATT_SERVICES_DISCOVERED == action)
            {
                // Show all the supported services and characteristics on the user interface.
                parseGattServices(bluetoothLeService?.supportedGattServices)
            }
            else if (BLEService.ACTION_DATA_AVAILABLE == action)
            {
                val type = intent.getIntExtra(BLEService.EXTRA_TYPE, -1)

                Log.d(tag, "receive data, type = $type")

                if (type == 0)
                {
                    topPanel.visibility = View.VISIBLE
                    navInstruction.text = intent.getStringExtra(BLEService.EXTRA_DATA)
                }
                else if (type == 1)
                {
                    topPanel.visibility = View.VISIBLE
                    navInstructionDistance.text = intent.getStringExtra(BLEService.EXTRA_DATA)
                }
                else if (type == 2)
                {
                    val data = intent.getByteArrayExtra(BLEService.EXTRA_DATA)

                    Log.d(tag, "parse turn image data, data.size = ${data?.size}")

                    if ((data != null) && data.isNotEmpty())
                    {
                        if (data.size > 1)
                        {
                            bluetoothLeService?.let {
                                topPanel.visibility = View.VISIBLE

                                val bmp = createBitmap(data, it.turnImageSize, it.turnImageSize)
                                navInstructionIcon.setImageBitmap(bmp)
                            }
                        }
                        else
                        {
                            topPanel.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCharacteristicRead(gattCharacteristic: BluetoothGattCharacteristic)
    {
        Log.d(tag, "onCharacteristicRead(): uuid = " + SampleGattAttributes.lookup(gattCharacteristic.uuid.toString(), gattCharacteristic.uuid.toString()))

        val nextUUIDToRead = when (gattCharacteristic.uuid)
        {
            SampleGattAttributes.TURN_IMAGE ->
            {
                SampleGattAttributes.TURN_DISTANCE
            }
            SampleGattAttributes.TURN_DISTANCE ->
            {
                SampleGattAttributes.TURN_INSTRUCTION
            }
            else ->
            {
                return
            }
        }

        for (characteristic in characteristics)
        {
            if (characteristic.uuid == nextUUIDToRead &&
                (characteristic.properties or BluetoothGattCharacteristic.PROPERTY_READ) > 0)
            {
                Handler(Looper.getMainLooper()).post {
                    bluetoothLeService?.readCharacteristic(characteristic)
                }
                break
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun clearUI()
    {
        topPanel.visibility = View.GONE
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    public override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.navigation_activity)

        tag = getString(R.string.app_name)
        deviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS)

        topPanel = findViewById(R.id.top_panel)
        navInstruction = findViewById(R.id.nav_instruction)
        navInstructionDistance = findViewById(R.id.instr_distance)
        navInstructionIcon = findViewById(R.id.nav_icon)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = intent.getStringExtra(EXTRAS_DEVICE_NAME).plus(" - ").plus(getString(R.string.disconnected))

        val gattServiceIntent = Intent(this, BLEService::class.java)
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE)

        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun makeGattUpdateIntentFilter(): IntentFilter
    {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED)
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED)
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED)
        intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE)
        return intentFilter
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onSupportNavigateUp(): Boolean
    {
        onBackPressed()
        return true
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()
        unregisterReceiver(gattUpdateReceiver)
        unbindService(mServiceConnection)
        bluetoothLeService?.let {
            it.disconnect()
            bluetoothLeService = null
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun updateConnectionState(resourceId: Int)
    {
        runOnUiThread {
            supportActionBar?.title = intent.getStringExtra(EXTRAS_DEVICE_NAME).plus(" - ").plus(getString(resourceId))
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun parseGattServices(gattServices: List<BluetoothGattService>?)
    {
        Log.d(tag, "parseGattServices(): services count = ${gattServices?.size}")

        if (gattServices != null)
        {
            for (gattService in gattServices)
            {
                if (gattService.uuid == SampleGattAttributes.NAVIGATION_SERVICE)
                {
                    Log.d(tag, "parseGattServices(): found navigation service!")

                    characteristics = gattService.characteristics
                    for (gattCharacteristic in characteristics)
                    {
                        if ((gattCharacteristic.uuid == SampleGattAttributes.TURN_IMAGE) &&
                            ((gattCharacteristic.properties or BluetoothGattCharacteristic.PROPERTY_READ) > 0))
                        {
                            bluetoothLeService?.readCharacteristic(gattCharacteristic)
                            break
                        }
                    }

                    break
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun showError(message: String)
    {
        Log.e(tag, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    companion object
    {
        const val EXTRAS_DEVICE_NAME = "DEVICE_NAME"
        const val EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS"
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
