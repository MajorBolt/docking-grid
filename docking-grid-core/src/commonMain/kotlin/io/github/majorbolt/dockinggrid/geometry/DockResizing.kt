package io.github.majorbolt.dockinggrid.geometry

import io.github.majorbolt.dockinggrid.DockLayout
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockOperationResult
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockableCatalog
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.applyOperation
import kotlin.math.abs

/**
 * Computes the resize operation for dragging [divider] by [dragDistanceDp] from its position in [startLayout].
 *
 * Lane extents are clamped so that the lane never goes below its minimum size and never grows into
 * space that would squeeze another lane's extent or violate any minimum size; the center absorbs the change.
 * Group boundaries and center splits are clamped to the minimum sizes of their two neighbouring parts.
 *
 * @param dragDistanceDp positive values move the divider right or down.
 * @return the operation to apply, or `null` when the divider target no longer exists or is not resizable.
 */
public fun resizeOperationFor(
    startLayout: DockLayout,
    divider: DockDivider,
    dragDistanceDp: Float,
    contentSize: DockSize,
    catalog: DockableCatalog,
    metrics: DockingMetrics = DockingMetrics(),
): DockLayoutOperation? = when (val target = divider.target) {
    is DividerTarget.LaneExtent -> laneResizeOperation(startLayout, target, dragDistanceDp, contentSize, catalog, metrics)
    is DividerTarget.GroupBoundary -> groupBoundaryOperation(startLayout, target, dragDistanceDp, contentSize, catalog, metrics)
    is DividerTarget.CenterSplit -> centerSplitOperation(startLayout, target, dragDistanceDp, contentSize, catalog, metrics)
}

private fun centerSplitOperation(
    startLayout: DockLayout,
    target: DividerTarget.CenterSplit,
    dragDistanceDp: Float,
    contentSize: DockSize,
    catalog: DockableCatalog,
    metrics: DockingMetrics,
): DockLayoutOperation.ResizeCenterSplit? {
    if (!metrics.centerSplitsResizable) return null
    val (firstMinimum, secondMinimum) = startLayout.centerSplitMinimumLengths(target, catalog, metrics) ?: return null
    val geometry = calculateDockGeometry(startLayout, contentSize, catalog, metrics)
    val divider = geometry.dividers.firstOrNull { divider -> divider.target == target } ?: return null
    val grid = startLayout.center
    val sectionIndex = target.sectionIndex
    // The parent is the whole center for the split between sections, otherwise the section: the union of its panels.
    val parentRect = if (sectionIndex == null) {
        geometry.centerRect
    } else {
        val panelRects = grid.sections[sectionIndex].panels.map { panel -> geometry.centerPanelRects[panel.id] ?: return null }
        DockRect(
            left = panelRects.minOf { rect -> rect.left },
            top = panelRects.minOf { rect -> rect.top },
            width = panelRects.maxOf { rect -> rect.right } - panelRects.minOf { rect -> rect.left },
            height = panelRects.maxOf { rect -> rect.bottom } - panelRects.minOf { rect -> rect.top },
        )
    }
    val alongWidth = divider.movesHorizontally
    val availableLength = (if (alongWidth) parentRect.width else parentRect.height) - metrics.dividerThicknessDp
    if (availableLength <= 0f) return null
    val firstLength = if (alongWidth) divider.rect.left - parentRect.left else divider.rect.top - parentRect.top
    val newFirstLength = if (firstMinimum + secondMinimum > availableLength) {
        firstLength
    } else {
        (firstLength + dragDistanceDp).coerceIn(firstMinimum, availableLength - secondMinimum)
    }
    return DockLayoutOperation.ResizeCenterSplit(sectionIndex, newFirstLength / availableLength)
}

private fun laneResizeOperation(
    startLayout: DockLayout,
    target: DividerTarget.LaneExtent,
    dragDistanceDp: Float,
    contentSize: DockSize,
    catalog: DockableCatalog,
    metrics: DockingMetrics,
): DockLayoutOperation.ResizeLane? {
    val location = startLayout.findLane(target.laneId) ?: return null
    val lane = startLayout.frame(location.frameLevel).lanes(location.side)[location.laneIndex]
    val isColumn = location.side.hasColumnLanes
    val contentDimension = if (isColumn) contentSize.widthDp else contentSize.heightDp
    if (contentDimension <= 0f) return null

    val startGeometry = calculateDockGeometry(startLayout, contentSize, catalog, metrics)
    val startRect = startGeometry.laneRects[lane.id] ?: return null
    val startExtent = if (isColumn) startRect.width else startRect.height
    // LEFT and TOP grow when the divider moves right/down; RIGHT and BOTTOM grow when it moves left/up.
    val growthDirection = if (location.side == DockSide.LEFT || location.side == DockSide.TOP) 1f else -1f
    val minimumExtent = lane.groups.maxOf { group -> if (isColumn) catalog.minimumWidthDp(group) else catalog.minimumHeightDp(group) }.toFloat()
    val requestedExtent = (startExtent + growthDirection * dragDistanceDp).coerceAtLeast(minimumExtent)

    fun operationFor(extentDp: Float) = DockLayoutOperation.ResizeLane(lane.id, extentDp / contentDimension)

    fun isAcceptable(extentDp: Float): Boolean {
        val candidate = startLayout.applyResize(operationFor(extentDp)) ?: return false
        if (!candidate.fitsInto(contentSize, catalog, metrics)) return false
        val candidateGeometry = calculateDockGeometry(candidate, contentSize, catalog, metrics)
        val candidateRect = candidateGeometry.laneRects[lane.id] ?: return false
        val renderedExtent = if (isColumn) candidateRect.width else candidateRect.height
        if (abs(renderedExtent - extentDp) > EXTENT_TOLERANCE_DP) return false
        // No other lane's extent (column width, row height) may be squeezed below its size before the drag.
        // Its length along its side legitimately changes, e.g. an inner row gets narrower when an outer column
        // grows; the minimum lengths of its groups are already guaranteed by the fit check above.
        return startGeometry.laneRects.all { (otherLaneId, otherStartRect) ->
            if (otherLaneId == lane.id) return@all true
            val otherCandidateRect = candidateGeometry.laneRects[otherLaneId] ?: return@all false
            val otherIsColumn = startLayout.findLane(otherLaneId)?.side?.hasColumnLanes ?: return@all false
            if (otherIsColumn) {
                otherCandidateRect.width >= otherStartRect.width - UNRELATED_LANE_TOLERANCE_DP
            } else {
                otherCandidateRect.height >= otherStartRect.height - UNRELATED_LANE_TOLERANCE_DP
            }
        }
    }

    if (requestedExtent <= startExtent || isAcceptable(requestedExtent)) {
        return operationFor(requestedExtent)
    }
    // Growing is limited: search the largest acceptable extent between the start and the request.
    var acceptableExtent = startExtent
    var rejectedExtent = requestedExtent
    repeat(BINARY_SEARCH_STEPS) {
        val middleExtent = (acceptableExtent + rejectedExtent) / 2f
        if (isAcceptable(middleExtent)) acceptableExtent = middleExtent else rejectedExtent = middleExtent
    }
    return operationFor(acceptableExtent)
}

private fun groupBoundaryOperation(
    startLayout: DockLayout,
    target: DividerTarget.GroupBoundary,
    dragDistanceDp: Float,
    contentSize: DockSize,
    catalog: DockableCatalog,
    metrics: DockingMetrics,
): DockLayoutOperation.ResizeGroups? {
    val location = startLayout.findLane(target.laneId) ?: return null
    val lane = startLayout.frame(location.frameLevel).lanes(location.side)[location.laneIndex]
    if (target.boundaryIndex !in 1 until lane.groups.size) return null
    val isColumn = location.side.hasColumnLanes
    val geometry = calculateDockGeometry(startLayout, contentSize, catalog, metrics)
    val groupLengths = lane.groups.map { group ->
        val rect = geometry.groupRects[group.id] ?: return null
        if (isColumn) rect.height else rect.width
    }
    val leadingIndex = target.boundaryIndex - 1
    val trailingIndex = target.boundaryIndex
    val leadingGroup = lane.groups[leadingIndex]
    val trailingGroup = lane.groups[trailingIndex]
    val leadingMinimum = (if (isColumn) catalog.minimumHeightDp(leadingGroup) else catalog.minimumWidthDp(leadingGroup)).toFloat()
    val trailingMinimum = (if (isColumn) catalog.minimumHeightDp(trailingGroup) else catalog.minimumWidthDp(trailingGroup)).toFloat()
    val pairLength = groupLengths[leadingIndex] + groupLengths[trailingIndex]
    if (pairLength <= 0f) return null

    val newLeadingLength = if (leadingMinimum + trailingMinimum > pairLength) {
        groupLengths[leadingIndex]
    } else {
        (groupLengths[leadingIndex] + dragDistanceDp).coerceIn(leadingMinimum, pairLength - trailingMinimum)
    }
    // Only the two neighbours change; their combined share stays, so all other groups keep their exact fractions.
    // Fractions cover the full lane length including half of each adjacent divider (see adjacentDividerCount).
    val laneLength = groupLengths.sum() + (lane.groups.size - 1) * metrics.dividerThicknessDp
    if (laneLength <= 0f) return null
    val pairFraction = leadingGroup.sizeFraction + trailingGroup.sizeFraction
    val leadingDividerShare = adjacentDividerCount(leadingIndex, lane.groups.size) * metrics.dividerThicknessDp / 2f
    val newLeadingFraction = ((newLeadingLength + leadingDividerShare) / laneLength)
        .coerceIn(MINIMUM_GROUP_FRACTION, pairFraction - MINIMUM_GROUP_FRACTION)
    val newFractions = lane.groups.map { group -> group.sizeFraction }.toMutableList().apply {
        set(leadingIndex, newLeadingFraction)
        set(trailingIndex, pairFraction - newLeadingFraction)
    }
    return DockLayoutOperation.ResizeGroups(lane.id, newFractions)
}

private fun DockLayout.applyResize(operation: DockLayoutOperation): DockLayout? =
    // Resizing is independent of orientation and capabilities.
    (applyOperation(operation, LayoutOrientation.LANDSCAPE, DockingCapabilities.Desktop) as? DockOperationResult.Applied)?.layout

private const val EXTENT_TOLERANCE_DP = 0.5f

/** Unrelated lanes must keep their size; this only absorbs floating-point noise. */
private const val UNRELATED_LANE_TOLERANCE_DP = 0.001f
private const val BINARY_SEARCH_STEPS = 24
private const val MINIMUM_GROUP_FRACTION = 0.0001f
