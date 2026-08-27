/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.downloadamap

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.ContentStoreItem
import com.magiclane.sdk.content.EContentStoreItemStatus
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.downloadamap.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.downloadamap.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val contentStore = ContentStore()

    // Cache flag bitmaps by ISO country code to avoid redundant rendering.
    private val flagBitmapsMap = HashMap<String, Bitmap?>()

    // Called back when the SDK token verification completes.
    private val checkAuthorizationListener = ProgressListener.create(onCompleted = { errorCode, _ ->
        if (errorCode != GemError.NoError) {
            runOnAliveUi { showInvalidTokenDialog() }
        } else {
            loadMapsCatalog()
        }
    })

    // Tracks catalog fetch progress and triggers the first-item download on success.
    private val progressListener = ProgressListener.create(
        onStarted = {
            runOnAliveUi {
                binding.progressBar.visibility = View.VISIBLE
                showStatusMessage(getString(R.string.status_downloading_maps_catalog))
            }
        },
        onCompleted = { errorCode, _ ->
            runOnAliveUi { binding.progressBar.visibility = View.GONE }

            when (errorCode) {
                GemError.NoError -> {
                    SdkCall.execute {
                        val mapsCatalog = contentStore.getStoreContentList(EContentType.RoadMap)?.first

                        if (!mapsCatalog.isNullOrEmpty()) {
                            val mapItem = mapsCatalog[0]
                            val itemName = mapItem.name

                            val downloadProgressListener = ProgressListener.create(
                                onStarted = {
                                    runOnAliveUi {
                                        showStatusMessage(
                                            getString(R.string.status_downloading_item, itemName),
                                        )
                                    }
                                },
                                onProgress = {
                                    runOnAliveUi { binding.listView.adapter?.notifyItemChanged(0) }
                                },
                                onCompleted = { dlErrorCode, _ ->
                                    runOnAliveUi { binding.listView.adapter?.notifyItemChanged(0) }
                                    if (dlErrorCode == GemError.NoError) {
                                        runOnAliveUi {
                                            showStatusMessage(getString(R.string.status_item_downloaded, itemName))
                                        }
                                    } else {
                                        runOnAliveUi {
                                            showStatusMessage(
                                                getString(
                                                    R.string.status_item_download_error,
                                                    itemName,
                                                    SdkCall.runSynced { GemError.getMessage(dlErrorCode, this) },
                                                ),
                                            )
                                        }
                                    }
                                    EspressoIdlingResource.decrement()
                                },
                            )

                            SdkCall.execute {
                                val error = mapItem.asyncDownload(
                                    downloadProgressListener,
                                    GemSdk.EDataSavePolicy.UseDefault,
                                    true,
                                )
                                when (error) {
                                    GemError.NoError -> { /* Download started; downloadProgressListener will handle updates. */ }
                                    GemError.UpToDate -> runOnAliveUi {
                                        showStatusMessage(getString(R.string.status_item_already_downloaded, itemName))
                                    }
                                    else -> runOnAliveUi {
                                        showStatusMessage(
                                            getString(
                                                R.string.status_download_item_error,
                                                SdkCall.runSynced { GemError.getMessage(error, this) },
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                        Util.postOnMain { displayList(mapsCatalog) }
                    }
                }

                else -> {
                    runOnAliveUi {
                        showStatusMessage(
                            getString(
                                R.string.status_maps_catalog_download_error,
                                SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                            ),
                        )
                    }
                    EspressoIdlingResource.decrement()
                }
            }
        },
    )

    private fun loadMapsCatalog() = SdkCall.execute {
        val error = contentStore.asyncGetStoreContentList(EContentType.RoadMap, progressListener)
        if (error != GemError.NoError) {
            runOnAliveUi {
                showStatusMessage(
                    getString(
                        R.string.status_maps_catalog_download_error,
                        SdkCall.runSynced { GemError.getMessage(error, this) },
                    ),
                )
            }
            EspressoIdlingResource.decrement()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        EspressoIdlingResource.increment()

        binding.listView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(
                DividerItemDecoration(applicationContext, (layoutManager as LinearLayoutManager).orientation),
            )
            itemAnimator = null
        }

        registerSdkListeners()

        // This step of initialization is mandatory if you want to use the SDK without a map.
        val errorCode = GemSdk.initSdkWithDefaults(this)
        if (errorCode != GemError.NoError) {
            showDialog(
                getString(
                    R.string.dialog_sdk_initialization_error,
                    SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                ),
            ) {
                finish()
                exitProcess(0)
            }
        }

        if (!Util.isInternetConnected(this)) {
            binding.progressBar.visibility = View.GONE
            showDialog(getString(R.string.dialog_internet_required))
        } else {
            showStatusMessage(getString(R.string.status_connecting_magic_lane_servers))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    // Registers all SDK-level callbacks.
    private fun registerSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                runOnAliveUi { showStatusMessage(getString(R.string.status_checking_token_validity)) }
                SdkSettings.appAuthorization?.let {
                    SdkCall.execute { SdkSettings.verifyAppAuthorization(it, checkAuthorizationListener) }
                } ?: run {
                    runOnAliveUi { showInvalidTokenDialog() }
                }
                // Unsubscribe after the first UpToDate event to avoid repeated verification.
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showInvalidTokenDialog() }
        }
    }

    // Clears SDK-level callbacks to prevent them reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun displayList(models: ArrayList<ContentStoreItem>?) {
        if (models != null) {
            binding.listView.adapter = CustomAdapter(models)
        }
    }

    /** Shows a non-dismissable bottom-sheet error dialog. */
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
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            show()
        }
    }

    private fun showInvalidTokenDialog() {
        showDialog(getString(R.string.invalid_token)) {
            finish()
            exitProcess(0)
        }
        binding.progressBar.isVisible = false
    }

    private fun showStatusMessage(text: String) {
        if (!binding.statusText.isVisible) {
            binding.statusText.visibility = View.VISIBLE
        }
        binding.statusText.text = text
    }

    inner class CustomAdapter(private val dataSet: ArrayList<ContentStoreItem>) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.text)
            val description: TextView = view.findViewById(R.id.description)
            val imageView: ImageView = view.findViewById(R.id.icon)
            val progressBar: ProgressBar = view.findViewById(R.id.item_progress_bar)
            val statusImageView: ImageView = view.findViewById(R.id.status_icon)
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item, viewGroup, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            viewHolder.apply {
                text.text = SdkCall.execute { dataSet[position].name }
                description.text = SdkCall.execute { GemUtil.formatSizeAsText(dataSet[position].totalSize) }
                imageView.setImageBitmap(SdkCall.execute { getFlagBitmap(dataSet[position]) })

                statusImageView.visibility = View.GONE
                progressBar.visibility = View.INVISIBLE

                when (SdkCall.execute { dataSet[position].status }) {
                    EContentStoreItemStatus.Completed -> {
                        statusImageView.visibility = View.VISIBLE
                        progressBar.visibility = View.INVISIBLE
                    }

                    EContentStoreItemStatus.DownloadRunning -> {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = SdkCall.execute { dataSet[position].downloadProgress } ?: 0
                    }

                    else -> return
                }
            }
        }

        override fun getItemCount() = dataSet.size

        private fun getFlagBitmap(item: ContentStoreItem): Bitmap? {
            item.countryCodes?.let { codes ->
                if (codes.isNotEmpty()) {
                    val isoCode = codes[0]
                    if (!flagBitmapsMap.containsKey(isoCode)) {
                        val size = resources.getDimension(R.dimen.icon_size).toInt()
                        flagBitmapsMap[isoCode] = MapDetails().getCountryFlag(isoCode)?.asBitmap(size, size)
                    }
                    return flagBitmapsMap[isoCode]
                }
                return null
            }
            return null
        }
    }
}

//region TESTING
@VisibleForTesting
object EspressoIdlingResource {
    private const val IDLING_RESOURCE_NAME = "DownloadAMapIdleRes"
    val espressoIdlingResource = CountingIdlingResource(IDLING_RESOURCE_NAME)

    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) {
        espressoIdlingResource.decrement()
    } else {
    }
}
//endregion
