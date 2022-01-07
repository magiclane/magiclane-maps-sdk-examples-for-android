/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.hellofragmentcustomstyle

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.examples.R
import com.generalmagic.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // This step of initialization is mandatory if you want to use the SDK without a map.
        if (!GemSdk.initSdkWithDefaults(this)) {
            // The SDK initialization was not completed.
            finish()
        }

        if (!Util.isInternetConnected(this)) {
            Toast.makeText(this, "You must be connected to internet!", Toast.LENGTH_LONG).show()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onDestroy() {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBackPressed() {
        finish()
        exitProcess(0)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
