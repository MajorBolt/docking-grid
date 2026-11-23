package io.github.majorbolt.dockinggrid

/** Restricted capabilities for the limit tests. */
internal object TestCapabilities {

    /**
     * No inner frame, at most 2 groups per lane. Landscape: 1 lane per side. Portrait: 1 lane LEFT/RIGHT, 2 TOP/BOTTOM.
     * Taken over from the first host application's tablet profile, which the expected values refer to.
     */
    val Tablet: DockingCapabilities = DockingCapabilities(
        isInnerFrameEnabled = false,
        maximumGroupsPerLane = 2,
        maximumLanesPerSide = mapOf(
            LayoutOrientation.LANDSCAPE to mapOf(
                DockSide.LEFT to 1,
                DockSide.RIGHT to 1,
                DockSide.TOP to 1,
                DockSide.BOTTOM to 1,
            ),
            LayoutOrientation.PORTRAIT to mapOf(
                DockSide.LEFT to 1,
                DockSide.RIGHT to 1,
                DockSide.TOP to 2,
                DockSide.BOTTOM to 2,
            ),
        ),
    )
}
