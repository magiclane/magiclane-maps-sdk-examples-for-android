/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bleclient2

import android.Manifest
import android.app.ActivityOptions
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.examples.bleclient2.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.bleclient2.databinding.ListitemDeviceBinding
import com.magiclane.sdk.examples.bleclient2.databinding.MainActivityBinding

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: MainActivityBinding
    private var leDeviceListAdapter: LeDeviceListAdapter? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanning = false
    private lateinit var tag: String
    private val scanPeriod: Long = 10000
    private var permissionsAreGranted = false
    private var appInForeground = false
    private val handler = Handler(Looper.getMainLooper())
    private val stopScanningRunnable = Runnable {
        if (scanning) {
            scanLeDevice(false)

            if (leDeviceListAdapter?.itemCount == 0) {
                Log.d(
                    tag,
                    "scanLeDevice(): no item found after $scanPeriod seconds, repeat scanning",
                )
                scanLeDevice(true)
            }
        }
    }
    private var dataSetRefreshScheduled = false
    private val bluetoothBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (BluetoothAdapter.ACTION_STATE_CHANGED == action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                if (state == BluetoothAdapter.STATE_ON) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        startScanning()
                    } else {
                        requestPermissions()
                    }
                }
            }
        }
    }

    private val requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                if (permissionsAreGranted) {
                    startScanning()
                }
            } else {
                showDialog(getString(R.string.error_bluetooth_turned_off))
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissionsMap ->
            if (!permissionsMap.isNullOrEmpty()) {
                val permissionsGranted = permissionsMap.entries.all { it.value }

                if (permissionsGranted) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(this, MainActivity::class.java)
                        val options = ActivityOptions.makeCustomAnimation(this, 0, 0)
                        startActivity(intent, options.toBundle())
                        finish()
                    } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                        requestBackgroundLocationPermissionLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        )
                    } else {
                        startScanning()
                        permissionsAreGranted = true
                    }
                } else {
                    showDialog(getString(R.string.error_no_permissions))
                }
            }
        }

    private val requestBackgroundLocationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { permissionGranted ->
            if (permissionGranted) {
                startScanning()
                permissionsAreGranted = true
            } else {
                showDialog(getString(R.string.error_no_permissions))
            }
        }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)

        tag = getString(R.string.app_name)

        binding.listView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            val separator =
                DividerItemDecoration(
                    applicationContext,
                    (layoutManager as LinearLayoutManager).orientation,
                )
            addItemDecoration(separator)

            val lateralPadding = resources.getDimension(R.dimen.big_padding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)

            itemAnimator = null
        }

        binding.toolbar.title = getString(R.string.title_devices)

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            showDialog(getString(R.string.ble_not_supported))
        } else {
            // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
            // BluetoothAdapter through BluetoothManager.
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter

            // Checks if Bluetooth is supported on the device.
            if (bluetoothAdapter == null) {
                showDialog(getString(R.string.error_bluetooth_not_supported))
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED && checkSelfPermission(
                            Manifest.permission.BLUETOOTH_SCAN,
                        )
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissions()
                    } else {
                        permissionsAreGranted = true
                    }
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                    if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissions()
                    } else {
                        permissionsAreGranted = true
                    }
                } else {
                    requestPermissions()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (permissionsAreGranted) {
                    if (bluetoothAdapter?.isEnabled == false) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        requestBluetooth.launch(enableBtIntent)
                    } else {
                        startScanning()
                    }
                }
            } else {
                if (bluetoothAdapter?.isEnabled == false) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    requestBluetooth.launch(enableBtIntent)
                } else {
                    requestPermissions()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        appInForeground = true

        ContextCompat.registerReceiver(
            this,
            bluetoothBroadcastReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )

        // Ensures Bluetooth is enabled on the device. If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.

        if (permissionsAreGranted) {
            startScanning()
        }

        // Initializes list view adapter.
        leDeviceListAdapter = LeDeviceListAdapter()
        binding.listView.adapter = leDeviceListAdapter
    }

    override fun onPause() {
        super.onPause()

        unregisterReceiver(bluetoothBroadcastReceiver)

        appInForeground = false

        scanLeDevice(false)
        dataSetRefreshScheduled = false
        // leDeviceListAdapter?.clear()
    }

    private fun startScanning() {
        if (bluetoothAdapter?.isEnabled == true) {
            scanLeDevice(true)
        }
    }

    private fun scanLeDevice(enable: Boolean) {
        if (enable) {
            if (!scanning) {
                // Stops scanning after a pre-defined scan period.
                handler.postDelayed(stopScanningRunnable, scanPeriod)

                scanning = true
                startBLEScan()
                binding.progressBar.visibility = View.VISIBLE

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    if (!isLocationEnabled(this)) {
                        showDialog(
                            "Please turn on your location in order to make scanning possible.",
                        )
                    }
                }
            }
        } else {
            if (scanning) {
                handler.removeCallbacks(stopScanningRunnable)

                scanning = false
                stopBLEScan()
            }
        }
    }

    inner class LeDeviceListAdapter : RecyclerView.Adapter<LeDeviceListAdapter.ViewHolder>() {

        private val mLeDevices: ArrayList<BluetoothDevice> = arrayListOf()

        inner class ViewHolder(binding: ListitemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
            val deviceAddress: TextView = binding.deviceAddress
            val deviceName: TextView = binding.deviceName
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val binding = ListitemDeviceBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            viewHolder.apply {
                if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                    (
                        ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        ) == PackageManager.PERMISSION_GRANTED
                        )
                ) {
                    val device = mLeDevices[position]
                    val deviceName = device.name

                    if (!deviceName.isNullOrEmpty()) {
                        viewHolder.deviceName.text = deviceName
                    } else {
                        viewHolder.deviceName.setText(R.string.unknown_device)
                    }

                    viewHolder.deviceAddress.text = device.address

                    viewHolder.itemView.setOnClickListener {
                        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                            (
                                ActivityCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.BLUETOOTH_SCAN,
                                ) == PackageManager.PERMISSION_GRANTED
                                )
                        ) {
                            val intent = Intent(this@MainActivity, NavigationActivity::class.java)
                            intent.putExtra(NavigationActivity.EXTRAS_DEVICE_NAME, device.name)
                            intent.putExtra(
                                NavigationActivity.EXTRAS_DEVICE_ADDRESS,
                                device.address,
                            )
                            if (scanning) {
                                scanLeDevice(false)
                            }
                            startActivity(intent)
                        }
                    }
                }
            }
        }

        override fun getItemCount() = mLeDevices.size

        fun addDevice(device: BluetoothDevice) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device)
                notifyItemInserted(mLeDevices.size - 1)
            }
        }

        /*
        fun clear() {
            mLeDevices.clear()
        }
         */
    }

    private fun startBLEScan() {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN,
                ) == PackageManager.PERMISSION_GRANTED
                )
        ) {
            Log.d(tag, "startBLEScan()")
            bluetoothAdapter?.bluetoothLeScanner?.startScan(mScanCallback)
        } else {
            Log.d(tag, "startBLEScan(): missing Manifest.permission.BLUETOOTH_SCAN permission")
        }
    }

    private fun stopBLEScan() {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN,
                ) == PackageManager.PERMISSION_GRANTED
                )
        ) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(mScanCallback)
        }
    }

    /*
    // Device scan callback.
    private val mLeScanCallback = LeScanCallback { device, _, _ ->
        runOnUiThread {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                (
                    ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    ) == PackageManager.PERMISSION_GRANTED
                    )
            ) {
                Log.d(tag, "onScanResult(): result.device = ${device.name}")
            }

            addDevice(device)
        }
    }
     */

    private val mScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                (
                    ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    ) == PackageManager.PERMISSION_GRANTED
                    )
            ) {
                Log.d(tag, "onScanResult(): result.device = ${result.device.name}")
            }

            addDevice(result.device)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            if (results.isNotEmpty()) {
                binding.progressBar.visibility = View.GONE
            }

            for (result in results) {
                leDeviceListAdapter?.addDevice(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            showDialog("onScanFailed(): errorCode = $errorCode")
        }
    }

    private fun addDevice(device: BluetoothDevice) {
        leDeviceListAdapter?.addDevice(device)
        binding.progressBar.visibility = View.GONE
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        } else {
            permissionsAreGranted = true
        }
    }

    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    private fun showDialog(text: String) {
        val dialog = BottomSheetDialog(this)
        val binding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.error)
            message.text = text
            button.setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.apply {
            setCancelable(false)
            setContentView(binding.root)
            show()
        }
    }
}
