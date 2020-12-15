/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.adapters.sectionedlist

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.adapters.GEMGenericAdapter

abstract class GenericSectionedAdapter<T : GenericSectionedAdapter.SectionModel?>(
    private val context: Context,
    private val recyclerViewType: SectionedListType
) : GEMGenericAdapter<T?>() {

    // 2 columns by default
    var gridColumnsNumber: Int = 2

    constructor(
        context: Context,
        recyclerViewType: SectionedListType,
        sectionedItemsList: MutableList<T?>
    ) :
        this(context, recyclerViewType) {
        listItems = sectionedItemsList
    }

    open class SectionModel(
        var sectionIndex: Int,
        var itemArrayList: MutableList<*>
    )

    enum class SectionedListType {
        LINEAR_VERTICAL,
        LINEAR_HORIZONTAL,
        GRID
    }

    private inner class SectionAdapterHolder(
        val sectionId: Int,
        val sectionAdapter: GEMGenericAdapter<*>
    )

    private val sectionAdapterHolderList = arrayListOf<SectionAdapterHolder>()

    abstract inner class GenericSectionedViewHolder<T : SectionModel?>(itemView: View) :
        GEMGenericViewHolder<T?>(itemView) {

        private var sectionRecyclerView: RecyclerView =
            itemView.findViewById(R.id.section_recycler_view)
        val text: TextView = itemView.findViewById(R.id.header_text)

        init {
            setRecyclerViewLayout()
        }

        private fun setRecyclerViewLayout() {
            // recycler view for sections
            sectionRecyclerView.setHasFixedSize(true)
            sectionRecyclerView.isNestedScrollingEnabled = false

            // deactivate animations so the items won't blink when they are updated
            sectionRecyclerView.itemAnimator = null

            when (recyclerViewType) {
                SectionedListType.LINEAR_VERTICAL -> {
                    // Setup layout manager
                    sectionRecyclerView.layoutManager = LinearLayoutManager(
                        context,
                        LinearLayoutManager.VERTICAL,
                        false
                    )
                }
                SectionedListType.LINEAR_HORIZONTAL -> {
                    sectionRecyclerView.layoutManager = LinearLayoutManager(
                        context,
                        LinearLayoutManager.HORIZONTAL,
                        false
                    )

                    val snapHelper = LinearSnapHelper()
                    snapHelper.attachToRecyclerView(sectionRecyclerView)
                }
                SectionedListType.GRID -> {
                    val gridLayoutManager = GridLayoutManager(context, gridColumnsNumber)
                    sectionRecyclerView.layoutManager = gridLayoutManager
                }
            }
        }

        fun setAdapterToSectionRecyclerView(sectionIndex: Int, adapter: GEMGenericAdapter<*>) {
            adapter.setHasStableIds(true)
            sectionRecyclerView.adapter = adapter
            sectionAdapterHolderList.add(SectionAdapterHolder(sectionIndex, adapter))
        }
    }

    override fun getLayoutId(position: Int): Int {
        return R.layout.sectioned_list_chapter
    }

    fun notifyItemChanged(sectionId: Int, itemSectionPosition: Int) {
        for (i in sectionAdapterHolderList) {
            if (i.sectionId == sectionId) {
                i.sectionAdapter.notifyItemChanged(itemSectionPosition)
            }
        }
    }
}
