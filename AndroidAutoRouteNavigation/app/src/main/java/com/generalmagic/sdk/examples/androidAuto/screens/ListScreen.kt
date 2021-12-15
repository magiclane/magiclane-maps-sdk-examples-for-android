/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.generalmagic.sdk.examples.androidAuto.screens

import androidx.car.app.CarContext
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Metadata
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.generalmagic.sdk.examples.androidAuto.util.Util
import com.generalmagic.sdk.examples.androidAuto.model.GenericListItemModel
import com.generalmagic.sdk.examples.androidAuto.model.UIActionModel

abstract class ListScreen(context: CarContext) : GemScreen(context) {
    var title: String = ""
    var headerAction: UIActionModel = UIActionModel()
    var noDataText: String = ""
    var isSelectableList: Boolean = false
    var selectedItemIndex: Int = 0
    var listItemModelList: ArrayList<GenericListItemModel> = arrayListOf()

    open fun didSelectItem(index: Int) {}

    override fun onGetTemplate(): Template {
        updateData()

        val builder = ListTemplate.Builder()

        val headerAction = UIActionModel.createAction(context, headerAction)

        builder.setTitle(title)
        builder.setHeaderAction(headerAction)

        builder.setLoading(isLoading)
        if (!isLoading) {
            builder.setSingleList(getItemList())
        }

        return builder.build()
    }

    private fun getItemList(): ItemList {
        val builder = ItemList.Builder()

        if (listItemModelList.isEmpty()) {
            builder.setNoItemsMessage(noDataText)
            return builder.build()
        }

        var selectedIndex = selectedItemIndex
        if (selectedIndex == -1)
            selectedIndex = 0

        builder.setSelectedIndex(selectedIndex)
        if (isSelectableList) {
            builder.setOnSelectedListener { index ->
                didSelectItem(index)
            }
        }

        for (model in listItemModelList) {
            createRow(context, model)?.let {
                builder.addItem(it)
            }
        }

        return builder.build()
    }

    private fun createRow(context: CarContext, model: GenericListItemModel): Row? {
        try {
            val place = model.createPlace(context)

            val builder = Row.Builder()
            builder.setTitle(model.title)
            builder.addText(model.description)
            builder.setBrowsable(model.isBrowsable == true)

            Util.asCarIcon(context, model.icon)?.let {
                builder.setImage(it)
            } ?: run {
                model.iconId?.let { iconId ->
                    Util.getDrawableIcon(context, iconId).let {
                        builder.setImage(it)
                    }
                }
            }

            if (model.hasToggle == true) {
                builder.setToggle(model.createToggle())
            } else if (!isSelectableList) {
                model.onClicked?.let { builder.setOnClickListener(it) }
            }

            place?.let {
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
