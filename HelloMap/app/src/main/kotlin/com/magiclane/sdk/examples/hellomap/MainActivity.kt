/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.hellomap

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.hellomap.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.hellomap.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var errorDialog: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSdkCallbacks()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up dialog to prevent memory leaks
        errorDialog?.dismiss()
        errorDialog = null

        GemSdk.release()
        exitProcess(0)
    }

    /**
     * Set up SDK initialization and token rejection callbacks.
     */
    private fun setupSdkCallbacks() {
        binding.gemSurface.onSdkInitFailed = { error ->
            handleSdkInitializationError(error)
        }

        SdkSettings.onApiTokenRejected = {
            handleTokenRejection()
        }
    }

    /**
     * Handle SDK initialization errors by showing an error dialog and finishing the activity.
     */
    private fun handleSdkInitializationError(error: Int) {
        val errorMessage = getString(
            R.string.sdk_initialization_failed,
            GemError.getMessage(error, this),
        )
        Util.postOnMain {
            showErrorDialog(errorMessage, shouldFinish = true)
        }
    }

    /**
     * Handle API token rejection by showing an error dialog.
     */
    private fun handleTokenRejection() {
        showErrorDialog(getString(R.string.token_rejected_message))
    }

    /**
     * Display an error dialog with the given message.
     *
     * @param text The error message to display.
     * @param shouldFinish Whether to finish the activity when the dialog is dismissed.
     */
    private fun showErrorDialog(text: String, shouldFinish: Boolean = false) {
        // Prevent showing multiple dialogs
        if (errorDialog?.isShowing == true) {
            return
        }

        if (isFinishing || isDestroyed) {
            return
        }

        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.error)
            message.text = text
            button.setOnClickListener {
                errorDialog?.dismiss()
                if (shouldFinish) {
                    finish()
                }
            }
        }

        errorDialog = BottomSheetDialog(this).apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            setOnDismissListener {
                errorDialog = null
            }
            show()
        }
    }
}
