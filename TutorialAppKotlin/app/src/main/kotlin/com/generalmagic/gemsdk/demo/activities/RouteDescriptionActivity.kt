/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.util.IntentHelper
import com.generalmagic.gemsdk.demo.util.Util
import com.generalmagic.gemsdk.demo.util.UtilUITexts
import com.generalmagic.gemsdk.demo.util.Utils.Companion.getDistText
import com.generalmagic.gemsdk.demo.util.Utils.Companion.getFormattedWaypointName
import com.generalmagic.gemsdk.demo.util.Utils.Companion.getUIString
import com.generalmagic.gemsdk.extensions.StringIds
import com.generalmagic.gemsdk.models.RouteTrafficEvent
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdk.util.GemIcons
import kotlinx.android.synthetic.main.activity_route_description.*
import kotlinx.android.synthetic.main.route_description_item.view.*

class RouteDescriptionActivity : Activity() {
    private lateinit var route: Route
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_description)
        setActionBar(rd_toolbar)
        actionBar?.title = "Route Description"
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setDisplayShowHomeEnabled(true)

        val inRoute = IntentHelper.getObjectForKey(EXTRA_ROUTE) as Route?
        if (inRoute == null) {
            finish()
            return
        }
        route = inRoute

        val adapter = RouteAdapter(this, toViewModels(route))

        Handler(Looper.getMainLooper()).post {
            routeDescriptionList.adapter = adapter
        }
    }

    override fun onNavigateUp(): Boolean {
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
                        route,
                        RouteWarningItem.TRouteWarningType.ERIRequireToll,
                        sortKey
                    )
                )
                sortKey = result.last().mSortKey
            }

            // add ferry item
            if (route.hasFerryConnections()) {
                result.add(
                    RouteWarningItem(
                        route,
                        RouteWarningItem.TRouteWarningType.ERIRequireFerry,
                        sortKey
                    )
                )
                sortKey = result.last().mSortKey
            }

            // add restricted area item
            val timeDistance = route.getTimeDistance()
            val distanceInMeters = timeDistance?.getRestrictedDistance() ?: 0
            val timeInSeconds = timeDistance?.getRestrictedTime() ?: 0

            if ((distanceInMeters > 0) || (timeInSeconds > 0)) { // ROUTE WARNING
                result.add(
                    RouteWarningItem(
                        route,
                        RouteWarningItem.TRouteWarningType.ERIRestrictedAreas,
                        sortKey
                    )
                )
// 				sortKey = result.last().mSortKey
            }

            // add route instructions
            val segmentList = route.getSegments()
            if (segmentList != null) {
                for (segment in segmentList) {
                    val instructionList = segment.getInstructions() ?: continue
                    for (instruction in instructionList) {
                        result.add(RouteInstructionItem(instruction))
                    }
                }
            }

            // add traffic events
            val routeLength = timeDistance?.getTotalDistance() ?: 0
            val trafficEventsList = route.getTrafficEvents()
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

// --------------------------------------------------------------------------------------------------

abstract class ListViewItem {
    var mSortKey = 0
    var text: String = ""
    var description: String = ""
    var descriptionColor: Int = Color.BLACK
    var statusText: String = ""
    var statusDescription: String = ""

    abstract fun getBitmap(width: Int, height: Int): Bitmap?
}

// --------------------------------------------------------------------------------------------------

class RouteAdapter(context: Context, items: ArrayList<ListViewItem>) :
    ArrayAdapter<ListViewItem>(context, -1, items.toList()) {

    private var iconSize: Int = context.resources.getDimension(R.dimen.listIconSize).toInt()

    private var onListItemSelected: ((index: Int) -> Unit)? = null

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var rowView: View? = convertView
        if (rowView == null) {
            val inflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            rowView = inflater.inflate(R.layout.route_description_item, parent, false)
        }

        val resultView = rowView!!
        val modelItem = getItem(position) ?: return resultView
        val bitmap = GEMSdkCall.execute { modelItem.getBitmap(iconSize, iconSize) }

        resultView.text.text = textToHtml(modelItem.text)
        resultView.description.text = textToHtml(modelItem.description)
        resultView.description.setTextColor(Util.getColor(modelItem.descriptionColor))
        resultView.status_text.text = textToHtml(modelItem.statusText)
        resultView.status_description.text = textToHtml(modelItem.statusDescription)

        if (bitmap != null) {
            resultView.icon.setImageBitmap(bitmap)
        }

        resultView.setOnClickListener {
            onListItemSelected?.invoke(position)
        }
        return resultView
    }

    private fun textToHtml(text: String): Spanned {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(text)
        }
    }
}

// --------------------------------------------------------------------------------------------------
// List Items
// --------------------------------------------------------------------------------------------------

class RouteTitleItem(route: Route, sortKey: Int) : ListViewItem() {
    init {
        val timeDistance = route.getTimeDistance()
        val distanceInMeters = timeDistance?.getTotalDistance() ?: 0
        val timeInSeconds = timeDistance?.getTotalTime() ?: 0

        description = UtilUITexts.getFormattedDistanceTime(distanceInMeters, timeInSeconds)
        val waypoints = route.getWaypoints()
        if (waypoints != null && waypoints.size > 0) {
            var departureName = waypoints[0].getName() ?: ""
            var destinationName = waypoints.last().getName() ?: ""

            if (departureName.isEmpty()) {
                departureName = getFormattedWaypointName(waypoints[0], true)
            }

            if (destinationName.isEmpty()) {
                destinationName = getFormattedWaypointName(waypoints.last(), true)
            }

            val departureNameFormatted: String
            val destinationNameFormatted: String

            if (GEMSdk.getNativeConfiguration() == GEMSdk.TNativeConfiguration.ENC_ANDROID) {
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

    override fun getBitmap(width: Int, height: Int): Bitmap? {
        GEMSdkCall.checkCurrentThread()

        return Util.getImageIdAsBitmap(GemIcons.Other_UI.RouteOverview.value, width, height)
    }
}

class RouteWarningItem(route: Route, private val type: TRouteWarningType, sortKey: Int) :
    ListViewItem() {
    enum class TRouteWarningType {
        ERIRequireToll, ERIRequireFerry, ERIRestrictedAreas
    }

    init {
        val warningText = when (type) {
            TRouteWarningType.ERIRequireToll -> {
                getUIString(StringIds.eStrRouteWithTolls)
            }
            TRouteWarningType.ERIRequireFerry -> {
                getUIString(StringIds.eStrRouteWithFerry)
            }
            TRouteWarningType.ERIRestrictedAreas -> {
                getUIString(StringIds.eStrRouteCrosesesRestrictedAreas)
            }
        }

        if (type == TRouteWarningType.ERIRestrictedAreas) {
            val timeDistance = route.getTimeDistance()!!
            val distanceInMeters = timeDistance.getRestrictedDistance()
            val timeInSeconds = timeDistance.getRestrictedTime()

            if (distanceInMeters > 0 || timeInSeconds > 0) {
                description = UtilUITexts.getFormattedDistanceTime(distanceInMeters, timeInSeconds)
            }
        }

        text = if (GEMSdk.getNativeConfiguration() == GEMSdk.TNativeConfiguration.ENC_ANDROID) {
            String.format("<b>$warningText</b>")
        } else {
            warningText
        }

        mSortKey = sortKey + 1
    }

    override fun getBitmap(width: Int, height: Int): Bitmap? {
        GEMSdkCall.checkCurrentThread()

        val iconId = when (type) {
            TRouteWarningType.ERIRequireToll -> {
                GemIcons.Other_UI.LocationDetails_TollStation.value
            }
            TRouteWarningType.ERIRequireFerry -> {
                GemIcons.Other_UI.LocationDetails_FerryTerminal.value
            }
            TRouteWarningType.ERIRestrictedAreas -> {
                GemIcons.Other_UI.DefineRoadblock.value
            }
        }

        return Util.getImageIdAsBitmap(iconId, width, height)
    }
}

class RouteInstructionItem(private val instruction: RouteInstruction, distOffset: Double = -1.0) :
    ListViewItem() {
    private var mCrossesRestrictedArea: Boolean = false

    init {
        if (instruction.hasTurnInfo()) {
            text = instruction.getTurnInstruction() ?: ""
            if (text.isNotEmpty() && text.last() == '.') {
                text.removeSuffix(".")
            }

            if (instruction.hasFollowRoadInfo()) {
                description = instruction.getFollowRoadInstruction() ?: ""
                if (description.isNotEmpty() && description.last() == '.') {
                    description.removeSuffix(".")
                }
            }

            var distance =
                instruction.getTraveledTimeDistance()?.getTotalDistance()?.toDouble() ?: 0.0
            if ((distOffset > 0.0) && (distance >= distOffset)) {
                distance -= distOffset
            }

            mSortKey = (distance + 0.5).toInt()

            val distText = getDistText(distance.toInt(), CommonSettings().getUnitSystem())
            statusText = distText.first
            statusDescription = distText.second
            if (statusText == "0.00") {
                statusText = "0"
            }

            val timeDistToNextTurn = instruction.getTimeDistanceToNextTurn()
            if (timeDistToNextTurn != null) {
                if (timeDistToNextTurn.getRestrictedTime() > 0 || timeDistToNextTurn.getRestrictedDistance() > 0) {
                    mCrossesRestrictedArea = true
                    descriptionColor = TRgba(255, 0, 0, 255).value()
                }
            }
        }
    }

    override fun getBitmap(width: Int, height: Int): Bitmap? {
        GEMSdkCall.checkCurrentThread()

        val aInner = TRgba(0, 0, 0, 255)
        val aOuter = TRgba(255, 255, 255, 255)
        val iInner = TRgba(128, 128, 128, 255)
        val iOuter = TRgba(128, 128, 128, 255)

        val image = instruction.getTurnDetails()?.getAbstractGeometryImage()
        return Util.createBitmap(image, width, height, aInner, aOuter, iInner, iOuter)
    }
}

class TrafficEventItem(private val event: RouteTrafficEvent, totalRouteLength: Int) :
    ListViewItem() {

    init {
        text = UtilUITexts.formatTrafficDelayAndLength(
            event.getLength(),
            event.getDelay(),
            event.isRoadblock()
        )
        val eDescription = event.getDescription() ?: ""

        if (description.isNotEmpty()) {
            text = String.format("%s (%s)", text, eDescription)
        }
// 		else{
// 			eDescription = eStrTraffic
// 		}

        val from = event.getFromLandmark()
        val to = event.getToLandmark()

        if (from != null && to != null) {
            if (from.second && to.second) {
                val strFrom: String = UtilUITexts.formatLandmarkDetails(from.first)
                val strTo: String = UtilUITexts.formatLandmarkDetails(to.first)

                description = if (strFrom.compareTo(strTo, true) == 0) {
                    String.format(getUIString(StringIds.eStrOnRoadName), strFrom)
                } else {
                    String.format(getUIString(StringIds.eStrFromAtoB), strFrom, strTo)
                }
            }
        }
        val remainingDistance = event.getDistanceToDestination()
        var distance = totalRouteLength - remainingDistance
        if (distance < 0) {
            distance = 0
        }

        mSortKey = (distance + 1)

        val distText = getDistText(distance, CommonSettings().getUnitSystem(), true)
        statusText = distText.first
        statusDescription = distText.second

        if (statusText == "0.00") {
            statusText = "0"
        }
    }

    override fun getBitmap(width: Int, height: Int): Bitmap? {
        GEMSdkCall.checkCurrentThread()

        val image = event.getImage() ?: return null
        return Util.createBitmap(image, width, height)
    }
}
