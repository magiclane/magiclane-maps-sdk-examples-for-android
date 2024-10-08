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

package com.magiclane.sdk.examples.bikesimulation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.magiclane.sdk.examples.bikesimulation.R

class BikeSettingsFragment : Fragment() {

    companion object{
        private val settingsAdapter = SettingsAdapter()
    }
    private val viewModel: MainActivityViewModel by activityViewModels()
    private lateinit var toolbar : MaterialToolbar
    private lateinit var settingsList: RecyclerView
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = layoutInflater.inflate(R.layout.fragment_bike_settings, container, false)
        toolbar = view.findViewById(R.id.bike_settings_toolbar)
        val settingsList = view.findViewById<RecyclerView>(R.id.settings_list)
        settingsList.apply { 
            adapter = settingsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            settingsAdapter.submitList(viewModel.getSettingsList())
        }
        toolbar.setNavigationOnClickListener { 
            requireActivity().supportFragmentManager.beginTransaction().remove(this).commit()
        }
        return view
    }
}
