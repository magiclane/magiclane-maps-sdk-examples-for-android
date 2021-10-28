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
import android.graphics.drawable.*
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.EContentStoreItemStatus
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.ErrorCode
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.d3scene.OverlayItem
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkImages
import com.generalmagic.sdk.util.Util.moveByRenaming
import com.generalmagic.sdk.util.UtilGemImages
import java.io.File
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

    fun getPhonePath(context: Context?): String =
        com.generalmagic.sdk.util.Util.getAppDirInternalPath(context)

    fun getSdCardPath(context: Context?): String =
        com.generalmagic.sdk.util.Util.getAppFilesDirExternalPath(context)

    fun getContentStoreStatusIconId(status: EContentStoreItemStatus): Int {
        return when (status) {
            EContentStoreItemStatus.Unavailable -> {
                SdkImages.UI.Button_DownloadOnServer_v2.value
            }

            EContentStoreItemStatus.Completed -> {
                SdkImages.UI.Button_DownloadOnDevice_v2.value
            }

            EContentStoreItemStatus.DownloadRunning -> {
                SdkImages.UI.Button_DownloadPause.value
            }

            EContentStoreItemStatus.DownloadQueued,
            EContentStoreItemStatus.DownloadWaitingFreeNetwork -> {
                SdkImages.UI.Button_DownloadQueue.value
            }

            EContentStoreItemStatus.DownloadWaiting,
            EContentStoreItemStatus.Paused -> {
                SdkImages.UI.Button_DownloadRefresh_v2.value
            }

            else -> {
                SdkImages.InvalidId
            }
        }
    }

    fun getContentStoreStatusAsBitmap(
        status: EContentStoreItemStatus,
        width: Int,
        height: Int
    ): Bitmap? {
        return UtilGemImages.asBitmap(getContentStoreStatusIconId(status), width, height)
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

    fun moveFileToDir(filePath: String, dirPath: String): ErrorCode {
        val from = File(filePath)
        if (!from.exists()) {
            return GemError.InvalidInput
        }

        val dir = File(dirPath)
        try {
            dir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
            return GemError.AccessDenied
        }

        val to = File(dirPath + File.separator + from.name)
        if (to.exists()) {
            return GemError.Exist
        } else if (to.isDirectory) {
            return GemError.InvalidInput
        }

        moveByRenaming(from, dir)
//            if(!from.renameTo(to))
//                return SdkError.KInternalAbort

        return GemError.NoError
    }

    fun downloadSecondStyle() {
        // download another style
        SdkCall.execute {
            val listener = object : ProgressListener() {
                override fun notifyComplete(errorCode: ErrorCode, hint: String) {
                    // select the downloaded style
                    val result =
                        ContentStore().getStoreContentList(EContentType.ViewStyleHighRes)
                    result ?: return

                    val contentStoreStyles = result.first
                    if (contentStoreStyles.isNotEmpty() && contentStoreStyles.size > 1) {
                        val styleListener = object : ProgressListener() {
                            override fun notifyComplete(errorCode: ErrorCode, hint: String) {}
                        }

                        for (style in contentStoreStyles) {
                            if (style.name != null && style.name!!.contains("Satellite")) {
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

