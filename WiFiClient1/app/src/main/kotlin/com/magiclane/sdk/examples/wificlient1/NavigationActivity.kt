/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.wificlient1

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import com.magiclane.sdk.examples.wificlient1.WiFiService.LocalBinder
import com.magiclane.sdk.examples.wificlient1.databinding.NavigationActivityBinding
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * For a WiFiServer1 selected on the discovery screen, this Activity connects to it (through
 * [WiFiService]) and displays the navigation data it sends: the next-turn icon, the distance
 * to the next turn and the turn instruction text.
 */
class NavigationActivity : AppCompatActivity() {

    private lateinit var binding: NavigationActivityBinding
    private lateinit var tag: String
    private var serverHost: String? = null
    private var serverPort: Int = 0
    private var wifiService: WiFiService? = null

    // Captured once at portrait orientation; all subsequent constraint updates are applied on
    // top of this baseline so portrait layout is never recomputed from scratch.
    private lateinit var portraitConstraintSet: ConstraintSet

    // Code to manage Service lifecycle.
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            wifiService = (service as LocalBinder).service

            val host = serverHost
            if (host != null) {
                // Automatically connects to the server upon successful start-up initialization.
                wifiService?.connect(host, serverPort)
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            wifiService = null
        }
    }

    // Handles the events fired by the Service.
    // ACTION_CONNECTED: connected to the WiFiServer1.
    // ACTION_DISCONNECTED: disconnected from the WiFiServer1.
    // ACTION_DATA_AVAILABLE: received navigation data from the server.
    private val connectionUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WiFiService.ACTION_CONNECTED -> {
                    updateConnectionState(R.string.connected)
                }

                WiFiService.ACTION_DISCONNECTED -> {
                    updateConnectionState(R.string.disconnected)
                    clearUI()
                }

                WiFiService.ACTION_DATA_AVAILABLE -> {
                    val type = intent.getIntExtra(WiFiService.EXTRA_TYPE, -1)
                    Log.d(tag, "receive data, type = $type")

                    when (type) {
                        WiFiService.DATA_TYPE_INSTRUCTION -> {
                            binding.topPanel.visibility = View.VISIBLE
                            binding.navInstruction.text = intent.getStringExtra(WiFiService.EXTRA_DATA)
                        }

                        WiFiService.DATA_TYPE_DISTANCE -> {
                            binding.topPanel.visibility = View.VISIBLE
                            binding.instrDistance.text = intent.getStringExtra(
                                WiFiService.EXTRA_DATA,
                            )
                        }

                        WiFiService.DATA_TYPE_TURN_IMAGE -> {
                            // The next-turn icon rendered by the server, as PNG bytes (like the
                            // BLEClient1 example, which receives the icon's raw pixels). Empty
                            // means navigation ended.
                            val data = intent.getByteArrayExtra(WiFiService.EXTRA_DATA)

                            Log.d(tag, "parse turn image, data.size = ${data?.size}")

                            if ((data != null) && data.isNotEmpty()) {
                                val turnImage = BitmapFactory.decodeByteArray(data, 0, data.size)
                                if (turnImage != null) {
                                    binding.topPanel.visibility = View.VISIBLE
                                    binding.navIcon.setImageBitmap(turnImage)
                                }
                            } else {
                                binding.topPanel.visibility = View.GONE
                            }
                        }

                        WiFiService.DATA_TYPE_ROUTE -> {
                            // [remaining travel time in seconds, remaining travel distance in
                            // meters]; the bottom panel values are formatted here, like the
                            // BLEClient2 example does.
                            val data = intent.getIntArrayExtra(WiFiService.EXTRA_DATA)

                            if ((data != null) && (data.size == 2) && (data[0] >= 0) && (data[1] >= 0)) {
                                binding.bottomPanel.visibility = View.VISIBLE
                                binding.eta.text = getEta(data[0])
                                binding.rtt.text = getRtt(data[0])
                                binding.rtd.text = getRtd(data[1])
                            }
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    private fun clearUI() {
        binding.topPanel.visibility = View.GONE
        binding.bottomPanel.visibility = View.GONE
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.navigation_activity)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        tag = getString(R.string.app_name)
        serverHost = intent.getStringExtra(EXTRAS_SERVER_HOST)
        serverPort = intent.getIntExtra(EXTRAS_SERVER_PORT, 0)

        // Snapshot portrait constraints so landscape adjustments always start from a clean
        // baseline, then lay the panels out for the current orientation (like the WiFiServer1
        // example does).
        portraitConstraintSet = ConstraintSet().apply { clone(binding.root as ConstraintLayout) }
        applyOrientationLayout()

        // Re-apply orientation layout when window insets first arrive so landscape cold-starts
        // and orientation changes both get the correct left / bottom system bar offsets.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyOrientationLayout()
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val upArrow = ResourcesCompat.getDrawable(resources, R.drawable.ic_arrow_back_white, theme)
        supportActionBar?.setHomeAsUpIndicator(upArrow)

        // Server name on the first line, connection status on the second one.
        supportActionBar?.title = intent.getStringExtra(EXTRAS_SERVER_NAME)
        supportActionBar?.subtitle = getString(R.string.disconnected)

        val serviceIntent = Intent(this, WiFiService::class.java)
        bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE)

        ContextCompat.registerReceiver(
            this,
            connectionUpdateReceiver,
            makeConnectionUpdateIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun makeConnectionUpdateIntentFilter(): IntentFilter {
        val intentFilter = IntentFilter()
        intentFilter.addAction(WiFiService.ACTION_CONNECTED)
        intentFilter.addAction(WiFiService.ACTION_DISCONNECTED)
        intentFilter.addAction(WiFiService.ACTION_DATA_AVAILABLE)
        return intentFilter
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout()
    }

    /**
     * Adjusts panel constraint widths and horizontal positions for portrait/landscape, like the
     * WiFiServer1 example does. In landscape, the panels occupy the left 40 % of the screen.
     * Restores live visibility state after ConstraintSet.applyTo(), which would otherwise reset
     * all views to their cloned (GONE) visibility.
     */
    private fun applyOrientationLayout() {
        val rootLayout = binding.root as ConstraintLayout
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // ConstraintSet.applyTo() restores visibility from the time of clone (all panels
        // were GONE at that point), so we must save and restore the live visibility state.
        val topVis = binding.topPanel.visibility
        val bottomVis = binding.bottomPanel.visibility

        val panelMargin = resources.getDimension(R.dimen.big_padding).toInt()

        // Read current window insets to account for system bars and display cutouts.
        // All panel insets are applied here (not via binding adapters) to keep ConstraintSet
        // as the sole owner of panel margins and avoid margin resets on orientation change.
        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val sysLeft = insets?.left ?: 0
        val sysBottom = insets?.bottom ?: 0

        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                // Panels occupy the left 40 % of the screen; offset by the left system bar / cutout.
                val panelWidth = (resources.displayMetrics.widthPixels * 0.4f).toInt()
                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    constrainWidth(id, panelWidth)
                    connect(
                        id,
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START,
                        sysLeft + panelMargin,
                    )
                    clear(id, ConstraintSet.END)
                }
            } else {
                for (id in intArrayOf(R.id.top_panel, R.id.bottom_panel)) {
                    // Restore MATCH_CONSTRAINT width explicitly so it is not left at the
                    // absolute pixel value that was set in the landscape branch.
                    constrainWidth(id, ConstraintSet.MATCH_CONSTRAINT)
                    connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, panelMargin)
                    connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, panelMargin)
                }
            }
            // Bottom panel always needs clearance for the bottom system bar / gesture indicator.
            connect(
                R.id.bottom_panel,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
                panelMargin + sysBottom,
            )
        }.applyTo(rootLayout)

        // Restore live visibility after ConstraintSet.applyTo() overwrote it.
        binding.topPanel.visibility = topVis
        binding.bottomPanel.visibility = bottomVis
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(connectionUpdateReceiver)
        unbindService(mServiceConnection)
        wifiService?.let {
            it.disconnect()
            wifiService = null
        }
    }

    private fun updateConnectionState(resourceId: Int) {
        runOnUiThread {
            supportActionBar?.subtitle = getString(resourceId)
        }
    }

    // ---- Formatting helpers (ported from the BLEClient2 example) --------------
    // The client app does not initialize the GEM SDK, so the values received from the
    // server are formatted with these plain-Android helpers instead of GemUtil.

    /** Formats the given time in seconds as a (value, unit) pair. */
    @SuppressLint("DefaultLocale")
    private fun getTimeText(timeInSeconds: Int): Pair<String, String> {
        return when {
            timeInSeconds >= 3600 -> {
                val nHour = timeInSeconds / 3600
                val nMin = (timeInSeconds / 60) - (nHour * 60)
                Pair(String.format("%d:%02d", nHour, nMin), "hr")
            }

            timeInSeconds >= 60 -> Pair(String.format("%d", timeInSeconds / 60), "min")
            timeInSeconds > 0 -> Pair("1", "min")
            else -> Pair("0", "min")
        }
    }

    /** Formats the given distance in meters as a (value, unit) pair. */
    @SuppressLint("DefaultLocale")
    private fun getDistText(meters: Int): Pair<String, String> {
        return when {
            // >20 km - 1 km accuracy
            meters >= 20000 -> Pair("${(meters + 500) / 1000}", "km")

            // 1 - 20 km - 0.1 km accuracy
            meters >= 1000 -> {
                val rounded = ((meters + 50) / 100) * 100
                Pair(String.format("%.1f", rounded.toFloat() / 1000), "km")
            }

            // 500 - 1,000 m - 50 m accuracy
            meters >= 500 -> {
                val rounded = ((meters + 25) / 50) * 50
                if (rounded == 1000) {
                    Pair(String.format("%.1f", rounded.toFloat() / 1000), "km")
                } else {
                    Pair("$rounded", "m")
                }
            }

            // 200 - 500 m - 25 m accuracy
            meters >= 200 -> Pair("${((meters + 12) / 25) * 25}", "m")

            // 100 - 200 m - 10 m accuracy
            meters >= 100 -> Pair("${((meters + 5) / 10) * 10}", "m")

            // 0 - 100 m - 5 m accuracy
            else -> Pair("${((meters + 2) / 5) * 5}", "m")
        }
    }

    /** Remaining travel time text for the bottom panel. */
    private fun getRtt(timeToDestinationInSeconds: Int): String =
        getTimeText(timeToDestinationInSeconds).let { pair -> pair.first + " " + pair.second }

    /** Remaining travel distance text for the bottom panel. */
    private fun getRtd(distanceToDestinationInMeters: Int): String =
        getDistText(distanceToDestinationInMeters).let { pair -> pair.first + " " + pair.second }

    /** Estimated time of arrival text for the bottom panel. */
    @SuppressLint("DefaultLocale")
    private fun getEta(timeToDestinationInSeconds: Int): String {
        val calendar = Calendar.getInstance(Locale.getDefault())
        calendar.time = Date(System.currentTimeMillis() + timeToDestinationInSeconds.toLong() * 1000)

        return String.format(
            "%d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
        )
    }

    companion object {
        const val EXTRAS_SERVER_NAME = "SERVER_NAME"
        const val EXTRAS_SERVER_HOST = "SERVER_HOST"
        const val EXTRAS_SERVER_PORT = "SERVER_PORT"
    }
}
