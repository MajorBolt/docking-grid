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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    public fun preset(preset: CenterPreset): String = "dock_center_preset_${preset.name}"
}

/**
 * The center arrangements of a center panel's options menu: a caption and one icon per [CenterPreset], the
 * [currentPreset] marked. Shown by [MaterialDockHeader] for center panels.
 */
@Composable
public fun MaterialCenterLayoutMenuSection(
    currentPreset: CenterPreset?,
    onSelectPreset: (CenterPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalDockingStyle.current.strings
    Column(modifier = modifier) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
            Canvas(modifier = Modifier.size(width = 24.dp, height = 18.dp)) {
                val gap = 2.dp.toPx()
                val strokeWidth = 1.2.dp.toPx()
                val corner = CornerRadius(1.5.dp.toPx())
                presetCells(preset).forEach { cell ->
                    val left = cell.left * size.width + if (cell.left > 0f) gap / 2f else 0f
                    val top = cell.top * size.height + if (cell.top > 0f) gap / 2f else 0f
                    val right = cell.right * size.width - if (cell.right < 1f) gap / 2f else 0f
                    val bottom = cell.bottom * size.height - if (cell.bottom < 1f) gap / 2f else 0f
                    drawRoundRect(
                        color = cellColor,
                        topLeft = Offset(left + strokeWidth / 2f, top + strokeWidth / 2f),
                        size = Size(right - left - strokeWidth, bottom - top - strokeWidth),
                        cornerRadius = corner,
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
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
