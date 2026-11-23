package io.github.majorbolt.dockinggrid.testing

import io.github.majorbolt.dockinggrid.DockFrameLevel
import io.github.majorbolt.dockinggrid.DockLocation
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.Dockable
import io.github.majorbolt.dockinggrid.DockableCatalog
import io.github.majorbolt.dockinggrid.DockableId

/**
 * Dockables for the tests of the dock host. Their minimum sizes and default locations define the default layout
 * that the expected positions refer to: LEFT lane with Scenes above Stream Info, Game Controls in the center,
 * RIGHT lane with Players above Audio Mixer. They mirror the engine tests' fixture in docking-grid-core.
 */
public object TestDockables {
    public val Scenes: DockableId = DockableId("scenes")
    public val GameControls: DockableId = DockableId("game_controls")
    public val Players: DockableId = DockableId("players")
    public val StreamInfo: DockableId = DockableId("stream_info")
    public val AudioMixer: DockableId = DockableId("audio_mixer")

    public val catalog: DockableCatalog = DockableCatalog(
        listOf(
            Dockable(Scenes, "Scenes", 220, 200, DockLocation.Side(DockFrameLevel.OUTER, DockSide.LEFT)),
            Dockable(GameControls, "Game Controls", 320, 200, DockLocation.Center),
            Dockable(Players, "Players", 220, 200, DockLocation.Side(DockFrameLevel.OUTER, DockSide.RIGHT)),
            Dockable(StreamInfo, "Stream Info", 240, 180, DockLocation.Side(DockFrameLevel.OUTER, DockSide.LEFT)),
            Dockable(AudioMixer, "Audio Mixer", 260, 220, DockLocation.Side(DockFrameLevel.OUTER, DockSide.RIGHT)),
        ),
    )

    public fun titleOf(dockableId: DockableId): String = catalog.find(dockableId)?.title ?: dockableId.value
}
