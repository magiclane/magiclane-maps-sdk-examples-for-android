/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.controllers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.Toast
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.util.MainMapStatusFollowingProvider
import com.generalmagic.gemsdk.demo.util.StaticsHolder.Companion.gemMapScreen
import com.generalmagic.gemsdk.demo.util.StaticsHolder.Companion.getMainMapView
import com.generalmagic.gemsdk.models.ContentStoreItem
import com.generalmagic.gemsdk.models.TContentType
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.app_bar_layout.view.*

// --------------------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------------

class HelloViewController(context: Context, attrs: AttributeSet?) :
    AppLayoutController(context, attrs) {

    override fun onMapFollowStatusChanged(following: Boolean) {
        if (following) {
            bottomButtons()?.let { buttons ->
                buttons.bottomCenterButton?.visibility = android.view.View.GONE
                buttons.bottomRightButton?.visibility = android.view.View.GONE
                buttons.bottomLeftButton?.visibility = android.view.View.GONE
            }
        } else {
            displayGpsButton()
        }
    }
}

// --------------------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------------

open class ManyMapsController(context: Context, attrs: AttributeSet?) :
    AppLayoutController(context, attrs) {
    protected val contentStore = ContentStore()
    protected val mapViews = ArrayList<View>()
    protected lateinit var mapStyles: ArrayList<ContentStoreItem>
    protected var defaultStyle: ContentStoreItem? = null

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        @Suppress
        mapStyles = GEMSdkCall.execute {
            contentStore.getLocalContentList(TContentType.ECT_ViewStyleHighRes.value)
        } ?: ArrayList()

        defaultStyle = if (mapStyles.size > 0) {
            mapStyles[0]
        } else null
    }

    override fun doStop() {
        GEMSdkCall.checkCurrentThread()

        removeAllMapViews()
    }

    protected fun addMapView(rect: TRectF, styleId: Long) {
        GEMSdkCall.checkCurrentThread()
        val screen = gemMapScreen() ?: return

        val subview = View(screen, rect, null)
        getMainMapView()?.getCamera()?.let{
            subview.setCamera(it)
        }
        subview.preferences()?.setMapStyleById(styleId)
        mapViews.add(subview)
    }

    protected fun removeLastMapView() {
        GEMSdkCall.checkCurrentThread()
        if (mapViews.size > 0) {
            val mapView = mapViews.last()
            mapView.release()
            mapViews.remove(mapView)
        }
    }

    protected fun removeAllMapViews() {
        GEMSdkCall.checkCurrentThread()
        for (view in mapViews) {
            view.release()
        }
        mapViews.clear()
    }

    override fun onMapFollowStatusChanged(following: Boolean) {
        if (following) {
            bottomButtons()?.let { buttons ->
                buttons.bottomCenterButton?.visibility = android.view.View.GONE
                buttons.bottomRightButton?.visibility = android.view.View.GONE
                buttons.bottomLeftButton?.visibility = android.view.View.GONE
            }
        } else {
            displayGpsButton()
        }
    }
}

// --------------------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------------

class TwoTiledViewsController(context: Context, attrs: AttributeSet?) :
    ManyMapsController(context, attrs) {
    private val mainViewCoords = TRectF(0.0f, 0.5f, 1.0f, 1.0f)
    private val subViewCoords = TRectF(0.0f, 0.0f, 1.0f, 0.5f)

    private var mainView: View? = null

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        mainView = getMainMapView() ?: return

        GEMSdkCall.execute {
            val mainViewStyle = mainView?.preferences()?.getMapStyleId()
            mainView?.resize(mainViewCoords)

            var style = defaultStyle
            for (value in mapStyles) {
                if (value.getId() != mainViewStyle) {
                    style = value
                    break
                }
            }

            if (style != null) {
                addMapView(subViewCoords, style.getId())
            }
        }
    }

    override fun doStop() {
        GEMSdkCall.checkCurrentThread()

        mainView?.resize(TRectF(0.0f, 0.0f, 1.0f, 1.0f))
        removeAllMapViews() // false because is already inside engine call
    }

    override fun onMapFollowStatusChanged(following: Boolean) {
        super.onMapFollowStatusChanged(following)
        bottomButtons()?.bottomLeftButton?.setOnClickListener {
            GEMSdkCall.execute {
                MainMapStatusFollowingProvider.getInstance().doFollow()
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------------

class MultipleViewsController(context: Context, attrs: AttributeSet?) :
    ManyMapsController(context, attrs) {
    companion object {
        const val MAX_SUBMAPS_ON_SCREEN = 9
    }

    private val mapCoords = arrayOf(
        TRectF(0.0f, 0.0f, 0.33f, 0.33f),
        TRectF(0.33f, 0.0f, 0.66f, 0.33f),
        TRectF(0.66f, 0.0f, 1.0f, 0.33f),

        TRectF(0.0f, 0.33f, 0.33f, 0.66f),
        TRectF(0.33f, 0.33f, 0.66f, 0.66f),
        TRectF(0.66f, 0.33f, 1.0f, 0.66f),

        TRectF(0.0f, 0.66f, 0.33f, 1.0f),
        TRectF(0.33f, 0.66f, 0.66f, 1.0f),
        TRectF(0.66f, 0.66f, 1.0f, 1.0f)
    )

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        prepareAddRemoveButtons()
    }

    override fun onMapFollowStatusChanged(following: Boolean) {
        super.onMapFollowStatusChanged(following)
        prepareAddRemoveButtons()
    }

    private fun doAddView() {
        GEMSdkCall.execute { // sync mapViews.size across UI and Engine threads
            if (mapViews.size >= MAX_SUBMAPS_ON_SCREEN) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "This demo has max 9 views!", Toast.LENGTH_SHORT).show()
                }
                return@execute
            }

            val rectf = mapCoords[mapViews.size]

            val style = if (mapStyles.size > mapViews.size) mapStyles[mapViews.size]
            else {
                defaultStyle ?: return@execute
            }

            addMapView(rectf, style.getId())
        }
    }

    private fun prepareAddRemoveButtons() {
        bottomButtons()?.let { buttons ->
            buttons.bottomRightButton?.let {
                it.visibility = android.view.View.VISIBLE
                buttonAsAdd(it)
                it.setOnClickListener { GEMSdkCall.execute { doAddView() } }
            }

            buttons.bottomCenterButton?.let {
                it.visibility = android.view.View.VISIBLE
                buttonAsDelete(it)
                it.setOnClickListener { GEMSdkCall.execute { removeLastMapView() } }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------------
