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

import android.content.pm.PackageManager
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
import com.generalmagic.sdk.examples.util.SdkInitHelper
import com.generalmagic.sdk.examples.util.SdkInitHelper.isMapReady
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
import com.generalmagic.sdk.examples.util.Util
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RouteInstruction
import com.generalmagic.sdk.routesandnavigation.RoutingService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import com.generalmagic.sdk.util.Util.Companion.postOnMain

////////////////////////////////////////////////////////////////////////////////////////////////

class MainActivity : AppCompatActivity() {
    var listView: RecyclerView? = null
    var progressBar: ProgressBar? = null
    var mainRoute: Route? = null

    private val routingService = RoutingService()

    fun displayRouteInstructions(route: Route) {
        val instructions = arrayListOf<RouteInstruction>()
        SdkCall.execute {
            // Get all the route segments.
            val segmentList = route.getSegments()
            if (segmentList != null) {
                // For each segment, get the route instructions.
                for (segment in segmentList) {
                    val instructionList = segment.getInstructions() ?: continue
                    for (instruction in instructionList) {
                        instructions.add(instruction)
                    }
                }
            }
        }

        val adapter = CustomAdapter(instructions)

        listView?.adapter = adapter
        progressBar?.visibility = View.GONE
    }

    private fun start() {
        if (!isMapReady) return

        val wayPoints = arrayListOf(
            Landmark("Frankfurt am Main", Coordinates(50.11428, 8.68133)),
            Landmark("Karlsruhe", Coordinates(49.0069, 8.4037)),
            Landmark("Munich", Coordinates(48.1351, 11.5820))
        )
        routingService.calculateRoute(wayPoints)
    }

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
        routingService.onStarted = {
            progressBar?.visibility = View.VISIBLE
        }

        routingService.onCompleted = onCompleted@{ routes, reason, hint ->
            when (val gemError = SdkError.fromInt(reason)) {
                SdkError.NoError -> {
                    // No error encountered, we can handle the results.
                    SdkCall.execute {
                        // Get the main route from the ones that were found.
                        mainRoute = if (routes.size > 0) {
                            routes[0]
                        } else {
                            null
                        }

                        postOnMain { mainRoute?.let { displayRouteInstructions(it) } }
                    }
                }

                SdkError.Cancel -> {
                    // The routing action was cancelled.
                    return@onCompleted
                }

                else -> {
                    // There was a problem at computing the routing operation.
                    Toast.makeText(
                        this@MainActivity,
                        "Routing service error: ${gemError.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val calcDefaultRoute = calcDefaultRoute@{
            if (!isMapReady) return@calcDefaultRoute
            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            SdkCall.execute {
                start()
            }
        }

        SdkInitHelper.onMapReady = {
            // Defines an action that should be done after the world map is updated.
            calcDefaultRoute()
        }

        SdkInitHelper.onNetworkConnected = {
            // Defines an action that should be done after the network is connected.
            calcDefaultRoute()
        }

        SdkInitHelper.onCancel = {
            // Defines what should be executed when the SDK initialization is cancelled.
            SdkCall.execute { routingService.cancelRoute() }
        }
        
        val app = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val token = app.metaData.getString("com.generalmagic.sdk.token") ?: "YOUR_TOKEN"

        if (!SdkInitHelper.init(this, token)) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        SdkInitHelper.deinit()
    }

    override fun onBackPressed() {
        terminateApp(this)
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

                val distText = Util.getDistText(distance.toInt(), CommonSettings.getUnitSystem())
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
