// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.multisurfinfragrecycler

// -------------------------------------------------------------------------------------------------------------------------------

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.magiclane.sdk.examples.multisurfinfragrecycler.data.MapItem
import java.util.Date

// -------------------------------------------------------------------------------------------------------------------------------

class SecondFragment : Fragment()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    private val maxSurfacesCount = 20

    private lateinit var recycler: RecyclerView
    private lateinit var mapAdapter: CustomAdapter

    val viewModel by activityViewModels<MainActivityViewModel>()


    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View?
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

        recycler = view.findViewById<RecyclerView>(R.id.list).apply {
            itemAnimator = null
            layoutManager = LinearLayoutManager(requireContext())
            mapAdapter = CustomAdapter().also { it.submitList(viewModel.list) }
            adapter = mapAdapter
            recycledViewPool.setMaxRecycledViews(1, 0)
            setItemViewCacheSize(5)
        }

        val leftBtn = view.findViewById<FloatingActionButton>(R.id.bottom_left_button)
        leftBtn.visibility = View.VISIBLE
        buttonAsDelete(requireContext(), leftBtn)
        {
            deleteLastSurface()
        }

        val rightBtn = view.findViewById<FloatingActionButton>(R.id.bottom_right_button)
        rightBtn.visibility = View.VISIBLE
        buttonAsAdd(requireContext(), rightBtn)
        {
            addSurface()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun addSurface()
    {
        viewModel.list.apply {
            if (viewModel.list.size >= maxSurfacesCount) return
            add(MapItem(lastIndex + 1, Date()))
            mapAdapter.submitList(this.toMutableList())
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun deleteLastSurface()
    {
        viewModel.list.apply {
            if (size == 0) return
            removeLast()
            mapAdapter.submitList(this.toMutableList())
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun buttonAsAdd(context: Context, button: FloatingActionButton?, action: () -> Unit)
    {
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

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun buttonAsDelete(context: Context, button: FloatingActionButton?, action: () -> Unit)
    {
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

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
