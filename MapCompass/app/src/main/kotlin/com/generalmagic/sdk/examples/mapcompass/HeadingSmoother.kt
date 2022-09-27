// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

// -------------------------------------------------------------------------------------------------

@file:Suppress("unused")

// -------------------------------------------------------------------------------------------------

package com.generalmagic.sdk.examples.mapcompass

// -------------------------------------------------------------------------------------------------

/**
 * Will smooth compass sensor data.
 */
class HeadingSmoother
{
    // ---------------------------------------------------------------------------------------------
    
    private var mHeading = 0.0
    private val mHistory = ArrayList<Pair<Float, Float>>()
    private var mSumSin = 0.0f
    private var mSumCos = 0.0f

    // ---------------------------------------------------------------------------------------------

    fun getHeading(): Double = mHeading

    // ---------------------------------------------------------------------------------------------
    
    fun reset()
    {
        mSumSin = 0.0f
        mSumCos = 0.0f
        mHistory.clear()
    }

    // ---------------------------------------------------------------------------------------------

    fun update(inHeading: Double): Double
    {
        if (mHistory.size > kHistoryMaxSize)
        {
            //trim history & adjust sums
            mSumSin -= mHistory.first().first
            mSumCos -= mHistory.first().second
            mHistory.removeFirst()
        }

        var heading = degToRad(inHeading)
        mHistory.add(Pair(kotlin.math.sin(heading).toFloat(), kotlin.math.cos(heading).toFloat()))
        mSumSin += mHistory.last().first
        mSumCos += mHistory.last().second

        heading = kotlin.math.atan2(
            mSumSin.toDouble() / mHistory.size,
            mSumCos.toDouble() / mHistory.size
        )

        heading = radToDeg(heading)

        mHeading = normalizeAngle(heading)
        return mHeading
    }

    // ---------------------------------------------------------------------------------------------

    companion object
    {
        const val kHistoryMaxSize = 50

        fun radToDeg(a: Double): Double
        {
            return a * 57.29577951308232
        }

        fun degToRad(a: Double): Double
        {
            return a * 0.017453292519943295
        }

        fun normalizeAngle(h: Double): Double
        {
            if (h < 0)
            {
                return h + 360f
            }
            else if (h > 360f)
            {
                return h - 360f
            }
            return h
        }
    }

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
