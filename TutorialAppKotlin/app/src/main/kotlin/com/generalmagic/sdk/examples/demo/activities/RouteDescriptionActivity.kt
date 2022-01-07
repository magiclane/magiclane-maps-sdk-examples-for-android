/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.generalmagic.sdk.*
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.Rgba
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.util.IntentHelper
import com.generalmagic.sdk.examples.demo.util.Util
import com.generalmagic.sdk.examples.demo.util.UtilUITexts
import com.generalmagic.sdk.examples.demo.util.UtilUITexts.fillLandmarkAddressInfo
import com.generalmagic.sdk.examples.demo.util.Utils.getFormattedWaypointName
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RouteInstruction
import com.generalmagic.sdk.routesandnavigation.RouteTrafficEvent
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkImages
import com.generalmagic.sdk.util.SdkUtil.getDistText
import com.generalmagic.sdk.util.SdkUtil.getUIString
import com.generalmagic.sdk.util.StringIds
import com.generalmagic.sdk.util.UtilGemImages

class RouteDescriptionActivity : AppCompatActivity() {
    private lateinit var route: Route

    private lateinit var rdToolbar: Toolbar
    lateinit var routeDescriptionList: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_description)

        rdToolbar = findViewById(R.id.rd_toolbar)
        routeDescriptionList = findViewById(R.id.routeDescriptionList)

        setSupportActionBar(rdToolbar)
        supportActionBar?.title = "Route Description"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val inRoute = IntentHelper.getObjectForKey(EXTRA_ROUTE) as Route?
        if (inRoute == null) {
            finish()
            return
        }
        route = inRoute

        SdkCall.execute { toViewModels(route) }?.let {
            routeDescriptionList.adapter = RouteAdapter(this, it)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_ROUTE = "route"

        fun showRouteDescription(context: Context, route: Route) {
            // AVOID SERIALIZABLE 
            IntentHelper.addObjectForKey(route, EXTRA_ROUTE)

            val intent = Intent(context, RouteDescriptionActivity::class.java)
            context.startActivity(intent)
        }

        private fun toViewModels(route: Route?): ArrayList<ListViewItem> {
            SdkCall.checkCurrentThread()
            if (route == null) return ArrayList()

            val result = ArrayList<ListViewItem>()

            var sortKey = -10

            // add title item
            result.add(RouteTitleItem(route, sortKey))
            sortKey = result.last().mSortKey

            // add tolls item
            if (route.hasTollRoads()) {
                result.add(
                    RouteWarningItem(
                        route, RouteWarningItem.TRouteWarningType.RequireToll, sortKey
                    )
                )

                sortKey = result.last().mSortKey
            }

            // add ferry item
            if (route.hasFerryConnections()) {
                result.add(
                    RouteWarningItem(
                        route, RouteWarningItem.TRouteWarningType.RequireFerry, sortKey
                    )
                )
                sortKey = result.last().mSortKey
            }

            // add restricted area item
            val timeDistance = route.timeDistance
            val distanceInMeters = timeDistance?.restrictedDistance ?: 0
            val timeInSeconds = timeDistance?.restrictedTime ?: 0

            if ((distanceInMeters > 0) || (timeInSeconds > 0)) { // ROUTE WARNING
                result.add(
                    RouteWarningItem(
                        route, RouteWarningItem.TRouteWarningType.RestrictedAreas, sortKey
                    )
                )
// 				sortKey = result.last().mSortKey
            }

            // add route instructions
            val segmentList = route.segments
            if (segmentList != null) {
                for (segment in segmentList) {
                    val instructionList = segment.instructions ?: continue
                    for (instruction in instructionList) {
                        result.add(RouteInstructionItem(instruction))
                    }
                }
            }

            // add traffic events
            val routeLength = timeDistance?.totalDistance ?: 0
            val trafficEventsList = route.trafficEvents
            if (trafficEventsList != null) {
                for (event in trafficEventsList) {
                    result.add(TrafficEventItem(event, routeLength))
                }
            }

            result.sortBy { it.mSortKey }

            return result
        }
    }
}

abstract class ListViewItem {
    var mSortKey = 0
    var text: String = ""
    var description: String = ""
    var descriptionColor: Int = Color.BLACK
    var statusText: String = ""
    var statusDescription: String = ""

    abstract fun getBitmap(width: Int, height: Int): Bitmap?
}

class RouteAdapter(context: Context, items: ArrayList<ListViewItem>) :
    ArrayAdapter<ListViewItem>(context, -1, items.toList()) {

    private var iconSize: Int = context.resources.getDimension(R.dimen.listIconSize).toInt()

    private var onListItemSelected: ((index: Int) -> Unit)? = { index ->
        val item = items[index]
        when {
            (item is RouteInstructionItem) -> {
                GEMApplication.focusOnRouteInstructionItem(item.instruction)
                (this@RouteAdapter.context as RouteDescriptionActivity).finish()
            }

            (item is TrafficEventItem) -> {
                GEMApplication.focusOnRouteTrafficItem(item.event)
                (this@RouteAdapter.context as RouteDescriptionActivity).finish()
            }
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var rowView: View? = convertView
        if (rowView == null) {
            val inflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            rowView = inflater.inflate(R.layout.route_description_item, parent, false)
        }

        val resultView = rowView!!
        val modelItem = getItem(position) ?: return resultView
        val bitmap = modelItem.getBitmap(iconSize, iconSize)

        val text = resultView.findViewById<TextView>(R.id.text)
        val description = resultView.findViewById<TextView>(R.id.description)
        val statusText = resultView.findViewById<TextView>(R.id.status_text)
        val statusDescription = resultView.findViewById<TextView>(R.id.status_description)
        val icon = resultView.findViewById<ImageView>(R.id.icon)

        text.text = textToHtml(modelItem.text)

        if (modelItem.description.isEmpty()) {
            description.visibility = View.GONE
        } else {
            description.visibility = View.VISIBLE
            description.text = textToHtml(modelItem.description)
            description.setTextColor(Util.getColor(modelItem.descriptionColor))
        }

        statusText.text = textToHtml(modelItem.statusText)
        statusDescription.text = textToHtml(modelItem.statusDescription)

        if (bitmap != null) {
            icon.setImageBitmap(bitmap)
        }

        resultView.setOnClickListener {
            onListItemSelected?.invoke(position)
        }
        return resultView
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun textToHtml(text: String): Spanned {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(text)
        }
    }
}

// List Items

class RouteTitleItem(route: Route, sortKey: Int) : ListViewItem() {
    init {
        SdkCall.checkCurrentThread()

        val timeDistance = route.timeDistance
        val distanceInMeters = timeDistance?.totalDistance ?: 0
        val timeInSeconds = timeDistance?.totalTime ?: 0

        description = UtilUITexts.getFormattedDistanceTime(distanceInMeters, timeInSeconds)
        val waypoints = route.waypoints
        if (waypoints != null && waypoints.size > 0) {
            var departureName = waypoints[0].name ?: ""
            var destinationName = waypoints.last().name ?: ""

            if (departureName.isEmpty()) {
                departureName = getFormattedWaypointName(waypoints[0], true)
            }

            if (destinationName.isEmpty()) {
                destinationName = getFormattedWaypointName(waypoints.last(), true)
            }

            val departureNameFormatted: String
            val destinationNameFormatted: String

            if (GemSdk.nativeConfiguration == GemSdk.ENativeConfiguration.Android) {
                departureNameFormatted = String.format("<b>$departureName</b>")
                destinationNameFormatted = String.format("<b>$destinationName</b>")
            } else {
                departureNameFormatted = departureName
                destinationNameFormatted = destinationName
            }

            text = String.format(
                getUIString(StringIds.eStrFromAtoB),
                departureNameFormatted,
                destinationNameFormatted
            )
            mSortKey = sortKey + 1
        }
    }

    override fun getBitmap(width: Int, height: Int): Bitmap? = SdkCall.execute {
        return@execute UtilGemImages.asBitmap(SdkImages.UI.RouteOverview.value, width, height)
    }
}

class RouteWarningItem(val route: Route, private val type: TRouteWarningType, sortKey: Int) :
    ListViewItem() {
    enum class TRouteWarningType {
        RequireToll, RequireFerry, RestrictedAreas
    }

    init {
        SdkCall.checkCurrentThread()

        val warningText = when (type) {
            TRouteWarningType.RequireToll -> {
                getUIString(StringIds.eStrRouteWithTolls)
            }
            TRouteWarningType.RequireFerry -> {
                getUIString(StringIds.eStrRouteWithFerry)
            }
            TRouteWarningType.RestrictedAreas -> {
                getUIString(StringIds.eStrRouteCrosesesRestrictedAreas)
            }
        }

        if (type == TRouteWarningType.RestrictedAreas) {
            val timeDistance = route.timeDistance
            val distanceInMeters = timeDistance?.restrictedDistance ?: 0
            val timeInSeconds = timeDistance?.restrictedTime ?: 0

            if (distanceInMeters > 0 || timeInSeconds > 0) {
                description = UtilUITexts.getFormattedDistanceTime(distanceInMeters, timeInSeconds)
            }
        }

        text = if (GemSdk.nativeConfiguration == GemSdk.ENativeConfiguration.Android) {
            String.format("<b>$warningText</b>")
        } else {
            warningText
        }

        mSortKey = sortKey + 1
    }

    override fun getBitmap(width: Int, height: Int): Bitmap? = SdkCall.execute {
        val iconId = when (type) {
            TRouteWarningType.RequireToll -> {
                SdkImages.UI.LocationDetails_TollStation.value
            }
            TRouteWarningType.RequireFerry -> {
                SdkImages.UI.LocationDetails_FerryTerminal.value
            }
            TRouteWarningType.RestrictedAreas -> {
                SdkImages.UI.DefineRoadblock.value
            }
        }

        return@execute UtilGemImages.asBitmap(iconId, width, height)
    }
}

class RouteInstructionItem(val instruction: RouteInstruction, distOffset: Double = -1.0) :
    ListViewItem() {
    private var mCrossesRestrictedArea: Boolean = false

    init {
        SdkCall.checkCurrentThread()

        if (instruction.hasTurnInfo()) {
            text = instruction.turnInstruction ?: ""
            if (text.isNotEmpty() && text.last() == '.') {
                text.removeSuffix(".")
            }

            if (instruction.hasFollowRoadInfo()) {
                description = instruction.followRoadInstruction ?: ""
                if (description.isNotEmpty() && description.last() == '.') {
                    description.removeSuffix(".")
                }
            }

            var distance =
                instruction.traveledTimeDistance?.totalDistance?.toDouble() ?: 0.0
            if ((distOffset > 0.0) && (distance >= distOffset)) {
                distance -= distOffset
            }

            mSortKey = (distance + 0.5).toInt()

            val distText = getDistText(distance.toInt(), SdkSettings().unitSystem)
            statusText = distText.first
            statusDescription = distText.second
            if (statusText == "0.00") {
                statusText = "0"
            }

            val timeDistToNextTurn = instruction.timeDistanceToNextTurn
            if (timeDistToNextTurn != null) {
                if (timeDistToNextTurn.restrictedTime > 0 || timeDistToNextTurn.restrictedDistance > 0) {
                    mCrossesRestrictedArea = true
                    descriptionColor = Rgba(255, 0, 0, 255).value
                }
            }
        }
    }

    override fun getBitmap(width: Int, height: Int): Bitmap? = SdkCall.execute {
        val aInner = Rgba(0, 0, 0, 255)
        val aOuter = Rgba(255, 255, 255, 255)
        val iInner = Rgba(128, 128, 128, 255)
        val iOuter = Rgba(128, 128, 128, 255)

        val image = instruction.turnDetails?.abstractGeometryImage
        return@execute UtilGemImages.asBitmap(image, width, height, aInner, aOuter, iInner, iOuter)
    }
}

class TrafficEventItem(val event: RouteTrafficEvent, totalRouteLength: Int) :
    ListViewItem() {

    init {
        SdkCall.checkCurrentThread()

        text = UtilUITexts.formatTrafficDelayAndLength(
            event.length,
            event.delay,
            event.isRoadblock()
        )
        val eDescription = event.description ?: ""

        if (description.isNotEmpty()) {
            text = String.format("%s (%s)", text, eDescription)
        }
// 		else{
// 			eDescription = eStrTraffic
// 		}

        val from = event.fromLandmark
        val to = event.toLandmark

        if (from != null && to != null) {
            if (from.second && to.second) {
                val strFrom: String =
                    SdkCall.execute { UtilUITexts.formatLandmarkDetails(from.first) } ?: ""
                val strTo: String =
                    SdkCall.execute { UtilUITexts.formatLandmarkDetails(to.first) } ?: ""

                description = if (strFrom.compareTo(strTo, true) == 0) {
                    String.format(getUIString(StringIds.eStrOnRoadName), strFrom)
                } else {
                    String.format(getUIString(StringIds.eStrFromAtoB), strFrom, strTo)
                }
            }
        }
        val remainingDistance = event.distanceToDestination
        var distance = totalRouteLength - remainingDistance
        if (distance < 0) {
            distance = 0
        }

        mSortKey = (distance + 1)

        val distText = getDistText(distance, SdkSettings().unitSystem, true)
        statusText = distText.first
        statusDescription = distText.second

        if (statusText == "0.00") {
            statusText = "0"
        }
    }

    override fun getBitmap(width: Int, height: Int): Bitmap? = SdkCall.execute {
        return@execute UtilGemImages.asBitmap(event.image, width, height)
    }
}
