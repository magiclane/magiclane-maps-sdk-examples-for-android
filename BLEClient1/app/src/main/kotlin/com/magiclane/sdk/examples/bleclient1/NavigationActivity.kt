/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bleclient1

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
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
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.set
import androidx.core.view.WindowCompat
import androidx.databinding.DataBindingUtil
import com.magiclane.sdk.examples.bleclient1.BLEService.LocalBinder
import com.magiclane.sdk.examples.bleclient1.databinding.NavigationActivityBinding

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with `BluetoothLeService`, which in turn interacts with the
 * Bluetooth LE API.
 */
class NavigationActivity : AppCompatActivity(), BLEService.IBLEServiceObserver {

    private lateinit var binding: NavigationActivityBinding
    private lateinit var tag: String
    private var deviceAddress: String? = null
    private var bluetoothLeService: BLEService? = null
    private var characteristics: List<BluetoothGattCharacteristic> = listOf()
    private var padding: Int = 0

    // Code to manage Service lifecycle.
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            bluetoothLeService = (service as LocalBinder).service
            bluetoothLeService?.let {
                if (it.initialize(this@NavigationActivity, this@NavigationActivity)) {
                    // Automatically connects to the device upon successful start-up initialization.
                    Handler(Looper.getMainLooper()).post {
                        it.connect(deviceAddress)
                    }
                } else {
                    showError("Unable to initialize Bluetooth")
                }
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothLeService = null
        }
    }

    /**
     * fun createBitmap(img: ByteArray?, width: Int, height: Int): Bitmap?
     * {
     * if ((img == null) ||
     * (width <= 0) ||
     * (height <= 0) ||
     * (img.size < (width * height * 4)))
     * {
     * return null
     * }
     *
     * val byteBuffer: ByteBuffer = ByteBuffer.wrap(img)
     * byteBuffer.order(ByteOrder.nativeOrder())
     * val buffer: IntBuffer = byteBuffer.asIntBuffer()
     * val imgArray = IntArray(width * height)
     * buffer.get(imgArray)
     * val result = Bitmap.createBitmap(imgArray, width, height, Bitmap.Config.ARGB_8888)
     * result.density = DisplayMetrics.DENSITY_MEDIUM
     * return result
     * }
     */

    /**
     * fun createBitmap(img: ByteArray?, width: Int, height: Int): Bitmap?
     * {
     * if ((img == null) ||
     * (width <= 0) ||
     * (height <= 0) ||
     * (img.size < (width * height * 2)))
     * {
     * return null
     * }
     *
     * val byteBuffer: ByteBuffer = ByteBuffer.wrap(img)
     * byteBuffer.order(ByteOrder.nativeOrder())
     *
     * val result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
     * result.copyPixelsFromBuffer(byteBuffer)
     * result.density = DisplayMetrics.DENSITY_MEDIUM
     * return result
     * }
     */

    fun createBitmap(img: ByteArray?, width: Int, height: Int): Bitmap? {
        if ((img == null) ||
            (width <= 0) ||
            (height <= 0) ||
            (img.size < (width * height))
        ) {
            return null
        }

        var index = 0
        var g: Int

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                g = img[index++].toUByte().toInt()
                if (g % 2 == 1) {
                    result[x, y] = Color.argb(g, 255, 0, 0)
                } else {
                    result[x, y] = Color.rgb(g, g, g)
                }
            }
        }

        result.density = DisplayMetrics.DENSITY_MEDIUM
        return result
    }

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BLEService.ACTION_GATT_CONNECTED == action) {
                updateConnectionState(R.string.connected)
            } else if (BLEService.ACTION_GATT_DISCONNECTED == action) {
                updateConnectionState(R.string.disconnected)
                clearUI()
            } else if (BLEService.ACTION_GATT_SERVICES_DISCOVERED == action) {
                // Show all the supported services and characteristics on the user interface.
                parseGattServices(bluetoothLeService?.supportedGattServices)
            } else if (BLEService.ACTION_DATA_AVAILABLE == action) {
                val type = intent.getIntExtra(BLEService.EXTRA_TYPE, -1)

                Log.d(tag, "receive data, type = $type")

                if (type == 0) {
                    binding.topPanel.visibility = View.VISIBLE
                    binding.navInstruction.text = intent.getStringExtra(BLEService.EXTRA_DATA)
                } else if (type == 1) {
                    binding.topPanel.visibility = View.VISIBLE
                    binding.instrDistance.text = intent.getStringExtra(BLEService.EXTRA_DATA)
                } else if (type == 2) {
                    val data = intent.getByteArrayExtra(BLEService.EXTRA_DATA)

                    Log.d(tag, "parse turn image data, data.size = ${data?.size}")

                    if ((data != null) && data.isNotEmpty()) {
                        if (data.size > 1) {
                            bluetoothLeService?.let {
                                binding.topPanel.visibility = View.VISIBLE

                                val bmp = createBitmap(data, it.turnImageSize, it.turnImageSize)
                                binding.navIcon.setImageBitmap(bmp)
                            }
                        } else {
                            binding.topPanel.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    override fun onCharacteristicRead(bluetoothGattCharacteristic: BluetoothGattCharacteristic) {
        Log.d(
            tag,
            "onCharacteristicRead(): uuid = " + SampleGattAttributes.lookup(
                bluetoothGattCharacteristic.uuid.toString(),
                bluetoothGattCharacteristic.uuid.toString(),
            ),
        )

        val nextUUIDToRead = when (bluetoothGattCharacteristic.uuid) {
            SampleGattAttributes.TURN_IMAGE -> {
                SampleGattAttributes.TURN_DISTANCE
            }

            SampleGattAttributes.TURN_DISTANCE -> {
                SampleGattAttributes.TURN_INSTRUCTION
            }

            else -> {
                return
            }
        }

        for (characteristic in characteristics) {
            if (characteristic.uuid == nextUUIDToRead &&
                (characteristic.properties or BluetoothGattCharacteristic.PROPERTY_READ) > 0
            ) {
                Handler(Looper.getMainLooper()).post {
                    bluetoothLeService?.readCharacteristic(characteristic)
                }
                break
            }
        }
    }

    private fun clearUI() {
        binding.topPanel.visibility = View.GONE
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.navigation_activity)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        tag = getString(R.string.app_name)
        deviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS)
        padding = resources.getDimension(R.dimen.big_padding).toInt()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = intent.getStringExtra(
            EXTRAS_DEVICE_NAME,
        ).plus(" - ").plus(getString(R.string.disconnected))

        val upArrow = ResourcesCompat.getDrawable(resources, R.drawable.ic_arrow_back_white, theme)
        supportActionBar?.setHomeAsUpIndicator(upArrow)

        val gattServiceIntent = Intent(this, BLEService::class.java)
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE)

        ContextCompat.registerReceiver(
            this,
            gattUpdateReceiver,
            makeGattUpdateIntentFilter(),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED)
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED)
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED)
        intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE)
        return intentFilter
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(gattUpdateReceiver)
        unbindService(mServiceConnection)
        bluetoothLeService?.let {
            it.disconnect()
            bluetoothLeService = null
        }
    }

    private fun updateConnectionState(resourceId: Int) {
        runOnUiThread {
            supportActionBar?.title = intent.getStringExtra(
                EXTRAS_DEVICE_NAME,
            ).plus(" - ").plus(getString(resourceId))
        }
    }

    private fun parseGattServices(gattServices: List<BluetoothGattService>?) {
        Log.d(tag, "parseGattServices(): services count = ${gattServices?.size}")

        if (gattServices != null) {
            for (gattService in gattServices) {
                if (gattService.uuid == SampleGattAttributes.NAVIGATION_SERVICE) {
                    Log.d(tag, "parseGattServices(): found navigation service!")

                    characteristics = gattService.characteristics
                    for (gattCharacteristic in characteristics) {
                        if ((gattCharacteristic.uuid == SampleGattAttributes.TURN_IMAGE) &&
                            ((gattCharacteristic.properties or BluetoothGattCharacteristic.PROPERTY_READ) > 0)
                        ) {
                            bluetoothLeService?.readCharacteristic(gattCharacteristic)
                            break
                        }
                    }

                    break
                }
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun showError(message: String) {
        Log.e(tag, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRAS_DEVICE_NAME = "DEVICE_NAME"
        const val EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS"
    }
}
