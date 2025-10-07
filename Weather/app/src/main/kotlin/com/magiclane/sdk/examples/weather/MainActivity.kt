// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.weather

// -------------------------------------------------------------------------------------------------

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import androidx.test.espresso.idling.CountingIdlingResource
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapSceneObject
import com.magiclane.sdk.examples.weather.databinding.ActivityMainBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------
    companion object
    {
        private const val REQUEST_PERMISSIONS = 110
    }

    private lateinit var binding: ActivityMainBinding
    private var imageSize = 0

    // ---------------------------------------------------------------------------------------------
    // region OVERRIDDEN METHODS--------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageSize = resources.getDimension(R.dimen.image_size).toInt()
        binding.buttonsGroup.isVisible = false

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady
            // Set GPS button if location permission is granted, otherwise request permission
            SdkCall.execute {
                val hasLocationPermission =
                    PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                if (hasLocationPermission)
                {
                    Util.postOnMain { enableGPSButton() }
                } else
                {
                    requestPermissions(this)
                }
            }
            // onTouch event callback
            binding.gemSurfaceView.mapView?.onTouch = { xy ->
                // xy are the coordinates of the touch event
                SdkCall.execute {
                    binding.apply {// tell the map view where the touch event happened
                        gemSurfaceView.mapView?.cursorScreenPosition = xy
                        gemSurfaceView.mapView?.deactivateAllHighlights()

                        val centerXy =
                            Xy(gemSurfaceView.measuredWidth / 2, gemSurfaceView.measuredHeight / 2)

                        val myPosition = gemSurfaceView.mapView?.cursorSelectionSceneObject
                        if (myPosition != null && isSameMapScene(
                                myPosition,
                                MapSceneObject.getDefPositionTracker().first!!
                            )
                        )
                        {
                            showOverlayContainer(
                                getString(R.string.my_position),
                                "",
                                ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_current_location_arrow)
                                    ?.toBitmap(imageSize, imageSize)
                            )

                            myPosition.coordinates?.let {
                                gemSurfaceView.mapView?.centerOnCoordinates(
                                    it,
                                    -1,
                                    centerXy,
                                    Animation(EAnimation.Linear),
                                    Double.MAX_VALUE,
                                    0.0
                                )
                            }

                            return@execute
                        }

                        val landmarks = gemSurfaceView.mapView?.cursorSelectionLandmarks
                        if (!landmarks.isNullOrEmpty())
                        {
                            val landmark = landmarks[0]
                            landmark.run {
                                showOverlayContainer(
                                    name.toString(),
                                    description.toString(),
                                    image?.asBitmap(imageSize, imageSize)
                                )
                            }

                            val contour = landmark.getContourGeographicArea()
                            if (contour != null && !contour.isEmpty())
                            {
                                contour.let {
                                    gemSurfaceView.mapView?.centerOnArea(
                                        it,
                                        -1,
                                        centerXy,
                                        Animation(EAnimation.Linear),
                                    )

                                    val displaySettings = HighlightRenderSettings(
                                        EHighlightOptions.ShowContour,
                                        Rgba(255, 98, 0, 255),
                                        Rgba(255, 98, 0, 255),
                                        0.75
                                    )

                                    gemSurfaceView.mapView?.activateHighlightLandmarks(
                                        landmark,
                                        displaySettings
                                    )
                                }
                            } else
                            {
                                landmark.coordinates?.let {
                                    gemSurfaceView.mapView?.centerOnCoordinates(
                                        it,
                                        -1,
                                        centerXy,
                                        Animation(EAnimation.Linear),
                                        Double.MAX_VALUE,
                                        0.0
                                    )
                                }
                            }
                            setupForecastButtons(landmark.coordinates, landmark.name ?: "")

                            return@execute
                        } else
                            hideOverlayContainer()
                    }
                }
            }
            EspressoIdlingResource.decrement()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            Utils.showDialog("TOKEN REJECTED",this)
        }

        if (!Util.isInternetConnected(this))
        {
            Utils.showDialog("You must be connected to the internet!",this)
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
        EspressoIdlingResource.increment()
    }

    // ---------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()
        // Release the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    )
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults)
        {
            if (item != PackageManager.PERMISSION_GRANTED)
            {
                Utils.showDialog("Location permission is required in order to select the current position cursor.",this)
                return
            }
        }

        SdkCall.execute {
            // Notify permission status had changed
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)

            lateinit var positionListener: PositionListener
            if (PositionService.position?.isValid() == true)
            {
                Util.postOnMain { enableGPSButton() }
            } else
            {
                positionListener = PositionListener {
                    if (!it.isValid()) return@PositionListener

                    PositionService.removeListener(positionListener)
                    Util.postOnMain { enableGPSButton() }
                }
                PositionService.addListener(positionListener, EDataType.Position)
            }
        }
    }

    // endregion -----------------------------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------
    // region PRIVATE FUNCTIONS---------------------------------------------------------------------
    private fun setupForecastButtons(coordinates: Coordinates?, name: String)
    {
        val intent = Intent(this@MainActivity, ForecastActivity::class.java)
        SdkCall.execute {
            coordinates?.run {
                intent.putExtra(ForecastActivity.LATITUDE_ARG_ID, latitude)
                intent.putExtra(ForecastActivity.LONGITUDE_ARG_ID, longitude)
                binding.apply {
                    forecastButton.setOnClickListener {
                        intent.putExtra(ForecastActivity.FORECAST_TYPE_ID, EForecastType.CURRENT.ordinal)
                        intent.putExtra(ForecastActivity.LOCATION_NAME, name)
                        startActivity(intent)
                    }
                    hourlyForecastButton.setOnClickListener {
                        intent.putExtra(ForecastActivity.FORECAST_TYPE_ID, EForecastType.HOURLY.ordinal)
                        startActivity(intent)
                    }
                    dailyForecastButton.setOnClickListener {
                        intent.putExtra(ForecastActivity.FORECAST_TYPE_ID, EForecastType.DAILY.ordinal)
                        startActivity(intent)
                    }
                }
            }
        }
    }
    // ---------------------------------------------------------------------------------------------

    private fun isSameMapScene(first: MapSceneObject, second: MapSceneObject): Boolean =
        first.maxScaleFactor == second.maxScaleFactor &&
            first.scaleFactor == second.scaleFactor &&
            first.visibility == second.visibility &&
            first.coordinates?.latitude == second.coordinates?.latitude &&
            first.coordinates?.longitude == second.coordinates?.longitude &&
            first.coordinates?.altitude == second.coordinates?.altitude &&
            first.orientation?.x == second.orientation?.x &&
            first.orientation?.y == second.orientation?.y &&
            first.orientation?.z == second.orientation?.z &&
            first.orientation?.w == second.orientation?.w

    // ---------------------------------------------------------------------------------------------

    private fun showOverlayContainer(nameString: String, descriptionString: String, bmp: Bitmap?) =
        Util.postOnMain {
            binding.apply {
                buttonsGroup.isVisible = true
                overlayContainer.isVisible = true

                name.text = nameString
                if (descriptionString.isNotEmpty())
                    description.apply {
                        text = descriptionString
                        isVisible = true
                    }
                else
                    description.visibility = View.GONE

                image.setImageBitmap(bmp)
            }
        }

    // ---------------------------------------------------------------------------------------------

    private fun hideOverlayContainer() = Util.postOnMain {
        binding.overlayContainer.isVisible = false
        binding.buttonsGroup.isVisible = false
    }

    // ---------------------------------------------------------------------------------------------

    private fun enableGPSButton()
    {
        // Set actions for entering/ exiting following position mode.
        binding.gemSurfaceView.mapView?.apply {
            val isFollowingPosition = SdkCall.execute { isFollowingPosition() }
            binding.followCursor.apply {
                isVisible = isFollowingPosition == false

                onExitFollowingPosition = {
                    isVisible = true
                }

                onEnterFollowingPosition = {
                    isVisible = false
                }
                // Set on click action for the GPS button.
                setOnClickListener {
                    SdkCall.execute {
                        deactivateAllHighlights()
                        followPosition()
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun requestPermissions(activity: Activity): Boolean
    {
        val permissions = arrayListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS, activity, permissions.toTypedArray()
        )
    }

    // ---------------------------------------------------------------------------------------------

/*    private fun Context.isDarkThemeOn(): Boolean
    {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }*/

    // endregion------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
//region --------------------------------------------------FOR TESTING------------------------------
// -------------------------------------------------------------------------------------------------
object EspressoIdlingResource
{
    val espressoIdlingResource = CountingIdlingResource("MapSelectionIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion  ---------------------------------------------------------------------------------------
// -------------------------------------------------------------------------------------------------
