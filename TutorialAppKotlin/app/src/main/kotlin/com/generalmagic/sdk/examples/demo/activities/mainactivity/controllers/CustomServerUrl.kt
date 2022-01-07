/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.app.MapLayoutController

class CustomServerUrl(context: Context, attrs: AttributeSet?) :
    MapLayoutController(context, attrs) {
    
    private lateinit var positiveButton: Button
    private lateinit var negativeButton: Button

    override fun onCreated() {
        super.onCreated()

        positiveButton = findViewById(R.id.positiveButton)
        negativeButton = findViewById(R.id.negativeButton)
        
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
//        val url = input.text.toString()

//        SdkCall.execute {
//            Debug().setCustomUrl(ECustomUrlService.Announce, GemString(url))
//        }

        Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
        visibility = View.GONE
    }
}
