/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RouteInstruction
import com.generalmagic.sdk.routesandnavigation.RoutingService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkUtil
import com.generalmagic.sdk.util.Util.postOnMain
import kotlin.system.exitProcess

////////////////////////////////////////////////////////////////////////////////////////////////

class MainActivity : AppCompatActivity() {
    private var listView: RecyclerView? = null
    private var progressBar: ProgressBar? = null

    private val routingService = RoutingService(
        onStarted = {
            progressBar?.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ routes, reason, _ ->
            progressBar?.visibility = View.GONE

            when (reason) {
                SdkError.NoError -> {
                    if (routes.size == 0) return@onCompleted

                    // Get the main route from the ones that were found.
                    displayRouteInstructions(routes[0])
                }

                SdkError.Cancel -> {
                    // The routing action was cancelled.
                }

                else -> {
                    // There was a problem at computing the routing operation.
                    Toast.makeText(
                        this@MainActivity, "Routing service error: ${reason.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.list_view)
        progressBar = findViewById(R.id.progressBar)

        val layoutManager = LinearLayoutManager(this)
        listView?.layoutManager = layoutManager

        val separator = DividerItemDecoration(applicationContext, layoutManager.orientation)
        listView?.addItemDecoration(separator)

        listView?.setBackgroundResource(R.color.white)
        val lateralPadding = resources.getDimension(R.dimen.bigPadding).toInt()
        listView?.setPadding(lateralPadding, 0, lateralPadding, 0)


        /// GENERAL MAGIC
        SdkSettings.onMapDataReady = {
            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            startCalculateRoute()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one. 
             */
            Toast.makeText(this@MainActivity, "TOKEN REJECTED", Toast.LENGTH_SHORT).show()
        }

        // This step of initialization is mandatory if you want to use the SDK without a map.
        if (!GemSdk.initSdkWithDefaults(this)) {
            finish()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBackPressed() {
        finish()
        exitProcess(0)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun startCalculateRoute() = SdkCall.execute {
        val wayPoints = arrayListOf(
            Landmark("Frankfurt am Main", 50.11428, 8.68133),
            Landmark("Karlsruhe", 49.0069, 8.4037),
            Landmark("Munich", 48.1351, 11.5820)
        )
        routingService.calculateRoute(wayPoints)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun displayRouteInstructions(route: Route) {
        // Get the instructions from the route.
        val instructions = SdkCall.execute { route.getInstructions() } ?: arrayListOf()
        listView?.adapter = CustomAdapter(instructions)
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * This custom adapter is made to facilitate the displaying of the data from the model
 * and to decide how it is displayed.
 */
class CustomAdapter(private val dataSet: ArrayList<RouteInstruction>) :
    RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.text)
        val status: TextView = view.findViewById(R.id.status_text)
        val description: TextView = view.findViewById(R.id.status_description)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.list_item, viewGroup, false)

        return ViewHolder(view)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val instruction = dataSet[position]
        var text: String
        var status: String
        var description: String

        SdkCall.execute {
            if (instruction.hasTurnInfo()) {
                text = instruction.getTurnInstruction() ?: ""
                if (text.isNotEmpty() && text.last() == '.') {
                    text.removeSuffix(".")
                }

                val distance =
                    instruction.getTraveledTimeDistance()?.getTotalDistance()?.toDouble() ?: 0.0

                val distText = SdkUtil.getDistText(distance.toInt(), SdkSettings().getUnitSystem())
                status = distText.first
                description = distText.second
                if (status == "0.00") {
                    status = "0"
                }

                postOnMain {
                    viewHolder.text.text = text
                    viewHolder.status.text = status
                    viewHolder.description.text = description
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun getItemCount() = dataSet.size

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
