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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.get
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.generalmagic.example.util.ButtonsDecorator
import com.generalmagic.example.util.Util
import com.generalmagic.sdk.GemMapSurface
import com.generalmagic.sdk.core.RectF
import com.generalmagic.sdk.d3scene.MapView
import com.generalmagic.sdk.d3scene.Screen
import com.generalmagic.sdk.util.SdkCall
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {
    private val maps = mutableMapOf<Long, MapView?>()
    private val SURFACES_COUNT_LIMIT = 9

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
            try{
                findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
            }catch (e : IllegalArgumentException){
                // nothing..
            }
        }

        val leftBtn = view.findViewById<FloatingActionButton>(R.id.bottomLeftButton)
        leftBtn.visibility = View.VISIBLE
        ButtonsDecorator.buttonAsDelete(requireContext(), leftBtn) {
            deleteLastSurface()
        }

        val rightBtn = view.findViewById<FloatingActionButton>(R.id.bottomRightButton)
        rightBtn.visibility = View.VISIBLE
        ButtonsDecorator.buttonAsAdd(requireContext(), rightBtn) {
            addSurface()
        }

        addSurface()
    }

    override fun onStop() {
        super.onStop()

        val linearLayout = view?.findViewById<LinearLayout>(R.id.scrolledLinearLayout) ?: return

        while(linearLayout.childCount > 0)
            deleteLastSurface()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun addSurface() {
        val linearLayout = view?.findViewById<LinearLayout>(R.id.scrolledLinearLayout) ?: return

        if (linearLayout.childCount >= SURFACES_COUNT_LIMIT) {
            return
        }

        val surface = GemMapSurface(requireContext())
        surface.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        surface.onScreenCreated = { screen ->
            onScreenCreated(surface, screen)
        }

        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 400)
        params.setMargins(50)

        val frame = FrameLayout(requireContext())
        frame.layoutParams = params
        frame.addView(surface)

        linearLayout.addView(frame)
    }

    private fun deleteLastSurface() {
        val linearLayout = view?.findViewById<LinearLayout>(R.id.scrolledLinearLayout) ?: return
        if (linearLayout.childCount == 0)
            return

        val lastIndex = linearLayout.childCount - 1
        val frame = (linearLayout[lastIndex] as FrameLayout)
        val lastSurface = frame[0] as GemMapSurface

        SdkCall.execute {
            val mapsId = lastSurface.getScreen()?.address()
            maps[mapsId]?.release() // release the map view
        }

        linearLayout.removeView(frame)
    }

    private fun onScreenCreated(surface: GemMapSurface, screen: Screen) {
        SdkCall.checkCurrentThread() // ensure we are on SDK thread.

        val mainViewRect = RectF(0.0f, 0.0f, 1.0f, 1.0f)
        val mapView = MapView.produce(screen, mainViewRect) ?: return

        maps[screen.address()] = mapView
    }
}
