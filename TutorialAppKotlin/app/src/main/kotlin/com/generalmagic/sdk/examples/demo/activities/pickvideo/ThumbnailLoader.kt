package com.generalmagic.sdk.examples.demo.activities.pickvideo

import android.app.Activity
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.ImageView
import androidx.annotation.RequiresApi
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MemoryCache {
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
        if (size > limit) {
            val iter =
                cache.entries.iterator() // least recently accessed item will be the first one iterated
            while (iter.hasNext()) {
                val entry = iter.next()
                size -= getSizeInBytes(entry.value)
                iter.remove()
                if (size <= limit) {
                    break
                }
            }
            Log.i(TAG, "Clean cache. New size " + cache.size)
        }
    }

    fun clear() {
        try {
            // NullPointerException sometimes happen here http://code.google.com/p/osmdroid/issues/detail?id=78
            cache.clear()
            size = 0
        } catch (ex: NullPointerException) {
            ex.printStackTrace()
        }
    }

    private fun getSizeInBytes(bitmap: Bitmap?): Long {
        return if (bitmap == null) 0 else (bitmap.rowBytes * bitmap.height).toLong()
    }

    companion object {
        private const val TAG = "MemoryCache"
    }
}

class ThumbnailLoader {
    private val imageViews = Collections.synchronizedMap(WeakHashMap<ImageView, String>())
    private var executorService: ExecutorService = Executors.newFixedThreadPool(5)

    var memoryCache = MemoryCache()
    internal var stubId = android.R.drawable.ic_menu_help

    fun setStubId(drawableId: Int) {
        stubId = drawableId
    }

    fun displayImage(url: String, imageView: ImageView) {
        imageViews[imageView] = url
        val bitmap = memoryCache[url]
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
        } else {
            queuePhoto(url, imageView)
            imageView.setImageResource(stubId)
        }
    }

    private fun queuePhoto(url: String, imageView: ImageView) {
        val p = PhotoToLoad(url, imageView)
        executorService.submit(PhotosLoader(p))
    }

    @Suppress("DEPRECATION")
    private fun getBitmap(url: String): Bitmap? {
        return ThumbnailUtils.createVideoThumbnail(url, MediaStore.Video.Thumbnails.MINI_KIND)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getBitmap(url: String, size: Size, signal: CancellationSignal? = null): Bitmap {
        return ThumbnailUtils.createVideoThumbnail(File(url), size, signal)
    }

    // Task for the queue
    inner class PhotoToLoad(var url: String, var imageView: ImageView)

    internal inner class PhotosLoader(private var photoToLoad: PhotoToLoad) : Runnable {
        override fun run() {
            if (imageViewReused(photoToLoad)) {
                return
            }

            val size = Size(photoToLoad.imageView.width, photoToLoad.imageView.height)

            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getBitmap(photoToLoad.url, size)
            } else {
                getBitmap(photoToLoad.url)
            }

            if (bmp != null) {
                memoryCache.put(photoToLoad.url, bmp)
            }

            if (imageViewReused(photoToLoad)) {
                return
            }
            val bd = BitmapDisplayer(bmp, photoToLoad)
            val a = photoToLoad.imageView.context as Activity
            a.runOnUiThread(bd)
        }
    }

    internal fun imageViewReused(photoToLoad: PhotoToLoad): Boolean {
        val tag = imageViews[photoToLoad.imageView]
        return tag == null || tag != photoToLoad.url
    }

    // Used to display bitmap in the UI thread
    internal inner class BitmapDisplayer(
        private var bitmap: Bitmap?,
        private var photoToLoad: PhotoToLoad
    ) :
        Runnable {
        override fun run() {
            if (imageViewReused(photoToLoad)) {
                return
            }
            if (bitmap != null) {
                photoToLoad.imageView.setImageBitmap(bitmap)
            } else {
                photoToLoad.imageView.setImageResource(stubId)
            }
        }
    }

    @Suppress("unused")
    fun clearCache() {
        memoryCache.clear()
    }
}
