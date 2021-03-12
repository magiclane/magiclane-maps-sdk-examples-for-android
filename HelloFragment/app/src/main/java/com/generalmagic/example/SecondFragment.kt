/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.generalmagic.sdk.GemMapSurface
import com.generalmagic.sdk.core.RectF
import com.generalmagic.sdk.d3scene.MapView
import com.generalmagic.sdk.util.SdkCall

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {
    private var mainMapView: MapView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_second, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.button_second).setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }

        /// General magic
        val mapSurface = view.findViewById<GemMapSurface>(R.id.gem_surface)
        mapSurface.onScreenCreated = { screen ->
            SdkCall.execute {
                val mainViewRect = RectF(0.0f, 0.0f, 1.0f, 1.0f)
                val mainMapView = MapView.produce(screen, mainViewRect)
                this.mainMapView = mainMapView
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        SdkCall.execute {
            mainMapView?.release()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

}
