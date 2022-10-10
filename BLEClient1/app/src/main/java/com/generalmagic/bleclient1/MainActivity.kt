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

package com.generalmagic.bleclient1

// -------------------------------------------------------------------------------------------------------------------------------

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog

// -------------------------------------------------------------------------------------------------------------------------------

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
class MainActivity: AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    private var leDeviceListAdapter: LeDeviceListAdapter? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanning = false
    private lateinit var progressBar: ProgressBar
    private lateinit var listView: RecyclerView
    private lateinit var tag: String
    private val scanPeriod: Long = 10000
    private var permissionsAreGranted = false
    private var appInForeground = false
    private val handler = Handler(Looper.getMainLooper())
    private val stopScanningRunnable = Runnable {
        if (scanning)
        {
            scanLeDevice(false)

            if (leDeviceListAdapter?.itemCount == 0)
            {
                Log.d(tag, "scanLeDevice(): no item found after $scanPeriod seconds, repeat scanning")
                scanLeDevice(true)
            }
        }
    }
    private var dataSetRefreshScheduled = false
    private val bluetoothBroadcastReceiver = object : BroadcastReceiver()
    {
        override fun onReceive(context: Context?, intent: Intent?)
        {
            val action = intent?.action
            if (BluetoothAdapter.ACTION_STATE_CHANGED == action)
            {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                if (state == BluetoothAdapter.STATE_ON)
                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    {
                        startScanning()
                    }
                    else
                    {
                        requestPermissions()
                    }
                }
            }
        }
    }

    private val requestBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK)
        {
            if (permissionsAreGranted)
            {
                startScanning()
            }
        }
        else
        {
            showDialog(getString(R.string.error_bluetooth_turned_off))
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap ->
        if (!permissionsMap.isNullOrEmpty())
        {
            permissionsAreGranted = permissionsMap.entries.all { it.value }

            if (permissionsAreGranted)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                    overridePendingTransition(0, 0)
                }
                else
                {
                    startScanning()
                }
            }
            else
            {
                showDialog(getString(R.string.error_no_permissions))
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    public override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        tag = getString(R.string.app_name)

        progressBar = findViewById(R.id.progress_bar)

        listView = findViewById<RecyclerView>(R.id.list_view).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            val separator = DividerItemDecoration(applicationContext, (layoutManager as LinearLayoutManager).orientation)
            addItemDecoration(separator)

            val lateralPadding = resources.getDimension(R.dimen.big_padding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)

            itemAnimator = null
        }

        supportActionBar?.title = getString(R.string.title_devices)

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            showDialog(getString(R.string.ble_not_supported))
        }
        else
        {
            // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
            // BluetoothAdapter through BluetoothManager.
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter

            // Checks if Bluetooth is supported on the device.
            if (bluetoothAdapter == null)
            {
                showDialog(getString(R.string.error_bluetooth_not_supported))
            }
            else
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED)
                    {
                        requestPermissions()
                    }
                    else
                    {
                        permissionsAreGranted = true
                    }
                }
                else
                {
                    requestPermissions()
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            {
                if (permissionsAreGranted)
                {
                    if (bluetoothAdapter?.isEnabled == false)
                    {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        requestBluetooth.launch(enableBtIntent)
                    }
                    else
                    {
                        startScanning()
                    }
                }
            }
            else
            {
                if (bluetoothAdapter?.isEnabled == false)
                {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    requestBluetooth.launch(enableBtIntent)
                }
                else
                {
                    requestPermissions()
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onResume()
    {
        super.onResume()

        appInForeground = true

        registerReceiver(bluetoothBroadcastReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        
        // Ensures Bluetooth is enabled on the device. If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.

        if (permissionsAreGranted)
        {
            startScanning()
        }

        // Initializes list view adapter.
        leDeviceListAdapter = LeDeviceListAdapter()
        listView.adapter = leDeviceListAdapter
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onPause()
    {
        super.onPause()
        
        unregisterReceiver(bluetoothBroadcastReceiver)
        
        appInForeground = false

        scanLeDevice(false)
        dataSetRefreshScheduled = false
        // leDeviceListAdapter?.clear()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun startScanning()
    {
        if (bluetoothAdapter?.isEnabled == true)
        {
            scanLeDevice(true)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun scanLeDevice(enable: Boolean)
    {
        if (enable)
        {
            if (!scanning)
            {
                // Stops scanning after a pre-defined scan period.
                handler.postDelayed(stopScanningRunnable, scanPeriod)

                scanning = true
                startBLEScan()
                progressBar.visibility = View.VISIBLE
            }
        }
        else
        {
            if (scanning)
            {
                handler.removeCallbacks(stopScanningRunnable)

                scanning = false
                stopBLEScan()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    inner class LeDeviceListAdapter: RecyclerView.Adapter<LeDeviceListAdapter.ViewHolder>()
    {
        // -----------------------------------------------------------------------------------------------------------------------

        private val mLeDevices: ArrayList<BluetoothDevice> = arrayListOf()

        // -----------------------------------------------------------------------------------------------------------------------

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
        {
            val deviceAddress : TextView = view.findViewById(R.id.device_address)
            val deviceName : TextView = view.findViewById(R.id.device_name)
        }
        // -----------------------------------------------------------------------------------------------------------------------

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder
        {
            val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.listitem_device, viewGroup, false)
            return ViewHolder(view)
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int)
        {
            viewHolder.apply {
                if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                    (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED))
                {
                    val device = mLeDevices[position]
                    val deviceName = device.name

                    if (!deviceName.isNullOrEmpty())
                    {
                        viewHolder.deviceName.text = deviceName
                    }
                    else
                    {
                        viewHolder.deviceName.setText(R.string.unknown_device)
                    }

                    viewHolder.deviceAddress.text = device.address

                    viewHolder.itemView.setOnClickListener {
                        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                            (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED))
                        {
                            val intent = Intent(this@MainActivity, NavigationActivity::class.java)
                            intent.putExtra(NavigationActivity.EXTRAS_DEVICE_NAME, device.name)
                            intent.putExtra(NavigationActivity.EXTRAS_DEVICE_ADDRESS, device.address)
                            if (scanning)
                            {
                                scanLeDevice(false)
                            }
                            startActivity(intent)
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun getItemCount() = mLeDevices.size

        // -----------------------------------------------------------------------------------------------------------------------

        fun addDevice(device: BluetoothDevice)
        {
            if (!mLeDevices.contains(device))
            {
                mLeDevices.add(device)
                notifyItemInserted(mLeDevices.size - 1)
            }
        }

        // -----------------------------------------------------------------------------------------------------------------------

        fun clear()
        {
            mLeDevices.clear()
        }

        // -----------------------------------------------------------------------------------------------------------------------
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun startBLEScan()
    {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED))
        {
            Log.d(tag, "startBLEScan()")
            bluetoothAdapter?.bluetoothLeScanner?.startScan(mScanCallback)
        }
        else
        {
            Log.d(tag, "startBLEScan(): missing Manifest.permission.BLUETOOTH_SCAN permission")
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun stopBLEScan()
    {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED))
        {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(mScanCallback)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    // Device scan callback.
    private val mLeScanCallback = LeScanCallback { device, _, _ ->
        runOnUiThread {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED))
            {
                Log.d(tag, "onScanResult(): result.device = ${device.name}")
            }

            addDevice(device)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private val mScanCallback: ScanCallback = object : ScanCallback()
    {
        override fun onScanResult(callbackType: Int, result: ScanResult)
        {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED))
            {
                Log.d(tag, "onScanResult(): result.device = ${result.device.name}")
            }

            addDevice(result.device)
        }

        override fun onBatchScanResults(results: List<ScanResult>)
        {
            if (results.isNotEmpty())
            {
                progressBar.visibility = View.GONE
            }

            for (result in results)
            {
                leDeviceListAdapter?.addDevice(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int)
        {
            showDialog("onScanFailed(): errorCode = $errorCode")
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun addDevice(device: BluetoothDevice)
    {
        leDeviceListAdapter?.addDevice(device)
        progressBar.visibility = View.GONE
    }

    // ---------------------------------------------------------------------------------------------------------------------------
    
    private fun requestPermissions()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
        else
        {
            permissionsAreGranted = true
        }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    @SuppressLint("InflateParams")
    private fun showDialog(message: String)
    {
        Log.e(tag, message)
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_layout, null).apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.error)
            findViewById<TextView>(R.id.message).text = message
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
}

// -------------------------------------------------------------------------------------------------------------------------------
