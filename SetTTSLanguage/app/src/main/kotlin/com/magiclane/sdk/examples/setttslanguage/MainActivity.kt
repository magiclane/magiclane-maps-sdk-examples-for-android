// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
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
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private lateinit var progressBar: ProgressBar
    private lateinit var selectedLanguageTextView: TextView
    private lateinit var languageContainer: LinearLayout
    private lateinit var playButton: Button
    
    private var selectedLanguageIndex = 0
    private var ttsLanguages = ArrayList<TTSLanguage>()

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        selectedLanguageTextView = findViewById(R.id.language_value)
        languageContainer = findViewById(R.id.language_container)
        playButton = findViewById(R.id.play_button)
        
        languageContainer.setOnClickListener { 
            onLanguageContainerClicked()
        }
        
        playButton.setOnClickListener {
            SdkCall.execute {
                SoundPlayingService.playText(GemUtil.getTTSString(EStringIds.eStrMindYourSpeed), SoundPlayingListener(), SoundPlayingPreferences())
            }
        }

        /// MAGIC LANE
        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            val ttsPlayerIsInitialized = SdkCall.execute { SoundPlayingService.ttsPlayerIsInitialized } ?: false

            if (!ttsPlayerIsInitialized)
            {
                SoundUtils.addTTSPlayerInitializationListener(this)
            }
            else
            {
                loadTTSLanguages()
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

    override fun onBackPressed()
    {
        finish()
        exitProcess(0)
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
        progressBar.visibility = View.GONE
        languageContainer.visibility = View.VISIBLE
        playButton.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------------------------------------------------------------------
    
    private fun onLanguageContainerClicked()
    {
        val builder = AlertDialog.Builder(this)

        val convertView = layoutInflater.inflate(R.layout.dialog_list, null)
        val listView = convertView.findViewById<RecyclerView>(R.id.list_view).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            
            addItemDecoration(DividerItemDecoration(applicationContext, (layoutManager as LinearLayoutManager).orientation))

            setBackgroundResource(R.color.background_color)
            
            val lateralPadding = resources.getDimension(R.dimen.big_padding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)
        }

        val adapter = CustomAdapter(selectedLanguageIndex, ttsLanguages)
        listView.adapter = adapter

        builder.setView(convertView)

        val dialog = builder.create()
        dialog.show()

        adapter.dialog = dialog
    }
    
    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * This custom adapter is made to facilitate the displaying of the data from the model
     * and to decide how it is displayed.
     */
    inner class CustomAdapter(private val selectedIndex: Int, private val dataSet: ArrayList<TTSLanguage>): RecyclerView.Adapter<CustomAdapter.ViewHolder>()
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
            val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_item, viewGroup, false)

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
