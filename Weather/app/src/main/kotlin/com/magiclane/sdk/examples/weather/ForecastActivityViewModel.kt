// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.weather

// -------------------------------------------------------------------------------------------------

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.magiclane.sdk.EDaylight
import com.magiclane.sdk.OnWeatherForecastCompleted
import com.magiclane.sdk.WeatherService
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.TimezoneResult
import com.magiclane.sdk.core.TimezoneService
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.CoordinatesList
import com.magiclane.sdk.util.SdkCall
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// -------------------------------------------------------------------------------------------------
class ForecastActivityViewModel : ViewModel()
{
    // ---------------------------------------------------------------------------------------------
    init
    {
        //initialise the weather service
        SdkCall.execute { weatherService = WeatherService() }
    }

    // ---------------------------------------------------------------------------------------------
    companion object
    {
        const val FORECAST_DAYS = 7
        const val FORECAST_HOUR = 48
        const val IMAGE_SIZE = 120
    }

    private var weatherService: WeatherService? = null
    var forecastItemsList: MutableLiveData<MutableList<ForecastItem>> = MutableLiveData()
    var errorMessage: MutableLiveData<String> = MutableLiveData()
    private lateinit var listener: OnWeatherForecastCompleted
    var updatedAt = ""
    var currentTime = ""
    var description = ""
    var currentBMP: Bitmap? = null
    var isDay = false
    var currentTemperature = ""
    var feelsLike = ""

    // ---------------------------------------------------------------------------------------------

    fun getForecastList(forecastType: EForecastType, coordinates: Coordinates) = SdkCall.execute {
        listener = { results, err, msg ->
            if (err == GemError.NoError)
                SdkCall.execute {
                    // in this example we require the weather forecast for only one location 
                    // results[0].forecast holds the list of forecast items for the selected landmark 
                    results[0].forecast?.let {
                        val list = mutableListOf<ForecastItem>()
                        //map the conditions list to a list of our data object, ForecastItem
                        it.mapNotNullTo(list) { forecastItem ->
                            when (forecastType)
                            {
                                EForecastType.NOT_ASSIGNED -> null
                                EForecastType.CURRENT ->
                                {
                                    updatedAt = results[0].updated?.run {
                                        "Updated at: " + SimpleDateFormat("HH:MM", Locale.getDefault()).format(Date(asLong()))
                                    } ?: ""
                                    currentTime = "Current time: " + SimpleDateFormat("HH:MM", Locale.getDefault()).format(Date())
                                    description = forecastItem.description ?: ""
                                    isDay = forecastItem.daylight == EDaylight.Day || forecastItem.daylight == EDaylight.NotAvailable
                                    forecastItem.parameters?.forEach { param ->
                                        when (param.type)
                                        {
                                            "Temperature" ->
                                                currentTemperature = String.format(Locale.getDefault(), "%d%s", param.value.roundToInt(), param.unit)

                                            "FeelsLike" ->
                                                feelsLike = String.format(Locale.getDefault(), "%s %d%s", param.name, param.value.roundToInt(), param.unit)

                                            "Sunrise", "Sunset" ->
                                            {
                                                val result = TimezoneResult()
                                                val offset = TimezoneService.getTimezoneInfoWithCoordinates(result, coordinates, Time.getUniversalTime()!!, ProgressListener(), false) * 1000
                                                val timeValue = (param.value * 1000) + offset
                                                val time = SimpleDateFormat("HH:MM", Locale.getDefault()).format(Date(timeValue.toLong()))
                                                list.add(
                                                    ForecastItem(
                                                        conditionName = param.type ?: "",
                                                        conditionValue = time
                                                    )
                                                )
                                            }

                                            else ->
                                            {
                                                if (param.value.rem(1) == 0.0)
                                                    list.add(
                                                        ForecastItem(
                                                            conditionName = param.type ?: "",
                                                            conditionValue = String.format(Locale.getDefault(), "%d %s", param.value.roundToInt(), param.unit)
                                                        )
                                                    )
                                                else
                                                    list.add(
                                                        ForecastItem(
                                                            conditionName = param.type ?: "",
                                                            conditionValue = String.format(Locale.getDefault(), "%.2f %s", param.value, param.unit)
                                                        )
                                                    )
                                            }
                                        }
                                    }
                                    currentBMP = forecastItem.image?.asBitmap(IMAGE_SIZE, IMAGE_SIZE)
                                    null
                                }

                                EForecastType.DAILY ->
                                {
                                    val dateParam = forecastItem.timestamp?.run {
                                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(asLong()))
                                    } ?: ""
                                    val dayOfWeekParam = forecastItem.timestamp?.run {
                                        DayOfWeek.entries[dayOfWeek - 1].name
                                    } ?: ""
                                    val temperatureHighParam = forecastItem.parameters?.find { param -> param.type == "TemperatureHigh" }?.run {
                                        String.format(Locale.getDefault(), "%d%s", value.roundToInt(), unit)
                                    } ?: ""
                                    val temperatureLowParam = forecastItem.parameters?.find { param -> param.type == "TemperatureLow" }?.run {
                                        String.format(Locale.getDefault(), "%d%s", value.roundToInt(), unit)
                                    } ?: ""
                                    ForecastItem(
                                        date = dateParam,
                                        dayOfWeek = dayOfWeekParam,
                                        bmp = forecastItem.image?.asBitmap(IMAGE_SIZE, IMAGE_SIZE),
                                        highTemperature = temperatureHighParam,
                                        lowTemperature = temperatureLowParam
                                    )
                                }

                                EForecastType.HOURLY ->
                                {
                                    val dateParam = forecastItem.timestamp?.run {
                                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(asLong()))
                                    } ?: ""
                                    val timeParam = forecastItem.timestamp?.run {
                                        SimpleDateFormat("HH:MM", Locale.getDefault()).format(Date(asLong()))
                                    } ?: ""
                                    val isDayLight = forecastItem.daylight == EDaylight.Day || forecastItem.daylight == EDaylight.NotAvailable

                                    val temperatureParam = forecastItem.parameters?.find { param -> param.type == "Temperature" }?.run {
                                        String.format(Locale.getDefault(), "%d%s", value.roundToInt(), unit)
                                    } ?: ""

                                    ForecastItem(
                                        date = dateParam,
                                        time = timeParam,
                                        bmp = forecastItem.image?.asBitmap(IMAGE_SIZE, IMAGE_SIZE),
                                        temperature = temperatureParam,
                                        isDuringDay = isDayLight
                                    )
                                }
                            }
                        }
                        //update the value of the live data to trigger the observer 
                        forecastItemsList.postValue(list)
                    }
                }
            else 
                errorMessage.postValue(msg)
        }
        val coords = CoordinatesList().apply { add(coordinates) }
        //get the type of forecast based on the activity's argument
        when (forecastType)
        {
            EForecastType.NOT_ASSIGNED -> null
            //retrieves a list of lists comprised of a single item with in depth details about the weather for each location
            EForecastType.CURRENT -> weatherService?.getCurrent(coords, onCompleted = listener)
            //retrieves a list of lists comprised of multiple items with a few details about the weather for each location
            EForecastType.DAILY -> weatherService?.getDailyForecast(FORECAST_DAYS, coords, onCompleted = listener)
            //retrieves a list of lists comprised of multiple items with a few details about the weather for each location
            EForecastType.HOURLY -> weatherService?.getHourlyForecast(FORECAST_HOUR, coords, onCompleted = listener)
        }
    }
    // ---------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------
