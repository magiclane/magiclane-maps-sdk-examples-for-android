/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.adapters.expandablelist

import android.util.SparseIntArray
import android.view.View
import android.widget.ImageView
import com.generalmagic.gemsdk.demo.adapters.GEMGenericAdapter

abstract class GenericExpandableAdapter<T : GenericExpandableAdapter.ExpandableListItem>(private var mode: ExpandableListMode) :
    GEMGenericAdapter<T>() {

    protected var visibleItems: MutableList<T?> = mutableListOf()
    private var indexList: MutableList<Int> = mutableListOf()
    private var expandMap = SparseIntArray()

    enum class ExpandableListMode {
        MODE_MULTIPLE_ITEMS_EXPANDED,
        MODE_SINGLE_ITEM_EXPANDED
    }

    open class ExpandableListItem(
        var itemType: Int,
        var chapterIndex: Int = 0,
        var positionInChapter: Int = 0
    )

    override fun getItemId(i: Int): Long {
        return i.toLong()
    }

    override fun getItemCount(): Int {
        return visibleItems.size
    }

    abstract inner class HeaderViewHolder<T> : GEMGenericViewHolder<T> {

        private lateinit var arrow: ImageView

        constructor(view: View) : super(view) {
            view.setOnClickListener {
                toggleExpandedItems(layoutPosition, false)
            }
        }

        constructor(view: View, arrow: ImageView) : super(view) {
            this.arrow = arrow
            view.setOnClickListener {
                handleClick()
            }
        }

        override fun bind(data: T?, position: Int) {
            arrow.rotation = if (isExpanded(position)) {
                180f
            } else 0f
        }

        private fun handleClick() {
            if (toggleExpandedItems(layoutPosition, false)) {
                openArrow(arrow)
            } else {
                closeArrow(arrow)
            }
        }
    }

    override fun setItems(listItems: MutableList<T?>) {
        this.listItems = listItems
        val visibleItems: MutableList<T?> = ArrayList()
        expandMap.clear()
        indexList.clear()
        for (i in listItems.indices) {
            if (listItems[i]?.itemType == TYPE_HEADER) {
                indexList.add(i)
                listItems[i]?.let { visibleItems.add(it) }
            }
        }
        this.visibleItems = visibleItems
        notifyDataSetChanged()
    }

    fun expandItems(position: Int, notify: Boolean) {
        if (listItems.isNotEmpty()) {
            var count = 0
            val index = indexList[position]
            var insert = position
            var i = index + 1
            while (i < listItems.size && listItems[i]?.itemType != TYPE_HEADER) {
                insert++
                count++
                visibleItems.add(insert, listItems[i])
                indexList.add(insert, i)
                i++
            }
            notifyItemRangeInserted(position + 1, count)
            val allItemsPosition = indexList[position]
            expandMap.put(allItemsPosition, 1)
            if (notify) {
                notifyItemChanged(position)
            }
        }
    }

    fun collapseItems(position: Int, notify: Boolean) {
        if (listItems.isNotEmpty()) {
            var count = 0
            val index = indexList[position]
            var i = index + 1
            while (i < listItems.size && listItems[i]?.itemType != TYPE_HEADER) {
                count++
                visibleItems.removeAt(position + 1)
                indexList.removeAt(position + 1)
                i++
            }
            notifyItemRangeRemoved(position + 1, count)
            val allItemsPosition = indexList[position]
            expandMap.delete(allItemsPosition)
            if (notify) {
                notifyItemChanged(position)
            }
        }
    }

    fun expandAll() {
        for (i in visibleItems.indices.reversed()) {
            if (visibleItems[i]?.itemType == TYPE_HEADER) {
                if (!isExpanded(i)) {
                    expandItems(i, true)
                }
            }
        }
    }

    fun collapseAll() {
        collapseAllExcept(-1)
    }

    fun toggleExpandedItems(position: Int, notify: Boolean): Boolean {
        return if (isExpanded(position)) {
            collapseItems(position, notify)
            false
        } else {
            expandItems(position, notify)
            if (mode == ExpandableListMode.MODE_SINGLE_ITEM_EXPANDED) {
                collapseAllExcept(position)
            }
            true
        }
    }

    protected fun isExpanded(position: Int): Boolean {
        val allItemsPosition = indexList[position]
        return expandMap[allItemsPosition, -1] >= 0
    }

    protected fun removeItemAt(visiblePosition: Int) {
        val allItemsPosition = indexList[visiblePosition]
        listItems.removeAt(allItemsPosition)
        visibleItems.removeAt(visiblePosition)
        incrementIndexList(allItemsPosition, visiblePosition, -1)
        incrementExpandMapAfter(allItemsPosition, -1)
        notifyItemRemoved(visiblePosition)
    }

    private fun incrementExpandMapAfter(position: Int, direction: Int) {
        val newExpandMap = SparseIntArray()
        for (i in 0 until expandMap.size()) {
            val index = expandMap.keyAt(i)
            newExpandMap.put(if (index < position) index else index + direction, 1)
        }
        expandMap = newExpandMap
    }

    private fun incrementIndexList(allItemsPosition: Int, visiblePosition: Int, direction: Int) {
        val newIndexList: MutableList<Int> = ArrayList()
        for (i in indexList.indices) {
            if (i == visiblePosition) {
                if (direction > 0) {
                    newIndexList.add(allItemsPosition)
                }
            }
            val index = indexList[i]
            newIndexList.add(if (index < allItemsPosition) index else index + direction)
        }
        indexList = newIndexList
    }

    private fun collapseAllExcept(position: Int) {
        for (i in visibleItems.indices.reversed()) {
            if (i != position && visibleItems[i]?.itemType == TYPE_HEADER) {
                if (isExpanded(i)) {
                    collapseItems(i, true)
                }
            }
        }
    }

    companion object {
        const val TYPE_HEADER = 1000
        const val TYPE_CONTENT = 1001
        private const val ARROW_ROTATION_DURATION = 150

        fun openArrow(view: View) {
            view.animate().setDuration(ARROW_ROTATION_DURATION.toLong()).rotation(180f)
        }

        fun closeArrow(view: View) {
            view.animate().setDuration(ARROW_ROTATION_DURATION.toLong()).rotation(0f)
        }
    }
}
