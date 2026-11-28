package io.github.majorbolt.dockinggrid.geometry

import io.github.majorbolt.dockinggrid.DockSide
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
    /** Width of a vertical header strip (without [headerTopInsetDp], which also applies at its outer edge). */
    val verticalHeaderWidthDp: Float = 36f,
    /** Height of one icon tab in a vertical header; the tabs are stacked from the strip's top without gaps. */
    val verticalTabHeightDp: Float = 36f,
    /**
     * The tabs of a group with several dockables lie in its header row instead of a tab row above it; the header
     * then is a single row in every group.
     */
    val tabsInHeaderRow: Boolean = false,
)

/**
 * Header area of a group: the tab row (if any) plus the selected dockable's header row.
 *
 * @property tabRects the tabs in order. A group with a single dockable has no tab row; its tab is the
 * virtual tab it would get at the start of a tab row, so that the insertion index matches the preview,
 * which shows a tab row as soon as a second dockable is added. In a vertical header the tabs are stacked.
 * @property isVertical the header is a strip at the group's side instead of a row at its top.
 */
internal data class GroupHeaderGeometry(
    val rect: DockRect,
    val tabRects: List<DockRect>,
    val isVertical: Boolean = false,
)

/**
 * Calculates the header of this group when the group occupies [groupRect]: a row at the top, or a strip at
 * [verticalSide] for a vertical header. An auto-hidden header has the same geometry, because headers are shown
 * while a dockable is dragged.
 */
internal fun TabGroup.headerGeometry(groupRect: DockRect, metrics: GroupHeaderMetrics, verticalSide: DockSide? = null): GroupHeaderGeometry {
    if (verticalSide != null) return verticalHeaderGeometry(groupRect, metrics, verticalSide)
    val hasTabRow = dockableIds.size > 1 && !metrics.tabsInHeaderRow
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

private fun TabGroup.verticalHeaderGeometry(groupRect: DockRect, metrics: GroupHeaderMetrics, side: DockSide): GroupHeaderGeometry {
    val stripWidth = (metrics.headerTopInsetDp + metrics.verticalHeaderWidthDp).coerceIn(0f, groupRect.width)
    val stripRect = if (side == DockSide.RIGHT) {
        groupRect.copy(left = groupRect.right - stripWidth, width = stripWidth)
    } else {
        groupRect.copy(width = stripWidth)
    }
    var tabTop = groupRect.top + metrics.headerTopInsetDp
    val tabRects = dockableIds.map {
        val tabHeight = metrics.verticalTabHeightDp.coerceAtMost((groupRect.bottom - tabTop).coerceAtLeast(0f))
        DockRect(left = stripRect.left, top = tabTop, width = stripRect.width, height = tabHeight).also { tabTop += metrics.verticalTabHeightDp }
    }
    return GroupHeaderGeometry(rect = stripRect, tabRects = tabRects, isVertical = true)
}

/** The part of [groupRect] below a header row or beside a header strip. */
internal fun GroupHeaderGeometry.bodyOf(groupRect: DockRect): DockRect = when {
    !isVertical -> groupRect.copy(top = rect.bottom, height = (groupRect.bottom - rect.bottom).coerceAtLeast(0f))
    rect.left > groupRect.left -> groupRect.copy(width = (rect.left - groupRect.left).coerceAtLeast(0f))
    else -> groupRect.copy(left = rect.right, width = (groupRect.right - rect.right).coerceAtLeast(0f))
}

/** Insertion index for a pointer at [verticalPosition] over a vertical header: the tabs whose center lies above it. */
public fun tabInsertionIndexVertical(tabRects: List<DockRect>, verticalPosition: Float): Int =
    tabRects.count { rect -> rect.top + rect.height / 2f < verticalPosition }

/** Insertion index for a pointer at [horizontalPosition] over a header: the number of tabs whose center lies before it. */
public fun tabInsertionIndex(tabRects: List<DockRect>, horizontalPosition: Float): Int =
    tabRects.count { rect -> rect.left + rect.width / 2f < horizontalPosition }
