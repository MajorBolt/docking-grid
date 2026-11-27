package io.github.majorbolt.dockinggrid.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Default chrome of DockHost, built from Compose foundation only and styled by LocalDockingStyle.

/** A horizontally scrollable row of tabs; the selected tab is underlined in [DockingColors.tabIndicator]. */
@Composable
public fun DockGroupScope.BasicDockTabStrip(modifier: Modifier = Modifier) {
    val style = LocalDockingStyle.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(style.colors.headerBackground)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
    ) {
        group.dockableIds.forEach { dockableId ->
            val isSelected = dockableId == group.selectedDockableId
            BasicText(
                text = titleOf(dockableId),
                style = style.textStyle.copy(color = style.colors.headerContent),
                modifier = Modifier
                    .dockTab(dockableId)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { select(dockableId) })
                    .drawBehind {
                        if (isSelected) {
                            val indicatorHeight = 2.dp.toPx()
                            drawLine(
                                color = style.colors.tabIndicator,
                                start = Offset(0f, size.height - indicatorHeight / 2f),
                                end = Offset(size.width, size.height - indicatorHeight / 2f),
                                strokeWidth = indicatorHeight,
                            )
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** Title of the selected dockable and a close button. */
@Composable
public fun DockGroupScope.BasicDockHeader(modifier: Modifier = Modifier) {
    val style = LocalDockingStyle.current
    val selectedDockableId = group.selectedDockableId
    val textStyle = style.textStyle.copy(color = style.colors.headerContent)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(style.colors.headerBackground)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = titleOf(selectedDockableId), style = textStyle)
        BasicText(
            text = "×",
            style = textStyle,
            modifier = Modifier
                .clip(style.groupShape)
                .clickable(role = Role.Button, onClick = { close(selectedDockableId) })
                .semantics { contentDescription = style.strings.close }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * A vertical header: one tab per dockable stacked from the top, showing its icon when the host has icons and the
 * first letter of its title otherwise. Sizes match [io.github.majorbolt.dockinggrid.geometry.GroupHeaderMetrics]'
 * defaults (36 dp wide, 36 dp per tab).
 */
@Composable
public fun DockGroupScope.BasicDockVerticalHeader(modifier: Modifier = Modifier) {
    val style = LocalDockingStyle.current
    Column(modifier = modifier.fillMaxHeight().width(36.dp).background(style.colors.headerBackground)) {
        group.dockableIds.forEach { dockableId ->
            val isSelected = dockableId == group.selectedDockableId
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .dockTab(dockableId)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { select(dockableId) })
                    .semantics { contentDescription = titleOf(dockableId) }
                    .background(if (isSelected) style.colors.tabIndicator.copy(alpha = 0.2f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (hasDockableIcons) {
                    DockableIcon(dockableId)
                } else {
                    BasicText(text = titleOf(dockableId).take(1), style = style.textStyle.copy(color = style.colors.headerContent))
                }
            }
        }
    }
}

/** The header of the center grid: its caption. */
@Composable
public fun CenterGridScope.BasicCenterHeader(modifier: Modifier = Modifier) {
    val style = LocalDockingStyle.current
    Row(
        modifier = modifier.fillMaxSize().background(style.colors.headerBackground).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = style.strings.center, style = style.textStyle.copy(color = style.colors.headerContent))
    }
}

/** Placeholder of an empty area that stays a drop target: the empty center or a dragged dockable's slot. */
@Composable
public fun BasicDockPlaceholder(text: String, modifier: Modifier = Modifier) {
    val style = LocalDockingStyle.current
    Box(
        modifier = modifier
            .padding(style.groupSpacing)
            .clip(style.groupShape)
            .then(
                if(style.groupBorderWidth.value > 0)
                    Modifier.border(style.groupBorderWidth, style.colors.groupBorder, style.groupShape)
                else Modifier
            )
            .background(style.colors.placeholderBackground),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = style.textStyle.copy(color = style.colors.placeholderContent),
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** The label that follows the pointer while a dockable is dragged. */
@Composable
public fun BasicDockDragLabel(title: String, modifier: Modifier = Modifier) {
    val style = LocalDockingStyle.current
    BasicText(
        text = title,
        style = style.textStyle.copy(color = style.colors.dragLabelContent),
        modifier = modifier
            .shadow(4.dp, style.groupShape)
            .background(style.colors.dragLabelBackground, style.groupShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
