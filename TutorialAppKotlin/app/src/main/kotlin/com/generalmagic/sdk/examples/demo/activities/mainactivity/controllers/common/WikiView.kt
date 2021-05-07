/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.common

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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.activities.WebActivity
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.app.Tutorials
import com.generalmagic.sdk.examples.demo.util.Util
import com.generalmagic.sdk.examples.demo.util.UtilUITexts
import com.generalmagic.sdk.examples.demo.util.Utils
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.*
import com.generalmagic.sdk.sensordatasource.PositionService
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import com.generalmagic.sdk.util.SdkIcons
import com.generalmagic.sdk.util.SdkList
import kotlinx.android.synthetic.main.location_details_panel.view.*
import java.util.concurrent.Executors

open class WikiServiceController {
    private val infoService = ExternalInfo()
    private val imgQuality = EExternalImageQuality.Medium

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

    @Suppress("unused")
    fun hasWiki(value: Landmark): Boolean {
        return SdkCall.execute { infoService.hasWikiInfo(value) } ?: false
    }

    fun requestWiki(value: Landmark) {
        wikiFetched = false
        SdkCall.execute { infoService.requestWikiInfo(value, listener) }
    }

    fun stopRequesting() {
        wikiFetched = false
        SdkCall.execute { infoService.cancelWikiInfo() }
    }

    fun getWikiPageDescription(): String {
        return SdkCall.execute { infoService.getWikiPageDescription() } ?: ""
    }

    fun getWikiPageURL(): String {
        return SdkCall.execute { infoService.getWikiPageURL() } ?: ""
    }

    fun getWikiImagesCount(): Int {
        return SdkCall.execute { infoService.getWikiImagesCount() } ?: 0
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
        return SdkCall.execute { gemString?.asKotlinString() } ?: ""
    }

    @Suppress("unused")
    fun getImageLoadState(index: Int): TLoadState {
        return holders[index].imageLoadState
    }

    private fun setImageLoadStatus(index: Int, model: LoadingImg, status: TLoadState) {
        model.imageLoadState = status
        onImageFetchStatusChanged(index, status)
    }

    private fun setDescriptionLoadStatus(index: Int, model: LoadingImg, status: TLoadState) {
        model.descriptionLoadState = status
        onImageDescriptionFetchStatusChanged(index, status)
    }

    private data class LoadingImg(
        var image: Image? = SdkCall.execute { Image() },
        var imageLoadState: TLoadState = TLoadState.ENotRequested,

        var description: GemString? = SdkCall.execute { GemString() },
        var descriptionLoadState: TLoadState = TLoadState.ENotRequested
    )

    private val holders = ArrayList<LoadingImg>()

    private val progress = object : ProgressListener() {
        var index = 0
        var retryCount = 0
        override fun notifyComplete(reason: Int, hint: String) {
            val model = holders[index]

            if (reason != SdkError.NoError.value) {
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

            if (reason != SdkError.NoError.value) {
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
        SdkCall.execute {
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
        SdkCall.execute {
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
    private var landmark: Landmark? = null
    private var locationDetails = LocationDetails()

    enum class TLocationDetailsButtonType {
        EUnknown,
        EDriveTo,
        EPublicTransportTo,
        EWalkTo,
        EBikeTo,
        EAddWaypoint,
        EFavourite
    }

    private var mButtons = arrayListOf<Int>()

    private val service = RoutingService()
    private val resultRoutes = SdkList(Route::class)


    var onWikiFetchCompleteCallback = { _: Int, _: String -> }

    var onVisibilityChanged = {}
    val images = ArrayList<ImageViewModel>()

    private val wiki = object : WikiServiceController() {
        override fun onWikiFetchComplete(reason: Int, hint: String) {

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
        landmark = value
        hide()
        val imgSizes = context.resources.getDimension(R.dimen.navigationImageSize).toInt()

        SdkCall.execute {
            locationDetails.text = value.getName()
            locationDetails.description = UtilUITexts.getLandmarkDescription(value)

            val icon = Utils.getLandmarkIcon(value, imgSizes)
            if (icon != null) {
                locationDetails.image = icon
            }

            locationDetails.wikipediaText = null
            locationDetails.wikipediaDescription = null
            locationDetails.wikipediaUrl = null

            // request wiki info
            wiki.requestWiki(value)
        }

        wiki_description.visibility = View.GONE
        wiki_title.visibility = View.GONE
        rightButtonContainer.visibility = View.GONE

        // put landmark info to view
        text.text = locationDetails.text
        description.text = locationDetails.description
        icon.setImageBitmap(locationDetails.image)

        SdkCall.execute {
            var bCalculateRoute = false
            val route = GEMApplication.getMainMapView()?.preferences()?.routes()?.getMainRoute()

            if (route != null) {
                if (route.preferences()?.getTransportMode() != ERouteTransportMode.Public) {
                    mButtons.add(TLocationDetailsButtonType.EPublicTransportTo.ordinal)
                    bCalculateRoute = true
                }
            } else {
                mButtons.add(TLocationDetailsButtonType.EDriveTo.ordinal)
                val position = PositionService().getPosition()
                if (position != null) {
                    bCalculateRoute = true
                }
            }

            if (bCalculateRoute) {
                calculateRoute(ERouteTransportMode.Car, value)
            }

            val buttonsCount = getButtonsCount()
            if (buttonsCount > 0) {
                val sizes = context.resources.getDimension(R.dimen.small_icon_size).toInt()

                locationDetails.buttonsImages = Array(buttonsCount) { index ->
                    getButtonImage(index, sizes, sizes, value)
                }

                locationDetails.buttonsTypes = Array(buttonsCount) { index ->
                    getButtonType(index)
                }

                locationDetails.buttonsTexts = Array(buttonsCount) { index ->
                    getButtonText(index)
                }
            } else {
                locationDetails.buttonsImages = arrayOf()
                locationDetails.buttonsTypes = arrayOf()
            }


            if (locationDetails.buttonsImages.isNotEmpty()) {
                for (i in locationDetails.buttonsImages.indices) {
                    if (locationDetails.buttonsTypes[i] == TLocationDetailsButtonType.EDriveTo.ordinal ||
                        locationDetails.buttonsTypes[i] == TLocationDetailsButtonType.EAddWaypoint.ordinal
                    ) {
                        rightButtonContainer.visibility = View.VISIBLE
                        rightButtonIcon.setImageBitmap(locationDetails.buttonsImages[i])
                        rightButtonIcon.setColorFilter(
                            ContextCompat.getColor(
                                context,
                                R.color.blue_color_new_design
                            )
                        )
                        rightButtonContainer.background =
                            GEMApplication.getDrawableResource(R.drawable.rounded_background_gray)

                        rightButtonIcon.setOnClickListener {
                            didTapButton(i)
                        }

                        if (locationDetails.buttonsTexts.isNotEmpty() && !locationDetails.buttonsTexts[i].isNullOrEmpty()) {
                            rightButtonText.visibility = View.VISIBLE
                            rightButtonText.text = locationDetails.buttonsTexts[i]
                        } else {
                            rightButtonText.visibility = View.GONE
                        }
                    }

                    if (locationDetails.buttonsTypes[i] == TLocationDetailsButtonType.EFavourite.ordinal) {
                        getFavouritesIcon(value)?.let { favIcon ->
                            favoritesIcon.setImageBitmap(favIcon)
                        }

                        iconContainer.setOnClickListener {
                            SdkCall.execute {
                                if (GEMApplication.isFavourite(value)) {
                                    GEMApplication.removeFavourite(value)
                                } else {
                                    GEMApplication.addFavourite(value)
                                }
                            }
                            getFavouritesIcon(value)?.let { favIcon ->
                                favoritesIcon.setImageBitmap(favIcon)
                            }
                        }
                    }
                }
            }
        }

        show() // actual show
        return true
    }

    private fun hide() {
        this.visibility = View.GONE
    }

    fun stopRequesting() {
        wiki.stopRequesting()
    }

    private fun show() {
        this.visibility = View.VISIBLE
    }

    @Suppress("unused")
    private fun notifyVisibilityChanged() {
        GEMApplication.postOnMain {
            onVisibilityChanged()
        }
    }

    private fun getFavouritesIcon(landmark: Landmark): Bitmap? {
        val imgSizes = context.resources.getDimension(R.dimen.small_icon_size).toInt()
        return if (GEMApplication.isFavourite(landmark)) {
            Utils.getImageAsBitmap(
                SdkIcons.Other_UI.LocationDetails_AddToFavourites.value,
                imgSizes,
                imgSizes
            )
        } else {
            Utils.getImageAsBitmap(
                SdkIcons.Other_UI.LocationDetails_RemoveFromFavourites.value,
                imgSizes,
                imgSizes
            )
        }
    }

    private fun getButtonsCount(): Int {
        return mButtons.size
    }

    private fun getButtonImage(index: Int, width: Int, height: Int, landmark: Landmark): Bitmap? {
        if (isValidButtonIndex(index)) {
            return when (mButtons[index]) {
                TLocationDetailsButtonType.EDriveTo.ordinal -> {
                    Utils.getImageAsBitmap(SdkIcons.Other_UI.DriveTo.value, width, height)
                }

                TLocationDetailsButtonType.EPublicTransportTo.ordinal -> {
                    Utils.getImageAsBitmap(SdkIcons.Other_UI.PublicTransportTo.value, width, height)
                }

                TLocationDetailsButtonType.EWalkTo.ordinal -> {
                    Utils.getImageAsBitmap(SdkIcons.Other_UI.WalkTo.value, width, height)
                }

                TLocationDetailsButtonType.EBikeTo.ordinal -> {
                    Utils.getImageAsBitmap(SdkIcons.Other_UI.BikeTo.value, width, height)
                }

                TLocationDetailsButtonType.EAddWaypoint.ordinal -> {
                    Utils.getImageAsBitmap(
                        SdkIcons.Other_UI.LocationDetails_Via.value,
                        width,
                        height
                    )
                }

                TLocationDetailsButtonType.EFavourite.ordinal -> {
//                    val imgSizes = context.resources.getDimension(R.dimen.small_icon_size).toInt()
                    return if (GEMApplication.isFavourite(landmark)) {
                        Utils.getImageAsBitmap(
                            SdkIcons.Other_UI.LocationDetails_AddToFavourites.value,
                            width,
                            height
                        )
                    } else {
                        Utils.getImageAsBitmap(
                            SdkIcons.Other_UI.LocationDetails_RemoveFromFavourites.value,
                            width,
                            height
                        )
                    }
                }

                else -> {
                    null
                }
            }
        }

        return null
    }

    private fun getButtonType(index: Int): Int {
        if (isValidButtonIndex(index)) {
            return mButtons[index]
        }
        return TLocationDetailsButtonType.EUnknown.ordinal
    }

    private fun getButtonText(index: Int): String {
        if (isValidButtonIndex(index)) {
            var route: Route? = null

            when (mButtons[index]) {
                TLocationDetailsButtonType.EDriveTo.ordinal,
                TLocationDetailsButtonType.EAddWaypoint.ordinal -> {
                    val routes = resultRoutes.asArrayList()
                    if (routes.isNotEmpty()) {
                        route = routes[0]
                    }
                }
                else -> {
                }
            }

            if (route != null) {
                val time = route.getTimeDistance()?.getTotalTime() ?: 0
                if (time > 0) {
                    val rtt = UtilUITexts.getTimeTextWithDays(time)
                    return String.format("%s %s", rtt.first, rtt.second)
                }
            }
        }

        return String()
    }

    private fun isValidButtonIndex(index: Int): Boolean {
        return ((index >= 0) && (index < mButtons.size))
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

//        val nMaxImageWidth = 3 * width.coerceAtMost(height)

        wikiListAdapter = WikiImagesListAdapter(context, images)
        wiki_image_list.adapter = wikiListAdapter
    }

    data class LocationDetails(
        var text: String? = null,
        var description: String? = null,
        var image: Bitmap? = null,
        var wikipediaText: String? = null,
        var wikipediaDescription: String? = null,
        var wikipediaUrl: String? = null,
        var buttonsImages: Array<Bitmap?> = arrayOf(),
        var buttonsTypes: Array<Int> = arrayOf(),
        var buttonsTexts: Array<String?> = arrayOf()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as LocationDetails

            if (text != other.text) return false
            if (description != other.description) return false
            if (image != other.image) return false
            if (wikipediaText != other.wikipediaText) return false
            if (wikipediaDescription != other.wikipediaDescription) return false
            if (wikipediaUrl != other.wikipediaUrl) return false
            if (!buttonsImages.contentEquals(other.buttonsImages)) return false
            if (!buttonsTypes.contentEquals(other.buttonsTypes)) return false
            if (!buttonsTexts.contentEquals(other.buttonsTexts)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = text?.hashCode() ?: 0
            result = 31 * result + (description?.hashCode() ?: 0)
            result = 31 * result + (image?.hashCode() ?: 0)
            result = 31 * result + (wikipediaText?.hashCode() ?: 0)
            result = 31 * result + (wikipediaDescription?.hashCode() ?: 0)
            result = 31 * result + (wikipediaUrl?.hashCode() ?: 0)
            result = 31 * result + buttonsImages.contentHashCode()
            result = 31 * result + buttonsTypes.contentHashCode()
            result = 31 * result + buttonsTexts.contentHashCode()
            return result
        }
    }

    @Suppress("SameParameterValue")
    private fun calculateRoute(type: ERouteTransportMode, landmark: Landmark) {
        SdkCall.execute {
            val position = PositionService().getPosition()
            if (position != null) {
                val waypoints = arrayListOf<Landmark>()

                val latitude = position.getLatitude()
                val longitude = position.getLongitude()
                waypoints.add(Landmark("", Coordinates(latitude, longitude)))
                waypoints.add(landmark)

                service.preferences.setTransportMode(type)
                service.preferences.setTimestamp(Time())
                service.preferences.setRouteType(ERouteType.Fastest)
                service.preferences.setResultDetails(ERouteResultDetails.TimeDistance)

                service.onCompleted = { _, reason, _ ->
                    onCompleteCalculating(reason, type)
                }

                when (type) {
                    ERouteTransportMode.Car -> {
                        resultRoutes.assignArrayList(ArrayList())
                        service.calculateRoute(
                            waypoints
                        )
                    }

                    else -> {
                    }
                }
            }
        }
    }

    private fun onCompleteCalculating(reason: Int, type: ERouteTransportMode) {
        val gemError = SdkError.fromInt(reason)
        if (gemError != SdkError.NoError) {
            Toast.makeText(
                context,
                "Routing service error: $gemError",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        var route: Route? = null
        when (type) {
            ERouteTransportMode.Car -> {
                val routes = SdkCall.execute { resultRoutes.asArrayList() }
                if (routes?.isNotEmpty() == true) {
                    route = routes[0]
                }
            }
            else -> {
            }
        }

        if (route != null) {
            didUpdateButtonText(0, getButtonText(0))
        }
    }

    @Suppress("SameParameterValue")
    private fun didUpdateButtonText(index: Int, text: String) {
        GEMApplication.postOnMain {
            if ((index == 0) && text.isNotEmpty()) {
                rightButtonText.visibility = View.VISIBLE
                rightButtonText.text = text
            }
        }
    }

    private fun didTapButton(index: Int) {
        if (isValidButtonIndex(index)) {
            when (mButtons[index]) {
                TLocationDetailsButtonType.EDriveTo.ordinal -> {
                    val position = SdkCall.execute { PositionService().getPosition() }
                    if (position != null) {
                        val waypoints = arrayListOf<Landmark>()
                        val latitude = position.getLatitude()
                        val longitude = position.getLongitude()
                        waypoints.add(Landmark("", Coordinates(latitude, longitude)))

                        landmark?.let { waypoints.add(it) }

                        Tutorials.openCustomRouteTutorial(waypoints)
                    }
                }
            }
        }
    }
}

data class ImageViewModel(
    var status: WikiServiceController.TLoadState = WikiServiceController.TLoadState.ENotRequested,
    var image: Bitmap? = null,
    var description: String = ""
)

class WikiImagesListAdapter(
    val context: Context,
    private val models: ArrayList<ImageViewModel>
) : RecyclerView.Adapter<WikiImagesListAdapter.ImageViewHolder>() {

    private val standardHeight = Util.mmToPixels(context, 25)
    private var nImagesHeight: Int

    init {
        nImagesHeight = standardHeight
    }

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        //        val container: ConstraintLayout = view.findViewById(R.id.wiki_image_list_item)
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

                        @Suppress("DEPRECATION")
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

        imagesExecutor.submit {
            val bitmap = SdkCall.execute {
                val size = image.getSize() ?: return@execute null
                val widthImage = ((size.width().toFloat() / size.height()) * standardHeight).toInt()
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
