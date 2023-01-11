/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.magiclane.sdk.examples.androidauto.androidAuto.model

import android.graphics.Bitmap
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Toggle
import com.magiclane.sdk.examples.androidauto.androidAuto.util.Util

typealias OnToggleChanged = ((Boolean) -> Unit)

data class GenericListItemModel(
    var title: String = "",
    var icon: Bitmap? = null,
    var iconId: Int? = null,
    var description: String = "",
    var distanceInMeters: Int = -1,
    var lat: Double? = null,
    var lon: Double? = null,
    var markerLabel: String? = null,
    var markerIcon: Bitmap? = null,
    var isBrowsable: Boolean? = null,

    var onClicked: OnClickAction? = null,
    var hasToggle: Boolean? = null,
    var isToggleChecked: Boolean? = null,
    var onToggleChanged: OnToggleChanged? = null,

    internal var carIcon: CarIcon? = null,
    internal var carMarkerIcon: CarIcon? = null,
) {
    internal fun getCarIcon(context: CarContext): CarIcon? {
        if (carIcon == null)
            carIcon = Util.asCarIcon(context, icon)

        if (carIcon == null) {
            iconId?.let { iconId ->
                carIcon = Util.getDrawableIcon(context, iconId)
            }
        }
        return carIcon
    }

    internal fun getCarMarkerIcon(context: CarContext): CarIcon? {
        if (carMarkerIcon == null)
            carMarkerIcon = Util.asCarIcon(context, markerIcon)
        return carMarkerIcon
    }

    fun createPlace(context: CarContext): Place? {
        val lat = lat ?: return null
        val lon = lon ?: return null

        try {
            val location = CarLocation.create(lat, lon)

            val builder = Place.Builder(location)

            val markerBuilder = PlaceMarker.Builder()

            Util.asCarIcon(context, icon)?.let {
                markerBuilder.setIcon(it, PlaceMarker.TYPE_ICON)
            }
            builder.setMarker(markerBuilder.build())

            return builder.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    fun createToggle(): Toggle {
        val isChecked = isToggleChecked == true

        val listener = Toggle.OnCheckedChangeListener { checked ->
            onToggleChanged?.let { it(checked) }
        }

        val builder = Toggle.Builder(listener)
        builder.setChecked(isChecked)
        return builder.build()
    }
}