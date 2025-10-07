package com.magiclane.sdk.examples.mapselection_compose

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.magiclane.sdk.examples.mapselection_compose.data.LocationDetailsInfo
import com.magiclane.sdk.examples.mapselection_compose.ui.theme.MapSelectionTheme


@Composable
fun LocationDetailsScreen(modifier: Modifier = Modifier, locationDetailsInfo: LocationDetailsInfo) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        with(locationDetailsInfo) {
            if (image != null)
                Image(
                    bitmap = image, contentDescription = "location icon",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .clip(RectangleShape)
                        .size(60.dp)
                )
            else {
                val defaultImageResource = if(locationDetailsInfo.text == "My position") R.drawable.ic_current_location_arrow else R.drawable.ic_baseline_route_24
                Image(
                    painter = painterResource(defaultImageResource), contentDescription = "location icon",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .clip(RectangleShape)
                        .size(60.dp)
                )
            }
            TitleDescriptionColumn(Modifier.weight(1f), text, description)
        }
    }
}

@SuppressLint("LocalContextResourcesRead")
@Preview
@Composable
private fun LocationDetailsScreenPreview() {
    val context = LocalContext.current
    val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.sky_night)

    MapSelectionTheme {
        Surface {
            LocationDetailsScreen(
                locationDetailsInfo = LocationDetailsInfo(
                    bmp.asImageBitmap(),
                    "This is the title",
                    "This is the description"
                )
            )
        }
    }
}
