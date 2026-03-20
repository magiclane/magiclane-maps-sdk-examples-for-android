/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.hellofragmentcustomstyle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.d3scene.MapViewPreferences
import com.magiclane.sdk.examples.hellofragmentcustomstyle.databinding.FragmentSecondBinding

class SecondFragment : Fragment() {

    private var binding: FragmentSecondBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate<FragmentSecondBinding>(inflater, R.layout.fragment_second, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding?.gemSurfaceView?.onDefaultMapViewCreated = { mapView ->
            mapView.preferences?.let {
                applyCustomAssetStyle(it)
            }
        }

        binding?.buttonSecond?.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun applyCustomAssetStyle(mapViewPreferences: MapViewPreferences) {
        val filename = "Basic_1_Oldtime_with_Elevation.style"

        // Opens style input stream.
        val inputStream = resources.assets.open(filename)

        // Take bytes.
        val data = inputStream.readBytes()
        if (data.isEmpty()) return

        // Apply style.
        mapViewPreferences.setMapStyleByDataBuffer(DataBuffer(data))
    }
}
