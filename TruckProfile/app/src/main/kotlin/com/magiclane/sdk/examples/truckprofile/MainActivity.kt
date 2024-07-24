// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
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
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
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
import java.util.Locale
import kotlin.system.exitProcess


// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------

    enum class ETruckProfileSettings
    {
        Weight,
        Height,
        Length,
        Width,
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
    enum class ETruckProfileUnitConverters(val unit: Float)
    {
        Weight(1000f),
        Height(100f),
        Length(100f),
        Width(100f),
        AxleWeight(1000f),
        MaxSpeed(0.27778f)
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
        var minDoubleValue: Float = 0f,
        var currentDoubleValue: Float = 0f,
        var maxDoubleValue: Float = 0f,
        var unit: String = ""
    )

    // ---------------------------------------------------------------------------------------------

    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var settingsButtons: FloatingActionButton
    private lateinit var preferencesTruckProfile: TruckProfile

    private var routesList = ArrayList<Route>()

    private val adapter = TruckProfileSettingsAdapter(getInitialDataSet())

    private var waypoints = arrayListOf<Landmark>()

    private val routingService = RoutingService(
        onStarted = {
            progressBar.isVisible = true
        },

        onCompleted = { routes, errorCode, _ ->
            progressBar.isVisible = false
            when (errorCode)
            {
                GemError.NoError ->
                {
                    routesList = routes
                    adapter.notifyItemRangeChanged(0, routesList.size)
                    SdkCall.execute {
                        gemSurfaceView.mapView?.presentRoutes(
                            routes = routes,
                            displayBubble = true
                        )
                    }

                    settingsButtons.isVisible = true
                    EspressoIdlingResource.decrement()
                }

                GemError.Cancel ->
                {
                    // The routing action was cancelled.
                    showDialog("The routing action was cancelled.")
                    EspressoIdlingResource.decrement()
                }

                else ->
                {
                    // There was a problem at computing the routing operation.
                    showDialog("Routing service error: ${GemError.getMessage(errorCode)}")
                    EspressoIdlingResource.decrement()
                }
            }
        }
    )

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        EspressoIdlingResource.increment()
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
                preferencesTruckProfile = TruckProfile(
                    (3 * ETruckProfileUnitConverters.Weight.unit).toInt(),
                    (1.8 * ETruckProfileUnitConverters.Height.unit).toInt(),
                    (5 * ETruckProfileUnitConverters.Length.unit).toInt(),
                    (2 * ETruckProfileUnitConverters.Width.unit).toInt(),
                    (1.5 * ETruckProfileUnitConverters.AxleWeight.unit).toInt(),
                    (60 * ETruckProfileUnitConverters.MaxSpeed.unit).toDouble()
                )
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


        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true)
        {
            override fun handleOnBackPressed()
            {
                finish()
                exitProcess(0)
            }
        })
    }
    // ---------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------

    private fun onSettingsButtonClicked()
    {
        val builder = AlertDialog.Builder(this)

        val convertView = layoutInflater.inflate(R.layout.truck_profile_settings_view, null)
        val listView =
            convertView.findViewById<RecyclerView>(R.id.truck_profile_settings_list).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                addItemDecoration(
                    DividerItemDecoration(
                        applicationContext,
                        (layoutManager as LinearLayoutManager).orientation
                    )
                )
            }

        listView.adapter = adapter
        adapter.notifyItemRangeChanged(0, ETruckProfileSettings.entries.size)

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
        EspressoIdlingResource.increment()
        val dataSet = adapter.dataSet

        // convert m to cm
        val width =
            (dataSet[ETruckProfileSettings.Width.ordinal].currentDoubleValue * ETruckProfileUnitConverters.Width.unit).toInt()
        val height =
            (dataSet[ETruckProfileSettings.Height.ordinal].currentDoubleValue * ETruckProfileUnitConverters.Height.unit).toInt()
        val length =
            (dataSet[ETruckProfileSettings.Length.ordinal].currentDoubleValue * ETruckProfileUnitConverters.Length.unit).toInt()
        // convert t to kg
        val weight =
            (dataSet[ETruckProfileSettings.Weight.ordinal].currentDoubleValue * ETruckProfileUnitConverters.Weight.unit).toInt()
        val axleWeight =
            (dataSet[ETruckProfileSettings.AxleWeight.ordinal].currentDoubleValue * ETruckProfileUnitConverters.AxleWeight.unit).toInt()
        // convert km/h to m/s
        val maxSpeed =
            dataSet[ETruckProfileSettings.MaxSpeed.ordinal].currentIntValue * ETruckProfileUnitConverters.MaxSpeed.unit.toDouble()

        SdkCall.execute {
            routingService.apply {
                preferences.alternativesSchema = ERouteAlternativesSchema.Never
                preferences.transportMode = ERouteTransportMode.Lorry
                preferencesTruckProfile = TruckProfile(
                    massKg = weight,
                    heightCm = height,
                    lengthCm = length,
                    widthCm = width,
                    axleLoadKg = axleWeight,
                    maxSpeedMs = maxSpeed
                )
                preferences.truckProfile = preferencesTruckProfile
                calculateRoute(waypoints)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun getInitialDataSet(): List<TruckProfileSettingsModel>
    {
        return mutableListOf<TruckProfileSettingsModel>().also {
            it.add(
                TruckProfileSettingsModel(
                    "Weight",
                    ESeekBarValuesType.DoubleType,
                    "3 t",
                    "3.0 t",
                    "50 t",
                    0,
                    0,
                    0,
                    3.0f,
                    3.0f,
                    50.0f,
                    "t"
                )
            )
            it.add(
                TruckProfileSettingsModel(
                    "Height",
                    ESeekBarValuesType.DoubleType,
                    "1.8 m",
                    "1.8 m",
                    "5 m",
                    0,
                    0,
                    0,
                    1.8f,
                    1.8f,
                    5.0f,
                    "m"
                )
            )
            it.add(
                TruckProfileSettingsModel(
                    "Length",
                    ESeekBarValuesType.DoubleType,
                    "5 m",
                    "5.0 m",
                    "20 m",
                    0,
                    0,
                    0,
                    5.0f,
                    5.0f,
                    20.0f,
                    "m"
                )
            )
            it.add(
                TruckProfileSettingsModel(
                    "Width",
                    ESeekBarValuesType.DoubleType,
                    "2 m",
                    "2.0 m",
                    "4 m",
                    0,
                    0,
                    0,
                    2f,
                    2f,
                    4f,
                    "m"
                )
            )
            it.add(
                TruckProfileSettingsModel(
                    "Axle Weight",
                    ESeekBarValuesType.DoubleType,
                    "1.5 t",
                    "1.5 t",
                    "10 t",
                    0,
                    0,
                    0,
                    1.5f,
                    1.5f,
                    10.0f,
                    "t"
                )
            )
            it.add(
                TruckProfileSettingsModel(
                    "Max Speed",
                    ESeekBarValuesType.IntType,
                    "60 km/h",
                    "130 km/h",
                    "250 km/h",
                    60,
                    130,
                    250,
                    0.0f,
                    0.0f,
                    0.0f,
                    "km/h"
                )
            )
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
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            TruckProfileSettingsItemViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.settings_list_item_seekbar, parent, false)
            )

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int)
        {
            (holder as TruckProfileSettingsItemViewHolder).bind(position)
        }

        override fun getItemViewType(position: Int): Int = dataSet[position].type.ordinal

        override fun getItemCount(): Int = dataSet.size

        inner class TruckProfileSettingsItemViewHolder(view: View) :
            RecyclerView.ViewHolder(view)
        {
            private val text: TextView = view.findViewById(R.id.text)
            private val minValueText: TextView = view.findViewById(R.id.min_value_text)
            private val currentValueText: TextView = view.findViewById(R.id.current_value_text)
            private val maxValueText: TextView = view.findViewById(R.id.max_value_text)
            private val seekBar: Slider = view.findViewById(R.id.seek_bar)

            fun bind(position: Int)
            {
                val isDoubleItem =
                    getItemViewType(position) == ESeekBarValuesType.DoubleType.ordinal
                val item = dataSet[position]
                text.text = item.title
                if (isDoubleItem)
                {
                    minValueText.text = item.minValueText
                    maxValueText.text = item.maxValueText

                    seekBar.apply {
                        valueTo = item.maxDoubleValue
                        valueFrom = item.minDoubleValue
                        addOnChangeListener { _, value, _ ->
                            //if (!fromUser) return@addOnChangeListener

                            item.currentDoubleValue = value
                            item.currentValueText = String.format(Locale.getDefault(), "%.1f %s", value, item.unit)

                            currentValueText.text = item.currentValueText
                        }
                    }

                } else
                {
                    minValueText.text = item.minValueText
                    maxValueText.text = item.maxValueText

                    seekBar.apply {
                        valueTo = item.maxIntValue.toFloat()
                        valueFrom = item.minIntValue.toFloat()
                        stepSize = 1f
                        addOnChangeListener { _, value, _ ->
                            /*           if (!fromUser)
                                           return@addOnChangeListener*/
                            item.currentIntValue = value.toInt()
                            item.currentValueText = String.format(Locale.getDefault(), "%d %s", value.toInt(), item.unit)
                            currentValueText.text = item.currentValueText
                        }
                    }
                }
                val setting = ETruckProfileSettings.entries[position]
                SdkCall.execute {
                    val actualVal = when (setting)
                    {
                        ETruckProfileSettings.Weight -> preferencesTruckProfile.mass / ETruckProfileUnitConverters.Weight.unit
                        ETruckProfileSettings.Height -> preferencesTruckProfile.height / ETruckProfileUnitConverters.Height.unit
                        ETruckProfileSettings.Length -> preferencesTruckProfile.length / ETruckProfileUnitConverters.Length.unit
                        ETruckProfileSettings.Width -> preferencesTruckProfile.width / ETruckProfileUnitConverters.Width.unit
                        ETruckProfileSettings.AxleWeight -> preferencesTruckProfile.axleLoad / ETruckProfileUnitConverters.AxleWeight.unit
                        ETruckProfileSettings.MaxSpeed -> (preferencesTruckProfile.maxSpeed / ETruckProfileUnitConverters.MaxSpeed.unit).toFloat()
                    }
                    seekBar.value = actualVal
                    if (isDoubleItem)
                        item.currentDoubleValue = actualVal
                    else
                        item.currentIntValue = actualVal.toInt()

                    val valueText = if (isDoubleItem)
                        String.format(Locale.getDefault(), "%.1f %s", actualVal, item.unit)
                    else
                        String.format(Locale.getDefault(), "%d %s", actualVal.toInt(), item.unit)

                    item.currentValueText = valueText
                    currentValueText.text = valueText
                }
                seekBar.contentDescription = item.title
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
//region --------------------------------------------------FOR TESTING------------------------------
// -------------------------------------------------------------------------------------------------
object EspressoIdlingResource
{
    val espressoIdlingResource = CountingIdlingResource("TruckProfileIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion  ----------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------------
