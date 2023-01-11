// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.truckprofile

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ERouteAlternativesSchema
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.routesandnavigation.TruckProfile
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------

    enum class ETruckProfileSettings
    {
        Width,
        Height,
        Length,
        Weight,
        AxleWeight,
        MaxSpeed
    }

    // ---------------------------------------------------------------------------------------------

    enum class ESeekBarValuesType
    {
        DoubleType,
        IntType
    }

    // ---------------------------------------------------------------------------------------------

    data class TruckProfileSettingsModel(
        var title: String = "",
        var type: ESeekBarValuesType,
        var minValueText: String = "",
        var currentValueText: String = "",
        var maxValueText: String = "",
        var minIntValue: Int = 0,
        var currentIntValue: Int = 0,
        var maxIntValue: Int = 0,
        var minDoubleValue: Double = 0.0,
        var currentDoubleValue: Double = 0.0,
        var maxDoubleValue: Double = 0.0,
        var unit: String = ""
    )

    // ---------------------------------------------------------------------------------------------

    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var settingsButtons: FloatingActionButton

    private var routesList = ArrayList<Route>()

    private val adapter = TruckProfileSettingsAdapter(getInitialDataSet())

    private var waypoints = arrayListOf<Landmark>()
    
    private val routingService = RoutingService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },
        
        onCompleted = { routes, errorCode, _ ->
            progressBar.visibility = View.GONE
            
            when (errorCode)
            {
                GemError.NoError ->
                {
                    routesList = routes

                    SdkCall.execute { gemSurfaceView.mapView?.presentRoutes(
                            routes = routes,
                            displayBubble = true
                        )
                    }
                    
                    settingsButtons.visibility = View.VISIBLE
                }
                
                GemError.Cancel ->
                {
                    // The routing action was cancelled.
                    showDialog("The routing action was cancelled.")
                }
                
                else ->
                {
                    // There was a problem at computing the routing operation.
                    showDialog("Routing service error: ${GemError.getMessage(errorCode)}")
                }
            }
        }
    )
    
    // ---------------------------------------------------------------------------------------------
    
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        gemSurfaceView = findViewById(R.id.gem_surface_view)
        progressBar = findViewById(R.id.progress_bar)
        settingsButtons = findViewById<FloatingActionButton?>(R.id.settings_button).also {
            it.setOnClickListener {
                onSettingsButtonClicked()
            }
        }
        
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            SdkCall.execute {
                waypoints = arrayListOf(
                    Landmark("London", 51.5073204, -0.1276475),
                    Landmark("Paris", 48.8566932, 2.3514616)
                )

                routingService.calculateRoute(waypoints)
            }
            
            gemSurfaceView.mapView?.onTouch = { xy ->
                SdkCall.execute {
                    // tell the map view where the touch event happened
                    gemSurfaceView.mapView?.cursorScreenPosition = xy

                    // get the visible routes at the touch event point 
                    val routes = gemSurfaceView.mapView?.cursorSelectionRoutes
                    // check if there is any route
                    if (!routes.isNullOrEmpty())
                    {
                        // set the touched route as the main route and center on it
                        val route = routes[0]
                        gemSurfaceView.mapView?.apply {
                            preferences?.routes?.mainRoute = route
                            centerOnRoutes(routesList)
                        }
                    }   
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            /*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one.
             */
            showDialog("TOKEN REJECTED")
        }

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------

    override fun onBackPressed()
    {
        finish()
        exitProcess(0)
    }
    
    // ---------------------------------------------------------------------------------------------

    private fun onSettingsButtonClicked()
    {
        val builder = AlertDialog.Builder(this)

        val convertView = layoutInflater.inflate(R.layout.truck_profile_settings_view, null)
        val listView = convertView.findViewById<RecyclerView>(R.id.truck_profile_settings_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(DividerItemDecoration(applicationContext, (layoutManager as LinearLayoutManager).orientation))
        }

        listView.adapter = adapter

        builder.setTitle(getString(R.string.app_name))
        builder.setView(convertView)
        builder.setNeutralButton(getString(R.string.save)) { dialog, _ ->
            onSaveButtonClicked()
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    // ---------------------------------------------------------------------------------------------

    private fun onSaveButtonClicked()
    {
        val dataSet = adapter.dataSet

        // convert m to cm
        val width = (dataSet[ETruckProfileSettings.Width.ordinal].currentDoubleValue * 100).toInt()
        val height = (dataSet[ETruckProfileSettings.Height.ordinal].currentDoubleValue * 100).toInt()
        val length = (dataSet[ETruckProfileSettings.Length.ordinal].currentDoubleValue * 100).toInt()
        // convert t to kg
        val weight = (dataSet[ETruckProfileSettings.Weight.ordinal].currentDoubleValue * 1000).toInt()
        val axleWeight = (dataSet[ETruckProfileSettings.AxleWeight.ordinal].currentDoubleValue * 1000).toInt()
        // convert km/h to m/s
        val maxSpeed = dataSet[ETruckProfileSettings.MaxSpeed.ordinal].currentIntValue * 0.27778

        SdkCall.execute {
            routingService.apply {
                preferences.alternativesSchema = ERouteAlternativesSchema.Never
                preferences.transportMode = ERouteTransportMode.Lorry
                preferences.truckProfile = TruckProfile(
                    massKg = weight,
                    heightCm = height,
                    lengthCm = length,
                    widthCm = width,
                    axleLoadKg = axleWeight,
                    maxSpeedMs = maxSpeed
                )
                calculateRoute(waypoints)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun getInitialDataSet(): List<TruckProfileSettingsModel>
    {
        return mutableListOf<TruckProfileSettingsModel>().also {
            it.add(TruckProfileSettingsModel(
                "Width",
                ESeekBarValuesType.DoubleType,
                "2 m",
                "2.0 m",
                "4 m",
                0,
                0,
                0,
                2.0,
                2.0,
                4.0,
                "m"
            ))
            it.add(TruckProfileSettingsModel(
                "Height",
                ESeekBarValuesType.DoubleType,
                "1.8 m",
                "1.8 m",
                "5 m",
                0,
                0,
                0,
                1.8,
                1.8,
                5.0,
                "m"
            ))
            it.add(TruckProfileSettingsModel(
                "Length",
                ESeekBarValuesType.DoubleType,
                "5 m",
                "5.0 m",
                "20 m",
                0,
                0,
                0,
                5.0,
                5.0,
                20.0,
                "m"
            ))
            it.add(TruckProfileSettingsModel(
                "Weight",
                ESeekBarValuesType.DoubleType,
                "3 t",
                "3.0 t",
                "50 t",
                0,
                0,
                0,
                3.0,
                3.0,
                50.0,
                "t"
            ))
            it.add(TruckProfileSettingsModel(
                "Axle Weight",
                ESeekBarValuesType.DoubleType,
                "1.5 t",
                "1.5 t",
                "10 t",
                0,
                0,
                0,
                1.5,
                1.5,
                10.0,
                "t"
            ))
            it.add(TruckProfileSettingsModel(
                "Max Speed",
                ESeekBarValuesType.IntType,
                "60 km/h",
                "130 km/h",
                "250 km/h",
                60,
                130,
                250,
                0.0,
                0.0,
                0.0,
                "km/h"
            ))
        }
    }

    // ---------------------------------------------------------------------------------------------

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
    
    // ---------------------------------------------------------------------------------------------

    inner class TruckProfileSettingsAdapter(val dataSet: List<TruckProfileSettingsModel>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>()
    {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder
        {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.settings_list_item_seekbar, parent, false)
            return when (viewType)
            {
                ESeekBarValuesType.DoubleType.ordinal -> TruckProfileSettingsDoubleItemViewHolder(view)
                ESeekBarValuesType.IntType.ordinal -> TruckProfileSettingsIntItemViewHolder(view)
                else -> TruckProfileSettingsDoubleItemViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int)
        {
            when (holder.itemViewType)
            {
                ESeekBarValuesType.IntType.ordinal -> (holder as TruckProfileSettingsIntItemViewHolder).bind(position)
                ESeekBarValuesType.DoubleType.ordinal -> (holder as TruckProfileSettingsDoubleItemViewHolder).bind(position)
                else -> (holder as TruckProfileSettingsDoubleItemViewHolder).bind(position)
            }
        }

        override fun getItemViewType(position: Int): Int
        {
            return dataSet[position].type.ordinal
        }

        override fun getItemCount(): Int = dataSet.size

        inner class TruckProfileSettingsDoubleItemViewHolder(view: View): RecyclerView.ViewHolder(view)
        {
            private val text: TextView = view.findViewById(R.id.text)
            private val minValueText: TextView = view.findViewById(R.id.min_value_text)
            private val currentValueText: TextView = view.findViewById(R.id.current_value_text)
            private val maxValueText: TextView = view.findViewById(R.id.max_value_text)
            private val seekBar: AppCompatSeekBar = view.findViewById(R.id.seek_bar)

            fun bind(position: Int)
            {
                val item = dataSet[position]

                text.text = item.title

                minValueText.text = item.minValueText
                currentValueText.text = item.currentValueText
                maxValueText.text = item.maxValueText

                val stepsNumber = 100 * (item.maxDoubleValue - item.minDoubleValue).toInt()
                seekBar.max = stepsNumber
                seekBar.progress = (((item.currentDoubleValue - item.minDoubleValue) / (item.maxDoubleValue - item.minDoubleValue)) * stepsNumber).toInt()

                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener
                {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean)
                    {
                        if (!fromUser) return

                        val progressValue = item.minDoubleValue + (progress.toFloat() * (1.0f / stepsNumber) * (item.maxDoubleValue - item.minDoubleValue))

                        item.currentDoubleValue = progressValue
                        item.currentValueText = String.format("%.1f %s", progressValue, item.unit)

                        currentValueText.text = item.currentValueText
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }
        }

        inner class TruckProfileSettingsIntItemViewHolder(view: View): RecyclerView.ViewHolder(view)
        {
            private val text: TextView = view.findViewById(R.id.text)
            private val minValueText: TextView = view.findViewById(R.id.min_value_text)
            private val currentValueText: TextView = view.findViewById(R.id.current_value_text)
            private val maxValueText: TextView = view.findViewById(R.id.max_value_text)
            private val seekBar: AppCompatSeekBar = view.findViewById(R.id.seek_bar)

            fun bind(position: Int)
            {
                val item = dataSet[position]

                text.text = item.title

                minValueText.text = item.minValueText
                currentValueText.text = item.currentValueText
                maxValueText.text = item.maxValueText

                val stepsNumber = item.maxIntValue - item.minIntValue
                seekBar.max = stepsNumber
                seekBar.progress = ((item.currentIntValue.toFloat() - item.minIntValue.toFloat()) / (item.maxIntValue.toFloat() - item.minIntValue.toFloat()) * stepsNumber).toInt()

                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener
                {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean)
                    {
                        if (!fromUser) return

                        val progressValue = (item.minIntValue + (progress.toFloat() * (1.0f / stepsNumber) * (item.maxIntValue - item.minIntValue))).toInt()

                        item.currentIntValue = progressValue
                        item.currentValueText = String.format("%d %s", progressValue, item.unit)

                        currentValueText.text = item.currentValueText
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
