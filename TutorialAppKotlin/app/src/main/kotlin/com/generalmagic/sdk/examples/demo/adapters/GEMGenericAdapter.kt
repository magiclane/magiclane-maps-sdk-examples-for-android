/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

abstract class GEMGenericAdapter<T> :
    RecyclerView.Adapter<GEMGenericAdapter.GEMGenericViewHolder<T>> {

    protected var listItems: MutableList<T?>

    constructor(listItems: MutableList<T?>) {
        this.listItems = listItems
    }

    constructor() {
        listItems = mutableListOf()
    }

    abstract class GEMGenericViewHolder<T>(itemView: View) :
        RecyclerView.ViewHolder(itemView),
        Binder<T>

    open fun setItems(listItems: MutableList<T?>) {
        this.listItems = listItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GEMGenericViewHolder<T> {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)

        return getViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: GEMGenericViewHolder<T>, position: Int) {
        if (position >= 0 && position < listItems.size) {
            (holder as Binder<T>).bind(listItems[position], position)
        } else {
            (holder as Binder<T>).bind(null, position)
        }
    }

    override fun getItemCount(): Int {
        return listItems.size
    }

    override fun getItemViewType(position: Int): Int {
        return getLayoutId(position)
    }

    protected abstract fun getLayoutId(position: Int): Int

    protected abstract fun getViewHolder(view: View, viewType: Int): GEMGenericViewHolder<T>
}
