/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.demo.activities.mainactivity.controllers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import com.generalmagic.sdk.core.Debug
import com.generalmagic.sdk.core.ECustomUrlService
import com.generalmagic.sdk.core.GemString
import com.generalmagic.sdk.demo.app.MapLayoutController
import com.generalmagic.sdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.custom_server_bottom_dialog.view.*

class CustomServerUrl(context: Context, attrs: AttributeSet?) :
    MapLayoutController(context, attrs) {

    override fun onCreated() {
        super.onCreated()

        hideAllButtons()

        positiveButton.setOnClickListener {
            onSubmitPressed()
        }

        negativeButton.setOnClickListener {
            onCancelPressed()
        }
    }

    override fun onMapFollowStatusChanged(following: Boolean) {}

    override fun doBackPressed(): Boolean {
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
            Debug().setCustomUrl(ECustomUrlService.EAnnounce, GemString(url))
        }

        Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
        visibility = View.GONE
    }
}
