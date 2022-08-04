/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.multiplesurfacesinfragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.d3scene.MapView
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
        buttonAsDelete(requireContext(), leftBtn) {
            deleteLastSurface()
        }

        val rightBtn = view.findViewById<FloatingActionButton>(R.id.bottomRightButton)
        rightBtn.visibility = View.VISIBLE
        buttonAsAdd(requireContext(), rightBtn) {
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

        val surface = GemSurfaceView(requireContext())
        surface.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        surface.onDefaultMapViewCreated = onDefaultMapViewCreated@{
            val screen = surface.gemScreen ?: return@onDefaultMapViewCreated

            // Add the map view to the collection of displayed maps.
            maps[screen.address] = it
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
        val frame = (linearLayout.getChildAt(lastIndex) as FrameLayout)
        val lastSurface = frame.getChildAt(0) as GemSurfaceView

        SdkCall.execute {
            val mapsId = lastSurface.gemScreen?.address
            // Release the map view.
            maps[mapsId]?.release()
            // Remove the map view from the collection of displayed maps.
            maps.remove(mapsId)
        }

        linearLayout.removeView(frame)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun buttonAsAdd(context: Context, button: FloatingActionButton?, action: () -> Unit) {
        button ?: return

        val tag = "add"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.green)
        val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_input_add)

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun buttonAsDelete(
        context: Context,
        button: FloatingActionButton?,
        action: () -> Unit
    ) {
        button ?: return

        val tag = "delete"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.red)
        val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_delete)

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }
}
