/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.setttslanguage

import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.TTSLanguage
import com.magiclane.sdk.examples.setttslanguage.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.setttslanguage.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.setttslanguage.databinding.DialogListBinding
import com.magiclane.sdk.examples.setttslanguage.databinding.ListItemBinding
import com.magiclane.sdk.util.EStringIds
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    private lateinit var binding: ActivityMainBinding
    private var selectedLanguageIndex = 0
    private var ttsLanguages = ArrayList<TTSLanguage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status bar icons light so they stay visible against the dark toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        // Kept open until TTS languages finish loading (decremented in onTTSLanguagesLoaded).
        EspressoIdlingResource.increment()

        binding.languageButton.setOnClickListener {
            onLanguageButtonClicked()
        }

        binding.playButton.setOnClickListener {
            SdkCall.execute {
                SoundPlayingService.playText(
                    GemUtil.getTTSString(EStringIds.eStrMindYourSpeed),
                    object : SoundPlayingListener() {
                        override fun notifyComplete(errorCode: Int, hint: String) {
                            EspressoIdlingResource.increment()
                            super.notifyComplete(errorCode, hint)
                        }
                    },
                    SoundPlayingPreferences(),
                )
            }
        }

        // Listeners must be registered before initSdkWithDefaults so no callbacks are missed
        // if they fire synchronously during initialization.
        registerSdkListeners()

        // This step of initialization is mandatory if you want to use the SDK without a map.
        val sdkInitError = GemSdk.initSdkWithDefaults(this)
        if (sdkInitError != GemError.NoError) {
            showDialog(
                getString(
                    R.string.sdk_initialization_failed,
                    SdkCall.runSynced {
                        GemError.getMessage(sdkInitError, this)
                    },
                ),
            ) { finish() }
        } else {
            if (SdkCall.runSynced { SoundPlayingService.ttsPlayerIsInitialized } == true) {
                loadTTSLanguages()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkListeners()

        // Release the SDK.
        GemSdk.release()
    }

    private fun registerSdkListeners() {
        // Covers the case where TTS initializes after the SDK but before loadTTSLanguages is called.
        SoundUtils.addTTSPlayerInitializationListener(this)

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        SoundUtils.removeTTSPlayerInitializationListener(this)
        SdkSettings.onApiTokenRejected = {}
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
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false // prevent accidental swipe-to-dismiss
            setCancelable(false) // require explicit OK tap
            setContentView(dialogBinding.root)
            show()
        }
    }

    override fun onTTSPlayerInitialized() {
        loadTTSLanguages()
    }

    override fun onTTSPlayerInitializationFailed() {
        runOnAliveUi {
            showDialog(getString(R.string.tts_player_initialization_failed))
        }
    }

    private fun loadTTSLanguages() {
        EspressoIdlingResource.increment()
        SdkCall.execute {
            ttsLanguages = SoundPlayingService.getTTSLanguages()
        }

        runOnUiThread { onTTSLanguagesLoaded() }
    }

    private fun onTTSLanguagesLoaded() {
        binding.apply {
            if (ttsLanguages.isNotEmpty()) {
                languageValue.text = ttsLanguages[selectedLanguageIndex].name
                // Apply the default language immediately so playback works before the user makes a selection.
                SoundPlayingService.setTTSLanguage(ttsLanguages[selectedLanguageIndex].code)
                languageContainer.isVisible = true
                playButton.isVisible = true
            } else {
                showDialog(getString(R.string.no_tts_languages_available))
            }
            progressBar.isVisible = false
            EspressoIdlingResource.decrement()
        }
    }

    private fun onLanguageButtonClicked() {
        EspressoIdlingResource.increment()
        val builder = AlertDialog.Builder(this)

        val dialogListBinding = DialogListBinding.inflate(layoutInflater)
        dialogListBinding.listView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            addItemDecoration(
                DividerItemDecoration(applicationContext, DividerItemDecoration.VERTICAL).also { decoration ->
                    ContextCompat.getDrawable(applicationContext, R.drawable.list_divider)?.let(decoration::setDrawable)
                },
            )

            // Override the default white background of the AlertDialog content view.
            setBackgroundResource(R.color.background)

            val lateralPadding = resources.getDimensionPixelSize(R.dimen.big_padding)
            setPadding(lateralPadding, 0, lateralPadding, 0)
        }

        val adapter = CustomAdapter(selectedLanguageIndex, ttsLanguages)
        dialogListBinding.listView.adapter = adapter

        builder.setView(dialogListBinding.root)

        val dialog = builder.create()
        dialog.setOnShowListener {
            EspressoIdlingResource.decrement()
        }
        dialog.show()
        // Pass the dialog reference so each item click can dismiss it.
        adapter.dialog = dialog
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain {
            if (isActivityAlive()) block()
        }
    }

    private fun isActivityAlive() = !isFinishing && !isDestroyed

    inner class CustomAdapter(
        private val selectedIndex: Int,
        private val dataSet: ArrayList<TTSLanguage>,
    ) : RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        var dialog: AlertDialog? = null

        inner class ViewHolder(private val itemBinding: ListItemBinding) : RecyclerView.ViewHolder(itemBinding.root) {
            fun bind(position: Int) {
                itemBinding.radioButton.isChecked = position == selectedIndex
                itemBinding.text.text = dataSet[position].name
                itemBinding.statusText.text = dataSet[position].code

                itemBinding.root.setOnClickListener {
                    selectedLanguageIndex = position
                    SoundPlayingService.setTTSLanguage(dataSet[position].code)
                    binding.languageValue.text = dataSet[position].name
                    dialog?.dismiss()
                }
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ListItemBinding.inflate(layoutInflater, viewGroup, false))
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            viewHolder.bind(position)
        }

        override fun getItemCount() = dataSet.size
    }
}

@VisibleForTesting
object EspressoIdlingResource {
    const val RESOURCE_NAME = "SetTTsLanguageIdlingResource"
    val espressoIdlingResource = CountingIdlingResource(RESOURCE_NAME)
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
