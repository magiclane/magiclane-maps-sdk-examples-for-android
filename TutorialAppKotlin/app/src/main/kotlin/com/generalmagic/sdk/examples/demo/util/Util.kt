/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.generalmagic.sdk.examples.demo.util

import android.content.Context
import android.graphics.*
import android.graphics.Rect
import android.graphics.drawable.*
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.generalmagic.sdk.*
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.EContentStoreItemStatus
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.d3scene.OverlayItem
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.routesandnavigation.*
import com.generalmagic.sdk.util.*
import com.generalmagic.sdk.util.Util.moveFile
import com.generalmagic.sdk.util.UtilToBitmap.imageToBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.util.*

object Util {
    fun getFileLastModifiedDate(pathStr: String): Date {
        val file = File(pathStr)
        return Date(file.lastModified())
    }

    fun getFileSize(pathStr: String): Long {
        val file = File(pathStr)
        return file.length()
    }

    fun isInternalLog(filepath: String): Boolean {
        val internalDir = GEMApplication.getInternalRecordsPath()

        return filepath.startsWith(internalDir)
    }

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

    fun createBitmap(img: RoadInfoImage?, width: Int, height: Int): Pair<Int, Bitmap?> {
        img ?: return Pair(0, null)

        return SdkCall.execute {
            val resultPair = imageToBitmap(img, width, height)
            val bmp = createBitmap(resultPair?.second, resultPair?.first ?: 0, height)

            return@execute Pair(resultPair?.first ?: 0, bmp)
        } ?: Pair(0, null)
    }

    fun createBitmap(
        img: LaneImage?,
        width: Int,
        height: Int,
        bkColor: Rgba? = null,
        activeColor: Rgba? = null,
        inactiveColor: Rgba? = null
    ): Pair<Int, Bitmap?> {
        img ?: return Pair(0, null)

        return SdkCall.execute {
            val resultPair =
                imageToBitmap(
                    img,
                    width,
                    height,
                    bkColor,
                    activeColor,
                    inactiveColor
                )
            val bmp = createBitmap(resultPair?.second, resultPair?.first ?: 0, height)

            return@execute Pair(resultPair?.first ?: 0, bmp)
        } ?: Pair(0, null)
    }

    fun createBitmap(img: SignpostImage?, width: Int, height: Int): Pair<Int, Bitmap?> {
        img ?: return Pair(0, null)

        return SdkCall.execute {
            val resultPair = imageToBitmap(img, width, height)
            val bmp = createBitmap(resultPair?.second, resultPair?.first ?: 0, height)

            return@execute Pair(resultPair?.first ?: 0, bmp)
        } ?: Pair(0, null)
    }

    fun createBitmap(
        img: AbstractGeometryImage?,
        width: Int,
        height: Int,
        activeInnerColor: Rgba? = null,
        activeOuterColor: Rgba? = null,
        inactiveInnerColor: Rgba? = null,
        inactiveOuterColor: Rgba? = null
    ): Bitmap? {
        img ?: return null

        return SdkCall.execute {
            val byteArray = imageToBitmap(
                img,
                width,
                height,
                activeInnerColor,
                activeOuterColor,
                inactiveInnerColor,
                inactiveOuterColor
            )
            return@execute createBitmap(byteArray, width, height)
        }
    }

    fun createBitmap(img: Image?, width: Int, height: Int): Bitmap? {
        img ?: return null
        return SdkCall.execute {
            val byteArray = imageToBitmap(img, width, height)
            return@execute createBitmap(byteArray, width, height)
        }
    }

    fun createBitmapFromNV21(data: ByteArray?, width: Int, height: Int): Bitmap? {
        data ?: return null

        val out = ByteArrayOutputStream()
        val yuvImage = YuvImage(data, ImageFormat.NV21, width, height, null)
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 50, out)
        val imageBytes: ByteArray = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
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

    fun getImageIdAsImage(id: Int): Image? {
        return SdkCall.execute { ImageDatabase().getImageById(id) }
    }

    fun getImageIdAsBitmap(id: Int, width: Int, height: Int): Bitmap? {
        return SdkCall.execute {
            val image = getImageIdAsImage(id)
            return@execute createBitmap(image, width, height)
        }
    }

    fun getPhonePath(context: Context?): String =
        com.generalmagic.sdk.util.Util.getAppDirInternalPath(context)

    fun getSdCardPath(context: Context?): String =
        com.generalmagic.sdk.util.Util.getAppFilesDirExternalPath(context)

    fun getContentStoreStatusIconId(status: EContentStoreItemStatus): Int {
        return when (status) {
            EContentStoreItemStatus.Unavailable -> {
                SdkIcons.Other_UI.Button_DownloadOnServer_v2.value
            }

            EContentStoreItemStatus.Completed -> {
                SdkIcons.Other_UI.Button_DownloadOnDevice_v2.value
            }

            EContentStoreItemStatus.DownloadRunning -> {
                SdkIcons.Other_UI.Button_DownloadPause.value
            }

            EContentStoreItemStatus.DownloadQueued,
            EContentStoreItemStatus.DownloadWaitingFreeNetwork -> {
                SdkIcons.Other_UI.Button_DownloadQueue.value
            }

            EContentStoreItemStatus.DownloadWaiting,
            EContentStoreItemStatus.Paused -> {
                SdkIcons.Other_UI.Button_DownloadRefresh_v2.value
            }

            else -> {
                SdkIcons.E.InvalidId.value
            }
        }
    }

    fun getContentStoreStatusAsBitmap(
        status: EContentStoreItemStatus,
        width: Int,
        height: Int
    ): Bitmap? {
        return getImageIdAsBitmap(getContentStoreStatusIconId(status), width, height)
    }

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

    fun getSizeInPixels(dpi: Int): Int {
        val metrics = GEMApplication.applicationContext().resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpi.toFloat(), metrics)
            .toInt()
    }

    fun getImageAspectRatio(marker: OverlayItem?): Float {
        val image = marker?.image ?: return 1.0f
        var fAspectRatio = 1.0f

        val size = image.size
        if (size != null && size.height != 0) {
            fAspectRatio = (size.width.toFloat() / size.height.toFloat())
        }

        return fAspectRatio
    }

    fun moveFileToDir(filePath: String, dirPath: String): SdkError {
        val from = File(filePath)
        if (!from.exists()) {
            return SdkError.InvalidInput
        }

        val dir = File(dirPath)
        try {
            dir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
            return SdkError.AccessDenied
        }

        val to = File(dirPath + File.separator + from.name)
        if (to.exists()) {
            return SdkError.Exist
        } else if (to.isDirectory) {
            return SdkError.InvalidInput
        }

        moveFile(from, dir)
//            if(!from.renameTo(to))
//                return SdkError.KInternalAbort

        return SdkError.NoError
    }

    fun downloadSecondStyle() {
        // download another style
        SdkCall.execute {
            val listener = object : ProgressListener() {
                override fun notifyComplete(reason: SdkError, hint: String) {
                    // select the downloaded style
                    val result =
                        ContentStore().getStoreContentList(EContentType.ViewStyleHighRes)
                    result ?: return

                    val contentStoreStyles = result.first
                    if (contentStoreStyles.isNotEmpty() && contentStoreStyles.size > 1) {
                        val styleListener = object : ProgressListener() {
                            override fun notifyComplete(reason: SdkError, hint: String) {}
                        }

                        for (style in contentStoreStyles) {
                            if (style.name != null && style.name!!
                                    .contains("Satellite")
                            ) {
                                style.asyncDownload(
                                    styleListener,
                                    GemSdk.EDataSavePolicy.UseDefault,
                                    true
                                )
                                break
                            }
                        }
                    }
                }
            }

            ContentStore().asyncGetStoreContentList(
                EContentType.ViewStyleHighRes,
                listener
            )
        }
    }
}

