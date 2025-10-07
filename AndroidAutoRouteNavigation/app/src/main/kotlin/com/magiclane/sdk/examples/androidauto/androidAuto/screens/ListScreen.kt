/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.magiclane.sdk.examples.androidauto.androidAuto.screens

import androidx.car.app.CarContext
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Metadata
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.magiclane.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.util.Util

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
        headerAction?.let { builder.setHeaderAction(it) }

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
                builder.setOnClickListener {
                    model.onClicked?.let { it() }
                }
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
