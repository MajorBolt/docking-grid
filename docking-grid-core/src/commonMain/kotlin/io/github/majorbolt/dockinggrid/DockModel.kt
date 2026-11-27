package io.github.majorbolt.dockinggrid

/**
 * One of the four sides of a [DockFrame].
 *
 * LEFT/RIGHT lanes are columns whose tab groups are stacked vertically.
 * TOP/BOTTOM lanes are rows whose tab groups are arranged side by side.
 */
public enum class DockSide {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM;

    /** `true` when lanes on this side are columns, so their extent is a width. */
    public val hasColumnLanes: Boolean get() = this == LEFT || this == RIGHT
}

/** The outer frame spans the whole content area; the inner frame lives inside the outer CENTER. */
public enum class DockFrameLevel {
    OUTER,
    INNER,
}

/** Each orientation keeps its own layout; see [WindowDockState]. */
public enum class LayoutOrientation {
    LANDSCAPE,
    PORTRAIT,
}

/**
 * A tab group: one or more dockables, exactly one of which is shown.
 *
 * @property sizeFraction share of the containing lane's full length along its stacking axis, including half of
 * each adjacent group divider; so inserting or removing a divider only affects the two groups beside it.
 * The fractions of all groups in a lane sum to 1 after normalization.
 */
public data class TabGroup(
    val id: String,
    val dockableIds: List<DockableId>,
    val selectedDockableId: DockableId = dockableIds.first(),
    val sizeFraction: Float = 1f,
)

/**
 * A column (LEFT/RIGHT) or row (TOP/BOTTOM) of tab groups.
 *
 * @property extentFraction width (columns) or height (rows) as a fraction of the window content size.
 * It is independent of other docks, so opening or closing unrelated docks never changes it.
 */
public data class DockLane(
    val id: String,
    val extentFraction: Float,
    val groups: List<TabGroup>,
)

/**
 * Four sides of lanes. Lanes are ordered from the frame edge inwards: index 0 touches the edge.
 * Within a frame, TOP and BOTTOM span the full width and LEFT/RIGHT sit between them.
 */
public data class DockFrame(
    val sides: Map<DockSide, List<DockLane>> = emptyMap(),
) {
    public fun lanes(side: DockSide): List<DockLane> = sides[side].orEmpty()

    public fun withLanes(side: DockSide, lanes: List<DockLane>): DockFrame {
        return DockFrame(sides = if (lanes.isEmpty()) sides - side else sides + (side to lanes))
    }

    val allLanes: List<DockLane> get() = DockSide.entries.flatMap { side -> lanes(side) }
}

/** How the two sections of a [CenterGrid] are arranged. */
public enum class CenterAxis {
    /** Sections side by side; the panels of a section are stacked. */
    HORIZONTAL,

    /** Sections stacked; the panels of a section are side by side. */
    VERTICAL;

    /** The other axis: the one the panels of a section are arranged along. */
    public val crossAxis: CenterAxis get() = if (this == HORIZONTAL) VERTICAL else HORIZONTAL

    public companion object {
        /** The axis along which a split at [side] inserts: LEFT/RIGHT side by side, TOP/BOTTOM stacked. */
        public fun of(side: DockSide): CenterAxis = if (side.hasColumnLanes) HORIZONTAL else VERTICAL
    }
}

/**
 * One cell of the [CenterGrid].
 *
 * @property group the tab group shown in the panel, or `null` for an empty panel created by a [CenterPreset]
 * that waits for a dockable to be dropped in. A panel whose group loses its last dockable is removed.
 */
public data class CenterPanel(
    val id: String,
    val group: TabGroup? = null,
)

/**
 * One or two panels arranged along the grid's [CenterAxis.crossAxis].
 *
 * @property splitFraction share of the section's length (without the divider) that the first panel takes.
 */
public data class CenterSection(
    val panels: List<CenterPanel>,
    val splitFraction: Float = 0.5f,
)

/**
 * The inner CENTER: up to two sections along [axis], each with up to two panels, so one full panel, two
 * panels side by side or stacked, three panels (one full-length) or a 2×2 grid. No sections means the
 * center shows the empty placeholder.
 *
 * @property splitFraction share of the center's length along [axis] (without the divider) that the first section takes.
 */
public data class CenterGrid(
    val axis: CenterAxis = CenterAxis.HORIZONTAL,
    val sections: List<CenterSection> = emptyList(),
    val splitFraction: Float = 0.5f,
) {
    val panels: List<CenterPanel> get() = sections.flatMap { section -> section.panels }

    val groups: List<TabGroup> get() = panels.mapNotNull { panel -> panel.group }

    val isEmpty: Boolean get() = sections.isEmpty()

    public fun findPanel(panelId: String): CenterPanel? = panels.firstOrNull { panel -> panel.id == panelId }

    public fun findPanelOf(groupId: String): CenterPanel? = panels.firstOrNull { panel -> panel.group?.id == groupId }

    /** The panels from the top left to the bottom right, row by row. */
    val panelsInReadingOrder: List<CenterPanel>
        get() = when (axis) {
            // Sections are columns: read the first row of both columns, then the second.
            CenterAxis.HORIZONTAL -> (0..1).flatMap { panelIndex -> sections.mapNotNull { section -> section.panels.getOrNull(panelIndex) } }
            CenterAxis.VERTICAL -> panels
        }

    public companion object {
        /** A center with one panel showing [group]. */
        public fun single(group: TabGroup, panelId: String = "panel-1"): CenterGrid =
            CenterGrid(sections = listOf(CenterSection(panels = listOf(CenterPanel(panelId, group)))))
    }
}

/** Ready-made center arrangements; the panels of a preset are listed in reading order. */
public enum class CenterPreset(public val panelCount: Int) {
    SINGLE(1),
    SIDE_BY_SIDE(2),
    STACKED(2),
    THREE_FULL_LEFT(3),
    THREE_FULL_RIGHT(3),
    THREE_FULL_TOP(3),
    THREE_FULL_BOTTOM(3),
    GRID_2X2(4),
}

/**
 * The complete arrangement of one window in one orientation.
 *
 * @property center the inner CENTER grid; empty when it shows the placeholder.
 */
public data class DockLayout(
    val outerFrame: DockFrame = DockFrame(),
    val innerFrame: DockFrame = DockFrame(),
    val center: CenterGrid = CenterGrid(),
) {
    public fun frame(level: DockFrameLevel): DockFrame = when (level) {
        DockFrameLevel.OUTER -> outerFrame
        DockFrameLevel.INNER -> innerFrame
    }

    public fun withFrame(level: DockFrameLevel, frame: DockFrame): DockLayout = when (level) {
        DockFrameLevel.OUTER -> copy(outerFrame = frame)
        DockFrameLevel.INNER -> copy(innerFrame = frame)
    }

    val allGroups: List<TabGroup>
        get() = outerFrame.allLanes.flatMap { lane -> lane.groups } +
            innerFrame.allLanes.flatMap { lane -> lane.groups } +
            center.groups

    val placedDockableIds: List<DockableId> get() = allGroups.flatMap { group -> group.dockableIds }

    public fun contains(dockableId: DockableId): Boolean = allGroups.any { group -> dockableId in group.dockableIds }

    public fun findGroupOf(dockableId: DockableId): TabGroup? = allGroups.firstOrNull { group -> dockableId in group.dockableIds }

    public fun findGroup(groupId: String): TabGroup? = allGroups.firstOrNull { group -> group.id == groupId }

    public fun findLane(laneId: String): LaneLocation? {
        DockFrameLevel.entries.forEach { level ->
            DockSide.entries.forEach { side ->
                val laneIndex = frame(level).lanes(side).indexOfFirst { lane -> lane.id == laneId }
                if (laneIndex >= 0) return LaneLocation(level, side, laneIndex)
            }
        }
        return null
    }

    val allIds: Set<String>
        get() = allGroups.map { group -> group.id }.toSet() +
            (outerFrame.allLanes + innerFrame.allLanes).map { lane -> lane.id } +
            center.panels.map { panel -> panel.id }
}

/** Position of a lane inside a [DockLayout]. */
public data class LaneLocation(
    val frameLevel: DockFrameLevel,
    val side: DockSide,
    val laneIndex: Int,
)

/** Where a dockable lives: either a side of a frame or the inner center. */
public sealed interface DockLocation {
    public data object Center : DockLocation

    public data class Side(
        val frameLevel: DockFrameLevel,
        val side: DockSide,
    ) : DockLocation
}

/**
 * Remembered position and size of a closed dockable, used to reopen it where the user left it.
 */
public data class PlacementMemory(
    val location: DockLocation,
    val groupId: String?,
    val laneId: String?,
    val laneIndex: Int,
    val groupIndex: Int,
    val tabIndex: Int,
    val laneExtentFraction: Float?,
)

/** Layout and placement memory of one orientation. */
public data class DockProfile(
    val layout: DockLayout,
    val placementMemory: Map<DockableId, PlacementMemory> = emptyMap(),
)

/**
 * Complete docking state of one application window.
 *
 * The set of open dockables is shared between orientations; each orientation has its own
 * [DockProfile]. Every open dockable is placed exactly once in every profile.
 */
public data class WindowDockState(
    val openDockableIds: Set<DockableId>,
    val profiles: Map<LayoutOrientation, DockProfile>,
) {
    public fun profile(orientation: LayoutOrientation): DockProfile =
        profiles[orientation] ?: DockProfile(layout = DockLayout())

    public fun withProfile(orientation: LayoutOrientation, profile: DockProfile): WindowDockState =
        copy(profiles = profiles + (orientation to profile))
}
