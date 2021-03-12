/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.example.util

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.generalmagic.example.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ButtonsDecorator {
    companion object {
        fun buttonAsAdd(context: Context, button: FloatingActionButton?, action: () -> Unit) {
            button ?: return

            val tag = "add"
            val backgroundTintList =
                AppCompatResources.getColorStateList(context, R.color.green)
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
                AppCompatResources.getColorStateList(context, R.color.red)
            val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_delete)

            button.tag = tag
            button.setOnClickListener { action() }
            button.setImageDrawable(drawable)
            button.backgroundTintList = backgroundTintList
        }
    }
}
