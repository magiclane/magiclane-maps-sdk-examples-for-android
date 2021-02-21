/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.activities.mainactivity.controllers

import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import com.generalmagic.gemsdk.ContentStore
import com.generalmagic.gemsdk.TRectF
import com.generalmagic.gemsdk.MapView
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.app.MapLayoutController
import com.generalmagic.gemsdk.demo.app.elements.ButtonsDecorator
import com.generalmagic.gemsdk.models.ContentStoreItem
import com.generalmagic.gemsdk.models.TContentType
import com.generalmagic.gemsdk.util.GEMSdkCall

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

        mapStyles = GEMSdkCall.execute {
            contentStore.getLocalContentList(TContentType.ECT_ViewStyleHighRes.value)
        } ?: ArrayList()

        defaultStyle = if (mapStyles.size > 0) {
            mapStyles[0]
        } else null
    }

    override fun doStop() {
        GEMSdkCall.execute {
            removeAllMapViews()
        }
    }

    protected fun addMapView(rect: TRectF, styleId: Long): Boolean {
        return GEMSdkCall.execute {

            val screen = GEMApplication.gemMapScreen() ?: return@execute false

            val subview = MapView.produce(screen, rect, null) ?: return@execute false
            GEMApplication.getMainMapView()?.getCamera()?.let {
                subview.setCamera(it)
            }
            subview.preferences()?.setMapStyleById(styleId)
            mapViews.add(subview)
            return@execute true
        } ?: false
    }

    protected fun removeLastMapView(): Boolean {
        return GEMSdkCall.execute {
            if (mapViews.size <= 0) return@execute false

            val mapView = mapViews.last()
            mapView.release()
            mapViews.remove(mapView)
            return@execute true
        } ?: false
    }

    protected fun removeAllMapViews() {
        GEMSdkCall.execute {
            for (view in mapViews) {
                view.release()
            }
            mapViews.clear()
        }
    }
}

class TwoTiledViewsController(context: Context, attrs: AttributeSet?) :
    ManyMapsController(context, attrs) {
    private val mainViewCoords = TRectF(0.0f, 0.5f, 1.0f, 1.0f)
    private val subViewCoords = TRectF(0.0f, 0.0f, 1.0f, 0.5f)

    override fun onCreated() {
        super.onCreated()

        GEMSdkCall.execute {
            val mainView = GEMApplication.getMainMapView() ?: return@execute
            val mainViewStyle = mainView.preferences()?.getMapStyleId()
            mainView.resize(mainViewCoords)

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
        GEMSdkCall.execute {
            val mainView = GEMApplication.getMainMapView()
            mainView?.resize(TRectF(0.0f, 0.0f, 1.0f, 1.0f))
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

    override fun onCreated() {
        super.onCreated()

        setAddButtonVisible(true)
        setRemoveButtonVisible(true)
    }

    override fun onMapFollowStatusChanged(following: Boolean) {}

    private fun doAddView(): Boolean {
        return GEMSdkCall.execute { // sync mapViews.size across UI and Engine threads
            if (mapViews.size >= MAX_SUBMAPS_ON_SCREEN) {
                GEMApplication.postOnMain {
                    Toast.makeText(context, "This demo has max 9 views!", Toast.LENGTH_SHORT).show()
                }
                return@execute false
            }

            val rectf = mapCoords[mapViews.size]

            val style = if (mapStyles.size > mapViews.size) mapStyles[mapViews.size]
            else {
                defaultStyle ?: return@execute false
            }

            return@execute addMapView(rectf, style.getId())
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
