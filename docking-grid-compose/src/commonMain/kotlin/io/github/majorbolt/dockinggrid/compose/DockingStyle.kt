package io.github.majorbolt.dockinggrid.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.majorbolt.dockinggrid.CenterPreset

/**
 * Appearance of a [DockHost]. The defaults are neutral and need no design system; `docking-grid-material3` derives a
 * style from the Material theme.
 *
 * Sizes that the layout calculation depends on (divider thickness, drop-zone depths) are not part of the style but
 * of [io.github.majorbolt.dockinggrid.geometry.DockingMetrics], so that drawing and hit testing cannot diverge.
 *
 * @property groupShape outline of a tab group and of the placeholders.
 * @property groupBorderWidth width of the outline of a tab group.
 * @property groupSpacing space between a group's outline and the edges of its area.
 * @property contentPadding space around the content of a dockable inside its group.
 * @property dropTargetShape outline of the drop-target highlight.
 * @property dropTargetBorderWidth width of the outline of the drop-target highlight.
 * @property textStyle text of the basic chrome (tabs, header, placeholder, drag label).
 */
@Immutable
public data class DockingStyle(
    val colors: DockingColors = DockingColors(),
    val strings: DockingStrings = DockingStrings(),
    val groupShape: Shape = RoundedCornerShape(8.dp),
    val groupBorderWidth: Dp = 1.dp,
    val groupSpacing: Dp = 2.dp,
    val contentPadding: Dp = 8.dp,
    val dropTargetShape: Shape = RoundedCornerShape(8.dp),
    val dropTargetBorderWidth: Dp = 2.dp,
    val textStyle: TextStyle = TextStyle(fontSize = 14.sp),
)

/**
 * Colors of a [DockHost].
 *
 * @property dropTargetFill fill of the highlighted drop target; keep it translucent, the preview shows through it.
 * @property dropTargetBorder outline of the highlighted drop target.
 * @property tabIndicator marks the selected tab in the basic tab strip.
 */
@Immutable
public data class DockingColors(
    val dropTargetFill: Color = Color(0xFF3D6FE0).copy(alpha = 0.20f),
    val dropTargetBorder: Color = Color(0xFF3D6FE0),
    val divider: Color = Color(0xFFD0D3DA),
    val groupBackground: Color = Color(0xFFFFFFFF),
    val groupBorder: Color = Color(0xFFD0D3DA),
    val headerBackground: Color = Color(0xFFEEF0F4),
    val headerContent: Color = Color(0xFF30333A),
    val tabIndicator: Color = Color(0xFF3D6FE0),
    val placeholderBackground: Color = Color(0x4DE1E3EA),
    val placeholderContent: Color = Color(0xFF5B5F69),
    val dragLabelBackground: Color = Color(0xFFDCE4FB),
    val dragLabelContent: Color = Color(0xFF13285C),
)

/**
 * Texts of a [DockHost].
 *
 * @property emptyCenter shown in the center while it holds no dockable.
 * @property emptyCenterPanel shown in an empty center panel that a [CenterPreset] created.
 * @property centerLayout caption of the center arrangements in a center panel's options menu.
 * @property centerPresetTitles name of each [CenterPreset]; a missing entry shows the preset's name.
 * @property liftedSlot shown in the place of a dragged dockable while the pointer is over no other target.
 * @property close label of the action that closes a dockable.
 * @property options label of the menu with further actions of a dockable, where the chrome has one.
 */
@Immutable
public data class DockingStrings(
    val emptyCenter: String = "Drag a window here",
    val emptyCenterPanel: String = "Drop a window here",
    val centerLayout: String = "Center layout",
    val centerPresetTitles: Map<CenterPreset, String> = DEFAULT_CENTER_PRESET_TITLES,
    val liftedSlot: String = "Drop here to keep it in place",
    val close: String = "Close",
    val options: String = "Options",
)

/** The title of [preset] from [DockingStrings.centerPresetTitles]. */
public fun DockingStrings.titleOf(preset: CenterPreset): String = centerPresetTitles[preset] ?: preset.name

private val DEFAULT_CENTER_PRESET_TITLES: Map<CenterPreset, String> = mapOf(
    CenterPreset.SINGLE to "One panel",
    CenterPreset.SIDE_BY_SIDE to "Two side by side",
    CenterPreset.STACKED to "Two stacked",
    CenterPreset.THREE_FULL_LEFT to "Three, large on the left",
    CenterPreset.THREE_FULL_RIGHT to "Three, large on the right",
    CenterPreset.THREE_FULL_TOP to "Three, large on top",
    CenterPreset.THREE_FULL_BOTTOM to "Three, large at the bottom",
    CenterPreset.GRID_2X2 to "Four in a 2×2 grid",
)

/** The style of the enclosing [DockHost], for slot implementations. */
public val LocalDockingStyle: ProvidableCompositionLocal<DockingStyle> = staticCompositionLocalOf { DockingStyle() }
