/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.demo.activities.searchaddress

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.generalmagic.sdk.core.Coordinates
import com.generalmagic.sdk.core.EAddressField
import com.generalmagic.sdk.core.MapDetails
import com.generalmagic.sdk.datasource.PositionService
import com.generalmagic.sdk.demo.R
import com.generalmagic.sdk.demo.app.BaseActivity
import com.generalmagic.sdk.demo.app.GEMApplication
import com.generalmagic.sdk.demo.util.AppUtils
import com.generalmagic.sdk.demo.util.Utils
import com.generalmagic.sdk.searching.GuidedAddressSearchService
import com.generalmagic.sdk.util.SdkCall
import kotlinx.android.synthetic.main.search_address_view.*

class SearchAddressActivity : BaseActivity() {

    private var searchAddressAdapter: SearchAddressAdapter? = null
    private var streetTopPadding: Int = 0
    private val iconSize =
        GEMApplication.appResources().getDimension(R.dimen.listIconSize).toInt()
    var viewId: Long = 0
    var hasState = false

    lateinit var cityTextChangedListener: CustomTextWatcher
    lateinit var streetTextChangedListener: CustomTextWatcher
    lateinit var streetNumberTextChangedListener: CustomTextWatcher
    lateinit var intersectionTextChangedListener: CustomTextWatcher

    companion object {
        var updateItems: Runnable? = null
    }

    internal interface TextChangeNotifiable {
        fun notifyTextChanged(charSequence: CharSequence?)
    }

    internal class TextChangeNotifier(
        private var charSequence: CharSequence?,
        private val notifiable: TextChangeNotifiable
    ) : Runnable {

        fun setCharSequence(charSequence: CharSequence) {
            this.charSequence = charSequence
        }

        override fun run() {
            notifiable.notifyTextChanged(charSequence)
        }

        fun start() {
            GEMApplication.postOnMainDelayed(this, 100)
        }
    }

    class CustomTextWatcher(private val field: Int) :
        TextWatcher,
        TextChangeNotifiable {

        private var notifier: TextChangeNotifier? = null

        override fun notifyTextChanged(charSequence: CharSequence?) {
            SdkCall.execute {
                GEMAddressSearchView.didChangeFilter(field, charSequence.toString())
            }
            notifier = null
        }

        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            if (GEMAddressSearchView.shouldChangeText) {
                if (notifier != null) {
                    notifier?.setCharSequence(charSequence)
                } else {
                    notifier = TextChangeNotifier(charSequence, this)
                    notifier?.start()
                }
            }
        }

        override fun afterTextChanged(editable: Editable) {
            GEMAddressSearchView.shouldChangeText = true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewId = intent.getLongExtra("viewId", 0)

        setContentView(R.layout.search_address_view)

        setSupportActionBar(toolbar)

        // display back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        SdkCall.execute {
            val position = PositionService().getPosition()
            var countryISOCode = ""
            if (position != null) {
                countryISOCode = MapDetails().getCountryCode(
                    Coordinates(
                        position.getLatitude(),
                        position.getLongitude()
                    )
                ) ?: ""
            }

            if (countryISOCode.isEmpty()) {
                countryISOCode = "USA"
            }

            val countryLevelItem = GuidedAddressSearchService().getCountryLevelItem(countryISOCode)
            countryLevelItem.let {
                GEMAddressSearchView.m_country = it
            }
        }

        GEMAddressSearchView.registerActivity(viewId, this)

        var title = ""
        var flag: Bitmap? = null
        SdkCall.execute {
            title = GEMAddressSearchView.getTitle()
            flag = GEMAddressSearchView.getCountryFlag(iconSize, iconSize)
            hasState = GEMAddressSearchView.hasState()
        }

        // set title
        supportActionBar?.title = title

        flag_icon?.let { icon ->
            icon.setOnClickListener {
                SdkCall.execute {
                    GEMAddressSearchView.didTapCountryFlag(viewId)
                }
            }

            icon.setImageBitmap(flag)

            if (!GEMAddressSearchView.isEnabled(EAddressField.ECountry)) {
                flag_icon.isEnabled = false
                flag_icon.imageAlpha = 128
            }
        }

        val bLandscape =
            GEMApplication.appResources().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        street?.let { streetTopPadding = it.paddingTop }

        recent_list?.itemsCanFocus = false
        recent_list?.setOnItemClickListener { _, view, position, _ ->
            SdkCall.execute {
                GEMAddressSearchView.didTapItem(viewId, position)
            }

            val textView = view.findViewById<TextView>(R.id.address_item)
            when {
                state?.isFocused == true -> {
                    GEMAddressSearchView.shouldChangeText = false
                    state?.text =
                        Editable.Factory.getInstance().newEditable(textView.text.split("-")[0])
                    city?.requestFocus()

                    SdkCall.execute {
                        GEMAddressSearchView.shouldChangeText = true
                        GEMAddressSearchView.didChangeFilter(
                            EAddressField.ECity.value,
                            city?.text.toString()
                        )
                    }
                }

                city?.isFocused == true -> {
                    GEMAddressSearchView.shouldChangeText = false
                    city?.text = Editable.Factory.getInstance().newEditable(textView.text)
                    street?.requestFocus()

                    SdkCall.execute {
                        GEMAddressSearchView.shouldChangeText = true
                        GEMAddressSearchView.didChangeFilter(
                            EAddressField.EStreetName.value,
                            street?.text.toString()
                        )
                    }
                }

                street?.isFocused == true -> {
                    GEMAddressSearchView.shouldChangeText = false
                    street?.text = Editable.Factory.getInstance().newEditable(textView.text)
                    street_number?.requestFocus()

                    SdkCall.execute {
                        GEMAddressSearchView.shouldChangeText = true
                        GEMAddressSearchView.didChangeFilter(
                            EAddressField.EStreetNumber.value,
                            street_number?.text.toString()
                        )
                    }
                }
            }
        }

        searchAddressAdapter = SearchAddressAdapter()

        state?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (!city?.text.isNullOrEmpty()) {
                    GEMAddressSearchView.shouldChangeText = false
                    city?.text?.clear()

                    GEMAddressSearchView.shouldChangeText = false
                    street?.text?.clear()

                    setAddressFieldEnabledState(
                        street,
                        EAddressField.EStreetName,
                        false
                    )

                    setAddressFieldEnabledState(
                        street_number,
                        EAddressField.EStreetNumber,
                        false
                    )

                    setAddressFieldEnabledState(
                        intersection,
                        EAddressField.ECrossing1,
                        false
                    )
                } else if (GEMAddressSearchView.isEnabled(EAddressField.EStreetName) ||
                    GEMAddressSearchView.isEnabled(EAddressField.EStreetNumber) ||
                    GEMAddressSearchView.isEnabled(EAddressField.ECrossing1)
                ) {
                    setAddressFieldEnabledState(
                        street,
                        EAddressField.EStreetName,
                        false
                    )
                    setAddressFieldEnabledState(
                        street_number,
                        EAddressField.EStreetNumber,
                        false
                    )
                    setAddressFieldEnabledState(
                        intersection,
                        EAddressField.ECrossing1,
                        false
                    )
                }

                state?.let {
                    if (!it.isFocused) {
                        SdkCall.execute {
                            GEMAddressSearchView.didChangeFilter(
                                EAddressField.EState.value,
                                it.text.toString()
                            )
                        }
                    }
                }
            }
            false
        }

        city?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (!street?.text.isNullOrEmpty()) {
                    GEMAddressSearchView.shouldChangeText = false
                    street?.text?.clear()

                    setAddressFieldEnabledState(
                        street_number,
                        EAddressField.EStreetNumber,
                        false
                    )

                    setAddressFieldEnabledState(
                        intersection,
                        EAddressField.ECrossing1,
                        false
                    )
                } else if (GEMAddressSearchView.isEnabled(EAddressField.EStreetNumber) ||
                    GEMAddressSearchView.isEnabled(EAddressField.ECrossing1)
                ) {
                    setAddressFieldEnabledState(
                        street_number,
                        EAddressField.EStreetNumber,
                        false
                    )

                    setAddressFieldEnabledState(
                        intersection,
                        EAddressField.ECrossing1,
                        false
                    )
                } else if (state?.text.isNullOrEmpty()) {
                    val text = SdkCall.execute {
                        GEMAddressSearchView.getItemText(0)
                    } ?: ""

                    GEMApplication.postOnMain {
                        state?.text = Editable.Factory.getInstance().newEditable(text)
                    }

                    SdkCall.execute {
                        GEMAddressSearchView.shouldChangeText = false
                        GEMAddressSearchView.didTapItem(viewId, 0)
                    }
                }

                SdkCall.execute {
                    GEMAddressSearchView.didChangeFilter(
                        EAddressField.ECity.value,
                        city?.text.toString()
                    )
                }
            }
            false
        }

        street?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (city?.text.isNullOrEmpty() || city.isFocused) {
                    val text = SdkCall.execute {
                        GEMAddressSearchView.getItemText(0)
                    } ?: ""

                    GEMApplication.postOnMain {
                        city?.text = Editable.Factory.getInstance().newEditable(text)
                    }

                    SdkCall.execute {
                        GEMAddressSearchView.shouldChangeText = false
                        GEMAddressSearchView.didTapItem(viewId, 0)
                    }
                }

                SdkCall.execute {
                    GEMAddressSearchView.didChangeFilter(
                        EAddressField.EStreetName.value,
                        street?.text.toString()
                    )
                }
            }
            false
        }

        street_number?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (street?.text.isNullOrEmpty() || street.isFocused) {
                    val text = SdkCall.execute {
                        GEMAddressSearchView.getItemText(0)
                    } ?: ""

                    GEMApplication.postOnMain {
                        street?.text = Editable.Factory.getInstance().newEditable(text)
                    }

                    SdkCall.execute {
                        GEMAddressSearchView.shouldChangeText = false
                        GEMAddressSearchView.didTapItem(viewId, 0)
                    }
                }

                SdkCall.execute {
                    GEMAddressSearchView.didChangeFilter(
                        EAddressField.EStreetNumber.value,
                        street_number?.text.toString()
                    )
                }
            }
            false
        }

        intersection?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (street?.text.isNullOrEmpty() || street.isFocused) {
                    val text = SdkCall.execute {
                        GEMAddressSearchView.getItemText(0)
                    }

                    GEMApplication.postOnMain {
                        street?.text = Editable.Factory.getInstance().newEditable(text)
                    }

                    SdkCall.execute {
                        GEMAddressSearchView.shouldChangeText = false
                        GEMAddressSearchView.didTapItem(viewId, 0)
                    }
                }

                SdkCall.execute {
                    GEMAddressSearchView.didChangeFilter(
                        EAddressField.ECrossing1.value,
                        intersection?.text.toString()
                    )
                }
            }
            false
        }

//        city?.addTextChangedListener(
//            CustomTextWatcher(EAddressField.ECity.value)
//        )
//        street?.addTextChangedListener(
//            CustomTextWatcher(EAddressField.EStreetName.value)
//        )
//        street_number?.addTextChangedListener(
//            CustomTextWatcher(EAddressField.EStreetNumber.value)
//        )
//        intersection?.addTextChangedListener(
//            CustomTextWatcher(EAddressField.ECrossing1.value)
//        )

        cityTextChangedListener = CustomTextWatcher(EAddressField.ECity.value)
        streetTextChangedListener = CustomTextWatcher(EAddressField.EStreetName.value)
        streetNumberTextChangedListener = CustomTextWatcher(EAddressField.EStreetNumber.value)
        intersectionTextChangedListener = CustomTextWatcher(EAddressField.ECrossing1.value)

        city.addTextChangedListener(cityTextChangedListener)
        street.addTextChangedListener(streetTextChangedListener)
        street_number.addTextChangedListener(streetNumberTextChangedListener)
        intersection.addTextChangedListener(intersectionTextChangedListener)

        setAddressFieldImeAction(city)
        setAddressFieldImeAction(street)
        setAddressFieldImeAction(street_number)
        setAddressFieldImeAction(intersection)

        recent_list?.adapter = searchAddressAdapter

        var cityHint = ""
        var streetHint = ""
        var streetNumberHint = ""
        var intersectionHint = ""

        SdkCall.execute {
            cityHint = GEMAddressSearchView.getHint(
                EAddressField.ECity.value
            )
            streetHint = GEMAddressSearchView.getHint(
                EAddressField.EStreetName.value
            )
            streetNumberHint = GEMAddressSearchView.getHint(
                EAddressField.EStreetNumber.value
            )
            intersectionHint = GEMAddressSearchView.getHint(
                EAddressField.ECrossing1.value
            )
        }

        recent_list.setOnScrollListener(object : AbsListView.OnScrollListener {
            private var lastFirstVisibleItem: Int = 0
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {}
            override fun onScroll(
                view: AbsListView,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int
            ) {
                // scrolling down
                if (lastFirstVisibleItem < firstVisibleItem) {
                    hideKeyboard()
                }
                lastFirstVisibleItem = firstVisibleItem
            }
        })

        setStateField(hasState)
        setAddressFieldEnabledState(city, EAddressField.ECity, !hasState)

        city?.hint = cityHint
        if (!hasState) {
            city?.requestFocus()
            showKeyboard(city)
        }

        street?.hint = streetHint
        street_number?.hint = streetNumberHint
        intersection?.hint = intersectionHint

        setAddressFieldEnabledState(street, EAddressField.EStreetName, false)
        setAddressFieldEnabledState(street_number, EAddressField.EStreetNumber, false)
        setAddressFieldEnabledState(intersection, EAddressField.ECrossing1, false)

        if (!bLandscape) {
            setAddressFieldPadding(street)
        }
        setAddressFieldPadding(street_number)
        setAddressFieldPadding(intersection)

        submit_button?.setOnClickListener {
            SdkCall.execute {
                GEMAddressSearchView.didTapSearchButton(viewId)
            }
        }

        submit_button?.setImageResource(R.drawable.ic_search_gray_24dp)

        if (bLandscape) {
            setAddressFieldsLandscapeLayoutParams()
        }

        if (updateItems != null) {
            GEMApplication.postOnMain {
                updateItems
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SdkCall.execute {
            GEMAddressSearchView.onViewClosed(viewId)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            setAddressFieldsLayoutParams(newConfig.orientation)
        }
    }

    override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams) {
        super.onWindowAttributesChanged(attrs)

        GEMApplication.postOnMainDelayed({
            if (GEMApplication.appResources().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                setAddressFieldsLayoutParams(Configuration.ORIENTATION_LANDSCAPE)
            }
        }, 20)
    }

    private fun setStateField(visible: Boolean) {
        val state = this.state ?: return

        if (!visible) {
            state.visibility = View.GONE
            return
        }

        if (state.isShown) {
            return
        }

        state.visibility = View.VISIBLE
        state.requestFocus()

        state.addTextChangedListener(
            CustomTextWatcher(EAddressField.EState.value)
        )
        setAddressFieldImeAction(state)

        state.hint = SdkCall.execute {
            GEMAddressSearchView.getHint(
                EAddressField.EState.value
            )
        }

        setAddressFieldEnabledState(state, EAddressField.EState, true)
    }

    private fun setAddressFieldsLayoutParams(newOrientation: Int) {
        if (newOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setAddressFieldsLandscapeLayoutParams()
            street?.let {
                it.setPadding(
                    it.paddingLeft,
                    streetTopPadding,
                    it.paddingRight,
                    it.paddingBottom
                )
            }
        } else {
            setAddressFieldsPortraitLayoutParams()
            street?.let { setAddressFieldPadding(it) }
        }
    }

    private fun setAddressFieldsLandscapeLayoutParams() {
        val fieldMargin =
            GEMApplication.appResources().getDimensionPixelSize(R.dimen.smallPadding)

        val availableWidth = Utils.getScreenWidth(this)
        val w = availableWidth / 2 + iconSize / 2 + fieldMargin

        val hasState = SdkCall.execute {
            GEMAddressSearchView.hasState()
        } ?: false

        val state = this.state
        val fieldWidth = if (state != null && hasState) {
            val text = state.hint
            val textWidth = state.paint.measureText(text, 0, text.length).toInt()
            w + textWidth / 2
        } else {
            w
        }

        if (findViewById<LinearLayout>(R.id.flag_city_container) != null) {
            val params = RelativeLayout.LayoutParams(
                fieldWidth,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            findViewById<LinearLayout>(R.id.flag_city_container).layoutParams = params
        }

        street?.let {
            val params = getBaseLandscapeLayoutParams(fieldMargin, fieldWidth)

            params.addRule(RelativeLayout.LEFT_OF, R.id.flag_city_container)
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT)

            params.addRule(RelativeLayout.CENTER_VERTICAL)

            it.layoutParams = params
        }
    }

    private fun getBaseLandscapeLayoutParams(
        fieldMargin: Int,
        fieldWidth: Int
    ): RelativeLayout.LayoutParams {
        val params =
            RelativeLayout.LayoutParams(fieldWidth, RelativeLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(fieldMargin, 0, 0, 0)

        return params
    }

    private fun setAddressFieldsPortraitLayoutParams() {
        val fieldMargin =
            GEMApplication.appResources().getDimensionPixelSize(R.dimen.smallPadding)

        if (findViewById<LinearLayout>(R.id.flag_city_container) != null) {
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            findViewById<LinearLayout>(R.id.flag_city_container).layoutParams = params
        }

        street?.let {
            val params = getBasePortraitLayoutParams(fieldMargin)
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            params.addRule(RelativeLayout.BELOW, R.id.flag_city_container)
            params.setMargins(fieldMargin, 0, fieldMargin, 0)

            it.layoutParams = params
        }
    }

    private fun getBasePortraitLayoutParams(fieldMargin: Int): RelativeLayout.LayoutParams {
        val params = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        params.setMargins(fieldMargin, 0, 0, 0)

        return params
    }

    class SearchAddressAdapter : BaseAdapter() {
        private lateinit var holder: ViewHolder

        override fun getCount(): Int {
            return SdkCall.execute { GEMAddressSearchView.getItemsCount() } ?: 0
        }

        override fun getItem(position: Int): Any? {
            return null
        }

        override fun getItemId(i: Int): Long {
            return i.toLong()
        }

        override fun getView(i: Int, convertView: View?, viewGroup: ViewGroup): View {
            if (convertView != null) {
                holder = convertView.tag as ViewHolder
                holder.setViews(i)
                return convertView
            } else {
                holder = ViewHolder()
                val context = GEMApplication.applicationContext()
                val view = View.inflate(context, R.layout.search_address_item, null)
                view?.let {
                    holder.mItemIcon = it.findViewById<View>(R.id.address_icon) as ImageView
                    holder.mItemLabel = it.findViewById<View>(R.id.address_item) as TextView
                    it.tag = holder
                }
                holder.setViews(i)
                return view
            }
        }

        internal inner class ViewHolder {
            var mItemLabel: TextView? = null
            var mItemIcon: ImageView? = null
            private val iconSize =
                GEMApplication.appResources().getDimension(R.dimen.listIconSize).toInt()

            fun setViews(itemIndex: Int) {
                var text = ""
                var description = ""
                var icon: Bitmap? = null

                SdkCall.execute {
                    text = GEMAddressSearchView.getItemText(itemIndex)
                    description = GEMAddressSearchView.getItemDescription(itemIndex)

                    icon = GEMAddressSearchView.getItemImage(itemIndex, iconSize, iconSize)
                }

                mItemLabel?.let {
                    it.text = if (description.isEmpty()) text else "$text - $description"
                    it.setTypeface(null, Typeface.BOLD)
                }

                mItemIcon?.let {
                    if (icon != null) {
                        it.visibility = View.VISIBLE
                        it.setImageBitmap(icon)
                    } else {
                        it.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun notifyDataChanged() {
        searchAddressAdapter?.notifyDataSetChanged()
        recent_list?.let { it.post { it.setSelection(0) } }
    }

    private fun setAddressFieldImeAction(addressField: EditText?) {
        addressField ?: return

        addressField.imeOptions = (
            EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                or EditorInfo.IME_FLAG_NO_FULLSCREEN
            )
        addressField.setOnEditorActionListener { v, actionId, event ->
            if (event != null && event.action != KeyEvent.ACTION_DOWN) {
                return@setOnEditorActionListener event.keyCode == KeyEvent.KEYCODE_ENTER
            }

            if (actionId == EditorInfo.IME_ACTION_SEARCH || event == null || event.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (GEMApplication.appResources().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                } else {
                    if (submit_button?.isEnabled == true) {
                        SdkCall.execute { GEMAddressSearchView.didTapSearchButton(viewId) }
                    }
                }
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun setAddressFieldEnabledState(
        addressField: EditText?,
        field: EAddressField,
        value: Boolean
    ) {
        if (addressField == null) return

        GEMAddressSearchView.setEnabledState(field, value)

        GEMApplication.postOnMain {
            addressField.isEnabled = value
            addressField.isClickable = value
            addressField.isFocusable = value
            addressField.isFocusableInTouchMode = value
        }
    }

    private fun setAddressFieldPadding(addressField: EditText?) {
        addressField ?: return

        val padding = AppUtils.getSizeInPixels(3)
        addressField.setPadding(
            addressField.paddingLeft,
            padding,
            addressField.paddingRight,
            addressField.paddingBottom
        )
    }

    fun selectIntersectionField() {
        setAddressFieldEnabledState(
            intersection,
            EAddressField.ECrossing1,
            true
        )
        intersection?.requestFocus()
        SdkCall.execute {
            GEMAddressSearchView.didChangeFilter(
                EAddressField.ECrossing1.value,
                intersection?.text.toString()
            )
        }
    }

    fun setField(field: Int, filter: String) {
        when (field) {
            EAddressField.EState.value -> {
                state?.text = Editable.Factory.getInstance().newEditable(filter)
                state.setSelection(state.text.length)
            }

            EAddressField.ECity.value -> {
                city?.text = Editable.Factory.getInstance().newEditable(filter)
                city.setSelection(city?.text?.length ?: 0)
            }

            EAddressField.EStreetName.value -> {
                street?.text = Editable.Factory.getInstance().newEditable(filter)
                street.setSelection(street?.text?.length ?: 0)
            }

            EAddressField.EStreetNumber.value -> {
                street_number?.text = Editable.Factory.getInstance().newEditable(filter)
                street_number.setSelection(street_number?.text?.length ?: 0)
            }

            EAddressField.ECrossing1.value -> {
                intersection?.text = Editable.Factory.getInstance().newEditable(filter)
                intersection.setSelection(intersection?.text?.length ?: 0)
            }
        }
    }

    override fun refresh() {
        city.removeTextChangedListener(cityTextChangedListener)
        street.removeTextChangedListener(streetNumberTextChangedListener)
        street_number.removeTextChangedListener(streetNumberTextChangedListener)
        intersection.removeTextChangedListener(intersectionTextChangedListener)

        street.text?.clear()
        setAddressFieldEnabledState(street, EAddressField.EStreetName, false)

        street_number.text?.clear()
        setAddressFieldEnabledState(
            street_number,
            EAddressField.EStreetNumber,
            false
        )

        intersection.text?.clear()
        setAddressFieldEnabledState(
            intersection,
            EAddressField.ECrossing1,
            false
        )

        var flag: Bitmap? = null
        var hasState = false

        SdkCall.execute {
            flag = GEMAddressSearchView.getCountryFlag(iconSize, iconSize)
            hasState = GEMAddressSearchView.hasState()
        }

        setStateField(hasState)

        if (hasState) {
            state?.text?.clear()
            city?.text?.clear()

            setAddressFieldEnabledState(city, EAddressField.ECity, false)

            SdkCall.execute {
                GEMAddressSearchView.didChangeFilter(EAddressField.EState.value, "")
            }
        } else {
            city.text?.clear()
            city.requestFocus()
            showKeyboard(city)

            SdkCall.execute {
                GEMAddressSearchView.didChangeFilter(EAddressField.ECity.value, "")
            }
        }

        flag_icon.setImageBitmap(flag)
    }

    fun refreshSearchResultsList() {
        notifyDataChanged()
        when {
            state?.isFocused == true -> {
                setAddressFieldEnabledState(city, EAddressField.ECity, true)
            }

            city?.isFocused == true -> {
                setAddressFieldEnabledState(street, EAddressField.EStreetName, true)
            }

            street?.isFocused == true -> {
                setAddressFieldEnabledState(street_number, EAddressField.EStreetNumber, true)
                setAddressFieldEnabledState(intersection, EAddressField.ECrossing1, true)
            }
        }
    }

    fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }


    fun showKeyboard(fieldView: EditText) {
        GEMApplication.postOnMainDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(fieldView, 0)
        }, 250)
    }
}
