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

package com.magiclane.sdk.examples.projection

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.projection.EHemisphere
import com.magiclane.sdk.projection.EProjectionType
import com.magiclane.sdk.projection.Projection
import com.magiclane.sdk.projection.ProjectionBNG
import com.magiclane.sdk.projection.ProjectionGK
import com.magiclane.sdk.projection.ProjectionLAM
import com.magiclane.sdk.projection.ProjectionMGRS
import com.magiclane.sdk.projection.ProjectionService
import com.magiclane.sdk.projection.ProjectionUTM
import com.magiclane.sdk.projection.ProjectionW3W
import com.magiclane.sdk.projection.ProjectionWGS84
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------

    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var projectionContainer: ConstraintLayout
    private lateinit var landmarkName: TextView
    private lateinit var projectionsList: RecyclerView
    private lateinit var hint: TextView

    private lateinit var projectionAdapter: ProjectionAdapter

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionAdapter = ProjectionAdapter(mutableListOf())

        gemSurfaceView = findViewById(R.id.gem_surface_view)
        projectionContainer = findViewById(R.id.projection_container)
        landmarkName = findViewById(R.id.landmark_name)
        hint = findViewById(R.id.hint)
        projectionsList = findViewById<RecyclerView?>(R.id.projections_list).also {
            it.layoutManager = LinearLayoutManager(this)
            it.addItemDecoration(DividerItemDecoration(this, (it.layoutManager as LinearLayoutManager).orientation))
            it.adapter = projectionAdapter
            it.itemAnimator = null
        }

        findViewById<ImageButton>(R.id.close_button).apply {
            setOnClickListener {
                projectionContainer.visibility = View.GONE
            }
        }

        setConstraints(resources.configuration.orientation)

        SdkSettings.onMapDataReady = onMapDataReady@ { isReady ->
            if (!isReady) return@onMapDataReady

            hint.visibility = View.VISIBLE

            gemSurfaceView.mapView?.onTouch = { xy ->
                // xy are the coordinates of the touch event
                SdkCall.execute {
                    // tell the map view where the touch event happened
                    gemSurfaceView.mapView?.cursorScreenPosition = xy

                    val centerXy = Xy(gemSurfaceView.measuredWidth / 2, gemSurfaceView.measuredHeight / 2)

                    val landmarks = gemSurfaceView.mapView?.cursorSelectionLandmarks
                    if (!landmarks.isNullOrEmpty())
                    {
                        val landmark = landmarks[0]
                        landmark.coordinates?.let {
                            gemSurfaceView.mapView?.centerOnCoordinates(
                                coords = it,
                                zoomLevel = -1,
                                xy = centerXy,
                                animation = Animation(EAnimation.Linear),
                                mapAngle = Double.MAX_VALUE,
                                viewAngle = Double.MAX_VALUE
                            )
                        }
                        Util.postOnMain { hint.visibility = View.GONE }
                        showProjectionsForLandmark(landmark)
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

    override fun onConfigurationChanged(newConfig: Configuration)
    {
        super.onConfigurationChanged(newConfig)

        setConstraints(newConfig.orientation)
    }

    // ---------------------------------------------------------------------------------------------

    private fun showProjectionsForLandmark(landmark: Landmark)
    {
        if (landmark.coordinates == null)
        {
            return
        }

        val wgs84Projection = ProjectionWGS84(landmark.coordinates!!)
        projectionAdapter.dataSet.apply {
            clear()
            add(wgs84Projection)
        }

        for (i in EProjectionType.values())
        {
            if (i == EProjectionType.EPR_Wgs84 || i == EProjectionType.EPR_Undefined)
                continue

            val projection: Projection = when (i)
            {
                EProjectionType.EPR_WhatThreeWords -> ProjectionW3W().also { it.setToken(getString(R.string.what_3_words_token)) }
                EProjectionType.EPR_Bng -> ProjectionBNG()
                EProjectionType.EPR_Lam -> ProjectionLAM()
                EProjectionType.EPR_Utm -> ProjectionUTM()
                EProjectionType.EPR_Mgrs -> ProjectionMGRS()
                EProjectionType.EPR_Gk -> ProjectionGK()
                else -> return
            }

            val progressListener = ProgressListener.create(
                onCompleted = onCompleted@{ errorCode, _ ->
                    if (GemError.isError(errorCode))
                    {
                        return@onCompleted
                    }

                    projectionAdapter.run {
                        dataSet.add(projection)
                        notifyItemInserted(dataSet.size - 1)
                    }
                }
            )

            ProjectionService.convert(wgs84Projection, projection, progressListener)
        }

        Util.postOnMain {
            landmarkName.text = SdkCall.execute { landmark.name }
            projectionContainer.visibility = View.VISIBLE
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setConstraints(orientation: Int)
    {
        val rootView = findViewById<ConstraintLayout>(R.id.root_view)
        when (orientation)
        {
            Configuration.ORIENTATION_LANDSCAPE ->
            {
                ConstraintSet().apply {
                    clone(rootView)

                    connect(R.id.projection_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(R.id.projection_container, ConstraintSet.END, R.id.gem_surface_view, ConstraintSet.START)
                    connect(R.id.projection_container, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(R.id.projection_container, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                    connect(R.id.gem_surface_view, ConstraintSet.START, R.id.projection_container, ConstraintSet.END)
                    connect(R.id.gem_surface_view, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    connect(R.id.gem_surface_view, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(R.id.gem_surface_view, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                    applyTo(rootView)
                }

                projectionContainer.layoutParams.apply {
                    width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                    height = ConstraintLayout.LayoutParams.MATCH_PARENT
                }

                gemSurfaceView.layoutParams.apply {
                    width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                    height = ConstraintLayout.LayoutParams.MATCH_PARENT
                }
            }

            Configuration.ORIENTATION_PORTRAIT ->
            {
                ConstraintSet().apply {
                    clone(rootView)

                    connect(R.id.projection_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(R.id.projection_container, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    connect(R.id.projection_container, ConstraintSet.TOP, R.id.guideline, ConstraintSet.BOTTOM)
                    connect(R.id.projection_container, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                    connect(R.id.gem_surface_view, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(R.id.gem_surface_view, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    connect(R.id.gem_surface_view, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(R.id.gem_surface_view, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                    applyTo(rootView)
                }

                projectionContainer.layoutParams.apply {
                    width = ConstraintLayout.LayoutParams.MATCH_PARENT
                    height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                }

                gemSurfaceView.layoutParams.apply {
                    width = ConstraintLayout.LayoutParams.MATCH_PARENT
                    height = ConstraintLayout.LayoutParams.MATCH_PARENT
                }
            }
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

    inner class ProjectionAdapter(val dataSet: MutableList<Projection>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>()
    {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder
        {
            val layout = when (viewType)
            {
                EProjectionType.EPR_WhatThreeWords.ordinal -> R.layout.one_param_list_item
                EProjectionType.EPR_Lam.ordinal,
                EProjectionType.EPR_Wgs84.ordinal -> R.layout.two_params_list_item
                EProjectionType.EPR_Mgrs.ordinal,
                EProjectionType.EPR_Utm.ordinal -> R.layout.four_params_list_item
                EProjectionType.EPR_Bng.ordinal,
                EProjectionType.EPR_Gk.ordinal -> R.layout.three_params_list_item
                else -> R.layout.two_params_list_item
            }

            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)

            return when (viewType)
            {
                EProjectionType.EPR_WhatThreeWords.ordinal -> WhatThreeWordsViewHolder(view)
                EProjectionType.EPR_Bng.ordinal -> BngViewHolder(view)
                EProjectionType.EPR_Lam.ordinal -> LamViewHolder(view)
                EProjectionType.EPR_Utm.ordinal -> UtmViewHolder(view)
                EProjectionType.EPR_Mgrs.ordinal -> MgrsViewHolder(view)
                EProjectionType.EPR_Gk.ordinal -> GkViewHolder(view)
                EProjectionType.EPR_Wgs84.ordinal -> Wgs84ViewHolder(view)
                else -> Wgs84ViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int)
        {
            when (holder.itemViewType)
            {
                EProjectionType.EPR_WhatThreeWords.ordinal -> (holder as WhatThreeWordsViewHolder).bind(position)
                EProjectionType.EPR_Bng.ordinal -> (holder as BngViewHolder).bind(position)
                EProjectionType.EPR_Lam.ordinal -> (holder as LamViewHolder).bind(position)
                EProjectionType.EPR_Utm.ordinal -> (holder as UtmViewHolder).bind(position)
                EProjectionType.EPR_Mgrs.ordinal -> (holder as MgrsViewHolder).bind(position)
                EProjectionType.EPR_Gk.ordinal -> (holder as GkViewHolder).bind(position)
                EProjectionType.EPR_Wgs84.ordinal -> (holder as Wgs84ViewHolder).bind(position)
            }
        }

        override fun getItemViewType(position: Int): Int = dataSet[position].type.ordinal

        override fun getItemCount(): Int = dataSet.size

        inner class WhatThreeWordsViewHolder(view: View) : RecyclerView.ViewHolder(view)
        {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val words: TextView = view.findViewById(R.id.words)

            fun bind(position: Int)
            {
                val item = dataSet[position] as ProjectionW3W

                var name = ""
                var wordsStr = ""

                SdkCall.execute {
                    name = item.type.toString().split("_")[1]
                    wordsStr = String.format("%s %s", getString(R.string.words), item.getWords())
                }

                projectionName.text = name.uppercase()
                words.text = wordsStr
            }
        }

        inner class BngViewHolder(view: View) : RecyclerView.ViewHolder(view)
        {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val easting: TextView = view.findViewById(R.id.x)
            private val northing: TextView = view.findViewById(R.id.y)
            private val gridReference: TextView = view.findViewById(R.id.zone)

            fun bind(position: Int)
            {
                val item = dataSet[position] as ProjectionBNG

                var name = ""
                var eastingStr = ""
                var northingStr = ""
                var gridReferenceStr = ""

                SdkCall.execute {
                    name = item.type.toString().split("_")[1]
                    eastingStr = String.format("%s %f", getString(R.string.easting), item.getEasting())
                    northingStr = String.format("%s %f", getString(R.string.northing), item.getNorthing())
                    gridReferenceStr = String.format("%s %s", getString(R.string.grid_reference), item.gridReference)
                }

                projectionName.text = name.uppercase()
                easting.text = eastingStr
                northing.text = northingStr
                gridReference.text = gridReferenceStr
            }
        }

        inner class LamViewHolder(view: View) : RecyclerView.ViewHolder(view)
        {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val x: TextView = view.findViewById(R.id.x)
            private val y: TextView = view.findViewById(R.id.y)

            fun bind(position: Int)
            {
                val item = dataSet[position] as ProjectionLAM

                var name = ""
                var xStr = ""
                var yStr = ""

                SdkCall.execute {
                    name = item.type.toString().split("_")[1]
                    xStr = String.format("%s %f", getString(R.string.x), item.getX())
                    yStr = String.format("%s %f", getString(R.string.y), item.getY())
                }

                projectionName.text = name.uppercase()
                x.text = xStr
                y.text = yStr
            }
        }

        inner class UtmViewHolder(view: View) : RecyclerView.ViewHolder(view)
        {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val x: TextView = view.findViewById(R.id.x)
            private val y: TextView = view.findViewById(R.id.y)
            private val zone: TextView = view.findViewById(R.id.zone)
            private val hemisphere: TextView = view.findViewById(R.id.hemisphere)

            fun bind(position: Int)
            {
                val item = dataSet[position] as ProjectionUTM

                var name = ""
                var xStr = ""
                var yStr = ""
                var zoneStr = ""
                var hemisphereStr = ""

                SdkCall.execute {
                    name = item.type.toString().split("_")[1]
                    xStr = String.format("%s %f", getString(R.string.x), item.getX())
                    yStr = String.format("%s %f", getString(R.string.y), item.getY())
                    zoneStr = String.format("%s %d", getString(R.string.zone), item.getZone())
                    hemisphereStr = String.format("%s %s", getString(R.string.hemisphere), EHemisphere.values()[item.getHemisphere()].toString())
                }

                projectionName.text = name.uppercase()
                x.text = xStr
                y.text = yStr
                zone.text = zoneStr
                hemisphere.text = hemisphereStr
            }
        }

        inner class MgrsViewHolder(view: View) : RecyclerView.ViewHolder(view)
        {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val easting: TextView = view.findViewById(R.id.x)
            private val northing: TextView = view.findViewById(R.id.y)
            private val zone: TextView = view.findViewById(R.id.zone)
            private val letters: TextView = view.findViewById(R.id.hemisphere)

            fun bind(position: Int)
            {
                val item = dataSet[position] as ProjectionMGRS

                var name = ""
                var eastingStr = ""
                var northingStr = ""
                var zoneStr = ""
                var lettersStr = ""

                SdkCall.execute {
                    name = item.type.toString().split("_")[1]
                    eastingStr = String.format("%s %06d", getString(R.string.easting), item.getEasting())
                    northingStr = String.format("%s %06d", getString(R.string.northing), item.getNorthing())
                    zoneStr = String.format("%s %s", getString(R.string.zone), item.getZone())
                    lettersStr = String.format("%s %s", getString(R.string.letters), item.getSq100kIdentifier())
                }

                projectionName.text = name.uppercase()
                easting.text = eastingStr
                northing.text = northingStr
                zone.text = zoneStr
                letters.text = lettersStr
            }
        }

        inner class GkViewHolder(view: View) : RecyclerView.ViewHolder(view)
        {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val easting: TextView = view.findViewById(R.id.x)
            private val northing: TextView = view.findViewById(R.id.y)
            private val zone: TextView = view.findViewById(R.id.zone)

            fun bind(position: Int)
            {
                val item = dataSet[position] as ProjectionGK

                var name = ""
                var eastingStr = ""
                var northingStr = ""
                var zoneStr = ""

                SdkCall.execute {
                    name = item.type.toString().split("_")[1]
                    eastingStr = String.format("%s %f", getString(R.string.easting), item.getEasting())
                    northingStr = String.format("%s %f", getString(R.string.northing), item.getNorthing())
                    zoneStr = String.format("%s %s", getString(R.string.zone), item.getZone())
                }

                projectionName.text = name.uppercase()
                easting.text = eastingStr
                northing.text = northingStr
                zone.text = zoneStr
            }
        }

        inner class Wgs84ViewHolder(view: View) : RecyclerView.ViewHolder(view)
        {
            private val projectionName: TextView = view.findViewById(R.id.projection_name)
            private val latitude: TextView = view.findViewById(R.id.x)
            private val longitude: TextView = view.findViewById(R.id.y)

            fun bind(position: Int)
            {
                val item = dataSet[position] as ProjectionWGS84

                var name = ""
                var latitudeStr = ""
                var longitudeStr = ""

                SdkCall.execute {
                    name = item.type.toString().split("_")[1]
                    latitudeStr = String.format("%s %f", getString(R.string.lat), item.coordinates.latitude)
                    longitudeStr = String.format("%s %f", getString(R.string.lon), item.coordinates.longitude)
                }

                projectionName.text = name.uppercase()
                latitude.text = latitudeStr
                longitude.text = longitudeStr
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------