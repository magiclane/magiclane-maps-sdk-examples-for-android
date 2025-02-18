// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.basicshapedrawer

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

// -------------------------------------------------------------------------------------------------------------------------------
object Utils
{
    // ---------------------------------------------------------------------------------------------------------------------------
    @SuppressLint("InflateParams")
    fun showDialog(text: String, activity: FragmentActivity)
    {
        activity.run {
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
    // ---------------------------------------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------------------------------------
