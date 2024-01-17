// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.mapselection

// -------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar

// -------------------------------------------------------------------------------------------------

class WebActivity : AppCompatActivity()
{
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)
        
        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        webView.webViewClient = object : WebViewClient()
        {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?)
            {
                super.onPageStarted(view, url, favicon)
                view?.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?)
            {
                super.onPageFinished(view, url)
                view?.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
                
                supportActionBar?.title = webView.title
            }
        }
        
        webView.apply { 
            webChromeClient = WebChromeClient()
            
            settings.run { 
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                builtInZoomControls = true
                domStorageEnabled = true
                builtInZoomControls = false
                allowFileAccess = true
                useWideViewPort = false
            }
            
            val url = intent.getStringExtra("url")
            url?.let { loadUrl(it) }
        }
    }

    override fun onSupportNavigateUp(): Boolean
    {
        onBackPressed()
        return true
    }
}

// -------------------------------------------------------------------------------------------------
