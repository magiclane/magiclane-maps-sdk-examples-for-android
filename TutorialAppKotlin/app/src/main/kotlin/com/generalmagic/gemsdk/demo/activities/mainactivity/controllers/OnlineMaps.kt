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
import android.widget.PopupMenu
import com.generalmagic.gemsdk.ContentStore
import com.generalmagic.gemsdk.GEMSdk
import com.generalmagic.gemsdk.ProgressListener
import com.generalmagic.gemsdk.demo.activities.*
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.models.ContentStoreItem
import com.generalmagic.gemsdk.models.TContentStoreItemStatus
import com.generalmagic.gemsdk.models.TContentType
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.activity_list_view.*
import java.util.*
import kotlin.collections.ArrayList

class OnlineMapsActivity : MapsListActivity() {
    private val contentStore = ContentStore()
    private var models = ArrayList<ContentStoreItem>()

    private val progressListener = object : ProgressListener() {
        override fun notifyComplete(reason: Int, hint: String) {
            models.clear()

            // get call result
            val result = ContentStore().getStoreContentList(TContentType.ECT_RoadMap.value)
            if (result != null) models = result.first

            GEMApplication.postOnMain {
                hideProgress()
                displayList(models)
            }
        }

        override fun notifyStart(hasProgress: Boolean) {
            GEMApplication.postOnMain { showProgress() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GEMSdkCall.execute {
            contentStore.asyncGetStoreContentList(TContentType.ECT_RoadMap.value, progressListener)
        }
    }

    private fun toChapters(storeItems: ArrayList<ContentStoreItem>): ArrayList<ArrayList<ContentStoreItem>> {
        val chapters = arrayListOf<ArrayList<ContentStoreItem>>()
        chapters.add(ArrayList())

        var lastName = ""
        var sameInRow = false
        for (i in 0 until storeItems.size) {
            val crtName = GEMSdkCall.execute { storeItems[i].getChapterName() } ?: ""

            if (crtName == lastName) {
                if (!sameInRow) {
                    if (chapters.last().size > 1) { // avoid leaving empty chapter if pop last
                        // pop last because is the same with current
                        val lastItem = chapters.last().last()
                        chapters.last().remove(lastItem)

                        // add new list with last
                        chapters.add(arrayListOf(lastItem))
                    }
                    sameInRow = true
                }
            } else {
                if (sameInRow) {
                    chapters.add(ArrayList())
                    sameInRow = false
                }
            }

            chapters.last().add(storeItems[i])
            lastName = crtName
        }

        return chapters
    }

    private fun wrapList(list: ArrayList<ContentStoreItem>): ArrayList<ListItemStatusImage> {
        val result = ArrayList<ListItemStatusImage>()
        for (item in list) {
            val wrapped = ContentStoreItemViewModel(item)
            wrapped.mOnLongClick = lambda@{ holder ->
                val parent = holder.parent
                val taskRefresh = {
                    GEMApplication.postOnMain {
                        holder.updateViews(wrapped)
                    }
                }
                when (GEMSdkCall.execute { item.getStatus() }) {
                    TContentStoreItemStatus.ECIS_Paused,
                    TContentStoreItemStatus.ECIS_Completed -> {
                        val menu = PopupMenu(parent.context, parent)
                        menu.menu.add("Delete")
                        menu.show()

                        menu.setOnMenuItemClickListener { _ ->
                            GEMSdkCall.execute {
                                item.deleteContent()
                                taskRefresh()
                            }
                            true
                        }
                    }

                    TContentStoreItemStatus.ECIS_Unavailable -> {
                    }

                    else -> {
                        val menu = PopupMenu(parent.context, parent)
                        menu.menu.add("Cancel")
                        menu.show()

                        menu.setOnMenuItemClickListener { _ ->
                            GEMSdkCall.execute {
                                item.cancelDownload()
                                taskRefresh()
                            }
                            true
                        }
                    }
                }
                return@lambda true
            }

            result.add(wrapped)
        }
        return result
    }

    override fun applyFilter(filter: String) {
        val resultList = if (filter.isNotEmpty()) {
            val lowerFilter = filter.toLowerCase(Locale.getDefault())
            val filterTokens = lowerFilter.split(' ', '-')

            ArrayList(
                models.filter {
                    val arg = GEMSdkCall.execute { it.getName() }
                    val lowerArg = arg?.toLowerCase(Locale.getDefault()) ?: ""
                    val argTokens = lowerArg.split(' ', '-')

                    for (filterWord in filterTokens) {
                        var found = false
                        for (token in argTokens) {
                            if (token.startsWith(filterWord)) {
                                found = true
                                break
                            }
                        }
                        if (!found) {
                            return@filter false
                        }
                    }

                    true
                }
            )
        } else {
            models
        }

        displayList(resultList)
    }

    fun displayList(storeItems: ArrayList<ContentStoreItem>) {
        val chapters = toChapters(storeItems)
        val wrapped = ArrayList<ArrayList<ListItemStatusImage>>()
        for (chapter in chapters) {
            wrapped.add(wrapList(chapter))
        }

        // adapt result to needs
        val adapter = ChapterLISIAdapter(wrapped)

        adapter.onStatusIconTapped = { _, it, holder ->
            onItemTapped(it, holder)
        }

        list_view.adapter = adapter
    }

    private fun onItemTapped(itUncasted: BaseListItem, holder: LISIViewHolder) {
        val item = (itUncasted as ContentStoreItemViewModel).it
// 		if(holder.isChapterHeader){
// 			
// 		}
// 		else{
        when (GEMSdkCall.execute { item.getStatus() }) {
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
// 		}
    }
}
