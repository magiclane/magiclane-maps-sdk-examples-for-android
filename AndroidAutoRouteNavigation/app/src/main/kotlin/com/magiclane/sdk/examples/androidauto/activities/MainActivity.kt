/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidauto.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.examples.androidauto.R
import com.magiclane.sdk.examples.androidauto.activities.controllers.MainActivityController

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

        onBackPressedDispatcher.addCallback(this /* lifecycle owner */, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                controller.onBackPressed()
            }
        })
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
