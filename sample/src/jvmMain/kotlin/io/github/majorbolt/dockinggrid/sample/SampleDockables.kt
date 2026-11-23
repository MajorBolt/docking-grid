package io.github.majorbolt.dockinggrid.sample

import io.github.majorbolt.dockinggrid.DockFrameLevel
import io.github.majorbolt.dockinggrid.DockLocation
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.Dockable
import io.github.majorbolt.dockinggrid.DockableCatalog
import io.github.majorbolt.dockinggrid.DockableId

/** The dummy content of the sample: an editor-like arrangement. */
internal object SampleDockables {
    val Project = DockableId("project")
    val Editor = DockableId("editor")
    val Console = DockableId("console")
    val Properties = DockableId("properties")
    val Outline = DockableId("outline")

    val catalog = DockableCatalog(
        listOf(
            Dockable(Project, "Project", 200, 160, DockLocation.Side(DockFrameLevel.OUTER, DockSide.LEFT)),
            Dockable(Editor, "Editor", 320, 200, DockLocation.Center),
            Dockable(Console, "Console", 240, 120, DockLocation.Side(DockFrameLevel.OUTER, DockSide.BOTTOM)),
            Dockable(Properties, "Properties", 200, 160, DockLocation.Side(DockFrameLevel.OUTER, DockSide.RIGHT)),
            Dockable(Outline, "Outline", 200, 160, DockLocation.Side(DockFrameLevel.OUTER, DockSide.RIGHT)),
        ),
    )
}
