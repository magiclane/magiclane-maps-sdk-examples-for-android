// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.setttslanguage

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.TTSLanguage
import com.magiclane.sdk.util.EStringIds
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener
{
    // ---------------------------------------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    private lateinit var progressBar: ProgressBar
    private lateinit var selectedLanguageTextView: TextView
    private lateinit var languageButton: Button
    private lateinit var playButton: Button
    private lateinit var languageContainer: LinearLayout

    private var selectedLanguageIndex = 0
    private var ttsLanguages = ArrayList<TTSLanguage>()

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        selectedLanguageTextView = findViewById(R.id.language_value)
        languageButton = findViewById(R.id.choose_language_button)
        playButton = findViewById(R.id.play_button)
        languageContainer = findViewById(R.id.language_container)

        EspressoIdlingResource.increment()
        languageButton.setOnClickListener {
            onLanguageButtonClicked()
        }

        playButton.setOnClickListener {
            SdkCall.execute {
                SoundPlayingService.playText(
                    GemUtil.getTTSString(EStringIds.eStrMindYourSpeed),
                    /* SoundPlayingListener()*/
                    object : SoundPlayingListener()
                    {
                        override fun notifyComplete(errorCode: Int, hint: String)
                        {
                            EspressoIdlingResource.increment()
                            super.notifyComplete(errorCode, hint)
                        }
                    },
                    SoundPlayingPreferences()
                )
            }
        }

        /// MAGIC LANE
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            val ttsPlayerIsInitialized =
                SdkCall.execute { SoundPlayingService.ttsPlayerIsInitialized } ?: false

            if (!ttsPlayerIsInitialized)
            {
                SoundUtils.addTTSPlayerInitializationListener(this)
            } else
            {
                loadTTSLanguages()
            }
            EspressoIdlingResource.decrement()
        }

        SdkSettings.onApiTokenRejected = {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED")
        }

        // This step of initialization is mandatory if you want to use the SDK without a map.
        if (!GemSdk.initSdkWithDefaults(this))
        {
            // The SDK initialization was not completed.
            finish()
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

        SoundUtils.removeTTSPlayerInitializationListener(this)

        // Release the SDK.
        GemSdk.release()
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

    override fun onTTSPlayerInitialized()
    {
        loadTTSLanguages()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun loadTTSLanguages()
    {
        EspressoIdlingResource.increment()
        SdkCall.execute {
            ttsLanguages = SoundPlayingService.getTTSLanguages()
        }

        runOnUiThread {
            onTTSLanguagesLoaded()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun onTTSLanguagesLoaded()
    {
        selectedLanguageTextView.text = ttsLanguages[selectedLanguageIndex].name
        SoundPlayingService.setTTSLanguage(ttsLanguages[selectedLanguageIndex].code)
        progressBar.isVisible = false
        languageContainer.isVisible = true
        playButton.isVisible = true
        EspressoIdlingResource.decrement()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun onLanguageButtonClicked()
    {
        EspressoIdlingResource.increment()
        val builder = AlertDialog.Builder(this)

        val convertView = layoutInflater.inflate(R.layout.dialog_list, null)
        val listView = convertView.findViewById<RecyclerView>(R.id.list_view).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            addItemDecoration(
                DividerItemDecoration(
                    applicationContext,
                    (layoutManager as LinearLayoutManager).orientation
                )
            )

            setBackgroundResource(R.color.background_color)

            val lateralPadding = resources.getDimension(R.dimen.big_padding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)
        }

        val adapter = CustomAdapter(selectedLanguageIndex, ttsLanguages)
        listView.adapter = adapter

        builder.setView(convertView)

        val dialog = builder.create()
        dialog.setOnShowListener {
            EspressoIdlingResource.decrement()
        }
        dialog.show()
        adapter.dialog = dialog
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * This custom adapter is made to facilitate the displaying of the data from the model
     * and to decide how it is displayed.
     */
    inner class CustomAdapter(
        private val selectedIndex: Int,
        private val dataSet: ArrayList<TTSLanguage>
    ) : RecyclerView.Adapter<CustomAdapter.ViewHolder>()
    {
        // -----------------------------------------------------------------------------------------------------------------------

        var dialog: AlertDialog? = null

        // -----------------------------------------------------------------------------------------------------------------------

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
        {
            private val text: TextView = view.findViewById(R.id.text)
            private val status: TextView = view.findViewById(R.id.status_text)
            private val radioButton: RadioButton = view.findViewById(R.id.radioButton)

            fun bind(position: Int)
            {
                radioButton.isChecked = position == selectedIndex
                text.text = dataSet[position].name
                status.text = dataSet[position].code

                itemView.setOnClickListener {
                    selectedLanguageIndex = position
                    SoundPlayingService.setTTSLanguage(dataSet[position].code)
                    selectedLanguageTextView.text = dataSet[position].name
                    dialog?.dismiss()
                }
            }
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder
        {
            val view =
                LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item, viewGroup, false)

            return ViewHolder(view)
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int)
        {
            viewHolder.bind(position)
        }

        // -----------------------------------------------------------------------------------------------------------------------

        override fun getItemCount() = dataSet.size

        // -----------------------------------------------------------------------------------------------------------------------
    }
    // ---------------------------------------------------------------------------------------------------------------------------
}

@VisibleForTesting
object EspressoIdlingResource
{
    const val resourceName = "SetTTsLanguageIdlingResource"
    val espressoIdlingResource = CountingIdlingResource(resourceName)
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else
    {
    }
}
// ---------------------------------------------------------------------------------------------------------------------------
