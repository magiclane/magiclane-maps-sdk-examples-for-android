/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
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
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.core.graphics.scale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var errorDialog: BottomSheetDialog? = null
    private var takeScreenshot = false
    private var dataBuffer = DataBuffer()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        setupSdkCallbacks()
        setupScreenshotButton()
    }

    override fun onDestroy() {
        super.onDestroy()

        errorDialog?.dismiss()
        errorDialog = null

        GemSdk.release()
        exitProcess(0)
    }

    private fun setupSdkCallbacks() {
        binding.gemSurface.onSdkInitFailed = { error ->
            handleSdkInitializationError(error)
        }

        binding.gemSurface.onDrawFrameCustom = {
            if (takeScreenshot) {
                takeScreenshot = false
                captureScreenshot()
            }
        }

        SdkSettings.onApiTokenRejected = {
            handleTokenRejection()
        }
    }

    private fun setupScreenshotButton() {
        binding.takeScreenshotButton.setOnClickListener {
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

        val bytes = dataBuffer.bytes ?: return
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val bitmap = decoded.scale(binding.gemSurface.width, binding.gemSurface.height)

        Util.postOnMain {
            showScreenshotPreview(bitmap)
        }
    }

    private fun showScreenshotPreview(bitmap: Bitmap) {
        if (isFinishing || isDestroyed) return

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

    private fun handleSdkInitializationError(error: Int) {
        val errorMessage = getString(
            R.string.sdk_initialization_failed,
            GemError.getMessage(error, this),
        )
        Util.postOnMain {
            showErrorDialog(errorMessage, shouldFinish = true)
        }
    }

    private fun handleTokenRejection() {
        showErrorDialog(getString(R.string.token_rejected_message))
    }

    private fun showErrorDialog(text: String, shouldFinish: Boolean = false) {
        if (errorDialog?.isShowing == true) return
        if (isFinishing || isDestroyed) return

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
}
