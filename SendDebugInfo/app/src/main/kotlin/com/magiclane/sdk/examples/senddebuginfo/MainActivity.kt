/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.senddebuginfo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.senddebuginfo.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.senddebuginfo.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.GEMLog
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.io.File
import kotlin.system.exitProcess

@Suppress("SameParameterValue")
class MainActivity : AppCompatActivity() {

    companion object {
        // System insets (status/navigation bars plus display cutout) used to keep the Magic Lane
        // logo and other map UI clear of system UI.
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: ActivityMainBinding

    @SuppressLint("DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show the spinner while waiting for the SDK to become ready.
        binding.progressBar.visibility = View.VISIBLE

        // Keep status bar icons light so they are visible over the map.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        // Build a feedback email (subject tagged with the SDK version) and attach the SDK logs.
        binding.sendDebugInfoButton.setOnClickListener {
            var subject = ""
            SdkCall.execute {
                subject = GemSdk.sdkVersion?.let {
                    String.format(
                        "User feedback (SDK example) - %d.%d.%d.%d.%s",
                        it.major,
                        it.minor,
                        it.year,
                        it.week,
                        it.revision,
                    )
                } ?: "User feedback"
                // Run GC before capturing the log so the memory snapshot is clean.
                System.gc()
            }

            // Write a sample message to the log so it appears in the attached log file.
            GEMLog.debug(this, "This is an UI message!")

            sendFeedback(this, "support@magicearth.com", subject)
        }

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        // Unregister listeners before releasing to avoid callbacks firing during teardown.
        clearSdkListeners()

        // Release the SDK before the activity is fully destroyed.
        GemSdk.release()

        super.onDestroy()
        // Force-exit to terminate any lingering SDK native threads.
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        // Fired if the SDK fails to initialise (e.g. missing or invalid native libraries).
        // No enclosing SdkCall.runSynced is needed here: this callback already runs in an SDK
        // context, so GemError.getMessage can be called directly.
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) { finish() }
            }
        }

        // Align the Magic Lane logo with the system window insets as soon as the map is created.
        binding.gemSurfaceView.onDefaultMapViewCreated = {
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. on rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        // Fired when the worldwide road-map support status changes. Once the map is ready
        // (UpToDate) we clear this single-shot listener and reveal the send button.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
                runOnAliveUi {
                    binding.progressBar.visibility = View.GONE
                    binding.sendDebugInfoButton.visibility = View.VISIBLE
                }
            }
        }

        /**
         * The TOKEN you provided in the AndroidManifest.xml file was rejected.
         * Make sure you provide the correct value, or if you don't have a TOKEN,
         * check the magiclane.com website, sign up/sign in and generate one.
         */
        SdkSettings.onApiTokenRejected = {
            runOnAliveUi {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // Positions the Magic Lane logo (and other map UI) inside the area left free by the system
    // bars and display cutout, so it is never hidden behind system UI. Called when the map view
    // is first created and whenever the surface is resized.
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

    @Suppress("SameParameterValue")
    private class SendFeedbackTask(
        val activity: Activity,
        val email: String,
        val subject: String,
    ) : CoroutinesAsyncTask<Void, Void, Intent>() {
        override fun doInBackground(vararg params: Void?): Intent {
            val subjectText = subject
            val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
            sendIntent.type = "message/rfc822"
            sendIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, subjectText)

            val emailBody = "\n\n$subjectText"
            sendIntent.putExtra(Intent.EXTRA_TEXT, emailBody)

            var publicLogPath = ""
            val privateLogPath = GemSdk.appLogPath
            // The SDK log lives in private storage; copy it to a public location so
            // FileProvider can attach it to the email intent.
            privateLogPath?.let {
                val path = GemUtil.getApplicationPublicFilesAbsolutePath(activity, "phoneLog.txt")
                if (GemUtil.copyFile(it, path)) {
                    publicLogPath = path
                }
            }

            val uris = ArrayList<Uri>()
            if (publicLogPath.isNotEmpty()) {
                val file = File(publicLogPath)
                // Schedule cleanup — the temp copy is no longer needed once the email client reads it.
                file.deleteOnExit()

                try {
                    uris.add(
                        FileProvider.getUriForFile(
                            activity,
                            activity.packageName + ".provider",
                            file,
                        ),
                    )
                } catch (e: Exception) {
                    GEMLog.error(this, "SendFeedbackTask.doInBackground(): error =  ${e.message}")
                }
            }

            if (GemSdk.internalStoragePath.isNotEmpty()) {
                val gmCrashesPath =
                    GemSdk.internalStoragePath + File.separator + "GMcrashlogs" + File.separator + "last"

                val file = File(gmCrashesPath)
                if (file.exists() && file.isDirectory) {
                    val files = file.listFiles()
                    // Attach only the first (most recent) crash log file.
                    files?.forEach breakLoop@{
                        try {
                            uris.add(
                                FileProvider.getUriForFile(
                                    activity,
                                    activity.packageName + ".provider",
                                    it,
                                ),
                            )
                        } catch (e: Exception) {
                            GEMLog.error(
                                this,
                                "SendFeedbackTask.doInBackground(): error =  ${e.message}",
                            )
                        }
                        return@breakLoop
                    }
                }
            }

            sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            return sendIntent
        }

        override fun onPostExecute(result: Intent?) {
            if (result == null) return

            activity.startActivity(result)
        }
    }

    private fun sendFeedback(a: Activity, email: String, subject: String) {
        val sendFeedbackTask = SendFeedbackTask(a, email, subject)
        sendFeedbackTask.execute(null)
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        if (!isActivityAlive()) return

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
            // Force fully expanded so the user can't accidentally dismiss by dragging.
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            show()
        }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain {
            if (isActivityAlive()) {
                block()
            }
        }
    }

    private fun isActivityAlive(): Boolean {
        return !isFinishing && !isDestroyed
    }
}
