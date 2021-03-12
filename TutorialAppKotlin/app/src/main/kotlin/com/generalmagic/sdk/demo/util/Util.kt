/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.demo.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.Rect
import android.graphics.drawable.*
import android.os.Build
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.generalmagic.sdk.*
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.EContentStoreItemStatus
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.d3scene.Marker
import com.generalmagic.sdk.demo.app.GEMApplication
import com.generalmagic.sdk.routingandnavigation.Route
import com.generalmagic.sdk.routingandnavigation.SignpostImage
import com.generalmagic.sdk.util.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.channels.FileChannel
import java.util.*

class Util {
    companion object {
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

        fun exportVideo(context: Context, videoFile: File?, outDir: File?): File? {
            if (outDir == null) return null
            if (videoFile == null) return null

            val newFile = moveFile(videoFile, outDir)
            if (newFile != null) {
                val cr = context.contentResolver

                val values = ContentValues()
                values.put(MediaStore.Video.Media.TITLE, videoFile.name)
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                values.put(MediaStore.Video.Media.DATA, newFile.absolutePath)
                val uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
                return newFile
            }

            return null
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
                val resultPair = SdkHelper.imageToBitmap(img, width, height)
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
                    SdkHelper.imageToBitmap(img, width, height, bkColor, activeColor, inactiveColor)
                val bmp = createBitmap(resultPair?.second, resultPair?.first ?: 0, height)

                return@execute Pair(resultPair?.first ?: 0, bmp)
            } ?: Pair(0, null)
        }

        fun createBitmap(img: SignpostImage?, width: Int, height: Int): Pair<Int, Bitmap?> {
            img ?: return Pair(0, null)

            return SdkCall.execute {
                val resultPair = SdkHelper.imageToBitmap(img, width, height)
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
                val byteArray = SdkHelper.imageToBitmap(
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
                val byteArray = SdkHelper.imageToBitmap(img, width, height)
                return@execute createBitmap(byteArray, width, height)
            }
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

        fun getImageIdAsImage(id: Int): Image? {
            return SdkCall.execute { ImageDatabase().getImageById(id) }
        }

        fun getImageIdAsBitmap(id: Int, width: Int, height: Int): Bitmap? {
            return SdkCall.execute {
                val image = getImageIdAsImage(id)
                return@execute createBitmap(image, width, height)
            }
        }

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
                trafficDelayInMinutes == 0 -> SdkIcons.Layout.NavigationLayout_GreenBall.value
                trafficDelayInMinutes < BIG_TRAFFIC_DELAY_IN_MINUTES -> SdkIcons.Layout.NavigationLayout_YellowBall.value
                else -> return SdkIcons.Layout.NavigationLayout_RedBall.value
            }
        }

        fun getFerryIconId(): Int {
            return SdkIcons.RoutePreviewBubble.Icon_Ferry.value
        }

        fun getTollIconId(): Int {
            return SdkIcons.RoutePreviewBubble.Icon_Toll.value
        }

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
                    SdkIcons.E.KInvalidId.value
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

        fun getImageAspectRatio(marker: Marker?): Float {
            val image = marker?.getImage() ?: return 1.0f
            var fAspectRatio = 1.0f

            val size = image.getSize()
            if (size != null && size.height() != 0) {
                fAspectRatio = (size.width().toFloat() / size.height().toFloat())
            }

            return fAspectRatio
        }

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

        fun moveFileToDir(filePath: String, dirPath: String): SdkError {
            val from = File(filePath)
            if (!from.exists()) {
                return SdkError.KInvalidInput
            }

            val dir = File(dirPath)
            try {
                dir.mkdirs()
            } catch (e: Exception) {
                e.printStackTrace()
                return SdkError.KAccessDenied
            }

            val to = File(dirPath + File.separator + from.name)
            if (to.exists()) {
                return SdkError.KExist
            } else if (to.isDirectory) {
                return SdkError.KInvalidInput
            }

            moveFile(from, dir)
//            if(!from.renameTo(to))
//                return SdkError.KInternalAbort

            return SdkError.KNoError
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

        fun downloadSecondStyle() {
            // download another style
            SdkCall.execute {
                val listener = object : ProgressListener() {
                    override fun notifyComplete(reason: Int, hint: String) {
                        // select the downloaded style
                        val result =
                            ContentStore().getStoreContentList(EContentType.ECT_ViewStyleHighRes.value)
                        result ?: return

                        val contentStoreStyles = result.first
                        if (contentStoreStyles.isNotEmpty() && contentStoreStyles.size > 1) {
                            val styleListener = object : ProgressListener() {
                                override fun notifyComplete(reason: Int, hint: String) {}
                            }

                            for (style in contentStoreStyles) {
                                if (style.getName() != null && style.getName()!!
                                        .contains("Satellite")
                                ) {
                                    style.asyncDownload(
                                        styleListener,
                                        GemSdk.EDataSavePolicy.EUseDefault,
                                        true
                                    )
                                    break
                                }
                            }
                        }
                    }
                }

                ContentStore().asyncGetStoreContentList(
                    EContentType.ECT_ViewStyleHighRes.value,
                    listener
                )
            }
        }
    }
}
