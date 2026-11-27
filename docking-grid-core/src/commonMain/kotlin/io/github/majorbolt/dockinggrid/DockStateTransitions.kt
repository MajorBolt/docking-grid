package io.github.majorbolt.dockinggrid

/** Default sizes taken over from the previous layout implementation (region weights). */
internal object DockingDefaults {
    fun laneExtentFraction(side: DockSide): Float = when (side) {
        DockSide.LEFT, DockSide.RIGHT -> 0.25f
        DockSide.TOP -> 0.20f
        DockSide.BOTTOM -> 0.25f
    }
}

/** Default state: every dockable of [catalog] opened at its default location, in catalog order. */
public fun defaultWindowDockState(catalog: DockableCatalog, capabilities: DockingCapabilities): WindowDockState {
    val emptyState = WindowDockState(
        openDockableIds = emptySet(),
        profiles = LayoutOrientation.entries.associateWith { DockProfile(layout = DockLayout()) },
    )
    return catalog.ids.fold(emptyState) { state, dockableId -> state.openingDockable(dockableId, catalog, capabilities) }
}

/**
 * Opens [dockableId] in every orientation profile: at its remembered position when that is still
 * available, otherwise at its default location. Opening never fails; the last resort is a tab.
 * An identifier that is not in [catalog] is ignored.
 */
public fun WindowDockState.openingDockable(
    dockableId: DockableId,
    catalog: DockableCatalog,
    capabilities: DockingCapabilities,
): WindowDockState {
    if (dockableId in openDockableIds) return this
    val dockable = catalog.find(dockableId) ?: return this
    val updatedProfiles = LayoutOrientation.entries.associateWith { orientation ->
        profile(orientation).placing(dockable, orientation, capabilities)
    }
    return WindowDockState(openDockableIds = openDockableIds + dockableId, profiles = updatedProfiles)
}

/** Closes [dockableId] in every profile and remembers where it was. */
public fun WindowDockState.closingDockable(dockableId: DockableId): WindowDockState {
    if (dockableId !in openDockableIds) return this
    return WindowDockState(
        openDockableIds = openDockableIds - dockableId,
        profiles = profiles.mapValues { (_, profile) -> profile.removing(dockableId) },
    )
}

public fun WindowDockState.togglingDockable(
    dockableId: DockableId,
    catalog: DockableCatalog,
    capabilities: DockingCapabilities,
): WindowDockState =
    if (dockableId in openDockableIds) closingDockable(dockableId) else openingDockable(dockableId, catalog, capabilities)

/**
 * Applies a structural [operation] to the profile of [orientation] only. Other profiles are unaffected.
 * The operation may only reference dockables that are open.
 */
public fun WindowDockState.applyingOperation(
    operation: DockLayoutOperation,
    orientation: LayoutOrientation,
    capabilities: DockingCapabilities,
): DockStateTransitionResult {
    val movedDockableId = operation.movedDockableId
    if (movedDockableId != null && movedDockableId !in openDockableIds) {
        return DockStateTransitionResult.Rejected(DockRejectionReason.UNKNOWN_DOCKABLE)
    }
    val currentProfile = profile(orientation)
    return when (val result = currentProfile.applyOperation(operation, orientation, capabilities)) {
        is DockOperationResult.Applied ->
            DockStateTransitionResult.Applied(withProfile(orientation, currentProfile.copy(layout = result.layout)))
        is DockOperationResult.Rejected -> DockStateTransitionResult.Rejected(result.reason)
    }
}

/**
 * Commits a drop from the dock host. Like [applyingOperation], but a moved dockable that is closed (one dragged
 * in from outside the host, for example from a toolbar) is opened: [operation] places it in the profile of
 * [orientation], and the other profiles place it at its remembered or default position, as [openingDockable] does.
 * Rejected when the operation is rejected, or for a [DockLayoutOperation.SelectTab] or an identifier that is not
 * in [catalog].
 */
public fun WindowDockState.applyingDropOperation(
    operation: DockLayoutOperation,
    orientation: LayoutOrientation,
    catalog: DockableCatalog,
    capabilities: DockingCapabilities,
): DockStateTransitionResult {
    val movedDockableId = operation.movedDockableId
    if (movedDockableId == null || movedDockableId in openDockableIds) return applyingOperation(operation, orientation, capabilities)
    val dockable = catalog.find(movedDockableId)
    if (dockable == null || operation is DockLayoutOperation.SelectTab) {
        return DockStateTransitionResult.Rejected(DockRejectionReason.UNKNOWN_DOCKABLE)
    }
    val currentProfile = profile(orientation)
    return when (val result = currentProfile.applyOperation(operation, orientation, capabilities)) {
        is DockOperationResult.Applied -> {
            val updatedProfiles = LayoutOrientation.entries.associateWith { profileOrientation ->
                if (profileOrientation == orientation) {
                    DockProfile(layout = result.layout, placementMemory = currentProfile.placementMemory - movedDockableId)
                } else {
                    profile(profileOrientation).placing(dockable, profileOrientation, capabilities)
                }
            }
            DockStateTransitionResult.Applied(WindowDockState(openDockableIds = openDockableIds + movedDockableId, profiles = updatedProfiles))
        }
        is DockOperationResult.Rejected -> DockStateTransitionResult.Rejected(result.reason)
    }
}

public sealed interface DockStateTransitionResult {
    public data class Applied(val state: WindowDockState) : DockStateTransitionResult
    public data class Rejected(val reason: DockRejectionReason) : DockStateTransitionResult
}

/**
 * Repairs a state loaded from storage or produced by an older version:
 * - identifiers that are not in [catalog] are removed everywhere, without placement memory;
 * - every profile exists and is normalized;
 * - dockables that are closed are not placed, and open dockables that are missing are placed;
 * - the inner frame is emptied where it is not allowed;
 * - a center grid is merged into a single tab group where the grid is not allowed.
 */
public fun WindowDockState.reconciled(catalog: DockableCatalog, capabilities: DockingCapabilities): WindowDockState {
    val knownOpenDockableIds = openDockableIds.filterTo(mutableSetOf()) { dockableId -> dockableId in catalog }
    val reconciledProfiles = LayoutOrientation.entries.associateWith { orientation ->
        val storedProfile = profile(orientation)
        val knownLayout = storedProfile.layout.placedDockableIds
            .filter { dockableId -> dockableId !in catalog }
            .fold(storedProfile.layout) { layout, unknownDockableId -> layout.removingDockable(unknownDockableId) }
        var reconciledProfile = DockProfile(
            layout = knownLayout.normalized(),
            placementMemory = storedProfile.placementMemory.filterKeys { dockableId -> dockableId in catalog },
        )
        if (!capabilities.isInnerFrameEnabled && reconciledProfile.layout.innerFrame.sides.isNotEmpty()) {
            reconciledProfile = reconciledProfile.copy(layout = reconciledProfile.layout.copy(innerFrame = DockFrame()))
        }
        if (!capabilities.isCenterGridEnabled && reconciledProfile.layout.center.panels.size > 1) {
            val mergedLayout = reconciledProfile.layout.applyOperation(
                DockLayoutOperation.ArrangeCenter(CenterPreset.SINGLE),
                orientation,
                capabilities,
            )
            if (mergedLayout is DockOperationResult.Applied) reconciledProfile = reconciledProfile.copy(layout = mergedLayout.layout)
        }
        reconciledProfile.layout.placedDockableIds
            .filter { dockableId -> dockableId !in knownOpenDockableIds }
            .forEach { dockableId -> reconciledProfile = reconciledProfile.removing(dockableId) }
        // Iterate in catalog order so that repairs are deterministic.
        catalog.dockables
            .filter { dockable -> dockable.id in knownOpenDockableIds && !reconciledProfile.layout.contains(dockable.id) }
            .forEach { dockable -> reconciledProfile = reconciledProfile.placing(dockable, orientation, capabilities) }
        reconciledProfile
    }
    return WindowDockState(openDockableIds = knownOpenDockableIds, profiles = reconciledProfiles)
}

private val DockLayoutOperation.movedDockableId: DockableId?
    get() = when (this) {
        is DockLayoutOperation.SelectTab -> dockableId
        is DockLayoutOperation.MoveToGroup -> dockableId
        is DockLayoutOperation.MoveToNewGroup -> dockableId
        is DockLayoutOperation.MoveToNewLane -> dockableId
        is DockLayoutOperation.MoveToCenter -> dockableId
        is DockLayoutOperation.SplitCenterPanel -> dockableId
        is DockLayoutOperation.MoveToCenterPanel -> dockableId
        is DockLayoutOperation.ResizeLane,
        is DockLayoutOperation.ResizeGroups,
        is DockLayoutOperation.ArrangeCenter,
        is DockLayoutOperation.ResizeCenterSplit,
        -> null
    }

/** Applies an operation to this profile, sizing new lanes from placement memory. */
public fun DockProfile.applyOperation(
    operation: DockLayoutOperation,
    orientation: LayoutOrientation,
    capabilities: DockingCapabilities,
): DockOperationResult = layout.applyOperation(
    operation = operation,
    orientation = orientation,
    capabilities = capabilities,
    newLaneExtentFraction = ::rememberedOrDefaultExtentFraction,
    reservedIds = reservedIds,
)

private val DockProfile.reservedIds: Set<String>
    get() = placementMemory.values.flatMap { memory -> listOfNotNull(memory.groupId, memory.laneId) }.toSet()

private fun DockProfile.rememberedOrDefaultExtentFraction(dockableId: DockableId, side: DockSide): Float {
    val memory = placementMemory[dockableId]
    val rememberedSide = (memory?.location as? DockLocation.Side)?.side
    val rememberedExtent = memory?.laneExtentFraction
    return if (rememberedSide != null && rememberedExtent != null && rememberedSide.hasColumnLanes == side.hasColumnLanes) {
        rememberedExtent
    } else {
        DockingDefaults.laneExtentFraction(side)
    }
}

private fun DockProfile.removing(dockableId: DockableId): DockProfile {
    val memory = layout.capturePlacement(dockableId) ?: return this
    return DockProfile(
        layout = layout.removingDockable(dockableId),
        placementMemory = placementMemory + (dockableId to memory),
    )
}

private fun DockProfile.placing(
    dockable: Dockable,
    orientation: LayoutOrientation,
    capabilities: DockingCapabilities,
): DockProfile {
    if (layout.contains(dockable.id)) return this
    val placedLayout = placementCandidates(dockable, capabilities)
        .firstNotNullOfOrNull { operation ->
            (applyOperation(operation, orientation, capabilities) as? DockOperationResult.Applied)?.layout
        }
        // MoveToCenter on an existing or empty center can never be rejected, so this is unreachable in practice.
        ?: layout
    return DockProfile(layout = placedLayout, placementMemory = placementMemory - dockable.id)
}

/** Ordered fallback chain: remembered group → remembered lane → remembered side/center → default → any tab. */
private fun DockProfile.placementCandidates(
    dockable: Dockable,
    capabilities: DockingCapabilities,
): List<DockLayoutOperation> {
    val dockableId = dockable.id
    val candidates = mutableListOf<DockLayoutOperation>()
    val memory = placementMemory[dockableId]
    if (memory != null) {
        val rememberedGroupId = memory.groupId
        if (rememberedGroupId != null && layout.findGroup(rememberedGroupId) != null) {
            candidates += DockLayoutOperation.MoveToGroup(dockableId, rememberedGroupId, memory.tabIndex)
        }
        val rememberedLaneId = memory.laneId
        if (rememberedLaneId != null && layout.findLane(rememberedLaneId) != null) {
            candidates += DockLayoutOperation.MoveToNewGroup(dockableId, rememberedLaneId, memory.groupIndex)
        }
        candidates += placementAt(dockableId, memory.location, capabilities, preferredLaneIndex = memory.laneIndex)
    }
    candidates += placementAt(dockableId, dockable.defaultLocation, capabilities, preferredLaneIndex = null)
    candidates += layout.allGroups.lastOrNull()?.let { group ->
        listOf(DockLayoutOperation.MoveToGroup(dockableId, group.id, group.dockableIds.size))
    }.orEmpty()
    candidates += DockLayoutOperation.MoveToCenter(dockableId)
    return candidates
}

private fun DockProfile.placementAt(
    dockableId: DockableId,
    location: DockLocation,
    capabilities: DockingCapabilities,
    preferredLaneIndex: Int?,
): List<DockLayoutOperation> = when (location) {
    DockLocation.Center -> listOf(DockLayoutOperation.MoveToCenter(dockableId))
    is DockLocation.Side -> {
        val frameLevel = if (capabilities.isFrameAllowed(location.frameLevel)) location.frameLevel else DockFrameLevel.OUTER
        val lanes = layout.frame(frameLevel).lanes(location.side)
        val operations = mutableListOf<DockLayoutOperation>()
        if (preferredLaneIndex != null) {
            operations += DockLayoutOperation.MoveToNewLane(dockableId, frameLevel, location.side, preferredLaneIndex)
        }
        val edgeLane = lanes.firstOrNull()
        if (edgeLane != null) {
            operations += DockLayoutOperation.MoveToNewGroup(dockableId, edgeLane.id, edgeLane.groups.size)
            val lastGroup = edgeLane.groups.last()
            operations += DockLayoutOperation.MoveToGroup(dockableId, lastGroup.id, lastGroup.dockableIds.size)
        } else {
            operations += DockLayoutOperation.MoveToNewLane(dockableId, frameLevel, location.side, laneIndex = 0)
        }
        operations
    }
}

/** Describes where [dockableId] currently is, or `null` when it is not placed. */
internal fun DockLayout.capturePlacement(dockableId: DockableId): PlacementMemory? {
    center.groups.forEach { group ->
        val tabIndex = group.dockableIds.indexOf(dockableId)
        if (tabIndex >= 0) {
            return PlacementMemory(
                location = DockLocation.Center,
                groupId = group.id,
                laneId = null,
                laneIndex = 0,
                groupIndex = 0,
                tabIndex = tabIndex,
                laneExtentFraction = null,
            )
        }
    }
    DockFrameLevel.entries.forEach { frameLevel ->
        DockSide.entries.forEach { side ->
            frame(frameLevel).lanes(side).forEachIndexed { laneIndex, lane ->
                lane.groups.forEachIndexed { groupIndex, group ->
                    val tabIndex = group.dockableIds.indexOf(dockableId)
                    if (tabIndex >= 0) {
                        return PlacementMemory(
                            location = DockLocation.Side(frameLevel, side),
                            groupId = group.id,
                            laneId = lane.id,
                            laneIndex = laneIndex,
                            groupIndex = groupIndex,
                            tabIndex = tabIndex,
                            laneExtentFraction = lane.extentFraction,
                        )
                    }
                }
            }
        }
    }
    return null
}
