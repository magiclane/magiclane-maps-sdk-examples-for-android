/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.demo.activities.publictransport

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnPreDraw
import com.generalmagic.sdk.demo.R
import com.generalmagic.sdk.demo.app.BaseActivity
import com.generalmagic.sdk.demo.app.GEMApplication
import com.generalmagic.sdk.demo.util.AppUtils
import com.generalmagic.sdk.demo.util.IntentHelper
import com.generalmagic.sdk.routingandnavigation.Route
import com.generalmagic.sdk.util.SdkCall
import kotlinx.android.synthetic.main.pt_route_description_activity.*
import kotlin.math.abs
import kotlin.math.roundToInt

class PublicTransportRouteDescriptionActivity : BaseActivity() {

    private var viewId: Long = 0

    private lateinit var route: Route

    private var routeDescriptionList: ListView? = null
    private var routeDescriptionAdapter: BaseAdapter? = null
    private var routeDescriptionViews: ArrayList<View?>? = null
    private var prevTouchDownItemTimeMs1: Long = -1
    private var prevTouchDownItemTimeMs2: Long = -1
    private var prevTouchDownItemTimeMs3: Long = -1
    private var prevTouchX1 = Float.MIN_VALUE
    private var prevTouchY1 = Float.MIN_VALUE
    private var prevTouchX2 = Float.MIN_VALUE
    private var prevTouchY2 = Float.MIN_VALUE
    private var prevTouchX3 = Float.MIN_VALUE
    private var prevTouchY3 = Float.MIN_VALUE
    private var clickThreshold: Float = 0f
    private val segmentViews = TRouteSegmentViews()

    internal inner class TRouteSegmentViews {
        var iconView: ImageView? = null
        var textView: TextView? = null
        var travelTimeContainerView: RelativeLayout? = null
        var travelTimeValueView: TextView? = null
        var travelTimeUnitView: TextView? = null
        var separatorView: ImageView? = null

        fun reset() {
            iconView = null
            textView = null
            travelTimeContainerView = null
            travelTimeValueView = null
            travelTimeUnitView = null
            separatorView = null
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PublicTransportRouteDescriptionLineView.setCellHeight(0f)
        PublicTransportRouteDescriptionLineView.setCellHeightExt(0f)
        clickThreshold = AppUtils.getSizeInPixelsFromMM(2).toFloat()

        setContentView(R.layout.pt_route_description_activity)

        setSupportActionBar(toolbar)

        // display back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewId = intent.getLongExtra("viewId", 0)

        val inRoute = IntentHelper.getObjectForKey(EXTRA_ROUTE) as Route?
        if (inRoute == null) {
            finish()
            return
        }
        route = inRoute

        SdkCall.execute {
            GEMPublicTransportRouteDescriptionView.loadItems(route)
        }

        val title = GEMPublicTransportRouteDescriptionView.title

        supportActionBar?.title = title

        var itemsCount = GEMPublicTransportRouteDescriptionView.itemsCount
        val segmentsCount = GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts

        PublicTransportRouteDescriptionLineView.resetIntermediatePointsOffsets(segmentsCount + 1)

        if (itemsCount > 0) {
            routeDescriptionList = findViewById(R.id.route_description_items_list)

            if (routeDescriptionList != null) {
                itemsCount++
                routeDescriptionViews = ArrayList(itemsCount)
                for (i in 0 until itemsCount) {
                    routeDescriptionViews!!.add(null)
                }

                initRouteDescriptionListAdapter()
                routeDescriptionList!!.adapter = routeDescriptionAdapter
            }
        }
    }

    private fun fillHeaderView(convertView: View?) {
        if (convertView != null) {
            val routeSegmentCount = GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts
            val tripTimeInterval = GEMPublicTransportRouteDescriptionView.routeItem.tripTimeInterval
            val tripDuration = GEMPublicTransportRouteDescriptionView.routeItem.tripDuration

            val tripTimeIntervalView = convertView.findViewById<TextView>(R.id.trip_time_interval)
            tripTimeIntervalView.text = tripTimeInterval

            val tripDurationView = convertView.findViewById<TextView>(R.id.trip_duration)
            tripDurationView.text = tripDuration

            val routeDescriptionContainer =
                convertView.findViewById<LinearLayout>(R.id.route_description_container)

            val params1 = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )

            val params2 = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                GEMApplication.appResources().getDimensionPixelSize(R.dimen.route_list_icon_size)
            )

            val params3 = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                GEMApplication.appResources()
                    .getDimensionPixelSize(R.dimen.route_list_separator_size)
            )

            params1.gravity = Gravity.CENTER_VERTICAL
            params2.gravity = Gravity.CENTER_VERTICAL
            params3.gravity = Gravity.CENTER_VERTICAL

            for (i in 0 until routeSegmentCount) {
                if (i != 0 && i != routeSegmentCount - 1) {
                    val isSegmentVisible =
                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[i]?.visible

                    if (isSegmentVisible == false) {
                        continue
                    }
                }

                val bAddItems = fillSegmentViews(i, convertView)

                val iconView = segmentViews.iconView
                val textView = segmentViews.textView
                val travelTimeContainerView = segmentViews.travelTimeContainerView
                val travelTimeValueView = segmentViews.travelTimeValueView
                val travelTimeUnitView = segmentViews.travelTimeUnitView
                val separatorView = segmentViews.separatorView
                var bAddImageView = bAddItems && iconView != null
                var bAddTextView = bAddItems && textView != null
                var bAddTravelTimeContainer = bAddItems && travelTimeContainerView != null
                var bAddSeparatorView = bAddItems && separatorView != null

                if (iconView != null) {
                    val routeSegmentIcon: Bitmap? =
                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[i]?.icon

                    iconView.setImageBitmap(routeSegmentIcon)
                    iconView.adjustViewBounds = true
                } else {
                    bAddImageView = false
                }

                val routeSegmentName =
                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[i]?.name

                var text = ""
                var separator = ""
                if (!routeSegmentName.isNullOrEmpty()) {
                    text = routeSegmentName
                }

                if (text.isNotEmpty()) {
                    text = " $text "
                }

                if (i < routeSegmentCount - 1) {
                    separator = " > "
                }

                if (textView != null) {
                    if (text.isNotEmpty()) {
                        textView.text = text
                        textView.setBackgroundResource(R.drawable.route_text_bgnd)

                        val backgroundColor =
                            GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[i]?.backgroundColor
                        val foregroundColor =
                            GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[i]?.foregroundColor

                        val bgndColorIsWhite = backgroundColor == Color.rgb(255, 255, 255)
                        val fgndColorIsBlack = foregroundColor == Color.rgb(0, 0, 0)

                        var bgndColor: Int?
                        var fgndColor: Int?

                        if (bgndColorIsWhite && fgndColorIsBlack) {
                            bgndColor = Color.argb(0, 255, 255, 255)
                            fgndColor = Color.argb(255, 0, 0, 0)
                        } else {
                            bgndColor = backgroundColor
                            fgndColor = foregroundColor
                        }

                        val drawable = textView.background as GradientDrawable
                        drawable.setColor(bgndColor!!)

                        textView.setTextColor(fgndColor!!)
                    } else {
                        textView.visibility = View.GONE
                        bAddTextView = false
                    }
                } else {
                    bAddTextView = false
                }

                if (text.isEmpty() && travelTimeContainerView != null) {
                    val routeSegmentTravelTimeValue =
                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[i]?.travelTimeValue
                    val routeSegmentTravelTimeUnit =
                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[i]?.travelTimeUnit

                    if (!routeSegmentTravelTimeValue.isNullOrEmpty() &&
                        !routeSegmentTravelTimeUnit.isNullOrEmpty() &&
                        travelTimeValueView != null && travelTimeUnitView != null
                    ) {
                        travelTimeContainerView.visibility = View.VISIBLE
                        travelTimeValueView.text = routeSegmentTravelTimeValue
                        travelTimeUnitView.text = routeSegmentTravelTimeUnit
                    } else {
                        bAddTravelTimeContainer = false
                    }
                } else {
                    bAddTravelTimeContainer = false
                }

                if (separatorView != null) {
                    if (separator.isNotEmpty()) {
                        val separatorIcon = GEMPublicTransportRouteDescriptionView.separatorIcon

                        separatorView.setImageBitmap(separatorIcon)
                    } else {
                        separatorView.visibility = View.GONE
                        bAddSeparatorView = false
                    }
                } else {
                    bAddSeparatorView = false
                }

                if (routeDescriptionContainer != null) {
                    if (bAddImageView) {
                        routeDescriptionContainer.addView(iconView, params1)
                    }

                    if (bAddTextView) {
                        routeDescriptionContainer.addView(textView, params2)
                    }

                    if (bAddTravelTimeContainer) {
                        routeDescriptionContainer.addView(travelTimeContainerView, params2)
                    }

                    if (bAddSeparatorView) {
                        routeDescriptionContainer.addView(separatorView, params3)
                    }
                }
            }

            val walkDistTimeView = convertView.findViewById<TextView>(R.id.trip_walk_dist_time)

            val walkingInfo = GEMPublicTransportRouteDescriptionView.routeItem.walkingInfo
            val fare = GEMPublicTransportRouteDescriptionView.routeItem.fare
            val frequency = GEMPublicTransportRouteDescriptionView.routeItem.frequency
            val tripChanges = GEMPublicTransportRouteDescriptionView.routeItem.numberOfChanges
            val warnings = GEMPublicTransportRouteDescriptionView.routeItem.warning

            if (walkDistTimeView != null) {
                if (walkingInfo.isNotEmpty()) {
                    walkDistTimeView.text = walkingInfo
                } else {
                    walkDistTimeView.visibility = View.GONE
                }
            }

            val tripFareView = convertView.findViewById<TextView>(R.id.trip_fare)
            if (tripFareView != null) {
                if (fare.isNotEmpty()) {
                    tripFareView.text = fare
                } else {
                    tripFareView.visibility = View.GONE
                }
            }

            val tripFrequencyView = convertView.findViewById<TextView>(R.id.trip_frequency)
            if (tripFrequencyView != null) {
                if (frequency.isNotEmpty()) {
                    tripFrequencyView.text = frequency
                } else {
                    tripFrequencyView.visibility = View.GONE
                }
            }

            val tripChangesView = convertView.findViewById<TextView>(R.id.trip_changes)
            if (tripChangesView != null) {
                if (tripChanges.isNotEmpty()) {
                    tripChangesView.text = tripChanges
                } else {
                    tripChangesView.visibility = View.GONE
                }
            }

            val tripWarningView = convertView.findViewById<TextView>(R.id.trip_warning)
            if (tripWarningView != null) {
                if (warnings.isNotEmpty()) {
                    tripWarningView.text = warnings
                } else {
                    tripWarningView.visibility = View.GONE
                }
            }
        }
    }

    private fun initRouteDescriptionListAdapter() {
        routeDescriptionAdapter = object : BaseAdapter() {
            override fun getCount(): Int {
                val agencyText = GEMPublicTransportRouteDescriptionView.agencyText
                val routeSegmentsCount =
                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments.size

                return if (agencyText.isEmpty()) {
                    routeSegmentsCount + 2
                } else {
                    routeSegmentsCount + 3
                }
            }

            override fun getItem(position: Int): Any? {
                return null
            }

            override fun getItemId(position: Int): Long {
                return (position + 1).toLong()
            }

            override fun isEnabled(position: Int): Boolean {
                return false
            }

            @SuppressLint("ClickableViewAccessibility")
            override fun getView(position: Int, convertView: View?, container: ViewGroup): View? {
                var view = routeDescriptionViews!![position]

                if (position == 0) {
                    view = null // otherwise issue with horizontal scroll view ...
                }

                val routeSegmentsCount =
                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments.size
                val agencyText = GEMPublicTransportRouteDescriptionView.agencyText

                if (view == null) {
                    val nItems = routeSegmentsCount + 1
                    val blueLinkColor = Color.rgb(5, 94, 247)

                    var itemIndex = position

                    view = when {
                        (itemIndex > nItems) -> {
                            layoutInflater.inflate(
                                R.layout.pt_route_description_agency_info,
                                container,
                                false
                            )
                        }

                        (itemIndex == nItems) -> {
                            layoutInflater.inflate(
                                R.layout.pt_route_description_item_last,
                                container,
                                false
                            )
                        }

                        (itemIndex == 0) -> {
                            layoutInflater.inflate(R.layout.route_item, container, false)
                        }

                        else -> {
                            layoutInflater.inflate(
                                R.layout.pt_route_description_item,
                                container,
                                false
                            )
                        }
                    }

                    if (view != null) {
                        if (itemIndex > nItems) {
                            val agencyInfo = view.findViewById<TextView>(R.id.agency_info)
                            if (agencyInfo != null) {
                                agencyInfo.text = agencyText
                                agencyInfo.setTextColor(blueLinkColor)

                                agencyInfo.setOnClickListener {
                                    val intent = Intent(
                                        this@PublicTransportRouteDescriptionActivity,
                                        PublicTransportAgenciesActivity::class.java
                                    )
                                    startActivity(intent)
                                }
                            }
                        } else if (itemIndex == nItems) {
                            itemIndex -= 1

                            val stationArrivalTime: String
                            val stopStationName: String

                            if ((itemIndex >= 1) && (itemIndex <= GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts)) {
                                stationArrivalTime =
                                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.arrivalTime!!
                                stopStationName =
                                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.stopStationName!!
                            } else {
                                stationArrivalTime =
                                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.arrivalTime!!
                                stopStationName =
                                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.stopStationName!!
                            }

                            val segmentArriveTimeView =
                                view.findViewById<TextView>(R.id.segment_arrive_time)
                            if (segmentArriveTimeView != null) {
                                segmentArriveTimeView.text = stationArrivalTime

                                segmentArriveTimeView.minWidth =
                                    getTextViewWidth(segmentArriveTimeView, "00:00 AM")
                            }

                            val segmentArriveStationView =
                                view.findViewById<TextView>(R.id.segment_arrive_station)
                            if (segmentArriveStationView != null) {
                                segmentArriveStationView.text = stopStationName
                            }

                            val lineView =
                                view.findViewById<PublicTransportRouteDescriptionLineView>(R.id.segment_line_view)
                            if (lineView != null) {
                                val bWalkingItem =
                                    if ((itemIndex >= 1) && (itemIndex <= GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts)) {
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.isWalk
                                    } else {
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.isWalk
                                    }

                                val segmentIndex = itemIndex - 1
                                val circleColor = Color.BLACK
                                val drawUp = !bWalkingItem!!
                                val lineUpColor = getSegmentLineColor(itemIndex - 1)
                                val drawDown = false
                                val lineDownColor = 0
                                val drawDownDashed = false

                                lineView.setInfo(
                                    segmentIndex,
                                    circleColor,
                                    drawUp,
                                    lineUpColor,
                                    drawDown,
                                    lineDownColor,
                                    drawDownDashed,
                                    0
                                )
                            }
                        } else if (itemIndex == 0) {
                            fillHeaderView(view)
                        } else {
                            val bWalkingItem =
                                if ((itemIndex >= 1) && (itemIndex <= GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts)) {
                                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.isWalk
                                } else {
                                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.isWalk
                                }

                            val darkGray = Color.rgb(128, 128, 128)

                            val routeSegmentIcon: Bitmap? =
                                GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.icon
                            val stationDepartureTime: String
                            val startStationName: String

                            if ((itemIndex >= 1) && (itemIndex <= GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts)) {
                                stationDepartureTime =
                                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.departureTime!!
                                startStationName =
                                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.startStationName!!
                            } else {
                                stationDepartureTime =
                                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.departureTime!!
                                startStationName =
                                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.startStationName!!
                            }

                            val segmentIconView = view.findViewById<ImageView>(R.id.segment_icon)
                            segmentIconView?.setImageBitmap(routeSegmentIcon)

                            val segmentDepartTimeView =
                                view.findViewById<TextView>(R.id.segment_depart_time)
                            if (segmentDepartTimeView != null) {
                                segmentDepartTimeView.text = stationDepartureTime

                                segmentDepartTimeView.minWidth =
                                    getTextViewWidth(segmentDepartTimeView, "00:00 AM")
                                if (segmentIconView != null) {
                                    segmentIconView.maxWidth =
                                        getTextViewWidth(segmentDepartTimeView, "00:00")
                                }
                            }

                            if (!bWalkingItem!!) {
                                val stationArrivalTime =
                                    if ((itemIndex >= 1) && (itemIndex <= GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts)) {
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.arrivalTime
                                    } else {
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.arrivalTime
                                    }

                                val segmentArrivalTimeView =
                                    view.findViewById<TextView>(R.id.segment_arrive_time)
                                if (segmentArrivalTimeView != null) {
                                    segmentArrivalTimeView.text = stationArrivalTime
                                }
                            }

                            val segmentStartStationView =
                                view.findViewById<TextView>(R.id.segment_start_station)
                            if (segmentStartStationView != null) {
                                segmentStartStationView.text = startStationName

                                if (PublicTransportRouteDescriptionLineView.shouldSetCellHeight() ||
                                    PublicTransportRouteDescriptionLineView.shouldSetOffset(
                                        0,
                                        itemIndex - 1
                                    )
                                ) {
                                    segmentStartStationView.doOnPreDraw {
                                        val finalHeight =
                                            segmentStartStationView.measuredHeight.toFloat()

                                        if (PublicTransportRouteDescriptionLineView.shouldSetCellHeight()) {
                                            val nLinesCount = segmentStartStationView.lineCount
                                            PublicTransportRouteDescriptionLineView.setCellHeight(
                                                if (nLinesCount > 1) finalHeight / 2 else finalHeight
                                            )
                                        }

                                        if (PublicTransportRouteDescriptionLineView.shouldSetOffset(
                                                0,
                                                itemIndex - 1
                                            )
                                        ) {
                                            PublicTransportRouteDescriptionLineView.setOffset(
                                                0,
                                                itemIndex - 1,
                                                finalHeight
                                            )
                                        }
                                    }
                                }
                            }

                            val segmentStationTimeInfoView =
                                view.findViewById<TextView>(R.id.segment_station_time_info)
                            if (segmentStationTimeInfoView != null) {
                                val stationTimeInfo: String
                                val stationTimeColor: Int
                                if ((itemIndex >= 1) && (itemIndex <= GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts)) {
                                    stationTimeInfo =
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.stationTimeInfo!!
                                    stationTimeColor =
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.stationTimeInfoColor!!
                                } else {
                                    stationTimeInfo =
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.stationTimeInfo!!
                                    stationTimeColor =
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.stationTimeInfoColor!!
                                }

                                if (stationTimeInfo.isNotEmpty()) {
                                    segmentStationTimeInfoView.text = stationTimeInfo

                                    segmentStationTimeInfoView.setTextColor(stationTimeColor)

                                    if (PublicTransportRouteDescriptionLineView.shouldSetOffset(
                                            1,
                                            itemIndex - 1
                                        )
                                    ) {
                                        segmentStationTimeInfoView.doOnPreDraw {
                                            if (PublicTransportRouteDescriptionLineView.shouldSetOffset(
                                                    1,
                                                    itemIndex - 1
                                                )
                                            ) {
                                                val finalHeight =
                                                    segmentStationTimeInfoView.measuredHeight.toFloat()
                                                PublicTransportRouteDescriptionLineView.setOffset(
                                                    1,
                                                    itemIndex - 1,
                                                    finalHeight
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    segmentStationTimeInfoView.visibility = View.GONE
                                }
                            }

                            val supportLineInfoView =
                                view.findViewById<TextView>(R.id.segment_support_line_info)
                            if (supportLineInfoView != null) {
                                val supportLineInfo =
                                    if ((itemIndex >= 1) && (itemIndex <= GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts)) {
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.supportLineInfo!!
                                    } else {
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.supportLineInfo!!
                                    }

                                if (supportLineInfo.isNotEmpty()) {
                                    supportLineInfoView.text = supportLineInfo

                                    if (PublicTransportRouteDescriptionLineView.shouldSetOffset(
                                            2,
                                            itemIndex - 1
                                        )
                                    ) {
                                        supportLineInfoView.doOnPreDraw {
                                            if (PublicTransportRouteDescriptionLineView.shouldSetOffset(
                                                    2,
                                                    itemIndex - 1
                                                )
                                            ) {
                                                val finalHeight =
                                                    supportLineInfoView.measuredHeight.toFloat()
                                                PublicTransportRouteDescriptionLineView.setOffset(
                                                    2,
                                                    itemIndex - 1,
                                                    finalHeight
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    supportLineInfoView.visibility = View.GONE
                                }
                            }

                            var lineColor = 0

                            if (!bWalkingItem) {
                                val segmentLineNameView =
                                    view.findViewById<TextView>(R.id.segment_line_name)
                                var segmentLineNameWidth = 0
                                if (segmentLineNameView != null) {
                                    val routeSegmentName =
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.name
                                    val backgroundColor =
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.backgroundColor
                                    val foregroundColor =
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.foregroundColor

                                    var lineName: String? = ""

                                    if (routeSegmentName!!.isNotEmpty()) {
                                        lineName = routeSegmentName
                                    }

                                    if (!lineName.isNullOrEmpty()) {
                                        lineName = " $lineName "
                                    }

                                    if (!lineName.isNullOrEmpty()) {
                                        segmentLineNameWidth =
                                            getTextViewWidth(segmentLineNameView, lineName)
                                        segmentLineNameView.text = lineName
                                        segmentLineNameView.setBackgroundResource(R.drawable.route_text_bgnd)

                                        val bgndColorIsWhite =
                                            backgroundColor == Color.rgb(255, 255, 255)
                                        val fgndColorIsBlack = foregroundColor == Color.rgb(0, 0, 0)

                                        val bgndColor: Int
                                        val fgndColor: Int

                                        if (bgndColorIsWhite && fgndColorIsBlack) {
                                            bgndColor = Color.argb(0, 255, 255, 255)
                                            fgndColor = Color.argb(255, 0, 0, 0)
                                            lineColor = fgndColor
                                        } else {
                                            bgndColor = backgroundColor!!
                                            fgndColor = foregroundColor!!
                                            lineColor = bgndColor
                                        }

                                        val drawable =
                                            segmentLineNameView.background as GradientDrawable
                                        drawable.setColor(bgndColor)
                                        segmentLineNameView.setTextColor(fgndColor)
                                    } else {
                                        segmentLineNameView.visibility = View.GONE
                                    }
                                }

                                val segmentTowardsView =
                                    view.findViewById<TextView>(R.id.segment_towards)
                                if (segmentTowardsView != null) {
                                    var towards = ""

                                    val toBLineName =
                                        if ((itemIndex >= 1) && (itemIndex <= GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts)) {
                                            GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.toBLineName
                                        } else {
                                            GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.toBLineName
                                        }

                                    if (toBLineName!!.isNotEmpty()) {
                                        if (segmentLineNameWidth > 0) {
                                            var towardsWidth: Int
                                            do {
                                                towards = " $towards"
                                                towardsWidth =
                                                    getTextViewWidth(segmentTowardsView, towards)
                                            } while (towardsWidth <= segmentLineNameWidth)

                                            towards += toBLineName
                                        }

                                        segmentTowardsView.text = towards

                                        segmentTowardsView.setTextColor(darkGray)

                                        if (PublicTransportRouteDescriptionLineView.shouldSetOffset(
                                                3,
                                                itemIndex - 1
                                            )
                                        ) {
                                            segmentTowardsView.doOnPreDraw {
                                                if (PublicTransportRouteDescriptionLineView.shouldSetOffset(
                                                        3,
                                                        itemIndex - 1
                                                    )
                                                ) {
                                                    val finalHeight =
                                                        segmentTowardsView.measuredHeight.toFloat()
                                                    PublicTransportRouteDescriptionLineView.setOffset(
                                                        3,
                                                        itemIndex - 1,
                                                        finalHeight
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        segmentTowardsView.visibility = View.GONE
                                    }
                                }
                            } else {
                                val segmentTowardsContainerView =
                                    view.findViewById<RelativeLayout>(R.id.segment_towards_container)
                                if (segmentTowardsContainerView != null) {
                                    segmentTowardsContainerView.visibility = View.GONE
                                }
                            }

                            val lineView =
                                view.findViewById<PublicTransportRouteDescriptionLineView>(R.id.segment_line_view)
                            if (lineView != null) {
                                val isWalking = if (itemIndex == 1) {
                                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.isWalk
                                } else if ((itemIndex >= 1) && (itemIndex <= GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts)) {
                                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 2]?.isWalk
                                } else {
                                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.isWalk
                                }

                                val segmentIndex = itemIndex - 1
                                val circleColor = Color.BLACK
                                val drawUp = (itemIndex > 1) && !isWalking!!
                                val lineUpColor = getSegmentLineColor(itemIndex - 2)
                                val drawDown = true
                                val lineDownColor =
                                    if (bWalkingItem) Color.rgb(2, 220, 255) else lineColor

                                val nIntermediatePts =
                                    if (GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.isExpended!!) {
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.numberOfStops!!
                                    } else 0

                                lineView.setInfo(
                                    segmentIndex,
                                    circleColor,
                                    drawUp,
                                    lineUpColor,
                                    drawDown,
                                    lineDownColor,
                                    bWalkingItem,
                                    nIntermediatePts
                                )
                            }

                            val segmentPlatformView =
                                view.findViewById<TextView>(R.id.segment_platform)
                            if (segmentPlatformView != null) {
                                val platform =
                                    if ((itemIndex >= 1) && (itemIndex <= GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts)) {
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.stationPlatform
                                    } else {
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.stationPlatform
                                    }

                                if (platform!!.isNotEmpty()) {
                                    segmentPlatformView.text = platform

                                    if (PublicTransportRouteDescriptionLineView.shouldSetOffset(
                                            4,
                                            itemIndex - 1
                                        )
                                    ) {
                                        segmentPlatformView.doOnPreDraw {
                                            if (PublicTransportRouteDescriptionLineView.shouldSetOffset(
                                                    4,
                                                    itemIndex - 1
                                                )
                                            ) {
                                                val finalHeight =
                                                    segmentPlatformView.measuredHeight.toFloat()
                                                PublicTransportRouteDescriptionLineView.setOffset(
                                                    4,
                                                    itemIndex - 1,
                                                    finalHeight
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    segmentPlatformView.visibility = View.GONE
                                }
                            }

                            val timeToNextStationView =
                                view.findViewById<TextView>(R.id.segment_time_to_next_station)
                            if (timeToNextStationView != null) {
                                val distToNextStation: String
                                val timeToNextStation: String
                                if ((itemIndex >= 1) && (itemIndex <= GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts)) {
                                    distToNextStation =
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.distanceToNextStation!!
                                    timeToNextStation =
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.timeToNextStation!!
                                } else {
                                    distToNextStation =
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.distanceToNextStation!!
                                    timeToNextStation =
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.timeToNextStation!!
                                }

                                var tmp: String? = ""

                                if (distToNextStation.isNotEmpty()) {
                                    tmp = distToNextStation
                                }

                                if (timeToNextStation.isNotEmpty()) {
                                    tmp = if (tmp!!.isNotEmpty()) {
                                        "$tmp ($timeToNextStation)"
                                    } else {
                                        timeToNextStation
                                    }
                                }

                                if (tmp!!.isNotEmpty()) {
                                    timeToNextStationView.visibility = View.VISIBLE
                                    timeToNextStationView.text = tmp

                                    if (!bWalkingItem) {
                                        if (PublicTransportRouteDescriptionLineView.shouldSetOffset(
                                                5,
                                                itemIndex - 1
                                            )
                                        ) {
                                            timeToNextStationView.doOnPreDraw {
                                                if (PublicTransportRouteDescriptionLineView.shouldSetOffset(
                                                        5,
                                                        itemIndex - 1
                                                    )
                                                ) {
                                                    val finalHeight =
                                                        timeToNextStationView.measuredHeight.toFloat()
                                                    PublicTransportRouteDescriptionLineView.setOffset(
                                                        5,
                                                        itemIndex - 1,
                                                        finalHeight
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    val numberOfStops: Int
                                    val instructionListCount: Int
                                    if ((itemIndex >= 1) && (itemIndex <= GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts)) {
                                        numberOfStops =
                                            GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.stopNames?.size!!
                                        instructionListCount =
                                            GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.routeInstructionsList?.size!!
                                    } else {
                                        numberOfStops =
                                            GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.stopNames?.size!!
                                        instructionListCount =
                                            GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.routeInstructionsList?.size!!
                                    }

                                    val stationsContainer =
                                        view.findViewById<LinearLayout>(R.id.segment_stations_container)
                                    if (stationsContainer != null) {
                                        if (GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.isExpended!!) {
                                            stationsContainer.visibility = View.VISIBLE
                                            if (bWalkingItem) {
                                                initPedestrianRouteInstructionsListView(
                                                    itemIndex,
                                                    stationsContainer
                                                )
                                            } else {
                                                initStationsListView(itemIndex, stationsContainer)
                                            }
                                        } else {
                                            stationsContainer.visibility = View.GONE
                                        }
                                    }

                                    if (numberOfStops > 0 || instructionListCount > 0) {
                                        val index = itemIndex - 1
                                        timeToNextStationView.setTextColor(blueLinkColor)

                                        timeToNextStationView.setOnClickListener {}

                                        timeToNextStationView.setOnTouchListener { _, event ->

                                            if (event != null) {
                                                if (event.action == MotionEvent.ACTION_UP) {
                                                    if (abs(event.eventTime - prevTouchDownItemTimeMs1) < 1000 &&
                                                        abs(prevTouchX1 - event.x) < clickThreshold &&
                                                        abs(prevTouchY1 - event.y) < clickThreshold
                                                    ) {
                                                        if (GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[index]?.isExpended == false) {
                                                            GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[index]?.isExpended =
                                                                true
                                                            reloadData()
                                                        } else {
                                                            GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[index]?.isExpended =
                                                                false
                                                            reloadData()
                                                        }
                                                    }
                                                    prevTouchDownItemTimeMs1 = -1
                                                    prevTouchX1 = Float.MIN_VALUE
                                                    prevTouchY1 = Float.MIN_VALUE
                                                } else if (event.action == MotionEvent.ACTION_DOWN) {
                                                    prevTouchDownItemTimeMs1 = event.eventTime
                                                    prevTouchX1 = event.x
                                                    prevTouchY1 = event.y
                                                }
                                            }

                                            false
                                        }
                                    }
                                } else {
                                    timeToNextStationView.visibility = View.GONE
                                }
                            }

                            val segmentStayOnSameVehicleView =
                                view.findViewById<TextView>(R.id.segment_stay_on_same_vehicle)

                            if (segmentStayOnSameVehicleView != null) {
                                val stayOnTheSameVehicle =
                                    if ((itemIndex >= 1) && (itemIndex <= GEMPublicTransportRouteDescriptionView.routeItem.segmentCounts)) {
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.stayOnSameVehicle
                                    } else {
                                        GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex]?.stayOnSameVehicle
                                    }

                                if (stayOnTheSameVehicle!!.isNotEmpty()) {
                                    segmentStayOnSameVehicleView.text = stayOnTheSameVehicle
                                } else {
                                    segmentStayOnSameVehicleView.visibility = View.GONE
                                }
                            }
                        }

                        if (itemIndex > 0) {
                            view.setOnClickListener {}

                            view.setOnTouchListener { _, event ->
                                if (event.action == MotionEvent.ACTION_UP) {
                                    if (abs(event.eventTime - prevTouchDownItemTimeMs2) < 1000 &&
                                        abs(prevTouchX2 - event.x) < clickThreshold &&
                                        abs(prevTouchY2 - event.y) < clickThreshold
                                    ) {
                                        didTapItem(itemIndex)
                                    }

                                    prevTouchDownItemTimeMs2 = -1
                                    prevTouchX2 = java.lang.Float.MIN_VALUE
                                    prevTouchY2 = java.lang.Float.MIN_VALUE
                                } else if (event.action == MotionEvent.ACTION_DOWN) {
                                    prevTouchDownItemTimeMs2 = event.eventTime
                                    prevTouchX2 = event.x
                                    prevTouchY2 = event.y
                                }

                                false
                            }
                        }
                    }

                    routeDescriptionViews!![position] = view
                }

                return view
            }
        }
    }

    internal fun getSegmentLineColor(segmentIndex: Int): Int {
        var lineColor = 0
        if (segmentIndex >= 0) {
            val backgroundColor =
                GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[segmentIndex]?.backgroundColor
            val foregroundColor =
                GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[segmentIndex]?.foregroundColor

            val bgndColorIsWhite = backgroundColor == Color.rgb(255, 255, 255)
            val fgndColorIsBlack = foregroundColor == Color.rgb(0, 0, 0)

            lineColor = if (bgndColorIsWhite && fgndColorIsBlack) {
                Color.argb(255, 0, 0, 0)
            } else {
                backgroundColor!!
            }
        }
        return lineColor
    }

    fun reloadData() {
        if (routeDescriptionAdapter != null && routeDescriptionViews != null) {
            routeDescriptionViews!!.clear()

            val nItemsCount = GEMPublicTransportRouteDescriptionView.itemsCount + 1
            for (i in 0 until nItemsCount) {
                routeDescriptionViews!!.add(null as View?)
            }

            routeDescriptionAdapter!!.notifyDataSetChanged()
        }
    }

    private fun getTextViewWidth(textView: TextView?, text: String?): Int {
        if (textView != null && text != null && text.isNotEmpty()) {
            val textPaint = textView.paint
            if (textPaint != null) {
                return textPaint.measureText(text).roundToInt()
            }
        }
        return 0
    }

    @SuppressLint("ClickableViewAccessibility")
    internal fun initStationsListView(itemIndex: Int, stationsContainer: LinearLayout?) {
        if (stationsContainer == null) {
            return
        }

        val params = LinearLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )

        params.gravity = Gravity.CENTER_VERTICAL

        val nStopsCount =
            GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.numberOfStops!!

        for (i in 0 until nStopsCount) {
            var textView: TextView?

            val stationItem = View.inflate(
                this,
                R.layout.pt_route_description_station_item,
                null
            ) as ConstraintLayout

            textView = stationItem.findViewById(R.id.segment_station_name)
            stationItem.removeAllViews()

            if (textView != null) {
                val text =
                    GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.stopNames!![i]

                if (text.isNullOrEmpty()) {
                    continue
                }

                textView.text = text
                stationsContainer.addView(textView, params)

                if (PublicTransportRouteDescriptionLineView.shouldSetCellHeightExt()) {
                    textView.doOnPreDraw {
                        if (PublicTransportRouteDescriptionLineView.shouldSetCellHeightExt()) {
                            val finalHeight = it.measuredHeight.toFloat()
                            PublicTransportRouteDescriptionLineView.setCellHeightExt(finalHeight)
                        }
                    }
                }
            }
        }

        stationsContainer.setOnClickListener {}

        stationsContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (abs(event.eventTime - prevTouchDownItemTimeMs3) < 1000 &&
                    abs(prevTouchX3 - event.x) < clickThreshold &&
                    abs(prevTouchY3 - event.y) < clickThreshold
                ) {
                    didTapItem(itemIndex)
                    reloadData()
                }

                prevTouchDownItemTimeMs3 = -1
                prevTouchX3 = java.lang.Float.MIN_VALUE
                prevTouchY3 = java.lang.Float.MIN_VALUE
            } else if (event.action == MotionEvent.ACTION_DOWN) {
                prevTouchDownItemTimeMs3 = event.eventTime
                prevTouchX3 = event.x
                prevTouchY3 = event.y
            }

            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    internal fun initPedestrianRouteInstructionsListView(
        itemIndex: Int,
        routeInstructionsContainer: LinearLayout?
    ) {
        if (routeInstructionsContainer == null) {
            return
        }

        val params = LinearLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        )

        params.gravity = Gravity.CENTER_VERTICAL

        val nInstructionsCount =
            GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.routeInstructionsList!!.size

        for (i in 0 until nInstructionsCount) {
            var imageView: ImageView?
            var simpleTextView: TextView?
            var detailTextView: TextView?
            var simpleStatusTextView: TextView?
            var detailStatusTextView: TextView?

            val textSize =
                GEMApplication.appResources().getDimension(R.dimen.route_list_font_size_medium)

            val genericListItem =
                View.inflate(this, R.layout.icon_text_status_list_item, null) as ConstraintLayout
            simpleTextView = genericListItem.findViewById(R.id.text)
            detailTextView = genericListItem.findViewById(R.id.description)
            simpleStatusTextView = genericListItem.findViewById(R.id.status_text)
            detailStatusTextView = genericListItem.findViewById(R.id.status_description)
            imageView = genericListItem.findViewById(R.id.icon)

            simpleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            detailTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize - 2)
            simpleStatusTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            detailStatusTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize - 2)

            val layoutParams = imageView.layoutParams
            if (layoutParams != null) {
                layoutParams.width = GEMApplication.appResources()
                    .getDimension(R.dimen.route_list_instr_icon_size).toInt()
                layoutParams.height = GEMApplication.appResources()
                    .getDimension(R.dimen.route_list_instr_icon_size).toInt()
                imageView.layoutParams = layoutParams
            }

            val icon =
                GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.routeInstructionsList!![i]?.icon
            imageView.setImageBitmap(icon)

            val simpleText =
                GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.routeInstructionsList!![i]?.simpleText
            val detailText =
                GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.routeInstructionsList!![i]?.detailText
            simpleTextView.text = simpleText
            detailTextView.text = detailText

            if (detailText.isNullOrEmpty()) {
                detailTextView.visibility = View.GONE
            }

            val simpleStatusText =
                GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.routeInstructionsList!![i]?.simpleStatusText
            val detailStatusText =
                GEMPublicTransportRouteDescriptionView.routeItem.tripSegments[itemIndex - 1]?.routeInstructionsList!![i]?.detailStatusText
            simpleStatusTextView.text = simpleStatusText
            detailStatusTextView.text = detailStatusText

            if (detailStatusText.isNullOrEmpty()) {
                detailStatusTextView.visibility = View.GONE
            }

            if (i > 0) {
                val divider = genericListItem.findViewById<View>(R.id.divider)
                if (divider != null) {
                    divider.visibility = View.VISIBLE
                }
            }

            routeInstructionsContainer.addView(genericListItem, params)
        }

        routeInstructionsContainer.setOnClickListener {}

        routeInstructionsContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (abs(event.eventTime - prevTouchDownItemTimeMs3) < 1000 &&
                    abs(prevTouchX3 - event.x) < clickThreshold &&
                    abs(prevTouchY3 - event.y) < clickThreshold
                ) {
                    didTapItem(itemIndex)
                    reloadData()
                }

                prevTouchDownItemTimeMs3 = -1
                prevTouchX3 = java.lang.Float.MIN_VALUE
                prevTouchY3 = java.lang.Float.MIN_VALUE
            } else if (event.action == MotionEvent.ACTION_DOWN) {
                prevTouchDownItemTimeMs3 = event.eventTime
                prevTouchX3 = event.x
                prevTouchY3 = event.y
            }

            false
        }
    }

    private fun fillSegmentViews(segmentIndex: Int, convertView: View?): Boolean {
        segmentViews.reset()

        if (segmentIndex == 0) {
            if (convertView != null) {
                segmentViews.iconView = convertView.findViewById(R.id.transport_mean_icon)
                segmentViews.textView = convertView.findViewById(R.id.transport_mean_text)
                segmentViews.travelTimeContainerView =
                    convertView.findViewById(R.id.transport_travel_time_container)
                segmentViews.travelTimeValueView =
                    convertView.findViewById(R.id.transport_travel_time_value)
                segmentViews.travelTimeUnitView =
                    convertView.findViewById(R.id.transport_travel_time_unit)
                segmentViews.separatorView = convertView.findViewById(R.id.transport_mean_separator)
            }
        } else {
            val routeItemExtra =
                View.inflate(this, R.layout.route_item_extra, null) as LinearLayout?
            if (routeItemExtra != null) {
                segmentViews.iconView = routeItemExtra.findViewById(R.id.transport_mean_extra_icon)
                segmentViews.textView = routeItemExtra.findViewById(R.id.transport_mean_extra_text)
                segmentViews.travelTimeContainerView =
                    routeItemExtra.findViewById(R.id.transport_travel_time_extra_container)
                segmentViews.travelTimeValueView =
                    routeItemExtra.findViewById(R.id.transport_travel_extra_time_value)
                segmentViews.travelTimeUnitView =
                    routeItemExtra.findViewById(R.id.transport_travel_extra_time_unit)
                segmentViews.separatorView =
                    routeItemExtra.findViewById(R.id.transport_mean_extra_separator)
                routeItemExtra.removeAllViews()

                return true
            }
        }

        return false
    }

    fun didTapItem(index: Int) {
        val itemIndex = index - 1
        if (itemIndex >= 0 && itemIndex < GEMPublicTransportRouteDescriptionView.m_routeDescriptionItems.size) {
            val item = GEMPublicTransportRouteDescriptionView.m_routeDescriptionItems[itemIndex]
            val area = item.m_geographicArea
            if (area != null) {
                GEMApplication.getMainMapView()?.centerOnRectArea(area)
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        fun showPTRouteDescription(context: Context, route: Route) {
            // AVOID SERIALIZABLE 
            IntentHelper.addObjectForKey(route, EXTRA_ROUTE)

            val intent = Intent(context, PublicTransportRouteDescriptionActivity::class.java)
            context.startActivity(intent)
        }
    }
}
