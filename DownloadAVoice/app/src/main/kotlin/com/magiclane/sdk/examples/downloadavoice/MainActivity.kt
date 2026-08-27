/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.downloadavoice

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.ContentStoreItem
import com.magiclane.sdk.content.EContentStoreItemStatus
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.downloadavoice.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.downloadavoice.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.downloadavoice.databinding.ListItemBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val contentStore = ContentStore()

    // Flag bitmap cache keyed by ISO country code — avoids re-fetching on every list bind.
    private val flagBitmapsMap = HashMap<String, Bitmap?>()

    // Fires once after verifyAppAuthorization completes.
    private val checkAuthorizationListener = ProgressListener.create(
        onCompleted = { errorCode, _ ->
            if (errorCode != GemError.NoError) {
                showInvalidTokenDialog()
            } else {
                // Authorization confirmed — safe to load the voices catalog.
                loadVoicesCatalog()
            }
        },
    )

    // Tracks async retrieval of the voices catalog from the content store.
    private val progressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
            showStatusMessage(getString(R.string.status_downloading_voices_catalog))
        },
        onCompleted = { errorCode, _ ->
            binding.progressBar.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> SdkCall.execute {
                    val voicesList = contentStore.getStoreContentList(EContentType.HumanVoice)?.first
                    // Kick off a download for the first available voice, if any.
                    voicesList?.firstOrNull()?.let { downloadFirstVoice(it) }
                    Util.postOnMain { displayList(voicesList) }
                }
                else -> showStatusMessage(
                    getString(
                        R.string.status_voices_catalog_download_error,
                        SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                    ),
                )
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding.listView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(DividerItemDecoration(applicationContext, LinearLayoutManager.VERTICAL))
            val lateralPadding = resources.getDimension(R.dimen.big_padding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)
            itemAnimator = null
        }

        val initResult = GemSdk.initSdkWithDefaults(this)
        if (initResult != GemError.NoError) {
            showDialog(
                message = getString(
                    R.string.sdk_initialization_failed,
                    SdkCall.runSynced { GemError.getMessage(initResult, this) },
                ),
            ) { finish() }
            return
        }

        if (!Util.isInternetConnected(this)) {
            runOnAliveUi { showDialog(message = getString(R.string.internet_required)) }
        } else {
            showStatusMessage(getString(R.string.status_connecting_magic_lane_servers))
        }

        registerSdkListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        SdkSettings.onApiTokenRejected = { showInvalidTokenDialog() }

        // Verify the app token on the first successful internet connection.
        // Self-clearing so it fires only once per session.
        SdkSettings.onConnectionStatusUpdated = { isConnected ->
            if (isConnected) {
                SdkSettings.onConnectionStatusUpdated = {}
                SdkSettings.appAuthorization?.let {
                    showStatusMessage(getString(R.string.status_checking_token_validity))
                    SdkCall.execute { SdkSettings.verifyAppAuthorization(it, checkAuthorizationListener) }
                } ?: showInvalidTokenDialog()
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        SdkSettings.onConnectionStatusUpdated = {}
    }

    private fun loadVoicesCatalog() = SdkCall.execute {
        val error = contentStore.asyncGetStoreContentList(EContentType.HumanVoice, progressListener)
        if (error != GemError.NoError) {
            // asyncGetStoreContentList can fail immediately (e.g. no network) before the listener fires.
            Util.postOnMain {
                showStatusMessage(
                    getString(
                        R.string.status_voices_catalog_download_error,
                        SdkCall.runSynced { GemError.getMessage(error, this) },
                    ),
                )
            }
        }
    }

    // Must be called from within SdkCall.execute. Kicks off an async download for voiceItem
    // and handles the immediate result: UpToDate (already downloaded) or error-on-start.
    private fun downloadFirstVoice(voiceItem: ContentStoreItem) {
        val itemName = voiceItem.name

        // Progress callbacks run on the main thread; no explicit posting needed inside them.
        val downloadProgressListener = ProgressListener.create(
            onStarted = {
                showStatusMessage(getString(R.string.status_downloading_item, itemName))
            },
            onProgress = {
                binding.listView.adapter?.notifyItemChanged(0)
            },
            onCompleted = { errorCode, _ ->
                binding.listView.adapter?.notifyItemChanged(0)
                if (errorCode == GemError.NoError) {
                    showStatusMessage(getString(R.string.status_item_downloaded, itemName))
                } else {
                    showStatusMessage(
                        getString(
                            R.string.status_item_download_error,
                            itemName,
                            SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                        ),
                    )
                }
            },
        )

        // asyncDownload returns immediately; NoError means the download started and
        // downloadProgressListener will receive further callbacks.
        val downloadError = voiceItem.asyncDownload(
            downloadProgressListener,
            GemSdk.EDataSavePolicy.UseDefault,
            true,
        )

        when (downloadError) {
            GemError.NoError -> { /* download started — progress handled by downloadProgressListener */ }
            GemError.UpToDate -> Util.postOnMain {
                showStatusMessage(getString(R.string.status_item_already_downloaded, itemName))
            }
            else -> Util.postOnMain {
                showStatusMessage(
                    getString(
                        R.string.status_download_item_error,
                        SdkCall.runSynced { GemError.getMessage(downloadError, this) },
                    ),
                )
            }
        }
    }

    private fun showDialog(
        title: String = getString(R.string.error),
        message: String,
        onDismiss: (() -> Unit)? = null,
    ) {
        if (!isActivityAlive()) return

        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            this.title.text = title
            this.message.text = message
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

    private fun showInvalidTokenDialog() {
        runOnAliveUi { showDialog(message = getString(R.string.invalid_token)) { finish() } }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    private fun displayList(voicesList: ArrayList<ContentStoreItem>?) {
        voicesList?.let { binding.listView.adapter = CustomAdapter(it) }
    }

    private fun showStatusMessage(text: String) {
        binding.statusText.text = text
    }

    inner class CustomAdapter(private val dataSet: ArrayList<ContentStoreItem>) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ListItemBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = ListItemBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val item = dataSet[position]
            viewHolder.binding.apply {
                text.text = SdkCall.execute {
                    "${item.name} (${"%.1f MB".format(item.totalSize / 1_048_576.0)})"
                }
                description.text = SdkCall.execute {
                    "${getCountryName(item)} - ${getParameter(item, "native_language")}"
                }
                icon.setImageBitmap(SdkCall.execute { getFlagBitmap(item) })
                genderIcon.setImageResource(
                    if (SdkCall.execute { getParameter(item, "gender").lowercase() } == "male") {
                        R.drawable.male
                    } else {
                        R.drawable.female
                    },
                )

                statusIcon.visibility = View.GONE
                itemProgressBar.visibility = View.INVISIBLE
                when (SdkCall.execute { item.status }) {
                    EContentStoreItemStatus.Completed -> {
                        statusIcon.visibility = View.VISIBLE
                        itemProgressBar.visibility = View.INVISIBLE
                    }
                    EContentStoreItemStatus.DownloadRunning -> {
                        itemProgressBar.visibility = View.VISIBLE
                        itemProgressBar.progress = SdkCall.execute { item.downloadProgress } ?: 0
                    }
                    else -> return // item not yet tracked; skip update
                }
            }
        }

        override fun getItemCount() = dataSet.size

        private fun getFlagBitmap(item: ContentStoreItem): Bitmap? {
            val isoCode = item.countryCodes?.firstOrNull() ?: return null
            if (!flagBitmapsMap.containsKey(isoCode)) {
                val size = resources.getDimension(R.dimen.icon_size).toInt()
                flagBitmapsMap[isoCode] = MapDetails().getCountryFlag(isoCode)?.asBitmap(size, size)
            }
            return flagBitmapsMap[isoCode]
        }

        private fun getCountryName(item: ContentStoreItem): String =
            item.countryCodes?.firstOrNull()?.let { MapDetails().getCountryName(it) } ?: ""

        private fun getParameter(item: ContentStoreItem, parameter: String): String = item.contentParameters
            ?.firstOrNull { it.name?.equals(parameter, ignoreCase = true) == true }
            ?.valueString
            ?: ""
    }
}
