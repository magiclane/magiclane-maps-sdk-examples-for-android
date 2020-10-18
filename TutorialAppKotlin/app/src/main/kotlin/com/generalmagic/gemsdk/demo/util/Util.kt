/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.util

import android.content.Context
import android.graphics.*
import android.graphics.drawable.*
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.generalmagic.gemsdk.Route
import com.generalmagic.gemsdk.SignpostImageRef
import com.generalmagic.gemsdk.TRgba
import com.generalmagic.gemsdk.models.*
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMHelper
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdk.util.GemIcons
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.channels.FileChannel

class Util {
    companion object {
        fun Double.round(decimals: Int = 2): Double {
            return String.format("%.${decimals}f", this).toDouble()
        }

        fun setPanelBackground(background: Drawable?, color: Int) {
            var bgnd = background

            if (background is LayerDrawable) {
                bgnd = background.getDrawable(1)
            }

            when (bgnd) {
                is ShapeDrawable -> bgnd.paint.color = color
                is GradientDrawable -> bgnd.setColor(color)
                is ColorDrawable -> bgnd.color = color
                is InsetDrawable -> (bgnd.drawable as GradientDrawable).setColor(color)
                else -> {
                }
            }
        }

        fun mmToPixels(context: Context, mm: Int): Int {
            val metrics: DisplayMetrics = context.resources.displayMetrics
            return mmToPixels(metrics.density * 160, mm)
        }

        fun mmToPixels(dpi: Float, mm: Int): Int {
            return (((dpi * mm) / 25.4) + 0.5).toInt()
        }

        fun grayOutViewBackground(value: View, doGray: Boolean = true) {
            // if not grayed
            if (doGray) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    value.background.colorFilter =
                        BlendModeColorFilter(Color.GRAY, BlendMode.MULTIPLY)
                } else {
                    @Suppress("DEPRECATION")
                    value.background.setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY)
                }

                value.tag = "grayed"
            } else {
                value.background.colorFilter = null
                value.tag = ""
            }
        }

        fun getColor(gemSdkColor: Int): Int {
            val r = 0x000000ff and gemSdkColor
            val g = 0x000000ff and (gemSdkColor shr 8)
            val b = 0x000000ff and (gemSdkColor shr 16)
            val a = 0x000000ff and (gemSdkColor shr 24)

            return Color.argb(a, r, g, b)
        }

        // ---------------------------------------------------------------------------------------------

        fun createBitmap(img: RoadInfoImageRef?, width: Int, height: Int): Pair<Int, Bitmap?> {
            GEMSdkCall.checkCurrentThread()

            img ?: return Pair(0, null)

            val resultPair = GEMHelper.imageToBitmap(img, width, height)
            val bmp = createBitmap(resultPair?.second, resultPair?.first ?: 0, height)

            return Pair(resultPair?.first ?: 0, bmp)
        }

        fun createBitmap(
            img: LaneImageRef?,
            width: Int,
            height: Int,
            bkColor: TRgba? = null,
            activeColor: TRgba? = null,
            inactiveColor: TRgba? = null
        ): Pair<Int, Bitmap?> {
            GEMSdkCall.checkCurrentThread()

            img ?: return Pair(0, null)

            val resultPair =
                GEMHelper.imageToBitmap(img, width, height, bkColor, activeColor, inactiveColor)
            val bmp = createBitmap(resultPair?.second, resultPair?.first ?: 0, height)

            return Pair(resultPair?.first ?: 0, bmp)
        }

        fun createBitmap(img: SignpostImageRef?, width: Int, height: Int): Pair<Int, Bitmap?> {
            GEMSdkCall.checkCurrentThread()

            img ?: return Pair(0, null)
            val resultPair = GEMHelper.imageToBitmap(img, width, height)
            val bmp = createBitmap(resultPair?.second, resultPair?.first ?: 0, height)

            return Pair(resultPair?.first ?: 0, bmp)
        }

        // ---------------------------------------------------------------------------------------------

        fun createBitmap(
            img: AbstractGeometryImageRef?,
            width: Int,
            height: Int,
            activeInnerColor: TRgba? = null,
            activeOuterColor: TRgba? = null,
            inactiveInnerColor: TRgba? = null,
            inactiveOuterColor: TRgba? = null
        ): Bitmap? {
            GEMSdkCall.checkCurrentThread()

            img ?: return null
            val byteArray = GEMHelper.imageToBitmap(
                img,
                width,
                height,
                activeInnerColor,
                activeOuterColor,
                inactiveInnerColor,
                inactiveOuterColor
            )
            return createBitmap(byteArray, width, height)
        }

        fun createBitmap(img: Image?, width: Int, height: Int): Bitmap? {
            GEMSdkCall.checkCurrentThread()

            img ?: return null
            val byteArray = GEMHelper.imageToBitmap(img, width, height)
            return createBitmap(byteArray, width, height)
        }

        fun createBitmapFromNV21(data: ByteArray?, width: Int, height: Int): Bitmap? {
            data ?: return null

            val out = ByteArrayOutputStream()
            val yuvImage = YuvImage(data, ImageFormat.NV21, width, height, null)
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 50, out)
            val imageBytes: ByteArray = out.toByteArray()
            val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            return image
        }

        fun createBitmap(byteArray: ByteArray?, width: Int, height: Int): Bitmap? {
            if (byteArray == null || width <= 0 || height <= 0) {
                return null
            }

            val byteBuffer: ByteBuffer = ByteBuffer.wrap(byteArray)
            byteBuffer.order(ByteOrder.nativeOrder())
            val buffer: IntBuffer = byteBuffer.asIntBuffer()
            val imgArray = IntArray(buffer.remaining())
            buffer.get(imgArray)
            val result = Bitmap.createBitmap(imgArray, width, height, Bitmap.Config.ARGB_8888)
            result.density = DisplayMetrics.DENSITY_MEDIUM
            return result
        }

        fun createBitmap(byteBuffer: ByteBuffer?, width: Int, height: Int): Bitmap? {
            if (byteBuffer == null || width <= 0 || height <= 0) {
                return null
            }

            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            result.copyPixelsFromBuffer(byteBuffer)
            result.density = DisplayMetrics.DENSITY_MEDIUM
            return result
        }

        fun getImageIdAsImage(id: Int, width: Int, height: Int): Image? {
            GEMSdkCall.checkCurrentThread()
            return ImageDatabase().getImageById(id)
        }

        fun getImageIdAsBitmap(id: Int, width: Int, height: Int): Bitmap? {
            GEMSdkCall.checkCurrentThread()

            val image = getImageIdAsImage(id, width, height)
            return createBitmap(image, width, height)
        }

        // ---------------------------------------------------------------------------------------------

        const val BIG_TRAFFIC_DELAY_IN_MINUTES = 10
        const val ROAD_BLOCK_DELAY = 3600 // one hour

        fun getTrafficEventsDelay(route: Route, bCheckUserRoadblocks: Boolean = false): Int {
            val list = route.getTrafficEvents() ?: return 0

            var trafficEventsDelay = 0

            var roadBlockConsidered = false

            for (index in 0 until list.size) {
                val event = list[index]
                if (bCheckUserRoadblocks || !event.isUserRoadblock()) {
                    val isRoadblock = event.isRoadblock()
                    if (isRoadblock) {
                        if (roadBlockConsidered) {
                            trafficEventsDelay += ROAD_BLOCK_DELAY
                            roadBlockConsidered = true
                        }
                    } else {
                        trafficEventsDelay += event.getDelay()
                    }
                }
            }

            return trafficEventsDelay
        }

        fun getTrafficIconId(route: Route): Int {
            val trafficDelayInMinutes = getTrafficEventsDelay(route)

            return when {
                trafficDelayInMinutes == 0 -> GemIcons.Layout.NavigationLayout_GreenBall.value
                trafficDelayInMinutes < BIG_TRAFFIC_DELAY_IN_MINUTES -> GemIcons.Layout.NavigationLayout_YellowBall.value
                else -> return GemIcons.Layout.NavigationLayout_RedBall.value
            }
        }

        // ---------------------------------------------------------------------------------------------

        fun getContentStoreStatusIconId(status: TContentStoreItemStatus): Int {
            return when (status) {
                TContentStoreItemStatus.ECIS_Unavailable -> {
                    GemIcons.Other_UI.Button_DownloadOnServer_v2.value
                }

                TContentStoreItemStatus.ECIS_Completed -> {
                    GemIcons.Other_UI.Button_DownloadOnDevice_v2.value
                }

                TContentStoreItemStatus.ECIS_DownloadRunning -> {
                    GemIcons.Other_UI.Button_DownloadPause.value
                }

                TContentStoreItemStatus.ECIS_DownloadQueued,
                TContentStoreItemStatus.ECIS_DownloadWaitingFreeNetwork -> {
                    GemIcons.Other_UI.Button_DownloadQueue.value
                }

                TContentStoreItemStatus.ECIS_DownloadWaiting,
                TContentStoreItemStatus.ECIS_Paused -> {
                    GemIcons.Other_UI.Button_DownloadRefresh_v2.value
                }

                else -> {
                    GemIcons.E.KInvalidId.value
                }
            }
        }

        fun getContentStoreStatusAsBitmap(
            status: TContentStoreItemStatus,
            width: Int,
            height: Int
        ): Bitmap? {
            return getImageIdAsBitmap(getContentStoreStatusIconId(status), width, height)
        }

        // ---------------------------------------------------------------------------------------------

        fun getTextWidth(textView: TextView?, maxWidth: Int): Int {
            val widthMeasureSpec =
                View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST)
            val heightMeasureSpec =
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

            textView?.measure(widthMeasureSpec, heightMeasureSpec)
            return textView?.measuredWidth ?: 0
        }

        fun doTextGroupFitInsideWidth(text1: TextView?, text2: TextView?, width: Int): Boolean {
            val w1 = getTextWidth(text1, Int.MAX_VALUE)
            val w2 = getTextWidth(text2, Int.MAX_VALUE)

            return (w1 + w2) <= width
        }

        // ---------------------------------------------------------------------------------------------

        fun getSizeInPixels(context: Context, dpi: Int): Int {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getMetrics(metrics)
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpi.toFloat(), metrics)
                .toInt()
        }

        fun getImageAspectRatio(marker: Marker?): Float {
            val image = marker?.getImage() ?: return 1.0f
            var fAspectRatio = 1.0f

            val size = image.getSize()
            if (size != null && size.height() != 0) {
                fAspectRatio = (size.width().toFloat() / size.height().toFloat())
            }

            return fAspectRatio
        }

        // ---------------------------------------------------------------------------------------------

        fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
            val lhsLength = lhs.length
            val rhsLength = rhs.length

            var cost = IntArray(lhsLength + 1) { it }
            var newCost = IntArray(lhsLength + 1) { 0 }

            for (i in 1..rhsLength) {
                newCost[0] = i

                for (j in 1..lhsLength) {
                    val editCost = if (lhs[j - 1] == rhs[i - 1]) 0 else 1

                    val costReplace = cost[j - 1] + editCost
                    val costInsert = cost[j] + 1
                    val costDelete = newCost[j - 1] + 1

                    newCost[j] = minOf(costInsert, costDelete, costReplace)
                }

                val swap = cost
                cost = newCost
                newCost = swap
            }

            return cost[lhsLength]
        }

        fun moveFileToDir(filePath: String, dirPath: String): GEMError {
            val from = File(filePath)
            if (!from.exists()) {
                return GEMError.KInvalidInput
            }

            val dir = File(dirPath)
            try {
                dir.mkdirs()
            } catch (e: Exception) {
                e.printStackTrace()
                return GEMError.KAccessDenied
            }

            val to = File(dirPath + File.separator + from.name)
            if (to.exists()) {
                return GEMError.KExist
            } else if (to.isDirectory) {
                return GEMError.KInvalidInput
            }

            moveFile(from, dir)
//            if(!from.renameTo(to))
//                return GEMError.KInternalAbort

            return GEMError.KNoError
        }

        fun moveFile(file: File, dir: File): File? {
            val newFile = File(dir, file.name)
            var outputChannel: FileChannel? = null
            var inputChannel: FileChannel? = null

            val result: File?
            try {
                outputChannel = FileOutputStream(newFile).channel
                inputChannel = FileInputStream(file).channel
                inputChannel.transferTo(0, inputChannel.size(), outputChannel)
                inputChannel.close()
                file.delete()
                result = newFile
            } finally {
                inputChannel?.close()
                outputChannel?.close()
            }
            return result
        }
    }
}
