/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.socialreport

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

data class CategoryItem(
    val uid: Int,
    val name: String,
    val icon: Bitmap?,
    val hasSubcategories: Boolean,
)

class CategoryAdapter(
    private val items: List<CategoryItem>,
    private val onItemClick: (CategoryItem) -> Unit,
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val icon: ImageView = view.findViewById(R.id.category_icon)
        val name: TextView = view.findViewById(R.id.category_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        if (item.icon != null) {
            holder.icon.setImageBitmap(item.icon)
        } else {
            holder.icon.setImageResource(android.R.drawable.ic_menu_report_image)
        }
        holder.card.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
