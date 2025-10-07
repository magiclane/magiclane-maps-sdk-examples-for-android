// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
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

    // -------------------------------------------------------------------------------------------------
    
    companion object {
        val diffUtil = object : DiffUtil.ItemCallback<SearchResultItem>() {
            override fun areItemsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem): Boolean = oldItem == newItem

            override fun areContentsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem): Boolean = false
        }
    }

    // -------------------------------------------------------------------------------------------------

    private var onClickListener: ((SearchResultItem) -> Unit)? = null
    
    // -------------------------------------------------------------------------------------------------
    
    fun setOnViewHolderClickListener(listener: (SearchResultItem) -> Unit) {
        onClickListener = listener
    }

    // -------------------------------------------------------------------------------------------------
    
    inner class SearchResultVieHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        private var textView = view.findViewById<MaterialTextView>(R.id.item_text)
        private var itemImage = view.findViewById<ImageView>(R.id.item_img)
        fun bind(item: SearchResultItem) {
            item.text?.let { textView.text = it }
            item.bmp?.let { itemImage.setImageBitmap(it) }
            view.setOnClickListener { onClickListener?.invoke(item) }
        }
    }
    
    // -------------------------------------------------------------------------------------------------
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultVieHolder = SearchResultVieHolder(LayoutInflater.from(parent.context).inflate(R.layout.searh_result_item, parent, false))

    // -------------------------------------------------------------------------------------------------
    
    override fun onBindViewHolder(holder: SearchResultVieHolder, position: Int) = holder.bind(getItem(position))
    // -------------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------
