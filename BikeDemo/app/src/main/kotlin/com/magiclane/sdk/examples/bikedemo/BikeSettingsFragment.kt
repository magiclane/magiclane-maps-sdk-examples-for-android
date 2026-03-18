/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.magiclane.sdk.examples.bikedemo.databinding.FragmentBikeSettingsBinding

class BikeSettingsFragment : Fragment() {

    companion object {
        private val settingsAdapter = SettingsAdapter()
    }

    private val viewModel: MainActivityViewModel by activityViewModels()

    private var mBinding: FragmentBikeSettingsBinding? = null
    private val binding
        get() = mBinding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mBinding = DataBindingUtil.inflate(layoutInflater, R.layout.fragment_bike_settings, container, false)
        binding.apply {
            settingsList.apply {
                adapter = settingsAdapter
                layoutManager = LinearLayoutManager(requireContext())
                settingsAdapter.submitList(viewModel.getSettingsList())
            }
            bikeSettingsToolbar.setNavigationOnClickListener {
                requireActivity().supportFragmentManager.beginTransaction().remove(this@BikeSettingsFragment).commit()
            }
        }
        return binding.root
    }
}
