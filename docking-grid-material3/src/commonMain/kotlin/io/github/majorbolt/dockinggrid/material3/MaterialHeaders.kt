package io.github.majorbolt.dockinggrid.material3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.rotate
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.compose.CenterGridScope
import io.github.majorbolt.dockinggrid.compose.DockGroupScope
import io.github.majorbolt.dockinggrid.compose.KeepHeaderVisible
import io.github.majorbolt.dockinggrid.compose.LocalDockingStyle

/**
 * A vertical header: one icon tab per dockable stacked from the top (the title in the tooltip, the selected tab
 * highlighted), and the options button at the bottom. A single dockable also shows its title, rotated, when there is
 * room. The sizes match [io.github.majorbolt.dockinggrid.geometry.GroupHeaderMetrics]' defaults: 36 dp wide, 36 dp per tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun DockGroupScope.MaterialVerticalDockHeader(
    modifier: Modifier = Modifier,
    menuItems: @Composable (dockableId: DockableId, dismissMenu: () -> Unit) -> Unit = { _, _ -> },
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(VERTICAL_HEADER_WIDTH)
            // Lying over the content, only the tabs, the title and the button get a background.
            .then(if (isHeaderOverlay) Modifier else Modifier.background(colorScheme.surfaceVariant)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        group.dockableIds.forEach { dockableId ->
            val isSelected = dockableId == group.selectedDockableId
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(titleOf(dockableId)) } },
                state = rememberTooltipState(),
            ) {
                Box(
                    modifier = Modifier
                        .size(VERTICAL_HEADER_WIDTH)
                        .dockTab(dockableId)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { select(dockableId) })
                        .semantics { contentDescription = titleOf(dockableId) }
                        .then(if (isHeaderOverlay) Modifier.padding(2.dp).overlayPart(isOverlay = true) else Modifier)
                        .background(
                            if (isSelected && group.dockableIds.size > 1) colorScheme.primary.copy(alpha = SELECTED_TAB_ALPHA) else Color.Transparent,
                            SELECTED_TAB_SHAPE,
                        )
                        .testTag(MaterialDockTestTags.verticalTab(dockableId)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasDockableIcons) {
                        DockableIcon(dockableId)
                    } else {
                        Text(text = titleOf(dockableId).take(1), style = MaterialTheme.typography.titleSmall, color = colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (group.dockableIds.size == 1) {
            // The title reads from bottom to top; it is left out when it does not fit.
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Text(
                    text = titleOf(group.selectedDockableId),
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.rotatedQuarterTurn().overlayPart(isHeaderOverlay).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        MaterialDockOptionsButton(menuItems = menuItems, modifier = Modifier.overlayPart(isHeaderOverlay))
    }
}

/** Lays out its content turned by a quarter (reading upwards): the content's width becomes its height. */
private fun Modifier.rotatedQuarterTurn(): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(minWidth = 0, maxWidth = constraints.maxHeight, minHeight = 0, maxHeight = constraints.maxWidth),
        )
        layout(placeable.height, placeable.width) {
            placeable.place(x = (placeable.height - placeable.width) / 2, y = (placeable.width - placeable.height) / 2)
        }
    }
    .rotate(-90f)

/**
 * The header of the center grid: the current arrangement as a button that opens the arrangements, and an
 * options menu with "Lock dividers" (when the host offers it), "Reset dividers" and "Auto-hide header".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun CenterGridScope.MaterialCenterHeader(modifier: Modifier = Modifier) {
    val strings = LocalDockingStyle.current.strings
    val colorScheme = MaterialTheme.colorScheme
    var isMenuExpanded by remember { mutableStateOf(false) }
    // The menu lies outside an auto-hidden header; the header must not slide out and take it along.
    KeepHeaderVisible(isHeld = isMenuExpanded)
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.surfaceContainerHigh)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No caption: the buttons alone, at the right end.
        Spacer(modifier = Modifier.weight(1f))
        MaterialCenterLayoutButton(currentPreset = preset, onSelectPreset = { selectedPreset -> arrangeCenter(selectedPreset) })
        Box {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(strings.options) } },
                state = rememberTooltipState(),
            ) {
                IconButton(
                    onClick = { isMenuExpanded = true },
                    modifier = Modifier.size(28.dp).testTag(MaterialCenterLayoutTestTags.CENTER_OPTIONS),
                ) {
                    Text(text = "⋮", style = MaterialTheme.typography.titleSmall, color = colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
                if (canChangeSplitsResizable) {
                    MaterialDockMenuItem(
                        text = strings.lockCenterDividers,
                        isChecked = !splitsResizable,
                        onClick = {
                            isMenuExpanded = false
                            setSplitsResizable(!splitsResizable)
                        },
                        modifier = Modifier.testTag(MaterialCenterLayoutTestTags.LOCK_DIVIDERS),
                    )
                }
                MaterialDockMenuItem(
                    text = strings.resetCenterDividers,
                    enabled = hasUnevenSplits,
                    onClick = {
                        isMenuExpanded = false
                        resetSplits()
                    },
                    modifier = Modifier.testTag(MaterialCenterLayoutTestTags.RESET_DIVIDERS),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MaterialDockMenuItem(
                    text = strings.autoHideHeader,
                    isChecked = autoHidesHeader,
                    onClick = {
                        isMenuExpanded = false
                        setAutoHidesHeader(!autoHidesHeader)
                    },
                    modifier = Modifier.testTag(MaterialCenterLayoutTestTags.AUTO_HIDE_CENTER_HEADER),
                )
            }
        }
    }
}

private val VERTICAL_HEADER_WIDTH = 36.dp
private const val SELECTED_TAB_ALPHA = 0.2f
private val SELECTED_TAB_SHAPE = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
