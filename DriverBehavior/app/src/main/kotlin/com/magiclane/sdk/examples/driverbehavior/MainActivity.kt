// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.driverbehavior

// -------------------------------------------------------------------------------------------------------------------------------

import android.Manifest
import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.core.*
import com.magiclane.sdk.driverbehaviour.DriverBehaviour
import com.magiclane.sdk.driverbehaviour.DrivingScores
import com.magiclane.sdk.sensordatasource.DataSource
import com.magiclane.sdk.sensordatasource.DataSourceFactory
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.renderer.XAxisRenderer
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.utils.Transformer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{

    // ---------------------------------------------------------------------------------------------------------------------------

    private var dataSource: DataSource? = null
    private var driverBehaviour: DriverBehaviour? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var chartScrollView: ScrollView
    private lateinit var instantChart: BarChart
    private lateinit var ongoingChart: BarChart
    private lateinit var lastChart: BarChart
    private lateinit var combinedChart: BarChart
    private lateinit var chartLabels: List<String>
    private var timer: Timer? = null

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)

        chartScrollView = findViewById(R.id.chart_scroll_view)

        instantChart = findViewById(R.id.instant_chart)
        ongoingChart = findViewById(R.id.ongoing_chart)
        lastChart = findViewById(R.id.last_chart)
        combinedChart = findViewById(R.id.combined_chart)

        setChartCustomization(instantChart)
        setChartCustomization(ongoingChart)
        setChartCustomization(lastChart)
        setChartCustomization(combinedChart)

        chartLabels = resources.getStringArray(R.array.chart_labels).toList()

        SdkSettings.onMapDataReady = { isReady ->
            if (isReady)
            {
                SdkCall.execute {
                    dataSource = DataSourceFactory.produceLive()
                    dataSource?.let { driverBehaviour = DriverBehaviour.produce(it, false) }
                    driverBehaviour?.startAnalysis()
                }

                timer = fixedRateTimer("timer", false, 0L, 1000) {
                    displayDriverBehaviorInfo()
                }

                progressBar.visibility = View.GONE
                chartScrollView.visibility = View.VISIBLE
            }
        }

        SdkSettings.onApiTokenRejected = {/*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        requestPermissions(this)

        if (!GemSdk.initSdkWithDefaults(this))
        {
            // The SDK initialization was not completed.
            finish()
        }

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        timer?.cancel()

        SdkCall.execute {
            driverBehaviour?.stopAnalysis()
            driverBehaviour?.release()
            dataSource?.release()
        }

        // Deinitialize the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onBackPressed()
    {
        finish()
        exitProcess(0)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults)
        {
            if (item != PackageManager.PERMISSION_GRANTED)
            {
                finish()
                exitProcess(0)
            }
        }

        SdkCall.execute { // Notice permission status had changed
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun requestPermissions(activity: Activity): Boolean
    {
        val permissions = arrayListOf(
            Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        )

        return PermissionsHelper.requestPermissions(REQUEST_PERMISSIONS, activity, permissions.toTypedArray())
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    @SuppressLint("InflateParams")
    private fun showDialog(text: String)
    {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_layout, null).apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.error)
            findViewById<TextView>(R.id.message).text = text
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

    private fun displayDriverBehaviorInfo()
    {
        var instantDrivingScores: DrivingScores? = null
        var ongoingDrivingScores: DrivingScores? = null
        var lastDrivingScores: DrivingScores? = null
        var combinedDrivingScore: DrivingScores? = null

        SdkCall.execute {
            instantDrivingScores = driverBehaviour?.instantaneousScores
            ongoingDrivingScores = driverBehaviour?.ongoingAnalysis?.drivingScores
            lastDrivingScores = driverBehaviour?.lastAnalysis?.drivingScores
            combinedDrivingScore = driverBehaviour?.getCombinedAnalysis(Time().apply { longValue = 1640988000000 }, Time().apply {
                longValue = System.currentTimeMillis()
            })?.drivingScores
        }

        val instantBarDataSet = instantDrivingScores?.let { DriverBehaviorBarDataSet(createBarEntriesList(it), "") }
        val ongoingBarDataSet = ongoingDrivingScores?.let { DriverBehaviorBarDataSet(createBarEntriesList(it), "") }
        val lastBarDataSet = lastDrivingScores?.let { DriverBehaviorBarDataSet(createBarEntriesList(it), "") }
        val combinedBarDataSet = combinedDrivingScore?.let { DriverBehaviorBarDataSet(createBarEntriesList(it), "") }

        instantBarDataSet?.let {
            setBarDataSetCustomization(it)
            setDataToChart(instantChart, getBarData(it))
        }

        ongoingBarDataSet?.let {
            setBarDataSetCustomization(it)
            setDataToChart(ongoingChart, getBarData(it))
        }

        lastBarDataSet?.let {
            setBarDataSetCustomization(it)
            setDataToChart(lastChart, getBarData(it))
        }

        combinedBarDataSet?.let {
            setBarDataSetCustomization(it)
            setDataToChart(combinedChart, getBarData(it))
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun createBarEntriesList(drivingScores: DrivingScores): MutableList<BarEntry>
    {
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

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun getBarData(barDataSet: BarDataSet?): BarData = BarData(barDataSet).apply {
        isHighlightEnabled = false
        barWidth = 0.7f
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun setDataToChart(chart: BarChart, barData: BarData)
    {
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

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun setBarDataSetCustomization(barDataSet: BarDataSet?)
    {
        barDataSet?.apply {
            colors = mutableListOf()
            valueFormatter = ValuesFormatter()
            valueTextColor = if (isDarkThemeOn()) Color.WHITE else Color.BLACK
            valueTextSize = 11f
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun setChartCustomization(chart: BarChart)
    {
        chart.apply {
            isDragEnabled = true
            description = null
            setTouchEnabled(true)
            isDoubleTapToZoomEnabled = false
            extraBottomOffset = 4f
            setXAxisRenderer(CustomXAxisRenderer(viewPortHandler, xAxis, getTransformer(YAxis.AxisDependency.LEFT)))

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

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun Context.isDarkThemeOn(): Boolean
    {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private inner class DriverBehaviorBarDataSet(val entries: List<BarEntry>, label: String) : BarDataSet(entries, label)
    {

        // -----------------------------------------------------------------------------------------------------------------------

        override fun getColor(index: Int): Int
        {
            val percent = entries[index].y * 0.01f
            return ArgbEvaluator().evaluate(percent, Color.RED, Color.GREEN) as Int
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun getEntryIndex(e: BarEntry?): Int
        {
            return super.getEntryIndex(e)
        }

        // -----------------------------------------------------------------------------------------------------------------------
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private inner class ValuesFormatter : ValueFormatter()
    {
        override fun getFormattedValue(value: Float): String
        {
            return if (value > 0f)
            {
                value.toInt().toString()
            }
            else
            {
                ""
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private inner class XAxisFormatter : IndexAxisValueFormatter()
    {
        override fun getAxisLabel(value: Float, axis: AxisBase?): String
        {
            val index = value.toInt()
            return if (index >= 0 && index < chartLabels.size)
            {
                axis?.textColor = if (isDarkThemeOn()) Color.WHITE else Color.BLACK
                axis?.textSize = 10.5f
                chartLabels[index]
            }
            else
            {
                ""
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private inner class CustomXAxisRenderer(viewPortHandler: ViewPortHandler,
                                            xAxis: XAxis,
                                            transformer: Transformer): XAxisRenderer(viewPortHandler, xAxis, transformer)
    {
        override fun drawLabel(c: Canvas?, formattedLabel: String, x: Float, y: Float, anchor: MPPointF?, angleDegrees: Float)
        {
            val line = formattedLabel.split("\n")
            Utils.drawXAxisValue(c, line[0], x, y, mAxisLabelPaint, anchor, angleDegrees)
            for (i in 1 until line.size)
            {
                Utils.drawXAxisValue(c, line[i], x, y + mAxisLabelPaint.textSize * i, mAxisLabelPaint, anchor, angleDegrees)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    companion object
    {
        private const val REQUEST_PERMISSIONS = 110
    }

    // ---------------------------------------------------------------------------------------------------------------------------

}

// -------------------------------------------------------------------------------------------------------------------------------
