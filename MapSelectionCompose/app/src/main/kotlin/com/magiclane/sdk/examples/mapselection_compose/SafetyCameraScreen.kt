package com.magiclane.sdk.examples.mapselection_compose

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.magiclane.sdk.examples.mapselection_compose.data.ECardinalDirections
import com.magiclane.sdk.examples.mapselection_compose.data.SafetyCameraInfo
import com.magiclane.sdk.examples.mapselection_compose.ui.theme.MapSelectionTheme

@Composable
fun SafetyCameraScreen(modifier: Modifier = Modifier, safetyCameraInfo: SafetyCameraInfo) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        ImageAndLargeTitle(img = safetyCameraInfo.image, title = safetyCameraInfo.type)

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = 2.dp
        )

        CameraDataSegment(safetyCameraInfo = safetyCameraInfo)

        with(safetyCameraInfo) {
            if (locationText.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 2.dp
                )
                TitleDescriptionColumn(modifier = Modifier.fillMaxWidth(), locationText, locationValue)
            }
            if (addedToDatabaseText.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 2.dp
                )
                TitleDescriptionColumn(modifier = Modifier.fillMaxWidth(), addedToDatabaseText, addedToDatabaseValue)
            }
        }
    }
}


@Composable
fun CameraDataSegment(modifier: Modifier = Modifier, safetyCameraInfo: SafetyCameraInfo) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (safetyCameraInfo.towardsValue.isNotEmpty()) {
            CameraCompass(cardinalDirections = safetyCameraInfo.towardsValue)
        }
        Column {
            Text("Speed Limit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ValueWithUnit(value = safetyCameraInfo.speedLimitValue, unit = safetyCameraInfo.speedLimitUnit)
            with(safetyCameraInfo) {
                if (cameraStatusText.isNotEmpty()) {
                    TitleDescriptionColumn(Modifier, cameraStatusText, cameraStatusValue)
                }
                if (towardsText.isNotEmpty()) {
                    TitleDescriptionColumn(Modifier, towardsText, towardsValue.toString())
                }
                if (drivingDirectionText.isNotEmpty()) {
                    TitleDescriptionColumn(Modifier, drivingDirectionText, drivingDirectionValue)
                }
            }
        }
    }
}

@Composable
fun CameraCompass(modifier: Modifier = Modifier, cardinalDirections: MutableList<ECardinalDirections>) {
    Box(modifier) {
        Text("N", modifier = Modifier.align(Alignment.TopCenter))
        Text("S", modifier = Modifier.align(Alignment.BottomCenter))
        Text("E", modifier = Modifier.align(Alignment.CenterEnd))
        Text("W", modifier = Modifier.align(Alignment.CenterStart))
        DirectionWheel(
            modifier = Modifier
                .size(200.dp)
                .padding(24.dp)
                .align(Alignment.Center), cardinalDirections
        )
    }
}

@Composable
fun DirectionWheel(modifier: Modifier = Modifier, cardinalDirections: MutableList<ECardinalDirections>) {
    val directions = ECardinalDirections.entries
    val highlightedColor = Color.Green
    val defaultColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val segmentAngle = 360f / directions.size

    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val rect = Rect(0f, 0f, diameter, diameter)
        directions.forEachIndexed { index, direction ->
            val startAngle = index * segmentAngle - 112.5f // Start from top (North)
            val color = if (direction in cardinalDirections) highlightedColor else defaultColor
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = segmentAngle,
                useCenter = true,
                topLeft = rect.topLeft,
                size = rect.size
            )
        }
    }
}

@Preview(heightDp = 600, widthDp = 412)
@Composable
private fun SafetyCameraScreenPreview() {
    MapSelectionTheme {
        //val viewModel = viewModel<MapSelectionModel>()
        Surface {
            SafetyCameraScreen(
                modifier = Modifier.fillMaxWidth(), SafetyCameraInfo(
                    type = "Speed camera",
                    speedLimitValue = "60",
                    speedLimitUnit = "km/h",
                    cameraStatusText = "Camera Status",
                    cameraStatusValue = "Active",
                    towardsText = "Towards",
                    towardsValue = mutableListOf(ECardinalDirections.SE,ECardinalDirections.NW ),
                    drivingDirectionText = "Driving direction",
                    drivingDirectionValue = "One way",
                    addedToDatabaseText = "Added to database",
                    addedToDatabaseValue = "10/20/2021",
                    locationText = "Location",
                    locationValue = "128 Maplewood Drive, Riverview, CA 90210"
                )
            )
        }
    }
}

@SuppressLint("LocalContextResourcesRead")
@Preview(heightDp = 350, widthDp = 412)
@Composable
private fun CameraDataSegmentPreview() {
    val context = LocalContext.current
    val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.sky_night)
    MapSelectionTheme {
        //val viewModel = viewModel<MapSelectionModel>()
        Surface {
            CameraDataSegment(
                modifier = Modifier.fillMaxWidth(), SafetyCameraInfo(
                    image = bmp.asImageBitmap(),
                    speedLimitValue = "60",
                    speedLimitUnit = "km/h",
                    cameraStatusText = "Camera Status",
                    cameraStatusValue = "Active",
                    towardsText = "Towards",
                    towardsValue = mutableListOf(ECardinalDirections.SE,ECardinalDirections.NW ),
                    drivingDirectionText = "Driving direction",
                    drivingDirectionValue = "One way"
                )
            )
        }
    }
}

@Preview(heightDp = 200, widthDp = 200)
@Composable
private fun CameraCompassPreview() {
    MapSelectionTheme {
        CameraCompass(modifier = Modifier.fillMaxSize(), cardinalDirections = mutableListOf(ECardinalDirections.SE,ECardinalDirections.NW ))
    }
}
