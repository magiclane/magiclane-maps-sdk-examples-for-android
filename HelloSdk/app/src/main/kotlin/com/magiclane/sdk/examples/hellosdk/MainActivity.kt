/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.hellosdk

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.hellosdk.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.hellosdk.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.SdkCall
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding.sdkStatusText.text = getString(R.string.sdk_initializing_message)

        registerSdkCallbacks()
        initializeSdk()
    }

    private fun initializeSdk() {
        val error = GemSdk.initSdkWithDefaults(this)
        if (error != GemError.NoError) {
            showDialog(
                getString(R.string.sdk_initialization_failed, SdkCall.runSynced { GemError.getMessage(error, this) }),
            ) {
                // The SDK initialization failed, so we exit the app.
                finish()
            }
        } else {
            SdkCall.execute {
                GemSdk.sdkVersion?.let {
                    val version = getString(
                        R.string.sdk_version_format,
                        it.major,
                        it.minor,
                        it.year,
                        it.week,
                        it.revision,
                    )
                    runOnUiThread {
                        binding.sdkStatusText.text = getString(R.string.sdk_initialized_message, version)
                    }
                } ?: runOnUiThread {
                    binding.sdkStatusText.text = getString(
                        R.string.sdk_initialized_message,
                        getString(R.string.sdk_version_unavailable),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkCallbacks()

        // Release the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.error)
            message.text = text
            button.setOnClickListener {
                onDismiss?.invoke()
                dialog.dismiss()
            }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            show()
        }
    }

    private fun registerSdkCallbacks() {
        SdkSettings.onApiTokenRejected = {
            showDialog(getString(R.string.token_rejected_message))
        }
    }

    private fun clearSdkCallbacks() {
        SdkSettings.onApiTokenRejected = {}
    }
}
