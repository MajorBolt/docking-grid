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

class CenterGridOperationsTest {

    private val landscape = LayoutOrientation.LANDSCAPE
    private val desktop = DockingCapabilities.Desktop

    private fun panel(identifier: String, dockableId: DockableId) =
        CenterPanel(identifier, TabGroup(id = "$identifier-group", dockableIds = listOf(dockableId)))

    private fun emptyPanel(identifier: String) = CenterPanel(identifier, group = null)

    private fun centerLayout(axis: CenterAxis, vararg sections: List<CenterPanel>) =
        DockLayout(center = CenterGrid(axis = axis, sections = sections.map { panels -> CenterSection(panels) }))

    private val singleScenes = centerLayout(CenterAxis.HORIZONTAL, listOf(panel("a", Scenes)))

    /** Columns [Scenes above Players] and [GameControls above StreamInfo]. */
    private val grid2x2 = centerLayout(
        CenterAxis.HORIZONTAL,
        listOf(panel("a", Scenes), panel("c", Players)),
        listOf(panel("b", GameControls), panel("d", StreamInfo)),
    )

    private fun DockLayout.applyOrFail(operation: DockLayoutOperation, capabilities: DockingCapabilities = desktop): DockLayout =
        assertIs<DockOperationResult.Applied>(applyOperation(operation, landscape, capabilities), "Expected $operation to be applied").layout

    private fun DockLayout.rejectionOf(operation: DockLayoutOperation, capabilities: DockingCapabilities = desktop): DockRejectionReason =
        assertIs<DockOperationResult.Rejected>(applyOperation(operation, landscape, capabilities)).reason

    /** The dockables of each panel, section by section. */
    private fun DockLayout.centerShape(): List<List<List<DockableId>>> =
        center.sections.map { section -> section.panels.map { panel -> panel.group?.dockableIds.orEmpty() } }

    @Test
    fun splittingASinglePanelPutsTheNewPanelOnTheDropSide() {
        val left = singleScenes.applyOrFail(DockLayoutOperation.SplitCenterPanel(Players, "a", DockSide.LEFT))
        assertEquals(CenterAxis.HORIZONTAL, left.center.axis)
        assertEquals(listOf(listOf(listOf(Players)), listOf(listOf(Scenes))), left.centerShape())

        val bottom = singleScenes.applyOrFail(DockLayoutOperation.SplitCenterPanel(Players, "a", DockSide.BOTTOM))
        assertEquals(CenterAxis.VERTICAL, bottom.center.axis)
        assertEquals(listOf(listOf(listOf(Scenes)), listOf(listOf(Players))), bottom.centerShape())
        assertEquals(0.5f, bottom.center.splitFraction)
    }

    @Test
    fun splittingAcrossTheAxisGrowsToThreeAndFourPanels() {
        val sideBySide = singleScenes.applyOrFail(DockLayoutOperation.SplitCenterPanel(GameControls, "a", DockSide.RIGHT))
        val gameControlsPanelId = sideBySide.center.findPanelOf(sideBySide.findGroupOf(GameControls)!!.id)!!.id

        val three = sideBySide.applyOrFail(DockLayoutOperation.SplitCenterPanel(Players, "a", DockSide.BOTTOM))
        assertEquals(listOf(listOf(listOf(Scenes), listOf(Players)), listOf(listOf(GameControls))), three.centerShape())
        assertEquals(CenterPreset.THREE_FULL_RIGHT, three.center.matchingPreset)

        val four = three.applyOrFail(DockLayoutOperation.SplitCenterPanel(StreamInfo, gameControlsPanelId, DockSide.TOP))
        assertEquals(listOf(listOf(listOf(Scenes), listOf(Players)), listOf(listOf(StreamInfo), listOf(GameControls))), four.centerShape())
        assertEquals(CenterPreset.GRID_2X2, four.center.matchingPreset)
    }

    @Test
    fun splitsBeyondTwoByTwoAreRejected() {
        val sideBySide = centerLayout(CenterAxis.HORIZONTAL, listOf(panel("a", Scenes)), listOf(panel("b", GameControls)))

        assertFalse(sideBySide.center.canSplitPanel("a", DockSide.LEFT))
        assertEquals(
            DockRejectionReason.CENTER_LIMIT_REACHED,
            sideBySide.rejectionOf(DockLayoutOperation.SplitCenterPanel(Players, "a", DockSide.LEFT)),
            "A third column",
        )
        DockSide.entries.forEach { side ->
            assertEquals(DockRejectionReason.CENTER_LIMIT_REACHED, grid2x2.rejectionOf(DockLayoutOperation.SplitCenterPanel(AudioMixer, "a", side)))
        }
        assertEquals(DockRejectionReason.UNKNOWN_TARGET, grid2x2.rejectionOf(DockLayoutOperation.SplitCenterPanel(AudioMixer, "missing", DockSide.TOP)))
    }

    @Test
    fun splittingIsRejectedWithoutTheCenterGrid() {
        assertEquals(
            DockRejectionReason.CENTER_LIMIT_REACHED,
            singleScenes.rejectionOf(DockLayoutOperation.SplitCenterPanel(Players, "a", DockSide.LEFT), desktop.copy(isCenterGridEnabled = false)),
        )
    }

    @Test
    fun aPanelThatLosesItsLastDockableIsRemovedAndTheGridStepsBack() {
        val three = grid2x2.removingDockable(StreamInfo)
        assertEquals(listOf(listOf(listOf(Scenes), listOf(Players)), listOf(listOf(GameControls))), three.centerShape())

        val two = three.removingDockable(Players)
        assertEquals(CenterPreset.SIDE_BY_SIDE, two.center.matchingPreset)

        val one = two.removingDockable(GameControls)
        assertEquals(listOf(listOf(listOf(Scenes))), one.centerShape())

        assertTrue(one.removingDockable(Scenes).center.isEmpty)
    }

    @Test
    fun aRemainingSectionWithTwoPanelsBecomesTwoSectionsAlongTheOtherAxisKeepingItsSplit() {
        val threeFullRight = DockLayout(
            center = CenterGrid(
                axis = CenterAxis.HORIZONTAL,
                sections = listOf(
                    CenterSection(listOf(panel("a", Scenes), panel("c", Players)), splitFraction = 0.3f),
                    CenterSection(listOf(panel("b", GameControls))),
                ),
            ),
        )

        val stacked = threeFullRight.removingDockable(GameControls)

        assertEquals(CenterAxis.VERTICAL, stacked.center.axis)
        assertEquals(listOf(listOf(listOf(Scenes)), listOf(listOf(Players))), stacked.centerShape())
        assertEquals(0.3f, stacked.center.splitFraction)
        assertEquals(stacked, stacked.normalized(), "Normalization is idempotent")
    }

    @Test
    fun presetsCreateEmptyPanelsThatStayUntilFilled() {
        val emptyGrid = DockLayout().applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.GRID_2X2))
        assertEquals(4, emptyGrid.center.panels.size)
        assertTrue(emptyGrid.center.panels.all { panel -> panel.group == null })
        assertEquals(CenterPreset.GRID_2X2, emptyGrid.center.matchingPreset)

        assertTrue(DockLayout().applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.SINGLE)).center.isEmpty, "A single empty panel is the empty center")

        val sideBySide = singleScenes.applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.SIDE_BY_SIDE))
        assertEquals(listOf(listOf(listOf(Scenes)), listOf(emptyList())), sideBySide.centerShape())
        assertEquals("a", sideBySide.center.panels.first().id, "Existing panel ids are reused")
    }

    @Test
    fun presetsKeepTheReadingOrderAndAddSurplusGroupsAsTabsOfTheLastPanel() {
        // Reading order of the 2×2 grid: Scenes, GameControls, Players, StreamInfo.
        val three = grid2x2.applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.THREE_FULL_TOP))
        assertEquals(CenterAxis.VERTICAL, three.center.axis)
        assertEquals(listOf(listOf(listOf(Scenes)), listOf(listOf(GameControls), listOf(Players, StreamInfo))), three.centerShape())

        val single = grid2x2.applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.SINGLE))
        assertEquals(listOf(listOf(listOf(Scenes, GameControls, Players, StreamInfo))), single.centerShape())

        val backToFour = three.applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.GRID_2X2))
        assertEquals(listOf(listOf(Scenes), listOf(GameControls), listOf(Players, StreamInfo), emptyList()), backToFour.center.panelsInReadingOrder.map { panel -> panel.group?.dockableIds.orEmpty() })
    }

    @Test
    fun everyPresetHasItsShape() {
        CenterPreset.entries.forEach { preset ->
            val arranged = grid2x2.applyOrFail(DockLayoutOperation.ArrangeCenter(preset))
            assertEquals(preset, arranged.center.matchingPreset, "$preset")
            assertEquals(preset.panelCount, arranged.center.panels.size, "$preset")
            assertEquals(setOf(Scenes, GameControls, Players, StreamInfo), arranged.placedDockableIds.toSet(), "Nothing is closed by $preset")
        }
    }

    @Test
    fun movingIntoTheCenterFillsTheFirstEmptyPanel() {
        val withPlaceholders = singleScenes.applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.GRID_2X2))

        val filled = withPlaceholders.applyOrFail(DockLayoutOperation.MoveToCenter(Players))
        assertEquals(listOf(listOf(Scenes), listOf(Players), emptyList(), emptyList()), filled.center.panelsInReadingOrder.map { panel -> panel.group?.dockableIds.orEmpty() })

        val lastPanelId = filled.center.panelsInReadingOrder.last().id
        val filledLast = filled.applyOrFail(DockLayoutOperation.MoveToCenterPanel(GameControls, lastPanelId))
        assertEquals(listOf(GameControls), filledLast.center.findPanel(lastPanelId)?.group?.dockableIds)

        val addedAsTab = filledLast.applyOrFail(DockLayoutOperation.MoveToCenterPanel(StreamInfo, lastPanelId))
        assertEquals(listOf(GameControls, StreamInfo), addedAsTab.center.findPanel(lastPanelId)?.group?.dockableIds)
    }

    @Test
    fun emptyPresetPanelsStayWhenAnotherPanelIsEmptied() {
        val sideBySide = singleScenes.applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.SIDE_BY_SIDE))
        val withPlayers = sideBySide.applyOrFail(DockLayoutOperation.MoveToCenterPanel(Players, sideBySide.center.panels.last().id))
            .applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.THREE_FULL_LEFT))

        val withoutPlayers = withPlayers.removingDockable(Players)

        assertEquals(2, withoutPlayers.center.panels.size, "The emptied panel is removed, the preset's empty panel stays")
        assertEquals(listOf(listOf(Scenes), emptyList()), withoutPlayers.center.panelsInReadingOrder.map { panel -> panel.group?.dockableIds.orEmpty() })
    }

    @Test
    fun resizingACenterSplitChangesOnlyThatFraction() {
        val resized = grid2x2.applyOrFail(DockLayoutOperation.ResizeCenterSplit(sectionIndex = null, splitFraction = 0.3f))
            .applyOrFail(DockLayoutOperation.ResizeCenterSplit(sectionIndex = 1, splitFraction = 0.7f))

        assertEquals(0.3f, resized.center.splitFraction)
        assertEquals(listOf(0.5f, 0.7f), resized.center.sections.map { section -> section.splitFraction })
        assertEquals(DockRejectionReason.INVALID_SIZE, grid2x2.rejectionOf(DockLayoutOperation.ResizeCenterSplit(null, Float.NaN)))
        assertEquals(DockRejectionReason.INVALID_SIZE, grid2x2.rejectionOf(DockLayoutOperation.ResizeCenterSplit(null, 1f)))
        assertEquals(DockRejectionReason.UNKNOWN_TARGET, singleScenes.rejectionOf(DockLayoutOperation.ResizeCenterSplit(null, 0.4f)))
        assertEquals(DockRejectionReason.UNKNOWN_TARGET, grid2x2.rejectionOf(DockLayoutOperation.ResizeCenterSplit(2, 0.4f)))
    }

    @Test
    fun resettingMakesEverySplitEven() {
        val resized = grid2x2.applyOrFail(DockLayoutOperation.ResizeCenterSplit(sectionIndex = null, splitFraction = 0.3f))
            .applyOrFail(DockLayoutOperation.ResizeCenterSplit(sectionIndex = 1, splitFraction = 0.7f))
        assertTrue(resized.center.hasUnevenSplits)

        val reset = resized.applyOrFail(DockLayoutOperation.ResetCenterSplits)

        assertFalse(reset.center.hasUnevenSplits)
        assertEquals(grid2x2.center, reset.center)
    }

    @Test
    fun swappingExchangesTwoDockablesWhereverTheyAre() {
        val withTabsAndLane = grid2x2.copy(
            outerFrame = DockFrame(mapOf(DockSide.RIGHT to listOf(DockLane("right", 0.25f, listOf(TabGroup("mixer", listOf(AudioMixer))))))),
        ).applyOrFail(DockLayoutOperation.MoveToCenterPanel(GameControls, "a"))

        val swapped = withTabsAndLane.applyOrFail(DockLayoutOperation.SwapDockables(AudioMixer, Players))

        assertEquals(listOf(AudioMixer), swapped.center.findPanel("c")?.group?.dockableIds)
        assertEquals(listOf(Players), swapped.findGroup("mixer")?.dockableIds)

        val swappedTab = withTabsAndLane.applyOrFail(DockLayoutOperation.SwapDockables(GameControls, StreamInfo))
        assertEquals(listOf(Scenes, StreamInfo), swappedTab.center.findPanel("a")?.group?.dockableIds, "The tab keeps its position")
        assertEquals(StreamInfo, swappedTab.center.findPanel("a")?.group?.selectedDockableId)
        assertEquals(listOf(GameControls), swappedTab.center.findPanel("d")?.group?.dockableIds)

        assertEquals(DockRejectionReason.UNKNOWN_DOCKABLE, grid2x2.rejectionOf(DockLayoutOperation.SwapDockables(AudioMixer, Scenes)), "Not placed")
    }

    @Test
    fun malformedGridsAreRepaired() {
        val malformed = DockLayout(
            center = CenterGrid(
                axis = CenterAxis.VERTICAL,
                sections = listOf(
                    CenterSection(listOf(panel("a", Scenes), panel("a", Players), panel("x", AudioMixer)), splitFraction = Float.NaN),
                    CenterSection(emptyList()),
                    CenterSection(listOf(panel("b", GameControls))),
                ),
                splitFraction = 7f,
            ),
        )

        val repaired = malformed.normalized()

        assertEquals(
            listOf(listOf(listOf(Scenes), listOf(AudioMixer)), listOf(listOf(GameControls))),
            repaired.centerShape(),
            "A duplicate panel id and empty sections are dropped before the 2×2 limit applies",
        )
        assertEquals(MAXIMUM_CENTER_SPLIT_FRACTION, repaired.center.splitFraction)
        assertEquals(0.5f, repaired.center.sections.first().splitFraction, "An invalid split becomes even")
        assertTrue(DockLayout(center = CenterGrid(sections = listOf(CenterSection(listOf(emptyPanel("e")))))).normalized().center.isEmpty)
    }

    @Test
    fun aDockableWhosePanelWasRemovedReopensInTheFirstPanel() {
        val defaultState = defaultWindowDockState(TestDockables.catalog, desktop)
        val centerPanelId = defaultState.profile(landscape).layout.center.panels.single().id
        val withPlayersInTheCenter = assertIs<DockStateTransitionResult.Applied>(
            defaultState.applyingOperation(DockLayoutOperation.SplitCenterPanel(Players, centerPanelId, DockSide.RIGHT), landscape, desktop),
        ).state
        assertEquals(2, withPlayersInTheCenter.profile(landscape).layout.center.panels.size)

        val reopened = withPlayersInTheCenter.closingDockable(Players).openingDockable(Players, TestDockables.catalog, desktop)

        val layout = reopened.profile(landscape).layout
        assertEquals(listOf(listOf(listOf(GameControls, Players))), layout.centerShape())
    }

    @Test
    fun withoutTheCenterGridAStoredGridIsMergedIntoOneGroup() {
        val state = WindowDockState(
            openDockableIds = setOf(Scenes, GameControls, Players, StreamInfo),
            profiles = mapOf(landscape to DockProfile(grid2x2), LayoutOrientation.PORTRAIT to DockProfile(grid2x2)),
        )

        val reconciled = state.reconciled(TestDockables.catalog, desktop.copy(isCenterGridEnabled = false))

        val layout = reconciled.profile(landscape).layout
        assertEquals(1, layout.center.panels.size)
        assertEquals(listOf(Scenes, GameControls, Players, StreamInfo), layout.center.groups.single().dockableIds)
        assertEquals(CenterPreset.SINGLE, layout.center.matchingPreset)
    }
}
