/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.activities.mainactivity.controllers.common

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.gemsdk.ExternalInfo
import com.generalmagic.gemsdk.GemString
import com.generalmagic.gemsdk.ProgressListener
import com.generalmagic.gemsdk.TExternalImageQuality
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.activities.WebActivity
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.util.Util
import com.generalmagic.gemsdk.demo.util.UtilUITexts
import com.generalmagic.gemsdk.models.Image
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.location_details_panel.view.*
import java.util.concurrent.Executors

open class WikiServiceController {
    private val infoService = ExternalInfo()
    private val imgQuality = TExternalImageQuality.eMediumImageQuality

    private var wikiFetched = false

    private var listener = object : ProgressListener() {
        override fun notifyComplete(reason: Int, hint: String) {
            GEMApplication.postOnMain {
                wikiFetched = true
                onWikiFetchComplete(reason, hint)
            }
        }
    }

    enum class TLoadState {
        ENotRequested,
        ELoading,
        EPendingReloading,
        EFailed,
        ELoaded,
    }

    fun hasWiki(value: Landmark): Boolean {
        return GEMSdkCall.execute { infoService.hasWikiInfo(value) } ?: false
    }

    fun requestWiki(value: Landmark) {
        wikiFetched = false
        GEMSdkCall.execute { infoService.requestWikiInfo(value, listener) }
    }

    fun stopRequesting() {
        wikiFetched = false
        GEMSdkCall.execute { infoService.cancelWikiInfo() }
    }

    fun getWikiPageDescription(): String {
        return GEMSdkCall.execute { infoService.getWikiPageDescription() } ?: ""
    }

    fun getWikiPageURL(): String {
        return GEMSdkCall.execute { infoService.getWikiPageURL() } ?: ""
    }

    fun getWikiImagesCount(): Int {
        return GEMSdkCall.execute { infoService.getWikiImagesCount() } ?: 0
    }

    fun startFetchingImages(): Boolean {
        holders.clear()
        val imgCount = getWikiImagesCount()
        for (i in 0 until imgCount) holders.add(LoadingImg())
        if (holders.size == 0) return false

        fetchImageAtIndex(0)

        return true
    }

    fun getImageAt(index: Int): Image? {
        return holders[index].image
    }

    fun getDescriptionAt(index: Int): String {
        val gemString = holders[index].description
        return GEMSdkCall.execute { gemString?.asKotlinString() } ?: ""
    }

    fun getImageLoadState(index: Int): TLoadState {
        return holders[index].imageLoadState
    }

    private fun setImageLoadStatus(index: Int, model: LoadingImg, status: TLoadState) {
        model.imageLoadState = status
//        Log.d("DRN", "onImageFetchStatusChanged($index, ${status.ordinal})")
        onImageFetchStatusChanged(index, status)
    }

    private fun setDescriptionLoadStatus(index: Int, model: LoadingImg, status: TLoadState) {
        model.descriptionLoadState = status
        onImageDescriptionFetchStatusChanged(index, status)
    }

    private data class LoadingImg(
        var image: Image? = GEMSdkCall.execute { Image() },
        var imageLoadState: TLoadState = TLoadState.ENotRequested,

        var description: GemString? = GEMSdkCall.execute { GemString() },
        var descriptionLoadState: TLoadState = TLoadState.ENotRequested
    )

    private val holders = ArrayList<LoadingImg>()

    private val progress = object : ProgressListener() {
        var index = 0
        var retryCount = 0
        override fun notifyComplete(reason: Int, hint: String) {
            val model = holders[index]

            if (reason != GEMError.KNoError.value) {
                if (retryCount > 0) { // retry
                    retryCount--
                    setImageLoadStatus(index, model, TLoadState.EPendingReloading)

                    model.image?.let {
                        infoService.requestWikiImage(this, it, index, imgQuality)
                    }
                } else { // fail
                    setImageLoadStatus(index, model, TLoadState.EFailed)
                    if (index + 1 < getWikiImagesCount()) {
                        fetchImageAtIndex(index + 1)
                    }
                }
                return
            }

            // success
            setImageLoadStatus(index, model, TLoadState.ELoaded)
            fetchDescriptionAtIndex(index)

            // start fetching next
            if (index + 1 < getWikiImagesCount()) {
                fetchImageAtIndex(index + 1)
            }
        }

        override fun notifyStart(hasProgress: Boolean) {
            val model = holders[index]

            setImageLoadStatus(index, model, TLoadState.ELoading)
        }
    }
    private val progressDescription = object : ProgressListener() {
        var index = 0
        var retryCount = 0

        override fun notifyComplete(reason: Int, hint: String) {
            val model = holders[index]

            if (reason != GEMError.KNoError.value) {
                if (retryCount > 0) { // retry
                    retryCount--
                    setDescriptionLoadStatus(index, model, TLoadState.EPendingReloading)

                    model.description?.let {
                        infoService.requestWikiImageInfo(this, index, it)
                    }
                } else { // fail
                    setDescriptionLoadStatus(index, model, TLoadState.EFailed)
                }
                return
            }

            setDescriptionLoadStatus(index, model, TLoadState.ELoaded)

            // start fetching next
            if (index + 1 < getWikiImagesCount()) {
                fetchDescriptionAtIndex(index + 1)
            }
        }

        override fun notifyStart(hasProgress: Boolean) {
            val model = holders[index]

            setDescriptionLoadStatus(index, model, TLoadState.ELoading)
        }
    }

    private fun fetchImageAtIndex(index: Int) {
        GEMSdkCall.execute {
            if (index < 0 || index >= holders.size) return@execute
            progress.retryCount = 3
            progress.index = index
            val model = holders[index]

            model.image?.let {
                infoService.requestWikiImage(progress, it, index, imgQuality)
            }
        }
    }

    private fun fetchDescriptionAtIndex(index: Int) {
        GEMSdkCall.execute {
            if (index < 0 || index >= holders.size) return@execute
            progressDescription.retryCount = 3
            progressDescription.index = index
            val model = holders[index]

            model.description?.let {
                infoService.requestWikiImageInfo(progressDescription, index, it)
            }
        }
    }

    //

    open fun onWikiFetchComplete(reason: Int, hint: String) {}
    open fun onImageFetchStatusChanged(index: Int, status: TLoadState) {}
    open fun onImageDescriptionFetchStatusChanged(index: Int, status: TLoadState) {}
}

class WikiView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private var locationDetails = LocationDetails()

    var onWikiFetchCompleteCallback = { _: Int, _: String -> }

    var onVisibilityChanged = {}
    val images = ArrayList<ImageViewModel>()

    private val wiki = object : WikiServiceController() {
        override fun onWikiFetchComplete(reason: Int, hint: String) {
//            GEMSdkCall.checkCurrentThread() //TODO: WTFFF, uncomment... ??

            locationDetails.wikipediaDescription = getWikiPageDescription()
            locationDetails.wikipediaUrl = getWikiPageURL()

            if (locationDetails.wikipediaUrl.isNullOrBlank()) {
                locationDetails.wikipediaText = null
            } else {
                locationDetails.wikipediaText = eStrWikipedia
            }

            val imagesCount = getWikiImagesCount()

            GEMApplication.postOnMain {
                if (imagesCount > 0) {
                    startFetchingImages()

                    onWikiFetchCompleteCallback(reason, hint)
                }

                images.clear()
                for (i in 0 until imagesCount) {
                    images.add(ImageViewModel())
                }
                updateWikiContents()
                show() // actual show
            }

        }

        override fun onImageFetchStatusChanged(index: Int, status: TLoadState) {
            GEMApplication.postOnMain {
                when (status) {
                    TLoadState.EFailed -> {
                        wikiListAdapter?.didUpdateImageLoadStatus(index, status)
                        Toast.makeText(
                            context,
                            "Loading Wiki img failed, index= $index",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    TLoadState.ELoaded -> {
                        getImageAt(index)?.let { wikiListAdapter?.didLoadImage(index, it) }
                    }
                    else -> {
                        wikiListAdapter?.didUpdateImageLoadStatus(index, status)
                    }
                }
            }
        }

        override fun onImageDescriptionFetchStatusChanged(index: Int, status: TLoadState) {
            GEMApplication.postOnMain {
                when (status) {
                    TLoadState.EFailed -> {
                        Toast.makeText(
                            context,
                            "Loading Wiki img description failed, index= $index",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    TLoadState.ELoaded -> {
                        wikiListAdapter?.didLoadDescription(index, getDescriptionAt(index))
                    }
                    else -> {
                    }
                }
            }
        }
    }

    /**
     * @return true if it started the wiki request else false.
     */
    fun show(value: Landmark): Boolean {
        hide()
        val imgSizes = context.resources.getDimension(R.dimen.navigationImageSize).toInt()

        if (!wiki.hasWiki(value)) {
            return false
        }

        GEMSdkCall.execute {
            locationDetails.text = value.getName()
            locationDetails.description = UtilUITexts.formatLandmarkDetails(value)
            locationDetails.image = Util.createBitmap(value.getImage(), imgSizes, imgSizes)
            locationDetails.wikipediaText = null
            locationDetails.wikipediaDescription = null
            locationDetails.wikipediaUrl = null

            // request wiki info
            wiki.requestWiki(value)
        }

        wiki_description.visibility = View.GONE
        wiki_title.visibility = View.GONE

        // put landmark info to view
        text.text = locationDetails.text
        description.text = locationDetails.description
        icon.setImageBitmap(locationDetails.image)

        return true
    }

    fun hide() {
        this.visibility = View.GONE
        notifyVisibilityChanged()
    }

    fun stopRequesting() {
        wiki.stopRequesting()
    }

    private fun show() {
        this.visibility = View.VISIBLE
//        notifyVisibilityChanged()
    }

    private fun notifyVisibilityChanged() {
        GEMApplication.postOnMain {
            onVisibilityChanged()
        }
    }

    private val eStrWikipedia = "Wikipedia"
    private var wikiListAdapter: WikiImagesListAdapter? = null
    private fun updateWikiContents() {
        locationDetails.wikipediaText?.let {
            wiki_title.visibility = View.VISIBLE
            wiki_description.visibility = View.VISIBLE
            wiki_description.maxLines = 5

            wiki_title.text = locationDetails.wikipediaText
            wiki_description.text = locationDetails.wikipediaDescription

            val url = locationDetails.wikipediaUrl
            if (!url.isNullOrEmpty()) {
                val titleColor = ContextCompat.getColor(context, android.R.color.holo_blue_dark)
                wiki_title.setTextColor(titleColor)
                wiki_title.setOnClickListener {
                    val intent = Intent(context, WebActivity::class.java)
                    intent.putExtra("url", url)
                    context.startActivity(intent)
                }
            }

            wiki_description.setOnClickListener {
                if (it is TextView) {
                    val maxLines = it.maxLines
                    if (maxLines < 10) {
                        it.maxLines = Int.MAX_VALUE
                    } else {
                        it.maxLines = 5
                    }
                }
            }
        } ?: run {
            wiki_title.visibility = View.GONE
            wiki_description.visibility = View.GONE
        }

        wiki_image_list.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        val nMaxImageWidth = 3 * width.coerceAtMost(height)

        wikiListAdapter = WikiImagesListAdapter(context, nMaxImageWidth, images)
        wiki_image_list.adapter = wikiListAdapter
    }

    data class LocationDetails(
        var text: String? = null,
        var description: String? = null,
        var image: Bitmap? = null,
        var wikipediaText: String? = null,
        var wikipediaDescription: String? = null,
        var wikipediaUrl: String? = null
    )
}

data class ImageViewModel(
    var status: WikiServiceController.TLoadState = WikiServiceController.TLoadState.ENotRequested,
    var image: Bitmap? = null,
    var description: String = ""
)

class WikiImagesListAdapter(
    val context: Context,
    private val nMaxImageWidth: Int,
    private val models: ArrayList<ImageViewModel>
) : RecyclerView.Adapter<WikiImagesListAdapter.ImageViewHolder>() {

    private val standardHeight = Util.mmToPixels(context, 25)
    private var nImagesHeight: Int

    init {
        nImagesHeight = standardHeight
    }

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: ConstraintLayout = view.findViewById(R.id.wiki_image_list_item)
        val image: ImageView = view.findViewById(R.id.wiki_image)
        val progress: ProgressBar = view.findViewById(R.id.wiki_image_progress)
        val description: Button = view.findViewById(R.id.wiki_image_description)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.wiki_image_list_item, parent, false)
        return ImageViewHolder(view)
    }

    override fun getItemCount(): Int {
        return models.size
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        if (position < 0 || position >= models.size) {
            return
        }

        val model = models[position]

        val isLoaded = model.status == WikiServiceController.TLoadState.ELoaded

        holder.image.maxHeight = nImagesHeight
        val layoutParams = holder.image.layoutParams

        if (isLoaded) {
            val bitmap = model.image
            if (bitmap != null) {
                layoutParams.width = bitmap.width
                layoutParams.height = bitmap.height

// 				if (layoutParams.width > nMaxImageWidth) {
// 					val imgParent = holder.image.parent
//
// 					if (imgParent != null && imgParent is ViewGroup) {
// 						imgParent.minimumHeight = nImagesHeight
// 						imgParent.layoutParams = ViewGroup.LayoutParams(
// 							ViewGroup.LayoutParams.WRAP_CONTENT,
// 							nImagesHeight
// 						)
// 					}
//
// 					val ratio = layoutParams.height.toFloat() / layoutParams.width
//
// 					layoutParams.width = nMaxImageWidth
// 					layoutParams.height = (layoutParams.width * ratio).toInt()
// 				}

                holder.image.layoutParams = layoutParams
                holder.image.setImageBitmap(bitmap)

                val description = model.description
                if (description.isNotEmpty()) {
                    holder.description.visibility = View.VISIBLE
                    holder.description.setOnClickListener {
                        val toast = Toast.makeText(context, description, Toast.LENGTH_LONG)
                        val view = toast.view
                        val text = view?.findViewById<TextView>(android.R.id.message)
                        val color = ContextCompat.getColor(context, R.color.colorPrimary)

                        view?.background?.colorFilter =
                            PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                        text?.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                        toast.show()
                    }
                } else {
                    holder.description.visibility = View.GONE
                }

                holder.progress.visibility = View.GONE
            } else {
                layoutParams.width = nImagesHeight
                layoutParams.height = nImagesHeight

                holder.image.layoutParams = layoutParams
                holder.image.setImageBitmap(null)

                holder.progress.visibility = View.VISIBLE
            }
        } else {
            holder.image.setImageBitmap(null)

            holder.progress.visibility = View.VISIBLE

            layoutParams.width = nImagesHeight
            holder.image.layoutParams = layoutParams
        }
    }

    private val imagesExecutor = Executors.newSingleThreadExecutor()
    fun didLoadImage(index: Int, image: Image) {
        if (index < 0 || index >= models.size) return

        val size = GEMSdkCall.execute { image.getSize() } ?: return

        val widthImage = ((size.width().toFloat() / size.height()) * standardHeight).toInt()

        imagesExecutor.submit {
            val bitmap = GEMSdkCall.execute {
                Util.createBitmap(image, widthImage, standardHeight)
            } ?: return@submit

            nImagesHeight = bitmap.height
            models[index].image = bitmap
            models[index].status = WikiServiceController.TLoadState.ELoaded

            GEMApplication.postOnMain {
//            Log.d("DRN", "notifyItemChanged index= $index")
                notifyItemChanged(index)
                if (index == (models.size - 1)) {
                    notifyDataSetChanged()
                }
            }
        }
    }

    fun didLoadDescription(index: Int, value: String) {
        if (index < 0 || index >= models.size) return

        models[index].description = value

        notifyItemChanged(index)
    }

    fun didUpdateImageLoadStatus(index: Int, value: WikiServiceController.TLoadState) {
        if (index < 0 || index >= models.size) return

        models[index].status = value

        notifyItemChanged(index)
    }
}
