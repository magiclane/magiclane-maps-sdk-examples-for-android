/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.controllers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import com.generalmagic.gemsdk.Debug
import com.generalmagic.gemsdk.GemString
import com.generalmagic.gemsdk.TCustomUrlService
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.custom_server_bottom_dialog.view.*

class CustomServerUrl(context: Context, attrs: AttributeSet?) :
    AppLayoutController(context, attrs) {

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        positiveButton.setOnClickListener {
            onSubmitPressed()
        }

        negativeButton.setOnClickListener {
            onCancelPressed()
        }
    }

    override fun onBackPressed(): Boolean {
        if (visibility == View.GONE) return false
        visibility = View.GONE
        return true
    }

    private fun onCancelPressed() {
        visibility = View.GONE
    }

    private fun onSubmitPressed() {
        val url = input.text.toString()

        GEMSdkCall.execute {
            Debug().setCustomUrl(TCustomUrlService.EAnnounce, GemString(url))
        }

        Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
        visibility = View.GONE
    }
}
