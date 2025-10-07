// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.routeterrainprofile

// -------------------------------------------------------------------------------------------------

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.codecrafters.tableview.SortableTableView
import de.codecrafters.tableview.TableView
import de.codecrafters.tableview.model.TableColumnWeightModel
import de.codecrafters.tableview.toolkit.LongPressAwareTableDataAdapter

// -------------------------------------------------------------------------------------------------

class SortableClimbTableView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet,
    styleAttributes: Int = android.R.attr.listViewStyle
) : SortableTableView<Climb?>(context, attributes, styleAttributes)
{
    init
    {
        val tableColumnWeightModel = TableColumnWeightModel(4)

        // change also the size inside TableDataAdapter, TableHeaderAdapter and MapChartList (3/20)
        tableColumnWeightModel.setColumnWeight(0, 1)
        tableColumnWeightModel.setColumnWeight(1, 2)
        tableColumnWeightModel.setColumnWeight(2, 1)
        tableColumnWeightModel.setColumnWeight(3, 1)
        columnModel = tableColumnWeightModel
    }
}

// -------------------------------------------------------------------------------------------------

class ClimbTableDataAdapter(context: Context?, data: List<Climb?>?, tableView: TableView<Climb?>?) :
    LongPressAwareTableDataAdapter<Climb?>(context, data, tableView)
{
    // ---------------------------------------------------------------------------------------------

    override fun getDefaultCellView(rowIndex: Int, columnIndex: Int, parentView: ViewGroup): View
    {
        val climb = getRowData(rowIndex)
        return when (columnIndex)
        {
            0 -> renderClimbRating(climb, parentView)
            1 -> renderStartEndPointsElevation(climb, parentView)
            2 -> renderClimbLength(climb, parentView)
            3 -> renderAvgGrade(climb, parentView)
            else -> renderClimbRating(climb, parentView)
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun getLongPressCellView(
        rowIndex: Int,
        columnIndex: Int,
        parentView: ViewGroup
    ): View =  getDefaultCellView(rowIndex, columnIndex, parentView)

    // ---------------------------------------------------------------------------------------------

    private fun renderStartEndPointsElevation(climb: Climb?, parentView: ViewGroup): View
    {
        val view = layoutInflater.inflate(R.layout.cell_two_texts, parentView, false)
        setColor(climb, view)
        val startEndPoint = view.findViewById<TextView>(R.id.first_text)
        val startEndElevation = view.findViewById<TextView>(R.id.second_text)
        startEndPoint.apply {
            text = climb?.startEndPoint
            setTextColor(Color.BLACK)
        }
        startEndElevation.apply {
            text = climb?.startEndElevation
            setTextColor(Color.BLACK)
        }
        if (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK == Configuration.SCREENLAYOUT_SIZE_LARGE)
        {
            if (startEndPoint.text.toString().length > 20)
            {
                startEndPoint.textSize = textSizeLargeLongText.toFloat()
            }
            else
            {
                startEndPoint.textSize = textSizeLarge.toFloat()
            }
            if (startEndElevation.text.toString().length > 20)
            {
                startEndElevation.textSize = textSizeLargeLongText.toFloat()
            }
            else
            {
                startEndElevation.textSize = textSizeLarge.toFloat()
            }
        }
        else
        {
            if (startEndPoint.text.toString().length > 20)
            {
                startEndPoint.textSize = textSizePhoneLongText.toFloat()
            }
            else
            {
                startEndPoint.textSize = textSizePhone.toFloat()
            }
            if (startEndElevation.text.toString().length > 20)
            {
                startEndElevation.textSize = textSizePhoneLongText.toFloat()
            }
            else
            {
                startEndElevation.textSize = textSizePhone.toFloat()
            }
        }

        return view
    }

    // ---------------------------------------------------------------------------------------------

    private fun renderClimbRating(climb: Climb?, parentView: ViewGroup): View = renderString(climb?.rating.toString(), climb, parentView)

    // ---------------------------------------------------------------------------------------------

    private fun renderClimbLength(climb: Climb?, parentView: ViewGroup): View = renderString(climb?.length.toString(), climb, parentView)

    // ---------------------------------------------------------------------------------------------

    private fun renderAvgGrade(climb: Climb?, parentView: ViewGroup): View = renderString(climb?.avgGrade.toString(), climb, parentView)

    // ---------------------------------------------------------------------------------------------

    private fun renderString(value: String, climb: Climb?, parentView: ViewGroup): View
    {
        val view = layoutInflater.inflate(R.layout.cell_single_text, parentView, false)
        val textView = view.findViewById<TextView>(R.id.text)
        setColor(climb, parentView)
        textView.apply {
            text = value
            setTextColor(Color.BLACK)
        }
        
        if (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK == Configuration.SCREENLAYOUT_SIZE_LARGE)
        {
            textView.textSize = textSizeLarge.toFloat()
        }
        else
        {
            textView.textSize = textSizePhone.toFloat()
        }
        return view
    }

    // ---------------------------------------------------------------------------------------------

    private fun setColor(climb: Climb?, view: View)
    {
        if (climb != null)
        {
            val string = climb.rating
            var color = 0
            when (string)
            {
                "0" -> color = Color.parseColor("#FF6428")
                "1" -> color = Color.parseColor("#FF8C28")
                "2" -> color = Color.parseColor("#FFB428")
                "3" -> color = Color.parseColor("#FFDC28")
                "4" -> color = Color.parseColor("#FFF028")
            }
            view.setBackgroundColor(color)
        }
    }

    // ---------------------------------------------------------------------------------------------

    companion object
    {
        private const val textSizeLarge = 18
        private const val textSizeLargeLongText = 15
        private const val textSizePhone = 11
        private const val textSizePhoneLongText = 9
    }

    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------

class Climb(
    var rating: String,
    var startEndPoint: String,
    var length: String,
    var startEndElevation: String,
    var avgGrade: String
)

// -------------------------------------------------------------------------------------------------
