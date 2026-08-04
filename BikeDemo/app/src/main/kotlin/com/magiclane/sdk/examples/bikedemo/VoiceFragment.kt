/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemo

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.content.ContentStoreItem
import com.magiclane.sdk.content.EContentStoreItemStatus
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.EVoiceType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.examples.bikedemo.databinding.FragmentVoiceBinding
import com.magiclane.sdk.examples.bikedemo.databinding.VoiceListItemBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util

/**
 * Lists the voices available for navigation / simulation instructions: the Text-to-Speech
 * engine (when it initialized successfully) followed by the human voices catalog from the
 * content store. Tapping a downloaded voice applies it to the SDK; tapping a voice that is
 * not on the device downloads it first.
 */
class VoiceFragment : Fragment() {

    companion object {
        private const val BYTES_PER_MEGABYTE = 1_048_576.0
    }

    private sealed class VoiceRow {
        object Tts : VoiceRow()
        data class Human(val item: ContentStoreItem) : VoiceRow()
    }

    private val viewModel: MainActivityViewModel by activityViewModels()

    private var mBinding: FragmentVoiceBinding? = null
    private val binding
        get() = mBinding!!

    // Flag bitmap cache keyed by ISO country code — avoids re-fetching on every list bind.
    private val flagBitmapsMap = HashMap<String, Bitmap?>()

    private val voicesAdapter = VoicesAdapter()
    private val rows = mutableListOf<VoiceRow>()

    // Country ISO code and description of the Text-to-Speech entry, resolved once.
    private val ttsCountryCode = MainActivityViewModel.TTS_LANGUAGE.substringAfter('-')
    private var ttsDescription = ""

    // Tracks async retrieval of the voices catalog from the content store.
    private val catalogListener = ProgressListener.create(
        onStarted = {
            mBinding?.progressBar?.visibility = View.VISIBLE
        },
        onCompleted = { errorCode, _ ->
            mBinding?.progressBar?.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> SdkCall.execute {
                    val voicesList = viewModel.contentStore.getStoreContentList(EContentType.HumanVoice)?.first
                    Util.postOnMain {
                        viewModel.voicesLivedata.value = voicesList ?: emptyList()
                    }
                }
                else -> if (isAdded) {
                    showToast(
                        getString(
                            R.string.status_voices_catalog_download_error,
                            SdkCall.runSynced { GemError.getMessage(errorCode, requireContext()) },
                        ),
                    )
                }
            }
        },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mBinding = DataBindingUtil.inflate(layoutInflater, R.layout.fragment_voice, container, false)
        binding.apply {
            voicesList.apply {
                adapter = voicesAdapter
                layoutManager = LinearLayoutManager(requireContext())
                addItemDecoration(DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL))
                itemAnimator = null
            }
            voiceToolbar.setNavigationOnClickListener {
                requireActivity().supportFragmentManager.popBackStack()
            }
        }

        resolveTtsDescription()
        rebuildRows(viewModel.voicesLivedata.value.orEmpty())

        viewModel.voicesLivedata.observe(viewLifecycleOwner) { voices ->
            voices?.let { rebuildRows(it) }
        }

        // Refresh the checkmarks whenever the applied voice changes.
        viewModel.currentVoice.observe(viewLifecycleOwner) {
            voicesAdapter.notifyDataSetChanged()
        }

        viewModel.refreshCurrentVoice()
        loadVoicesCatalog()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mBinding = null
    }

    private fun rebuildRows(voices: List<ContentStoreItem>) {
        rows.clear()
        if (SoundPlayingService.ttsPlayerIsInitialized) rows.add(VoiceRow.Tts)
        voices.forEach { rows.add(VoiceRow.Human(it)) }
        voicesAdapter.notifyDataSetChanged()
    }

    private fun resolveTtsDescription() {
        ttsDescription = SdkCall.execute {
            val languageCode = MainActivityViewModel.TTS_LANGUAGE.substringBefore('-')
            val ttsVoice = SdkSettings.getBestVoiceMatch(languageCode, ttsCountryCode)
                ?.firstOrNull { it.type == EVoiceType.Computer }
            val country = MapDetails().getCountryName(ttsCountryCode) ?: ""
            val language = ttsVoice?.language?.name ?: ""
            listOf(country, language).filter { it.isNotEmpty() }.joinToString(" - ")
        } ?: ""
    }

    private fun loadVoicesCatalog() {
        // The catalog is cached in the view model for the whole session.
        if (viewModel.voicesLivedata.value != null) return

        SdkCall.execute {
            val error = viewModel.contentStore.asyncGetStoreContentList(EContentType.HumanVoice, catalogListener)
            if (error != GemError.NoError) {
                // asyncGetStoreContentList can fail immediately (e.g. no network) before the listener fires.
                Util.postOnMain {
                    if (!isAdded) return@postOnMain
                    showToast(
                        getString(
                            R.string.status_voices_catalog_download_error,
                            SdkCall.runSynced { GemError.getMessage(error, requireContext()) },
                        ),
                    )
                }
            }
        }
    }

    private fun onRowClicked(row: VoiceRow, position: Int) {
        when (row) {
            is VoiceRow.Tts -> {
                viewModel.selectTtsVoice()
                close()
            }
            is VoiceRow.Human -> when (SdkCall.execute { row.item.status }) {
                // Already on the device: apply it right away.
                EContentStoreItemStatus.Completed -> {
                    viewModel.selectHumanVoice(row.item)
                    close()
                }
                EContentStoreItemStatus.DownloadRunning -> { /* download already in progress */ }
                else -> downloadVoice(row.item, position)
            }
        }
    }

    /** Returns to the settings view once a voice has been applied. */
    private fun close() {
        requireActivity().supportFragmentManager.popBackStack()
    }

    // Must be called from outside SdkCall (wraps itself). Kicks off an async download for
    // voiceItem and handles the immediate result: UpToDate (already downloaded) or error-on-start.
    private fun downloadVoice(voiceItem: ContentStoreItem, position: Int) = SdkCall.execute {
        val itemName = voiceItem.name ?: ""

        // Progress callbacks run on the main thread; no explicit posting needed inside them.
        val downloadProgressListener = ProgressListener.create(
            onStarted = {
                mBinding?.voicesList?.adapter?.notifyItemChanged(position)
            },
            onProgress = {
                mBinding?.voicesList?.adapter?.notifyItemChanged(position)
            },
            onCompleted = { errorCode, _ ->
                mBinding?.voicesList?.adapter?.notifyItemChanged(position)
                if (!isAdded) return@create
                if (errorCode == GemError.NoError) {
                    showToast(getString(R.string.status_item_downloaded, itemName))
                } else {
                    showToast(
                        getString(
                            R.string.status_item_download_error,
                            itemName,
                            SdkCall.runSynced { GemError.getMessage(errorCode, requireContext()) },
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
                if (!isAdded) return@postOnMain
                showToast(getString(R.string.status_item_already_downloaded, itemName))
                mBinding?.voicesList?.adapter?.notifyItemChanged(position)
            }
            else -> Util.postOnMain {
                if (!isAdded) return@postOnMain
                showToast(
                    getString(
                        R.string.status_download_item_error,
                        SdkCall.runSynced { GemError.getMessage(downloadError, requireContext()) },
                    ),
                )
            }
        }
    }

    private fun showToast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    private fun getFlagBitmap(isoCode: String?): Bitmap? {
        if (isoCode.isNullOrEmpty()) return null
        if (!flagBitmapsMap.containsKey(isoCode)) {
            val size = resources.getDimension(R.dimen.voice_icon_size).toInt()
            flagBitmapsMap[isoCode] = SdkCall.execute { MapDetails().getCountryFlag(isoCode)?.asBitmap(size, size) }
        }
        return flagBitmapsMap[isoCode]
    }

    inner class VoicesAdapter : RecyclerView.Adapter<VoicesAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: VoiceListItemBinding) : RecyclerView.ViewHolder(binding.root) {
            init {
                binding.root.setOnClickListener {
                    @Suppress("DEPRECATION")
                    val position = adapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                    onRowClicked(rows[position], position)
                }
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = VoiceListItemBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is VoiceRow.Tts -> bindTts(viewHolder.binding)
                is VoiceRow.Human -> bindHuman(viewHolder.binding, row.item)
            }
        }

        override fun getItemCount() = rows.size

        private fun bindTts(binding: VoiceListItemBinding) {
            binding.apply {
                text.text = getString(R.string.text_to_speech)
                description.text = ttsDescription
                icon.setImageBitmap(getFlagBitmap(ttsCountryCode))
                genderIcon.setImageResource(R.drawable.robot_green)
                itemProgressBar.visibility = View.INVISIBLE
                statusIcon.visibility = View.GONE
                checkIcon.visibility =
                    if (viewModel.currentVoice.value?.isTts == true) View.VISIBLE else View.GONE
            }
        }

        private fun bindHuman(binding: VoiceListItemBinding, item: ContentStoreItem) {
            binding.apply {
                text.text = SdkCall.execute {
                    "${item.name} (${"%.1f MB".format(item.totalSize / BYTES_PER_MEGABYTE)})"
                }
                description.text = SdkCall.execute {
                    "${getCountryName(item)} - ${getParameter(item, "native_language")}"
                }
                icon.setImageBitmap(getFlagBitmap(SdkCall.execute { item.countryCodes?.firstOrNull() }))
                genderIcon.setImageResource(
                    if (SdkCall.execute { getParameter(item, "gender").lowercase() } == "male") {
                        R.drawable.male
                    } else {
                        R.drawable.female
                    },
                )

                val isSelected = viewModel.currentVoice.value?.let { current ->
                    !current.isTts &&
                        current.filename.isNotEmpty() &&
                        current.filename == SdkCall.execute { item.fileName }
                } == true

                checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
                statusIcon.visibility = View.GONE
                itemProgressBar.visibility = View.INVISIBLE
                when (SdkCall.execute { item.status }) {
                    // Downloaded but not selected: show the on-device indicator.
                    EContentStoreItemStatus.Completed -> {
                        statusIcon.visibility = if (isSelected) View.GONE else View.VISIBLE
                    }
                    EContentStoreItemStatus.DownloadRunning -> {
                        itemProgressBar.visibility = View.VISIBLE
                        itemProgressBar.progress = SdkCall.execute { item.downloadProgress } ?: 0
                    }
                    else -> { /* not on the device yet: tap to download */ }
                }
            }
        }

        private fun getCountryName(item: ContentStoreItem): String =
            item.countryCodes?.firstOrNull()?.let { MapDetails().getCountryName(it) } ?: ""

        private fun getParameter(item: ContentStoreItem, parameter: String): String = item.contentParameters
            ?.firstOrNull { it.name?.equals(parameter, ignoreCase = true) == true }
            ?.valueString
            ?: ""
    }
}
