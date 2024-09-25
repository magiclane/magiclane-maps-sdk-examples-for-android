// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.weather

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

// -------------------------------------------------------------------------------------------------

object Utils
{
    // ---------------------------------------------------------------------------------------------

    @SuppressLint("InflateParams")
    fun showDialog(text: String, activityRef : AppCompatActivity)
    {
        activityRef.run{
            val dialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.dialog_layout, null).apply {
                findViewById<TextView>(R.id.title).text = getString(R.string.error)
                findViewById<TextView>(R.id.message).text = text
                findViewById<Button>(R.id.button).setOnClickListener {
                    dialog.dismiss()
                }
            }
            dialog.apply {
                setCancelable(false)
                setContentView(view)
                show()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------
