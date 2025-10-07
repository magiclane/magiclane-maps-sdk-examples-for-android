// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.hellosdk

// -------------------------------------------------------------------------------------------------------------------------------

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    override fun onCreate(savedInstanceState: Bundle?) 
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            Toast.makeText(this, "TOKEN REJECTED", Toast.LENGTH_LONG).show()
        }

        if (!GemSdk.initSdkWithDefaults(this)) 
        {
            // The SDK initialization was not completed.
            finish()
        }

        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true){
            override fun handleOnBackPressed()
            {
                finish()
                exitProcess(0)
            }
        })
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy() 
    {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------