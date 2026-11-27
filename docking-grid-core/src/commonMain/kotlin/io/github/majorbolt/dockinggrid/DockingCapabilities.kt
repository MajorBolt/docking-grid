package io.github.majorbolt.dockinggrid

/**
 * Structural limits of the docking system for a device profile, for example a tablet that allows fewer docks
 * than a desktop window. Host applications define the limits of their profiles; [Desktop] has none.
 *
 * `null` limits are unlimited; the geometric fit check (minimum sizes) applies in addition.
 *
 * @property isInnerFrameEnabled whether docks may be created inside the center (the inner frame).
 * @property maximumGroupsPerLane how many tab groups one lane may hold.
 * @param maximumLanesPerSide how many lanes each side may hold, per orientation; missing entries are unlimited.
 * @property isCenterGridEnabled whether the center may be split into up to 2×2 panels. When `false`, the center is a
 * single tab group and dropping on the sides of the center creates docks around it.
 */
public data class DockingCapabilities(
    val isInnerFrameEnabled: Boolean,
    val maximumGroupsPerLane: Int?,
    private val maximumLanesPerSide: Map<LayoutOrientation, Map<DockSide, Int>>,
    val isCenterGridEnabled: Boolean = true,
) {
    public fun maximumLanes(orientation: LayoutOrientation, side: DockSide): Int? =
        maximumLanesPerSide[orientation]?.get(side)

    public fun isFrameAllowed(frameLevel: DockFrameLevel): Boolean =
        frameLevel == DockFrameLevel.OUTER || isInnerFrameEnabled

    public companion object {
        /** No structural limits: both frames, any number of lanes and groups. */
        public val Desktop: DockingCapabilities = DockingCapabilities(
            isInnerFrameEnabled = true,
            maximumGroupsPerLane = null,
            maximumLanesPerSide = emptyMap(),
        )
    }
}
