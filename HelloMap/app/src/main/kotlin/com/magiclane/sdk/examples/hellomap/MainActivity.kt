// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.hellomap

// -------------------------------------------------------------------------------------------------------------------------------

import android.graphics.Color
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.BasicShapeDrawer
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val gemSurface = findViewById<GemSurfaceView>(R.id.gem_surface)
        SdkSettings.onMapDataReady = {
            gemSurface.onDrawFrameCustom = { _ ->
                SdkCall.execute {
                    gemSurface.gemScreen?.canvases?.let {
                        assert(it.size > 0) { it.size }
                        val canvas = it[0]
                        val bsd = BasicShapeDrawer.produce(canvas)
                        assert(bsd != null) { "Basic Shape Drawer is null" }
                        bsd?.drawRectangle(10f, 10f, 200f, 400f, Color.BLACK, true, 2f)
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true)
        {
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
