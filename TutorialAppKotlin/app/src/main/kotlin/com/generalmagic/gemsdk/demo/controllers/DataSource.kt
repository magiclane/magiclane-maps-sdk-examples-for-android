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

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import com.generalmagic.apihelper.EnumHelp
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.activities.ChapterLISIAdapter
import com.generalmagic.gemsdk.demo.activities.GenericListActivity
import com.generalmagic.gemsdk.demo.activities.ListItemStatusImage
import com.generalmagic.gemsdk.demo.app.BaseActivity
import com.generalmagic.gemsdk.demo.app.BaseLayoutController
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.app.GEMApplication.Companion.postOnMain
import com.generalmagic.gemsdk.demo.app.StaticsHolder.Companion.getGlContext
import com.generalmagic.gemsdk.demo.app.StaticsHolder.Companion.getMainMapView
import com.generalmagic.gemsdk.demo.util.Util.Companion.moveFile
import com.generalmagic.gemsdk.extensions.*
import com.generalmagic.gemsdk.models.Canvas
import com.generalmagic.gemsdk.models.CanvasListener
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.activity_camera.*
import kotlinx.android.synthetic.main.activity_list_view.*
import kotlinx.android.synthetic.main.activity_list_view.root_view
import kotlinx.android.synthetic.main.activity_list_view.toolbar
import kotlinx.android.synthetic.main.app_bar_layout.view.*
import java.io.File

open class BasicSensorsActivity : GenericListActivity() {
    var currentDataSource: DataSource? = null
    val lastData = HashMap<EDataType, SenseData?>()

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
        private val lastData: HashMap<EDataType, SenseData?>
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
        fun toString(SenseData: SenseData): String {
            return when (val type = SenseData.getType()) {
                EDataType.Acceleration -> {
                    val data = AccelerationData(SenseData)
                    val x = data.getX()
                    val y = data.getY()
                    val z = data.getZ()

                    String.format("$x, $y, $z")
                }

                EDataType.Activity -> {
                    val data = ActivityData(SenseData)

                    val x = data.getActivityConfidence()
                    val y = data.getType()

                    String.format("$x, $y")
                }

                EDataType.Attitude -> {
                    val data = AttitudeData(SenseData)

                    val x = data.getPitch()
                    val y = data.getRoll()
                    val z = data.getYaw()

                    String.format("$x, $y, $z")
                }

                EDataType.Battery -> {
                    val data = BatteryData(SenseData)

                    val y = data.getBatteryLevel()
                    val x = data.getBatteryHealth()
                    val z = data.getBatteryState()

                    String.format("$x, $y, $z")
                }

                EDataType.Camera -> {
                    val data = CameraData(SenseData)

                    val config = data.getCameraConfiguration() ?: return ""
// 				val bmp = Util.createBitmapFromNV21(data.getBuffer(), 1280, 720)

                    val x = data.getAcquisitionTimestamp()
                    val y = config.frameWidth()
                    val z = config.frameHeight()

                    String.format("$x, $y, $z")
                }

                EDataType.Compass -> {
                    val data = CompassData(SenseData)

                    val x = data.getHeading()
                    val y = data.getAccuracy()

                    String.format("$x, $y")
                }

                EDataType.MagneticField -> {
                    val data = MagneticFieldData(SenseData)

                    val x = data.getX()
                    val y = data.getY()
                    val z = data.getZ()

                    String.format("$x, $y, $z")
                }

                EDataType.Orientation -> {
                    val data = OrientationData(SenseData)

                    val x = data.getDeviceOrientation()
                    val y = data.getUIOrientation()

                    String.format("$x, $y")
                }

                EDataType.ImprovedPosition,
                EDataType.Position -> {
                    val data = PositionData(SenseData)

                    val x = data.getLatitude()
                    val y = data.getLongitude()

                    String.format("$x, $y")
                }

                EDataType.RotationRate -> {
                    val data = RotationRateData(SenseData)

                    val x = data.getX()
                    val y = data.getY()
                    val z = data.getZ()

                    String.format("$x, $y, $z")
                }

                EDataType.Temperature -> {
                    val data = TemperatureData(SenseData)

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
    var listener = object : DataSourceListener() {
        override fun onDataInterruption(type: EDataType, reason: EDataInterruptionReason) {
            Log.d("GEMSDK", "onDataInterruption: $type, $reason")
        }

        override fun onDataInterruptionEnded(type: EDataType) {
            Log.d("GEMSDK", "onDataInterruptionEnded: $type")
        }

        override fun onNewData(data: SenseData?) {
            data ?: return

            val type = data.getType()

            val willAdd = lastData[type] == null
            lastData[type] = data

            if (willAdd) {
                postOnMain { refresh() }
            } else {
                postOnMain { list_view.adapter?.notifyDataSetChanged() }
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
            val errorCode = currentDataSource.addListener(listener, type, false)

            val gemError = GEMError.fromInt(errorCode)
            if (gemError != GEMError.KNoError) {
                postOnMain {
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
        override fun onNewData(data: SenseData?) {
            if (data?.getType() != EDataType.Camera) return

            val camData = CameraData(data)
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

        val errorCode = currentDataSource.addListener(listener, type, false)
        val gemError = GEMError.fromInt(errorCode)

        if (gemError != GEMError.KNoError) {
            postOnMain {
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
            postOnMain {
                Toast.makeText(this, "error adding surface = $error", Toast.LENGTH_SHORT)
                    .show()
            }
            return
        }

        this.currentDataSource = currentDataSource
    }
}

// ------------------------------------------------------------------------------------------------------

open class DataSourceController(context: Context, attrs: AttributeSet) :
    BaseLayoutController(context, attrs) {
    protected var currentDataSource: DataSource? = null
}

class FrameDrawController(val context: Context, private val currentDataSource: DataSource) {
    private val subViewCoords = TRectF(0.0f, 0.0f, 1.0f, 1.0f)
    private var drawer: CanvasBufferRenderer? = null

    private val framesDrawerListener = object : DataSourceListener() {
        override fun onNewData(data: SenseData?) {
            data ?: return
            GEMSdkCall.execute {
                val camData = CameraData(data)
                val buffer = camData.getDirectBuffer() ?: return@execute
                val configs = camData.getCameraConfiguration() ?: return@execute

                val width = configs.frameWidth()
                val height = configs.frameHeight()
                val rotation = configs.recordedAngle().toInt()
                val format = configs.pixelFormat()

                drawer?.uploadFrame(buffer, width, height, format, rotation)
                getGlContext()?.needsRender()
            }
        }
    }

    private var canvas: Canvas? = null
    private val canvasListener = object : CanvasListener() {
        override fun onRender() {
            GEMSdkCall.execute { drawer?.renderFrame() }
        }
    }

    fun doStart() {
        GEMSdkCall.checkCurrentThread()

        if (!currentDataSource.getAvailableDataTypes().contains(EDataType.Camera)) {
            postOnMain {
                Toast.makeText(context, "CAMERA FRAME IS NOT AVAILABLE !", Toast.LENGTH_SHORT)
                    .show()
            }
            return
        }

        val screen = getMainMapView()?.getScreen()
        if (screen == null) {
            postOnMain {
                Toast.makeText(context, "No screen !", Toast.LENGTH_SHORT).show()
            }
            return
        }

        this.canvas = Canvas.produce(screen, subViewCoords, canvasListener) ?: return
        this.drawer = CanvasBufferRenderer.produce(canvas)
        this.drawer?.setFrameFit(TFrameFit.eCenter)

        val errorCode = currentDataSource.addListener(framesDrawerListener, EDataType.Camera)
        val error = GEMError.fromInt(errorCode)

        if (error != GEMError.KNoError) {
            postOnMain {
                Toast.makeText(context, "start error = ${error.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun doStop() {
        GEMSdkCall.checkCurrentThread()

        currentDataSource.removeListener(framesDrawerListener, EDataType.Camera)

        canvas?.release()
        canvas = null

        getGlContext()?.needsRender()

        drawer?.release()
        drawer = null
    }
}

open class LiveDataSourceController(context: Context, attrs: AttributeSet) :
    DataSourceController(context, attrs) {

    override fun doStart() {
        super.doStart()

        GEMSdkCall.checkCurrentThread()

        val currentDataSource = GMDataSourceFactory.produceLive() ?: return
        this.currentDataSource = currentDataSource
    }
}

open class LogDataSourceController(context: Context, attrs: AttributeSet) :
    DataSourceController(context, attrs) {
    var videoPath: String = ""

    override fun doStart() {
        super.doStart()

        GEMSdkCall.checkCurrentThread()

        val currentDataSource = GMDataSourceFactory.produceLog(videoPath) ?: return
        this.currentDataSource = currentDataSource
    }
}

// ------------------------------------------------------------------------------------------------------

// ------------------------------------------------------------------------------------------------------

// ------------------------------------------------------------------------------------------------------

class LogRecorderController(context: Context, attrs: AttributeSet) :
    LiveDataSourceController(context, attrs) {

    private var drawer: FrameDrawController? = null
    private var recorder: Recorder? = null
    private val recorderListener = object : ProgressListener() {
        override fun notifyComplete(reason: Int, hint: String) {
            when (val error = GEMError.fromInt(reason)) {
                GEMError.KNoError -> {
                    val inputFile = File(hint)
                    val videosDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    if (videosDir == null) {
                        val text = "Export Failed!"
                        postOnMain { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
                        return
                    }

                    var text = "Not Exported!"
                    val newFile = moveFile(inputFile, videosDir)
                    if (newFile != null) {
                        text = "Exported!"

                        val cr = context.contentResolver

                        val values = ContentValues()
                        values.put(MediaStore.Video.Media.TITLE, inputFile.name)
                        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        values.put(MediaStore.Video.Media.DATA, newFile.absolutePath)
                        val uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
                    }

                    postOnMain { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
                }
                else -> {
                    val text = "${error.name} - $hint"
                    postOnMain { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
                }
            }
        }

        override fun notifyStatusChanged(status: Int) {
            val recStatus = EnumHelp.fromInt<ERecorderStatus>(status)

            when (recStatus) {
                ERecorderStatus.Stopped -> {
                    postOnMain {
                        Toast.makeText(context, "Recording Stopped!", Toast.LENGTH_SHORT).show()
                        doStoppedButtons()
                    }
                }
                ERecorderStatus.Starting,
                ERecorderStatus.Recording -> {
                    postOnMain {
                        if (status == ERecorderStatus.Recording.value)
                            Toast.makeText(context, "Recording Running!", Toast.LENGTH_SHORT).show()
                        doRecordingButtons()
                    }
                }

                else -> {
                }
            }
        }
    }

    override fun doStart() {
        super.doStart()

        GEMSdkCall.checkCurrentThread()

        val currentDataSource = this.currentDataSource ?: return

        val logsDir = GEMApplication.iRecordsPath

        File(logsDir).mkdirs()

        val config = TRecorderConfiguration.produce(
            logsDir,
            currentDataSource.getAvailableDataTypes()
        ) ?: return

        config.setVideoQuality(EVideoQuality.HD_720p)
        config.setContinuousRecording(true);
        config.setChunkDurationSeconds(300);

        recorder = Recorder.produce(config, currentDataSource)
        recorder?.addListener(recorderListener)

        val startError =
            GEMError.fromInt(recorder?.startRecording() ?: GEMError.KInternalAbort.value)
        if (startError == GEMError.KNoError) {
            val drawer = FrameDrawController(context, currentDataSource)
            drawer.doStart()

            this.drawer = drawer

            postOnMain {
                disableScreenLock();
            }
        } else {
            postOnMain {
                Toast.makeText(context, "Start error = ${startError.name}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun doStop() {
        super.doStop()

        GEMSdkCall.checkCurrentThread()

        recorder?.stopRecording()

        drawer?.doStop()
        drawer = null

        postOnMain {
            enableScreenLock();
        }
    }

    fun doStoppedButtons() {
        bottomButtons()?.let { bottomButtons ->
            bottomButtons.bottomCenterButton?.visibility = View.GONE
            bottomButtons.bottomRightButton?.visibility = View.GONE
            bottomButtons.bottomLeftButton?.let {
                it.visibility = View.VISIBLE
                buttonAsStart(it)
                it.setOnClickListener {
                    GEMSdkCall.execute { doStart() }
                }
            }
        }
    }

    fun doRecordingButtons() {
        bottomButtons()?.let { bottomButtons ->
            bottomButtons.bottomCenterButton?.visibility = View.GONE
            bottomButtons.bottomRightButton?.visibility = View.GONE
            bottomButtons.bottomLeftButton?.let {
                it.visibility = View.VISIBLE
                buttonAsStop(it)
                it.setOnClickListener {
                    GEMSdkCall.execute { doStop() }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------

class LogPlayerController(context: Context, attrs: AttributeSet) :
    LogDataSourceController(context, attrs) {

    private var drawer: FrameDrawController? = null

    override fun doStart() {
        super.doStart()

        GEMSdkCall.checkCurrentThread()

        val currentDataSource = this.currentDataSource ?: return
        val playback = currentDataSource.getPlayback() ?: return

        if (playback.isPaused()) {
            playback.resume()
        }

        if (playback.isPaused()) {
            postOnMain { doResumeButtons() }
        } else {
            postOnMain { doPauseButtons() }
        }

        val drawer = FrameDrawController(context, currentDataSource)
        drawer.doStart()

        this.drawer = drawer
    }

    override fun doStop() {
        super.doStop()

        GEMSdkCall.checkCurrentThread()

        drawer?.doStop()
        drawer = null

        currentDataSource?.getPlayback()?.pause()

        postOnMain { doResumeButtons() }
    }

    private fun doResumeButtons() {
        bottomButtons()?.let { bottomButtons ->
            bottomButtons.bottomCenterButton?.visibility = View.GONE
            bottomButtons.bottomRightButton?.visibility = View.GONE
            bottomButtons.bottomLeftButton?.let {
                it.visibility = View.VISIBLE
                buttonAsStart(it)
                it.setOnClickListener {
                    GEMSdkCall.execute { doStart() }
                }
            }
        }
    }

    private fun doPauseButtons() {
        bottomButtons()?.let { bottomButtons ->
            bottomButtons.bottomCenterButton?.visibility = View.GONE
            bottomButtons.bottomRightButton?.visibility = View.GONE
            bottomButtons.bottomLeftButton?.let {
                it.visibility = View.VISIBLE
                buttonAsStop(it)
                it.setOnClickListener {
                    GEMSdkCall.execute { doStop() }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------
