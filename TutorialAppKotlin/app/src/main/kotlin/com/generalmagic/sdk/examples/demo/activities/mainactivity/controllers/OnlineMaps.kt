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
import android.widget.PopupMenu
import com.generalmagic.sdk.content.ContentStore
import com.generalmagic.sdk.content.ContentStoreItem
import com.generalmagic.sdk.content.EContentStoreItemStatus
import com.generalmagic.sdk.content.EContentType
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.enums.SdkError
import com.generalmagic.sdk.examples.demo.activities.*
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.util.SdkCall
import java.util.*
import kotlin.collections.ArrayList

class OnlineMapsActivity : MapsListActivity() {
    private val contentStore = ContentStore()
    private var models = ArrayList<ContentStoreItem>()

    private val progressListener = object : ProgressListener() {
        override fun notifyComplete(reason: SdkError, hint: String) {
            models.clear()

            // get call result
            val result = ContentStore().getStoreContentList(EContentType.RoadMap.value)
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

        SdkCall.execute {
            contentStore.asyncGetStoreContentList(EContentType.RoadMap.value, progressListener)
        }
    }

    private fun toChapters(storeItems: ArrayList<ContentStoreItem>): ArrayList<ArrayList<ContentStoreItem>> {
        val chapters = arrayListOf<ArrayList<ContentStoreItem>>()
        chapters.add(ArrayList())

        var lastName = ""
        var sameInRow = false
        for (i in 0 until storeItems.size) {
            val crtName = SdkCall.execute { storeItems[i].getChapterName() } ?: ""

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
                when (SdkCall.execute { item.getStatus() }) {
                    EContentStoreItemStatus.Paused,
                    EContentStoreItemStatus.Completed -> {
                        val menu = PopupMenu(parent.context, parent)
                        menu.menu.add("Delete")
                        menu.show()

                        menu.setOnMenuItemClickListener {
                            SdkCall.execute {
                                item.deleteContent()
                                taskRefresh()
                            }
                            true
                        }
                    }

                    EContentStoreItemStatus.Unavailable -> {
                    }

                    else -> {
                        val menu = PopupMenu(parent.context, parent)
                        menu.menu.add("Cancel")
                        menu.show()

                        menu.setOnMenuItemClickListener {
                            SdkCall.execute {
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
            val lowerFilter = filter.lowercase(Locale.getDefault())
            val filterTokens = lowerFilter.split(' ', '-')

            ArrayList(
                models.filter {
                    val arg = SdkCall.execute { it.getName() }
                    val lowerArg = arg?.lowercase(Locale.getDefault()) ?: ""
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

        listView.adapter = adapter
    }

    private fun onItemTapped(itUncasted: BaseListItem, holder: LISIViewHolder) {
        val item = (itUncasted as ContentStoreItemViewModel).it
// 		if(holder.isChapterHeader){
// 			
// 		}
// 		else{
        when (SdkCall.execute { item.getStatus() }) {
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
// 		}
    }
}
