// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.senddebuginfo

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.magiclane.sdk.core.*
import com.magiclane.sdk.util.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

@Suppress("SameParameterValue")
class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var progressBar: ProgressBar
    private lateinit var sendDebugInfoButton: Button

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        gemSurfaceView = findViewById(R.id.gem_surface)
        sendDebugInfoButton = findViewById(R.id.sendDebugInfo)

        progressBar.visibility = View.VISIBLE

        sendDebugInfoButton.setOnClickListener {
            var subject = ""
            SdkCall.execute {
                subject = GemSdk.sdkVersion?.let {
                    String.format("User feedback (SDK example) - %d.%d.%d.%d.%s", it.major, it.minor, it.year, it.week, it.revision)
                } ?: "User feedback"
                System.gc()
            }

            GEMLog.debug(this, "This is an UI message!")

            sendFeedback(this, "support@magicearth.com", subject)
        }

        SdkSettings.onMapDataReady = {
            Util.postOnMain {
                progressBar.visibility = View.GONE
                sendDebugInfoButton.visibility = View.VISIBLE
            }
        }

        SdkSettings.onApiTokenRejected = {
            /*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        if (!Util.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!")
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    @Suppress("SameParameterValue")
    private class SendFeedbackTask(val activity: Activity, val email: String, val subject: String) : CoroutinesAsyncTask<Void, Void, Intent>()
    {
        override fun doInBackground(vararg params: Void?): Intent
        {
            val subjectText = subject
            val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
            sendIntent.type = "message/rfc822"
            sendIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, subjectText)

            val emailBody = "\n\n$subjectText"
            sendIntent.putExtra(Intent.EXTRA_TEXT, emailBody)

            var publicLogPath = ""
            val privateLogPath = GemSdk.appLogPath
            privateLogPath?.let {
                val path = GemUtil.getApplicationPublicFilesAbsolutePath(activity, "phoneLog.txt")
                if (GemUtil.copyFile(it, path))
                {
                    publicLogPath = path
                }
            }

            val uris = ArrayList<Uri>()
            if (publicLogPath.isNotEmpty())
            {
                val file = File(publicLogPath)
                file.deleteOnExit()

                try
                {
                    uris.add(FileProvider.getUriForFile(activity, activity.packageName + ".provider", file))
                }
                catch (e: Exception)
                {
                    GEMLog.error(this, "SendFeedbackTask.doInBackground(): error =  ${e.message}")
                }
            }

            if (GemSdk.internalStoragePath.isNotEmpty())
            {
                val gmCrashesPath = GemSdk.internalStoragePath + File.separator + "GMcrashlogs" + File.separator + "last"

                val file = File(gmCrashesPath)
                if (file.exists() && file.isDirectory)
                {
                    val files = file.listFiles()
                    files?.forEach breakLoop@{
                        try
                        {
                            uris.add(FileProvider.getUriForFile(activity, activity.packageName + ".provider", it))
                        }
                        catch (e: Exception)
                        {
                            GEMLog.error(this, "SendFeedbackTask.doInBackground(): error =  ${e.message}")
                        }
                        return@breakLoop
                    }
                }
            }

            sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            return sendIntent
        }

        override fun onPostExecute(result: Intent?)
        {
            if (result == null) return

            activity.startActivity(result)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun sendFeedback(a: Activity, email: String, subject: String)
    {
        val sendFeedbackTask = SendFeedbackTask(a, email, subject)
        sendFeedbackTask.execute(null)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    @SuppressLint("InflateParams")
    private fun showDialog(text: String)
    {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_layout, null).apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.error)
            findViewById<TextView>(R.id.message).text = text
            findViewById<Button>(R.id.button).setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.apply {
            setCancelable(false)
            setContentView(view)
            show()
        }
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
