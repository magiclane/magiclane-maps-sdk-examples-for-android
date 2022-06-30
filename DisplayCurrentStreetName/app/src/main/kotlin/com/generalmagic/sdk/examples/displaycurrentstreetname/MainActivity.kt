/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.displaycurrentstreetname

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.examples.displaycurrentstreetname.R
import com.generalmagic.sdk.sensordatasource.DataSource
import com.generalmagic.sdk.sensordatasource.DataSourceFactory
import com.generalmagic.sdk.sensordatasource.DataSourceListener
import com.generalmagic.sdk.sensordatasource.ImprovedPositionData
import com.generalmagic.sdk.sensordatasource.enums.EDataType
import com.generalmagic.sdk.util.PermissionsHelper
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var currentStreetNameView: TextView
    private lateinit var followCursorButton: FloatingActionButton

    private var dataSource: DataSource? = null
    private val dataSourceListener = object : DataSourceListener() {
        override fun onNewData(dataType: EDataType) {
            if (dataType != EDataType.ImprovedPosition)
                return

            var text = ""
            SdkCall.execute execute@{
                val lastData = dataSource?.getLatestData(dataType) ?: return@execute

                val improvedPositionData = ImprovedPositionData(lastData)
                val roadAddress = improvedPositionData.roadAddress ?: return@execute

                roadAddress.format()?.let let@{
                    val df = DecimalFormat("#.##")
                    df.roundingMode = RoundingMode.CEILING

                    if (it.isEmpty())
                        return@let

                    text = it
                    text += ", ${df.format(improvedPositionData.roadSpeedLimit)}"
                    text += ", ${improvedPositionData.roadModifier}"
                }
            }
            currentStreetNameView.text = text
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gemSurfaceView = findViewById(R.id.gem_surface)
        currentStreetNameView = findViewById(R.id.current_street_name)
        followCursorButton = findViewById(R.id.followCursor)

        gemSurfaceView.onDefaultMapViewCreated = {
            enableGPSButton()
        }

        SdkSettings.onMapDataReady = {
            val hasPermissions = PermissionsHelper.hasPermissions(this, permissions)

            if (hasPermissions) {
                SdkCall.execute {
                    startImprovedPositionListener()
                }
            } else {
                requestPermissions(this)
            }
        }

        if (!Util.isInternetConnected(this)) {
            Toast.makeText(this, "You must be connected to internet!", Toast.LENGTH_LONG).show()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onBackPressed() {
        finish()
        exitProcess(0)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun startImprovedPositionListener() {
        if (gemSurfaceView.mapView?.isFollowingPosition() != true)
            gemSurfaceView.mapView?.followPosition()

        if (dataSource == null)
            dataSource = DataSourceFactory.produceLive()

        dataSource?.addListener(dataSourceListener, EDataType.ImprovedPosition)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        gemSurfaceView.mapView?.apply {
            onExitFollowingPosition = {
                Util.postOnMain { followCursorButton.visibility = View.VISIBLE }
            }

            onEnterFollowingPosition = {
                Util.postOnMain { followCursorButton.visibility = View.GONE }
            }

            // Set on click action for the GPS button.
            followCursorButton.setOnClickListener {
                SdkCall.execute { followPosition() }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                finish()
                exitProcess(0)
            }
        }

        SdkCall.execute {
            // Notice permission status had changed
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)

            startImprovedPositionListener()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private val permissions = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private fun requestPermissions(activity: Activity): Boolean {
        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS, activity, permissions
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
