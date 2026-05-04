/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.downloadingonboardmap

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
import com.magiclane.sdk.examples.downloadingonboardmap.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.downloadingonboardmap.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val contentStore = ContentStore()

    private val flagBitmapsMap = HashMap<String, Bitmap?>()

    private val checkAuthorizationListener = ProgressListener.create(onCompleted = { errorCode, _ ->
        if (errorCode != GemError.NoError) {
            showInvalidTokenDialog()
        } else {
            // The app authorization is valid, we can start loading the content store.
            loadMapsCatalog()
        }
    })

    private val progressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
            showStatusMessage(getString(R.string.status_downloading_maps_catalog))
        },
        onCompleted = { errorCode, _ ->
            binding.progressBar.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> {
                    SdkCall.execute {
                        // No error encountered, we can handle the results.
                        val models = contentStore.getStoreContentList(
                            EContentType.RoadMap,
                        )?.first

                        if (!models.isNullOrEmpty()) {
                            // The map items list is not empty or null.
                            val mapItem = models[0]
                            val itemName = mapItem.name

                            // Define a listener to the progress of the map download action.
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
                                                GemError.getMessage(errorCode, this),
                                            ),
                                        )
                                    }
                                    EspressoIdlingResource.decrement()
                                },
                            )

                            // Start downloading the first map item.
                            SdkCall.execute {
                                val errorCode = mapItem.asyncDownload(
                                    downloadProgressListener,
                                    GemSdk.EDataSavePolicy.UseDefault,
                                    true,
                                )

                                when (errorCode) {
                                    GemError.UpToDate -> {
                                        // The item is already downloaded and up to date.
                                        Util.postOnMain {
                                            showStatusMessage(
                                                getString(R.string.status_item_already_downloaded, itemName),
                                            )
                                        }
                                    }
                                    else -> {
                                        // There was a problem at starting the download action.
                                        Util.postOnMain {
                                            showStatusMessage(
                                                getString(
                                                    R.string.status_download_item_error,
                                                    GemError.getMessage(errorCode, this),
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Util.postOnMain {
                            displayList(models)
                        }
                    }
                }

                else -> {
                    // There was a problem at retrieving the content store items.
                    showStatusMessage(
                        getString(
                            R.string.status_maps_catalog_download_error,
                            GemError.getMessage(errorCode, this),
                        ),
                    )
                    EspressoIdlingResource.decrement()
                }
            }
        },
    )

    private fun loadMapsCatalog() = SdkCall.execute {
        // Call to the content store to asynchronously retrieve the list of maps.
        val error = contentStore.asyncGetStoreContentList(EContentType.RoadMap, progressListener)
        if (error != GemError.NoError) {
            // There was a problem at starting the content store retrieval action.
            Util.postOnMain {
                showStatusMessage(
                    getString(
                        R.string.status_maps_catalog_download_error,
                        GemError.getMessage(error, this),
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
        EspressoIdlingResource.increment()

        binding.listView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            val separator = DividerItemDecoration(
                applicationContext,
                (layoutManager as LinearLayoutManager).orientation,
            )
            addItemDecoration(separator)

            val lateralPadding = resources.getDimension(R.dimen.bigPadding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)

            itemAnimator = null
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                showStatusMessage(getString(R.string.status_checking_token_validity))
                SdkSettings.appAuthorization?.let {
                    SdkCall.execute {
                        SdkSettings.verifyAppAuthorization(it, checkAuthorizationListener)
                    }
                } ?: run {
                    showInvalidTokenDialog()
                }

                SdkSettings.onWorldwideRoadMapSupportStatus = {}
            }
        }

        SdkSettings.onApiTokenRejected = {
            showInvalidTokenDialog()
        }

        // This step of initialization is mandatory if you want to use the SDK without a map.
        val errorCode = GemSdk.initSdkWithDefaults(this)
        if (errorCode != GemError.NoError) {
            // The SDK initialization failed, we can't continue.
            showDialog(
                getString(
                    R.string.dialog_sdk_initialization_error,
                    GemError.getMessage(errorCode, this),
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

        // Release the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun displayList(models: ArrayList<ContentStoreItem>?) {
        if (models != null) {
            val adapter = CustomAdapter(models)
            binding.listView.adapter = adapter
        }
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

    private fun showInvalidTokenDialog() {
        showDialog(
            getString(R.string.invalid_token),
        ) {
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
                        progressBar.progress =
                            SdkCall.execute { dataSet[position].downloadProgress } ?: 0
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
                        flagBitmapsMap[isoCode] =
                            MapDetails().getCountryFlag(isoCode)?.asBitmap(size, size)
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
    private const val IDLING_RESOURCE_NAME = "DownloadingOnboardMapIdleRes"
    val espressoIdlingResource = CountingIdlingResource(IDLING_RESOURCE_NAME)

    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) {
        espressoIdlingResource.decrement()
    } else {
    }
}
//endregion
