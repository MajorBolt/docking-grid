package io.github.majorbolt.dockinggrid

import io.github.majorbolt.dockinggrid.TestDockables.AudioMixer
import io.github.majorbolt.dockinggrid.TestDockables.GameControls
import io.github.majorbolt.dockinggrid.TestDockables.Players
import io.github.majorbolt.dockinggrid.TestDockables.Scenes
import io.github.majorbolt.dockinggrid.TestDockables.StreamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HeaderStylesTest {

    private val landscape = LayoutOrientation.LANDSCAPE
    private val desktop = DockingCapabilities.Desktop
    private val vertical = GroupHeaderStyle(isVertical = true)
    private val autoHiding = GroupHeaderStyle(autoHides = true)

    private fun panel(identifier: String, dockableId: DockableId, style: GroupHeaderStyle = GroupHeaderStyle()) =
        CenterPanel(identifier, TabGroup(id = "$identifier-group", dockableIds = listOf(dockableId), headerStyle = style))

    /** Columns [Scenes above Players] and [GameControls above StreamInfo]. */
    private fun grid2x2(autoHidesHeader: Boolean = false, scenesStyle: GroupHeaderStyle = GroupHeaderStyle()) = DockLayout(
        center = CenterGrid(
            axis = CenterAxis.HORIZONTAL,
            sections = listOf(
                CenterSection(listOf(panel("a", Scenes, scenesStyle), panel("c", Players))),
                CenterSection(listOf(panel("b", GameControls), panel("d", StreamInfo))),
            ),
            autoHidesHeader = autoHidesHeader,
        ),
    )

    private fun DockLayout.applyOrFail(operation: DockLayoutOperation): DockLayout =
        assertIs<DockOperationResult.Applied>(applyOperation(operation, landscape, desktop), "Expected $operation to be applied").layout

    @Test
    fun theStyleBelongsToTheGroupAndNewGroupsStartWithTheDefault() {
        val layout = grid2x2().applyOrFail(DockLayoutOperation.SetGroupHeaderStyle("a-group", vertical))

        val withTab = layout.applyOrFail(DockLayoutOperation.MoveToGroup(Players, "a-group", tabIndex = 1))
        assertEquals(vertical, withTab.findGroup("a-group")?.headerStyle, "Adding a tab keeps the style")

        val swapped = layout.applyOrFail(DockLayoutOperation.SwapDockables(Scenes, GameControls))
        assertEquals(vertical, swapped.findGroup("a-group")?.headerStyle, "The group keeps its style, the dockables move")
        assertEquals(listOf(GameControls), swapped.findGroup("a-group")?.dockableIds)

        val split = withTab.applyOrFail(DockLayoutOperation.SplitCenterPanel(AudioMixer, "a", DockSide.TOP))
        assertEquals(GroupHeaderStyle(), split.findGroupOf(AudioMixer)?.headerStyle, "A new group has the default style")

        assertIs<DockOperationResult.Rejected>(layout.applyOperation(DockLayoutOperation.SetGroupHeaderStyle("missing", vertical), landscape, desktop))
    }

    @Test
    fun aMovedDockKeepsItsHeaderStyleButATabMovedIntoAnotherDockTakesThatDocksStyle() {
        // Default layout: LEFT lane with Scenes above StreamInfo (each its own group), GameControls in the center.
        val defaultLayout = defaultWindowDockState(TestDockables.catalog, desktop).profile(landscape).layout
        val layout = defaultLayout.applyOrFail(DockLayoutOperation.SetGroupHeaderStyle(defaultLayout.findGroupOf(Scenes)!!.id, vertical))

        val movedToNewLane = layout.applyOrFail(DockLayoutOperation.MoveToNewLane(Scenes, DockFrameLevel.OUTER, DockSide.BOTTOM, 0))
        assertEquals(vertical, movedToNewLane.findGroupOf(Scenes)?.headerStyle, "The dock moved as a whole")

        val split = movedToNewLane.applyOrFail(DockLayoutOperation.MoveToGroup(StreamInfo, movedToNewLane.findGroupOf(Scenes)!!.id, 1))
            .applyOrFail(DockLayoutOperation.MoveToNewLane(StreamInfo, DockFrameLevel.OUTER, DockSide.LEFT, 0))
        assertEquals(vertical, split.findGroupOf(StreamInfo)?.headerStyle, "A new dock takes the style of the dock its tab came from")

        val intoCenter = layout.applyOrFail(DockLayoutOperation.MoveToGroup(Scenes, layout.findGroupOf(GameControls)!!.id, 1))
        assertEquals(GroupHeaderStyle(), intoCenter.findGroupOf(Scenes)?.headerStyle, "Into an existing dock: that dock's style")
    }

    @Test
    fun aClosedDockReopensWithItsHeaderStyle() {
        val state = defaultWindowDockState(TestDockables.catalog, desktop)
        val scenesGroupId = state.profile(landscape).layout.findGroupOf(Scenes)!!.id
        val styled = assertIs<DockStateTransitionResult.Applied>(
            state.applyingOperation(DockLayoutOperation.SetGroupHeaderStyle(scenesGroupId, autoHiding), landscape, desktop),
        ).state

        val closed = styled.closingDockable(Scenes)
        assertEquals(autoHiding, closed.profile(landscape).placementMemory[Scenes]?.headerStyle)
        val reopened = closed.openingDockable(Scenes, TestDockables.catalog, desktop)

        assertEquals(autoHiding, reopened.profile(landscape).layout.findGroupOf(Scenes)?.headerStyle)
    }

    @Test
    fun aPositionKeepsItsHeaderStyleWhenTheGridShrinksAndGrowsAgain() {
        // The reported case: bottom right (StreamInfo) vertical; three panels (the bottom ones merge into tabs); 2×2
        // again; then a dock dragged into the empty bottom-right panel.
        val layout = grid2x2().applyOrFail(DockLayoutOperation.SetGroupHeaderStyle("d-group", vertical))

        val three = layout.applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.THREE_FULL_BOTTOM))
        assertEquals(listOf(Players, StreamInfo), three.center.panelsInReadingOrder.last().group?.dockableIds, "Merged into tabs")

        val fourAgain = three.applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.GRID_2X2))
        val bottomRight = fourAgain.center.panelsInReadingOrder.last()
        assertEquals(null, bottomRight.group)
        assertEquals(vertical, bottomRight.headerStyle, "The empty panel remembers the style of its position")

        val filled = fourAgain.applyOrFail(DockLayoutOperation.MoveToCenterPanel(StreamInfo, bottomRight.id))
        assertEquals(vertical, filled.findGroupOf(StreamInfo)?.headerStyle, "Not the normal header of the tabs it came from")
        assertEquals(GroupHeaderStyle(), filled.findGroupOf(Players)?.headerStyle)
    }

    @Test
    fun theGridKeepsItsAutoHideWhileItChanges() {
        val layout = grid2x2().applyOrFail(DockLayoutOperation.SetCenterHeaderAutoHide(true))
        assertTrue(layout.center.autoHidesHeader)

        val three = layout.removingDockable(StreamInfo)
        assertTrue(three.center.autoHidesHeader)
        assertTrue(three.applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.STACKED)).center.autoHidesHeader)
    }

    @Test
    fun theGridsAutoHideHidesThePanelHeadersOnlyWhenItTakesPriority() {
        val layout = grid2x2(autoHidesHeader = true)

        assertTrue(layout.isHeaderAutoHidden("b-group", gridHidesPanelHeaders = true))
        assertTrue(layout.isHeaderAutoHideForcedByGrid("b-group", gridHidesPanelHeaders = true))
        assertFalse(layout.isHeaderAutoHidden("b-group", gridHidesPanelHeaders = false), "Each panel follows its own choice")

        val panelHides = grid2x2(scenesStyle = autoHiding)
        assertTrue(panelHides.isHeaderAutoHidden("a-group", gridHidesPanelHeaders = true))
        assertFalse(panelHides.isHeaderAutoHidden("b-group", gridHidesPanelHeaders = true), "The grid does not hide anything")
    }

    @Test
    fun aSinglePanelHasNoGridHeaderSoNothingIsForced() {
        val single = DockLayout(center = CenterGrid.single(TabGroup("only", listOf(Scenes))).copy(autoHidesHeader = true))

        assertFalse(single.center.hasHeader)
        assertFalse(single.isHeaderAutoHidden("only", gridHidesPanelHeaders = true))
    }

    @Test
    fun verticalHeadersSitOnTheOuterSide() {
        val defaultLayout = defaultWindowDockState(TestDockables.catalog, desktop).profile(landscape).layout
        assertEquals(DockSide.LEFT, defaultLayout.verticalHeaderSide(defaultLayout.findGroupOf(Scenes)!!.id), "LEFT lane")
        assertEquals(DockSide.RIGHT, defaultLayout.verticalHeaderSide(defaultLayout.findGroupOf(Players)!!.id), "RIGHT lane")
        assertEquals(DockSide.LEFT, defaultLayout.verticalHeaderSide(defaultLayout.findGroupOf(GameControls)!!.id), "Single center panel")

        val grid = grid2x2()
        assertEquals(DockSide.LEFT, grid.verticalHeaderSide("a-group"))
        assertEquals(DockSide.LEFT, grid.verticalHeaderSide("c-group"))
        assertEquals(DockSide.RIGHT, grid.verticalHeaderSide("b-group"))
        assertEquals(DockSide.RIGHT, grid.verticalHeaderSide("d-group"))

        val threeFullTop = grid.applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.THREE_FULL_TOP))
        val topGroupId = threeFullTop.center.sections[0].panels.single().group!!.id
        val bottomRightGroupId = threeFullTop.center.sections[1].panels[1].group!!.id
        assertEquals(DockSide.LEFT, threeFullTop.verticalHeaderSide(topGroupId), "Full width: left")
        assertEquals(DockSide.RIGHT, threeFullTop.verticalHeaderSide(bottomRightGroupId))
    }
}
