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

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.magiclane.sdk.examples.weather.databinding.ActivityForecastBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.util.SdkCall

// -------------------------------------------------------------------------------------------------
class ForecastActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------
    companion object
    {
        private var forecastAdapter: ForecastListAdapter? = null
        const val LATITUDE_ARG_ID = "LATITUDE"
        const val LONGITUDE_ARG_ID = "LONGITUDE"
        const val FORECAST_TYPE_ID = "FORECAST_TYPE"
        const val LOCATION_NAME = "LOCATION_NAME"
    }

    private lateinit var coordinatesReference: Coordinates
    private var forecastType = EForecastType.NOT_ASSIGNED
    private lateinit var binding: ActivityForecastBinding
    private val viewModel: ForecastActivityViewModel by viewModels()

    // ---------------------------------------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        //setup binding's layout as the content of th screen
        binding = ActivityForecastBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //set window insets
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.forecastBackground.setOnApplyWindowInsetsListener { view, insets ->
                val systemBarsInsets = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(
                    systemBarsInsets.left,
                    systemBarsInsets.top,
                    systemBarsInsets.right,
                    systemBarsInsets.bottom
                )
                insets
            }
        }
        //get arguments
        val latitude = intent.getDoubleExtra(LATITUDE_ARG_ID, 0.0)
        val longitude = intent.getDoubleExtra(LONGITUDE_ARG_ID, 0.0)
        val location = intent.getStringExtra(LOCATION_NAME)
        forecastType = EForecastType.entries[intent.getIntExtra(FORECAST_TYPE_ID, 0)]
        //initialise adapter
        if (forecastAdapter == null)
            forecastAdapter = ForecastListAdapter(forecastType)
        forecastAdapter!!.type = forecastType
        binding.forecastList.adapter = forecastAdapter
        binding.forecastList.layoutManager = LinearLayoutManager(this)
        //request list of forecast items on sdk thread
        SdkCall.execute {
            coordinatesReference = Coordinates(latitude, longitude)
            //when another sdk call is done on the sdk thread the process will be run synchronously  
            viewModel.getForecastList(forecastType, coordinatesReference)
        }
        // use mutable data to observe the list when it is updated
        viewModel.forecastItemsList.observe(this) { newList ->

            if (forecastType == EForecastType.CURRENT)
            {
                binding.apply {
                    val textColor = if (viewModel.isDay) Color.DKGRAY else Color.WHITE
                    viewModel.currentBMP?.let { currentForecastImage.setImageBitmap(it) }
                    location?.let {
                        locationName.text = it
                        locationName.setTextColor(textColor)
                    }
                    currentTemperature.text = viewModel.currentTemperature
                    description.text = viewModel.description
                    currentTemperature.text = viewModel.currentTemperature
                    feelsLike.text = viewModel.feelsLike
                    updatedAt.text = viewModel.updatedAt
                    localTime.text = viewModel.currentTime
                    currentForecastContainer.background = ContextCompat.getDrawable(this@ForecastActivity, if (viewModel.isDay) R.drawable.sky_day else R.drawable.sky_night)
                    currentTemperature.setTextColor(textColor)
                    description.setTextColor(textColor)
                    currentTemperature.setTextColor(textColor)
                    feelsLike.setTextColor(textColor)
                    updatedAt.setTextColor(textColor)
                    localTime.setTextColor(textColor)
                    forecastBackground.setBackgroundColor(
                        ContextCompat.getColor(
                            this@ForecastActivity,
                            if (viewModel.isDay) R.color.activity_background_color
                            else R.color.activity_background_color_dark
                        )
                    )
                    currentForecastCard.isVisible = true
                }
            }
            //send the new list to pe processed and displayed by the adapter
            forecastAdapter?.submitList(newList)
        }

        viewModel.errorMessage.observe(this) {
            Utils.showDialog(it,this)
        }
    }
    // ---------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------
