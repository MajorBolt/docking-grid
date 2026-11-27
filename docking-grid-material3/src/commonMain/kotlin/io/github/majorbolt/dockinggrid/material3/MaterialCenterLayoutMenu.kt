package io.github.majorbolt.dockinggrid.material3

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.majorbolt.dockinggrid.CenterPreset
import io.github.majorbolt.dockinggrid.compose.LocalDockingStyle
import io.github.majorbolt.dockinggrid.compose.titleOf

/** Test tags of the center layout entries of the dock options menu. */
public object MaterialCenterLayoutTestTags {
    public const val LAYOUT_BUTTON: String = "dock_center_layout_button"
    public const val LOCK_DIVIDERS: String = "dock_center_lock_dividers"
    public const val RESET_DIVIDERS: String = "dock_center_reset_dividers"
    public const val CENTER_OPTIONS: String = "dock_center_options"
    public const val AUTO_HIDE_CENTER_HEADER: String = "dock_center_auto_hide_header"

    public fun preset(preset: CenterPreset): String = "dock_center_preset_${preset.name}"
}

/**
 * A header button that shows the [currentPreset] of the center and opens the arrangements
 * ([MaterialCenterLayoutMenuSection]) on click. Shown by [MaterialDockHeader] in center panels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun MaterialCenterLayoutButton(
    currentPreset: CenterPreset,
    onSelectPreset: (CenterPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalDockingStyle.current.strings
    var isPickerExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text("${strings.centerLayout}: ${strings.titleOf(currentPreset)}") } },
            state = rememberTooltipState(),
        ) {
            IconButton(
                onClick = { isPickerExpanded = true },
                modifier = Modifier
                    .size(32.dp)
                    .semantics { contentDescription = strings.centerLayout }
                    .testTag(MaterialCenterLayoutTestTags.LAYOUT_BUTTON),
            ) {
                CenterPresetIcon(preset = currentPreset, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = isPickerExpanded, onDismissRequest = { isPickerExpanded = false }) {
            MaterialCenterLayoutMenuSection(
                currentPreset = currentPreset,
                onSelectPreset = { preset ->
                    isPickerExpanded = false
                    onSelectPreset(preset)
                },
            )
        }
    }
}

/** The center arrangements: a caption and one icon per [CenterPreset], the [currentPreset] marked. */
@Composable
public fun MaterialCenterLayoutMenuSection(
    currentPreset: CenterPreset?,
    onSelectPreset: (CenterPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalDockingStyle.current.strings
    Column(modifier = modifier) {
        Text(
            text = strings.centerLayout,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        CenterPreset.entries.chunked(PRESETS_PER_ROW).forEach { rowPresets ->
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rowPresets.forEach { preset ->
                    CenterPresetButton(
                        preset = preset,
                        isSelected = preset == currentPreset,
                        title = strings.titleOf(preset),
                        onClick = { onSelectPreset(preset) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CenterPresetButton(
    preset: CenterPreset,
    isSelected: Boolean,
    title: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colorScheme = MaterialTheme.colorScheme
    val background = when {
        isSelected -> colorScheme.primary.copy(alpha = SELECTED_ALPHA)
        isHovered || isFocused -> colorScheme.primary.copy(alpha = HOVER_ALPHA)
        else -> Color.Transparent
    }
    val cellColor = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(title) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 28.dp)
                .clip(BUTTON_SHAPE)
                .background(background)
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = title
                    selected = isSelected
                }
                .testTag(MaterialCenterLayoutTestTags.preset(preset)),
            contentAlignment = Alignment.Center,
        ) {
            CenterPresetIcon(preset = preset, color = cellColor)
        }
    }
}

/** The cells of [preset] as outlined rounded rectangles, 24 × 18 dp. */
@Composable
public fun CenterPresetIcon(preset: CenterPreset, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 24.dp, height = 18.dp)) {
        val gap = 2.dp.toPx()
        val strokeWidth = 1.2.dp.toPx()
        val corner = CornerRadius(1.5.dp.toPx())
        presetCells(preset).forEach { cell ->
            val left = cell.left * size.width + if (cell.left > 0f) gap / 2f else 0f
            val top = cell.top * size.height + if (cell.top > 0f) gap / 2f else 0f
            val right = cell.right * size.width - if (cell.right < 1f) gap / 2f else 0f
            val bottom = cell.bottom * size.height - if (cell.bottom < 1f) gap / 2f else 0f
            drawRoundRect(
                color = color,
                topLeft = Offset(left + strokeWidth / 2f, top + strokeWidth / 2f),
                size = Size(right - left - strokeWidth, bottom - top - strokeWidth),
                cornerRadius = corner,
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

/** One cell of a preset icon, in fractions of the icon. */
private data class PresetCell(val left: Float, val top: Float, val right: Float, val bottom: Float)

private fun presetCells(preset: CenterPreset): List<PresetCell> = when (preset) {
    CenterPreset.SINGLE -> listOf(PresetCell(0f, 0f, 1f, 1f))
    CenterPreset.SIDE_BY_SIDE -> listOf(PresetCell(0f, 0f, 0.5f, 1f), PresetCell(0.5f, 0f, 1f, 1f))
    CenterPreset.STACKED -> listOf(PresetCell(0f, 0f, 1f, 0.5f), PresetCell(0f, 0.5f, 1f, 1f))
    CenterPreset.THREE_FULL_LEFT -> listOf(PresetCell(0f, 0f, 0.5f, 1f), PresetCell(0.5f, 0f, 1f, 0.5f), PresetCell(0.5f, 0.5f, 1f, 1f))
    CenterPreset.THREE_FULL_RIGHT -> listOf(PresetCell(0f, 0f, 0.5f, 0.5f), PresetCell(0f, 0.5f, 0.5f, 1f), PresetCell(0.5f, 0f, 1f, 1f))
    CenterPreset.THREE_FULL_TOP -> listOf(PresetCell(0f, 0f, 1f, 0.5f), PresetCell(0f, 0.5f, 0.5f, 1f), PresetCell(0.5f, 0.5f, 1f, 1f))
    CenterPreset.THREE_FULL_BOTTOM -> listOf(PresetCell(0f, 0f, 0.5f, 0.5f), PresetCell(0.5f, 0f, 1f, 0.5f), PresetCell(0f, 0.5f, 1f, 1f))
    CenterPreset.GRID_2X2 -> listOf(
        PresetCell(0f, 0f, 0.5f, 0.5f),
        PresetCell(0.5f, 0f, 1f, 0.5f),
        PresetCell(0f, 0.5f, 0.5f, 1f),
        PresetCell(0.5f, 0.5f, 1f, 1f),
    )
}

private const val PRESETS_PER_ROW = 4
private const val SELECTED_ALPHA = 0.2f
private const val HOVER_ALPHA = 0.12f
private val BUTTON_SHAPE = RoundedCornerShape(4.dp)
