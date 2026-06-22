/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView

class SearchAdapter : ListAdapter<SearchResultItem, SearchAdapter.SearchResultVieHolder>(diffUtil) {

    companion object {
        val diffUtil = object : DiffUtil.ItemCallback<SearchResultItem>() {
            override fun areItemsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem): Boolean =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem): Boolean = false
        }
    }

    private var onClickListener: ((SearchResultItem) -> Unit)? = null

    fun setOnViewHolderClickListener(listener: (SearchResultItem) -> Unit) {
        onClickListener = listener
    }

    inner class SearchResultVieHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        private var textView = view.findViewById<MaterialTextView>(R.id.item_text)
        private var descriptionView = view.findViewById<MaterialTextView>(R.id.item_description)
        private var distanceView = view.findViewById<MaterialTextView>(R.id.item_distance)
        private var unitView = view.findViewById<MaterialTextView>(R.id.item_unit)
        private var itemImage = view.findViewById<ImageView>(R.id.item_img)
        fun bind(item: SearchResultItem) {
            textView.text = item.text
            descriptionView.text = item.subText
            distanceView.text = item.distance
            unitView.text = item.unit
            itemImage.setImageBitmap(item.bmp)
            view.setOnClickListener { onClickListener?.invoke(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultVieHolder = SearchResultVieHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.searh_result_item, parent, false),
    )

    override fun onBindViewHolder(holder: SearchResultVieHolder, position: Int) = holder.bind(getItem(position))
}
