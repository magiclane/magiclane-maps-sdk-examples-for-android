/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.controllers

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.activities.BaseActivity
import com.generalmagic.gemsdk.demo.activities.ChapterLISIAdapter
import com.generalmagic.gemsdk.demo.activities.GenericListActivity
import com.generalmagic.gemsdk.demo.activities.ListItemStatusImage
import com.generalmagic.gemsdk.demo.util.GEMApplication
import com.generalmagic.gemsdk.demo.util.StaticsHolder.Companion.getGlContext
import com.generalmagic.gemsdk.demo.util.StaticsHolder.Companion.getMainMapView
import com.generalmagic.gemsdk.magicearth.CanvasBufferRenderer
import com.generalmagic.gemsdk.models.Canvas
import com.generalmagic.gemsdk.models.CanvasListener
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.activity_camera.*
import kotlinx.android.synthetic.main.activity_list_view.*
import kotlinx.android.synthetic.main.activity_list_view.root_view
import kotlinx.android.synthetic.main.activity_list_view.toolbar

open class BasicSensorsActivity : GenericListActivity() {
    val lastData = HashMap<EDataType, IData?>()

    override fun refresh() {
        list_view.adapter = ChapterLISIAdapter(wrapList(lastData.keys))
    }

    private fun wrapList(list: MutableSet<EDataType>): ArrayList<ListItemStatusImage> {
        val result = ArrayList<ListItemStatusImage>()
        for (item in list) {
            lastData[item] ?: continue
            result.add(DataItemViewModel(item, lastData))
        }
        return result
    }

    open class DataItemViewModel(
        val type: EDataType,
        private val lastData: HashMap<EDataType, IData?>
    ) : ListItemStatusImage() {
        override fun getText(): String {
            return String.format("$type")
        }

        override fun getDescription(): String {
            val data = lastData[type] ?: return ""
            return GEMSdkCall.execute { toString(data) } ?: ""
        }
    }

    companion object {
        fun toString(iData: IData): String {
            return when (val type = iData.getType()) {
                EDataType.Acceleration -> {
                    val data = IAcceleration(iData)
                    val x = data.getX()
                    val y = data.getY()
                    val z = data.getZ()

                    String.format("$x, $y, $z")
                }

                EDataType.Activity -> {
                    val data = IActivity(iData)

                    val x = data.getActivityConfidence()
                    val y = data.getType()

                    String.format("$x, $y")
                }

                EDataType.Attitude -> {
                    val data = IAttitude(iData)

                    val x = data.getPitch()
                    val y = data.getRoll()
                    val z = data.getYaw()

                    String.format("$x, $y, $z")
                }

                EDataType.Battery -> {
                    val data = IBattery(iData)

                    val y = data.getBatteryLevel()
                    val x = data.getBatteryHealth()
                    val z = data.getBatteryState()

                    String.format("$x, $y, $z")
                }

                EDataType.Camera -> {
                    val data = ICamera(iData)

                    val config = data.getCameraConfiguration() ?: return ""
// 				val bmp = Util.createBitmapFromNV21(data.getBuffer(), 1280, 720)

                    val x = data.getAcquisitionTimestamp()
                    val y = config.frameWidth()
                    val z = config.frameHeight()

                    String.format("$x, $y, $z")
                }

                EDataType.Compass -> {
                    val data = ICompass(iData)

                    val x = data.getHeading()
                    val y = data.getAccuracy()

                    String.format("$x, $y")
                }

                EDataType.MagneticField -> {
                    val data = IMagneticField(iData)

                    val x = data.getX()
                    val y = data.getY()
                    val z = data.getZ()

                    String.format("$x, $y, $z")
                }

                EDataType.Orientation -> {
                    val data = IOrientation(iData)

                    val x = data.getDeviceOrientation()
                    val y = data.getUIOrientation()

                    String.format("$x, $y")
                }

                EDataType.ImprovedPosition,
                EDataType.Position -> {
                    val data = IPosition(iData)

                    val x = data.getLatitude()
                    val y = data.getLongitude()

                    String.format("$x, $y")
                }

                EDataType.RotationRate -> {
                    val data = IRotationRate(iData)

                    val x = data.getX()
                    val y = data.getY()
                    val z = data.getZ()

                    String.format("$x, $y, $z")
                }

                EDataType.Temperature -> {
                    val data = ITemperature(iData)

                    val x = data.getTemperatureDegrees()
                    val y = data.getTemperatureLevel()

                    String.format("$x, $y")
                }

                EDataType.Notification,
                EDataType.Unknown -> {
                    ""
                }
            }
        }
    }
}

class SensorsListActivity : BasicSensorsActivity() {
    var currentDataSource: DataSource? = null
    var listener = object : DataSourceListener() {
        override fun onDataInterruption(type: EDataType, reason: EDataInterruptionReason) {
            Log.d("GEMSDK", "onDataInterruption: $type, $reason")
        }

        override fun onDataInterruptionEnded(type: EDataType) {
            Log.d("GEMSDK", "onDataInterruptionEnded: $type")
        }

        override fun onNewData(data: IData?) {
            data ?: return

            val type = data.getType()

            val willAdd = lastData[type] == null
            lastData[type] = data

            if (willAdd) {
                GEMApplication.uiHandler.post { refresh() }
            } else {
                GEMApplication.uiHandler.post { list_view.adapter?.notifyDataSetChanged() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GEMSdkCall.execute { doStart() }
    }

    fun doStart() {
        GEMSdkCall.checkCurrentThread()

        val desiredTypes = arrayListOf(
            EDataType.Acceleration,
            EDataType.Activity,
            EDataType.Attitude,
            EDataType.Battery,
            EDataType.Camera,
            EDataType.Compass,
            EDataType.MagneticField,
            EDataType.Orientation,
            EDataType.Position,
            EDataType.RotationRate,
            EDataType.Temperature
        )

        val currentDataSource = GMDataSourceFactory.produceLive()
        currentDataSource ?: return

        for (type in desiredTypes) {
            val errorCode = currentDataSource.addListener(listener, type)

            val gemError = GEMError.fromInt(errorCode)
            if (gemError != GEMError.KNoError) {
                GEMApplication.uiHandler.post {
                    Toast.makeText(this, "error adding $type = $gemError", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        for (type in desiredTypes) {
            lastData[type] = currentDataSource.getLatestData(type)
        }

        this.currentDataSource = currentDataSource
    }
}

class DirectCamActivity : BaseActivity() {
    private var surfaceId = -1
    private var surfaceHolder: SurfaceHolder? = null
    private var surface: Surface? = null

    private var currentDataSource: DataSource? = null
    private var listener = object : DataSourceListener() {
        override fun onNewData(data: IData?) {
            data ?: return

            val camData = ICamera(data)
            val configs = camData.getCameraConfiguration() ?: return

// 			val bmp = Util.createBitmap(data.getBuffer()?.array(), configs.frameWidth(), configs.frameHeight())

// 			Log.d("GEMSDK", "zzzzz")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // set root view background (we used grouped style for list view)
        root_view.setBackgroundResource(R.color.list_view_bgnd_color)

        toolbar.visibility = View.VISIBLE

        setSupportActionBar(toolbar)
        // no title
        supportActionBar?.title = ""

        // display back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        surfaceHolder = surface_cam.holder
        surfaceHolder?.addCallback(
            object : SurfaceHolder.Callback {
                override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
                }

                override fun surfaceDestroyed(p0: SurfaceHolder) {
                }

                override fun surfaceCreated(p0: SurfaceHolder) {
                    surface = p0.surface
                    GEMSdkCall.execute { doStart() }
                }
            }
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        super.onSupportNavigateUp()

        GEMSdkCall.execute {
            val type = EDataType.Camera

            currentDataSource?.removeCameraSurface(surfaceId)
            currentDataSource?.removeListener(listener, type)
        }

        return true
    }

    fun doStart() {
        GEMSdkCall.checkCurrentThread()

        val currentDataSource = GMDataSourceFactory.produceLive()
        currentDataSource ?: return

        val type = EDataType.Camera

        val errorCode = currentDataSource.addListener(listener, type)
        val gemError = GEMError.fromInt(errorCode)

        if (gemError != GEMError.KNoError) {
            GEMApplication.uiHandler.post {
                Toast.makeText(this, "LISTENER error $type = $gemError", Toast.LENGTH_SHORT)
                    .show()
            }
            return
        }

        val resultPair = surface?.let {
            currentDataSource.addSurfaceToCamera(it)
        } ?: Pair(GEMError.KInvalidInput.value, -1)

        val error = GEMError.fromInt(resultPair.first)
        surfaceId = resultPair.second

        if (error != GEMError.KNoError) {
            GEMApplication.uiHandler.post {
                Toast.makeText(this, "error adding surface = $error", Toast.LENGTH_SHORT)
                    .show()
            }
            return
        }

        this.currentDataSource = currentDataSource
    }
}

class CanvasDrawerCam(context: Context, attrs: AttributeSet) : AppLayoutController(context, attrs) {
    private var drawer: CanvasBufferRenderer? = null
    private var canvas: Canvas? = null
    private var currentDataSource: DataSource? = null

    private val cameraListener = object : DataSourceListener() {
        override fun onNewData(data: IData?) {
            data ?: return
            GEMSdkCall.execute {
                val camData = ICamera(data)
                val buffer = camData.getDirectBuffer() ?: return@execute
                val configs = camData.getCameraConfiguration() ?: return@execute

// 				try {
// 					val yuvImage = YuvImage(buffer.array(), ImageFormat.NV21, width, height, null)
// 					val os = ByteArrayOutputStream()
// 					yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, os)
// 					val jpegByteArray: ByteArray = os.toByteArray()
// 					val bitmap = BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.size)
// 				}catch (e: Exception){
// 					Log.d("GEMSDK", "zz")
// 				}

                drawer?.uploadFrame(buffer, configs.frameWidth(), configs.frameHeight(), 90)
                getGlContext()?.needsRender()
            }
        }
    }
    private val canvasListener = object : CanvasListener() {
        override fun render() {
            GEMSdkCall.execute { drawer?.renderFrame() }
        }
    }

    private val subViewCoords = TRectF(0.0f, 0.0f, 1.0f, 0.5f)

    override fun onBackPressed(): Boolean {
        return GEMSdkCall.execute {
            val result = drawer != null
            doStop()
            return@execute result
        } ?: false
    }

    override fun doStart() {
        GEMSdkCall.checkCurrentThread()

        val screen = getMainMapView()?.getScreen()
        if (screen == null) {
            Toast.makeText(context, "No screen !", Toast.LENGTH_SHORT).show()
            return
        }

        val canvas = Canvas(screen, subViewCoords, canvasListener)
        val drawer = CanvasBufferRenderer(canvas)

        val currentDataSource = GMDataSourceFactory.produceLive()
        currentDataSource ?: return

        val type = EDataType.Camera

        val errorCode = currentDataSource.addListener(cameraListener, type)
        val error = GEMError.fromInt(errorCode)

        if (error != GEMError.KNoError) {
            GEMApplication.uiHandler.post {
                Toast.makeText(context, "camera error = $error", Toast.LENGTH_SHORT)
                    .show()
            }
            return
        }

        this.drawer = drawer
        this.canvas = canvas
        this.currentDataSource = currentDataSource
    }

    override fun doStop() {
        GEMSdkCall.checkCurrentThread()

        val type = EDataType.Camera

        drawer?.release()
        canvas?.release()
        drawer = null
        canvas = null

        currentDataSource?.removeListener(cameraListener, type)
    }
}
