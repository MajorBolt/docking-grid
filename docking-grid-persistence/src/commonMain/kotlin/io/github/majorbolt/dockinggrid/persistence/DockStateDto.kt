package io.github.majorbolt.dockinggrid.persistence

import kotlinx.serialization.Serializable

/** Current version of the stored format; see [DockStateCodec]. */
// 4: the center is a grid of panels (center) instead of one group (centerGroup).
// 3: neutral keys (openDockableIds, closedDockableIds, dockableIds, selectedDockableId). 2 used Tool Window names.
internal const val DOCK_STATE_SCHEMA_VERSION = 4

@Serializable
internal data class WindowDockStateDto(
    val schemaVersion: Int,
    val openDockableIds: List<String> = emptyList(),
    /** Stored explicitly so that dockables added in a later app version open at their default. */
    val closedDockableIds: List<String> = emptyList(),
    val profiles: Map<String, DockProfileDto> = emptyMap(),
)

@Serializable
internal data class DockProfileDto(
    val layout: DockLayoutDto = DockLayoutDto(),
    val placementMemory: Map<String, PlacementMemoryDto> = emptyMap(),
)

@Serializable
internal data class DockLayoutDto(
    val outerFrame: DockFrameDto = DockFrameDto(),
    val innerFrame: DockFrameDto = DockFrameDto(),
    val center: CenterGridDto = CenterGridDto(),
)

@Serializable
internal data class CenterGridDto(
    /** `HORIZONTAL` or `VERTICAL`. */
    val axis: String = "HORIZONTAL",
    val splitFraction: Float = 0.5f,
    val sections: List<CenterSectionDto> = emptyList(),
    val autoHidesHeader: Boolean = false,
)

@Serializable
internal data class CenterSectionDto(
    val splitFraction: Float = 0.5f,
    val panels: List<CenterPanelDto> = emptyList(),
)

@Serializable
internal data class CenterPanelDto(
    val id: String,
    /** `null` for an empty panel. */
    val group: TabGroupDto? = null,
)

@Serializable
internal data class DockFrameDto(
    val left: List<DockLaneDto> = emptyList(),
    val right: List<DockLaneDto> = emptyList(),
    val top: List<DockLaneDto> = emptyList(),
    val bottom: List<DockLaneDto> = emptyList(),
)

@Serializable
internal data class DockLaneDto(
    val id: String,
    val extentFraction: Float,
    val groups: List<TabGroupDto> = emptyList(),
)

@Serializable
internal data class TabGroupDto(
    val id: String,
    val dockableIds: List<String>,
    val selectedDockableId: String? = null,
    val sizeFraction: Float = 1f,
    val hasVerticalHeader: Boolean = false,
    val autoHidesHeader: Boolean = false,
)

@Serializable
internal data class PlacementMemoryDto(
    /** `CENTER` or `SIDE`. */
    val locationKind: String,
    val frameLevel: String? = null,
    val side: String? = null,
    val groupId: String? = null,
    val laneId: String? = null,
    val laneIndex: Int = 0,
    val groupIndex: Int = 0,
    val tabIndex: Int = 0,
    val laneExtentFraction: Float? = null,
    /** The remembered header style; `null` (both) when none is remembered. */
    val hasVerticalHeader: Boolean? = null,
    val autoHidesHeader: Boolean? = null,
)
