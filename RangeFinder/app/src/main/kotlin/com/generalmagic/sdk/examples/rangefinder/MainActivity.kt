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

package com.generalmagic.sdk.examples.rangefinder

// -------------------------------------------------------------------------------------------------------------------------------

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.EBikeProfile
import com.generalmagic.sdk.routesandnavigation.EEBikeType
import com.generalmagic.sdk.routesandnavigation.ERouteTransportMode
import com.generalmagic.sdk.routesandnavigation.ERouteType
import com.generalmagic.sdk.routesandnavigation.ElectricBikeProfile
import com.generalmagic.sdk.routesandnavigation.RoutingService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity: AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    private lateinit var rootView: ConstraintLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var transportModeSpinner: Spinner
    private lateinit var rangeTypeSpinner: Spinner
    private lateinit var bikeWeightEditText: EditText
    private lateinit var bikeWeightTextView: TextView
    private lateinit var bikerWeightEditText: EditText
    private lateinit var bikerWeightTextView: TextView
    private lateinit var rangeValueEditText: EditText
    private lateinit var addButton: TextView
    private lateinit var currentRangesScrollContainer: HorizontalScrollView
    private lateinit var currentRangesContainer: LinearLayout
    private lateinit var rangeViewsContainer: ScrollView
    
    private var currentTransportMode: ERouteTransportMode? = null
    private var currentRangeType: ERouteType? = null
    
    private val currentSelectedRanges = ArrayList<Int>()
    
    private var transportTypesList = mutableListOf<String>()

    private val routingService = RoutingService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
            addButton.visibility = View.GONE
        },
        onCompleted = { routes, errorCode, _ ->
            progressBar.visibility = View.GONE
            addButton.visibility = View.VISIBLE

            when (errorCode)
            {
                GemError.NoError ->
                {
                    SdkCall.execute {
                        gemSurfaceView.mapView?.presentRoutes(routes, displayBubble = true)
                    }
                    renderSelectedRanges()
                }

                GemError.Cancel ->
                { // The routing action was cancelled.
                }

                else ->
                { // There was a problem at computing the routing operation.
                    currentSelectedRanges.removeAt(currentSelectedRanges.size - 1)
                    
                    Toast.makeText(
                        this@MainActivity, "Routing service error: ${GemError.getMessage(errorCode)}", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    )

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootView = findViewById(R.id.root_view)
        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)
        transportModeSpinner = findViewById(R.id.transport_mode_spinner)
        rangeTypeSpinner = findViewById(R.id.range_type_spinner)
        bikeWeightEditText = findViewById(R.id.bike_weight_edit_text)
        bikeWeightTextView = findViewById(R.id.bike_weight_text)
        bikerWeightEditText = findViewById(R.id.biker_weight_edit_text)
        bikerWeightTextView = findViewById(R.id.biker_weight_text)
        rangeValueEditText = findViewById(R.id.range_value_edit_text)
        addButton = findViewById(R.id.add_button)
        currentRangesScrollContainer = findViewById(R.id.current_ranges_scroll_container)
        currentRangesContainer = findViewById(R.id.current_ranges_buttons_container)
        rangeViewsContainer = findViewById(R.id.range_container)
        
        setConstraints(resources.configuration.orientation)
        
        SdkSettings.onMapDataReady = { isReady ->
            if (isReady)
            {
                // Defines an action that should be done when the world map is ready (Updated/ loaded).
                progressBar.visibility = View.GONE
                rangeViewsContainer.visibility = View.VISIBLE
                
                prepareViews()
            }
        }

        SdkSettings.onApiTokenRejected = {/*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one. 
             */
            Toast.makeText(this, "TOKEN REJECTED", Toast.LENGTH_LONG).show()
        }

        if (!Util.isInternetConnected(this))
        {
            Toast.makeText(this, "You must be connected to internet!", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onConfigurationChanged(newConfig: Configuration)
    {
        super.onConfigurationChanged(newConfig)
        
        setConstraints(newConfig.orientation)
        rangeViewsContainer.post { rangeViewsContainer.fullScroll(View.FOCUS_DOWN) }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

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

    private fun calculateRange(transportMode: ERouteTransportMode,
                               routeType: ERouteType,
                               ranges: ArrayList<Int>,
                               bikeProfile: EBikeProfile = EBikeProfile.Road,
                               electricBikeProfile: ElectricBikeProfile? = null)
    {
        SdkCall.execute {
            routingService.preferences.transportMode = transportMode
            routingService.preferences.routeType = routeType
            routingService.preferences.setRouteRanges(ranges, 100)

            if (transportMode == ERouteTransportMode.Bicycle)
            {
                routingService.preferences.setBikeProfile(bikeProfile, electricBikeProfile)
            }

            routingService.calculateRoute(arrayListOf(Landmark("London", 51.5073204, -0.1276475)))
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun setConstraints(orientation: Int)
    {
        when (orientation)
        {
            Configuration.ORIENTATION_PORTRAIT ->
            {
                if (currentRangeType == ERouteType.Economic)
                {
                    rangeViewsContainer.layoutParams.apply {
                        width = ConstraintLayout.LayoutParams.MATCH_PARENT
                        height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                    }
                    ConstraintSet().apply {
                        clone(rootView)
                        constrainPercentHeight(R.id.range_container, 0.35f)
                        applyTo(rootView)
                    }
                }
                else
                {
                    rangeViewsContainer.layoutParams.apply {
                        width = ConstraintLayout.LayoutParams.MATCH_PARENT
                        height = ConstraintLayout.LayoutParams.WRAP_CONTENT
                    }
                }

                gemSurfaceView.layoutParams.apply {
                    width = ConstraintLayout.LayoutParams.MATCH_PARENT
                    height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                }
                
                ConstraintSet().apply { 
                    clone(rootView)
                    connect(R.id.range_container, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(R.id.range_container, ConstraintSet.BOTTOM, R.id.gem_surface, ConstraintSet.TOP)
                    connect(R.id.range_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(R.id.range_container, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    
                    connect(R.id.gem_surface, ConstraintSet.TOP, R.id.range_container, ConstraintSet.BOTTOM)
                    connect(R.id.gem_surface, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                    connect(R.id.gem_surface, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(R.id.gem_surface, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                    constrainMinHeight(R.id.gem_surface, resources.displayMetrics.heightPixels / 2)
                    constrainPercentHeight(R.id.range_container, 0.4f)

                    applyTo(rootView)
                }
            }
            
            Configuration.ORIENTATION_LANDSCAPE ->
            {
                rangeViewsContainer.layoutParams.apply {
                    width = resources.displayMetrics.widthPixels / 2
                    height = ConstraintLayout.LayoutParams.MATCH_PARENT
                }
                rangeViewsContainer.forceLayout()

                gemSurfaceView.layoutParams.apply {
                    width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                    height = ConstraintLayout.LayoutParams.MATCH_PARENT
                }
                
                ConstraintSet().apply {
                    clone(rootView)
                    connect(R.id.range_container, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(R.id.range_container, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                    connect(R.id.range_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(R.id.range_container, ConstraintSet.END, R.id.gem_surface, ConstraintSet.START)

                    connect(R.id.gem_surface, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(R.id.gem_surface, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                    connect(R.id.gem_surface, ConstraintSet.START, R.id.range_container, ConstraintSet.END)
                    connect(R.id.gem_surface, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                    constrainPercentHeight(R.id.range_container, 1f)

                    applyTo(rootView)
                }
            }
            
            else -> return
        }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    private fun prepareViews()
    {
        transportTypesList = mutableListOf(getString(R.string.car), getString(R.string.lorry), getString(R.string.pedestrian), getString(R.string.bicycle))
        val adapter = ArrayAdapter(this, R.layout.spinner_item, R.id.spinner_text, transportTypesList)
        transportModeSpinner.adapter = adapter
        transportModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long)
            {
                onTransportModeSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        transportModeSpinner.setSelection(0)
        
        addButton.setOnClickListener { 
            if (rangeValueEditText.text.isNotEmpty())
            {
                currentSelectedRanges.add(rangeValueEditText.text.toString().toInt())
                SdkCall.execute { calculateRanges() }
                rangeValueEditText.setText("")
                hideKeyboard()
            }
            else
            {
                Toast.makeText(this, "Range value is empty!", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    private fun onTransportModeSelected(transportMode: Int)
    {
        var rangeTypesList = mutableListOf<String>()
        
        when (transportMode)
        {
            ERouteTransportMode.Car.value ->
            {
                rangeTypesList = mutableListOf(getString(R.string.fastest), getString(R.string.shortest))
                currentTransportMode = ERouteTransportMode.Car
            }
            
            ERouteTransportMode.Lorry.value ->
            {
                rangeTypesList = mutableListOf(getString(R.string.fastest), getString(R.string.shortest))
                currentTransportMode = ERouteTransportMode.Lorry
            }
            
            ERouteTransportMode.Pedestrian.value ->
            {
                rangeTypesList = mutableListOf(getString(R.string.fastest))
                currentTransportMode = ERouteTransportMode.Pedestrian
            }
            
            ERouteTransportMode.Bicycle.value ->
            {
                rangeTypesList = mutableListOf(getString(R.string.fastest), getString(R.string.shortest), getString(R.string.economic))
                currentTransportMode = ERouteTransportMode.Bicycle
            }
        }
        
        val adapter = ArrayAdapter(this, R.layout.spinner_item, R.id.spinner_text, rangeTypesList)
        rangeTypeSpinner.adapter = adapter
        rangeTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
        {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long)
            {
                onRangeTypeSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        rangeTypeSpinner.setSelection(0)

        if (currentSelectedRanges.isNotEmpty())
        {
            currentSelectedRanges.clear()
            renderSelectedRanges()
            SdkCall.execute {
                calculateRanges()
                gemSurfaceView.mapView?.hideRoutes() 
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    private fun onRangeTypeSelected(rangeType: Int)
    {
        clearEditTexts()
        currentRangeType = ERouteType.values()[rangeType]
        
        rangeValueEditText.hint = when (rangeType)
        {
            ERouteType.Fastest.value -> getString(R.string.seconds)
            ERouteType.Shortest.value -> getString(R.string.meters)
            ERouteType.Economic.value -> getString(R.string.watts_per_hour)
            else -> ""
        }
        
        if (rangeType == ERouteType.Economic.value)
        {
            bikeWeightEditText.visibility = View.VISIBLE
            bikeWeightTextView.visibility = View.VISIBLE
            bikerWeightEditText.visibility = View.VISIBLE
            bikerWeightTextView.visibility = View.VISIBLE

            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
            {
                rangeViewsContainer.layoutParams.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                ConstraintSet().apply {
                    clone(rootView)
                    constrainPercentHeight(R.id.range_container, 0.35f)
                    applyTo(rootView)
                }
            }
            
            rangeViewsContainer.post { rangeViewsContainer.fullScroll(View.FOCUS_DOWN) }
        }
        else
        {
            bikeWeightEditText.visibility = View.GONE
            bikeWeightTextView.visibility = View.GONE
            bikerWeightEditText.visibility = View.GONE
            bikerWeightTextView.visibility = View.GONE

            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
            {
                rangeViewsContainer.layoutParams.height = ConstraintLayout.LayoutParams.WRAP_CONTENT   
            }
        }
        
        if (currentSelectedRanges.isNotEmpty())
        {
            currentSelectedRanges.clear()
            renderSelectedRanges()
            SdkCall.execute {
                calculateRanges()    
                gemSurfaceView.mapView?.hideRoutes() 
            }
        }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    private fun clearEditTexts()
    {
        rangeValueEditText.setText("")
        bikeWeightEditText.setText("")
        bikerWeightEditText.setText("")
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private fun hideKeyboard()
    {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    private fun renderSelectedRanges()
    {
        if (currentSelectedRanges.size <= 0)
        {
            currentRangesScrollContainer.visibility = View.GONE
        }
        else
        {
            currentRangesScrollContainer.visibility = View.VISIBLE
            currentRangesContainer.removeAllViews()
            
            for (i in 0 until currentSelectedRanges.size)
            {
                val rangeContainer = layoutInflater.inflate(R.layout.button_text, currentRangesContainer, false) as ConstraintLayout
                val textView = rangeContainer.findViewById<TextView>(R.id.text_button)
                val clearButton = rangeContainer.findViewById<ImageView>(R.id.icon)
                
                textView.text = currentSelectedRanges[i].toString()
                
                clearButton.setOnClickListener { 
                     currentSelectedRanges.removeAt(i)
                    renderSelectedRanges()
                    SdkCall.execute {
                        calculateRanges()    
                        gemSurfaceView.mapView?.hideRoutes() 
                    }
                }
                
                currentRangesContainer.addView(rangeContainer)
            }

            rangeViewsContainer.post { rangeViewsContainer.fullScroll(View.FOCUS_DOWN) }
            currentRangesScrollContainer.post { currentRangesScrollContainer.fullScroll(View.FOCUS_RIGHT) }
        }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    private fun calculateRanges()
    {
        if (currentRangeType == ERouteType.Economic)
        {
            currentTransportMode?.let { transportMode ->
                currentRangeType?.let { rangeType ->
                    val bikeWeight = bikeWeightEditText.toString().toIntOrNull() ?: 0
                    val bikerWeight = bikerWeightEditText.toString().toIntOrNull() ?: 0
                    calculateRange (
                        transportMode,
                        rangeType,
                        currentSelectedRanges,
                        EBikeProfile.Road, ElectricBikeProfile (
                            EEBikeType.Pedelec,
                            bikeWeight.toFloat(),
                            bikerWeight.toFloat(),
                            2f,
                            4f
                        )
                    )
                }
            }
        }
        else
        {
            currentTransportMode?.let { transportMode ->
                currentRangeType?.let { rangeType ->
                    calculateRange(transportMode, rangeType, currentSelectedRanges)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
