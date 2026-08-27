/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapscatalog

import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors

/**
 * Swipe-to-reveal delete for the online rows: swiping a downloaded/downloading row
 * towards the start uncovers a red trash background; releasing it past the trigger
 * distance asks the adapter to route the delete (behind a confirmation). The row is
 * never dismissed — it always springs back and the data layer drives whether it
 * disappears, exactly like the Compose `SwipeToDeleteRow` (whose `confirmValueChange`
 * fires the request and then refuses the dismissal).
 */
class SwipeToDeleteCallback(private val adapter: CatalogAdapter) :
    ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START) {

    // The swipe being tracked: the last drag distance seen while the user still held
    // the row, and whether its release was already evaluated. Only one swipe can be
    // active at a time.
    private var trackedHolder: RecyclerView.ViewHolder? = null
    private var lastActiveDx = 0f
    private var releaseEvaluated = false

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION || !adapter.isSwipeable(position)) return 0
        return super.getSwipeDirs(recyclerView, viewHolder)
    }

    // The row must never swipe out: an unreachable threshold and escape velocity turn
    // the gesture into reveal-only, so onSwiped never fires and the row springs back.
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = UNREACHABLE_THRESHOLD

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = Float.MAX_VALUE

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float = Float.MAX_VALUE

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (viewHolder === trackedHolder) {
            trackedHolder = null
            lastActiveDx = 0f
            releaseEvaluated = false
        }
        super.clearView(recyclerView, viewHolder)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            if (isCurrentlyActive) {
                // Track the drag while the user holds the row; the release decision
                // uses the last held position, not the spring-back frames.
                trackedHolder = viewHolder
                lastActiveDx = dX
                releaseEvaluated = false
            } else if (viewHolder === trackedHolder && !releaseEvaluated) {
                releaseEvaluated = true
                if (-lastActiveDx > viewHolder.itemView.width * TRIGGER_FRACTION) {
                    val position = viewHolder.bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) adapter.onSwipeDeleteRequested(position)
                }
            }
            if (dX < 0) drawDeleteBackground(c, recyclerView, viewHolder, dX)
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    // Paints the red background and trash icon in the strip uncovered by the swipe.
    private fun drawDeleteBackground(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
    ) {
        val itemView = viewHolder.itemView
        val background = ContextCompat.getDrawable(recyclerView.context, R.drawable.bg_card_middle)?.mutate()
        background?.setTint(MaterialColors.getColor(recyclerView, com.google.android.material.R.attr.colorError))
        background?.setBounds(
            itemView.right + dX.toInt(),
            itemView.top,
            itemView.right,
            itemView.bottom,
        )
        background?.draw(c)

        val icon = ContextCompat.getDrawable(recyclerView.context, R.drawable.ml_trash_24)?.mutate()
        if (icon != null) {
            icon.setTint(MaterialColors.getColor(recyclerView, com.google.android.material.R.attr.colorOnError))
            val margin = (ICON_MARGIN_DP * recyclerView.resources.displayMetrics.density).toInt()
            val iconSize = (ICON_SIZE_DP * recyclerView.resources.displayMetrics.density).toInt()
            val top = itemView.top + (itemView.height - iconSize) / 2
            val right = itemView.right - margin
            if (-dX > iconSize + 2 * margin) {
                icon.setBounds(right - iconSize, top, right, top + iconSize)
                icon.draw(c)
            }
        }
    }

    private companion object {
        const val ICON_MARGIN_DP = 20f
        const val ICON_SIZE_DP = 24f

        // Fraction of the row width the release must uncover to count as a delete
        // request (mirrors the Compose SwipeToDismissBox positional threshold).
        const val TRIGGER_FRACTION = 0.4f

        // A swipe threshold the drag can never reach: the row cannot be dismissed.
        const val UNREACHABLE_THRESHOLD = 2f
    }
}
