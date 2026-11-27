package io.github.majorbolt.dockinggrid

import kotlin.math.abs

/**
 * Structural change of a single [DockLayout].
 *
 * Move operations first remove the moved dockable and collapse containers that become empty; their
 * target ids and indices refer to that layout without the moved dockable. A drag therefore resolves its
 * targets in exactly the layout it shows (the window lifted out), and preview and commit are identical.
 * Move operations also accept a dockable that is not placed yet; it is then simply inserted.
 */
public sealed interface DockLayoutOperation {

    public data class SelectTab(val dockableId: DockableId) : DockLayoutOperation

    /** Adds [dockableId] as a tab of [targetGroupId] at [tabIndex], or reorders it inside that group. */
    public data class MoveToGroup(
        val dockableId: DockableId,
        val targetGroupId: String,
        val tabIndex: Int,
    ) : DockLayoutOperation

    /**
     * Creates a new tab group at [groupIndex] inside an existing lane (stack in columns, side by side in rows).
     * The new group takes half of the space of [splitGroupId]; all other groups keep their size. Without
     * [splitGroupId] the group before the insertion point is split (the first group when inserting at 0).
     */
    public data class MoveToNewGroup(
        val dockableId: DockableId,
        val laneId: String,
        val groupIndex: Int,
        val splitGroupId: String? = null,
    ) : DockLayoutOperation

    /** Creates a new lane at [laneIndex] (0 = frame edge) on [side] of the frame at [frameLevel]. */
    public data class MoveToNewLane(
        val dockableId: DockableId,
        val frameLevel: DockFrameLevel,
        val side: DockSide,
        val laneIndex: Int,
    ) : DockLayoutOperation

    /**
     * Adds [dockableId] to the inner center: into its first empty panel, otherwise as a tab of its first panel
     * (reading order), creating the center panel if the center is empty. [tabIndex] applies to the tab case.
     */
    public data class MoveToCenter(
        val dockableId: DockableId,
        val tabIndex: Int? = null,
    ) : DockLayoutOperation

    /**
     * Splits the center panel [panelId] at [side]: the new panel with [dockableId] is placed on that side and takes
     * half of the space. Rejected with [DockRejectionReason.CENTER_LIMIT_REACHED] when the split would exceed the
     * 2×2 grid (see [CenterGrid.canSplitPanel]).
     */
    public data class SplitCenterPanel(
        val dockableId: DockableId,
        val panelId: String,
        val side: DockSide,
    ) : DockLayoutOperation

    /** Adds [dockableId] to the center panel [panelId]: fills an empty panel, or appends a tab. */
    public data class MoveToCenterPanel(
        val dockableId: DockableId,
        val panelId: String,
    ) : DockLayoutOperation

    /** Rearranges the center as [preset]; no dockable is opened or closed. */
    public data class ArrangeCenter(
        val preset: CenterPreset,
    ) : DockLayoutOperation

    /**
     * Sets the share of the first part of a center split: between the two sections when [sectionIndex] is `null`,
     * otherwise between the two panels of that section.
     */
    public data class ResizeCenterSplit(
        val sectionIndex: Int?,
        val splitFraction: Float,
    ) : DockLayoutOperation

    public data class ResizeLane(
        val laneId: String,
        val extentFraction: Float,
    ) : DockLayoutOperation

    /** Replaces the size fractions of all groups in a lane; the list must match the group count. */
    public data class ResizeGroups(
        val laneId: String,
        val groupFractions: List<Float>,
    ) : DockLayoutOperation
}

public enum class DockRejectionReason {
    UNKNOWN_DOCKABLE,
    UNKNOWN_TARGET,
    FRAME_NOT_ALLOWED,
    LANE_LIMIT_REACHED,
    GROUP_LIMIT_REACHED,
    INVALID_SIZE,

    /** The center grid is disabled, or the split would exceed 2×2 panels. */
    CENTER_LIMIT_REACHED,
}

public sealed interface DockOperationResult {
    public data class Applied(val layout: DockLayout) : DockOperationResult
    public data class Rejected(val reason: DockRejectionReason) : DockOperationResult
}

/** Smallest and largest lane extent the model accepts; real minimum sizes are enforced by the geometry. */
internal const val MINIMUM_EXTENT_FRACTION = 0.01f
internal const val MAXIMUM_EXTENT_FRACTION = 0.95f

/**
 * Applies [operation] and returns the normalized result, or a rejection when the target does not exist
 * or the result would exceed [capabilities].
 *
 * @param newLaneExtentFraction extent for lanes created by this operation.
 * @param reservedIds identifiers that must not be reused for new containers (for example ids held by placement memory).
 */
internal fun DockLayout.applyOperation(
    operation: DockLayoutOperation,
    orientation: LayoutOrientation,
    capabilities: DockingCapabilities,
    newLaneExtentFraction: (DockableId, DockSide) -> Float = { _, side -> DockingDefaults.laneExtentFraction(side) },
    reservedIds: Set<String> = emptySet(),
): DockOperationResult {
    val idGenerator = DockIdGenerator(allIds + reservedIds)
    return when (operation) {
        is DockLayoutOperation.SelectTab -> selectTab(operation.dockableId)
        is DockLayoutOperation.MoveToGroup -> moveToGroup(operation)
        is DockLayoutOperation.MoveToNewGroup -> moveToNewGroup(operation, capabilities, idGenerator)
        is DockLayoutOperation.MoveToNewLane -> moveToNewLane(
            operation = operation,
            orientation = orientation,
            capabilities = capabilities,
            extentFraction = newLaneExtentFraction(operation.dockableId, operation.side),
            idGenerator = idGenerator,
        )
        is DockLayoutOperation.MoveToCenter -> moveToCenter(operation, idGenerator)
        is DockLayoutOperation.SplitCenterPanel -> splitCenterPanel(operation, capabilities, idGenerator)
        is DockLayoutOperation.MoveToCenterPanel -> moveToCenterPanel(operation, idGenerator)
        is DockLayoutOperation.ArrangeCenter -> arrangeCenter(operation, capabilities, idGenerator)
        is DockLayoutOperation.ResizeCenterSplit -> resizeCenterSplit(operation)
        is DockLayoutOperation.ResizeLane -> resizeLane(operation)
        is DockLayoutOperation.ResizeGroups -> resizeGroups(operation)
    }
}

private fun DockLayout.selectTab(dockableId: DockableId): DockOperationResult {
    if (!contains(dockableId)) return DockOperationResult.Rejected(DockRejectionReason.UNKNOWN_DOCKABLE)
    return DockOperationResult.Applied(
        mapGroups { group ->
            if (dockableId in group.dockableIds) group.copy(selectedDockableId = dockableId) else group
        },
    )
}

private fun DockLayout.moveToGroup(operation: DockLayoutOperation.MoveToGroup): DockOperationResult {
    val layoutWithoutDockable = removingDockableGivingShareTo(operation.dockableId, preferredReceivingGroupId = operation.targetGroupId)
    layoutWithoutDockable.findGroup(operation.targetGroupId)
        ?: return DockOperationResult.Rejected(DockRejectionReason.UNKNOWN_TARGET)

    val updatedLayout = layoutWithoutDockable.mapGroups { group ->
        if (group.id != operation.targetGroupId) return@mapGroups group
        val insertionIndex = operation.tabIndex.coerceIn(0, group.dockableIds.size)
        val updatedDockableIds = group.dockableIds.toMutableList().apply { add(insertionIndex, operation.dockableId) }
        group.copy(dockableIds = updatedDockableIds, selectedDockableId = operation.dockableId)
    }
    return DockOperationResult.Applied(updatedLayout.normalized())
}

private fun DockLayout.moveToNewGroup(
    operation: DockLayoutOperation.MoveToNewGroup,
    capabilities: DockingCapabilities,
    idGenerator: DockIdGenerator,
): DockOperationResult {
    val layoutWithoutDockable = removingDockable(operation.dockableId)
    val laneLocation = layoutWithoutDockable.findLane(operation.laneId)
        ?: return DockOperationResult.Rejected(DockRejectionReason.UNKNOWN_TARGET)
    val frame = layoutWithoutDockable.frame(laneLocation.frameLevel)
    val lanes = frame.lanes(laneLocation.side)
    val targetLane = lanes[laneLocation.laneIndex]
    val insertionIndex = operation.groupIndex.coerceIn(0, targetLane.groups.size)
    // Only the split group gives up space: half of it, so all other groups of the lane keep their size.
    val splitGroup = operation.splitGroupId?.let { groupId -> targetLane.groups.firstOrNull { group -> group.id == groupId } }
        ?: targetLane.groups.getOrNull(insertionIndex - 1)
        ?: targetLane.groups.first()
    val halfShare = splitGroup.sizeFraction / 2f
    val newGroup = TabGroup(
        id = idGenerator.nextId(GROUP_ID_PREFIX),
        dockableIds = listOf(operation.dockableId),
        sizeFraction = halfShare,
    )
    val updatedGroups = targetLane.groups
        .map { group -> if (group.id == splitGroup.id) group.copy(sizeFraction = halfShare) else group }
        .toMutableList()
        .apply { add(insertionIndex, newGroup) }
    val updatedLane = targetLane.copy(groups = updatedGroups)
    val updatedLanes = lanes.toMutableList().apply { set(laneLocation.laneIndex, updatedLane) }
    val resultLayout = layoutWithoutDockable
        .withFrame(laneLocation.frameLevel, frame.withLanes(laneLocation.side, updatedLanes))
        .normalized()

    val maximumGroups = capabilities.maximumGroupsPerLane
    val resultLane = resultLayout.findLane(operation.laneId)?.let { location ->
        resultLayout.frame(location.frameLevel).lanes(location.side)[location.laneIndex]
    }
    if (maximumGroups != null && resultLane != null && resultLane.groups.size > maximumGroups) {
        return DockOperationResult.Rejected(DockRejectionReason.GROUP_LIMIT_REACHED)
    }
    return DockOperationResult.Applied(resultLayout)
}

private fun DockLayout.moveToNewLane(
    operation: DockLayoutOperation.MoveToNewLane,
    orientation: LayoutOrientation,
    capabilities: DockingCapabilities,
    extentFraction: Float,
    idGenerator: DockIdGenerator,
): DockOperationResult {
    if (!capabilities.isFrameAllowed(operation.frameLevel)) {
        return DockOperationResult.Rejected(DockRejectionReason.FRAME_NOT_ALLOWED)
    }
    val layoutWithoutDockable = removingDockable(operation.dockableId)
    val frame = layoutWithoutDockable.frame(operation.frameLevel)
    val lanes = frame.lanes(operation.side)
    val newLane = DockLane(
        id = idGenerator.nextId(LANE_ID_PREFIX),
        extentFraction = extentFraction.coerceIn(MINIMUM_EXTENT_FRACTION, MAXIMUM_EXTENT_FRACTION),
        groups = listOf(TabGroup(id = idGenerator.nextId(GROUP_ID_PREFIX), dockableIds = listOf(operation.dockableId))),
    )
    val insertionIndex = operation.laneIndex.coerceIn(0, lanes.size)
    val updatedLanes = lanes.toMutableList().apply { add(insertionIndex, newLane) }
    val resultLayout = layoutWithoutDockable
        .withFrame(operation.frameLevel, frame.withLanes(operation.side, updatedLanes))
        .normalized()

    val maximumLanes = capabilities.maximumLanes(orientation, operation.side)
    if (maximumLanes != null && resultLayout.frame(operation.frameLevel).lanes(operation.side).size > maximumLanes) {
        return DockOperationResult.Rejected(DockRejectionReason.LANE_LIMIT_REACHED)
    }
    return DockOperationResult.Applied(resultLayout)
}

private fun DockLayout.moveToCenter(
    operation: DockLayoutOperation.MoveToCenter,
    idGenerator: DockIdGenerator,
): DockOperationResult {
    val existingTargetGroup = center.defaultTargetPanel?.group
    if (existingTargetGroup != null && existingTargetGroup.dockableIds == listOf(operation.dockableId)) {
        return DockOperationResult.Applied(this)
    }
    val layoutWithoutDockable = removingDockable(operation.dockableId)
    val targetPanel = layoutWithoutDockable.center.defaultTargetPanel
    if (targetPanel == null) {
        val newCenter = CenterGrid.single(
            group = TabGroup(id = idGenerator.nextId(GROUP_ID_PREFIX), dockableIds = listOf(operation.dockableId)),
            panelId = idGenerator.nextId(PANEL_ID_PREFIX),
        )
        return DockOperationResult.Applied(layoutWithoutDockable.copy(center = newCenter).normalized())
    }
    val remainingTargetGroup = targetPanel.group
        ?: return layoutWithoutDockable.moveToCenterPanel(DockLayoutOperation.MoveToCenterPanel(operation.dockableId, targetPanel.id), idGenerator)
    return moveToGroup(
        DockLayoutOperation.MoveToGroup(
            dockableId = operation.dockableId,
            targetGroupId = remainingTargetGroup.id,
            tabIndex = operation.tabIndex ?: remainingTargetGroup.dockableIds.size,
        ),
    )
}

private fun DockLayout.resizeLane(operation: DockLayoutOperation.ResizeLane): DockOperationResult {
    val laneLocation = findLane(operation.laneId)
        ?: return DockOperationResult.Rejected(DockRejectionReason.UNKNOWN_TARGET)
    if (!operation.extentFraction.isFinite() || operation.extentFraction <= 0f) {
        return DockOperationResult.Rejected(DockRejectionReason.INVALID_SIZE)
    }
    val frame = frame(laneLocation.frameLevel)
    val updatedLanes = frame.lanes(laneLocation.side).toMutableList().apply {
        val lane = get(laneLocation.laneIndex)
        set(
            laneLocation.laneIndex,
            lane.copy(extentFraction = operation.extentFraction.coerceIn(MINIMUM_EXTENT_FRACTION, MAXIMUM_EXTENT_FRACTION)),
        )
    }
    return DockOperationResult.Applied(withFrame(laneLocation.frameLevel, frame.withLanes(laneLocation.side, updatedLanes)))
}

private fun DockLayout.resizeGroups(operation: DockLayoutOperation.ResizeGroups): DockOperationResult {
    val laneLocation = findLane(operation.laneId)
        ?: return DockOperationResult.Rejected(DockRejectionReason.UNKNOWN_TARGET)
    val frame = frame(laneLocation.frameLevel)
    val lane = frame.lanes(laneLocation.side)[laneLocation.laneIndex]
    val fractionsAreValid = operation.groupFractions.size == lane.groups.size &&
        operation.groupFractions.all { fraction -> fraction.isFinite() && fraction > 0f }
    if (!fractionsAreValid) return DockOperationResult.Rejected(DockRejectionReason.INVALID_SIZE)

    val updatedLane = lane.copy(
        groups = lane.groups.zip(operation.groupFractions) { group, fraction -> group.copy(sizeFraction = fraction) },
    )
    val updatedLanes = frame.lanes(laneLocation.side).toMutableList().apply { set(laneLocation.laneIndex, updatedLane) }
    return DockOperationResult.Applied(
        withFrame(laneLocation.frameLevel, frame.withLanes(laneLocation.side, updatedLanes)).normalized(),
    )
}

/**
 * Removes [dockableId] but keeps now-empty groups and lanes, so that container positions of the original layout
 * stay valid (for example the lifted slot of a drag). Callers that need a valid layout must run [normalized].
 */
internal fun DockLayout.removingDockableKeepingContainers(dockableId: DockableId): DockLayout =
    mapGroups { group -> group.withoutDockable(dockableId) }

/**
 * This group without [dockableId]; the selection moves to the neighbouring tab when the removed one was selected.
 * The result has no dockables when [dockableId] was the only one.
 */
public fun TabGroup.withoutDockable(dockableId: DockableId): TabGroup {
    val removedIndex = dockableIds.indexOf(dockableId)
    if (removedIndex < 0) return this
    val remainingDockableIds = dockableIds - dockableId
    val remainingSelection = when {
        remainingDockableIds.isEmpty() -> selectedDockableId
        selectedDockableId != dockableId -> selectedDockableId
        else -> remainingDockableIds[removedIndex.coerceAtMost(remainingDockableIds.lastIndex)]
    }
    return copy(dockableIds = remainingDockableIds, selectedDockableId = remainingSelection)
}

/**
 * Removes [dockableId] and collapses every container that became empty. The share of an emptied group goes to its
 * neighbour in the lane (the group before it, or after it for the first group), including the divider that
 * disappears, so all other groups keep their size.
 */
internal fun DockLayout.removingDockable(dockableId: DockableId): DockLayout =
    removingDockableGivingShareTo(dockableId, preferredReceivingGroupId = null)

/**
 * Like [removingDockable], but an emptied group's share goes to [preferredReceivingGroupId] when that group is in
 * the same lane: merging two groups of a lane into one tab group leaves the other groups untouched.
 */
private fun DockLayout.removingDockableGivingShareTo(dockableId: DockableId, preferredReceivingGroupId: String?): DockLayout {
    val layoutWithoutDockable = removingDockableKeepingContainers(dockableId)
    val emptiedGroup = findGroupOf(dockableId)?.takeIf { group -> group.dockableIds == listOf(dockableId) }
        ?: return layoutWithoutDockable.normalized()
    val sourceLane = (outerFrame.allLanes + innerFrame.allLanes).firstOrNull { lane -> lane.groups.any { group -> group.id == emptiedGroup.id } }
        ?: return layoutWithoutDockable.normalized()
    val emptiedIndex = sourceLane.groups.indexOfFirst { group -> group.id == emptiedGroup.id }
    val receivingGroupId = preferredReceivingGroupId
        ?.takeIf { groupId -> groupId != emptiedGroup.id && sourceLane.groups.any { group -> group.id == groupId } }
        ?: (sourceLane.groups.getOrNull(emptiedIndex - 1) ?: sourceLane.groups.getOrNull(emptiedIndex + 1))?.id
        ?: return layoutWithoutDockable.normalized()
    return layoutWithoutDockable
        .mapGroups { group ->
            if (group.id == receivingGroupId) group.copy(sizeFraction = group.sizeFraction + emptiedGroup.sizeFraction) else group
        }
        .normalized()
}

/**
 * Establishes the layout invariants:
 * - every dockable appears at most once (first occurrence wins),
 * - no empty groups or lanes remain; lanes collapse towards the edge automatically because they are ordered edge-first,
 * - each group's selection is one of its dockables,
 * - group fractions in each lane are positive and sum to 1,
 * - lane extents are within the accepted range;
 * - the center grid is canonical (see [CenterGrid.normalized]).
 */
internal fun DockLayout.normalized(): DockLayout {
    val seenDockableIds = mutableSetOf<DockableId>()

    fun normalizeGroup(group: TabGroup): TabGroup? {
        val uniqueDockableIds = group.dockableIds.filter { dockableId -> seenDockableIds.add(dockableId) }
        if (uniqueDockableIds.isEmpty()) return null
        val selectedDockableId = group.selectedDockableId.takeIf { selected -> selected in uniqueDockableIds }
            ?: uniqueDockableIds.first()
        return group.copy(dockableIds = uniqueDockableIds, selectedDockableId = selectedDockableId)
    }

    fun normalizeLane(lane: DockLane): DockLane? {
        val groups = lane.groups.mapNotNull(::normalizeGroup)
        if (groups.isEmpty()) return null
        return lane.copy(
            extentFraction = lane.extentFraction.coerceIn(MINIMUM_EXTENT_FRACTION, MAXIMUM_EXTENT_FRACTION),
            groups = normalizedGroupFractions(groups),
        )
    }

    fun normalizeFrame(frame: DockFrame): DockFrame {
        return DockSide.entries.fold(DockFrame()) { normalizedFrame, side ->
            normalizedFrame.withLanes(side, frame.lanes(side).mapNotNull(::normalizeLane))
        }
    }

    // Order matters for duplicate resolution: outer frame, inner frame, then center.
    val normalizedOuterFrame = normalizeFrame(outerFrame)
    val normalizedInnerFrame = normalizeFrame(innerFrame)
    val normalizedCenter = center.normalized(::normalizeGroup)
    return DockLayout(
        outerFrame = normalizedOuterFrame,
        innerFrame = normalizedInnerFrame,
        center = normalizedCenter,
    )
}

private const val FRACTION_SUM_TOLERANCE = 0.00001

/**
 * Rescales group fractions so they are positive and sum to 1. Fractions that already satisfy this are
 * returned unchanged, which keeps [normalized] idempotent despite floating-point rounding.
 */
private fun normalizedGroupFractions(groups: List<TabGroup>): List<TabGroup> {
    val allPositive = groups.all { group -> group.sizeFraction.isFinite() && group.sizeFraction > 0f }
    val fractionSum = groups.sumOf { group -> group.sizeFraction.toDouble() }
    if (allPositive && abs(fractionSum - 1.0) <= FRACTION_SUM_TOLERANCE) return groups
    if (!allPositive || fractionSum <= 0.0) return groups.map { group -> group.copy(sizeFraction = 1f / groups.size) }
    return groups.map { group -> group.copy(sizeFraction = (group.sizeFraction / fractionSum).toFloat()) }
}

internal fun DockLayout.mapGroups(transform: (TabGroup) -> TabGroup): DockLayout {
    fun mapFrame(frame: DockFrame): DockFrame = DockFrame(
        sides = frame.sides.mapValues { (_, lanes) ->
            lanes.map { lane -> lane.copy(groups = lane.groups.map(transform)) }
        },
    )
    return DockLayout(
        outerFrame = mapFrame(outerFrame),
        innerFrame = mapFrame(innerFrame),
        center = center.mapPanels { panel -> panel.copy(group = panel.group?.let(transform)) },
    )
}

internal const val LANE_ID_PREFIX = "lane"
internal const val GROUP_ID_PREFIX = "group"

/**
 * Generates identifiers deterministically from the identifiers already in use, so applying the same
 * operation to the same layout always yields an identical result (preview and commit match exactly).
 */
internal class DockIdGenerator(usedIds: Set<String>) {
    private val usedIds = usedIds.toMutableSet()

    fun nextId(prefix: String): String {
        var sequenceNumber = 1
        while ("$prefix-$sequenceNumber" in usedIds) sequenceNumber++
        val identifier = "$prefix-$sequenceNumber"
        usedIds += identifier
        return identifier
    }
}
