package com.generalmagic.gemsdk.demo.app

import android.content.Intent
import android.os.Environment
import com.generalmagic.gemsdk.Route
import com.generalmagic.gemsdk.demo.activities.mainactivity.controllers.*
import com.generalmagic.gemsdk.demo.activities.pickvideo.PickLogActivity
import com.generalmagic.gemsdk.demo.activities.searchaddress.SearchAddressActivity
import com.generalmagic.gemsdk.demo.activities.settings.SettingsActivity
import com.generalmagic.gemsdk.models.Landmark
import com.generalmagic.gemsdk.util.GEMSdkCall
import java.util.*


/** //////////////////////////////////////////////////////////// */
///                 TUTORIALS
/** //////////////////////////////////////////////////////////// */

object TutorialsOpener {
    private val tutorialStack = Stack<ITutorialController>()
    private var mapTutorialsOpener: ITutorialsOpener? = null

    /**
     * Call this on main thread.
     */
    fun openTutorial(id: Tutorials.Id, vararg args: Any): Boolean {
        if (activitiesTutorialOpener.openTutorial(id, args))
            return true

        GEMSdkCall.execute {
            val prefs = GEMApplication.getMainMapView()?.preferences() ?: return@execute

            GEMApplication.clearMapVisibleRoutes()
            GEMApplication.getMainMapView()?.deactivateHighlight()
//            defaultMapStyle?.let { prefs.setMapStyleById(it.getId()) }
        }

        GEMApplication.doMapFollow(false)

        val mapTutorialsOpener = this.mapTutorialsOpener ?: return false
        mapTutorialsOpener.preOpenTutorial()

        return mapTutorialsOpener.openTutorial(id, args)
    }

    fun getCurrentTutorial(): ITutorialController? {
        try {
            return tutorialStack.peek()
        } catch (e: Exception) {
        }

        return null
    }

    fun onTutorialCreated(value: ITutorialController) {
        tutorialStack.push(value)

        value.doStart()
        mapTutorialsOpener?.onTutorialOpened(value)
    }

    fun onTutorialDestroyed(value: ITutorialController) {
        value.doStop()

        try {
            if (value == tutorialStack.peek())
                tutorialStack.pop()
        } catch (e: Exception) {
        }
    }

    interface ITutorialController {
        fun doBackPressed(): Boolean

        fun doStart()
        fun doStop()

        fun onMapFollowStatusChanged(following: Boolean)
    }

    interface ITutorialsOpener {
        fun preOpenTutorial()
        fun openTutorial(id: Tutorials.Id, args: Array<out Any>): Boolean
        fun onTutorialOpened(value: ITutorialController)
    }

    fun setMapTutorialsOpener(value: ITutorialsOpener) {
        this.mapTutorialsOpener = value
    }

    private val activitiesTutorialOpener = object : ITutorialsOpener {
        override fun preOpenTutorial() {}

        override fun onTutorialOpened(value: ITutorialController) {}

        override fun openTutorial(id: Tutorials.Id, args: Array<out Any>): Boolean {
            val activity = GEMApplication.topActivity() ?: return false
            when (id) {
                Tutorials.Id.Styles -> {
                    val intent = Intent(activity, StylesActivity::class.java)
                    activity.startActivity(intent)
                    return true
                }

                Tutorials.Id.Settings -> {
                    val intent = Intent(activity, SettingsActivity::class.java)
                    activity.startActivity(intent)
                    return true
                }

                Tutorials.Id.SearchText -> {
                    val intent = Intent(activity, SearchTextActivity::class.java)
                    activity.startActivity(intent)
                    return true
                }

                Tutorials.Id.SearchNearby -> {
                    val intent = Intent(activity, SearchNearbyActivity::class.java)
                    activity.startActivity(intent)
                    return true
                }

                Tutorials.Id.SearchPoi -> {
                    val intent = Intent(activity, SearchPoiActivity::class.java)
                    activity.startActivity(intent)
                    return true
                }

                Tutorials.Id.AddressSearch -> {
                    val intent = Intent(activity, SearchAddressActivity::class.java)
                    activity.startActivity(intent)
                    return true
                }

                Tutorials.Id.SensorsList -> {
                    val intent = Intent(activity, SensorsListActivity::class.java)
                    activity.startActivity(intent)
                    return true
                }

                Tutorials.Id.DirectCam -> {
                    val intent = Intent(activity, DirectCamActivity::class.java)
                    activity.startActivity(intent)
                    return true
                }

                Tutorials.Id.OnlineMaps -> {
                    val intent = Intent(activity, OnlineMapsActivity::class.java)
                    activity.startActivity(intent)
                    return true
                }

                Tutorials.Id.LogPlayer_PICK -> {
                    val internalDir = GEMApplication.getInternalRecordsPath()
                    val publicDir = activity.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    publicDir ?: return false

                    val intent = Intent(activity, PickLogActivity::class.java)
                    intent.putStringArrayListExtra(PickLogActivity.INPUT_DIR, arrayListOf<String>(
                        publicDir.absolutePath,
                        internalDir
                    ))
                    
                    activity.startActivityForResult(
                        intent, PickLogActivity.CODE_RESULT_SELECT_VIDEO
                    )

                    /** !! onActivityResult please call openLogPlayerTutorial !! */
                    return true
                }
                else -> {
                }
            }

            return false
        }
    }
}

object Tutorials {
    enum class Id {
        HelloWorld,
        Wiki,
        LogPlayer_PLAY,
        LogPlayer_PICK,
        CustomUrl,
        OnlineMaps,
        FlyTo_Traffic,
        FlyTo_Route,
        FlyTo_Instr,
        FlyTo_Area,
        FlyTo_Coords,
        LogRecorder,
        CanvasDrawerCam,
        DirectCam,
        SensorsList,
        Navigation_Custom,
        Simulation_Custom,
        PublicNav_Custom,
        PublicNav_Predef,
        Navigation_Predef,
        Simulation_Landmarks,
        Simulation_Route,
        Route_Custom,
        Route_ABC,
        Route_AB,
        AddressSearch,
        SearchPoi,
        SearchNearby,
        SearchText,
        Settings,
        MultipleViews,
        TwoTiledViews,
        Styles
    }

    ///////////////////////////////////////////////////////////////////////////////////

    fun openHelloWorldTutorial() = TutorialsOpener.openTutorial(Id.HelloWorld)
    fun openWikiTutorial(landmark: Landmark) = TutorialsOpener.openTutorial(Id.Wiki, landmark)
    fun openLogPlayerTutorial(filepath: String) =
        TutorialsOpener.openTutorial(Id.LogPlayer_PLAY, filepath)

    fun openStylesTutorial() = TutorialsOpener.openTutorial(Id.Styles)
    fun openTwoTiledViewsTutorial() = TutorialsOpener.openTutorial(Id.TwoTiledViews)
    fun openMultipleViewsTutorial() = TutorialsOpener.openTutorial(Id.MultipleViews)
    fun openSettingsTutorial() = TutorialsOpener.openTutorial(Id.Settings)
    fun openSearchTextTutorial() = TutorialsOpener.openTutorial(Id.SearchText)
    fun openSearchNearbyTutorial() = TutorialsOpener.openTutorial(Id.SearchNearby)
    fun openSearchPoiTutorial() = TutorialsOpener.openTutorial(Id.SearchPoi)
    fun openAddressSearchTutorial() = TutorialsOpener.openTutorial(Id.AddressSearch)
    fun openRouteAbTutorial() = TutorialsOpener.openTutorial(Id.Route_AB)
    fun openRouteAbcTutorial() = TutorialsOpener.openTutorial(Id.Route_ABC)
    fun openCustomRouteTutorial() = TutorialsOpener.openTutorial(Id.Route_Custom)
    fun openLandmarksSimulationTutorial(landmarks: ArrayList<Landmark>) = TutorialsOpener.openTutorial(Id.Simulation_Landmarks, landmarks)
    fun openRouteSimulationTutorial(route: Route) = TutorialsOpener.openTutorial(Id.Simulation_Route, route)
    fun openPredefNavigationTutorial() = TutorialsOpener.openTutorial(Id.Navigation_Predef)
    fun openPredefPublicNavTutorial() = TutorialsOpener.openTutorial(Id.PublicNav_Predef)
    fun openDirectCamTutorial() = TutorialsOpener.openTutorial(Id.DirectCam)
    fun openCustomPublicNavTutorial() = TutorialsOpener.openTutorial(Id.PublicNav_Custom)
    fun openCustomSimulationTutorial() = TutorialsOpener.openTutorial(Id.Simulation_Custom)
    fun openCustomNavigationTutorial() = TutorialsOpener.openTutorial(Id.Navigation_Custom)
    fun openSensorsListTutorial() = TutorialsOpener.openTutorial(Id.SensorsList)
    fun openCustomUrlTutorial() = TutorialsOpener.openTutorial(Id.CustomUrl)
    fun openPredefWikiTutorial() = TutorialsOpener.openTutorial(Id.Wiki)
    fun openCanvasDrawerCamTutorial() = TutorialsOpener.openTutorial(Id.CanvasDrawerCam)
    fun openLogRecorderTutorial() = TutorialsOpener.openTutorial(Id.LogRecorder)
    fun openPickLogTutorial() = TutorialsOpener.openTutorial(Id.LogPlayer_PICK)
    fun openFlyToCoordsTutorial() = TutorialsOpener.openTutorial(Id.FlyTo_Coords)
    fun openFlyToAreaTutorial() = TutorialsOpener.openTutorial(Id.FlyTo_Area)
    fun openFlyToNavInstrTutorial() = TutorialsOpener.openTutorial(Id.FlyTo_Instr)
    fun openFlyToRouteTutorial() = TutorialsOpener.openTutorial(Id.FlyTo_Route)
    fun openFlyToTrafficTutorial() = TutorialsOpener.openTutorial(Id.FlyTo_Traffic)
    fun openOnlineMapsTutorial() = TutorialsOpener.openTutorial(Id.OnlineMaps)
}
