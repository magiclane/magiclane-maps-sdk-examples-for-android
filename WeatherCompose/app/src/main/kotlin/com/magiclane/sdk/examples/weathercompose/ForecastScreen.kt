/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.weathercompose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.magiclane.sdk.compose.components.common.PanelTopBar
import com.magiclane.sdk.compose.components.weather.CurrentWeatherView
import com.magiclane.sdk.compose.components.weather.DailyForecastView
import com.magiclane.sdk.compose.components.weather.HourlyForecastView
import com.magiclane.sdk.examples.weathercompose.ui.theme.ForecastBackgroundDay
import com.magiclane.sdk.examples.weathercompose.ui.theme.ForecastBackgroundNight

/**
 * Full screen view of the requested forecast, drawn over the map: a sky-colored background, a top
 * bar naming the forecast and the matching forecast view from the Maps SDK Compose components
 * (current conditions with a sky header card, daily or hourly rows).
 */
@Composable
fun ForecastScreen(modifier: Modifier = Modifier, viewModel: WeatherViewModel, type: EForecastType) {
    // Only the current forecast is aware of the local daylight; the other screens use the day tone.
    val isDay = viewModel.currentForecast?.isDay ?: true
    val background = if (isDay) ForecastBackgroundDay else ForecastBackgroundNight

    val title = when (type) {
        EForecastType.CURRENT -> R.string.current_forecast
        EForecastType.DAILY -> R.string.daily_forecast
        EForecastType.HOURLY -> R.string.hourly_forecast
    }

    Column(
        modifier = modifier
            .background(background)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
    ) {
        PanelTopBar(
            title = stringResource(title),
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            contentColor = Color.White,
            onClose = { viewModel.closeForecast() },
        )

        when (type) {
            EForecastType.CURRENT -> CurrentWeatherView(
                data = viewModel.currentForecast,
                details = viewModel.forecastItems,
                modifier = Modifier.fillMaxWidth(),
                headerBackground = viewModel.currentForecast?.let {
                    painterResource(if (it.isDay) R.drawable.sky_day else R.drawable.sky_night)
                },
            )

            EForecastType.DAILY -> DailyForecastView(
                items = viewModel.forecastItems,
                modifier = Modifier.fillMaxWidth(),
            )

            EForecastType.HOURLY -> HourlyForecastView(
                items = viewModel.forecastItems,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
