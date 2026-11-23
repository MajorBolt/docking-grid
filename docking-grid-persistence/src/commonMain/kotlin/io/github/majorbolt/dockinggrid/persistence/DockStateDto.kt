package io.github.majorbolt.dockinggrid.persistence

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Current version of the stored format; see [DockStateCodec]. */
internal const val DOCK_STATE_SCHEMA_VERSION = 2

@Serializable
internal data class WindowDockStateDto(
    val schemaVersion: Int,
    // The serial names are kept from the format's origin in an app with Tool Windows; renaming them would break it.
    @SerialName("openToolWindowIds")
    val openDockableIds: List<String> = emptyList(),
    /** Stored explicitly so that dockables added in a later app version open at their default. */
    @SerialName("closedToolWindowIds")
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
    val centerGroup: TabGroupDto? = null,
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
    @SerialName("toolWindowIds")
    val dockableIds: List<String>,
    @SerialName("selectedToolWindowId")
    val selectedDockableId: String? = null,
    val sizeFraction: Float = 1f,
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
)
