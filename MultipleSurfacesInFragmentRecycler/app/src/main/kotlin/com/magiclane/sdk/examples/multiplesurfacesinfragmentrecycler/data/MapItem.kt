package com.magiclane.sdk.examples.multiplesurfacesinfragmentrecycler.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MapItem(
    val id: Int,
    val date: Date
){
    val timestamp : String
        get() = SimpleDateFormat("dd.MM.yyyy HH:mm::ss", Locale("UK")).format(date)
}