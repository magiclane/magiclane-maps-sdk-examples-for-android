/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.wificlient1

import android.annotation.SuppressLint
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.examples.wificlient1.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.wificlient1.databinding.ListitemDeviceBinding
import com.magiclane.sdk.examples.wificlient1.databinding.MainActivityBinding

/**
 * Activity for discovering and displaying WiFiServer1 instances available on the local network.
 *
 * The WiFi counterpart of the BLE example's device scan: instead of scanning for BLE
 * advertisements, it discovers the DNS-SD service the WiFiServer1 example registers
 * (see [NavProtocol.SERVICE_TYPE]) via Android's Network Service Discovery.
 */
class MainActivity : AppCompatActivity() {

    private data class DiscoveredServer(val name: String, val host: String, val port: Int)

    private lateinit var binding: MainActivityBinding
    private lateinit var tag: String

    private val nsdManager: NsdManager
        get() = getSystemService(NSD_SERVICE) as NsdManager

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // NsdManager allows only one resolve at a time; found services are queued and
    // resolved one by one.
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolveInProgress = false

    private val serverListAdapter = ServerListAdapter()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        tag = getString(R.string.app_name)

        binding.listView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            val separator = DividerItemDecoration(
                applicationContext,
                (layoutManager as LinearLayoutManager).orientation,
            )
            addItemDecoration(separator)

            val lateralPadding = resources.getDimension(R.dimen.big_padding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)

            itemAnimator = null
            adapter = serverListAdapter
        }

        binding.toolbar.title = getString(R.string.title_devices)
    }

    override fun onResume() {
        super.onResume()
        startDiscovery()
    }

    override fun onPause() {
        super.onPause()
        stopDiscovery()
    }

    // ---- Service discovery -----------------------------------------------------

    private fun startDiscovery() {
        if (discoveryListener != null) return

        binding.progressBar.visibility = View.VISIBLE

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(tag, "NSD discovery started for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(tag, "NSD service found: ${serviceInfo.serviceName}")
                enqueueResolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(tag, "NSD service lost: ${serviceInfo.serviceName}")
                runOnUiThread { serverListAdapter.removeServer(serviceInfo.serviceName) }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(tag, "NSD discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryListener = null
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    showDialog(getString(R.string.error_discovery_failed, errorCode))
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(tag, "NSD stop discovery failed: $errorCode")
            }
        }

        discoveryListener = listener
        nsdManager.discoverServices(NavProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: IllegalArgumentException) {
                Log.w(tag, "NSD discovery was not active: $e")
            }
        }
        discoveryListener = null

        synchronized(resolveQueue) {
            resolveQueue.clear()
        }
        binding.progressBar.visibility = View.GONE
    }

    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        synchronized(resolveQueue) {
            resolveQueue.addLast(serviceInfo)
            if (!resolveInProgress) {
                resolveNext()
            }
        }
    }

    /** Must be called while holding the [resolveQueue] lock. */
    private fun resolveNext() {
        val serviceInfo = resolveQueue.removeFirstOrNull()
        if (serviceInfo == null) {
            resolveInProgress = false
            return
        }
        resolveInProgress = true

        // resolveService() is deprecated in favor of registerServiceInfoCallback() (API 34+),
        // but it is the only option on the older releases this example supports.
        @Suppress("DEPRECATION")
        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val host = resolvedInfo.host?.hostAddress
                    Log.d(tag, "NSD service resolved: ${resolvedInfo.serviceName} -> $host:${resolvedInfo.port}")

                    if (host != null) {
                        runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            serverListAdapter.addServer(
                                DiscoveredServer(resolvedInfo.serviceName, host, resolvedInfo.port),
                            )
                        }
                    }

                    synchronized(resolveQueue) { resolveNext() }
                }

                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(tag, "NSD resolve failed for ${info.serviceName}: $errorCode")
                    synchronized(resolveQueue) { resolveNext() }
                }
            },
        )
    }

    // ---- Server list -------------------------------------------------------------

    private inner class ServerListAdapter : RecyclerView.Adapter<ServerListAdapter.ViewHolder>() {

        private val servers: ArrayList<DiscoveredServer> = arrayListOf()

        inner class ViewHolder(val binding: ListitemDeviceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val binding = ListitemDeviceBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
            return ViewHolder(binding)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val server = servers[position]

            viewHolder.binding.deviceName.text = server.name
            viewHolder.binding.deviceAddress.text = "${server.host}:${server.port}"

            viewHolder.itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, NavigationActivity::class.java)
                intent.putExtra(NavigationActivity.EXTRAS_SERVER_NAME, server.name)
                intent.putExtra(NavigationActivity.EXTRAS_SERVER_HOST, server.host)
                intent.putExtra(NavigationActivity.EXTRAS_SERVER_PORT, server.port)
                startActivity(intent)
            }
        }

        override fun getItemCount() = servers.size

        @SuppressLint("NotifyDataSetChanged")
        fun addServer(server: DiscoveredServer) {
            val existingIndex = servers.indexOfFirst { it.name == server.name }
            if (existingIndex >= 0) {
                if (servers[existingIndex] != server) {
                    servers[existingIndex] = server
                    notifyItemChanged(existingIndex)
                }
            } else {
                servers.add(server)
                notifyItemInserted(servers.size - 1)
            }
        }

        fun removeServer(name: String) {
            val index = servers.indexOfFirst { it.name == name }
            if (index >= 0) {
                servers.removeAt(index)
                notifyItemRemoved(index)
            }
        }
    }

    // ---- Dialog --------------------------------------------------------------

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
