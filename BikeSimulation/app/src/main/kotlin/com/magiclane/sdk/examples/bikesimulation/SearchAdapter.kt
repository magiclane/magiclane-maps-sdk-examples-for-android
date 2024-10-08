// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.bikesimulation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.magiclane.sdk.examples.bikesimulation.R

// -------------------------------------------------------------------------------------------------

class SearchAdapter : ListAdapter<SearchResultItem, SearchAdapter.SearchResultVieHolder>(diffUtil) {

    companion object {
        val diffUtil = object : DiffUtil.ItemCallback<SearchResultItem>() {
            override fun areItemsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem): Boolean = oldItem == newItem

            override fun areContentsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem): Boolean = false

        }
    }

    private var onClickListener: ((SearchResultItem) -> Unit)? = null

    fun setOnViewHolderClickListener(listener: (SearchResultItem) -> Unit) {
        onClickListener = listener
    }

    // -------------------------------------------------------------------------------------------------
    inner class SearchResultVieHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private var textView = view.findViewById<MaterialTextView>(R.id.item_text)
        private var itemImage = view.findViewById<ImageView>(R.id.item_img)
        fun bind(item: SearchResultItem) {
            item.text?.let { textView.text = it }
            item.bmp?.let { itemImage.setImageBitmap(it) }
            view.setOnClickListener { onClickListener?.invoke(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultVieHolder = SearchResultVieHolder(LayoutInflater.from(parent.context).inflate(R.layout.searh_result_item, parent, false))

    override fun onBindViewHolder(holder: SearchResultVieHolder, position: Int) = holder.bind(getItem(position))
    // -------------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------
