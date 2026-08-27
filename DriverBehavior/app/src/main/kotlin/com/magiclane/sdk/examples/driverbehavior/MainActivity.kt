/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.driverbehavior

import android.Manifest
import android.animation.ArgbEvaluator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Canvas
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.renderer.XAxisRenderer
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.utils.Transformer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.driverbehaviour.DriverBehaviour
import com.magiclane.sdk.driverbehaviour.DrivingScores
import com.magiclane.sdk.examples.driverbehavior.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.driverbehavior.databinding.DialogLayoutBinding
import com.magiclane.sdk.sensordatasource.DataSource
import com.magiclane.sdk.sensordatasource.DataSourceFactory
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.util.Timer
import kotlin.concurrent.fixedRateTimer
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private var dataSource: DataSource? = null
    private var driverBehaviour: DriverBehaviour? = null
    private lateinit var chartLabels: List<String>
    private var timer: Timer? = null
    private var shouldCheckLocationPermissionOnResume = false

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply chart appearance before any data arrives.
        binding.apply {
            setChartCustomization(instantChart)
            setChartCustomization(ongoingChart)
            setChartCustomization(lastChart)
            setChartCustomization(combinedChart)
        }

        chartLabels = resources.getStringArray(R.array.chart_labels).toList()

        registerSdkListeners()

        val errorCode = GemSdk.initSdkWithDefaults(this)
        if (errorCode != GemError.NoError) {
            // Fatal: SDK could not start. Show error then terminate.
            showDialog(
                getString(
                    R.string.dialog_sdk_initialization_error,
                    SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                ),
            ) {
                finish()
                exitProcess(0)
            }
        }

        if (checkLocationStatus()) {
            requestPermissions()
        }

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldCheckLocationPermissionOnResume) {
            shouldCheckLocationPermissionOnResume = false
            if (isLocationEnabled()) {
                requestPermissions()
            } else {
                showDialog(getString(R.string.location_services_required)) { finish() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        timer?.cancel()
        clearSdkListeners()

        SdkCall.execute {
            driverBehaviour?.stopAnalysis()
            driverBehaviour?.release()
            dataSource?.release()
        }

        GemSdk.release()
        exitProcess(0)
    }

    // Registers all SDK callbacks. Always paired with clearSdkListeners() in onDestroy.
    private fun registerSdkListeners() {
        // Wait for map data to be ready before starting driver behaviour analysis.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkCall.execute {
                    dataSource = DataSourceFactory.produceLive()
                    dataSource?.let { driverBehaviour = DriverBehaviour.produce(it, false) }
                    driverBehaviour?.startAnalysis()
                }

                // Refresh chart data every second from a background timer thread.
                timer = fixedRateTimer("timer", false, 0L, 1000) {
                    displayDriverBehaviorInfo()
                }

                // Show the charts and hide the loading spinner on the main thread.
                runOnAliveUi {
                    binding.progressBar.visibility = View.GONE
                    binding.chartScrollView.visibility = View.VISIBLE
                }

                // One-shot listener — clear after the first UpToDate signal.
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // Clears SDK callbacks to prevent them reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                showDialog(getString(R.string.location_permission_required)) { finish() }
                return
            }
        }

        SdkCall.execute {
            // Notify the SDK that permission status has changed.
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)
        }
    }

    private fun requestPermissions(): Boolean {
        val permissions = arrayListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        return PermissionsHelper.requestPermissions(REQUEST_PERMISSIONS, this, permissions.toTypedArray())
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }

    private fun checkLocationStatus(): Boolean {
        if (!isLocationEnabled()) {
            showLocationDialog(
                message = getString(R.string.location_disabled),
                settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            )
            return false
        }
        return true
    }

    private fun showLocationDialog(message: String, settingsIntent: Intent) {
        if (!isActivityAlive()) return
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.location_status)
            this.message.text = message
            button.text = getString(R.string.open_settings)
            button.setOnClickListener {
                dialog.dismiss()
                startActivity(settingsIntent)
                shouldCheckLocationPermissionOnResume = true
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

    /** Shows a non-dismissable bottom-sheet error dialog. */
    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        if (!isActivityAlive()) return
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

    // Posts a block to the main thread only if the activity is still alive.
    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    // Reads the latest driving scores from the SDK and updates all four charts.
    // Called from the background timer thread every second.
    private fun displayDriverBehaviorInfo() {
        var instantDrivingScores: DrivingScores? = null
        var ongoingDrivingScores: DrivingScores? = null
        var lastDrivingScores: DrivingScores? = null
        var combinedDrivingScore: DrivingScores? = null

        SdkCall.execute {
            instantDrivingScores = driverBehaviour?.instantaneousScores
            ongoingDrivingScores = driverBehaviour?.ongoingAnalysis?.drivingScores
            lastDrivingScores = driverBehaviour?.lastAnalysis?.drivingScores
            combinedDrivingScore = driverBehaviour?.getCombinedAnalysis(
                Time().apply { longValue = 1640988000000 },
                Time().apply { longValue = System.currentTimeMillis() },
            )?.drivingScores
        }

        val instantBarDataSet = instantDrivingScores?.let { DriverBehaviorBarDataSet(createBarEntriesList(it), "") }
        val ongoingBarDataSet = ongoingDrivingScores?.let { DriverBehaviorBarDataSet(createBarEntriesList(it), "") }
        val lastBarDataSet = lastDrivingScores?.let { DriverBehaviorBarDataSet(createBarEntriesList(it), "") }
        val combinedBarDataSet = combinedDrivingScore?.let { DriverBehaviorBarDataSet(createBarEntriesList(it), "") }

        // Chart mutations must happen on the main thread.
        runOnAliveUi {
            instantBarDataSet?.let {
                setBarDataSetCustomization(it)
                setDataToChart(binding.instantChart, getBarData(it))
            }
            ongoingBarDataSet?.let {
                setBarDataSetCustomization(it)
                setDataToChart(binding.ongoingChart, getBarData(it))
            }
            lastBarDataSet?.let {
                setBarDataSetCustomization(it)
                setDataToChart(binding.lastChart, getBarData(it))
            }
            combinedBarDataSet?.let {
                setBarDataSetCustomization(it)
                setDataToChart(binding.combinedChart, getBarData(it))
            }
        }
    }

    private fun createBarEntriesList(drivingScores: DrivingScores): MutableList<BarEntry> {
        val barEntries = mutableListOf<BarEntry>()

        SdkCall.execute {
            barEntries.apply {
                add(BarEntry(0f, drivingScores.speedAverageRiskScore.toFloat()))
                add(BarEntry(1f, drivingScores.speedVariableRiskScore.toFloat()))
                add(BarEntry(2f, drivingScores.harshAccelerationScore.toFloat()))
                add(BarEntry(3f, drivingScores.harshBrakingScore.toFloat()))
                add(BarEntry(4f, drivingScores.swervingScore.toFloat()))
                add(BarEntry(5f, drivingScores.corneringScore.toFloat()))
                add(BarEntry(6f, drivingScores.tailgatingScore.toFloat()))
                add(BarEntry(7f, drivingScores.ignoredStopSignsScore.toFloat()))
                add(BarEntry(8f, drivingScores.fatigueScore.toFloat()))
                add(BarEntry(9f, drivingScores.aggregateScore.toFloat()))
            }
        }

        return barEntries
    }

    private fun getBarData(barDataSet: BarDataSet?): BarData = BarData(barDataSet).apply {
        isHighlightEnabled = false
        barWidth = 0.7f
    }

    private fun setDataToChart(chart: BarChart, barData: BarData) {
        chart.apply {
            data = barData
            xAxis.apply {
                valueFormatter = XAxisFormatter()
                labelCount = chartLabels.size
            }
            axisLeft.textColor = if (isDarkThemeOn()) Color.WHITE else Color.BLACK
            notifyDataSetChanged()
            invalidate()
        }
    }

    private fun setBarDataSetCustomization(barDataSet: BarDataSet?) {
        barDataSet?.apply {
            colors = mutableListOf()
            valueFormatter = ValuesFormatter()
            valueTextColor = if (isDarkThemeOn()) Color.WHITE else Color.BLACK
            valueTextSize = 11f
        }
    }

    private fun setChartCustomization(chart: BarChart) {
        chart.apply {
            isDragEnabled = true
            description = null
            setTouchEnabled(true)
            isDoubleTapToZoomEnabled = false
            extraBottomOffset = 4f
            setXAxisRenderer(
                CustomXAxisRenderer(
                    viewPortHandler,
                    xAxis,
                    getTransformer(YAxis.AxisDependency.LEFT),
                ),
            )

            xAxis.apply {
                labelRotationAngle = -45f
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                isGranularityEnabled = true
                setDrawGridLines(false)
                axisMinimum = -0.5f
                axisMaximum = 9.5f
            }

            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 110f
                setDrawGridLines(false)
                textColor = if (context.isDarkThemeOn()) Color.WHITE else Color.BLACK
            }

            axisRight.isEnabled = false
        }
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES
    }

    // Color-codes each bar from red (0) to green (100) based on the score percentage.
    private class DriverBehaviorBarDataSet(barEntries: List<BarEntry>, label: String) : BarDataSet(barEntries, label) {

        override fun getColor(index: Int): Int {
            val percent = entries[index].y * 0.01f
            return ArgbEvaluator().evaluate(percent, Color.RED, Color.GREEN) as Int
        }

        override fun getEntryIndex(e: BarEntry?): Int = super.getEntryIndex(e)
    }

    private class ValuesFormatter : IValueFormatter {
        override fun getFormattedValue(
            value: Float,
            entry: Entry?,
            dataSetIndex: Int,
            viewPortHandler: ViewPortHandler?,
        ): String = value.toInt().toString()
    }

    private inner class XAxisFormatter : IndexAxisValueFormatter() {
        override fun getFormattedValue(value: Float, axis: AxisBase?): String {
            val index = value.toInt()
            return if (index >= 0 && index < chartLabels.size) {
                axis?.textColor = if (isDarkThemeOn()) Color.WHITE else Color.BLACK
                axis?.textSize = 10.5f
                chartLabels[index]
            } else {
                ""
            }
        }
    }

    private class CustomXAxisRenderer(
        viewPortHandler: ViewPortHandler,
        xAxis: XAxis,
        transformer: Transformer,
    ) : XAxisRenderer(viewPortHandler, xAxis, transformer) {
        override fun drawLabel(
            c: Canvas?,
            formattedLabel: String,
            x: Float,
            y: Float,
            anchor: MPPointF?,
            angleDegrees: Float,
        ) {
            val line = formattedLabel.split("\n")
            Utils.drawXAxisValue(c, line[0], x, y, mAxisLabelPaint, anchor, angleDegrees)
            for (i in 1 until line.size) {
                Utils.drawXAxisValue(
                    c,
                    line[i],
                    x,
                    y + mAxisLabelPaint.textSize * i,
                    mAxisLabelPaint,
                    anchor,
                    angleDegrees,
                )
            }
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }
}
