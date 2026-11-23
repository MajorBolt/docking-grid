package io.github.majorbolt.dockinggrid.material3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.majorbolt.dockinggrid.compose.DockGroupScope
import io.github.majorbolt.dockinggrid.compose.LocalDockingStyle

/** Material scrollable tab row with a secondary indicator under the selected tab. */
@Composable
public fun DockGroupScope.MaterialDockTabStrip(modifier: Modifier = Modifier) {
    val selectedTabIndex = group.dockableIds.indexOf(group.selectedDockableId).coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        edgePadding = 4.dp,
        modifier = modifier.fillMaxWidth(),
        indicator = { tabPositions ->
            if (selectedTabIndex in tabPositions.indices) {
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]))
            }
        },
    ) {
        group.dockableIds.forEach { dockableId ->
            Tab(
                selected = dockableId == group.selectedDockableId,
                onClick = { select(dockableId) },
                text = { Text(titleOf(dockableId)) },
                modifier = Modifier.dockTab(dockableId),
            )
        }
    }
}

/** Title bar of the selected dockable with an options menu that can close it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun DockGroupScope.MaterialDockHeader(modifier: Modifier = Modifier) {
    val strings = LocalDockingStyle.current.strings
    val selectedDockableId = group.selectedDockableId
    var isMenuExpanded by remember { mutableStateOf(false) }
    val tooltipState = rememberTooltipState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = titleOf(selectedDockableId),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(strings.options) } },
                state = tooltipState,
            ) {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Text(
                        text = "⋮",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(strings.close) },
                    onClick = {
                        isMenuExpanded = false
                        close(selectedDockableId)
                    },
                )
            }
        }
    }
}

/** Placeholder of an empty area that stays a drop target: the empty center or a dragged dockable's slot. */
@Composable
public fun MaterialDockPlaceholder(text: String, modifier: Modifier = Modifier) {
    val style = LocalDockingStyle.current
    Box(
        modifier = modifier
            .padding(style.groupSpacing)
            .clip(style.groupShape)
            .border(style.groupBorderWidth, style.colors.groupBorder, style.groupShape)
            .background(style.colors.placeholderBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = style.colors.placeholderContent,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** The label that follows the pointer while a dockable is dragged. */
@Composable
public fun MaterialDockDragLabel(title: String, modifier: Modifier = Modifier) {
    val colors = LocalDockingStyle.current.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = colors.dragLabelBackground,
        contentColor = colors.dragLabelContent,
        shadowElevation = 4.dp,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
