package io.github.majorbolt.dockinggrid.material3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.majorbolt.dockinggrid.DockableId
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

/**
 * Title bar of the selected dockable with an options menu that can close it. In a center panel the menu also offers
 * the center arrangements ([MaterialCenterLayoutMenuSection]).
 *
 * @param menuItems further entries of the options menu, shown below "Close", for the selected dockable. Use
 *   [MaterialDockMenuItem] for them and call `dismissMenu` when an entry was chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun DockGroupScope.MaterialDockHeader(
    modifier: Modifier = Modifier,
    menuItems: @Composable (dockableId: DockableId, dismissMenu: () -> Unit) -> Unit = { _, _ -> },
) {
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
                MaterialDockMenuItem(
                    text = strings.close,
                    onClick = {
                        isMenuExpanded = false
                        close(selectedDockableId)
                    },
                )
                menuItems(selectedDockableId) { isMenuExpanded = false }
                val currentCenterPreset = centerPreset
                if (currentCenterPreset != null) {
                    MaterialCenterLayoutMenuSection(
                        currentPreset = currentCenterPreset,
                        onSelectPreset = { preset ->
                            isMenuExpanded = false
                            arrangeCenter(preset)
                        },
                    )
                }
            }
        }
    }
}

/** Height of one entry of a dock menu: more compact than the Material default, like desktop menus. */
private val DOCK_MENU_ITEM_HEIGHT: Dp = 28.dp

/** Space between the menu's edge and an entry, so the rounded highlight stands free. */
private val DOCK_MENU_ITEM_INSET: Dp = 4.dp
private val DOCK_MENU_ITEM_SHAPE = RoundedCornerShape(4.dp)
private const val DOCK_MENU_HIGHLIGHT_ALPHA = 0.2f
private const val DOCK_MENU_DISABLED_ALPHA = 0.38f

/**
 * One compact entry of a dock options menu (see [MaterialDockHeader]): inset from the menu's edge, with a rounded
 * highlight in the theme's primary color while hovered or focused.
 */
@Composable
public fun MaterialDockMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colorScheme = MaterialTheme.colorScheme
    val isHighlighted = enabled && (isHovered || isFocused)
    Box(
        modifier = modifier
            .padding(horizontal = DOCK_MENU_ITEM_INSET)
            .widthIn(min = 112.dp)
            .fillMaxWidth()
            .height(DOCK_MENU_ITEM_HEIGHT)
            .clip(DOCK_MENU_ITEM_SHAPE)
            .background(if (isHighlighted) colorScheme.primary.copy(alpha = DOCK_MENU_HIGHLIGHT_ALPHA) else Color.Transparent)
            .hoverable(interactionSource, enabled = enabled)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = DOCK_MENU_DISABLED_ALPHA),
            maxLines = 1,
        )
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
            .then(
                if(style.groupBorderWidth.value > 0)
                    Modifier.border(style.groupBorderWidth, style.colors.groupBorder, style.groupShape)
                else Modifier
            )
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
