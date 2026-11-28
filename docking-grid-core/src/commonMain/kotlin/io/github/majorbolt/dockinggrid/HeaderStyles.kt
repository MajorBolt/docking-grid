package io.github.majorbolt.dockinggrid

/**
 * Whether the header of the group [groupId] is hidden until hovered: its own [GroupHeaderStyle.autoHides], or, for a
 * center panel while the grid has a header that auto-hides, always when [gridHidesPanelHeaders] is set (the grid's
 * choice then takes priority; the panel's own choice applies again when the grid's header is shown).
 */
public fun DockLayout.isHeaderAutoHidden(groupId: String, gridHidesPanelHeaders: Boolean): Boolean {
    val group = findGroup(groupId) ?: return false
    return group.headerStyle.autoHides || isHeaderAutoHideForcedByGrid(groupId, gridHidesPanelHeaders)
}

/** `true` when the grid's auto-hide hides the header of the center panel group [groupId]; see [isHeaderAutoHidden]. */
public fun DockLayout.isHeaderAutoHideForcedByGrid(groupId: String, gridHidesPanelHeaders: Boolean): Boolean =
    gridHidesPanelHeaders && center.hasHeader && center.autoHidesHeader && center.findPanelOf(groupId) != null

/**
 * The side a vertical header of the group [groupId] is drawn on, its outer side: [DockSide.RIGHT] for groups in a
 * RIGHT lane, for the rightmost of several groups in a TOP or BOTTOM lane, and for center panels in the right column
 * of the grid; [DockSide.LEFT] otherwise.
 */
public fun DockLayout.verticalHeaderSide(groupId: String): DockSide {
    DockFrameLevel.entries.forEach { frameLevel ->
        DockSide.entries.forEach { side ->
            val lane = frame(frameLevel).lanes(side).firstOrNull { lane -> lane.groups.any { group -> group.id == groupId } }
            if (lane != null) {
                val isRightmostInRow = !side.hasColumnLanes && lane.groups.size > 1 && lane.groups.last().id == groupId
                return if (side == DockSide.RIGHT || isRightmostInRow) DockSide.RIGHT else DockSide.LEFT
            }
        }
    }
    center.sections.forEachIndexed { sectionIndex, section ->
        val panelIndex = section.panels.indexOfFirst { panel -> panel.group?.id == groupId }
        if (panelIndex >= 0) {
            // Sections side by side: the second section is the right column; stacked: the second panel of a section.
            val isRightColumn = when (center.axis) {
                CenterAxis.HORIZONTAL -> sectionIndex == 1 && center.sections.size == 2
                CenterAxis.VERTICAL -> panelIndex == 1
            }
            return if (isRightColumn) DockSide.RIGHT else DockSide.LEFT
        }
    }
    return DockSide.LEFT
}
