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

package com.magiclane.sdk.examples.multisurfinfragrecycler

// -------------------------------------------------------------------------------------------------------------------------------

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.examples.multisurfinfragrecycler.data.MapItem

// -------------------------------------------------------------------------------------------------------------------------------
class CustomAdapter : ListAdapter<MapItem, CustomAdapter.ViewHolder>(diffUtil)
{

    companion object
    {
        val diffUtil: DiffUtil.ItemCallback<MapItem> = object : DiffUtil.ItemCallback<MapItem>()
        {
            override fun areItemsTheSame(oldItem: MapItem, newItem: MapItem): Boolean =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: MapItem, newItem: MapItem): Boolean =
                oldItem == newItem
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
    // -------------------------------------------------------------------------------------------------------------------------------

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(viewGroup.context).inflate(R.layout.map_layout, viewGroup, false)
        ).apply { setIsRecyclable(false) }

    // -------------------------------------------------------------------------------------------------------------------------------

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int)
    {
        getItem(position)?.let {
            viewHolder.itemView.findViewById<TextView>(R.id.date)?.text = it.timestamp
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    override fun onViewAttachedToWindow(holder: ViewHolder)
    {
        val parent = holder.itemView.findViewById<ConstraintLayout>(R.id.content)
        val loadingImage = holder.itemView.findViewById<ImageView>(R.id.loading_indicator)
        if (parent != null)
            AsyncLayoutInflater(holder.itemView.context).inflate(
                R.layout.surface_view,
                parent
            ) { view, _, _ ->
                parent.addView(
                    view, 0,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        parent.context.resources.getDimension(R.dimen.map_height).toInt()
                    )
                )
                (view as GemSurfaceView).doOnLayout {
                    loadingImage?.let { it.isInvisible = true }
                }
            }
        super.onViewAttachedToWindow(holder)
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    override fun onViewDetachedFromWindow(holder: ViewHolder)
    {
        val surface = holder.itemView.findViewById<GemSurfaceView>(R.id.surface)
        val parent = holder.itemView.findViewById<ConstraintLayout>(R.id.content)
        val loadingImage = holder.itemView.findViewById<ImageView>(R.id.loading_indicator)
        surface?.let {
            parent.removeView(it)
        }
        loadingImage?.let { it.isInvisible = false }
        super.onViewDetachedFromWindow(holder)
    }

    // -------------------------------------------------------------------------------------------------------------------------------

    override fun getItemId(position: Int): Long = 1

// -------------------------------------------------------------------------------------------------------------------------------
}
