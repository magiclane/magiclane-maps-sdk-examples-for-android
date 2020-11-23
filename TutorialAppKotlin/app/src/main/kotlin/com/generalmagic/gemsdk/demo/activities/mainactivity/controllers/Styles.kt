/*
* Copyright (C) 2019-2020, General Magic B.V.
* All rights reserved.
*
* This software is confidential and proprietary information of General Magic
* ("Confidential Information"). You shall not disclose such Confidential
* Information and shall use it only in accordance with the terms of the
* license agreement you entered into with General Magic.
*/

package com.generalmagic.gemsdk.demo.activities.mainactivity.controllers

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.generalmagic.gemsdk.ContentStore
import com.generalmagic.gemsdk.GEMSdk
import com.generalmagic.gemsdk.ProgressListener
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.activities.*
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.app.Tutorials
import com.generalmagic.gemsdk.models.ContentStoreItem
import com.generalmagic.gemsdk.models.TContentStoreItemStatus
import com.generalmagic.gemsdk.models.TContentType
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.activity_list_view.*

class StylesActivity : StylesListActivity() {
    private val contentStore = ContentStore()

    private val progressListener = object : ProgressListener() {
        override fun notifyComplete(reason: Int, hint: String) {
            GEMApplication.postOnMain { hideProgress() }

            val gemError = GEMError.fromInt(reason)
            if (gemError != GEMError.KNoError) {
                // show error
                return
            }

            // get call result
            val result = ContentStore().getStoreContentList(TContentType.ECT_ViewStyleHighRes.value)
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

        GEMSdkCall.execute {
            val localItems =
                contentStore.getLocalContentList(TContentType.ECT_ViewStyleHighRes.value)
                    ?: ArrayList()

            contentStore.asyncGetStoreContentList(
                TContentType.ECT_ViewStyleHighRes.value, progressListener
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
        val styleId = GEMSdkCall.execute {
            GEMApplication.getMainMapView()?.preferences()?.getMapStyleId()
        }

        val result = ArrayList<StylesListItem>()
        for (item in list) {
            val wrapped = StyleItemViewModel(item)
            wrapped.m_checked = GEMSdkCall.execute { item.getId() } == styleId
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
            GEMSdkCall.execute {
                GEMApplication.getMainMapView()?.preferences()?.setMapStyleById(item.getId())
            }
            GEMApplication.postOnMain { refreshChecked() }
        }

        when (GEMSdkCall.execute { item.getStatus() }) {
            TContentStoreItemStatus.ECIS_Completed -> {
                selectStyle()
                finish()
            }
            TContentStoreItemStatus.ECIS_Paused, TContentStoreItemStatus.ECIS_Unavailable -> {
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

                    override fun notifyComplete(reason: Int, hint: String) {
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

                GEMSdkCall.execute {
                    item.asyncDownload(listener, GEMSdk.TDataSavePolicy.EUseDefault, true)
                }
            }
            TContentStoreItemStatus.ECIS_DownloadRunning -> {
                GEMSdkCall.execute { item.pauseDownload() }
            }
            TContentStoreItemStatus.ECIS_DownloadWaitingFreeNetwork -> {
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
                GEMSdkCall.execute { item.deleteContent() }
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
        val styleId = GEMSdkCall.execute {
            GEMApplication.getMainMapView()?.preferences()?.getMapStyleId()
        } ?: return

        var changed = false
        for (sectionIndex in 0 until adapter.sectionCount) {
            val section = adapter.getSection(sectionIndex) as StylesViewHolder.Chapter<*>

            for (item in section.nItems) {
                val casted = item as StyleItemViewModel? ?: continue
                val oldChecked = casted.m_checked
                casted.m_checked = GEMSdkCall.execute { casted.it.getId() } == styleId

                if (oldChecked != casted.m_checked) {
                    changed = true
                }
            }
        }

        if (changed) {
            adapter.notifyDataSetChanged()
        }
    }
}
