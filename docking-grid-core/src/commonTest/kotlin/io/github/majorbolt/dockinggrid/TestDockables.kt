package io.github.majorbolt.dockinggrid

/**
 * Dockables used by the docking engine tests. Their identifiers, minimum sizes and default locations define the
 * default layout that the expected sizes in the tests refer to (two LEFT groups, a center group, two RIGHT groups).
 * They were taken over from the first host application; changing them changes the expected values.
 */
internal object TestDockables {
    val Scenes = DockableId("scenes")
    val GameControls = DockableId("game_controls")
    val Players = DockableId("players")
    val StreamInfo = DockableId("stream_info")
    val AudioMixer = DockableId("audio_mixer")

    val catalog = DockableCatalog(
        listOf(
            Dockable(Scenes, "Scenes", 220, 200, DockLocation.Side(DockFrameLevel.OUTER, DockSide.LEFT)),
            Dockable(GameControls, "Game Controls", 320, 200, DockLocation.Center),
            Dockable(Players, "Players", 220, 200, DockLocation.Side(DockFrameLevel.OUTER, DockSide.RIGHT)),
            Dockable(StreamInfo, "Stream Info", 240, 180, DockLocation.Side(DockFrameLevel.OUTER, DockSide.LEFT)),
            Dockable(AudioMixer, "Audio Mixer", 260, 220, DockLocation.Side(DockFrameLevel.OUTER, DockSide.RIGHT)),
        ),
    )

    val ALL: List<DockableId> = catalog.ids
}
