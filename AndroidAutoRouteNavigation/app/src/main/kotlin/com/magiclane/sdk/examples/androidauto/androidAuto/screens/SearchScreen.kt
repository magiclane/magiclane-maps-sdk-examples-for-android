/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.magiclane.sdk.examples.androidauto.androidAuto.screens

import androidx.car.app.CarContext
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.core.text.HtmlCompat
import com.magiclane.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.util.Util

abstract class SearchScreen(context: CarContext) : GemScreen(context) {
    var initialText: String = ""
    var hintText: String = ""
    var noDataText: String = ""
    var headerAction: UIActionModel = UIActionModel()
    var showKeyboardByDefault: Boolean = false
    var listItemModelList: ArrayList<GenericListItemModel> = arrayListOf()
    var actionStripModelList: ArrayList<UIActionModel> = arrayListOf()

    abstract fun onTextInputChanged(value: String)
    abstract fun onTextInputSubmit(value: String)
    open fun onListItemsVisibilityChanged(startIndex: Int, endIndex: Int) {}

    override fun onGetTemplate(): Template {
        updateData()

        val builder = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                onTextInputChanged(searchText)
            }

            override fun onSearchSubmitted(searchText: String) {
                onTextInputSubmit(searchText)
            }
        })

        try {
            val headerAction = UIActionModel.createAction(context, headerAction)

            Util.getActionStrip(context, actionStripModelList)?.let { builder.setActionStrip(it) }
            builder.setHeaderAction(headerAction)
            builder.setShowKeyboardByDefault(showKeyboardByDefault)
            builder.setLoading(isLoading)

            initialText.let { builder.setInitialSearchText(it) }
            hintText.let { builder.setSearchHint(it) }

            if (!isLoading) {
                val itemsListBuilder = ItemList.Builder()

                if (listItemModelList.size > 0) {
                    listItemModelList.forEach { rowData ->
                        createRow(context, rowData)?.let { itemsListBuilder.addItem(it) }
                    }
                } else {
                    var noDataText = noDataText
                    if (noDataText.isEmpty())
                        noDataText = "-"

                    noDataText.let { itemsListBuilder.setNoItemsMessage(it) }
                }

                itemsListBuilder.setOnItemsVisibilityChangedListener { startIndex, endIndex ->
                    onListItemsVisibilityChanged(startIndex, endIndex)
                }

                builder.setItemList(itemsListBuilder.build())
            }

            return builder.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        throw Exception("Bad input data.")
    }

    // -------------

    companion object {
        private fun createRow(context: CarContext, item: GenericListItemModel): Row? {
            try {
                val builder = Row.Builder()

                val title = HtmlCompat.fromHtml(item.title, HtmlCompat.FROM_HTML_MODE_LEGACY)
                val description =
                    HtmlCompat.fromHtml(item.description, HtmlCompat.FROM_HTML_MODE_LEGACY)
                builder.setTitle(title)
                builder.addText(description)

                Util.asCarIcon(context, item.icon)?.let {
                    builder.setImage(it)
                }

                item.isBrowsable?.let { builder.setBrowsable(it) }

                item.onClicked?.let { builder.setOnClickListener(it) }

                item.createPlace(context)?.let {
                    val metaBuilder = Metadata.Builder()
                    metaBuilder.setPlace(it)
                    builder.setMetadata(metaBuilder.build())
                }

                return builder.build()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return null
        }
    }
}
