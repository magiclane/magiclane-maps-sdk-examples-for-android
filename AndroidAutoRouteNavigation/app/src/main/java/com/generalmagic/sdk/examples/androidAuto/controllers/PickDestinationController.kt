/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("unused")

package com.generalmagic.sdk.examples.androidAuto.controllers

import androidx.car.app.CarContext
import com.generalmagic.sdk.core.ImageDatabase
import com.generalmagic.sdk.core.Rgba
import com.generalmagic.sdk.d3scene.BasicShapeDrawer
import com.generalmagic.sdk.d3scene.Canvas
import com.generalmagic.sdk.d3scene.ETextAlignment.Center
import com.generalmagic.sdk.d3scene.ETextStyle.BoldStyle
import com.generalmagic.sdk.d3scene.TextState
import com.generalmagic.sdk.examples.androidAuto.Service
import com.generalmagic.sdk.examples.androidAuto.model.UIActionModel
import com.generalmagic.sdk.examples.androidAuto.screens.FreeNavigationScreen
import com.generalmagic.sdk.examples.util.TextsUtil
import com.generalmagic.sdk.examples.util.Util
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util.postOnMain

class PickDestinationController(context: CarContext) : FreeNavigationScreen(context) {
    private var canvas: Canvas? = null
    private var shapesDrawer: BasicShapeDrawer? = null
    private var textureId: Int? = null

    override fun onCreate() {
        super.onCreate()
        onScreenCreated()
    }

    override fun onDestroy() {
        super.onDestroy()
        onScreenDestroy()
    }

    override fun updateData() {
        actionStripModelList.clear()
        mapActionStripModelList.clear()

        actionStripModelList.add(UIActionModel.backModel())
        actionStripModelList.add(UIActionModel(
            text = "Start",
            onClicked = {
                onStartPressed()
            }
        ))

        mapActionStripModelList.add(UIActionModel.panModel())
    }

    private fun onScreenCreated() {
        SdkCall.execute {
            canvas = mapView?.screen?.canvases?.get(0)
            val dpi = mapView?.screen?.openGLContext?.dpi ?: 0
            shapesDrawer = BasicShapeDrawer.produce(canvas)

            Service.instance?.surfaceAdapter?.onDrawFrameCustom = draw@{
                val center = canvas?.screen?.viewport?.center ?: return@draw
                val xCenter = center.x.toFloat()
                val yCenter = center.y.toFloat()

                if (textureId == null) {
                    val image = ImageDatabase.searchResultsPin ?: return@draw
                    textureId = shapesDrawer?.createTexture(image, 100, 100)
                }

                textureId?.let {
                    val size = Util.mmToPixels(10, dpi)

                    val left = xCenter - size / 2
                    val top = yCenter - size / 2
                    val right = xCenter + size / 2
                    val bottom = yCenter + size / 2

                    val color = Rgba.white().value

                    shapesDrawer?.drawTexturedRectangle(
                        it,
                        left,
                        top,
                        right,
                        bottom,
                        color,
                        true,
                        0.0f
                    )
                }

                shapesDrawer?.renderShapes(null, null)
            }
        }
    }

    private fun onScreenDestroy() {
        SdkCall.execute {
            textureId?.let {
                shapesDrawer?.deleteTexture(it)
            }
        }
        Service.instance?.surfaceAdapter?.onDrawFrameCustom = null
    }

    private fun onStartPressed() {
        SdkCall.execute {
            val coordinates = mapView?.cursorWgsPosition ?: return@execute

            val landmark = Landmark("Destination", coordinates)
            TextsUtil.fillLandmarkAddressInfo(mapView, landmark, true)

            postOnMain {
                Service.show(RoutesPreviewController(context, landmark))
            }
        }
    }

}
