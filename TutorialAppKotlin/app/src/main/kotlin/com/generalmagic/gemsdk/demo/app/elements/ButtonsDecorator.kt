/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.app.elements

import android.content.Context
import android.os.Build
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.generalmagic.gemsdk.demo.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

object ButtonsDecorator {
    fun buttonAsFollowGps(context: Context, button: FloatingActionButton?, action: () -> Unit) {
        button ?: return

        val tag = "gps"
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_gps_fixed_white_24dp)
        val backgroundTintList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.resources.getColorStateList(R.color.colorPrimary, null)
        } else {
            AppCompatResources.getColorStateList(context, R.color.colorPrimary)
        }

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }

    fun buttonAsStart(context: Context, button: FloatingActionButton?, action: () -> Unit) {
        button ?: return

        val tag = "start"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.colorGreenFull)

        val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_media_play)

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }

    fun buttonAsOk(context: Context, button: FloatingActionButton?, action: () -> Unit) {
        button ?: return

        val tag = "ok"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.colorGreenFull)
        val drawable = ContextCompat.getDrawable(
            context, android.R.drawable.checkbox_on_background
        )

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }

    fun buttonAsStop(context: Context, button: FloatingActionButton?, action: () -> Unit) {
        button ?: return

        val tag = "stop"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.colorRedFull)
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_close_gray_24dp)

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }

    fun buttonAsAdd(context: Context, button: FloatingActionButton?, action: () -> Unit) {
        button ?: return

        val tag = "add"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.colorGreenFull)
        val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_input_add)

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }

    fun buttonAsDelete(context: Context, button: FloatingActionButton?, action: () -> Unit) {
        button ?: return

        val tag = "delete"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.colorRedFull)
        val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_delete)

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }

    fun buttonAsInfo(context: Context, button: FloatingActionButton?, action: () -> Unit) {
        button ?: return

        val tag = "info"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.colorLightBlue)
        val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_info)

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }

    fun buttonAsPickStart(
        context: Context, button: FloatingActionButton?, action: () -> Unit
    ) {
        button ?: return

        val tag = "start"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.colorGreenFull)
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_place_green_24dp)

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }

    fun buttonAsPickIntermediate(
        context: Context, button: FloatingActionButton?, action: () -> Unit
    ) {
        button ?: return

        val tag = "intermediate"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, android.R.color.darker_gray)
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_place_gray_24dp)

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }

    fun buttonAsPickDestination(
        context: Context, button: FloatingActionButton?, action: () -> Unit
    ) {
        button ?: return

        val tag = "destination"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.colorRedFull)
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_place_red_24dp)

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }
}
