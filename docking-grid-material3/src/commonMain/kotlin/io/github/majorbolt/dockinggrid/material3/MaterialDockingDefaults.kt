package io.github.majorbolt.dockinggrid.material3

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
    ): DockingStyle = DockingStyle(
        colors = colors,
        strings = strings,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}
