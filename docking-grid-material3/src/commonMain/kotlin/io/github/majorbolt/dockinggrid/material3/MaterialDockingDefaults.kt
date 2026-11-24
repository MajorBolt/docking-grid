package io.github.majorbolt.dockinggrid.material3

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.majorbolt.dockinggrid.compose.DockingColors
import io.github.majorbolt.dockinggrid.compose.DockingStrings
import io.github.majorbolt.dockinggrid.compose.DockingStyle

/** Docking style derived from the current [MaterialTheme]. */
public object MaterialDockingDefaults {

    /**
     * The drop target is highlighted in the primary color at 20 % with a primary outline; dividers and outlines use
     * outlineVariant, groups the surface color.
     */
    @Composable
    @ReadOnlyComposable
    public fun colors(): DockingColors {
        val colorScheme = MaterialTheme.colorScheme
        return DockingColors(
            dropTargetFill = colorScheme.primary.copy(alpha = 0.20f),
            dropTargetBorder = colorScheme.primary,
            divider = colorScheme.outlineVariant,
            groupBackground = colorScheme.surface,
            groupBorder = colorScheme.outlineVariant,
            headerBackground = colorScheme.surfaceVariant,
            headerContent = colorScheme.onSurfaceVariant,
            tabIndicator = colorScheme.primary,
            placeholderBackground = colorScheme.surfaceVariant.copy(alpha = 0.3f),
            placeholderContent = colorScheme.onSurfaceVariant,
            dragLabelBackground = colorScheme.primaryContainer,
            dragLabelContent = colorScheme.onPrimaryContainer,
        )
    }

    @Composable
    @ReadOnlyComposable
    public fun style(
        colors: DockingColors = colors(),
        strings: DockingStrings = DockingStrings(),
        groupShape: Shape = RoundedCornerShape(8.dp),
        groupBorderWidth: Dp = 1.dp,
        groupSpacing: Dp = 2.dp,
        contentPadding: Dp = 8.dp,
        dropTargetShape: Shape = RoundedCornerShape(8.dp),
        dropTargetBorderWidth: Dp = 2.dp,
        textStyle: TextStyle  = MaterialTheme.typography.bodyMedium,
    ): DockingStyle = DockingStyle(
        colors = colors,
        strings = strings,
        groupShape = groupShape,
        groupBorderWidth = groupBorderWidth,
        groupSpacing = groupSpacing,
        contentPadding = contentPadding,
        dropTargetShape = dropTargetShape,
        dropTargetBorderWidth = dropTargetBorderWidth,
        textStyle = textStyle,
    )
}
