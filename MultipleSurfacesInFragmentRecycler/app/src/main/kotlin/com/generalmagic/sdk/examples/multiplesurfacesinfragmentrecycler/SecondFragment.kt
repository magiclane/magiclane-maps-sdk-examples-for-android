// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.generalmagic.sdk.examples.multiplesurfacesinfragmentrecycler

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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

// -------------------------------------------------------------------------------------------------------------------------------

class SecondFragment : Fragment()
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private val maxSurfacesCount = 9
    private val list = arrayListOf(0, 1, 2, 3)

    private lateinit var recycler: RecyclerView

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

        recycler = view.findViewById<RecyclerView>(R.id.list).apply { 
            itemAnimator = null
            layoutManager = LinearLayoutManager(requireContext())
            adapter = CustomAdapter(list)
        }

        val leftBtn = view.findViewById<FloatingActionButton>(R.id.bottomLeftButton)
        leftBtn.visibility = View.VISIBLE
        buttonAsDelete(requireContext(), leftBtn)
        {
            deleteLastSurface()
        }

        val rightBtn = view.findViewById<FloatingActionButton>(R.id.bottomRightButton)
        rightBtn.visibility = View.VISIBLE
        buttonAsAdd(requireContext(), rightBtn)
        {
            addSurface()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun addSurface()
    {
        if (list.size >= maxSurfacesCount)
        {
            return
        }

        list.add(list.lastIndex + 1)
        recycler.adapter?.notifyItemInserted(list.lastIndex)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun deleteLastSurface()
    {
        if (list.size == 0) return
        list.removeLast()
        recycler.adapter?.notifyItemRemoved(list.lastIndex + 1)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    class CustomAdapter(private val dataSet: ArrayList<Int>) : RecyclerView.Adapter<CustomAdapter.ViewHolder>()
    {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder
        {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.map_layout, viewGroup, false)

            val result = ViewHolder(view)
            result.setIsRecyclable(false)

            return result
        }

        override fun getItemCount() = dataSet.size

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) = Unit
    }

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
