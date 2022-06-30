/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.androidauto.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.d3scene.MapView
import com.generalmagic.sdk.examples.androidauto.R
import com.generalmagic.sdk.examples.androidauto.activities.controllers.MainActivityController

class MainActivity : BaseActivity() {
    private val controller = MainActivityController(this)

    private var gemSurfaceView: GemSurfaceView? = null
    lateinit var progressBar: ProgressBar

    val mapView: MapView?
        get() = gemSurfaceView?.mapView

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectControls()

        controller.onCreate()

        gemSurfaceView?.onDefaultMapViewCreated = {
            controller.onDefaultMapViewCreated()
        }
    }

    private fun connectControls() {
        progressBar = findViewById(R.id.progressBar)
//        gemSurfaceView = findViewById(R.id.gem_surface)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onDestroy() {
        super.onDestroy()
        controller.onDestroy()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBackPressed() {
        controller.onBackPressed()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        controller.onRequestPermissionsResult(requestCode, grantResults)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent ?: return
        controller.onNewIntent(intent)
    }
}
