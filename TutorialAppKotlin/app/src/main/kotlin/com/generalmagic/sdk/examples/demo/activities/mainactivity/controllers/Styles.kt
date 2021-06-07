/*
* Copyright (C) 2019-2021, General Magic B.V.
* All rights reserved.
*
* This software is confidential and proprietary information of General Magic
* ("Confidential Information"). You shall not disclose such Confidential
* Information and shall use it only in accordance with the terms of the
* license agreement you entered into with General Magic.
*/

package com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.ContentStoreItem
import com.generalmagic.sdk.content.EContentStoreItemStatus
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.activities.*
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.app.Tutorials
import com.generalmagic.sdk.util.SdkCall
import kotlinx.android.synthetic.main.activity_list_view.*

class StylesActivity : StylesListActivity() {
    private val contentStore = ContentStore()

    private val progressListener = object : ProgressListener() {
        override fun notifyComplete(reason: SdkError, hint: String) {
            GEMApplication.postOnMain { hideProgress() }

            if (reason != SdkError.NoError) {
                // show error
                return
            }

            // get call result
            val result = ContentStore().getStoreContentList(EContentType.ViewStyleHighRes.value)
            result ?: return

            GEMApplication.postOnMain {
                updateList(result.first)
            }
        }

        override fun notifyStart(hasProgress: Boolean) {
            GEMApplication.postOnMain { showProgress() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SdkCall.execute {
            val localItems =
                contentStore.getLocalContentList(EContentType.ViewStyleHighRes.value)
                    ?: ArrayList()

            contentStore.asyncGetStoreContentList(
                EContentType.ViewStyleHighRes.value, progressListener
            )

            GEMApplication.postOnMain {
                updateList(localItems)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        Tutorials.openHelloWorldTutorial()
    }

    private fun wrapList(list: ArrayList<ContentStoreItem>): ArrayList<StylesListItem> {
        val styleId = SdkCall.execute {
            GEMApplication.getMainMapView()?.preferences()?.getMapStyleId()
        }

        val result = ArrayList<StylesListItem>()
        for (item in list) {
            val wrapped = StyleItemViewModel(item)
            wrapped.mChecked = SdkCall.execute { item.getId() } == styleId
            wrapped.mOnClick = { onItemTapped(wrapped, it) }
            result.add(wrapped)
        }
        return result
    }

    private fun updateList(storeItems: ArrayList<ContentStoreItem>) {
        val adapter = ChapterStylesAdapter(wrapList(storeItems))
        adapter.onStatusIconTapped = { _: Int, it, holder -> onItemTapped(it, holder) }
        adapter.onDeleteIconTapped = { _: Int, it, holder ->
            onDeleteTapped(it, holder)
        }
        list_view.adapter = adapter
    }

    private fun onItemTapped(itUncasted: BaseListItem, holder: StylesViewHolder) {
        val item = (itUncasted as StyleItemViewModel).it

        val selectStyle = {
            SdkCall.execute {
                GEMApplication.getMainMapView()?.preferences()?.setMapStyleById(item.getId())
            }
            GEMApplication.postOnMain { refreshChecked() }
        }

        when (SdkCall.execute { item.getStatus() }) {
            EContentStoreItemStatus.Completed -> {
                selectStyle()
                finish()
            }
            EContentStoreItemStatus.Paused, EContentStoreItemStatus.Unavailable -> {
                val taskRefresh = {
                    GEMApplication.postOnMain {
                        holder.updateViews(itUncasted)
                    }
                }

                val listener = object : ProgressListener() {
                    override fun notifyProgress(progress: Int) {
                        GEMApplication.postOnMain {
                            if (holder.statusProgress.visibility == View.GONE) {
// 								holder.refreshItemStatus(item)
                                holder.statusProgress.visibility = View.VISIBLE
                            }
                            holder.statusProgress.progress = progress
                        }
                    }

                    override fun notifyComplete(reason: SdkError, hint: String) {
                        taskRefresh()
                        selectStyle()
                    }

                    override fun notifyStart(hasProgress: Boolean) {
                        taskRefresh()
                    }

                    override fun notifyStatusChanged(status: Int) {
                        taskRefresh()
                    }
                }

                SdkCall.execute {
                    item.asyncDownload(listener, GemSdk.EDataSavePolicy.UseDefault, true)
                }
            }
            EContentStoreItemStatus.DownloadRunning -> {
                SdkCall.execute { item.pauseDownload() }
            }
            EContentStoreItemStatus.DownloadWaitingFreeNetwork -> {
                // ask for user permission to download map over mobile network
            }
            else -> { /* NOTHING */
            }
        }
    }

    private fun onDeleteTapped(itUncasted: BaseListItem, holder: StylesViewHolder) {
        val item = (itUncasted as StyleItemViewModel).it

        val builder = AlertDialog.Builder(this)
        builder.setMessage("Are you sure you want to delete ${itUncasted.getText()}?")
            .setCancelable(true)
            .setPositiveButton(getText(R.string.yes)) { dialog, _ ->
                dialog.dismiss()
                SdkCall.execute { item.deleteContent() }
                holder.updateViews(itUncasted as StylesListItem)
            }
            .setNegativeButton(R.string.no) { dialog, _ ->
                dialog.dismiss()
            }

        val alert = builder.create()
        val color = ContextCompat.getColor(this, R.color.colorPrimary)
        alert.show()
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
        alert.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
    }

    private fun refreshChecked() {
        val adapter = list_view.adapter as ChapterStylesAdapter? ?: return
        val styleId = SdkCall.execute {
            GEMApplication.getMainMapView()?.preferences()?.getMapStyleId()
        } ?: return

        var changed = false
        for (sectionIndex in 0 until adapter.sectionCount) {
            val section = adapter.getSection(sectionIndex) as StylesViewHolder.Chapter<*>

            for (item in section.nItems) {
                val casted = item as StyleItemViewModel? ?: continue
                val oldChecked = casted.mChecked
                casted.mChecked = SdkCall.execute { casted.it.getId() } == styleId

                if (oldChecked != casted.mChecked) {
                    changed = true
                }
            }
        }

        if (changed) {
            adapter.notifyDataSetChanged()
        }
    }
}
