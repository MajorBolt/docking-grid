package io.github.majorbolt.dockinggrid.persistence

import io.github.majorbolt.dockinggrid.CenterAxis
import io.github.majorbolt.dockinggrid.CenterGrid
import io.github.majorbolt.dockinggrid.CenterPanel
import io.github.majorbolt.dockinggrid.CenterSection
import io.github.majorbolt.dockinggrid.DockFrame
import io.github.majorbolt.dockinggrid.DockFrameLevel
import io.github.majorbolt.dockinggrid.DockLane
import io.github.majorbolt.dockinggrid.DockLayout
import io.github.majorbolt.dockinggrid.DockLocation
import io.github.majorbolt.dockinggrid.DockProfile
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockableCatalog
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.GroupHeaderStyle
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.PlacementMemory
import io.github.majorbolt.dockinggrid.TabGroup
import io.github.majorbolt.dockinggrid.WindowDockState

private const val LOCATION_KIND_CENTER = "CENTER"
private const val LOCATION_KIND_SIDE = "SIDE"

/** Converts to the stored format; open and closed identifiers are written in [catalog] order. */
internal fun WindowDockState.toDto(catalog: DockableCatalog): WindowDockStateDto = WindowDockStateDto(
    schemaVersion = DOCK_STATE_SCHEMA_VERSION,
    openDockableIds = catalog.ids.filter { dockableId -> dockableId in openDockableIds }.map { dockableId -> dockableId.value },
    closedDockableIds = catalog.ids.filter { dockableId -> dockableId !in openDockableIds }.map { dockableId -> dockableId.value },
    profiles = profiles.entries.associate { (orientation, profile) -> orientation.name to profile.toDto() },
)

private fun DockProfile.toDto(): DockProfileDto = DockProfileDto(
    layout = layout.toDto(),
    placementMemory = placementMemory.entries.associate { (dockableId, memory) -> dockableId.value to memory.toDto() },
)

private fun DockLayout.toDto(): DockLayoutDto = DockLayoutDto(
    outerFrame = outerFrame.toDto(),
    innerFrame = innerFrame.toDto(),
    center = center.toDto(),
)

private fun CenterGrid.toDto(): CenterGridDto = CenterGridDto(
    axis = axis.name,
    splitFraction = splitFraction,
    autoHidesHeader = autoHidesHeader,
    sections = sections.map { section ->
        CenterSectionDto(
            splitFraction = section.splitFraction,
            panels = section.panels.map { panel -> CenterPanelDto(id = panel.id, group = panel.group?.toDto()) },
        )
    },
)

private fun DockFrame.toDto(): DockFrameDto = DockFrameDto(
    left = lanes(DockSide.LEFT).map { lane -> lane.toDto() },
    right = lanes(DockSide.RIGHT).map { lane -> lane.toDto() },
    top = lanes(DockSide.TOP).map { lane -> lane.toDto() },
    bottom = lanes(DockSide.BOTTOM).map { lane -> lane.toDto() },
)

private fun DockLane.toDto(): DockLaneDto = DockLaneDto(
    id = id,
    extentFraction = extentFraction,
    groups = groups.map { group -> group.toDto() },
)

private fun TabGroup.toDto(): TabGroupDto = TabGroupDto(
    id = id,
    dockableIds = dockableIds.map { dockableId -> dockableId.value },
    selectedDockableId = selectedDockableId.value,
    sizeFraction = sizeFraction,
    hasVerticalHeader = headerStyle.isVertical,
    autoHidesHeader = headerStyle.autoHides,
)

private fun PlacementMemory.toDto(): PlacementMemoryDto {
    val sideLocation = location as? DockLocation.Side
    return PlacementMemoryDto(
        locationKind = if (sideLocation == null) LOCATION_KIND_CENTER else LOCATION_KIND_SIDE,
        frameLevel = sideLocation?.frameLevel?.name,
        side = sideLocation?.side?.name,
        groupId = groupId,
        laneId = laneId,
        laneIndex = laneIndex,
        groupIndex = groupIndex,
        tabIndex = tabIndex,
        laneExtentFraction = laneExtentFraction,
        hasVerticalHeader = headerStyle?.isVertical,
        autoHidesHeader = headerStyle?.autoHides,
    )
}

/**
 * Converts to the domain model. Identifiers that are not in [catalog], unknown orientations and sides, and empty
 * containers are dropped; dockables that are neither open nor closed (added in a newer app version) count as open.
 * The result must still be reconciled.
 */
internal fun WindowDockStateDto.toDomain(catalog: DockableCatalog): WindowDockState {
    val closedIds = closedDockableIds.map(::DockableId).toSet()
    val explicitlyOpenIds = openDockableIds.map(::DockableId).toSet()
    val openDockableIds = catalog.ids.filter { dockableId ->
        dockableId in explicitlyOpenIds || dockableId !in closedIds
    }.toSet()
    val domainProfiles = profiles.entries.mapNotNull { (orientationName, profileDto) ->
        val orientation = LayoutOrientation.entries.firstOrNull { entry -> entry.name == orientationName }
        orientation?.let { knownOrientation -> knownOrientation to profileDto.toDomain(catalog) }
    }.toMap()
    return WindowDockState(openDockableIds = openDockableIds, profiles = domainProfiles)
}

private fun DockProfileDto.toDomain(catalog: DockableCatalog): DockProfile = DockProfile(
    layout = layout.toDomain(catalog),
    placementMemory = placementMemory.entries
        .mapNotNull { (dockableIdValue, memoryDto) ->
            val dockableId = DockableId(dockableIdValue).takeIf { dockableId -> dockableId in catalog }
            val memory = memoryDto.toDomain()
            if (dockableId != null && memory != null) dockableId to memory else null
        }
        .toMap(),
)

private fun DockLayoutDto.toDomain(catalog: DockableCatalog): DockLayout = DockLayout(
    outerFrame = outerFrame.toDomain(catalog),
    innerFrame = innerFrame.toDomain(catalog),
    center = center.toDomain(catalog),
)

/** A panel whose group holds only unknown dockables is dropped; a panel stored without a group stays empty. */
private fun CenterGridDto.toDomain(catalog: DockableCatalog): CenterGrid = CenterGrid(
    axis = CenterAxis.entries.firstOrNull { entry -> entry.name == axis } ?: CenterAxis.HORIZONTAL,
    splitFraction = splitFraction,
    autoHidesHeader = autoHidesHeader,
    sections = sections.mapNotNull { sectionDto ->
        val panels = sectionDto.panels.mapNotNull { panelDto ->
            val groupDto = panelDto.group ?: return@mapNotNull CenterPanel(id = panelDto.id, group = null)
            groupDto.toDomain(catalog)?.let { group -> CenterPanel(id = panelDto.id, group = group) }
        }
        if (panels.isEmpty()) null else CenterSection(panels = panels, splitFraction = sectionDto.splitFraction)
    },
)

private fun DockFrameDto.toDomain(catalog: DockableCatalog): DockFrame = DockFrame()
    .withLanes(DockSide.LEFT, left.mapNotNull { laneDto -> laneDto.toDomain(catalog) })
    .withLanes(DockSide.RIGHT, right.mapNotNull { laneDto -> laneDto.toDomain(catalog) })
    .withLanes(DockSide.TOP, top.mapNotNull { laneDto -> laneDto.toDomain(catalog) })
    .withLanes(DockSide.BOTTOM, bottom.mapNotNull { laneDto -> laneDto.toDomain(catalog) })

private fun DockLaneDto.toDomain(catalog: DockableCatalog): DockLane? {
    val domainGroups = groups.mapNotNull { groupDto -> groupDto.toDomain(catalog) }
    if (domainGroups.isEmpty()) return null
    return DockLane(id = id, extentFraction = extentFraction, groups = domainGroups)
}

private fun TabGroupDto.toDomain(catalog: DockableCatalog): TabGroup? {
    val knownDockableIds = dockableIds.map(::DockableId).filter { dockableId -> dockableId in catalog }.distinct()
    if (knownDockableIds.isEmpty()) return null
    val knownSelectedDockableId = selectedDockableId?.let(::DockableId)?.takeIf { selected -> selected in knownDockableIds }
        ?: knownDockableIds.first()
    return TabGroup(
        id = id,
        dockableIds = knownDockableIds,
        selectedDockableId = knownSelectedDockableId,
        sizeFraction = sizeFraction,
        headerStyle = GroupHeaderStyle(isVertical = hasVerticalHeader, autoHides = autoHidesHeader),
    )
}

private fun PlacementMemoryDto.toDomain(): PlacementMemory? {
    val location = when (locationKind) {
        LOCATION_KIND_CENTER -> DockLocation.Center
        LOCATION_KIND_SIDE -> {
            val domainFrameLevel = DockFrameLevel.entries.firstOrNull { entry -> entry.name == frameLevel }
            val domainSide = DockSide.entries.firstOrNull { entry -> entry.name == side }
            if (domainFrameLevel == null || domainSide == null) return null
            DockLocation.Side(domainFrameLevel, domainSide)
        }
        else -> return null
    }
    return PlacementMemory(
        location = location,
        groupId = groupId,
        laneId = laneId,
        laneIndex = laneIndex,
        groupIndex = groupIndex,
        tabIndex = tabIndex,
        laneExtentFraction = laneExtentFraction,
        headerStyle = if (hasVerticalHeader == null && autoHidesHeader == null) {
            null
        } else {
            GroupHeaderStyle(isVertical = hasVerticalHeader == true, autoHides = autoHidesHeader == true)
        },
    )
}
