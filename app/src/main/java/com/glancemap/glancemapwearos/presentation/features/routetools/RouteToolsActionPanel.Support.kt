package com.glancemap.glancemapwearos.presentation.features.routetools

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.features.poi.PoiSearchUiState
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import org.mapsforge.core.model.LatLong

@Composable
internal fun RouteToolsScrollIndicator(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    if (scrollState.maxValue <= 0) return

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxHeight()
                .width(6.dp),
    ) {
        val thumbHeight = (maxHeight * 0.22f).coerceIn(24.dp, 46.dp)
        val travel = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
        val progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        val thumbOffset = travel * progress

        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(50)),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbOffset)
                    .width(6.dp)
                    .height(thumbHeight)
                    .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(50)),
        )
    }
}

@Composable
internal fun RouteToolDraftSummaryDialog(
    visible: Boolean,
    session: RouteToolSession?,
    isExecuting: Boolean,
    executionMessage: String?,
    loopRetryOptions: List<RouteToolLoopRetryOption> = emptyList(),
    onDismiss: () -> Unit,
    onConfirmCreate: (() -> Unit)? = null,
    onConfirmModify: (() -> Unit)? = null,
    onSelectLoopRetryOption: ((RouteToolLoopRetryOption) -> Unit)? = null,
) {
    if (!visible || session == null) return

    val adaptive = rememberWearAdaptiveSpec()
    val bottomActionSafeInset =
        if (!adaptive.isRound) {
            3.dp
        } else {
            when (adaptive.screenSize) {
                WearScreenSize.LARGE -> 11.dp
                WearScreenSize.MEDIUM -> 13.dp
                WearScreenSize.SMALL -> 15.dp
            }
        }
    val isCreate = session.options.toolKind == RouteToolKind.CREATE
    val isReplaceCurrent = session.options.saveBehavior == RouteSaveBehavior.REPLACE_CURRENT
    val title =
        when {
            executionMessage != null && isCreate -> "Create failed"
            executionMessage != null -> "Save failed"
            isCreate -> "Create GPX?"
            isReplaceCurrent -> "Replace GPX?"
            else -> "Save GPX?"
        }
    val actionLabel =
        if (executionMessage != null) {
            "Retry"
        } else if (isCreate) {
            if (isExecuting) "Creating..." else "Create GPX"
        } else {
            if (isExecuting) "Saving..." else "Save GPX"
        }
    val supportingLine =
        buildString {
            append(session.modeTitle)
            if (!isCreate) {
                append(" • ")
                append(session.options.saveBehavior.title)
            }
        }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(alpha = 0.86f),
                        RoundedCornerShape(adaptive.dialogCornerRadius),
                    ).padding(
                        horizontal = adaptive.dialogHorizontalPadding,
                        vertical = adaptive.dialogVerticalPadding,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = supportingLine,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.84f),
                textAlign = TextAlign.Center,
            )

            when {
                executionMessage != null -> {
                    Text(
                        text = executionMessage,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = Color(0xFFFFCC80),
                    )
                }

                isCreate -> {
                    Text(
                        text = "Ready to generate the GPX.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = Color(0xFFFFCC80),
                    )
                }

                else -> {
                    Text(
                        text =
                            if (session.options.modifyMode == RouteModifyMode.RESHAPE_ROUTE) {
                                "Preview the rerouted section, then save."
                            } else if (isReplaceCurrent) {
                                "This will replace the active GPX."
                            } else {
                                "Ready to save the GPX edit."
                            },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = Color(0xFFFFCC80),
                    )
                }
            }
            when {
                executionMessage != null &&
                    session.options.toolKind == RouteToolKind.CREATE &&
                    loopRetryOptions.isNotEmpty() &&
                    onSelectLoopRetryOption != null -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = bottomActionSafeInset),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            loopRetryOptions.forEach { option ->
                                Button(
                                    onClick = { onSelectLoopRetryOption(option) },
                                    enabled = !isExecuting,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(option.label)
                                }
                            }
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(0.46f),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                        ) {
                            Text("Back")
                        }
                    }
                }

                session.options.toolKind == RouteToolKind.CREATE && onConfirmCreate != null -> {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = bottomActionSafeInset),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                        ) {
                            Text("Back")
                        }
                        Button(
                            onClick = onConfirmCreate,
                            enabled = !isExecuting,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(actionLabel)
                        }
                    }
                }

                session.options.toolKind == RouteToolKind.MODIFY && onConfirmModify != null -> {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = bottomActionSafeInset),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                        ) {
                            Text("Back")
                        }
                        Button(
                            onClick = onConfirmModify,
                            enabled = !isExecuting,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(actionLabel)
                        }
                    }
                }

                else -> {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.padding(bottom = bottomActionSafeInset),
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
internal fun RouteToolKindSelector(
    selected: RouteToolKind,
    onSelected: (RouteToolKind) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteSegmentButton(
            modifier = Modifier.weight(1f),
            text = RouteToolKind.CREATE.title,
            selected = selected == RouteToolKind.CREATE,
            onClick = { onSelected(RouteToolKind.CREATE) },
        )
        RouteSegmentButton(
            modifier = Modifier.weight(1f),
            text = RouteToolKind.MODIFY.title,
            selected = selected == RouteToolKind.MODIFY,
            onClick = { onSelected(RouteToolKind.MODIFY) },
        )
    }
}

@Composable
internal fun RouteActionSelector(
    options: RouteToolOptions,
    canModifyActiveGpx: Boolean,
    coordinateSeed: LatLong?,
    onOptionsChange: (RouteToolOptions) -> Unit,
    onStartSelection: (RouteToolSession) -> Unit,
    onOpenCoordinateEditor: (RouteToolOptions) -> Unit,
    onOpenPoiSearchDialog: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (options.toolKind) {
            RouteToolKind.CREATE -> {
                createMenuModes().forEach { mode ->
                    val updatedOptions =
                        when (mode) {
                            RouteCreateMode.COORDINATES ->
                                options
                                    .copy(createMode = mode)
                                    .seedCoordinateTarget(coordinateSeed)

                            else -> options.copy(createMode = mode)
                        }
                    RouteActionButton(
                        text = mode.title,
                        icon = createModeIcon(mode),
                        selected = options.createMode == mode,
                        enabled = true,
                        onClick = {
                            when (mode) {
                                RouteCreateMode.CURRENT_TO_HERE,
                                RouteCreateMode.MULTI_POINT_CHAIN,
                                RouteCreateMode.POINT_A_TO_B,
                                -> {
                                    onOptionsChange(updatedOptions)
                                    onStartSelection(RouteToolSession(options = updatedOptions))
                                }

                                RouteCreateMode.SEARCH -> {
                                    onOptionsChange(updatedOptions)
                                    onOpenPoiSearchDialog()
                                }

                                RouteCreateMode.COORDINATES -> {
                                    onOpenCoordinateEditor(updatedOptions)
                                }

                                RouteCreateMode.LOOP_AROUND_HERE -> {
                                    onOptionsChange(updatedOptions)
                                }

                                RouteCreateMode.ACTIVE_GPX_END_TO_HERE -> Unit
                            }
                        },
                    )
                }
            }

            RouteToolKind.MODIFY -> {
                visibleModifyModes().forEach { mode ->
                    RouteActionButton(
                        text = mode.title,
                        icon = null,
                        selected = options.modifyMode == mode,
                        enabled = canModifyActiveGpx,
                        onClick = {
                            val updatedOptions = options.copy(modifyMode = mode)
                            onOptionsChange(updatedOptions)
                            onStartSelection(RouteToolSession(options = updatedOptions))
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun RouteSaveBehaviorSelector(
    selected: RouteSaveBehavior,
    onSelected: (RouteSaveBehavior) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Save mode",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RouteSegmentButton(
                modifier = Modifier.weight(1f),
                text = RouteSaveBehavior.SAVE_AS_NEW.title,
                selected = selected == RouteSaveBehavior.SAVE_AS_NEW,
                onClick = { onSelected(RouteSaveBehavior.SAVE_AS_NEW) },
            )
            RouteSegmentButton(
                modifier = Modifier.weight(1f),
                text = RouteSaveBehavior.REPLACE_CURRENT.title,
                selected = selected == RouteSaveBehavior.REPLACE_CURRENT,
                onClick = { onSelected(RouteSaveBehavior.REPLACE_CURRENT) },
            )
        }
    }
}

@Composable
internal fun LoopTargetModeSelector(
    selected: LoopTargetMode,
    onSelected: (LoopTargetMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Loop by",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RouteSegmentButton(
                modifier = Modifier.weight(1f),
                text = LoopTargetMode.DISTANCE.title,
                selected = selected == LoopTargetMode.DISTANCE,
                onClick = { onSelected(LoopTargetMode.DISTANCE) },
            )
            RouteSegmentButton(
                modifier = Modifier.weight(1f),
                text = LoopTargetMode.TIME.title,
                selected = selected == LoopTargetMode.TIME,
                onClick = { onSelected(LoopTargetMode.TIME) },
            )
        }
    }
}

@Composable
internal fun LoopTargetEditor(
    targetMode: LoopTargetMode,
    distanceKm: Int,
    durationMinutes: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text =
                when (targetMode) {
                    LoopTargetMode.DISTANCE -> "Loop distance"
                    LoopTargetMode.TIME -> "Loop time"
                },
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onDecrease,
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                    ),
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease loop distance")
            }
            Text(
                text =
                    when (targetMode) {
                        LoopTargetMode.DISTANCE -> "$distanceKm km"
                        LoopTargetMode.TIME -> formatLoopDuration(durationMinutes)
                    },
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(
                onClick = onIncrease,
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                    ),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase loop distance")
            }
        }
        if (targetMode == LoopTargetMode.TIME) {
            Text(
                text = "Time is estimated from hiking pace.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun LoopStartModeSelector(
    selected: LoopStartMode,
    onSelected: (LoopStartMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Start from",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RouteSegmentButton(
                modifier = Modifier.weight(1f),
                text = LoopStartMode.CURRENT_LOCATION.title,
                selected = selected == LoopStartMode.CURRENT_LOCATION,
                onClick = { onSelected(LoopStartMode.CURRENT_LOCATION) },
            )
            RouteSegmentButton(
                modifier = Modifier.weight(1f),
                text = LoopStartMode.PICK_ON_MAP.title,
                selected = selected == LoopStartMode.PICK_ON_MAP,
                onClick = { onSelected(LoopStartMode.PICK_ON_MAP) },
            )
        }
    }
}

@Composable
internal fun RouteSettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.10f),
                contentColor = Color.White,
            ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun RouteActionButton(
    text: String,
    icon: ImageVector?,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.10f)
                    },
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        Color.White
                    },
                disabledContainerColor = Color.White.copy(alpha = 0.06f),
                disabledContentColor = Color.White.copy(alpha = 0.40f),
            ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(text = text, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun RouteSegmentButton(
    modifier: Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.10f)
                    },
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        Color.White
                    },
            ),
    ) {
        Text(text = text, textAlign = TextAlign.Center)
    }
}

internal fun RouteStylePreset.next(): RouteStylePreset {
    val entries = RouteStylePreset.entries
    val currentIndex = entries.indexOf(this)
    return entries[(currentIndex + 1) % entries.size]
}

internal fun visibleModifyModes(): List<RouteModifyMode> =
    listOf(
        RouteModifyMode.RESHAPE_ROUTE,
        RouteModifyMode.TRIM_START_TO_HERE,
        RouteModifyMode.TRIM_END_FROM_HERE,
        RouteModifyMode.REVERSE_GPX,
    )

internal enum class CoordinateStep(
    val delta: Double,
    val label: String,
) {
    TENTH(0.1, "0.1"),
    HUNDREDTH(0.01, "0.01"),
    THOUSANDTH(0.001, "0.001"),
    ONE_TEN_THOUSANDTH(0.0001, "0.0001"),
    ;

    fun next(): CoordinateStep {
        val entries = CoordinateStep.entries
        val currentIndex = entries.indexOf(this)
        return entries[(currentIndex + 1) % entries.size]
    }

    fun previous(): CoordinateStep {
        val entries = CoordinateStep.entries
        val currentIndex = entries.indexOf(this)
        return entries[(currentIndex - 1 + entries.size) % entries.size]
    }

    companion object {
        val ONE_THOUSANDTH = THOUSANDTH
    }
}

@Composable
internal fun CoordinateEntryDialog(
    visible: Boolean,
    latitude: Double,
    longitude: Double,
    step: CoordinateStep,
    hasSeed: Boolean,
    onLatitudeChange: (Double) -> Unit,
    onLongitudeChange: (Double) -> Unit,
    onStepChange: (CoordinateStep) -> Unit,
    onUseSeed: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return

    val adaptive = rememberWearAdaptiveSpec()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFF141414).copy(alpha = 0.98f),
                            RoundedCornerShape(adaptive.dialogCornerRadius),
                        ).padding(
                            horizontal = adaptive.dialogHorizontalPadding,
                            vertical = adaptive.dialogVerticalPadding,
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Coordinates",
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
                CoordinateValueEditorRow(
                    label = "Lat",
                    value = latitude,
                    onDecrease = { onLatitudeChange(latitude - step.delta) },
                    onIncrease = { onLatitudeChange(latitude + step.delta) },
                )
                CoordinateValueEditorRow(
                    label = "Lon",
                    value = longitude,
                    onDecrease = { onLongitudeChange(longitude - step.delta) },
                    onIncrease = { onLongitudeChange(longitude + step.delta) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onStepChange(step.previous()) },
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                contentColor = Color.White,
                            ),
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease coordinate step")
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Step", style = MaterialTheme.typography.labelMedium)
                        Text(step.label, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(
                        onClick = { onStepChange(step.next()) },
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                contentColor = Color.White,
                            ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase coordinate step")
                    }
                }
                if (hasSeed) {
                    Button(
                        onClick = onUseSeed,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.10f),
                                contentColor = Color.White,
                            ),
                    ) {
                        Text("Use map center")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoordinateValueEditorRow(
    label: String,
    value: Double,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onDecrease,
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.14f),
                    contentColor = Color.White,
                ),
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(formatCoordinateValue(value), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(
            onClick = onIncrease,
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.14f),
                    contentColor = Color.White,
                ),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase $label")
        }
    }
}

internal fun createMenuModes(): List<RouteCreateMode> =
    listOf(
        RouteCreateMode.CURRENT_TO_HERE,
        RouteCreateMode.POINT_A_TO_B,
        RouteCreateMode.MULTI_POINT_CHAIN,
        RouteCreateMode.SEARCH,
        RouteCreateMode.COORDINATES,
        RouteCreateMode.LOOP_AROUND_HERE,
    )

internal fun createModeIcon(mode: RouteCreateMode): ImageVector =
    when (mode) {
        RouteCreateMode.CURRENT_TO_HERE -> Icons.Default.MyLocation
        RouteCreateMode.MULTI_POINT_CHAIN -> Icons.Default.Polyline
        RouteCreateMode.SEARCH -> Icons.Default.Search
        RouteCreateMode.POINT_A_TO_B -> Icons.AutoMirrored.Filled.CallSplit
        RouteCreateMode.COORDINATES -> Icons.Default.Place
        RouteCreateMode.LOOP_AROUND_HERE -> Icons.Default.Loop
        RouteCreateMode.ACTIVE_GPX_END_TO_HERE -> Icons.Default.Loop
    }

internal fun routeToolsHintText(options: RouteToolOptions): String =
    when {
        options.toolKind == RouteToolKind.CREATE &&
            options.createMode == RouteCreateMode.MULTI_POINT_CHAIN -> {
            "Pick one point at a time and build the route step by step."
        }

        options.toolKind == RouteToolKind.CREATE &&
            options.createMode == RouteCreateMode.SEARCH -> {
            "Search enabled offline POIs and route from current location."
        }

        options.toolKind == RouteToolKind.CREATE &&
            options.createMode == RouteCreateMode.COORDINATES -> {
            "Set the destination coordinates, then create the route."
        }

        options.toolKind == RouteToolKind.CREATE &&
            options.createMode == RouteCreateMode.LOOP_AROUND_HERE -> {
            "Choose target, shape, and where the loop should start."
        }

        options.toolKind == RouteToolKind.MODIFY -> {
            "Tap an action to start editing on the map."
        }

        else -> {
            "Tap an action to start on the map."
        }
    }

internal fun RouteToolOptions.seedCoordinateTarget(seed: LatLong?): RouteToolOptions {
    if (coordinateLatitude != null && coordinateLongitude != null) return this
    val fallback = seed ?: return this
    return copy(
        coordinateLatitude = fallback.latitude,
        coordinateLongitude = fallback.longitude,
    )
}

internal fun RouteToolOptions.coordinatesSummary(): String {
    val latitude = coordinateLatitude
    val longitude = coordinateLongitude
    return if (latitude == null || longitude == null) {
        "Use map center"
    } else {
        formatCoordinateValue(latitude) + ", " + formatCoordinateValue(longitude)
    }
}

internal fun poiSearchSummary(state: PoiSearchUiState): String =
    when {
        state.isLoading -> "Searching..."
        !state.errorMessage.isNullOrBlank() -> state.errorMessage
        state.results.isNotEmpty() -> "${state.results.size} result(s)"
        state.query.isNotBlank() -> "Search again"
        else -> "Enabled .poi files"
    }

internal fun formatCoordinateValue(value: Double): String = String.format("%.5f", value)

internal fun normalizeLongitude(value: Double): Double {
    var normalized = value
    while (normalized < -180.0) normalized += 360.0
    while (normalized > 180.0) normalized -= 360.0
    return normalized
}
