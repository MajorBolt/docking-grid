package io.github.majorbolt.dockinggrid.geometry

import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.TabGroup

/**
 * Measured sizes of the header UI of a tab group, in dp. The UI reports them; they depend only on the
 * dockables and the theme, not on which layout is rendered, so header geometry can be calculated for
 * any layout (for example the lifted layout of a drag) without measuring it.
 */
public data class GroupHeaderMetrics(
    /** Space between the group's top edge and its first header row (the container's padding). */
    val headerTopInsetDp: Float = 0f,
    /** Height of the tab row shown above the header when a group has more than one dockable. */
    val tabRowHeightDp: Float = 48f,
    /** Height of the header row of the selected dockable. */
    val headerRowHeightDp: Float = 36f,
    /** Space before the first tab. */
    val tabStartPaddingDp: Float = 4f,
    /** Width of each dockable's tab; tabs are laid out next to each other without gaps. */
    val tabWidthsDp: Map<DockableId, Float> = emptyMap(),
    /** Width assumed for a tab that has not been measured yet. */
    val fallbackTabWidthDp: Float = 90f,
)

/**
 * Header area of a group: the tab row (if any) plus the selected dockable's header row.
 *
 * @property tabRects the tabs in order. A group with a single dockable has no tab row; its tab is the
 * virtual tab it would get at the start of a tab row, so that the insertion index matches the preview,
 * which shows a tab row as soon as a second dockable is added.
 */
internal data class GroupHeaderGeometry(
    val rect: DockRect,
    val tabRects: List<DockRect>,
)

/** Calculates the header of this group when the group occupies [groupRect]. */
internal fun TabGroup.headerGeometry(groupRect: DockRect, metrics: GroupHeaderMetrics): GroupHeaderGeometry {
    val hasTabRow = dockableIds.size > 1
    val tabRowHeight = if (hasTabRow) metrics.tabRowHeightDp else 0f
    val headerHeight = (metrics.headerTopInsetDp + tabRowHeight + metrics.headerRowHeightDp).coerceIn(0f, groupRect.height)
    val tabTop = groupRect.top + metrics.headerTopInsetDp
    val headerRect = groupRect.copy(height = headerHeight)
    val tabHeight = (if (hasTabRow) tabRowHeight else metrics.headerRowHeightDp)
        .coerceAtMost(groupRect.bottom - tabTop)
        .coerceAtLeast(0f)
    var tabLeft = groupRect.left + metrics.tabStartPaddingDp
    val tabRects = dockableIds.map { dockableId ->
        val tabWidth = metrics.tabWidthsDp[dockableId] ?: metrics.fallbackTabWidthDp
        DockRect(left = tabLeft, top = tabTop, width = tabWidth, height = tabHeight).also { tabLeft += tabWidth }
    }
    return GroupHeaderGeometry(rect = headerRect, tabRects = tabRects)
}

/** Insertion index for a pointer at [horizontalPosition] over a header: the number of tabs whose center lies before it. */
public fun tabInsertionIndex(tabRects: List<DockRect>, horizontalPosition: Float): Int =
    tabRects.count { rect -> rect.left + rect.width / 2f < horizontalPosition }
