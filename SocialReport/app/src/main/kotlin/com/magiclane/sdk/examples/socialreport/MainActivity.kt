/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.socialreport

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.ESocialOverlayParamsKeys
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SocialOverlay
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.OverlayItem
import com.magiclane.sdk.examples.socialreport.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.socialreport.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 110

        // System insets (status/navigation bars plus display cutout) used to keep map content
        // and the Magic Lane logo clear of system UI.
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private data class SocialEventInfo(
        val overlay: OverlayItem,
        val bitmap: Bitmap?,
        val name: String,
        val time: String,
    )

    private lateinit var binding: ActivityMainBinding

    private var currentOverlayItem: OverlayItem? = null

    // Notifies the user only if a deletion fails; on success the report simply disappears from the map.
    private val deleteReportListener = ProgressListener.create(onCompleted = { error, _ ->
        if (GemError.isError(error)) {
            runOnAliveUi {
                showDialog(
                    getString(
                        R.string.report_delete_failed,
                        SdkCall.runSynced { GemError.getMessage(error, this) },
                    ),
                )
            }
        }
    })

    private lateinit var positionListener: PositionListener

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        registerSdkListeners()

        requestPermissions(this)

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        binding.reportEventButton.setOnClickListener {
            startActivity(Intent(this, ReportCategoriesActivity::class.java))
        }
    }

    override fun onDestroy() {
        clearSdkListeners()
        GemSdk.release()
        super.onDestroy()
        exitProcess(0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val lat = intent.getDoubleExtra(ReportCategoriesActivity.EXTRA_REPORT_LAT, Double.NaN)
        val lon = intent.getDoubleExtra(ReportCategoriesActivity.EXTRA_REPORT_LON, Double.NaN)
        if (!lat.isNaN() && !lon.isNaN()) {
            showReportSentDialog(lat, lon)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    showDialog(getString(R.string.location_permission_required)) {
                        finish()
                        exitProcess(0)
                    }
                    return
                }
            }
            SdkCall.execute { PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults) }
        }
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(errorMessage) { finish() } }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = { mapView ->
            updateFocusViewport()
            if (PositionService.position?.isValid() == true) {
                mapView.followPosition()
                enableGpsButton(mapView)
            } else {
                positionListener = PositionListener {
                    if (!it.isValid()) return@PositionListener
                    mapView.followPosition()
                    PositionService.removeListener(positionListener)
                    enableGpsButton(mapView)
                }
                PositionService.addListener(positionListener, EDataType.Position)
            }
        }

        // Map interactions are only set up once the worldwide road map is confirmed available.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}
                SdkCall.execute {
                    val mapView = binding.gemSurfaceView.mapView ?: return@execute

                    mapView.onTouch = { xy ->
                        SdkCall.execute {
                            mapView.cursorScreenPosition = xy
                            val eventInfo = fetchSocialEventInfo(mapView.cursorSelectionOverlayItems)
                            if (eventInfo != null) {
                                Util.postOnMain { showSocialEventPanel(eventInfo) }
                            } else {
                                Util.postOnMain { hideSocialEventPanel() }
                            }
                        }
                    }
                }
            }
        }

        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    private fun clearSdkListeners() {
        if (::positionListener.isInitialized) {
            PositionService.removeListener(positionListener)
        }
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
            mapView?.onTouch = {}
        }
    }

    // Positions the Magic Lane logo (and other map UI) inside the area left free by the system
    // bars and display cutout, so it is never hidden behind system UI. Called when the map view
    // is first created and whenever the surface is resized (e.g. on rotation).
    private fun updateFocusViewport() = SdkCall.runSynced {
        val mapView = binding.gemSurfaceView.mapView ?: return@runSynced
        val viewport = mapView.viewport ?: return@runSynced
        val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

        val left = insets?.left ?: 0
        val top = insets?.top ?: 0
        val right = (viewport.width - (insets?.right ?: 0)).coerceAtLeast(left)
        val bottom = (viewport.height - (insets?.bottom ?: 0)).coerceAtLeast(top)
        mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
    }

    private fun enableGpsButton(mapView: MapView) = Util.postOnMain {
        mapView.onExitFollowingPosition = { binding.followGpsButton.visibility = View.VISIBLE }
        mapView.onEnterFollowingPosition = { binding.followGpsButton.visibility = View.GONE }
        binding.followGpsButton.setOnClickListener {
            SdkCall.execute { mapView.followPosition() }
        }
    }

    // Must be called on the SDK thread. Returns null if the tapped overlay is not
    // a self-reported social event (i.e., one the current user can delete).
    private fun fetchSocialEventInfo(overlays: List<OverlayItem>?): SocialEventInfo? {
        val overlay = overlays?.firstOrNull {
            it.overlayInfo?.uid == ECommonOverlayId.SocialReports.value
        } ?: return null

        val previewData = overlay.getPreviewData() ?: return null

        // "allow_delete" is true only on reports submitted by the current user.
        if (previewData.find { it.key == "allow_delete" }?.valueBoolean != true) return null

        val iconSize = resources.getDimension(R.dimen.event_image_size).toInt()
        return SocialEventInfo(
            overlay = overlay,
            bitmap = overlay.image?.asBitmap(iconSize, iconSize),
            name = overlay.name.toString(),
            time = formatEventTimestamp(
                previewData.find { it.key == ESocialOverlayParamsKeys.ReportCreateTimeUTC.value }?.valueLong ?: 0,
            ),
        )
    }

    private fun showSocialEventPanel(info: SocialEventInfo) {
        currentOverlayItem = info.overlay
        binding.icon.setImageBitmap(info.bitmap)
        binding.text.text = info.name
        binding.time.text = info.time
        binding.eventPanel.visibility = View.VISIBLE
        binding.deleteButton.setOnClickListener {
            val item = currentOverlayItem ?: return@setOnClickListener
            SdkCall.execute { SocialOverlay.deleteReport(item, deleteReportListener) }
            hideSocialEventPanel()
        }
    }

    private fun hideSocialEventPanel() {
        currentOverlayItem = null
        binding.eventPanel.visibility = View.GONE
    }

    private fun showReportSentDialog(lat: Double, lon: Double) {
        showBottomSheet(
            title = getString(R.string.info),
            message = getString(R.string.report_sent_message),
            onShow = { root ->
                // Wait for the sheet to be laid out before reading its height.
                root.post {
                    val freeRect = getFreeSpaceRect(bottomOffset = root.height)
                    SdkCall.execute {
                        binding.gemSurfaceView.mapView?.centerOnCoordinates(
                            Coordinates(lat, lon),
                            -1,
                            freeRect.center,
                            Animation(EAnimation.Linear, 900),
                            0.0,
                            0.0,
                        )
                    }
                }
            },
        )
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        showBottomSheet(getString(R.string.error), text, onButton = onDismiss)
    }

    // Creates and shows a non-dismissible, fully-expanded bottom sheet.
    // onShow receives the content root view once the sheet is visible and attached to the window.
    private fun showBottomSheet(
        title: String,
        message: String,
        onButton: (() -> Unit)? = null,
        onShow: ((View) -> Unit)? = null,
    ) {
        if (!isActivityAlive()) return
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            this.title.text = title
            this.message.text = message
            button.setOnClickListener {
                dialog.dismiss()
                onButton?.invoke()
            }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            if (onShow != null) setOnShowListener { onShow(dialogBinding.root) }
            show()
        }
    }

    // Returns the usable map viewport as a Rect, excluding system bars, cutouts, the toolbar,
    // and an optional bottom offset (e.g., a bottom sheet overlapping the map).
    private fun getFreeSpaceRect(bottomOffset: Int = 0): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsets(SYSTEM_INSET_TYPES)
        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val left = insets?.left ?: 0
        val right = (width - (insets?.right ?: 0)).coerceAtLeast(left)
        val topInset = insets?.top ?: 0
        val toolbarBottom = binding.toolbar.bottom.takeIf { it > 0 } ?: 0
        val top = maxOf(topInset, toolbarBottom)
        val bottomInset = insets?.bottom ?: 0
        val bottom = (height - bottomInset - bottomOffset).coerceAtLeast(top)
        return Rect(left, top, right, bottom)
    }

    private fun formatEventTimestamp(stampUtcSeconds: Long): String {
        val eventTime = Calendar.getInstance(Locale.getDefault()).also { it.timeInMillis = stampUtcSeconds * 1000 }
        val now = Calendar.getInstance(Locale.getDefault())
        val sameDay = eventTime.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            eventTime.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
            eventTime.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)
        val pattern = if (sameDay) "HH:mm" else "dd/MM/yyyy"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(eventTime.timeInMillis))
    }

    private fun requestPermissions(activity: Activity): Boolean {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        return PermissionsHelper.requestPermissions(REQUEST_LOCATION_PERMISSION, activity, permissions)
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
