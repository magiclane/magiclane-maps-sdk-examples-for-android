/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.demo.activities.mainactivity.controllers

import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.ContentStoreItem
import com.generalmagic.sdk.content.EContentStoreItemStatus
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.RectF
import com.generalmagic.sdk.d3scene.MapView
import com.generalmagic.sdk.demo.app.GEMApplication
import com.generalmagic.sdk.demo.app.MapLayoutController
import com.generalmagic.sdk.demo.app.elements.ButtonsDecorator
import com.generalmagic.sdk.util.SdkCall

class HelloViewController(context: Context, attrs: AttributeSet?) :
    MapLayoutController(context, attrs) {

    override fun doBackPressed(): Boolean = false
}

open class ManyMapsController(context: Context, attrs: AttributeSet?) :
    MapLayoutController(context, attrs) {
    protected val contentStore = ContentStore()
    protected val mapViews = ArrayList<MapView>()
    protected var mapStyles: ArrayList<ContentStoreItem> = ArrayList()
    protected var defaultStyle: ContentStoreItem? = null

    override fun onCreated() {
        super.onCreated()

        mapStyles = SdkCall.execute {
            contentStore.getLocalContentList(EContentType.ECT_ViewStyleHighRes.value)
        } ?: ArrayList()

        defaultStyle = if (mapStyles.size > 0) {
            mapStyles[0]
        } else null
    }

    override fun doStop() {
        SdkCall.execute {
            removeAllMapViews()
        }
    }

    protected fun addMapView(rect: RectF, styleId: Long): Boolean {
        return SdkCall.execute {

            val screen = GEMApplication.gemMapScreen() ?: return@execute false

            val subview = MapView.produce(screen, rect, null, null) ?: return@execute false
            GEMApplication.getMainMapView()?.getCamera()?.let {
                subview.setCamera(it)
            }
            subview.preferences()?.setMapStyleById(styleId)
            mapViews.add(subview)
            return@execute true
        } ?: false
    }

    protected fun removeLastMapView(): Boolean {
        return SdkCall.execute {
            if (mapViews.size <= 0) return@execute false

            val mapView = mapViews.last()
            mapView.release()
            mapViews.remove(mapView)
            return@execute true
        } ?: false
    }

    protected fun removeAllMapViews() {
        SdkCall.execute {
            for (view in mapViews) {
                view.release()
            }
            mapViews.clear()
        }
    }
}

class TwoTiledViewsController(context: Context, attrs: AttributeSet?) :
    ManyMapsController(context, attrs) {
    private val mainViewCoords = RectF(0.0f, 0.5f, 1.0f, 1.0f)
    private val subViewCoords = RectF(0.0f, 0.0f, 1.0f, 0.5f)

    override fun onCreated() {
        super.onCreated()

        SdkCall.execute {
            val mainView = GEMApplication.getMainMapView() ?: return@execute
            val mainViewStyle = mainView.preferences()?.getMapStyleId()
            mainView.resize(mainViewCoords)

            var style = defaultStyle
            for (value in mapStyles) {
                if(value.getStatus() != EContentStoreItemStatus.Completed){
                    continue
                }
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
        SdkCall.execute {
            val mainView = GEMApplication.getMainMapView()
            mainView?.resize(RectF(0.0f, 0.0f, 1.0f, 1.0f))
            removeAllMapViews()
        }
    }
}

class MultipleViewsController(context: Context, attrs: AttributeSet?) :
    ManyMapsController(context, attrs) {
    companion object {
        const val MAX_SUBMAPS_ON_SCREEN = 9
    }

    private val mapCoords = arrayOf(
        RectF(0.0f, 0.0f, 0.33f, 0.33f),
        RectF(0.33f, 0.0f, 0.66f, 0.33f),
        RectF(0.66f, 0.0f, 1.0f, 0.33f),

        RectF(0.0f, 0.33f, 0.33f, 0.66f),
        RectF(0.33f, 0.33f, 0.66f, 0.66f),
        RectF(0.66f, 0.33f, 1.0f, 0.66f),

        RectF(0.0f, 0.66f, 0.33f, 1.0f),
        RectF(0.33f, 0.66f, 0.66f, 1.0f),
        RectF(0.66f, 0.66f, 1.0f, 1.0f)
    )

    override fun onCreated() {
        super.onCreated()

        setAddButtonVisible(true)
        setRemoveButtonVisible(true)
    }

    override fun onMapFollowStatusChanged(following: Boolean) {}

    private fun doAddView(): Boolean {
        return SdkCall.execute { // sync mapViews.size across UI and Engine threads
            if (mapViews.size >= MAX_SUBMAPS_ON_SCREEN) {
                GEMApplication.postOnMain {
                    Toast.makeText(context, "This demo has max 9 views!", Toast.LENGTH_SHORT).show()
                }
                return@execute false
            }

            val rectf = mapCoords[mapViews.size]

            var style : ContentStoreItem? = if (mapStyles.size > mapViews.size) mapStyles[mapViews.size]
            else {
                defaultStyle ?: return@execute false
            }

            if(style?.getStatus() != EContentStoreItemStatus.Completed){
                style = defaultStyle
            }
            
            style?.let { return@execute addMapView(rectf, it.getId()) }

            return@execute false
        } ?: false
    }

    private fun setAddButtonVisible(visible: Boolean) {
        val button = getBottomLeftButton() ?: return

        if (visible) {
            ButtonsDecorator.buttonAsAdd(context, button) {
                doAddView()
            }

            button.visibility = android.view.View.VISIBLE
        } else {
            button.visibility = android.view.View.GONE
        }
    }

    private fun setRemoveButtonVisible(visible: Boolean) {
        val button = getBottomRightButton() ?: return

        if (visible) {
            ButtonsDecorator.buttonAsDelete(context, button) {
                removeLastMapView()
            }

            button.visibility = android.view.View.VISIBLE
        } else {
            button.visibility = android.view.View.GONE
        }
    }
}
