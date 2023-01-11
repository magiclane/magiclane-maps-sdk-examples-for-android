/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

@file:Suppress("unused")

package com.magiclane.sdk.examples.androidauto.androidAuto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.d3scene.BasicShapeDrawer
import com.magiclane.sdk.d3scene.Canvas
import com.magiclane.sdk.examples.androidauto.androidAuto.Service
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.screens.FreeNavigationScreen
import com.magiclane.sdk.examples.androidauto.util.TextsUtil
import com.magiclane.sdk.examples.androidauto.util.Util
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util.postOnMain

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
        horizontalActions.clear()
        verticalActions.clear()

        horizontalActions.add(UIActionModel.backModel())
        horizontalActions.add(UIActionModel(
            text = "Start",
            onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                onStartPressed()
            }
        ))

        verticalActions.add(UIActionModel.panModel())
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
            textureId = null
            Service.instance?.surfaceAdapter?.onDrawFrameCustom = null
        }
    }

    private fun onStartPressed() {
        SdkCall.execute {
            val coordinates = mapView?.cursorWgsPosition ?: return@execute

            val landmark = Landmark("Destination", coordinates)
            TextsUtil.fillLandmarkAddressInfo(mapView, landmark, true)

            postOnMain {
                Service.pushScreen(RoutesPreviewController(context, landmark), true)
            }
        }
    }

}
