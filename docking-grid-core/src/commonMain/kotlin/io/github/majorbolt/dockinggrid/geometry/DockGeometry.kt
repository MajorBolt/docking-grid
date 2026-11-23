package io.github.majorbolt.dockinggrid.geometry

import io.github.majorbolt.dockinggrid.DockFrame
import io.github.majorbolt.dockinggrid.DockFrameLevel
import io.github.majorbolt.dockinggrid.DockLane
import io.github.majorbolt.dockinggrid.DockLayout
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockableCatalog
import io.github.majorbolt.dockinggrid.TabGroup

/** Axis-aligned rectangle in dp. */
public data class DockRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    public fun contains(horizontalPosition: Float, verticalPosition: Float): Boolean =
        horizontalPosition in left..<right && verticalPosition >= top && verticalPosition < bottom
}

public data class DockSize(
    val widthDp: Float,
    val heightDp: Float,
)

/**
 * Visual constants of the docking system, in dp. They are presentation metrics, not layout limits;
 * real minimum sizes come from the [DockableCatalog].
 */
public data class DockingMetrics(
    val dividerThicknessDp: Float = 8f,
    /** Thickness of the detection band of an empty side, so that empty docks stay targetable. */
    val emptySideBandDp: Float = 28f,
    /**
     * Depth of the part of an empty outer side's band at the window edge that wins over tab strips;
     * the rest of the band yields to the tab strips it overlaps.
     */
    val windowEdgeDropZoneDp: Float = 8f,
    /**
     * Share of a lane group's body, along each of the two edges across the lane's stacking axis, that creates
     * a new lane (for example the left and right quarter of a column group); the rest splits the lane by halves.
     */
    val laneInsertionBandShare: Float = 0.25f,
    /** Minimum size of the empty-center placeholder. 0 means the placeholder may collapse completely. */
    val emptyCenterMinimumDp: Float = 0f,
)

/** What dragging a divider changes. */
public sealed interface DividerTarget {
    /** Resizes the extent of one lane; the divider sits on the lane's inner side. */
    public data class LaneExtent(
        val laneId: String,
        val frameLevel: DockFrameLevel,
        val side: DockSide,
    ) : DividerTarget

    /** Moves the boundary between the groups at [boundaryIndex] - 1 and [boundaryIndex] of a lane. */
    public data class GroupBoundary(
        val laneId: String,
        val boundaryIndex: Int,
    ) : DividerTarget
}

/**
 * @property movesHorizontally `true` for a vertical divider line that moves left/right (↔ cursor).
 */
public data class DockDivider(
    val rect: DockRect,
    val movesHorizontally: Boolean,
    val target: DividerTarget,
)

/** Calculated placement of every element of a layout. All rects are in content coordinates (dp). */
public data class DockGeometry(
    val contentSize: DockSize,
    val laneRects: Map<String, DockRect>,
    val groupRects: Map<String, DockRect>,
    val innerFrameRect: DockRect,
    val centerRect: DockRect,
    val dividers: List<DockDivider>,
)

/**
 * Calculates the geometry of [layout] for [contentSize].
 *
 * Within each frame TOP/BOTTOM lanes span the full frame width and LEFT/RIGHT lanes sit between them;
 * the outer frame's remaining area is the inner frame, whose remaining area is the center.
 * Lane extents are fractions of the content size. When space is insufficient, lanes shrink towards
 * their minimum sizes first; only if even the minimums do not fit, everything shrinks proportionally.
 * Stored fractions are never changed by this calculation.
 */
public fun calculateDockGeometry(
    layout: DockLayout,
    contentSize: DockSize,
    catalog: DockableCatalog,
    metrics: DockingMetrics = DockingMetrics(),
): DockGeometry {
    val builder = GeometryBuilder(layout, contentSize, catalog, metrics)
    val contentRect = DockRect(0f, 0f, contentSize.widthDp.coerceAtLeast(0f), contentSize.heightDp.coerceAtLeast(0f))
    val innerFrameRect = builder.placeFrame(
        frame = layout.outerFrame,
        frameLevel = DockFrameLevel.OUTER,
        bounds = contentRect,
        remainingMinimum = DockSize(builder.innerFrameMinimumWidth(), builder.innerFrameMinimumHeight()),
    )
    val centerRect = builder.placeFrame(
        frame = layout.innerFrame,
        frameLevel = DockFrameLevel.INNER,
        bounds = innerFrameRect,
        remainingMinimum = DockSize(builder.centerMinimumWidth(), builder.centerMinimumHeight()),
    )
    layout.centerGroup?.let { group -> builder.groupRects[group.id] = centerRect }
    return DockGeometry(
        contentSize = contentSize,
        laneRects = builder.laneRects.toMap(),
        groupRects = builder.groupRects.toMap(),
        innerFrameRect = innerFrameRect,
        centerRect = centerRect,
        dividers = builder.dividers.toList(),
    )
}

/** Minimum content size required to show this layout without violating any dockable's minimum size. */
public fun DockLayout.minimumContentSize(catalog: DockableCatalog, metrics: DockingMetrics = DockingMetrics()): DockSize {
    val builder = GeometryBuilder(this, DockSize(0f, 0f), catalog, metrics)
    return DockSize(
        widthDp = builder.frameMinimumWidth(outerFrame, builder.innerFrameMinimumWidth()),
        heightDp = builder.frameMinimumHeight(outerFrame, builder.innerFrameMinimumHeight()),
    )
}

/** The fit check: `true` when every minimum size can be satisfied in [contentSize]. */
public fun DockLayout.fitsInto(
    contentSize: DockSize,
    catalog: DockableCatalog,
    metrics: DockingMetrics = DockingMetrics(),
): Boolean {
    val minimumSize = minimumContentSize(catalog, metrics)
    return minimumSize.widthDp <= contentSize.widthDp + SIZE_TOLERANCE && minimumSize.heightDp <= contentSize.heightDp + SIZE_TOLERANCE
}

private const val SIZE_TOLERANCE = 0.01f

private class GeometryBuilder(
    private val layout: DockLayout,
    private val contentSize: DockSize,
    private val catalog: DockableCatalog,
    private val metrics: DockingMetrics,
) {
    val laneRects = mutableMapOf<String, DockRect>()
    val groupRects = mutableMapOf<String, DockRect>()
    val dividers = mutableListOf<DockDivider>()

    private val divider: Float get() = metrics.dividerThicknessDp

    fun centerMinimumWidth(): Float =
        layout.centerGroup?.let { group -> catalog.minimumWidthDp(group).toFloat() } ?: metrics.emptyCenterMinimumDp

    fun centerMinimumHeight(): Float =
        layout.centerGroup?.let { group -> catalog.minimumHeightDp(group).toFloat() } ?: metrics.emptyCenterMinimumDp

    fun innerFrameMinimumWidth(): Float = frameMinimumWidth(layout.innerFrame, centerMinimumWidth())

    fun innerFrameMinimumHeight(): Float = frameMinimumHeight(layout.innerFrame, centerMinimumHeight())

    /** Columns plus the remaining area side by side; rows must fit their side-by-side groups. */
    fun frameMinimumWidth(frame: DockFrame, remainingMinimumWidth: Float): Float {
        val columnLanes = frame.lanes(DockSide.LEFT) + frame.lanes(DockSide.RIGHT)
        val middleWidth = columnLanes.sumOf { lane -> (lane.minimumExtent(isColumn = true) + divider).toDouble() }.toFloat() +
            remainingMinimumWidth
        val widestRow = (frame.lanes(DockSide.TOP) + frame.lanes(DockSide.BOTTOM))
            .maxOfOrNull { lane -> lane.minimumLength(isColumn = false) } ?: 0f
        return maxOf(middleWidth, widestRow)
    }

    /** Rows plus the middle band stacked; the middle band must fit the tallest column and the remaining area. */
    fun frameMinimumHeight(frame: DockFrame, remainingMinimumHeight: Float): Float {
        val rowLanes = frame.lanes(DockSide.TOP) + frame.lanes(DockSide.BOTTOM)
        val rowsHeight = rowLanes.sumOf { lane -> (lane.minimumExtent(isColumn = false) + divider).toDouble() }.toFloat()
        val tallestColumn = (frame.lanes(DockSide.LEFT) + frame.lanes(DockSide.RIGHT))
            .maxOfOrNull { lane -> lane.minimumLength(isColumn = true) } ?: 0f
        return rowsHeight + maxOf(remainingMinimumHeight, tallestColumn)
    }

    /**
     * Places the lanes of [frame] inside [bounds] and returns the remaining rect.
     * Order: TOP and BOTTOM (full width), then LEFT and RIGHT in the middle band.
     */
    fun placeFrame(frame: DockFrame, frameLevel: DockFrameLevel, bounds: DockRect, remainingMinimum: DockSize): DockRect {
        val topLanes = frame.lanes(DockSide.TOP)
        val bottomLanes = frame.lanes(DockSide.BOTTOM)
        val leftLanes = frame.lanes(DockSide.LEFT)
        val rightLanes = frame.lanes(DockSide.RIGHT)

        val tallestColumn = (leftLanes + rightLanes).maxOfOrNull { lane -> lane.minimumLength(isColumn = true) } ?: 0f
        val rowExtents = allocateLaneExtents(
            lanes = topLanes + bottomLanes,
            isColumn = false,
            contentDimension = contentSize.heightDp,
            available = bounds.height,
            remainingMinimum = maxOf(remainingMinimum.heightDp, tallestColumn),
        )
        var middleTop = bounds.top
        topLanes.forEachIndexed { laneIndex, lane ->
            val extent = rowExtents[laneIndex]
            placeLane(lane, DockRect(bounds.left, middleTop, bounds.width, extent), isColumn = false)
            middleTop += extent
            dividers += DockDivider(
                rect = DockRect(bounds.left, middleTop, bounds.width, divider),
                movesHorizontally = false,
                target = DividerTarget.LaneExtent(lane.id, frameLevel, DockSide.TOP),
            )
            middleTop += divider
        }
        var middleBottom = bounds.bottom
        bottomLanes.forEachIndexed { laneIndex, lane ->
            val extent = rowExtents[topLanes.size + laneIndex]
            middleBottom -= extent
            placeLane(lane, DockRect(bounds.left, middleBottom, bounds.width, extent), isColumn = false)
            middleBottom -= divider
            dividers += DockDivider(
                rect = DockRect(bounds.left, middleBottom, bounds.width, divider),
                movesHorizontally = false,
                target = DividerTarget.LaneExtent(lane.id, frameLevel, DockSide.BOTTOM),
            )
        }
        val middleHeight = (middleBottom - middleTop).coerceAtLeast(0f)

        val columnExtents = allocateLaneExtents(
            lanes = leftLanes + rightLanes,
            isColumn = true,
            contentDimension = contentSize.widthDp,
            available = bounds.width,
            remainingMinimum = remainingMinimum.widthDp,
        )
        var remainingLeft = bounds.left
        leftLanes.forEachIndexed { laneIndex, lane ->
            val extent = columnExtents[laneIndex]
            placeLane(lane, DockRect(remainingLeft, middleTop, extent, middleHeight), isColumn = true)
            remainingLeft += extent
            dividers += DockDivider(
                rect = DockRect(remainingLeft, middleTop, divider, middleHeight),
                movesHorizontally = true,
                target = DividerTarget.LaneExtent(lane.id, frameLevel, DockSide.LEFT),
            )
            remainingLeft += divider
        }
        var remainingRight = bounds.right
        rightLanes.forEachIndexed { laneIndex, lane ->
            val extent = columnExtents[leftLanes.size + laneIndex]
            remainingRight -= extent
            placeLane(lane, DockRect(remainingRight, middleTop, extent, middleHeight), isColumn = true)
            remainingRight -= divider
            dividers += DockDivider(
                rect = DockRect(remainingRight, middleTop, divider, middleHeight),
                movesHorizontally = true,
                target = DividerTarget.LaneExtent(lane.id, frameLevel, DockSide.RIGHT),
            )
        }
        return DockRect(remainingLeft, middleTop, (remainingRight - remainingLeft).coerceAtLeast(0f), middleHeight)
    }

    private fun allocateLaneExtents(
        lanes: List<DockLane>,
        isColumn: Boolean,
        contentDimension: Float,
        available: Float,
        remainingMinimum: Float,
    ): List<Float> {
        if (lanes.isEmpty()) return emptyList()
        val spaceForLanes = available - lanes.size * divider
        return distributeWithRemainder(
            desired = lanes.map { lane -> lane.extentFraction * contentDimension },
            minimum = lanes.map { lane -> lane.minimumExtent(isColumn) },
            remainderMinimum = remainingMinimum,
            available = spaceForLanes,
        )
    }

    private fun placeLane(lane: DockLane, rect: DockRect, isColumn: Boolean) {
        laneRects[lane.id] = rect
        val groupCount = lane.groups.size
        val laneLength = if (isColumn) rect.height else rect.width
        // Each divider belongs half to each of its two neighbours, so adding or removing a divider changes only them.
        val groupLengths = distributeToFill(
            desired = lane.groups.mapIndexed { groupIndex, group ->
                group.sizeFraction * laneLength - adjacentDividerCount(groupIndex, groupCount) * divider / 2f
            },
            minimum = lane.groups.map { group -> group.minimumLength(isColumn) },
            available = (laneLength - (groupCount - 1) * divider).coerceAtLeast(0f),
        )
        var position = if (isColumn) rect.top else rect.left
        lane.groups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) {
                val dividerRect = if (isColumn) {
                    DockRect(rect.left, position, rect.width, divider)
                } else {
                    DockRect(position, rect.top, divider, rect.height)
                }
                dividers += DockDivider(
                    rect = dividerRect,
                    movesHorizontally = !isColumn,
                    target = DividerTarget.GroupBoundary(lane.id, groupIndex),
                )
                position += divider
            }
            val length = groupLengths[groupIndex]
            groupRects[group.id] = if (isColumn) {
                DockRect(rect.left, position, rect.width, length)
            } else {
                DockRect(position, rect.top, length, rect.height)
            }
            position += length
        }
    }

    /** Extent across the lane: width of a column, height of a row. */
    private fun DockLane.minimumExtent(isColumn: Boolean): Float =
        groups.maxOf { group -> if (isColumn) catalog.minimumWidthDp(group) else catalog.minimumHeightDp(group) }.toFloat()

    /** Length along the lane: all groups plus the dividers between them. */
    fun DockLane.minimumLength(isColumn: Boolean): Float =
        groups.sumOf { group -> group.minimumLength(isColumn).toDouble() }.toFloat() + (groups.size - 1) * divider

    private fun TabGroup.minimumLength(isColumn: Boolean): Float =
        (if (isColumn) catalog.minimumHeightDp(this) else catalog.minimumWidthDp(this)).toFloat()
}

/**
 * Number of group dividers next to the group at [groupIndex] of a lane with [groupCount] groups: 0 for a single
 * group, 1 for the first and the last, 2 in between. A group's [io.github.majorbolt.dockinggrid.TabGroup.sizeFraction]
 * covers its own length plus half of each adjacent divider.
 */
internal fun adjacentDividerCount(groupIndex: Int, groupCount: Int): Int =
    (if (groupIndex > 0) 1 else 0) + (if (groupIndex < groupCount - 1) 1 else 0)
