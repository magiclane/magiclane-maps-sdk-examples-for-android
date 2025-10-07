// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.hellofragment

// -------------------------------------------------------------------------------------------------------------------------------

import android.os.Bundle
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.core.GemSdk
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
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
