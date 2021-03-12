/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.example.hellosdk.R
import com.generalmagic.example.util.SdkInitHelper
import com.generalmagic.example.util.Util

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /// General magic
        if (!SdkInitHelper.init(this)) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        SdkInitHelper.deinit()
    }

    override fun onBackPressed() {
        Util.terminateApp(this)
    }
}
