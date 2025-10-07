// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.mapcompass

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.sensordatasource.*
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.GemCall
import com.magiclane.sdk.util.Util
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------
    
    private lateinit var surfaceView: GemSurfaceView
    private lateinit var compass: ImageView
    private lateinit var btnEnableLiveHeading: FloatingActionButton
    private lateinit var statusText: TextView
    private var isLiveHeadingEnabled = AtomicBoolean(false)

    private var dataSource: DataSource? = null

    private var dataSourceListener = object : DataSourceListener()
    {
        override fun onNewData(data: SenseData)
        {
            GemCall.execute {
                // smooth new compass data
                val heading = headingSmoother.update(CompassData(data).heading)

                // update map view based on the recent changes
                surfaceView.mapView?.preferences?.rotationAngle = heading
            }
        }
    }

    val headingSmoother = HeadingSmoother()

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        increment()

        surfaceView = findViewById(R.id.gem_surface)
        compass = findViewById(R.id.compass)
        btnEnableLiveHeading = findViewById(R.id.btn_enable_live_heading)
        statusText = findViewById(R.id.status_text)

        // start stop btn
        buttonAsStart(this, btnEnableLiveHeading)

        btnEnableLiveHeading.setOnClickListener {
            isLiveHeadingEnabled.set(!isLiveHeadingEnabled.get())

            if (isLiveHeadingEnabled.get())
            {
                buttonAsStop(this, btnEnableLiveHeading)
                statusText.text = getString(R.string.live_heading_enabled)
            }
            else
            {
                buttonAsStart(this, btnEnableLiveHeading)
                statusText.text = getString(R.string.live_heading_disabled)
            }

            GemCall.execute {
                if (isLiveHeadingEnabled.get())
                {
                    startLiveHeading()
                }
                else
                {
                    stopLiveHeading()
                }
            }
        }

        // compass sync with mapView's rotation angle
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            if(!mainActivityIdlingResource.isIdleNow)
                decrement()
            
            compass.visibility = View.VISIBLE
            btnEnableLiveHeading.visibility = View.VISIBLE
            statusText.visibility = View.VISIBLE
            
            // Get the map view.
            surfaceView.mapView?.let { mapView ->
                // Change the compass icon rotation based on the map rotation at rendering.
                mapView.onMapAngleUpdated = {
                    compass.rotation = -it.toFloat()
                }
                
                // Align the map to north if the compass icon is pressed. 
                compass.setOnClickListener {
                    GemCall.execute {
                        mapView.alignNorthUp(Animation(EAnimation.Linear, 300))
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
            compass.visibility = View.GONE
            btnEnableLiveHeading.visibility = View.GONE
            statusText.visibility = View.GONE
        }

        onBackPressedDispatcher.addCallback(this){
            finish()
            exitProcess(0)
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Will start listening for compass data. Compass's data needs to be smoothed by [HeadingSmoother].
     * The result, as rotation angle, will be applied to the map view.
     */
    private fun startLiveHeading() = GemCall.execute {
        dataSource = DataSourceFactory.produceLive()

        // start listening for compass data
        dataSource?.addListener(dataSourceListener, EDataType.Compass)
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Will stop listening for compass data.
     */
    private fun stopLiveHeading() = GemCall.execute {
        dataSource?.let {
            it.removeListener(dataSourceListener)
            it.release()
            dataSource = null
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------

    private fun buttonAsStart(context: Context, button: FloatingActionButton?)
    {
        button ?: return

        val tag = "start"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.green)
        val drawable = ContextCompat.getDrawable(
            context, android.R.drawable.ic_media_play
        )

        button.tag = tag
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }

    // ---------------------------------------------------------------------------------------------

    private fun buttonAsStop(context: Context, button: FloatingActionButton?)
    {
        button ?: return

        val tag = "stop"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.red)
        val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_media_pause)

        button.tag = tag
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }

    // ---------------------------------------------------------------------------------------------

    @SuppressLint("InflateParams")
    private fun showDialog(text: String)
    {
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

    //region members for testing
    companion object
    {
        const val RESOURCE = "GLOBAL"
    }

    private var mainActivityIdlingResource = CountingIdlingResource(RESOURCE, true)
    //endregion
    //region --------------------------------------------------FOR TESTING--------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------------------------------------

    @VisibleForTesting
    fun getActivityIdlingResource(): IdlingResource
    {
        return mainActivityIdlingResource
    }

    // ---------------------------------------------------------------------------------------------
    private fun increment() = mainActivityIdlingResource.increment()

    // ---------------------------------------------------------------------------------------------
    private fun decrement() = mainActivityIdlingResource.decrement()
    //endregion ---------------------------------------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------