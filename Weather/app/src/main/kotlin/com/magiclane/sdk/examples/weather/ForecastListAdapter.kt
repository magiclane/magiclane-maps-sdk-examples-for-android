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

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.examples.weather.databinding.ForecastItemBinding

// -------------------------------------------------------------------------------------------------

class ForecastListAdapter(var type: EForecastType) : ListAdapter<ForecastItem, RecyclerView.ViewHolder>(diffUtilCallback)
{
    // ---------------------------------------------------------------------------------------------
    
    companion object
    {
        //used by the adapter for calculating the optimum number of changes to be made when the list is being updated
        val diffUtilCallback = object : DiffUtil.ItemCallback<ForecastItem>()
        {
            override fun areItemsTheSame(oldItem: ForecastItem, newItem: ForecastItem): Boolean = oldItem == newItem

            override fun areContentsTheSame(oldItem: ForecastItem, newItem: ForecastItem): Boolean = false
        }
    }

    class ForecastItemViewHolder(val binding: ForecastItemBinding) : RecyclerView.ViewHolder(binding.root)
    
    // ---------------------------------------------------------------------------------------------
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = 
        ForecastItemViewHolder(ForecastItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    
    // ---------------------------------------------------------------------------------------------
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int)
    {
        val item = getItem(position)
        (holder as ForecastItemViewHolder).binding.apply { 
            when(type){
                EForecastType.NOT_ASSIGNED -> {}
                EForecastType.CURRENT -> {
                    mainText.text = item.conditionName
                    subtext.isVisible = false
                    mainDetails.text = item.conditionValue
                }
                EForecastType.DAILY -> {
                    mainText.text = item.dayOfWeek
                    subtext.text = item.date
                    mainDetails.text = "${item.highTemperature}/"
                    subDetails.text = item.lowTemperature
                }
                EForecastType.HOURLY -> {
                    mainText.text = item.time
                    subtext.text = item.date
                    mainDetails.text = item.temperature
                }
            }
            forecastImage.setImageBitmap(item.bmp)
        }
    }
    // ---------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------
