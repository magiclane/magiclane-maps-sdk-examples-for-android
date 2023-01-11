// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.hellofragmentcustomstyle

// -------------------------------------------------------------------------------------------------------------------------------

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.util.SdkCall

// -------------------------------------------------------------------------------------------------------------------------------

class SecondFragment : Fragment()
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private lateinit var gemSurfaceView: GemSurfaceView

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        return inflater.inflate(R.layout.fragment_second, container, false)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.button_second).setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }

        gemSurfaceView = view.findViewById(R.id.gem_surface)

        gemSurfaceView.onDefaultMapViewCreated = {
            applyCustomAssetStyle(it)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun applyCustomAssetStyle(mapView: MapView?) = SdkCall.execute {
        val filename = "(Desktop) Monochrome Deep Blue (5a1da93a-dbf2-4a36-9b5c-1370386c1496).style"

        // Opens GPX input stream.
        val inputStream = resources.assets.open(filename)

        // Take bytes.
        val data = inputStream.readBytes()
        if (data.isEmpty()) return@execute

        // Apply style.
        mapView?.preferences?.setMapStyleByDataBuffer(DataBuffer(data))
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
