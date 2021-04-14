/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples

import android.os.Bundle
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
import com.generalmagic.sdk.examples.util.ButtonsDecorator
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.core.RectF
import com.generalmagic.sdk.d3scene.MapView
import com.generalmagic.sdk.d3scene.Screen
import com.generalmagic.sdk.util.SdkCall
import com.google.android.material.floatingactionbutton.FloatingActionButton

class SecondFragment : Fragment() {
    private val maps = mutableMapOf<Long, MapView?>()
    private val maxSurfacesCount = 9

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_second, container, false)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.button_second).setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
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

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onStop() {
        super.onStop()

        val linearLayout = view?.findViewById<LinearLayout>(R.id.scrolledLinearLayout) ?: return

        while (linearLayout.childCount > 0) {
            deleteLastSurface()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun addSurface() {
        val linearLayout = view?.findViewById<LinearLayout>(R.id.scrolledLinearLayout) ?: return

        if (linearLayout.childCount >= maxSurfacesCount) {
            return
        }

        val surface = SdkCall.execute { GemSurfaceView(requireContext()) }
        surface?.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        surface?.onScreenCreated = { screen ->
            // Defines an action that should be done after the screen is created.
            onScreenCreated(screen)
        }

        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 400)
        params.setMargins(50)

        val frame = FrameLayout(requireContext())
        frame.layoutParams = params
        frame.addView(surface)

        linearLayout.addView(frame)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun deleteLastSurface() {
        val linearLayout = view?.findViewById<LinearLayout>(R.id.scrolledLinearLayout) ?: return
        if (linearLayout.childCount == 0)
            return

        val lastIndex = linearLayout.childCount - 1
        val frame = (linearLayout[lastIndex] as FrameLayout)
        val lastSurface = frame[0] as GemSurfaceView

        SdkCall.execute {
            val mapsId = lastSurface.getScreen()?.address()
            // Release the map view.
            maps[mapsId]?.release()
            // Remove the map view from the collection of displayed maps.
            maps.remove(mapsId)
        }

        linearLayout.removeView(frame)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun onScreenCreated(screen: Screen) {
        SdkCall.checkCurrentThread() // Ensure we are on SDK thread.

        /* 
        Define a rectangle in which the map view will expand.
        Predefined value of the offsets is 0.
        Value 1 means the offset will take 100% of available space.
         */
        val mainViewRect = RectF(0.0f, 0.0f, 1.0f, 1.0f)
        // Produce a map view and establish that it is the main map view.
        val mapView = MapView.produce(screen, mainViewRect) ?: return

        // Add the map view to the collection of displayed maps.
        maps[screen.address()] = mapView
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
