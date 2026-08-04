/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.weather

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.magiclane.sdk.examples.weather.databinding.ActivityForecastBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.util.SdkCall

class ForecastActivity : AppCompatActivity() {
    companion object {
        const val LATITUDE_ARG_ID = "LATITUDE"
        const val LONGITUDE_ARG_ID = "LONGITUDE"
        const val FORECAST_TYPE_ID = "FORECAST_TYPE"
        const val LOCATION_NAME = "LOCATION_NAME"
    }

    private lateinit var coordinatesReference: Coordinates
    private lateinit var forecastAdapter: ForecastListAdapter
    private var forecastType = EForecastType.NOT_ASSIGNED
    private lateinit var binding: ActivityForecastBinding
    private lateinit var portraitConstraintSet: ConstraintSet
    private val viewModel: ForecastActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Setup binding's layout as the content of the screen.
        binding = ActivityForecastBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // set window insets (including display cutouts)
        ViewCompat.setOnApplyWindowInsetsListener(binding.forecastBackground) { view, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(safeInsets.left, safeInsets.top, safeInsets.right, safeInsets.bottom)
            insets
        }
        portraitConstraintSet = ConstraintSet().also { it.clone(binding.currentForecastContainer) }
        applyOrientationLayout()
        // get arguments
        val latitude = intent.getDoubleExtra(LATITUDE_ARG_ID, 0.0)
        val longitude = intent.getDoubleExtra(LONGITUDE_ARG_ID, 0.0)
        val location = intent.getStringExtra(LOCATION_NAME)
        forecastType = EForecastType.entries[intent.getIntExtra(FORECAST_TYPE_ID, 0)]
        // initialise adapter
        forecastAdapter = ForecastListAdapter(forecastType)
        binding.forecastList.adapter = forecastAdapter
        binding.forecastList.layoutManager = LinearLayoutManager(this)
        // request list of forecast items on sdk thread
        SdkCall.execute {
            coordinatesReference = Coordinates(latitude, longitude)
            // when another sdk call is done on the sdk thread the process will be run synchronously
            viewModel.getForecastList(forecastType, coordinatesReference)
        }
        // use mutable data to observe the list when it is updated
        viewModel.forecastItemsList.observe(this) { newList ->

            if (forecastType == EForecastType.CURRENT) {
                binding.apply {
                    val textColor = if (viewModel.isDay) Color.DKGRAY else Color.WHITE
                    viewModel.currentBMP?.let { currentForecastImage.setImageBitmap(it) }
                    location?.let {
                        locationName.text = it
                        locationName.setTextColor(textColor)
                    }
                    currentTemperature.text = viewModel.currentTemperature
                    description.text = viewModel.description
                    feelsLike.text = viewModel.feelsLike
                    updatedAt.text = viewModel.updatedAt
                    localTime.text = viewModel.currentTime
                    currentForecastContainer.background = ContextCompat.getDrawable(
                        this@ForecastActivity,
                        if (viewModel.isDay) R.drawable.sky_day else R.drawable.sky_night,
                    )
                    currentTemperature.setTextColor(textColor)
                    description.setTextColor(textColor)
                    feelsLike.setTextColor(textColor)
                    updatedAt.setTextColor(textColor)
                    localTime.setTextColor(textColor)
                    forecastBackground.setBackgroundColor(
                        ContextCompat.getColor(
                            this@ForecastActivity,
                            if (viewModel.isDay) {
                                R.color.activity_background_color
                            } else {
                                R.color.activity_background_color_dark
                            },
                        ),
                    )
                    currentForecastCard.isVisible = true
                }
            }
            // Send the new list to be processed and displayed by the adapter.
            forecastAdapter.submitList(newList)
        }

        viewModel.errorMessage.observe(this) {
            Utils.showDialog(it, this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.root.post { applyOrientationLayout() }
    }

    private fun applyOrientationLayout() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val margin = resources.getDimensionPixelSize(R.dimen.double_margin)

        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                connect(R.id.description, ConstraintSet.END, R.id.middle_guideline, ConstraintSet.START, margin)
                connect(R.id.current_temperature, ConstraintSet.END, R.id.middle_guideline, ConstraintSet.START, margin)
                connect(R.id.feels_like, ConstraintSet.START, R.id.middle_guideline, ConstraintSet.END, margin)
                connect(R.id.feels_like, ConstraintSet.TOP, R.id.location_name, ConstraintSet.BOTTOM, 0)
                connect(R.id.local_time, ConstraintSet.START, R.id.middle_guideline, ConstraintSet.END, margin)
                connect(R.id.updated_at, ConstraintSet.START, R.id.middle_guideline, ConstraintSet.END, margin)
            }
        }.applyTo(binding.currentForecastContainer)
    }
}
