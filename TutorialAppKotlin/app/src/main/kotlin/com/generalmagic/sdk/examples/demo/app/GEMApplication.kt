/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("unused", "MemberVisibilityCanBePrivate", "UNUSED_PARAMETER")

package com.generalmagic.sdk.examples.demo.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.*
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.generalmagic.sdk.content.*
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.d3scene.*
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.activities.WebActivity
import com.generalmagic.sdk.examples.demo.activities.history.Trip
import com.generalmagic.sdk.examples.demo.activities.history.TripsHistory
import com.generalmagic.sdk.examples.demo.activities.pickvideo.PickLogActivity
import com.generalmagic.sdk.examples.demo.app.TutorialsOpener.getCurrentTutorial
import com.generalmagic.sdk.examples.demo.util.KeyboardUtil
import com.generalmagic.sdk.examples.demo.util.Util
import com.generalmagic.sdk.examples.demo.util.Utils
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.LandmarkStore
import com.generalmagic.sdk.places.LandmarkStoreService
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.routesandnavigation.RouteInstruction
import com.generalmagic.sdk.routesandnavigation.RouteTrafficEvent
import com.generalmagic.sdk.util.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.system.exitProcess

object GEMApplication {
    private var appContext: Context? = null

    private var iRecordsPath = ""
    private var eRecordsPath = ""

    private var defaultMapStyle: ContentStoreItem? = null

    private var mapSurface: GemSurfaceView? = null
    private var mainMapView: MapView? = null

    private var mHistoryLandmarkStore: LandmarkStore? = null
    var mTripsHistory: TripsHistory? = null

    private var mFavouritesLandmarkStore: LandmarkStore? = null

    ///

    fun applicationContext(): Context = appContext!!

    fun appResources(): Resources = applicationContext().resources

    fun init(context: Context, mapSurface: GemSurfaceView, onFinish: () -> Unit = {}) {
        SdkCall.checkCurrentThread()

        SdkSettings.onConnected = {
            val styles =
                ContentStore().getLocalContentList(EContentType.ViewStyleHighRes.value)
            styles?.let {
                if (styles.size < 2) {
                    Util.downloadSecondStyle()
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            postOnMain {
                Toast.makeText(context, "Token Rejected", Toast.LENGTH_SHORT).show()
            }
        }

        this.mapSurface = mapSurface

        appContext = context.applicationContext

        // intern paths

        val recordsDirName = "records"
        iRecordsPath = StringBuilder().append(Util.getPhonePath(context))
            .append(File.separator).append(recordsDirName).toString()

        eRecordsPath = StringBuilder().append(Util.getSdCardPath(context))
            .append(File.separator).append(recordsDirName).toString()

        mapSurface.onScreenCreated = { screen ->
            val rectF = RectF(0.0f, 0.0f, 1.0f, 1.0f)
            MapView.produce(screen, rectF)?.let {
                mainMapView = it
                notifyMapFollowStatusChanged(it.isFollowingPosition())
            }
        }

        // touch

        var bWasFollowingPosition = false

        GEMApplication.mapSurface?.onPreHandleTouchListener = { _ ->
            SdkCall.execute {
                bWasFollowingPosition = getMainMapView()?.isFollowingPosition() ?: false
            }
            true
        }

        GEMApplication.mapSurface?.onPostHandleTouchListener = { _ ->
            SdkCall.execute {
                if (bWasFollowingPosition && getMainMapView()?.isFollowingPosition() != true) {
                    notifyMapFollowStatusChanged(false)
                }
            }
            true
        }

        // styles

        if (defaultMapStyle == null) {
            val styles = ContentStore().getLocalContentList(EContentType.ViewStyleHighRes.value)
            if (styles != null && styles.size > 0) {
                defaultMapStyle = styles[0]
            }
        }

        // history

        mTripsHistory = TripsHistory()
        mHistoryLandmarkStore = LandmarkStoreService().createLandmarkStore("History")?.first

        // favorites

        mFavouritesLandmarkStore = LandmarkStoreService().createLandmarkStore("Favorites")?.first

        postOnMain(onFinish)

        // default tutorial loaded
        postOnMain { Tutorials.openHelloWorldTutorial() }
    }

    /** absolute path to internal records */
    fun getInternalRecordsPath() = iRecordsPath

    fun getPublicRecordsDir(): File? {
        return appContext?.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
    }

    /** absolute path where records will be externally saved. */
    fun getExternalRecordsPath() = eRecordsPath

    fun getMainMapView() = SdkCall.execute { mainMapView }
    fun gemMapScreen() = mapSurface?.getScreen()
    fun getGlContext() = mapSurface?.getGlContext()

    fun onFollowGpsPressed() {
        doMapFollow()
    }

    private fun notifyMapFollowStatusChanged(following: Boolean) {
        postOnMain {
            getCurrentTutorial()?.onMapFollowStatusChanged(following)
        }
    }

    fun isFollowingGps(): Boolean {
        return SdkCall.execute { getMainMapView()?.isFollowingPosition() } ?: false
    }

    fun doMapFollow(following: Boolean = true) {
        SdkCall.execute {
            val mainMapView = this.getMainMapView() ?: return@execute

            if (!following) {
                mainMapView.stopFollowingPosition()
                return@execute
            }

            if (isFollowingGps())
                return@execute // already following...

            val animation = Animation()
            animation.setType(EAnimation.AnimationLinear)
            animation.setDuration(900)

            mainMapView.startFollowingPosition(animation)
            notifyMapFollowStatusChanged(true)
        }
    }

    fun clearMapVisibleRoutes(): Boolean {
        return SdkCall.execute {
            val routes = getMainMapView()?.preferences()?.routes() ?: return@execute false
            if (routes.size() == 0) {
                return@execute false
            }
            routes.clear()
            return@execute true
        } ?: false
    }

    fun deactivateMapHighlight() {
        SdkCall.execute { getMainMapView()?.deactivateHighlight() }
    }

    fun focusOnRouteInstructionItem(value: RouteInstruction) {
        SdkCall.execute {
            val animation = Animation()
            animation.setType(EAnimation.AnimationLinear)
            getMainMapView()?.centerOnRouteInstruction(value, -1, Xy(), animation)
        }
    }

    fun focusOnRouteTrafficItem(value: RouteTrafficEvent) {
        SdkCall.execute {
            val animation = Animation()
            animation.setType(EAnimation.AnimationLinear)
            getMainMapView()?.centerOnRouteTrafficEvent(value, -1, Rect(), animation)
        }
    }

    private var logUploader: LogUploader? = null
    private val logUploaderListeners = ArrayList<LogUploaderListener>()
    fun addLogUploadListener(value: LogUploaderListener) = logUploaderListeners.add(value)
    fun removeLogUploadListener(value: LogUploaderListener) = logUploaderListeners.remove(value)

    fun uploadLog(filepath: String, username: String, email: String, details: String): Int {
        if (logUploader == null) {
            logUploader = SdkCall.execute {
                val proxyListener = object : LogUploaderListener() {
                    override fun onLogStatusChanged(
                        sLogPath: String, nProgress: Int, nStatus: Int
                    ) {
                        for (listener in logUploaderListeners) {
                            listener.onLogStatusChanged(sLogPath, nProgress, nStatus)
                        }

                        if (nStatus < 0) { // internally aborted
                            return
                        }

                        val chunks = sLogPath.split("/")
                        val name = chunks.last()

                        when (EnumHelp.fromInt<ELogUploaderState>(nStatus)) {
                            ELogUploaderState.Progress -> {
                                //
                            }
                            ELogUploaderState.Ready -> {
                                showToast("Uploaded: $name")
                            }
                        }
                    }
                }
                return@execute LogUploader.produce(proxyListener)
            }
        }

        val logUploader = logUploader ?: return SdkError.NotSupported.value

        return SdkCall.execute {
            logUploader.upload(filepath, username, email, details, arrayListOf(filepath))
        } ?: SdkError.General.value
    }

    /** //////////////////////////////////////////////////////////// */
    ///                 activities management
    /** //////////////////////////////////////////////////////////// */

    private val activityStack = Stack<BaseActivity>()

    fun topActivity(): BaseActivity? {
        try {
            return activityStack.peek()
        } catch (e: Exception) {
        }

        return null
    }

    fun onActivityCreated(activity: BaseActivity, savedInstanceState: Bundle?) {
        activityStack.push(activity)
    }

    fun onDestroyed(activity: BaseActivity) {
        activityStack.pop()
    }

    fun onActivityResumed(activity: BaseActivity) {}

    fun onActivityPaused(activity: BaseActivity) {}

    fun onCreateOptionsMenu(activity: BaseActivity, menu: Menu): Boolean {
        activity.menuInflater.inflate(R.menu.main, menu)
        return true
    }

    fun onNavigateUp(activity: BaseActivity): Boolean {
        activity.onBackPressed()
        return true
    }

    fun onSupportNavigateUp(activity: BaseActivity): Boolean {
        activity.onBackPressed()
        return true
    }

    fun onBackPressed(activity: BaseActivity) {
        KeyboardUtil.hideKeyboard(activity)
        if (getCurrentTutorial()?.doBackPressed() == true) {
            setAppBarVisible(true)
            setSystemBarsVisible(true)

            hideBusyIndicator()
            deactivateMapHighlight()
            Tutorials.openHelloWorldTutorial()
            return
        }

        if (activity.doBackPressed()) {
            return
        }

        val routesCount = SdkCall.execute {
            getMainMapView()?.preferences()?.routes()?.size()
        } ?: 0

        if (activity == topActivity() && activityStack.size == 1 && routesCount == 0) {
            activity.displayCloseAppDialog()
        }
    }

    fun onCloseAppDialogResponse(value: Boolean) {
        if (value)
            terminateApp()
    }

    fun onOptionsItemSelected(activity: BaseActivity, item: MenuItem) {
        when (item.itemId) {
            R.id.action_show_code -> {
                val intent = Intent(activity, WebActivity::class.java)
                intent.putExtra("url", "https://www.generalmagic.com")
                activity.startActivity(intent)
            }
        }
    }

    fun onActivityResult(activity: BaseActivity, requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            PickLogActivity.CODE_RESULT_SELECT_VIDEO -> {
                if (data == null) {
                    Toast.makeText(activity, "Please pick again!", Toast.LENGTH_SHORT).show()
                    return
                }

                val pickedPath = data.getStringExtra(PickLogActivity.RESULT_VIDEO_PATH) ?: return
                Tutorials.openLogPlayerTutorial(pickedPath)
            }
            else -> {
                // unknown answer
            }
        }
    }

    /** //////////////////////////////////////////////////////////// */
    ///                      permissions
    /** //////////////////////////////////////////////////////////// */

    fun hasPermissions(context: Context, permissions: Array<String>): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    const val REQUEST_PERMISSIONS = 110

    fun onRequestPermissionsResult(
        activity: BaseActivity, requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (grantResults.isEmpty())
            return

        val result = grantResults[0]
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                PermissionsHelper.onRequestPermissionsResult(activity, requestCode, grantResults)
                activity.onRequestPermissionsFinish(result == PackageManager.PERMISSION_GRANTED)
            }
        }
    }

    /** //////////////////////////////////////////////////////////// */
    ///                      utils
    /** //////////////////////////////////////////////////////////// */

    private var mLastDisplayedError: SdkError = SdkError.NoError

    fun showErrorMessage(error: SdkError, length: Int = Toast.LENGTH_SHORT) {
        if (mLastDisplayedError == error) {
            return
        }

        mLastDisplayedError = error
        showErrorMessage(Utils.getErrorMessage(error), length)
    }

    fun showErrorMessage(error: String, length: Int = Toast.LENGTH_SHORT) {
        showToast("Error: $error", length)
    }

    fun showToast(message: String, length: Int = Toast.LENGTH_SHORT) {
        postOnMain {
            Toast.makeText(applicationContext(), message, length).show()
        }
    }

    fun hideBusyIndicator() {
        topActivity()?.hideProgress()
    }

    fun showBusyIndicator() {
        topActivity()?.showProgress()
    }

    fun setAppBarVisible(visible: Boolean) {
        topActivity()?.setAppBarVisible(visible)
    }

    fun setSystemBarsVisible(visible: Boolean) {
        topActivity()?.setSystemBarsVisible(visible)
    }

    fun setScreenAlwaysOn(enabled: Boolean) {
        topActivity()?.setScreenAlwaysOn(enabled)
    }

    fun setRequestedOrientation(orientation: Int) {
        topActivity()?.requestedOrientation = orientation
    }

    fun postOnMain(action: () -> Unit) = postOnMainDelayed(Runnable { action() })
    fun postOnMain(action: Runnable) = postOnMainDelayed(action)
    fun postOnMainDelayed(action: () -> Unit, delay: Long = 0L) =
        postOnMainDelayed(Runnable { action() }, delay)

    fun postOnMainDelayed(action: Runnable, delay: Long = 0L) {
        val uiHandler = Handler(Looper.getMainLooper())
        if (delay == 0L)
            uiHandler.post(action)
        else
            uiHandler.postDelayed(action, delay)
    }

    fun terminateApp() {
        topActivity()?.finish()
        exitProcess(0)
    }

    fun getHistoryLandmarkStore(): LandmarkStore? {
        return mHistoryLandmarkStore
    }

    fun getTripsHistory(): TripsHistory? {
        return mTripsHistory
    }

    fun addLandmarkToHistory(landmark: Landmark) {
        mHistoryLandmarkStore.let { landmarkStore ->
            val id = getLandmarkStoreLandmarkId(landmarkStore, landmark)
            val time = Time()
            time.setLocalTime()
            landmark.setTimeStamp(time)
            if (id > 0) {
                landmarkStore?.updateLandmark(landmark)
            } else {
                landmarkStore?.addLandmark(landmark)
            }
        }
    }

    fun removeLandmarkFromHistory(landmark: Landmark): Boolean {
        mHistoryLandmarkStore?.let { landmarkStore ->
            val id = getLandmarkStoreLandmarkId(landmarkStore, landmark)
            mHistoryLandmarkStore?.removeLandmark(id)
            return true
        }
        return false
    }

    private fun getLandmarkStoreLandmarkId(landmarkStore: LandmarkStore?, landmark: Landmark): Int =
        SdkCall.execute {
            val treshold = 0.00001
            val landmarks = landmarkStore?.getLandmarks() ?: arrayListOf()
            val name = landmark.getName() ?: ""
            val lat = landmark.getCoordinates()?.getLatitude() ?: 0.0
            val lon = landmark.getCoordinates()?.getLongitude() ?: 0.0

            for (item in landmarks) {
                val itemLatitude = item.getCoordinates()?.getLatitude() ?: 0.0
                val itemLongitude = item.getCoordinates()?.getLongitude() ?: 0.0
                if ((item.getName() == name) &&
                    (abs(itemLatitude - lat) < treshold) &&
                    (abs(itemLongitude - lon) < treshold)
                ) {
                    return@execute item.getLandmarkId()
                }
            }
            return@execute 0
        } ?: 0

    fun addRouteToHistory(route: Route, isFromAToB: Boolean = true) {
        val trip = Trip()
        trip.set(route, isFromAToB)

        mTripsHistory?.saveTrip(trip)
    }

    fun getFavouritesLandmarkStore(): LandmarkStore? {
        return mFavouritesLandmarkStore
    }

    fun isFavourite(landmark: Landmark): Boolean {
        return mFavouritesLandmarkStore?.let { landmarkStore ->
            return@let (getLandmarkStoreLandmarkId(landmarkStore, landmark) > 0)
        } ?: false
    }

    fun addFavourite(landmark: Landmark, checkIfIsFavourite: Boolean = true): Boolean {
        mFavouritesLandmarkStore.let { landmarkStore ->
            val lmk = Landmark("", landmark.getCoordinates() ?: Coordinates())
            lmk.assign(landmark)
            if (!checkIfIsFavourite || !isFavourite(landmark)) {
                ImageDatabase().getImageById(SdkIcons.Other_Engine.LocationDetailsFavouritePushPin.value)
                    ?.let { lmk.setImage(it) }

                val tmp = String.format("original_icon_id=%d", landmark.getImage()?.getUid())
                val extra = arrayListOf(tmp)

                lmk.setExtraInfo(extra)

                landmarkStore?.addLandmark(lmk)
                return true
            }
        }
        return false
    }

    fun removeFavourite(landmark: Landmark): Boolean {
        mFavouritesLandmarkStore.let { landmarkStore ->
            val id = getLandmarkStoreLandmarkId(landmarkStore, landmark)
            if (id > 0) {
                SdkCall.execute { landmarkStore?.removeLandmark(id) }
                return true
            }
        }
        return false
    }

    fun getDrawableResource(id: Int): Drawable? {
        return ContextCompat.getDrawable(applicationContext(), id)
    }
}
