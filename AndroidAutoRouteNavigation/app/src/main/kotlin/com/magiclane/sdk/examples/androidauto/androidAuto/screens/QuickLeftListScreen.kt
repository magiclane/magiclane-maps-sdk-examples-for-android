/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.magiclane.sdk.examples.androidauto.androidAuto.screens

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.PlaceListNavigationTemplate
import com.magiclane.sdk.examples.androidauto.androidAuto.util.Util
import com.magiclane.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import kotlin.math.roundToInt

abstract class QuickLeftListScreen(context: CarContext) : GemScreen(context) {
    var title: String = ""
    var headerAction: UIActionModel? = null
    var noDataText: String = ""
    var listItemModelList: ArrayList<GenericListItemModel> = arrayListOf()
    var actionStripModelList: ArrayList<UIActionModel> = arrayListOf()

    init {
        isMapVisible = true
    }

    open fun onListItemsVisibilityChanged(startIndex: Int, endIndex: Int) {}

    override fun onGetTemplate(): Template {
        updateData()

        val builder = PlaceListNavigationTemplate.Builder()

        val headerBuilder = Header.Builder()
            .setTitle(title)

        headerAction?.let { headerAction ->
            UIActionModel.createAction(context, headerAction)?.let { action -> headerBuilder.setStartHeaderAction(action) }
        }
        
        builder.setHeader(headerBuilder.build())

        Util.getActionStrip(context, actionStripModelList)?.let { builder.setActionStrip(it) }

        builder.setLoading(isLoading)
        if (!isLoading) {
            val itemsListBuilder = ItemList.Builder()

            for (model in listItemModelList) {
                itemsListBuilder.addItem(createRow(context, model))
            }

            itemsListBuilder.setNoItemsMessage(noDataText)

            itemsListBuilder.setOnItemsVisibilityChangedListener { startIndex, endIndex ->
                onListItemsVisibilityChanged(startIndex, endIndex)
            }

            builder.setItemList(itemsListBuilder.build())
        }

        return builder.build()
    }

    companion object {
        private fun createRow(
            context: CarContext,
            value: GenericListItemModel
        ): Row {
            val builder = Row.Builder()
            builder.setTitle(value.title)

            value.isBrowsable?.let { builder.setBrowsable(it) }

            value.getCarIcon(context)?.let { builder.setImage(it) }
            value.onClicked?.let { builder.setOnClickListener { it() } }

            // description

            val distance: Distance? = when {
                value.distanceInMeters == -1 -> null
                value.distanceInMeters >= 1000 -> {
                    Distance.create(
                        (value.distanceInMeters / 1000.0f).toDouble(),
                        Distance.UNIT_KILOMETERS
                    )
                }
                else -> {
                    val roundedDist = (value.distanceInMeters / 50.0f).roundToInt() * 50
                    Distance.create(roundedDist.toDouble(), Distance.UNIT_METERS)
                }
            }

            val description: SpannableString? =
                if (distance != null) {
                    val result = if (value.description.isNotEmpty())
                        SpannableString("· · ${value.description}")
                    else
                        SpannableString("·")

                    result.setSpan(
                        DistanceSpan.create(distance), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    )

                    result
                } else null

            description?.let { builder.addText(description) }

            // metadata
            val lat = value.lat
            val lon = value.lon
            if (lat != null && lon != null) {
                val place = Place.Builder(CarLocation.create(lat, lon))

                if (value.icon == null && (value.markerLabel != null || value.markerIcon != null)) {
                    val placeMarker = PlaceMarker.Builder()
                    value.markerLabel?.let { placeMarker.setLabel(it) }
                    value.getCarMarkerIcon(context)
                        ?.let { placeMarker.setIcon(it, PlaceMarker.TYPE_ICON) }

                    place.setMarker(placeMarker.build())
                }

                val metadata = Metadata.Builder()
                metadata.setPlace(place.build())
                builder.setMetadata(metadata.build())
            }

            return builder.build()
        }
    }
}
