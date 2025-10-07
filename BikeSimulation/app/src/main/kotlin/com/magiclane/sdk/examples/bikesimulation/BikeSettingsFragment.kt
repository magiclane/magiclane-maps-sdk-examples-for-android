// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.bikesimulation

// -------------------------------------------------------------------------------------------------

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

// -------------------------------------------------------------------------------------------------

class BikeSettingsFragment : Fragment() {

    // -------------------------------------------------------------------------------------------------
    
    companion object{
        private val settingsAdapter = SettingsAdapter()
    }
    
    // -------------------------------------------------------------------------------------------------
    
    private val viewModel: MainActivityViewModel by activityViewModels()
    private lateinit var toolbar : MaterialToolbar
    private lateinit var settingsList: RecyclerView
    
    // -------------------------------------------------------------------------------------------------
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = layoutInflater.inflate(R.layout.fragment_bike_settings, container, false)
        toolbar = view.findViewById(R.id.bike_settings_toolbar)
        settingsList = view.findViewById(R.id.settings_list)
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
    // -------------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------
