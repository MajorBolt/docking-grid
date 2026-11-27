package io.github.majorbolt.dockinggrid.geometry

import io.github.majorbolt.dockinggrid.CenterAxis
import io.github.majorbolt.dockinggrid.CenterGrid
import io.github.majorbolt.dockinggrid.CenterPanel
import io.github.majorbolt.dockinggrid.CenterSection
import io.github.majorbolt.dockinggrid.DockFrameLevel
import io.github.majorbolt.dockinggrid.DockLayout
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockProfile
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.TabGroup
import io.github.majorbolt.dockinggrid.TestDockables
import io.github.majorbolt.dockinggrid.TestDockables.AudioMixer
import io.github.majorbolt.dockinggrid.TestDockables.GameControls
import io.github.majorbolt.dockinggrid.TestDockables.Players
import io.github.majorbolt.dockinggrid.TestDockables.Scenes
import io.github.majorbolt.dockinggrid.TestDockables.StreamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A center-only layout in 1600 × 900 dp with 8 dp dividers. The 2×2 grid has the columns [Scenes above Players] and
 * [GameControls above StreamInfo]: evenly split, each column is 796 wide and each panel 446 high, so
 * a = (0, 0), b = (804, 0), c = (0, 454), d = (804, 454). A panel header is 36 dp high.
 */
class CenterGridGeometryTest {

    private val desktop = DockingCapabilities.Desktop
    private val landscape = LayoutOrientation.LANDSCAPE
    private val contentSize = DockSize(widthDp = 1600f, heightDp = 900f)

    private fun panel(identifier: String, dockableId: DockableId) =
        CenterPanel(identifier, TabGroup(id = "$identifier-group", dockableIds = listOf(dockableId)))

    private fun grid2x2(columnSplit: Float = 0.5f, secondColumnSplit: Float = 0.5f) = DockLayout(
        center = CenterGrid(
            axis = CenterAxis.HORIZONTAL,
            sections = listOf(
                CenterSection(listOf(panel("a", Scenes), panel("c", Players))),
                CenterSection(listOf(panel("b", GameControls), panel("d", StreamInfo)), splitFraction = secondColumnSplit),
            ),
            splitFraction = columnSplit,
        ),
    )

    private val sideBySide = DockLayout(
        center = CenterGrid(
            axis = CenterAxis.HORIZONTAL,
            sections = listOf(CenterSection(listOf(panel("a", Scenes))), CenterSection(listOf(panel("b", GameControls)))),
        ),
    )

    private fun geometryOf(layout: DockLayout, metrics: DockingMetrics = DockingMetrics()) =
        calculateDockGeometry(layout, contentSize, TestDockables.catalog, metrics)

    private fun targetAt(layout: DockLayout, horizontalPosition: Float, verticalPosition: Float, capabilities: DockingCapabilities = desktop) =
        resolveDropTarget(layout, geometryOf(layout), horizontalPosition, verticalPosition, capabilities, landscape)

    @Test
    fun aTwoByTwoGridDividesTheCenterEvenly() {
        val geometry = geometryOf(grid2x2())

        assertEquals(DockRect(0f, 0f, 796f, 446f), geometry.centerPanelRects["a"])
        assertEquals(DockRect(804f, 0f, 796f, 446f), geometry.centerPanelRects["b"])
        assertEquals(DockRect(0f, 454f, 796f, 446f), geometry.centerPanelRects["c"])
        assertEquals(DockRect(804f, 454f, 796f, 446f), geometry.centerPanelRects["d"])
        assertEquals(geometry.centerPanelRects["d"], geometry.groupRects["d-group"], "A panel's group has the panel's rect")

        val centerDividers = geometry.dividers.filter { divider -> divider.target is DividerTarget.CenterSplit }
        assertEquals(
            listOf(
                DockDivider(DockRect(796f, 0f, 8f, 900f), movesHorizontally = true, target = DividerTarget.CenterSplit(null)),
                DockDivider(DockRect(0f, 446f, 796f, 8f), movesHorizontally = false, target = DividerTarget.CenterSplit(0)),
                DockDivider(DockRect(804f, 446f, 796f, 8f), movesHorizontally = false, target = DividerTarget.CenterSplit(1)),
            ),
            centerDividers,
        )
    }

    @Test
    fun lockedCenterSplitsKeepTheirPositionButCannotBeDragged() {
        val resizedLayout = grid2x2(columnSplit = 0.25f, secondColumnSplit = 0.75f)

        val resized = geometryOf(resizedLayout)
        assertEquals(398f, resized.centerPanelRects.getValue("a").width)
        assertEquals(669f, resized.centerPanelRects.getValue("b").height)

        val locked = DockingMetrics(centerSplitsResizable = false)
        val lockedGeometry = geometryOf(resizedLayout, locked)
        assertEquals(resized.centerPanelRects, lockedGeometry.centerPanelRects)
        assertTrue(lockedGeometry.dividers.filter { divider -> divider.target is DividerTarget.CenterSplit }.none { divider -> divider.isResizable })
        val mainDivider = lockedGeometry.dividers.first { divider -> divider.target == DividerTarget.CenterSplit(null) }
        assertNull(resizeOperationFor(resizedLayout, mainDivider, 100f, contentSize, TestDockables.catalog, locked))
    }

    @Test
    fun minimumSizesAddUpAlongEachSplit() {
        // Widths: max(Scenes 220, Players 220) + 8 + max(GameControls 320, StreamInfo 240); heights: max(200 + 8 + 200, 200 + 8 + 180).
        assertEquals(DockSize(widthDp = 548f, heightDp = 408f), grid2x2().minimumContentSize(TestDockables.catalog))
    }

    @Test
    fun draggingACenterDividerResizesThatSplitWithinTheMinimumSizes() {
        val layout = grid2x2()
        val geometry = geometryOf(layout)
        val mainDivider = geometry.dividers.first { divider -> divider.target == DividerTarget.CenterSplit(null) }
        val secondColumnDivider = geometry.dividers.first { divider -> divider.target == DividerTarget.CenterSplit(1) }

        assertEquals(
            DockLayoutOperation.ResizeCenterSplit(null, 896f / 1592f),
            resizeOperationFor(layout, mainDivider, 100f, contentSize, TestDockables.catalog),
        )
        assertEquals(
            DockLayoutOperation.ResizeCenterSplit(null, 220f / 1592f),
            resizeOperationFor(layout, mainDivider, -10_000f, contentSize, TestDockables.catalog),
            "The first column keeps its minimum width",
        )
        assertEquals(
            DockLayoutOperation.ResizeCenterSplit(1, (892f - 180f) / 892f),
            resizeOperationFor(layout, secondColumnDivider, 10_000f, contentSize, TestDockables.catalog),
            "StreamInfo keeps its minimum height",
        )
    }

    @Test
    fun centerPanelTrianglesSplitWherePossibleAndOtherwiseCreateInnerLanes() {
        // Side by side, a is 0..796 × 0..900 with its body from 36. The empty-side bands along the center's edges
        // (28..56 dp from the edge) come first, so the points lie further inside.
        assertEquals(DropTarget.SplitCenterPanel("a", DockSide.BOTTOM), targetAt(sideBySide, 398f, 820f))
        assertEquals(DropTarget.SplitCenterPanel("a", DockSide.TOP), targetAt(sideBySide, 398f, 100f))
        assertEquals(
            DropTarget.NewLane(DockFrameLevel.INNER, DockSide.LEFT, 0),
            targetAt(sideBySide, 60f, 450f),
            "A third column is not possible: the inner lane",
        )
        assertEquals(DropTarget.NewLane(DockFrameLevel.INNER, DockSide.TOP, 0), targetAt(grid2x2(), 398f, 100f), "The 2×2 grid is full")
        assertNull(targetAt(grid2x2(), 800f, 200f), "On the divider between the columns")
    }

    @Test
    fun theMiddleOfACenterPanelSwapsAndInnerEdgesOfAFullDirectionSwapToo() {
        // The middle of a (body 0..796 × 36..446).
        assertEquals(DropTarget.Swap(Scenes), targetAt(grid2x2(), 398f, 240f))
        // The bottom edge of a lies inside the grid (c is below it): no split, no lane below the grid.
        assertEquals(DropTarget.Swap(Scenes), targetAt(grid2x2(), 398f, 430f))
        // The left edge of a is an outer edge of the full grid: an inner dock as before.
        assertEquals(DropTarget.NewLane(DockFrameLevel.INNER, DockSide.LEFT, 0), targetAt(grid2x2(), 60f, 240f))

        val stacked = DockLayout(
            center = CenterGrid(
                axis = CenterAxis.VERTICAL,
                sections = listOf(CenterSection(listOf(panel("a", Scenes))), CenterSection(listOf(panel("b", GameControls)))),
            ),
        )
        // Stacked, a is 0..1600 × 0..446: its bottom edge faces b, so it swaps instead of creating a bottom lane.
        assertEquals(DropTarget.Swap(Scenes), targetAt(stacked, 800f, 430f))
        assertEquals(DropTarget.SplitCenterPanel("a", DockSide.LEFT), targetAt(stacked, 100f, 240f), "Across the axis it still splits")
    }

    @Test
    fun droppingInTheMiddleSwapsTheTwoPanelsInThePreview() {
        val layout = grid2x2()
        val dragBase = liftedDragBase(layout, Scenes, contentSize, TestDockables.catalog)

        // The middle of d (804..1600 × 454..900, body from 490).
        val preview = assertNotNull(resolveDropPreview(DockProfile(layout), dragBase, 1202f, 700f, desktop, landscape, TestDockables.catalog))

        assertEquals(DropTarget.Swap(StreamInfo), preview.target)
        assertEquals(DockLayoutOperation.SwapDockables(Scenes, StreamInfo), preview.operation)
        assertEquals(listOf(StreamInfo), preview.layout.center.findPanel("a")?.group?.dockableIds)
        assertEquals(listOf(Scenes), preview.layout.center.findPanel("d")?.group?.dockableIds)
        assertEquals(geometryOf(layout).centerPanelRects["d"], preview.highlightRect, "The grid itself does not move")
    }

    @Test
    fun aDockableFromOutsideTheHostCannotSwap() {
        val layout = grid2x2()
        // AudioMixer is not placed: dragged in from a toolbar, for example.
        val dragBase = liftedDragBase(layout, AudioMixer, contentSize, TestDockables.catalog)

        assertNull(resolveDropPreview(DockProfile(layout), dragBase, 1202f, 700f, desktop, landscape, TestDockables.catalog))
    }

    @Test
    fun anEmptyPanelIsFilledByADrop() {
        val withEmptyPanel = DockLayout(
            center = CenterGrid(
                axis = CenterAxis.HORIZONTAL,
                sections = listOf(CenterSection(listOf(panel("a", Scenes))), CenterSection(listOf(CenterPanel("b", group = null)))),
            ),
        )

        assertEquals(DropTarget.EmptyCenterPanel("b"), targetAt(withEmptyPanel, 1200f, 450f))
    }

    @Test
    fun liftingTheOnlyDockableOfAPanelAllowsSplittingWhatRemains() {
        val layout = grid2x2()
        val profile = DockProfile(layout)
        val dragBase = liftedDragBase(layout, StreamInfo, contentSize, TestDockables.catalog)

        // Top triangle of b's body (b: 804..1600 × 36..446): with StreamInfo lifted, b's column has a single panel.
        val preview = assertNotNull(resolveDropPreview(profile, dragBase, 1202f, 100f, desktop, landscape, TestDockables.catalog))

        assertEquals(DropTarget.SplitCenterPanel("b", DockSide.TOP), preview.target)
        val previewColumn = preview.layout.center.sections[1].panels.map { panel -> panel.group?.dockableIds?.single() }
        assertEquals(listOf(StreamInfo, GameControls), previewColumn)
        assertEquals(preview.geometry.centerPanelRects[preview.layout.center.sections[1].panels.first().id], preview.highlightRect)
        assertTrue(preview.changesLayout)
    }

    @Test
    fun droppingFromALaneIntoACenterSplitPreviewsTheNewPanel() {
        val withLane = sideBySide.copy(
            outerFrame = sideBySide.outerFrame.withLanes(
                DockSide.RIGHT,
                listOf(io.github.majorbolt.dockinggrid.DockLane("right", 0.25f, listOf(TabGroup("mixer", listOf(AudioMixer))))),
            ),
        )
        val dragBase = liftedDragBase(withLane, AudioMixer, contentSize, TestDockables.catalog)
        val geometry = dragBase.geometry
        val panelA = geometry.centerPanelRects.getValue("a")

        val preview = assertNotNull(
            resolveDropPreview(DockProfile(withLane), dragBase, panelA.left + panelA.width / 2, panelA.bottom - 120f, desktop, landscape, TestDockables.catalog),
        )

        assertIs<DropTarget.SplitCenterPanel>(preview.target)
        assertEquals(3, preview.layout.center.panels.size)
        assertFalse(preview.layout.outerFrame.lanes(DockSide.RIGHT).isNotEmpty(), "The emptied lane collapses")
    }
}
