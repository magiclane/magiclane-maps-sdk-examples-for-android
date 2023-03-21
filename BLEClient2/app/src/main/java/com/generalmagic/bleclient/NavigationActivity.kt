// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.generalmagic.bleclient

// -------------------------------------------------------------------------------------------------------------------------------

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.generalmagic.bleclient.BLEService.LocalBinder
import java.util.*

// -------------------------------------------------------------------------------------------------------------------------------

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with `BluetoothLeService`, which in turn interacts with the
 * Bluetooth LE API.
 */
class NavigationActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    private lateinit var topPanel: ConstraintLayout
    private lateinit var navInstruction: TextView
    private lateinit var navInstructionDistance: TextView
    private lateinit var navInstructionIcon: ImageView
    private lateinit var bottomPanel: ConstraintLayout
    private lateinit var rtt: TextView
    private lateinit var rtd: TextView
    private lateinit var eta: TextView
    private lateinit var speed: TextView
    private lateinit var tag: String
    private var deviceAddress: String? = null
    private var bluetoothLeService: BLEService? = null
    private var characteristics: List<BluetoothGattCharacteristic> = listOf()

    // ---------------------------------------------------------------------------------------------------------------------------

    enum class ETurnEvent(val value: Int)
    {
        NotAvailable(0),
        Straight(1),
        Right(2),
        Right1(3),
        Right2(4),
        Left(5),
        Left1(6),
        Left2(7),
        LightLeft(8),
        LightLeft1(9),
        LightLeft2(10),
        LightRight(11),
        LightRight1(12),
        LightRight2(13),
        SharpRight(14),
        SharpRight1(15),
        SharpRight2(16),
        SharpLeft(17),
        SharpLeft1(18),
        SharpLeft2(19),
        RoundaboutExitRight(20),
        Roundabout(21),
        RoundRight(22),
        RoundLeft(23),
        ExitRight(24),
        ExitRight1(25),
        ExitRight2(26),
        InfoGeneric(27),
        DriveOn(28),
        ExitNo(29),
        ExitLeft(30),
        ExitLeft1(31),
        ExitLeft2(32),
        RoundaboutExitLeft(33),
        IntoRoundabout(34),
        StayOn(35),
        BoatFerry(36),
        RailFerry(37),
        InfoLane(38),
        InfoSign(39),
        LeftRight(40),
        RightLeft(41),
        KeepLeft(42),
        KeepRight(43),
        Start(44),
        Intermediate(45),
        Stop(46);

        override fun toString(): String = value.toString()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    // Code to manage Service lifecycle.
    private val mServiceConnection: ServiceConnection = object : ServiceConnection
    {
        // -----------------------------------------------------------------------------------------------------------------------

        override fun onServiceConnected(componentName: ComponentName, service: IBinder)
        {
            bluetoothLeService = (service as LocalBinder).service
            bluetoothLeService?.let {
                if (it.initialize(this@NavigationActivity))
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

                when (type)
                {
                    BLEService.TDataType.ETurnInstruction.value -> // turn instruction
                    {
                        topPanel.visibility = View.VISIBLE
                        navInstruction.text = intent.getStringExtra(BLEService.EXTRA_DATA)
                    }
                    BLEService.TDataType.ETurnDistance.value -> // turn distance
                    {
                        topPanel.visibility = View.VISIBLE
                        navInstructionDistance.text = intent.getStringExtra(BLEService.EXTRA_DATA)
                    }
                    BLEService.TDataType.ETurnImage.value -> // image id
                    {
                        val data = intent.getByteArrayExtra(BLEService.EXTRA_DATA)

                        Log.d(tag, "parse turn image data, data.size = ${data?.size}")

                        if ((data != null) && (data.size == 4))
                        {
                            Log.d(tag, "get turn image icon, data[0] = ${data[0]}")

                            var imageId = 0
                            when (data[0].toInt())
                            {
                                ETurnEvent.Straight.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_keep_going
                                }
                                ETurnEvent.Right.value, ETurnEvent.Right1.value, ETurnEvent.Right2.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_turn_right
                                }
                                ETurnEvent.Left.value, ETurnEvent.Left1.value, ETurnEvent.Left2.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_turn_left
                                }
                                ETurnEvent.LightLeft.value, ETurnEvent.LightLeft1.value, ETurnEvent.LightLeft2.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_keep_left
                                }
                                ETurnEvent.LightRight.value, ETurnEvent.LightRight1.value, ETurnEvent.LightRight2.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_keep_right
                                }
                                ETurnEvent.SharpRight.value, ETurnEvent.SharpRight1.value, ETurnEvent.SharpRight2.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_turn_hard_right
                                }
                                ETurnEvent.SharpLeft.value, ETurnEvent.SharpLeft1.value, ETurnEvent.SharpLeft2.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_turn_hard_left
                                }
                                ETurnEvent.RoundaboutExitRight.value ->
                                {
                                    imageId = R.drawable.ic_nav_roundabout_exit_ccw
                                }
                                ETurnEvent.Roundabout.value ->
                                {
                                    imageId = R.drawable.ic_nav_roundabout_fallback
                                }
                                ETurnEvent.RoundRight.value ->
                                {
                                    imageId = R.drawable.ic_nav_roundabout_ccw1_3
                                }
                                ETurnEvent.RoundLeft.value ->
                                {
                                    imageId = R.drawable.ic_nav_roundabout_cw1_3
                                }
                                ETurnEvent.ExitRight.value, ETurnEvent.ExitRight1.value, ETurnEvent.ExitRight2.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_fork_right
                                }
                                ETurnEvent.ExitLeft.value, ETurnEvent.ExitLeft1.value, ETurnEvent.ExitLeft2.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_fork_left
                                }
                                ETurnEvent.RoundaboutExitLeft.value ->
                                {
                                    imageId = R.drawable.ic_nav_roundabout_exit_cw
                                }
                                ETurnEvent.IntoRoundabout.value ->
                                {
                                    val entrance = data[1].toInt()
                                    val exit = data[2].toInt()
                                    val rightSide = data[3].toInt() == 1

                                    if (entrance == 6)
                                    {
                                        imageId = when (exit)
                                        {
                                            12 ->
                                            {
                                                if (rightSide)
                                                {
                                                    R.drawable.ic_nav_roundabout_ccw1_1
                                                }
                                                else
                                                {
                                                    R.drawable.ic_nav_roundabout_cw1_1
                                                }
                                            }
                                            10, 11 ->
                                            {
                                                if (rightSide)
                                                {
                                                    R.drawable.ic_nav_roundabout_ccw2_2
                                                }
                                                else
                                                {
                                                    R.drawable.ic_nav_roundabout_cw1_2
                                                }
                                            }
                                            1, 2 ->
                                            {
                                                if (rightSide)
                                                {
                                                    R.drawable.ic_nav_roundabout_ccw1_2
                                                }
                                                else
                                                {
                                                    R.drawable.ic_nav_roundabout_cw2_2
                                                }
                                            }
                                            7, 8, 9 ->
                                            {
                                                if (rightSide)
                                                {
                                                    R.drawable.ic_nav_roundabout_ccw3_3
                                                }
                                                else
                                                {
                                                    R.drawable.ic_nav_roundabout_cw1_3
                                                }
                                            }
                                            3, 4, 5 ->
                                            {
                                                if (rightSide)
                                                {
                                                    R.drawable.ic_nav_roundabout_ccw1_3
                                                }
                                                else
                                                {
                                                    R.drawable.ic_nav_roundabout_cw3_3
                                                }
                                            }
                                            else ->
                                            {
                                                R.drawable.ic_nav_roundabout_fallback
                                            }
                                        }
                                    }
                                    else
                                    {
                                        imageId = R.drawable.ic_nav_roundabout_fallback
                                    }
                                }
                                ETurnEvent.StayOn.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_keep_going
                                }
                                ETurnEvent.BoatFerry.value ->
                                {
                                    imageId = R.drawable.ic_nav_boat
                                }
                                ETurnEvent.RailFerry.value ->
                                {
                                    imageId = R.drawable.ic_nav_train
                                }
                                ETurnEvent.LeftRight.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_turn_left
                                }
                                ETurnEvent.RightLeft.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_turn_right
                                }
                                ETurnEvent.KeepLeft.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_keep_left
                                }
                                ETurnEvent.KeepRight.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_keep_right
                                }
                                ETurnEvent.Start.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_start
                                }
                                ETurnEvent.Intermediate.value, ETurnEvent.Stop.value ->
                                {
                                    imageId = R.drawable.ic_nav_arrow_finish
                                }
                            }

                            if (imageId != 0)
                            {
                                topPanel.visibility = View.VISIBLE
                                navInstructionIcon.setImageResource(imageId)
                                navInstructionIcon.setColorFilter(Color.WHITE)
                            }
                            else
                            {
                                topPanel.visibility = View.GONE
                            }
                        }
                    }
                    BLEService.TDataType.ERemainingTravelTime.value -> // remaining travel time
                    {
                        Log.d(tag, "remaining travel time = ${intent.getStringExtra(BLEService.EXTRA_DATA)}")
                        val rttVal = intent.getStringExtra(BLEService.EXTRA_DATA)?.toInt() ?: 0
                        val remainingTravelTime = getRtt(rttVal)
                        if (remainingTravelTime.isNotEmpty())
                        {
                            bottomPanel.visibility = View.VISIBLE
                            rtt.text = remainingTravelTime
                            eta.text = getEta(rttVal)
                        }
                    }
                    BLEService.TDataType.ERemainingTravelDistance.value -> // remaining travel distance
                    {
                        Log.d("tag", "remaining travel distance = ${intent.getStringExtra(BLEService.EXTRA_DATA)}")
                        val remainingTravelDistance = getRtd(intent.getStringExtra(BLEService.EXTRA_DATA)?.toInt() ?: 0)
                        if (remainingTravelDistance.isNotEmpty())
                        {
                            bottomPanel.visibility = View.VISIBLE
                            rtd.text = remainingTravelDistance
                        }
                    }
                    BLEService.TDataType.ESpeed.value -> // speed
                    {
                        val speedStr = intent.getStringExtra(BLEService.EXTRA_DATA)
                        if (speedStr?.isNotEmpty() == true)
                        {
                            val speedInMps = speedStr.toInt().toDouble() / 100000
                            val speedInKmH = (speedInMps * 3.6 + 0.5).toInt().toString()

                            if (speedInKmH.isNotEmpty())
                            {
                                speed.visibility = View.VISIBLE
                                speed.text = speedInKmH + " km/h"
                            }
                            else
                            {
                                speed.visibility = View.GONE
                            }

                            Log.d(tag, "speed = $speedInMps")
                        }
                    }
                }
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
        bottomPanel = findViewById(R.id.bottom_panel)
        rtt = findViewById(R.id.rtt)
        rtd = findViewById(R.id.rtd)
        eta = findViewById(R.id.eta)
        speed = findViewById(R.id.speed)

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
                        if ((gattCharacteristic.uuid == SampleGattAttributes.TURN_INSTRUCTION) &&
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

    /**
     * Formats the given time seconds as pair of (value, unit).
     * @param timeInSeconds Value to be formatted.
     * @param bForceHours Forces to format in hours even if timeInSeconds isn't big enough.
     * @param bCapitalizeResult Capitalize the whole result pair.
     */
    private fun getTimeText(timeInSeconds: Int, bForceHours: Boolean = false, bCapitalizeResult: Boolean = false): Pair<String, String>
    {
        val time: String
        val unit: String
        if (timeInSeconds == 0)
        {
            time = "0"
            unit = "min"

            return Pair(time, unit)
        }

        var nMin: Int
        val nHour: Int

        if ((timeInSeconds >= 3600) || bForceHours)
        {
            nMin = timeInSeconds / 60
            nHour = nMin / 60
            nMin -= (nHour * 60)

            time = String.format("%d:%02d", nHour, nMin)
            unit = "hr"
        }
        else if (timeInSeconds >= 60)
        {
            nMin = timeInSeconds / 60
            time = String.format("%d", nMin)

            unit = "min"
        }
        else
        {
            time = String.format("%d", 1)
            unit = "min"
        }

        if (bCapitalizeResult)
        {
            time.uppercase(Locale.getDefault())
            unit.uppercase(Locale.getDefault())
        }

        return Pair(time, unit)
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    private fun getRtt(timeToDestinationInSeconds: Int): String
    {
        return getTimeText(timeToDestinationInSeconds).let { pair -> pair.first + " " + pair.second }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun getDistText(meters: Int, bHighResolution: Boolean = false, bCapitalizeUnit: Boolean = false): Pair<String, String>
    {
        var mMeters = meters
        var distance = ""
        val unit: String
        
        val upperLimit: Double = if (bHighResolution) 100000.0 else 20000.0

        if (mMeters >= upperLimit)
        { // >20 km - 1 km accuracy - KM
            mMeters = ((mMeters + 500) / 1000) * 1000

            distance = "${mMeters / 1000}"
            unit = "km"
        }
        else if ((mMeters >= 1000) && (mMeters < upperLimit))
        { // 1 - 20 km - 0.1 km accuracy - KM
            mMeters = ((mMeters + 50) / 100) * 100

            distance = String.format("%.1f", mMeters.toFloat() / 1000)
            unit = "km"
        }
        else if ((mMeters >= 500) && (mMeters < 1000))
        { // 500 - 1,000 m - 50 m accuracy - M
            mMeters = ((mMeters + 25) / 50) * 50

            if (mMeters == 1000)
            {
                distance = String.format("%.1f", mMeters.toFloat() / 1000)
                unit = "km"
            }
            else
            {
                distance = String.format("%d", mMeters)
                unit = "m"
            }
        }
        else if ((mMeters >= 200) && (mMeters < 500))
        { // 200 - 500 m - 25 m accuracy - M
            mMeters = ((mMeters + 12) / 25) * 25

            distance = String.format("%d", mMeters)
            unit = "m"
        }
        else if ((mMeters >= 100) && (mMeters < 200))
        { // 100 - 200 m - 10 m accuracy - M
            mMeters = ((mMeters + 5) / 10) * 10

            distance = String.format("%d", mMeters)
            unit = "m"
        }
        else
        { // 0 - 100 m - 5 m accuracy - M
            mMeters = ((mMeters + 2) / 5) * 5

            distance = String.format("%d", mMeters)
            unit = "m"
        }

        if (bCapitalizeUnit)
        {
            unit.uppercase(Locale.getDefault())
        }

        val decimalSeparator = '.'
        val groupingSeparator = ','

        if (distance.indexOf(',') >= 0)
        {
            distance.replace(',', decimalSeparator)
        }
        else
        { // if (distance.find(".") >= 0)
            distance.replace('.', decimalSeparator)
        }

        var index: Int = distance.indexOf(decimalSeparator)
        if (index < 0)
        {
            index = distance.length
        }

        for ((j, i) in (index - 1 downTo 0).withIndex())
        {
            val nRemaining: Int = j % 3
            if (nRemaining == 0)
            {
                val preSeparator = distance.substring(0, i)
                val postSeparator = distance.subSequence(i, distance.length)
                distance.format("$preSeparator$groupingSeparator$postSeparator")
            }
        }

        return Pair(distance, unit)
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private fun getRtd(distanceToDestinationInMeters: Int): String
    {
        return getDistText(distanceToDestinationInMeters).let { pair -> pair.first + " " + pair.second }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun getEta(timeToDestinationInSeconds: Int): String
    {
        val calendar = Calendar.getInstance()
        calendar.time = Date(System.currentTimeMillis() + timeToDestinationInSeconds.toLong() * 1000)

        return String.format("%d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
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
