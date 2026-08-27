/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.definepersistentroadblockcompose.ui.components

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.compose.components.R as ComponentsR
import com.magiclane.sdk.compose.components.common.PanelTopBar
import com.magiclane.sdk.compose.components.traffic.RoadblockCard
import com.magiclane.sdk.compose.components.traffic.RoadblockDefinitionToolbar
import com.magiclane.sdk.compose.map.GemMap
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.compose.map.ObstructionEdge
import com.magiclane.sdk.compose.map.mapObstruction
import com.magiclane.sdk.compose.map.rememberGemMapState
import com.magiclane.sdk.compose.sdk.rememberGemSdkState
import com.magiclane.sdk.compose.ui.ErrorDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.definepersistentroadblockcompose.R
import com.magiclane.sdk.examples.definepersistentroadblockcompose.RoadblockItem
import com.magiclane.sdk.examples.definepersistentroadblockcompose.RoadblocksModel
import com.magiclane.sdk.util.Util

// Fraction of the screen width the info panel occupies in landscape, leaving the right
// half free for centering the presented roadblock.
private const val LANDSCAPE_PANEL_WIDTH_FRACTION = 0.5f

// Height caps of the startup explanation sheet, so it never covers the whole map.
private const val SHEET_HEIGHT_FRACTION_PORTRAIT = 0.5f
private const val SHEET_HEIGHT_FRACTION_LANDSCAPE = 0.75f

@Composable
fun DefineRoadblockApp(modifier: Modifier = Modifier, viewModel: RoadblocksModel = viewModel()) {
    val context = LocalContext.current

    // Map hosting, SDK listeners, the focus-viewport system and the roadblock composables
    // come from the maps-compose library; this app owns the definition flow.
    val mapState = rememberGemMapState()
    val sdkState = rememberGemSdkState()

    LaunchedEffect(sdkState.isTokenRejected) {
        if (sdkState.isTokenRejected) {
            viewModel.errorMessage = context.getString(R.string.token_rejected_message)
        }
    }

    // The example needs the network for the map and the road-following path previews;
    // warn once if there is none.
    LaunchedEffect(Unit) {
        if (!Util.isInternetConnected(context)) {
            viewModel.errorMessage = context.getString(R.string.internet_required)
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GemMap(
                modifier = Modifier
                    .fillMaxSize()
                    // The definition target sits in the middle of the map surface; the
                    // preview polyline is computed for the position underneath it.
                    .onSizeChanged { viewModel.updateMapCenter(it.width, it.height) },
                mapState = mapState,
                sdkState = sdkState,
                onMapReady = { viewModel.initialize(mapState) },
                onSdkInitFailed = { errorCode ->
                    viewModel.errorMessage = context.getString(
                        R.string.sdk_initialization_failed,
                        GemError.getMessage(errorCode, context),
                    )
                },
            )

            DefineRoadblockScreen(
                modifier = Modifier.fillMaxSize(),
                mapState = mapState,
                viewModel = viewModel,
            )

            if (viewModel.isListVisible) {
                RoadblocksListScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                )
            }
        }
    }

    DefineRoadblockDialogs(viewModel)
}

@Composable
private fun DefineRoadblockScreen(modifier: Modifier = Modifier, mapState: GemMapState, viewModel: RoadblocksModel) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var cancelConfirmVisible by remember { mutableStateOf(false) }

    // Back closes, in order: the set-roadblock panel (cancelling the definition, like
    // Magic Earth), the roadblocks list, the info panel, then asks about an ongoing
    // definition; otherwise the default back behavior closes the app.
    BackHandler(
        enabled = viewModel.isSetRoadblockPanelVisible || viewModel.isListVisible ||
            viewModel.infoItem != null || viewModel.isDefining,
    ) {
        when {
            viewModel.isSetRoadblockPanelVisible -> viewModel.onSetRoadblockPanelClosed()
            viewModel.isListVisible -> viewModel.hideList()
            viewModel.infoItem != null -> viewModel.dismissInfoPanel()
            viewModel.isDefining -> cancelConfirmVisible = true
        }
    }

    // The info panel grows when the async card details arrive; re-center the presented
    // roadblock in the free map space one frame later, after the map obstruction the
    // panel registers has been laid out for the new size.
    val infoItem = viewModel.infoItem
    LaunchedEffect(infoItem, infoItem?.data, isLandscape) {
        infoItem ?: return@LaunchedEffect
        withFrameNanos { }
        viewModel.centerOnRoadblock(infoItem)
    }

    Box(modifier) {
        if (viewModel.isDefining) {
            // Target cross the roads are traced with while defining a roadblock.
            Image(
                painter = painterResource(R.drawable.ic_target_56),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center),
            )

            // Roadblock definition toolbar, from the maps-compose library: "✓" commits
            // the roadblock, "+" adds a new segment.
            RoadblockDefinitionToolbar(
                title = stringResource(R.string.define_roadblock),
                onFinish = viewModel::finishRoadblockDefinition,
                onAddSegment = viewModel::addSegmentPoint,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(
                        WindowInsets.systemBars.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .mapObstruction(mapState, ObstructionEdge.Top),
            )

            MapFab(
                icon = R.drawable.ic_close_24,
                contentDescription = R.string.cancel_definition,
                onClick = { cancelConfirmVisible = true },
                modifier = Modifier.align(Alignment.BottomStart),
                insetSides = WindowInsetsSides.Start + WindowInsetsSides.Bottom,
            )
        } else {
            MapFab(
                icon = R.drawable.ic_list_24,
                contentDescription = R.string.open_roadblocks_list,
                onClick = viewModel::showList,
                modifier = Modifier.align(Alignment.TopEnd),
                insetSides = WindowInsetsSides.End + WindowInsetsSides.Top,
            )

            var transportDialogVisible by remember { mutableStateOf(false) }
            MapFab(
                icon = R.drawable.ic_settings_24,
                contentDescription = R.string.open_transport_mode_settings,
                onClick = { transportDialogVisible = true },
                modifier = Modifier.align(Alignment.BottomEnd),
                insetSides = WindowInsetsSides.End + WindowInsetsSides.Bottom,
            )
            if (transportDialogVisible) {
                TransportModeDialog(
                    selectedIndex = viewModel.selectedTransportIndex,
                    onSelect = { index ->
                        viewModel.selectTransportMode(index)
                        transportDialogVisible = false
                    },
                    onDismiss = { transportDialogVisible = false },
                )
            }
        }

        // Panel shown when a user defined roadblock is presented (map icon tap or
        // roadblocks list tap): the same card as in the roadblocks list, including the
        // delete button. Full-width at the bottom in portrait, docked to the start side
        // at half the screen width in landscape; the map obstruction it registers keeps
        // the presented roadblock centered in the remaining free map space.
        infoItem?.let { item ->
            var deleteConfirmVisible by remember(item) { mutableStateOf(false) }

            val insetSides = if (isLandscape) {
                WindowInsetsSides.Start + WindowInsetsSides.Bottom
            } else {
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // A tall card (landscape, long street names) stops below the top
                    // system bar instead of extending under it; the card then scrolls.
                    .windowInsetsPadding(
                        WindowInsets.systemBars.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Top),
                    )
                    .fillMaxWidth(if (isLandscape) LANDSCAPE_PANEL_WIDTH_FRACTION else 1f)
                    .mapObstruction(
                        mapState,
                        if (isLandscape) ObstructionEdge.Start else ObstructionEdge.Bottom,
                    ),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {
                RoadblockCard(
                    data = item.data,
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.systemBars.union(WindowInsets.displayCutout)
                                .only(insetSides),
                        )
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    onDelete = { deleteConfirmVisible = true },
                )
            }

            if (deleteConfirmVisible) {
                DeleteRoadblockDialog(
                    onConfirm = {
                        deleteConfirmVisible = false
                        viewModel.deleteRoadblock(item)
                    },
                    onDismiss = { deleteConfirmVisible = false },
                )
            }
        }

        // Set-roadblock panel (name + validity interval), on top of everything with a
        // scrim consuming the taps around it: closing it is an explicit choice because
        // it cancels the whole definition, like Magic Earth's set roadblock view.
        if (viewModel.isSetRoadblockPanelVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
            ) {
                SetRoadblockPanel(
                    defaultName = viewModel.setRoadblockDefaultName,
                    onClose = viewModel::onSetRoadblockPanelClosed,
                    onDone = viewModel::defineRoadblock,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding(),
                )
            }
        }
    }

    if (cancelConfirmVisible) {
        AlertDialog(
            text = { Text(stringResource(R.string.cancel_roadblock_definition)) },
            onDismissRequest = { cancelConfirmVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    cancelConfirmVisible = false
                    viewModel.cancelDefinition()
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { cancelConfirmVisible = false }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

/** Full-screen view with the active persistent roadblocks. */
@Composable
private fun RoadblocksListScreen(modifier: Modifier = Modifier, viewModel: RoadblocksModel) {
    // The list's primary-colored top bar sits under the status bar: light icons while
    // it is open, back to theme-appropriate ones when it closes.
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    DisposableEffect(darkTheme) {
        val controller = (view.context as? Activity)?.window
            ?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = !darkTheme }
    }

    var pendingDelete by remember { mutableStateOf<RoadblockItem?>(null) }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        Surface(color = MaterialTheme.colorScheme.primary) {
            PanelTopBar(
                title = stringResource(R.string.roadblocks),
                modifier = Modifier.statusBarsPadding(),
                onClose = viewModel::hideList,
            )
        }

        if (viewModel.roadblocks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.no_roadblocks),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.systemBars.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Horizontal),
                    ),
            ) {
                items(viewModel.roadblocks) { item ->
                    RoadblockCard(
                        data = item.data,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        onClick = { viewModel.showRoadblockOnMap(item) },
                        onDelete = { pendingDelete = item },
                    )
                }
            }
        }
    }

    pendingDelete?.let { item ->
        DeleteRoadblockDialog(
            onConfirm = {
                pendingDelete = null
                viewModel.deleteRoadblock(item)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Map-overlay floating action button in the surface color, inset-aware. */
@Composable
private fun MapFab(
    icon: Int,
    contentDescription: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    insetSides: WindowInsetsSides,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout).only(insetSides),
            )
            .padding(16.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(contentDescription),
        )
    }
}

/**
 * Transport mode selection: one radio-button row per mode, the checked radio button
 * marking the current selection. Tapping a row (also the already selected one) selects
 * it and dismisses the dialog.
 */
@Composable
private fun TransportModeDialog(selectedIndex: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        title = { Text(stringResource(R.string.transport_mode)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                listOf(
                    R.string.car to ComponentsR.drawable.ml_car_24,
                    R.string.truck to ComponentsR.drawable.ml_lorry_24,
                    R.string.bike to ComponentsR.drawable.ml_bike_24,
                ).forEachIndexed { index, (label, icon) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = index == selectedIndex,
                                onClick = { onSelect(index) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = null)
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

@Composable
private fun DeleteRoadblockDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        title = { Text(stringResource(R.string.delete_one_item)) },
        text = { Text(stringResource(R.string.action_can_not_be_undone)) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.yes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.no)) }
        },
    )
}

@Composable
private fun DefineRoadblockDialogs(viewModel: RoadblocksModel) {
    // Startup explanation of the definition flow, shown once the map is ready.
    if (viewModel.explanationVisible) {
        ExplanationSheet(onDismiss = { viewModel.explanationVisible = false })
    }

    if (viewModel.errorMessage.isNotEmpty()) {
        ErrorDialog(
            message = viewModel.errorMessage,
            onDismiss = { viewModel.errorMessage = "" },
            title = null,
            confirmText = stringResource(R.string.ok),
        )
    }
}

/**
 * Startup explanation, presented like the non-Compose example: a bottom sheet with a
 * circled close button, capped at half the screen height in portrait and 75% in
 * landscape. The message scrolls so capped content is never cut off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExplanationSheet(onDismiss: () -> Unit) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val maxSheetHeight = configuration.screenHeightDp.dp *
        if (isLandscape) SHEET_HEIGHT_FRACTION_LANDSCAPE else SHEET_HEIGHT_FRACTION_PORTRAIT

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        // The sheet itself reaches the bottom screen edge; the content pads itself
        // away from the system bars instead.
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = maxSheetHeight)
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(horizontal = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.define_roadblock),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_highlight_off_34),
                        contentDescription = stringResource(R.string.close_dialog),
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.explanation_message),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
            )
        }
    }
}
