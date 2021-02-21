/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.*
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.generalmagic.apihelper.EnumHelp
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.activities.WebActivity
import com.generalmagic.gemsdk.demo.activities.history.Trip
import com.generalmagic.gemsdk.demo.activities.history.TripsHistory
import com.generalmagic.gemsdk.demo.activities.pickvideo.PickLogActivity
import com.generalmagic.gemsdk.demo.activities.settings.SettingsProvider
import com.generalmagic.gemsdk.demo.app.TutorialsOpener.getCurrentTutorial
import com.generalmagic.gemsdk.demo.util.KeyboardUtil
import com.generalmagic.gemsdk.demo.util.Util
import com.generalmagic.gemsdk.demo.util.Utils
import com.generalmagic.gemsdk.demo.util.network.NetworkManager
import com.generalmagic.gemsdk.demo.util.network.NetworkProviderImpl
import com.generalmagic.gemsdk.extensions.PermissionsHelper
import com.generalmagic.gemsdk.mapview.GEMMapSurface
import com.generalmagic.gemsdk.models.*
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdk.util.SDKPathsHelper
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

    private val networkProvider = NetworkProviderImpl()
    private lateinit var networkManager: NetworkManager

    private var mapUpdater: ContentUpdater? = null

    private var mapSurface: GEMMapSurface? = null
    private var mainMapView: MapView? = null
    private val mainMapViewListener = object : MapViewListener() {}

    var mHistoryLandmarkStore: LandmarkStore? = null
    var mTripsHistory: TripsHistory? = null

    private val offboardListener = object : OffboardListener() {
        override fun onOnlineWorldMapSupportStatus(state: TStatus) {
            val listener = object : ProgressListener() {
                override fun notifyStatusChanged(status: Int) {
                    when (EnumHelp.fromInt<TContentUpdaterStatus>(status)) {
                        TContentUpdaterStatus.EFullyReady,
                        TContentUpdaterStatus.EPartiallyReady -> {
                            val updater = mapUpdater ?: return
                            // cancel routing
                            updater.apply()
                        }
                        else -> {
                        }
                    }
                }
            }

            val result =
                ContentStore().createContentUpdater(TContentType.ECT_RoadMap.value) ?: return

            if (result.second == GEMError.KNoError.value) {
                mapUpdater = result.first ?: return
                mapUpdater?.update(true, listener)
            }
        }
    }

    ///

    fun applicationContext(): Context = appContext!!

    fun appResources(): Resources = applicationContext().resources

    fun init(context: Context, mapSurface: GEMMapSurface, onFinish: () -> Unit = {}) {
        this.mapSurface = mapSurface

        mapSurface.onMainScreenCreated = { screen: Screen ->
            onInit(context, screen)
            postOnMain(onFinish)
            val styles =
                ContentStore().getLocalContentList(TContentType.ECT_ViewStyleHighRes.value)
            styles?.run {
                if (size < 2) {
                    Util.downloadSecondStyle()
                }
            }
        }
    }

    private fun onInit(context: Context, screen: Screen) {
        GEMSdkCall.checkCurrentThread()

        appContext = context.applicationContext
        SettingsProvider /* called here to ensure callbacks are loaded */

        // intern paths

        val recordsDirName = "records"
        iRecordsPath = StringBuilder().append(SDKPathsHelper.getPhonePath(context))
            .append(File.separator).append(recordsDirName).toString()

        eRecordsPath = StringBuilder().append(SDKPathsHelper.getSdCardPath(context))
            .append(File.separator).append(recordsDirName).toString()

        // network

        networkManager = NetworkManager(context)
        networkManager.onConnectionTypeChangedCallback =
            { type: NetworkManager.TConnectionType, https: TProxyDetails, http: TProxyDetails ->
                networkProvider.onNetworkConnectionTypeChanged(type, https, http)
            }

        CommonSettings().setNetworkProvider(networkProvider)
        CommonSettings().setAllowConnection(true, offboardListener)

        // touch

        var bWasFollowingPosition = false
        mapSurface?.onPreHandleTouchListener = { _ ->
            GEMSdkCall.execute {
                bWasFollowingPosition = getMainMapView()?.isFollowingPosition() ?: false
            }
            true
        }

        mapSurface?.onPostHandleTouchListener = { _ ->
            GEMSdkCall.execute {
                if (bWasFollowingPosition && getMainMapView()?.isFollowingPosition() != true) {
                    notifyMapFollowStatusChanged(false)
                }
            }
            true
        }

        // styles

        if (defaultMapStyle == null) {
            val styles = ContentStore().getLocalContentList(TContentType.ECT_ViewStyleHighRes.value)
            if (styles != null && styles.size > 0) {
                defaultMapStyle = styles[0]
            }
        }

        // history

        mTripsHistory = TripsHistory()
        mHistoryLandmarkStore = LandmarkStoreService().createLandmarkStore("History")?.first

        // main map view

        val mainViewRect = TRectF(0.0f, 0.0f, 1.0f, 1.0f)
        val mainMapView = MapView.produce(screen, mainViewRect, mainMapViewListener)

        mainMapView?.let { notifyMapFollowStatusChanged(it.isFollowingPosition()) }
        this.mainMapView = mainMapView

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

    fun getMainMapView() = GEMSdkCall.execute { mainMapView }
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
        return GEMSdkCall.execute { getMainMapView()?.isFollowingPosition() } ?: false
    }

    fun doMapFollow(following: Boolean = true) {
        GEMSdkCall.execute {
            val mainMapView = this.getMainMapView() ?: return@execute

            if (!following) {
                mainMapView.stopFollowingPosition()
                return@execute
            }

            if (isFollowingGps())
                return@execute // already following...

            val animation = Animation()
            animation.setMethod(TAnimation.EAnimationFly)
            animation.setFly(TFlyModes.EFM_Linear)
            animation.setDuration(900)

            mainMapView.startFollowingPosition(animation)
            notifyMapFollowStatusChanged(true)
        }
    }

    fun clearMapVisibleRoutes(): Boolean {
        return GEMSdkCall.execute {
            val routes = getMainMapView()?.preferences()?.routes() ?: return@execute false
            if (routes.size() == 0) {
                return@execute false
            }
            routes.clear()
            return@execute true
        } ?: false
    }

    fun deactivateMapHighlight() {
        GEMSdkCall.execute { getMainMapView()?.deactivateHighlight() }
    }

    fun focusOnRouteInstructionItem(value: RouteInstruction) {
        GEMSdkCall.execute {
            val animation = Animation()
            animation.setMethod(TAnimation.EAnimationFly)
            getMainMapView()?.centerOnRouteInstruction(value, -1, TXy(), animation)
        }
    }

    fun focusOnRouteTrafficItem(value: RouteTrafficEvent) {
        GEMSdkCall.execute {
            val animation = Animation()
            animation.setMethod(TAnimation.EAnimationFly)
            getMainMapView()?.centerOnRouteTrafficEvent(value, -1, TRect(), animation)
        }
    }

    private var logUploader: LogUploader? = null
    private val logUploaderListeners = ArrayList<LogUploaderListener>()
    fun addLogUploadListener(value: LogUploaderListener) = logUploaderListeners.add(value)
    fun removeLogUploadListener(value: LogUploaderListener) = logUploaderListeners.remove(value)

    fun uploadLog(filepath: String, username: String, email: String, details: String): Int {
        if (logUploader == null) {
            logUploader = GEMSdkCall.execute {
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

                        when (EnumHelp.fromInt<TLogUploaderState>(nStatus)) {
                            TLogUploaderState.ELU_Progress -> {
                                //
                            }
                            TLogUploaderState.ELU_Ready -> {
                                showToast("Uploaded: $name")
                            }
                        }
                    }
                }
                return@execute LogUploader.produce(proxyListener)
            }
        }

        val logUploader = logUploader ?: return GEMError.KNotSupported.value

        return GEMSdkCall.execute {
            logUploader.upload(filepath, username, email, details, arrayListOf(filepath))
        } ?: GEMError.KGeneral.value
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

        val hasRoutes =
            (getMainMapView()?.preferences()?.routes()?.size() ?: 0) != 0
        if (activity == topActivity() && activityStack.size == 1 && !hasRoutes) {
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

    private val REQUEST_PERMISSIONS = 110
    fun requestPermissions(activity: BaseActivity, permissions: Array<String>): Boolean {
        var requested = false
        if (!hasPermissions(activity, permissions)) {
            requested = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.requestPermissions(permissions, REQUEST_PERMISSIONS)
            }
// 			else {
// 				//TODO: implement this
// 			}
        }

        return requested
    }

    fun onRequestPermissionsResult(
        activity: BaseActivity, requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (grantResults.isEmpty())
            return

        val result = grantResults[0]
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                GEMSdkCall.execute {
                    PermissionsHelper.produce()?.notifyOnPermissionsStatusChanged()
                }
                activity.onRequestPermissionsFinish(result == PackageManager.PERMISSION_GRANTED)
            }
        }
    }

    /** //////////////////////////////////////////////////////////// */
    ///                      utils
    /** //////////////////////////////////////////////////////////// */

    private var mLastDisplayedError: GEMError = GEMError.KNoError

    fun showErrorMessage(error: GEMError, length: Int = Toast.LENGTH_SHORT) {
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
        mHistoryLandmarkStore?.let { landmarkStore ->
            val id = getLandmarkStoreLandmarkId(landmarkStore, landmark)
            val time = Time()
            time.setLocalTime()
            landmark.setTimeStamp(time)
            if (id > 0) {
                landmarkStore.updateLandmark(landmark)
            } else {
                landmarkStore.addLandmark(landmark)
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

    private fun getLandmarkStoreLandmarkId(landmarkStore: LandmarkStore, landmark: Landmark): Int {
        val treshold = 0.00001
        val landmarks = landmarkStore.getLandmarks() ?: arrayListOf()
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
                return item.getLandmarkId()
            }
        }

        return 0
    }

    fun addRouteToHistory(route: Route, isFromAToB: Boolean = true) {
        val trip = Trip()
        trip.set(route, isFromAToB)

        mTripsHistory?.saveTrip(trip)
    }
}
