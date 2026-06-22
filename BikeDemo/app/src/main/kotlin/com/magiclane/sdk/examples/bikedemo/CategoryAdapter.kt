/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
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

// Horizontal bar of POI category chips. Tapping a chip selects it and triggers an
// around-position search for that category.
class CategoryAdapter : ListAdapter<CategoryItem, CategoryAdapter.CategoryViewHolder>(diffUtil) {

    companion object {
        const val NO_CATEGORY = -1

        val diffUtil = object : DiffUtil.ItemCallback<CategoryItem>() {
            override fun areItemsTheSame(oldItem: CategoryItem, newItem: CategoryItem): Boolean =
                oldItem.categoryId == newItem.categoryId

            override fun areContentsTheSame(oldItem: CategoryItem, newItem: CategoryItem): Boolean =
                oldItem == newItem
        }
    }

    private var selectedIndex: Int = NO_CATEGORY
    private var onClickListener: ((Int) -> Unit)? = null

    fun setOnCategoryClickListener(listener: (Int) -> Unit) {
        onClickListener = listener
    }

    fun setSelectedIndex(index: Int) {
        val previous = selectedIndex
        selectedIndex = index
        if (previous != NO_CATEGORY) notifyItemChanged(previous)
        if (index != NO_CATEGORY) notifyItemChanged(index)
    }

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.category_container)
        val icon: ImageView = view.findViewById(R.id.category_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val item = getItem(position)
        val isSelected = position == selectedIndex

        holder.icon.setImageBitmap(item.icon)

        if (isSelected) {
            holder.container.setBackgroundResource(R.drawable.rounded_background_primary)
        } else {
            holder.container.background = null
        }

        holder.container.setOnClickListener {
            onClickListener?.invoke(holder.adapterPosition)
        }
    }
}
