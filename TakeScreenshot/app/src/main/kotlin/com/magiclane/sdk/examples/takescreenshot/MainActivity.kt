/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.takescreenshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.takescreenshot.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.takescreenshot.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.takescreenshot.databinding.ScreenshotPreviewLayoutBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        // System insets (status/navigation bars plus display cutout) used to keep the Magic Lane
        // logo and other map UI clear of system UI.
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: ActivityMainBinding

    // Single active error dialog, so we never stack multiple dialogs on top of each other.
    private var errorDialog: BottomSheetDialog? = null

    // Set when the user taps the button; the next rendered frame captures the screenshot.
    private var takeScreenshot = false

    // Reused buffer that receives the captured map image bytes.
    private val dataBuffer = DataBuffer()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status bar icons light so they remain visible over the map and the toolbar.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        registerSdkListeners()
        setupScreenshotButton()

        if (!Util.isInternetConnected(this)) {
            showErrorDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        clearSdkListeners()

        errorDialog?.dismiss()
        errorDialog = null

        // Release the SDK before the activity is fully destroyed.
        GemSdk.release()

        super.onDestroy()
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        // No SdkCall.runSynced wrapper here: onSdkInitFailed is already invoked on the SDK thread.
        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showErrorDialog(errorMessage, shouldFinish = true) }
        }

        // Align the Magic Lane logo with the system window insets as soon as the map is created.
        binding.gemSurface.onDefaultMapViewCreated = {
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. on rotation).
        binding.gemSurface.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        // Capture happens inside the render loop, once the frame requested by the button is drawn.
        binding.gemSurface.onDrawFrameCustom = {
            if (takeScreenshot) {
                takeScreenshot = false
                captureScreenshot()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showErrorDialog(getString(R.string.token_rejected_message)) }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurface.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
            onDrawFrameCustom = null
        }
    }

    private fun setupScreenshotButton() {
        binding.takeScreenshotButton.setOnClickListener {
            // Request a new frame; the capture is performed in onDrawFrameCustom.
            takeScreenshot = true
            SdkCall.runSynced {
                binding.gemSurface.gemScreen?.needsRender()
            }
        }
    }

    private fun captureScreenshot() {
        /*
        // capture to a file
        val path = GemSdk.internalStoragePath + File.separator + "test.jpeg"
        binding.gemSurface.mapView?.captureAsImage(path, Rect())
         */

        binding.gemSurface.mapView?.captureAsImage(dataBuffer, Rect())

        // Decode the captured bytes and scale to the surface size, reporting any failure.
        val bytes = dataBuffer.bytes
        val decoded = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (decoded == null) {
            runOnAliveUi { showErrorDialog(getString(R.string.screenshot_failed)) }
            return
        }
        val bitmap = decoded.scale(binding.gemSurface.width, binding.gemSurface.height)

        runOnAliveUi { showScreenshotPreview(bitmap) }
    }

    private fun showScreenshotPreview(bitmap: Bitmap) {
        if (!isActivityAlive()) return

        // Cap the preview sheet to 75% of the screen height so it never covers the whole screen.
        val maxSheetHeight = (resources.displayMetrics.heightPixels * 0.75).toInt()

        val dialog = BottomSheetDialog(this)
        val previewBinding = ScreenshotPreviewLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.screenshot_preview)
            screenshotImage.setImageBitmap(bitmap)
            closeButton.setOnClickListener { dialog.dismiss() }
        }

        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(true)
            setContentView(previewBinding.root)
            previewBinding.root.layoutParams.height = maxSheetHeight
            show()
        }
    }

    private fun showErrorDialog(text: String, shouldFinish: Boolean = false) {
        if (errorDialog?.isShowing == true) return
        if (!isActivityAlive()) return

        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.error)
            message.text = text
            button.setOnClickListener {
                errorDialog?.dismiss()
                if (shouldFinish) finish()
            }
        }

        errorDialog = BottomSheetDialog(this).apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            setOnDismissListener { errorDialog = null }
            show()
        }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    // Positions the Magic Lane logo (and other map UI) inside the area left free by the system
    // bars and display cutout, so it is never hidden behind system UI. Called when the map view
    // is first created and whenever the surface is resized.
    private fun updateFocusViewport() = SdkCall.runSynced {
        val mapView = binding.gemSurface.mapView ?: return@runSynced
        val viewport = mapView.viewport ?: return@runSynced
        val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

        val left = insets?.left ?: 0
        val top = insets?.top ?: 0
        val right = (viewport.width - (insets?.right ?: 0)).coerceAtLeast(left)
        val bottom = (viewport.height - (insets?.bottom ?: 0)).coerceAtLeast(top)
        mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed
}
