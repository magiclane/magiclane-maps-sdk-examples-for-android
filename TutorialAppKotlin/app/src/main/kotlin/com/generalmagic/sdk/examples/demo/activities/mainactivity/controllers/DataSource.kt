/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import com.generalmagic.apihelper.EnumHelp
import com.generalmagic.datatypes.EDirectBufferType
import com.generalmagic.sdk.*
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.RectF
import com.generalmagic.sdk.d3scene.Canvas
import com.generalmagic.sdk.d3scene.CanvasBufferRenderer
import com.generalmagic.sdk.d3scene.CanvasListener
import com.generalmagic.sdk.d3scene.EFrameFit
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.activities.ChapterLISIAdapter
import com.generalmagic.sdk.examples.demo.activities.GenericListActivity
import com.generalmagic.sdk.examples.demo.activities.ListItemStatusImage
import com.generalmagic.sdk.examples.demo.activities.settings.SettingsProvider
import com.generalmagic.sdk.examples.demo.activities.settings.TBoolSettings
import com.generalmagic.sdk.examples.demo.activities.settings.TIntSettings
import com.generalmagic.sdk.examples.demo.app.BaseActivity
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.app.GEMApplication.postOnMain
import com.generalmagic.sdk.examples.demo.app.MapLayoutController
import com.generalmagic.sdk.examples.demo.app.elements.ButtonsDecorator
import com.generalmagic.sdk.examples.demo.util.IntentHelper
import com.generalmagic.sdk.examples.demo.util.Util
import com.generalmagic.sdk.sensordatasource.*
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkError
import com.generalmagic.sensors.EResolution
import kotlinx.android.synthetic.main.activity_camera.*
import kotlinx.android.synthetic.main.activity_list_view.*
import kotlinx.android.synthetic.main.activity_list_view.root_view
import kotlinx.android.synthetic.main.activity_list_view.toolbar
import kotlinx.android.synthetic.main.tutorial_logplayer.view.*
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
            return SdkCall.execute { toString(data) } ?: ""
        }
    }

    companion object {
        fun toString(SenseData: SenseData): String {
            return when (SenseData.getType()) {
//                EDataType.MountInformation ->{
//                    ""
//                }
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

                    val x = 0//data.getAcquisitionTimestamp()
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

                    val x = data.getFaceType()
                    val y = data.getDeviceOrientation()

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

                else -> {
                    ""
                }
            }
        }
    }
}

class SensorsListActivity : BasicSensorsActivity() {
    private var listener = object : DataSourceListener() {
//        override fun onDataInterruptionEvent(type: EDataType, reason: EDataInterruptionReason, ended: Boolean) {
//            Log.d("GEMSDK", "onDataInterruption: $type, $reason, $ended")
//        }

        override fun onNewData(data: SenseData?) {
            data ?: return

            val type = data.getType()

            val willAdd = lastData[type] == null
            lastData[type] = data

            postOnMain {
                if (willAdd) refresh()
                else list_view.adapter?.notifyDataSetChanged()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SdkCall.execute { doStart() }
    }

    override fun doStart() {
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

        SdkCall.execute {
            val currentDataSource = DataSourceFactory.produceLive()
            currentDataSource ?: return@execute

            for (type in desiredTypes) {
                val errorCode = currentDataSource.addListener(listener, type, false)

                val gemError = SdkError.fromInt(errorCode)
                if (gemError != SdkError.NoError) {
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
}

class DirectCamActivity : BaseActivity() {
    private var surfaceId = -1
    private var surfaceHolder: SurfaceHolder? = null
    private var surface: Surface? = null

    private var currentDataSource: DataSource? = null
    private var listener = object : DataSourceListener() {
        override fun onNewData(data: SenseData?) {
            if (data?.getType() != EDataType.Camera) return

//            val camData = CameraData(data)
//            val configs = camData.getCameraConfiguration() ?: return

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
                    SdkCall.execute { doStart() }
                }
            }
        )
    }

    override fun doBackPressed(): Boolean {
        SdkCall.execute {
            currentDataSource?.removeCameraSurface(surfaceId)
            currentDataSource?.removeListener(listener)
        }

        return true
    }

    override fun doStart() {
        SdkCall.execute {
            val currentDataSource = DataSourceFactory.produceLive()
            currentDataSource ?: return@execute

            val type = EDataType.Camera

            val errorCode = currentDataSource.addListener(listener, type, false)
            val gemError = SdkError.fromInt(errorCode)

            if (gemError != SdkError.NoError) {
                postOnMain {
                    Toast.makeText(this, "LISTENER error $type = $gemError", Toast.LENGTH_SHORT)
                        .show()
                }
                return@execute
            }

            val resultPair = surface?.let {
                currentDataSource.addSurfaceToCamera(it)
            } ?: Pair(SdkError.InvalidInput.value, -1)

            val error = SdkError.fromInt(resultPair.first)
            surfaceId = resultPair.second

            if (error != SdkError.NoError) {
                postOnMain {
                    Toast.makeText(this, "error adding surface = $error", Toast.LENGTH_SHORT)
                        .show()
                }
                return@execute
            }

            this.currentDataSource = currentDataSource
        }
    }
}

open class DataSourceController(context: Context, attrs: AttributeSet) :
    MapLayoutController(context, attrs) {
    protected var currentDataSource: DataSource? = null
}

class FrameDrawController(val context: Context, private val currentDataSource: DataSource) {
    private val subViewCoords = RectF(0.0f, 0.0f, 1.0f, 1.0f)
    private var drawer: CanvasBufferRenderer? = null

    private val canvasListener = object : CanvasListener() {
        var camData: CameraData? = null
        override fun onRender() {
            val buffer = camData?.getDirectBuffer() ?: return
            val bufferType = EDirectBufferType.EByteBuffer
            val configs = camData?.getCameraConfiguration() ?: return

            val width = configs.frameWidth()
            val height = configs.frameHeight()
            val rotation = configs.rotationAngle().toInt()
            val format = configs.pixelFormat()

            drawer?.uploadFrame(bufferType, buffer, width, height, format, rotation)
            drawer?.renderFrame()
        }
    }

    private val framesDrawerListener = object : DataSourceListener() {
        override fun onNewData(data: SenseData?) {
            data ?: return

            SdkCall.execute {
                when (data.getType()) {
                    EDataType.Camera -> {
                        canvasListener.camData = CameraData(data)
                        GEMApplication.getGlContext()?.needsRender()
                    }
                    else -> {
                        /*NOT INTERESTED*/
                    }
                }
            }
        }
    }

    private var canvas: Canvas? = null

    fun doStart() = SdkCall.execute {
        if (!currentDataSource.getAvailableDataTypes().contains(EDataType.Camera)) {
            postOnMain {
                Toast.makeText(context, "CAMERA FRAME IS NOT AVAILABLE !", Toast.LENGTH_SHORT)
                    .show()
            }
            return@execute
        }

        val screen = GEMApplication.getMainMapView()?.getScreen()
        if (screen == null) {
            postOnMain {
                Toast.makeText(context, "No screen !", Toast.LENGTH_SHORT).show()
            }
            return@execute
        }

        this.canvas = Canvas.produce(screen, subViewCoords, canvasListener) ?: return@execute
        this.drawer = CanvasBufferRenderer.produce(canvas)
        this.drawer?.setFrameFit(EFrameFit.FitInside)
//        this.drawer?.setFrameFit(EFrameFit.Stretch)

        val cameraType = EDataType.Camera

        val errorCode = currentDataSource.addListener(framesDrawerListener, cameraType)
        val error = SdkError.fromInt(errorCode)

        if (error != SdkError.NoError) {
            postOnMain {
                Toast.makeText(
                    context,
                    "starting ${cameraType.name} error = ${error.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun doStop() = SdkCall.execute {
        currentDataSource.removeListener(framesDrawerListener)

        canvas?.release()
        canvas = null

        GEMApplication.getGlContext()?.needsRender()

        drawer?.release()
        drawer = null
    }
}

open class LiveDataSourceController(context: Context, attrs: AttributeSet) :
    DataSourceController(context, attrs) {

    override fun doStart() {
        super.doStart()

        SdkCall.execute {
            this.currentDataSource = DataSourceFactory.produceLive() ?: return@execute
        }
    }
}

open class LogDataSourceController(context: Context, attrs: AttributeSet) :
    DataSourceController(context, attrs) {
    private var videoPath = IntentHelper.getObjectForKey(EXTRA_FILEPATH) as String

    companion object {
        const val EXTRA_FILEPATH = "filepath"
    }

    override fun doStart() {
        super.doStart()

        val videoPath = videoPath

        SdkCall.execute {
            val currentDataSource = DataSourceFactory.produceLog(videoPath) ?: return@execute
            this.currentDataSource = currentDataSource
        }
    }
}

class LogRecorderController(context: Context, attrs: AttributeSet) :
    LiveDataSourceController(context, attrs) {

    private var drawer: FrameDrawController? = null
    private var recorder: Recorder? = null
    private val recorderListener = object : ProgressListener() {
        @Suppress("unused")
        private fun export(filepath: String): Boolean {
            val videoFile = File(filepath)
            if (!videoFile.exists())
                return false

            return Util.exportVideo(context, videoFile, GEMApplication.getPublicRecordsDir()) != null
        }

        override fun notifyComplete(reason: Int, hint: String) {
            when (val error = SdkError.fromInt(reason)) {
                SdkError.NoError -> {
//                    val text = if (export(hint)) "Exported!"
//                    else "Not Exported!"
//
//                    postOnMain { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
                }
                else -> {
                    postOnMain {
                        Toast.makeText(context, "${error.name} - $hint", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        override fun notifyStatusChanged(status: Int) {
            val recStatus = EnumHelp.fromInt<ERecorderStatus>(status)

            postOnMain {
                when (recStatus) {
                    ERecorderStatus.Stopped -> {
                        setScreenAlwaysOn(false)
                        GEMApplication.setAppBarVisible(true)

                        Toast.makeText(context, "Recording Stopped!", Toast.LENGTH_SHORT).show()

                        doStoppedButtons()
                    }
                    ERecorderStatus.Starting,
                    ERecorderStatus.Recording -> {
                        setScreenAlwaysOn(true)
                        GEMApplication.setAppBarVisible(false)

                        if (status == ERecorderStatus.Recording.value) {
                            Toast.makeText(context, "Recording!", Toast.LENGTH_SHORT).show()
                        }

                        doRecordingButtons()
                    }

                    else -> {
                    }
                }
            }
        }
    }

    private val orientationListener = object : DataSourceListener() {
        var mOrientation = OrientationData.EOrientationType.Unknown

        override fun onNewData(data: SenseData?) {
            data ?: return

            SdkCall.execute {
                when (data.getType()) {
                    EDataType.Orientation -> {
                        val orientationData = OrientationData(data)
                        val orientation = orientationData.getDeviceOrientation()

                        if (mOrientation != orientation) {
                            val wasUnknown =
                                mOrientation == OrientationData.EOrientationType.Unknown
                            mOrientation = orientation

                            if (!wasUnknown) {
                                postOnMain {
                                    doStop()
                                    doStart()
                                }
                            }
                        }
                    }
                    else -> {
                        /*NOT INTERESTED*/
                    }
                }
            }
        }
    }

    override fun doStart() {
        super.doStart()
        val logsDir = GEMApplication.getInternalRecordsPath()

        SdkCall.execute {
            val currentDataSource = this.currentDataSource ?: return@execute

            val availableTypes = currentDataSource.getAvailableDataTypes()

            val chunkSizeSetting = SettingsProvider.getIntValue(TIntSettings.ERecordingChunk.value)
            val recordAudioSetting =
                SettingsProvider.getBooleanValue(TBoolSettings.ERecordAudio.value)
            val keepRecentSetting = SettingsProvider.getIntValue(TIntSettings.EMinMinutes.value)
            val diskLimitSetting = SettingsProvider.getIntValue(TIntSettings.EDiskLimit.value)

            val chunkLengthInMin = if (chunkSizeSetting.second == 61) {
                RecorderConfiguration.INFINITE_RECORDING
            } else chunkSizeSetting.second.toLong()

            val enableAudio = recordAudioSetting.second

            val keepRecentMin = if (keepRecentSetting.second == 181) {
                RecorderConfiguration.KEEP_ALL_RECORDINGS
            } else keepRecentSetting.second.toLong()

            val diskLimitMB = if (diskLimitSetting.second == 10001) {
                RecorderConfiguration.IGNORE_DISK_LIMIT
            } else diskLimitSetting.second.toLong()

            val quality = EResolution.HD_720p
//            val chunkLengthInMin = 5 //or RecorderConfiguration.INFINITE_RECORDING
            val minimumBatteryPercent = 5
            val continuousRecording = true

            val config = RecorderConfiguration(logsDir, availableTypes)
            config.setVideoQuality(quality)
            config.setEnableAudio(enableAudio)
            config.setContinuousRecording(continuousRecording)
            config.setChunkDurationSeconds(chunkLengthInMin * 60L)
            config.setMinimumBatteryPercent(minimumBatteryPercent)
            config.setKeepMinimumSeconds(keepRecentMin * 60L)
            config.setDiskSpaceLimit(diskLimitMB * 1024 * 1024)

            recorder = Recorder.produce(config, currentDataSource)
            recorder?.addListener(recorderListener)

            val startErrorInt = recorder?.startRecording() ?: SdkError.InternalAbort.value
            val startError = SdkError.fromInt(startErrorInt)

            if (startError == SdkError.NoError) {
                recorder?.startAudioRecording()
                currentDataSource.addListener(orientationListener, EDataType.Orientation)

                drawer = FrameDrawController(context, currentDataSource)
                drawer?.doStart()
            } else {
                postOnMain {
                    val text = "Start error = ${startError.name}"
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun doStop() {
        super.doStop()

        SdkCall.execute {
            recorder?.stopRecording()

            currentDataSource?.removeListener(orientationListener)

            drawer?.doStop()
            drawer = null
        }
    }

    fun doStoppedButtons() {
        hideAllButtons()
        setStartButtonVisible(true)
    }

    fun doRecordingButtons() {
        hideAllButtons()
        setStopButtonVisible(true)
    }
}

class LogPlayerController(context: Context, attrs: AttributeSet) :
    LogDataSourceController(context, attrs) {

    private var drawer: FrameDrawController? = null

    private val playingStatusListener = object : DataSourceListener() {
        override fun onPlayingStatusChanged(type: EDataType, status: EPlayingStatus) {
            if (type != EDataType.Position) return

            SdkCall.execute {
                when (status) {
                    EPlayingStatus.Playing -> onPlaying()
                    EPlayingStatus.Paused -> onPaused()
                    EPlayingStatus.Stopped -> onStopped()
                    else -> {
                    }
                }
            }
        }

        override fun onProgressChanged(progressMs: Long) {
            postOnMain {
                seekBar.progress = progressMs.toInt()
            }
        }
    }

    private val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                SdkCall.execute {
                    val currentDataSource = currentDataSource ?: return@execute
                    val playback = currentDataSource.getPlayback() ?: return@execute

                    playback.setCurrentPosition(progress.toLong())
                }
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
        }
    }

    /** doStart/doResume */
    override fun doStart() {
        var constructedNow = false
        if (currentDataSource == null) {
            super.doStart() // construct data source
            constructedNow = true
        }

        val currentDataSource = currentDataSource ?: return

        seekBar.progress = 0
        seekBar.max = SdkCall.execute {
            currentDataSource.getPlayback()?.getDuration()?.toInt()
        } ?: 0
        seekBar.setOnSeekBarChangeListener(seekBarListener)

        SdkCall.execute {
            if (constructedNow) {
                onPlaying()
                currentDataSource.addListener(playingStatusListener, EDataType.Position)

                val drawer = FrameDrawController(context, currentDataSource)
                drawer.doStart()

                this.drawer = drawer
                postOnMain {
                    setScreenAlwaysOn(true)
                }
            }

            val playback = currentDataSource.getPlayback() ?: return@execute

            if (playback.getState() == EPlayingStatus.Paused) {
                playback.resume()
            }
        }
    }

    /** doPause */
    override fun doStop() {
        super.doStop()

        onStopped()
//        currentDataSource?.getPlayback()?.pause()
    }

    private fun onPlaying() {
        SdkCall.checkCurrentThread() // to be sure is called fro GEMSDK
        postOnMain {
            doPauseButtons()
        }
    }

    private fun onPaused() {
        postOnMain {
            doResumeButtons()
        }
    }

    private fun onStopped() {
        onPaused()

        SdkCall.execute {
            drawer?.doStop()
            drawer = null

            currentDataSource?.removeListener(playingStatusListener)
            currentDataSource = null
        }

        postOnMain {
            setScreenAlwaysOn(false)
        }
    }

    private fun setSeekbarVisibility(visible: Boolean) {
        val item = seekBar
        if (visible) {
            item.visibility = View.VISIBLE
        } else {
            item.visibility = View.GONE
        }
    }

    private fun setPlayStopButtonVisible(visible: Boolean) {
        val item = playBotLeftButton
        if (visible) {
            ButtonsDecorator.buttonAsStop(context, item) {
                doStop()
            }

            item.visibility = View.VISIBLE
        } else {
            item.visibility = View.GONE
        }
    }

    private fun doResumeButtons() {
        hideAllButtons()
        setStartButtonVisible(true)

        setPlayStopButtonVisible(false)
        setSeekbarVisibility(false)
    }

    private fun doPauseButtons() {
        hideAllButtons()

        setPlayStopButtonVisible(true)
        setSeekbarVisibility(true)
    }
}
