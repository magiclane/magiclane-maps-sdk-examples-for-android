/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.demo.activities.publictransport

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.generalmagic.sdk.demo.R
import com.generalmagic.sdk.demo.util.AppUtils
import kotlin.math.abs

class PublicTransportRouteDescriptionLineView : View {

    private val mCircleBounds = RectF()
    private val mOvalBounds = RectF()
    private val mSolidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mPath = Path()

    private var mRadius = 0.0f
    private var mStrokeWidth = 0.0f
    private var mCellHeight = 0.0f
    private var mIntermediatePts = 0

    private var mSegmentIndex = 0
    private var mLineUpColor = 0
    private var mLineDownColor = 0
    private var mCircleColor = 0
    private var mDrawUp = true
    private var mDrawDown = true
    private var mDrawDownDashed = false

    constructor(context: Context) : super(context)

    constructor(context: Context?, attrs: AttributeSet) : super(context, attrs) {
        if (context == null || context.theme == null) {
            return
        }

        val a = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.PublicTransportRouteDescriptionLineView,
            0,
            0
        )

        try {
            mRadius =
                a.getDimension(R.styleable.PublicTransportRouteDescriptionLineView_radius, 0.0f)
            mStrokeWidth = a.getDimension(
                R.styleable.PublicTransportRouteDescriptionLineView_strokeWidth,
                0.0f
            )
            mCellHeight =
                a.getDimension(R.styleable.PublicTransportRouteDescriptionLineView_cellHeight, 0.0f)
        } finally {
            // release the TypedArray so that it can be reused.
            a.recycle()
        }

        mSolidPaint.style = Paint.Style.STROKE
        mSolidPaint.strokeWidth = mStrokeWidth

        mFillPaint.style = Paint.Style.FILL
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = this.width
        val h = this.height

        val qMM = AppUtils.getSizeInPixelsFromMM(1).toFloat() / 4
        val x = (w / 2).toFloat()
        val y = sCellHeight / 2 + qMM

        mCircleBounds.set(x - mRadius, y - mRadius, x + mRadius, y + mRadius)

        if (mDrawDown) {
            if (mDrawDownDashed) {
                val d = y - mRadius - mStrokeWidth / 2
                val y0 = y + mRadius + mStrokeWidth / 2
                val available_space = h - y0
                val diameter = mStrokeWidth + mStrokeWidth / 2
                val radius = diameter / 2
                val max_d = Math.max(diameter, d)

                var n = (available_space / max_d).toInt()

                if (n % 2 == 1) {
                    val diff = (available_space - n * max_d) / max_d

                    if (diff >= 0.5f) {
                        ++n
                    } else {
                        --n
                    }
                }

                val nDots = n / 2
                val extra_space = available_space - nDots * diameter
                val space_between_dots = (extra_space - d) / (nDots - 1)

                mFillPaint.color = mLineDownColor
                mOvalBounds.set(x - radius, y0, x + radius, y0 + diameter)

                for (i in 0 until n) {
                    if (i % 2 == 0) {
                        if (i == 0) {
                            mOvalBounds.top += d
                            mOvalBounds.bottom += d
                        } else {
                            mOvalBounds.top += space_between_dots
                            mOvalBounds.bottom += space_between_dots
                        }
                    } else {
                        canvas.drawOval(mOvalBounds, mFillPaint)
                        mOvalBounds.top += diameter
                        mOvalBounds.bottom += diameter
                    }
                }
            } else {
                mPath.moveTo(x, mCircleBounds.bottom)
                mPath.lineTo(x, h.toFloat())

                mSolidPaint.color = mLineDownColor
                canvas.drawPath(mPath, mSolidPaint)

                if (mIntermediatePts > 0 &&
                    mSegmentIndex >= 0 &&
                    mSegmentIndex < sOffsets!![0].size
                ) {
                    val radius = mRadius
                    var y0 = sCellHeightExt / 2 + qMM

                    for (i in sOffsets!!.indices) {
                        y0 += sOffsets!![i][mSegmentIndex]
                    }

                    mFillPaint.color = mLineDownColor

                    mOvalBounds.set(x - radius, y0 - radius, x + radius, y0 + radius)
                    for (i in 0 until mIntermediatePts) {
                        canvas.drawOval(mOvalBounds, mFillPaint)
                        mOvalBounds.top += sCellHeightExt
                        mOvalBounds.bottom += sCellHeightExt
                    }
                }
            }
        }

        if (mDrawUp) {
            mSolidPaint.color = mLineUpColor
            canvas.drawLine(x, 0f, x, mCircleBounds.top + mStrokeWidth / 2, mSolidPaint)
        }

        mSolidPaint.color = mCircleColor
        canvas.drawCircle(mCircleBounds.centerX(), mCircleBounds.centerY(), mRadius, mSolidPaint)
    }

    override fun getSuggestedMinimumWidth(): Int {
        return mRadius.toInt() * 4
    }

    override fun getSuggestedMinimumHeight(): Int {
        return mRadius.toInt() * 6
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return true
    }

    fun setInfo(
        segmentIndex: Int,
        circleColor: Int,
        drawUp: Boolean,
        lineUpColor: Int,
        drawDown: Boolean,
        lineDownColor: Int,
        drawDownDashed: Boolean,
        nIntermediatePts: Int
    ) {
        mSegmentIndex = segmentIndex
        mLineUpColor = lineUpColor
        mLineDownColor = lineDownColor
        mCircleColor = circleColor
        mDrawUp = drawUp
        mDrawDown = drawDown
        mDrawDownDashed = drawDownDashed
        mIntermediatePts = nIntermediatePts
    }

    companion object {
        private const val nOffsets = 6
        private var sCellHeight = 0.0f
        private var sCellHeightExt = 0.0f
        private var sOffsets: Array<FloatArray>? = Array(nOffsets) { FloatArray(0) }

        fun setCellHeight(cellHeight: Float) {
            sCellHeight = cellHeight
        }

        fun setCellHeightExt(cellHeightExt: Float) {
            sCellHeightExt = cellHeightExt
        }

        fun shouldSetCellHeight(): Boolean {
            return abs(sCellHeight) < 0.0001f
        }

        fun shouldSetCellHeightExt(): Boolean {
            return abs(sCellHeightExt) < 0.0001f
        }

        fun resetIntermediatePointsOffsets(nSegments: Int) {
            if (nSegments > 0) {
                sOffsets = Array(nOffsets) { FloatArray(nSegments) }
                for (i in 0 until nOffsets) {
                    sOffsets!![i] = FloatArray(nSegments)
                    for (j in 0 until nSegments) {
                        sOffsets!![i][j] = 0f
                    }
                }
            }
        }

        fun shouldSetOffset(i: Int, itemIndex: Int): Boolean {
            if (sOffsets != null) {
                if (i >= 0 && i < sOffsets!!.size) {
                    if (itemIndex >= 0 && itemIndex < sOffsets!![i].size) {
                        return sOffsets!![i][itemIndex] == 0f
                    }
                }
            }
            return false
        }

        fun setOffset(i: Int, segmentIndex: Int, offset: Float) {
            if (sOffsets != null && i >= 0 && i < sOffsets!!.size) {
                if (segmentIndex >= 0 && segmentIndex < sOffsets!![i].size) {
                    sOffsets!![i][segmentIndex] = offset
                }
            }
        }
    }
}
