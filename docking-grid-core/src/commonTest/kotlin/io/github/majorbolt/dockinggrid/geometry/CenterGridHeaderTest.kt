package io.github.majorbolt.dockinggrid.geometry

import io.github.majorbolt.dockinggrid.CenterAxis
import io.github.majorbolt.dockinggrid.CenterGrid
import io.github.majorbolt.dockinggrid.CenterPanel
import io.github.majorbolt.dockinggrid.CenterSection
import io.github.majorbolt.dockinggrid.DockLayout
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockOperationResult
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.GroupHeaderStyle
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.TabGroup
import io.github.majorbolt.dockinggrid.TestDockables
import io.github.majorbolt.dockinggrid.TestDockables.GameControls
import io.github.majorbolt.dockinggrid.TestDockables.Players
import io.github.majorbolt.dockinggrid.TestDockables.Scenes
import io.github.majorbolt.dockinggrid.TestDockables.StreamInfo
import io.github.majorbolt.dockinggrid.applyOperation
import io.github.majorbolt.dockinggrid.defaultWindowDockState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The grid header and vertical headers in 1600 × 900 dp with 8 dp dividers and the default 28 dp grid header. */
class CenterGridHeaderTest {

    private val desktop = DockingCapabilities.Desktop
    private val landscape = LayoutOrientation.LANDSCAPE
    private val contentSize = DockSize(widthDp = 1600f, heightDp = 900f)

    private fun panel(identifier: String, dockableId: io.github.majorbolt.dockinggrid.DockableId) =
        CenterPanel(identifier, TabGroup(id = "$identifier-group", dockableIds = listOf(dockableId)))

    private fun sideBySide(autoHidesHeader: Boolean = false) = DockLayout(
        center = CenterGrid(
            axis = CenterAxis.HORIZONTAL,
            sections = listOf(CenterSection(listOf(panel("a", Scenes))), CenterSection(listOf(panel("b", GameControls)))),
            autoHidesHeader = autoHidesHeader,
        ),
    )

    private fun geometryOf(layout: DockLayout) = calculateDockGeometry(layout, contentSize, TestDockables.catalog)

    @Test
    fun theGridHeaderTakesTheTopOfTheCenterWhileItIsShown() {
        val geometry = geometryOf(sideBySide())

        assertEquals(DockRect(0f, 0f, 1600f, 28f), geometry.centerHeaderRect)
        assertEquals(DockRect(0f, 28f, 796f, 872f), geometry.centerPanelRects["a"])
        assertEquals(DockSize(widthDp = 548f, heightDp = 228f), sideBySide().minimumContentSize(TestDockables.catalog), "200 + 28")
        // The grid header is no target of its own: it lies on the detection band of the empty TOP side.
        assertIs<DropTarget.NewLane>(resolveDropTarget(sideBySide(), geometry, 400f, 20f, desktop, landscape))
    }

    @Test
    fun anAutoHiddenGridHeaderLiesOverThePanels() {
        val geometry = geometryOf(sideBySide(autoHidesHeader = true))

        assertEquals(DockRect(0f, 0f, 1600f, 28f), geometry.centerHeaderRect, "Still known for its hover zone")
        assertEquals(DockRect(0f, 0f, 796f, 900f), geometry.centerPanelRects["a"])
    }

    @Test
    fun aRevealedAutoHiddenGridHeaderPushesThePanelsDown() {
        val layout = sideBySide(autoHidesHeader = true)
        fun panelTop(revealFraction: Float) =
            calculateDockGeometry(layout, contentSize, TestDockables.catalog, centerHeaderRevealFraction = revealFraction).centerPanelRects.getValue("a").top

        assertEquals(0f, panelTop(0f))
        assertEquals(14f, panelTop(0.5f), "Halfway through the slide")
        assertEquals(28f, panelTop(1f))
        assertEquals(28f, calculateDockGeometry(sideBySide(), contentSize, TestDockables.catalog, centerHeaderRevealFraction = 0f).centerPanelRects.getValue("a").top,
            "A header that does not auto-hide always takes its space")
    }

    @Test
    fun aSingleCenterPanelHasNoGridHeader() {
        val single = DockLayout(center = CenterGrid.single(TabGroup("only", listOf(Scenes))))
        val geometry = geometryOf(single)

        assertNull(geometry.centerHeaderRect)
        assertEquals(DockRect(0f, 0f, 1600f, 900f), geometry.groupRects["only"])
    }

    @Test
    fun aVerticalHeaderIsAStripWithStackedTabsOnTheOuterSide() {
        // Default layout: LEFT lane 0..400 with Scenes (0..446) above StreamInfo, RIGHT lane 1200..1600 with Players.
        val defaultLayout = defaultWindowDockState(TestDockables.catalog, desktop).profile(landscape).layout
        val scenesGroupId = defaultLayout.findGroupOf(Scenes)!!.id
        val playersGroupId = defaultLayout.findGroupOf(Players)!!.id
        val layout = listOf(scenesGroupId, playersGroupId).fold(defaultLayout) { currentLayout, groupId ->
            val result = currentLayout.applyOperation(DockLayoutOperation.SetGroupHeaderStyle(groupId, GroupHeaderStyle(isVertical = true)), landscape, desktop)
            assertIs<DockOperationResult.Applied>(result).layout
        }.let { withVertical ->
            assertIs<DockOperationResult.Applied>(
                withVertical.applyOperation(DockLayoutOperation.MoveToGroup(StreamInfo, scenesGroupId, tabIndex = 1), landscape, desktop),
            ).layout
        }
        val geometry = geometryOf(layout)
        val playersRect = geometry.groupRects.getValue(playersGroupId)
        fun targetAt(horizontalPosition: Float, verticalPosition: Float) =
            resolveDropTarget(layout, geometry, horizontalPosition, verticalPosition, desktop, landscape)

        // The strip of the Scenes group is 0..36 wide with the tabs Scenes (0..36) and StreamInfo (36..72).
        assertEquals(DropTarget.Tab(scenesGroupId, 0), targetAt(18f, 10f))
        assertEquals(DropTarget.Tab(scenesGroupId, 1), targetAt(18f, 30f))
        assertEquals(DropTarget.Tab(scenesGroupId, 2), targetAt(18f, 300f), "Below the last tab: at the end")
        // Beside the strip the body begins: the lane's upper half splits it.
        assertIs<DropTarget.NewGroup>(targetAt(200f, 100f))

        // Players is in the RIGHT lane: its strip is at the right edge.
        assertEquals(DropTarget.Tab(playersGroupId, 0), targetAt(playersRect.right - 10f, playersRect.top + 10f))
        // No header row at the top: below the empty TOP side's band (0..28) the body begins right away.
        assertIs<DropTarget.NewGroup>(targetAt(playersRect.left + playersRect.width / 2f, playersRect.top + 60f), "No header row at the top")
    }
}
