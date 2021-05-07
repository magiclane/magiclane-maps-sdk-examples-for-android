package com.generalmagic.sdk.examples.demo.activities.routeprofile

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.generalmagic.sdk.examples.demo.R
import de.codecrafters.tableview.SortableTableView
import de.codecrafters.tableview.TableView
import de.codecrafters.tableview.model.TableColumnWeightModel
import de.codecrafters.tableview.toolkit.LongPressAwareTableDataAdapter

class SortableClimbTableView @JvmOverloads constructor(
    context: Context?,
    attributes: AttributeSet? = null,
    styleAttributes: Int = android.R.attr.listViewStyle
) : SortableTableView<Climb?>(context, attributes, styleAttributes) {
    init {
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
    LongPressAwareTableDataAdapter<Climb?>(context, data, tableView) {
    // ---------------------------------------------------------------------------------------------

    override fun getDefaultCellView(rowIndex: Int, columnIndex: Int, parentView: ViewGroup): View {
        val climb = getRowData(rowIndex)
        return when (columnIndex) {
            0 -> renderClimbRating(climb, parentView)
            1 -> renderStartEndPointsElevation(climb, parentView)
            2 -> renderClimbLength(climb)
            3 -> renderAvgGrade(climb)
            else -> renderClimbRating(climb, parentView)
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun getLongPressCellView(
        rowIndex: Int,
        columnIndex: Int,
        parentView: ViewGroup
    ): View {
        return getDefaultCellView(rowIndex, columnIndex, parentView)
    }

    // ---------------------------------------------------------------------------------------------

    private fun renderClimbRating(climb: Climb?, parentView: ViewGroup): View {
        val view = layoutInflater.inflate(R.layout.table_rating_view, parentView, false)
        val rating = view.findViewById<TextView>(R.id.rating)
        rating.text = climb?.rating

        setColor(climb, view)
        if (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK == Configuration.SCREENLAYOUT_SIZE_LARGE) {
            rating.textSize = textSizeLarge.toFloat()
        } else {
            rating.textSize = textSizePhone.toFloat()
        }
        return view
    }

    // ---------------------------------------------------------------------------------------------

    private fun renderStartEndPointsElevation(climb: Climb?, parentView: ViewGroup): View {
        val view =
            layoutInflater.inflate(R.layout.cell_start_end_points_elevation, parentView, false)
        setColor(climb, view)
        val startEndPoint = view.findViewById<TextView>(R.id.start_end_points)
        val startEndElevation = view.findViewById<TextView>(R.id.start_end_elevation)
        startEndPoint.text = climb?.startEndPoint
        startEndElevation.text = climb?.startEndElevation
        if (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK == Configuration.SCREENLAYOUT_SIZE_LARGE) {
            if (startEndPoint.text.toString().length > 20) {
                startEndPoint.textSize = textSizeLargeLongText.toFloat()
            } else {
                startEndPoint.textSize = textSizeLarge.toFloat()
            }
            if (startEndElevation.text.toString().length > 20) {
                startEndElevation.textSize = textSizeLargeLongText.toFloat()
            } else {
                startEndElevation.textSize = textSizeLarge.toFloat()
            }
        } else {
            if (startEndPoint.text.toString().length > 20) {
                startEndPoint.textSize = textSizePhoneLongText.toFloat()
            } else {
                startEndPoint.textSize = textSizePhone.toFloat()
            }
            if (startEndElevation.text.toString().length > 20) {
                startEndElevation.textSize = textSizePhoneLongText.toFloat()
            } else {
                startEndElevation.textSize = textSizePhone.toFloat()
            }
        }

        return view
    }

    // ---------------------------------------------------------------------------------------------

    private fun renderClimbLength(climb: Climb?): View {
        return renderString(climb?.length.toString(), climb)
    }

    // ---------------------------------------------------------------------------------------------

    private fun renderAvgGrade(climb: Climb?): View {
        return renderString(climb?.avgGrade.toString(), climb)
    }

    // ---------------------------------------------------------------------------------------------

    private fun renderString(value: String, climb: Climb?): View {
        val textView = TextView(context)
        setColor(climb, textView)
        textView.text = value
        textView.gravity = Gravity.CENTER or Gravity.CENTER_HORIZONTAL
        if (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK == Configuration.SCREENLAYOUT_SIZE_LARGE) {
            textView.textSize = textSizeLarge.toFloat()
        } else {
            textView.textSize = textSizePhone.toFloat()
        }

        return textView
    }

    // ---------------------------------------------------------------------------------------------

    private fun setColor(climb: Climb?, view: View) {
        if (climb != null) {
            val string = climb.rating
            var color = 0
            when (string) {
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

    companion object {
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
