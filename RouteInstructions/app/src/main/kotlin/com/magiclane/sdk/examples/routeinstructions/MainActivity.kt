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

package com.magiclane.sdk.examples.routeinstructions

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RouteInstruction
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sdk.util.Util.postOnMain
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------
    
    private lateinit var listView: RecyclerView
    private lateinit var progressBar: ProgressBar

    private val routingService = RoutingService(
        onStarted = {
            progressBar.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ routes, errorCode, _ ->
            progressBar.visibility = View.GONE

            when (errorCode)
            {
                GemError.NoError ->
                {
                    if (routes.size == 0) return@onCompleted

                    // Get the main route from the ones that were found.
                    displayRouteInstructions(routes[0])
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

        progressBar = findViewById(R.id.progressBar)
        listView = findViewById<RecyclerView?>(R.id.list_view).also { 
            it.layoutManager = LinearLayoutManager(this)
            it.addItemDecoration(DividerItemDecoration(this, (it.layoutManager as LinearLayoutManager).orientation))
        }

        /// MAGIC LANE
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            startCalculateRoute()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        // This step of initialization is mandatory if you want to use the SDK without a map.
        if (!GemSdk.initSdkWithDefaults(this))
        {
            finish()
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

    private fun startCalculateRoute() = SdkCall.execute {
        val wayPoints = arrayListOf(
            Landmark("Frankfurt am Main", 50.11428, 8.68133),
            Landmark("Karlsruhe", 49.0069, 8.4037),
            Landmark("Munich", 48.1351, 11.5820)
        )
        routingService.calculateRoute(wayPoints)
    }

    // ---------------------------------------------------------------------------------------------

    private fun displayRouteInstructions(route: Route)
    {
        // Get the instructions from the route.
        val instructions = SdkCall.execute { route.instructions } ?: arrayListOf()
        val imageSize = resources.getDimension(R.dimen.turn_image_size).toInt()
        listView.adapter = CustomAdapter(instructions, imageSize, isDarkThemeOn())
    }

    // ---------------------------------------------------------------------------------------------

    private fun isDarkThemeOn(): Boolean
    {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
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
}

// -------------------------------------------------------------------------------------------------

/**
 * This custom adapter is made to facilitate the displaying of the data from the model
 * and to decide how it is displayed.
 */
class CustomAdapter(
    private val dataSet: ArrayList<RouteInstruction>,
    private val imageSize: Int,
    private val isDarkThemeOn: Boolean
) : RecyclerView.Adapter<CustomAdapter.ViewHolder>()
{
    // ---------------------------------------------------------------------------------------------

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
    {
        val turnImage: ImageView = view.findViewById(R.id.turn_image)
        val text: TextView = view.findViewById(R.id.text)
        val status: TextView = view.findViewById(R.id.status_text)
        val description: TextView = view.findViewById(R.id.status_description)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder
    {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.list_item, viewGroup, false)

        return ViewHolder(view)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int)
    {
        val instruction = dataSet[position]
        var text: String
        var status: String
        var description: String
        var turnImage: Bitmap?

        SdkCall.execute {
            if (instruction.hasTurnInfo())
            {
                val aInner = if (isDarkThemeOn) Rgba(255, 255, 255, 255) else Rgba(0, 0, 0, 255)
                val aOuter = if (isDarkThemeOn) Rgba(0, 0, 0, 255) else Rgba(255, 255, 255, 255)
                val iInner = Rgba(128, 128, 128, 255)
                val iOuter = Rgba(128, 128, 128, 255)
                
                turnImage = GemUtilImages.asBitmap(instruction.turnDetails?.abstractGeometryImage, imageSize, imageSize, aInner, aOuter, iInner, iOuter)
                
                text = instruction.turnInstruction ?: ""
                if (text.isNotEmpty() && text.last() == '.')
                {
                    text.removeSuffix(".")
                }

                val distance = instruction.traveledTimeDistance?.totalDistance?.toDouble() ?: 0.0

                val distText = GemUtil.getDistText(distance.toInt(), SdkSettings.unitSystem)
                status = distText.first
                description = distText.second
                if (status == "0.00")
                {
                    status = "0"
                }

                postOnMain {
                    viewHolder.turnImage.setImageBitmap(turnImage)
                    viewHolder.text.text = text
                    viewHolder.status.text = status
                    viewHolder.description.text = description
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun getItemCount() = dataSet.size

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
