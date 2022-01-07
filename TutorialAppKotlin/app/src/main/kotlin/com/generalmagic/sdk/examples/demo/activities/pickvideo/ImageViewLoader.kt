/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.generalmagic.sdk.examples.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.ImageView
import com.generalmagic.sdk.util.TAG
import com.generalmagic.sdk.util.Util
import java.io.File
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// ////////////////////////////////////////////////////////////////////////

/**
 * Helps in loading different image sources into a ImageView.
 */
class ImageViewLoader {
    /**
     * [ImageViewLoader.Model].
     */
    private data class Model(
        /**
         * ImageView.
         */
        val imageView: WeakReference<ImageView>,
        /**
         * Already loaded bitmap.
         */
        val bitmap: Bitmap?,
        /**
         * Video Url. A thumbnail will be created async.
         */
        val videoUrl: String,
        /**
         * Image Url. A image will be loaded async.
         */
        val imageUrl: String,
        /**
         * Resource id. See [ImageView.setImageResource].
         */
        val resId: Int
    ) {
        // May be used as caching key.
        val tag = "${imageView.get()!!.id}"

        constructor(
            imageView: ImageView,
            bitmap: Bitmap? = null,
            videoUrl: String = "",
            imageUrl: String = "",
            resId: Int = INVALID_RES_ID
        ) : this(WeakReference(imageView), bitmap, videoUrl, imageUrl, resId)

        fun isLoadingVideo(): Boolean {
            return videoUrl.isNotEmpty()
        }

        fun isLoadingImage(): Boolean {
            return imageUrl.isNotEmpty()
        }

        fun getCachingKey(): String {
            return getLoadingPath() ?: tag
        }

        fun getLoadingPath(): String? {
            if (isLoadingVideo())
                return videoUrl
            if (isLoadingImage())
                return imageUrl
            return null
        }

        companion object {
            /**
             * Invalid resource id, -1.
             */
            const val INVALID_RES_ID: Int = -1
        }
    }

    private var executorService: ExecutorService = Executors.newFixedThreadPool(5)

    private var memoryCache = MemoryCache()
    private var stubId = android.R.drawable.ic_menu_help

    /**
     * Sets place holder resource id. Used while loading async images.
     */
    fun setStubId(drawableId: Int) {
        stubId = drawableId
    }

    /**
     * Displays a image, into the [ImageView] by using [ImageView.setImageBitmap].
     */
    fun displayImage(imageView: ImageView, bitmap: Bitmap) =
        display(Model(imageView, bitmap = bitmap))

    /**
     * Displays a image, into the [ImageView] by using [ImageView.setImageResource].
     */
    fun displayResImage(imageView: ImageView, resId: Int) =
        display(Model(imageView, resId = resId))

    /**
     * Displays a image, async, into the [ImageView] by using [ThumbnailUtils.createVideoThumbnail].
     * Caches the image for later reuse.
     */
    fun displayVideoThumbnail(imageView: ImageView, videoUrl: String) =
        display(Model(imageView, videoUrl = videoUrl))

    /**
     * Displays a image, async, into the [ImageView] by using [BitmapFactory.decodeFile].
     * Caches the image for later reuse.
     */
    fun displayImage(imageView: ImageView, imageUrl: String) =
        display(Model(imageView, imageUrl = imageUrl))

    /**
     * Displays a image based on provided [Model]. Not guaranteed to be a instant method.
     */
    private fun display(value: Model) {
        val imageView = value.imageView.get() ?: return

        if (value.resId != Model.INVALID_RES_ID) {
            imageView.setImageResource(value.resId)
            return
        }

        if (value.bitmap != null) {
            imageView.setImageBitmap(value.bitmap)
            return
        }

        // async stuff
        val bitmap = memoryCache[value.getCachingKey()]
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
        } else {
            imageView.setImageResource(stubId)
            executorService.submit { loadAsync(value) }
        }
    }

    /**
     * Clears cached images.
     */
    fun clearCache() {
        memoryCache.clear()
    }

    // ////////////////////////////////////////////////////////////////////////
    // private
    // ////////////////////////////////////////////////////////////////////////

    private fun loadAsync(value: Model) {
        val imageView = value.imageView.get() ?: return

        val filePath = value.getLoadingPath()

        var bmp = memoryCache[value.getCachingKey()]
        val wasCached = bmp != null

        if (!wasCached && filePath != null) {
            bmp = when {
                value.isLoadingVideo() -> {
                    createVideoThumbnail(filePath, Size(imageView.width, imageView.height))
                }
                value.isLoadingImage() -> {
                    readImage(filePath)
                }
                else -> null
            }

            if (!wasCached && bmp != null) {
                memoryCache.put(value.tag, bmp)
            }
        }

        Util.postOnMain {
            if (bmp != null) {
                imageView.setImageBitmap(bmp)
            } else {
                imageView.setImageResource(stubId)
            }
        }
    }

    // sources

    private fun readImage(filePath: String): Bitmap? {
        try {
            val file = File(filePath)
            if (!file.exists() || file.isDirectory)
                return null

            return BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
        }

        return null
    }

    private fun createVideoThumbnail(
        url: String, size: Size, signal: CancellationSignal? = null
    ): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return ThumbnailUtils.createVideoThumbnail(File(url), size, signal)
            } catch (e: Exception) {
            }
        }

        try {
            @Suppress("Deprecation")
            return ThumbnailUtils.createVideoThumbnail(url, MediaStore.Video.Thumbnails.MINI_KIND)
        } catch (e: Exception) {
        }

        return null
    }
}

// ////////////////////////////////////////////////////////////////////////

internal class MemoryCache {
    private val cache = Collections.synchronizedMap(
        LinkedHashMap<String, Bitmap>(10, 1.5f, true)
    ) // Last argument true for LRU ordering

    private var size: Long = 0 // current allocated size
    private var limit: Long = 1000000 // max memory in bytes

    init {
        // use 25% of available heap size
        setLimit(Runtime.getRuntime().maxMemory() / 4)
    }

    private fun setLimit(new_limit: Long) {
        limit = new_limit
        Log.i(TAG, "MemoryCache will use up to " + limit.toDouble() / 1024.0 / 1024.0 + "MB")
    }

    operator fun get(id: String): Bitmap? {
        return try {
            if (!cache.containsKey(id)) null else cache[id]
            // NullPointerException sometimes happen here http://code.google.com/p/osmdroid/issues/detail?id=78
        } catch (ex: NullPointerException) {
            ex.printStackTrace()
            null
        }
    }

    fun put(id: String, bitmap: Bitmap) {
        try {
            if (cache.containsKey(id)) {
                size -= getSizeInBytes(cache[id])
            }
            cache[id] = bitmap
            size += getSizeInBytes(bitmap)
            checkSize()
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    private fun checkSize() {
        Log.i(TAG, "cache size=" + size + " length=" + cache.size)
        if (size <= limit)
            return

        // least recently accessed item will be the first one iterated
        val it = cache.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            size -= getSizeInBytes(entry.value)
            it.remove()
            if (size <= limit) {
                break
            }
        }

        Log.i(TAG, "Clean cache. New size " + cache.size)
    }

    fun clear() {
        try {
            // NullPointerException sometimes happen here http://code.google.com/p/osmdroid/issues/detail?id=78
            cache.clear()
            size = 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSizeInBytes(bitmap: Bitmap?): Long {
        return if (bitmap == null) 0 else (bitmap.rowBytes * bitmap.height).toLong()
    }
}

// ////////////////////////////////////////////////////////////////////////
