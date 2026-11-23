package io.github.majorbolt.dockinggrid.geometry

import io.github.majorbolt.dockinggrid.DockFrame
import io.github.majorbolt.dockinggrid.DockFrameLevel
import io.github.majorbolt.dockinggrid.DockLane
import io.github.majorbolt.dockinggrid.DockLayout
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockOperationResult
import io.github.majorbolt.dockinggrid.DockProfile
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockableCatalog
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.TabGroup
import io.github.majorbolt.dockinggrid.applyOperation
import io.github.majorbolt.dockinggrid.removingDockableKeepingContainers

/** A place a dragged dockable can be dropped, independent of which dockable is dragged. */
public sealed interface DropTarget {
    /** Add as a tab; [tabIndex] `null` appends at the end. */
    public data class Tab(val groupId: String, val tabIndex: Int?) : DropTarget

    /** Splits [splitGroupId]: the new group is inserted at [groupIndex] of the lane and takes half of its space. */
    public data class NewGroup(val laneId: String, val groupIndex: Int, val splitGroupId: String) : DropTarget

    public data class NewLane(val frameLevel: DockFrameLevel, val side: DockSide, val laneIndex: Int) : DropTarget

    /** The empty center placeholder. */
    public data object Center : DropTarget

    /** The dragged dockable's own lifted slot: dropping there changes nothing. */
    public data object Origin : DropTarget
}

/** The operation for dropping [dockableId] here; `null` for [DropTarget.Origin], which changes nothing. */
internal fun DropTarget.toOperation(dockableId: DockableId, layout: DockLayout): DockLayoutOperation? = when (this) {
    is DropTarget.Tab -> DockLayoutOperation.MoveToGroup(
        dockableId = dockableId,
        targetGroupId = groupId,
        tabIndex = tabIndex ?: (layout.findGroup(groupId)?.dockableIds?.size ?: 0),
    )
    is DropTarget.NewGroup -> DockLayoutOperation.MoveToNewGroup(dockableId, laneId, groupIndex, splitGroupId)
    is DropTarget.NewLane -> DockLayoutOperation.MoveToNewLane(dockableId, frameLevel, side, laneIndex)
    DropTarget.Center -> DockLayoutOperation.MoveToCenter(dockableId)
    DropTarget.Origin -> null
}

/** Detection band of a side that currently has no lanes. */
internal data class EmptySideBand(
    val rect: DockRect,
    val target: DropTarget.NewLane,
)

/**
 * Bands along every empty side, so empty docks are detectable although nothing is rendered there.
 * Outer bands lie at the window edge; when the inner side on the same edge is empty too, its band lies
 * directly inside the outer band. TOP/BOTTOM come first because they own the corners.
 */
internal fun emptySideBands(
    layout: DockLayout,
    geometry: DockGeometry,
    capabilities: DockingCapabilities,
    orientation: LayoutOrientation,
    metrics: DockingMetrics = DockingMetrics(),
): List<EmptySideBand> {
    val bandDepth = metrics.emptySideBandDp
    val innerFrameRect = geometry.innerFrameRect
    val centerRect = geometry.centerRect
    val bands = mutableListOf<EmptySideBand>()
    val orderedSides = listOf(DockSide.TOP, DockSide.BOTTOM, DockSide.LEFT, DockSide.RIGHT)

    fun isSideAvailable(frameLevel: DockFrameLevel, side: DockSide): Boolean =
        capabilities.isFrameAllowed(frameLevel) &&
            layout.frame(frameLevel).lanes(side).isEmpty() &&
            (capabilities.maximumLanes(orientation, side) ?: 1) >= 1

    orderedSides.filter { side -> isSideAvailable(DockFrameLevel.OUTER, side) }.forEach { side ->
        val rect = when (side) {
            DockSide.TOP -> DockRect(0f, innerFrameRect.top, geometry.contentSize.widthDp, bandDepth)
            DockSide.BOTTOM -> DockRect(0f, innerFrameRect.bottom - bandDepth, geometry.contentSize.widthDp, bandDepth)
            DockSide.LEFT -> DockRect(innerFrameRect.left, innerFrameRect.top, bandDepth, innerFrameRect.height)
            DockSide.RIGHT -> DockRect(innerFrameRect.right - bandDepth, innerFrameRect.top, bandDepth, innerFrameRect.height)
        }
        bands += EmptySideBand(rect, DropTarget.NewLane(DockFrameLevel.OUTER, side, laneIndex = 0))
    }

    orderedSides.filter { side -> isSideAvailable(DockFrameLevel.INNER, side) }.forEach { side ->
        // The inner edge coincides with the outer edge exactly when the outer side is empty as well.
        val offset = if (layout.outerFrame.lanes(side).isEmpty()) bandDepth else 0f
        val rect = when (side) {
            DockSide.TOP -> DockRect(innerFrameRect.left, centerRect.top + offset, innerFrameRect.width, bandDepth)
            DockSide.BOTTOM -> DockRect(innerFrameRect.left, centerRect.bottom - offset - bandDepth, innerFrameRect.width, bandDepth)
            DockSide.LEFT -> DockRect(centerRect.left + offset, centerRect.top, bandDepth, centerRect.height)
            DockSide.RIGHT -> DockRect(centerRect.right - offset - bandDepth, centerRect.top, bandDepth, centerRect.height)
        }
        bands += EmptySideBand(rect, DropTarget.NewLane(DockFrameLevel.INNER, side, laneIndex = 0))
    }
    return bands
}

/**
 * Resolves the drop target under the pointer in [layout] with its [geometry]. During a drag [layout] is the
 * lifted layout: the committed layout without the dragged dockable, whose emptied group (and lane) is kept
 * as a placeholder, so that nothing moves and [geometry] is the committed, rendered geometry. Target indices
 * are converted to the layout without those placeholders, which is what the move operations act on. Priority:
 * 1. the window-edge part ([DockingMetrics.windowEdgeDropZoneDp]) of an empty outer side's band;
 * 1a. the dragged dockable's own placeholder: [DropTarget.Origin];
 * 2. the header of the group under the pointer: add, reorder or merge as a tab;
 * 3. the rest of the empty-side bands;
 * 4. the group body: in a lane, bands along the two edges across the stacking axis create a new lane
 *    ([DockingMetrics.laneInsertionBandShare]) and the rest splits the lane by halves; the center body is
 *    divided along its diagonals into four triangles, each creating an inner dock on that side;
 * 5. the empty center placeholder as a whole.
 * Returns `null` over dividers or outside the content.
 */
internal fun resolveDropTarget(
    layout: DockLayout,
    geometry: DockGeometry,
    horizontalPosition: Float,
    verticalPosition: Float,
    capabilities: DockingCapabilities,
    orientation: LayoutOrientation,
    metrics: DockingMetrics = DockingMetrics(),
    headerMetrics: GroupHeaderMetrics = GroupHeaderMetrics(),
): DropTarget? {
    val bands = emptySideBands(layout, geometry, capabilities, orientation, metrics)
    bands.firstOrNull { band ->
        band.target.frameLevel == DockFrameLevel.OUTER &&
            band.windowEdgePart(metrics.windowEdgeDropZoneDp).contains(horizontalPosition, verticalPosition)
    }?.let { band -> return band.target }

    val groupHit = groupAt(layout, geometry, horizontalPosition, verticalPosition)
    if (groupHit?.group?.dockableIds?.isEmpty() == true) return DropTarget.Origin
    val hitGroup = groupHit?.group
    val header = hitGroup?.headerGeometry(groupHit.rect, headerMetrics)
    if (hitGroup != null && header != null && header.rect.contains(horizontalPosition, verticalPosition)) {
        return DropTarget.Tab(hitGroup.id, tabInsertionIndex(header.tabRects, horizontalPosition))
    }

    bands.firstOrNull { band -> band.rect.contains(horizontalPosition, verticalPosition) }?.let { band -> return band.target }
    if (groupHit == null) return null
    if (hitGroup == null) return DropTarget.Center

    val bodyRect = if (header == null) {
        groupHit.rect
    } else {
        groupHit.rect.copy(top = header.rect.bottom, height = groupHit.rect.bottom - header.rect.bottom)
    }
    val laneContext = groupHit.laneContext
    if (laneContext == null) {
        val side = triangleSide(bodyRect, horizontalPosition, verticalPosition) ?: return null
        val frameLevel = if (capabilities.isFrameAllowed(DockFrameLevel.INNER)) DockFrameLevel.INNER else DockFrameLevel.OUTER
        return DropTarget.NewLane(frameLevel, side, laneIndex = layout.frame(frameLevel).lanes(side).count { lane -> lane.isOccupied })
    }
    val side = laneBodySide(bodyRect, laneContext.side, horizontalPosition, verticalPosition, metrics.laneInsertionBandShare)
        ?: return null
    // Placeholders of the lifted dockable disappear when it is moved; count only the containers that remain.
    val lanesOfSide = layout.frame(laneContext.frameLevel).lanes(laneContext.side)
    val remainingLaneIndex = lanesOfSide.take(laneContext.laneIndex).count { lane -> lane.isOccupied }
    val remainingGroupIndex = lanesOfSide[laneContext.laneIndex].groups.take(laneContext.groupIndex).count { group -> group.dockableIds.isNotEmpty() }
    return edgeTarget(
        frameLevel = laneContext.frameLevel,
        side = laneContext.side,
        laneId = laneContext.laneId,
        laneIndex = remainingLaneIndex,
        groupIndex = remainingGroupIndex,
        groupId = hitGroup.id,
        edge = side,
    )
}

private val DockLane.isOccupied: Boolean get() = groups.any { group -> group.dockableIds.isNotEmpty() }

/** The group (or the empty center, [group] `null`) under the pointer; [laneContext] is `null` for the center. */
private data class GroupHit(
    val group: TabGroup?,
    val rect: DockRect,
    val laneContext: LaneContext?,
)

private data class LaneContext(
    val frameLevel: DockFrameLevel,
    val side: DockSide,
    val laneId: String,
    val laneIndex: Int,
    val groupIndex: Int,
)

private fun groupAt(layout: DockLayout, geometry: DockGeometry, horizontalPosition: Float, verticalPosition: Float): GroupHit? {
    if (geometry.centerRect.contains(horizontalPosition, verticalPosition)) {
        return GroupHit(group = layout.centerGroup, rect = geometry.centerRect, laneContext = null)
    }
    DockFrameLevel.entries.forEach { frameLevel ->
        DockSide.entries.forEach { side ->
            layout.frame(frameLevel).lanes(side).forEachIndexed { laneIndex, lane ->
                lane.groups.forEachIndexed { groupIndex, group ->
                    val groupRect = geometry.groupRects[group.id]
                    if (groupRect != null && groupRect.contains(horizontalPosition, verticalPosition)) {
                        return GroupHit(group, groupRect, LaneContext(frameLevel, side, lane.id, laneIndex, groupIndex))
                    }
                }
            }
        }
    }
    return null
}

/** The part of an outer band that touches the window edge. */
private fun EmptySideBand.windowEdgePart(depth: Float): DockRect = when (target.side) {
    DockSide.TOP -> rect.copy(height = minOf(depth, rect.height))
    DockSide.BOTTOM -> rect.copy(top = rect.bottom - minOf(depth, rect.height), height = minOf(depth, rect.height))
    DockSide.LEFT -> rect.copy(width = minOf(depth, rect.width))
    DockSide.RIGHT -> rect.copy(left = rect.right - minOf(depth, rect.width), width = minOf(depth, rect.width))
}

/**
 * Maps a body triangle to a target. Sides along the lane's stacking axis split the lane; the other two sides
 * insert a new lane on the outer side (towards the frame edge) or on the inner side (towards the center).
 */
private fun edgeTarget(
    frameLevel: DockFrameLevel,
    side: DockSide,
    laneId: String,
    laneIndex: Int,
    groupIndex: Int,
    groupId: String,
    edge: DockSide,
): DropTarget {
    val edgeIsAlongStackingAxis = if (side.hasColumnLanes) {
        edge == DockSide.TOP || edge == DockSide.BOTTOM
    } else {
        edge == DockSide.LEFT || edge == DockSide.RIGHT
    }
    if (edgeIsAlongStackingAxis) {
        val isLeadingEdge = edge == DockSide.TOP || edge == DockSide.LEFT
        return DropTarget.NewGroup(laneId, if (isLeadingEdge) groupIndex else groupIndex + 1, splitGroupId = groupId)
    }
    val isOuterEdge = edge == side
    return DropTarget.NewLane(frameLevel, side, if (isOuterEdge) laneIndex else laneIndex + 1)
}

/**
 * The zone of a lane group's body under the pointer. In a column lane (LEFT/RIGHT) the left and right
 * [bandShare] of the width create a new column; the rest is split into an upper and a lower half, which
 * insert above or below. Row lanes (TOP/BOTTOM) use the same rule rotated by 90°.
 * `null` only outside [rect] or for an empty rect.
 */
internal fun laneBodySide(
    rect: DockRect,
    laneSide: DockSide,
    horizontalPosition: Float,
    verticalPosition: Float,
    bandShare: Float,
): DockSide? {
    if (rect.width <= 0f || rect.height <= 0f || !rect.contains(horizontalPosition, verticalPosition)) return null
    return if (laneSide.hasColumnLanes) {
        val bandWidth = rect.width * bandShare
        when {
            horizontalPosition < rect.left + bandWidth -> DockSide.LEFT
            horizontalPosition >= rect.right - bandWidth -> DockSide.RIGHT
            verticalPosition < rect.top + rect.height / 2f -> DockSide.TOP
            else -> DockSide.BOTTOM
        }
    } else {
        val bandHeight = rect.height * bandShare
        when {
            verticalPosition < rect.top + bandHeight -> DockSide.TOP
            verticalPosition >= rect.bottom - bandHeight -> DockSide.BOTTOM
            horizontalPosition < rect.left + rect.width / 2f -> DockSide.LEFT
            else -> DockSide.RIGHT
        }
    }
}

/**
 * The triangle of [rect] (divided along its diagonals) that contains the pointer: the side the pointer is
 * relatively closest to. Every point of a non-empty rect belongs to a triangle; `null` only outside or for an empty rect.
 */
internal fun triangleSide(rect: DockRect, horizontalPosition: Float, verticalPosition: Float): DockSide? {
    if (rect.width <= 0f || rect.height <= 0f || !rect.contains(horizontalPosition, verticalPosition)) return null
    val relativeDistances = listOf(
        DockSide.LEFT to (horizontalPosition - rect.left) / rect.width,
        DockSide.RIGHT to (rect.right - horizontalPosition) / rect.width,
        DockSide.TOP to (verticalPosition - rect.top) / rect.height,
        DockSide.BOTTOM to (rect.bottom - verticalPosition) / rect.height,
    )
    return relativeDistances.minBy { (_, distance) -> distance }.first
}

/**
 * The fit check for drops: the result must fit into [contentSize]. When the committed layout already does not
 * fit (for example after the window was made smaller), a drop is allowed as long as it needs no more space.
 */
private fun DockLayout.fitsAtLeastAsWellAs(
    committedLayout: DockLayout,
    contentSize: DockSize,
    catalog: DockableCatalog,
    metrics: DockingMetrics,
): Boolean {
    if (fitsInto(contentSize, catalog, metrics)) return true
    if (committedLayout.fitsInto(contentSize, catalog, metrics)) return false
    val previewMinimum = minimumContentSize(catalog, metrics)
    val committedMinimum = committedLayout.minimumContentSize(catalog, metrics)
    return previewMinimum.widthDp <= committedMinimum.widthDp && previewMinimum.heightDp <= committedMinimum.heightDp
}

/** `true` when both layouts are identical apart from container identifiers. */
internal fun DockLayout.hasSameArrangementAs(other: DockLayout): Boolean =
    withPositionalIds() == other.withPositionalIds()

private fun DockLayout.withPositionalIds(): DockLayout {
    fun renameFrame(frame: DockFrame, framePrefix: String) =
        DockFrame(
            sides = frame.sides.mapValues { (side, lanes) ->
                lanes.mapIndexed { laneIndex, lane ->
                    val laneIdentifier = "$framePrefix-${side.name}-$laneIndex"
                    lane.copy(
                        id = laneIdentifier,
                        groups = lane.groups.mapIndexed { groupIndex, group -> group.copy(id = "$laneIdentifier-$groupIndex") },
                    )
                }
            },
        )
    return DockLayout(
        outerFrame = renameFrame(outerFrame, "outer"),
        innerFrame = renameFrame(innerFrame, "inner"),
        centerGroup = centerGroup?.copy(id = "center"),
    )
}

/**
 * What a drag works on: the committed layout with the dragged dockable lifted out, keeping its emptied
 * group as a placeholder ([DropTarget.Origin]), and the committed geometry. Nothing moves when a drag starts,
 * and all targets are resolved where they are drawn.
 *
 * @property sourceGroupId the group the dockable was lifted from (it may be the placeholder).
 */
public data class LiftedDragBase(
    val dockableId: DockableId,
    val layout: DockLayout,
    val geometry: DockGeometry,
    val sourceGroupId: String?,
)

public fun liftedDragBase(
    committedLayout: DockLayout,
    dockableId: DockableId,
    contentSize: DockSize,
    catalog: DockableCatalog,
    metrics: DockingMetrics = DockingMetrics(),
): LiftedDragBase = LiftedDragBase(
    dockableId = dockableId,
    layout = committedLayout.removingDockableKeepingContainers(dockableId),
    geometry = calculateDockGeometry(committedLayout, contentSize, catalog, metrics),
    sourceGroupId = committedLayout.findGroupOf(dockableId)?.id,
)

/** The prospective result of dropping at the current pointer position. */
public data class DropPreview(
    val target: DropTarget,
    /** `null` for [DropTarget.Origin]. */
    val operation: DockLayoutOperation?,
    val layout: DockLayout,
    val geometry: DockGeometry,
    /** Rect of the dragged dockable's group in the preview: exactly the area that is highlighted. */
    val highlightRect: DockRect,
    /** `false` when dropping here restores the committed arrangement; such a drop commits nothing. */
    val changesLayout: Boolean,
)

/**
 * Builds the preview for dropping the lifted dockable of [dragBase] at the pointer. The target is resolved
 * in the lifted layout; the operation is applied to the committed [profile] through the same
 * [DockProfile.applyOperation] as the commit. Because move operations act on the layout without the moved
 * dockable, this is exactly the committed result. Returns `null` when there is no target, the operation is
 * rejected, or the result would not fit its minimum sizes into the content.
 */
public fun resolveDropPreview(
    profile: DockProfile,
    dragBase: LiftedDragBase,
    horizontalPosition: Float,
    verticalPosition: Float,
    capabilities: DockingCapabilities,
    orientation: LayoutOrientation,
    catalog: DockableCatalog,
    metrics: DockingMetrics = DockingMetrics(),
    headerMetrics: GroupHeaderMetrics = GroupHeaderMetrics(),
): DropPreview? {
    val target = resolveDropTarget(
        layout = dragBase.layout,
        geometry = dragBase.geometry,
        horizontalPosition = horizontalPosition,
        verticalPosition = verticalPosition,
        capabilities = capabilities,
        orientation = orientation,
        metrics = metrics,
        headerMetrics = headerMetrics,
    ) ?: return null
    val operation = target.toOperation(dragBase.dockableId, dragBase.layout)
    if (operation == null) {
        val sourceRect = dragBase.sourceGroupId?.let { groupId -> dragBase.geometry.groupRects[groupId] } ?: return null
        return DropPreview(
            target = target,
            operation = null,
            layout = profile.layout,
            geometry = dragBase.geometry,
            highlightRect = sourceRect,
            changesLayout = false,
        )
    }
    val previewLayout = (profile.applyOperation(operation, orientation, capabilities) as? DockOperationResult.Applied)?.layout
        ?: return null
    val contentSize = dragBase.geometry.contentSize
    if (!previewLayout.fitsAtLeastAsWellAs(profile.layout, contentSize, catalog, metrics)) return null
    val previewGeometry = calculateDockGeometry(previewLayout, contentSize, catalog, metrics)
    val highlightRect = previewLayout.findGroupOf(dragBase.dockableId)?.let { group -> previewGeometry.groupRects[group.id] }
        ?: return null
    return DropPreview(
        target = target,
        operation = operation,
        layout = previewLayout,
        geometry = previewGeometry,
        highlightRect = highlightRect,
        changesLayout = !previewLayout.hasSameArrangementAs(profile.layout),
    )
}
