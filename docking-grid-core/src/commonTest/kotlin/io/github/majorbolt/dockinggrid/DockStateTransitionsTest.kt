package io.github.majorbolt.dockinggrid

import io.github.majorbolt.dockinggrid.TestDockables.AudioMixer
import io.github.majorbolt.dockinggrid.TestDockables.GameControls
import io.github.majorbolt.dockinggrid.TestDockables.Players
import io.github.majorbolt.dockinggrid.TestDockables.Scenes
import io.github.majorbolt.dockinggrid.TestDockables.StreamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DockStateTransitionsTest {

    private val desktop = DockingCapabilities.Desktop
    private val landscape = LayoutOrientation.LANDSCAPE
    private val portrait = LayoutOrientation.PORTRAIT

    private fun WindowDockState.layout(orientation: LayoutOrientation = landscape) = profile(orientation).layout

    private fun WindowDockState.applyOrFail(
        operation: DockLayoutOperation,
        orientation: LayoutOrientation = landscape,
        capabilities: DockingCapabilities = desktop,
    ): WindowDockState {
        val result = applyingOperation(operation, orientation, capabilities)
        return assertIs<DockStateTransitionResult.Applied>(result, "Expected $operation to be applied").state
    }

    @Test
    fun defaultStateMatchesThePreviousDefaultArrangement() {
        val state = defaultWindowDockState(TestDockables.catalog, desktop)

        assertEquals(TestDockables.ALL.toSet(), state.openDockableIds)
        val layout = state.layout()
        val leftLane = layout.outerFrame.lanes(DockSide.LEFT).single()
        assertEquals(listOf(listOf(Scenes), listOf(StreamInfo)), leftLane.groups.map { group -> group.dockableIds })
        assertEquals(0.25f, leftLane.extentFraction)
        val rightLane = layout.outerFrame.lanes(DockSide.RIGHT).single()
        assertEquals(listOf(listOf(Players), listOf(AudioMixer)), rightLane.groups.map { group -> group.dockableIds })
        assertEquals(listOf(GameControls), layout.centerGroup?.dockableIds)
        assertTrue(layout.innerFrame.sides.isEmpty())
        assertEquals(state.layout(landscape), state.layout(portrait))
    }

    @Test
    fun closeRemovesTheWindowFromEveryProfileAndCollapsesEmptyContainers() {
        val state = defaultWindowDockState(TestDockables.catalog, desktop)
            .closingDockable(Players)
            .closingDockable(AudioMixer)

        assertFalse(Players in state.openDockableIds)
        LayoutOrientation.entries.forEach { orientation ->
            assertTrue(state.layout(orientation).outerFrame.lanes(DockSide.RIGHT).isEmpty())
        }
    }

    @Test
    fun reopenRestoresTheTabPositionInTheRememberedGroup() {
        val withTabs = defaultWindowDockState(TestDockables.catalog, desktop)
            .applyOrFail(DockLayoutOperation.MoveToGroup(Players, groupIdOf(defaultWindowDockState(TestDockables.catalog, desktop), GameControls), tabIndex = 0))
        val centerGroupId = assertNotNull(withTabs.layout().centerGroup).id

        val reopened = withTabs.closingDockable(Players).openingDockable(Players, TestDockables.catalog, desktop)

        val centerGroup = reopened.layout().findGroup(centerGroupId)
        assertEquals(listOf(Players, GameControls), centerGroup?.dockableIds)
    }

    @Test
    fun reopenRestoresARemovedLaneAtItsIndexWithItsRememberedWidth() {
        val resizedState = defaultWindowDockState(TestDockables.catalog, desktop)
            .closingDockable(AudioMixer)
            .let { state ->
                val rightLaneId = state.layout().outerFrame.lanes(DockSide.RIGHT).single().id
                state.applyOrFail(DockLayoutOperation.ResizeLane(rightLaneId, 0.4f))
            }

        val reopened = resizedState.closingDockable(Players).openingDockable(Players, TestDockables.catalog, desktop)

        val rightLane = reopened.layout().outerFrame.lanes(DockSide.RIGHT).single()
        assertEquals(listOf(Players), rightLane.groups.single().dockableIds)
        assertEquals(0.4f, rightLane.extentFraction)
    }

    @Test
    fun reopenFallsBackToTheDefaultLocationWhenTheRememberedSideIsNotAllowed() {
        val tablet = TestCapabilities.Tablet
        val stateWithInnerMemory = defaultWindowDockState(TestDockables.catalog, desktop)
            .applyOrFail(DockLayoutOperation.MoveToNewLane(Players, DockFrameLevel.INNER, DockSide.BOTTOM, 0))
            .closingDockable(Players)

        val reopenedOnTablet = stateWithInnerMemory.openingDockable(Players, TestDockables.catalog, tablet)

        val layout = reopenedOnTablet.layout()
        assertTrue(layout.innerFrame.sides.isEmpty())
        assertTrue(layout.outerFrame.lanes(DockSide.BOTTOM).single().groups.single().dockableIds == listOf(Players))
    }

    @Test
    fun operationsChangeOnlyTheActiveOrientation() {
        val state = defaultWindowDockState(TestDockables.catalog, desktop)
            .applyOrFail(DockLayoutOperation.MoveToNewLane(Scenes, DockFrameLevel.OUTER, DockSide.BOTTOM, 0), orientation = portrait)

        assertEquals(defaultWindowDockState(TestDockables.catalog, desktop).layout(landscape), state.layout(landscape))
        assertEquals(listOf(Scenes), state.layout(portrait).outerFrame.lanes(DockSide.BOTTOM).single().groups.single().dockableIds)
    }

    @Test
    fun windowOpenedInOneOrientationAppearsAtItsOwnPositionInEveryOrientation() {
        val state = defaultWindowDockState(TestDockables.catalog, desktop)
            .applyOrFail(DockLayoutOperation.MoveToNewLane(Scenes, DockFrameLevel.OUTER, DockSide.BOTTOM, 0), orientation = portrait)
            .closingDockable(Scenes)
            .openingDockable(Scenes, TestDockables.catalog, desktop)

        assertEquals(listOf(Scenes), state.layout(portrait).outerFrame.lanes(DockSide.BOTTOM).single().groups.single().dockableIds)
        assertEquals(Scenes, state.layout(landscape).outerFrame.lanes(DockSide.LEFT).single().groups.first().dockableIds.single())
    }

    @Test
    fun operationsOnClosedWindowsAreRejected() {
        val state = defaultWindowDockState(TestDockables.catalog, desktop).closingDockable(Scenes)

        val result = state.applyingOperation(DockLayoutOperation.MoveToCenter(Scenes), landscape, desktop)

        assertEquals(DockStateTransitionResult.Rejected(DockRejectionReason.UNKNOWN_DOCKABLE), result)
    }

    @Test
    fun reconcileRemovesClosedWindowsAndPlacesMissingOpenOnes() {
        val inconsistentState = WindowDockState(
            openDockableIds = setOf(Scenes, Players),
            profiles = mapOf(
                landscape to DockProfile(
                    layout = DockLayout(centerGroup = TabGroup(id = "center", dockableIds = listOf(Scenes, AudioMixer))),
                ),
            ),
        )

        val reconciledState = inconsistentState.reconciled(TestDockables.catalog, desktop)

        LayoutOrientation.entries.forEach { orientation ->
            val placed = reconciledState.layout(orientation).placedDockableIds.toSet()
            assertEquals(setOf(Scenes, Players), placed, "Orientation $orientation")
        }
    }

    @Test
    fun reconcileEmptiesTheInnerFrameOnTabletsAndReplacesItsWindows() {
        val desktopState = defaultWindowDockState(TestDockables.catalog, desktop)
            .applyOrFail(DockLayoutOperation.MoveToNewLane(Scenes, DockFrameLevel.INNER, DockSide.TOP, 0))

        val tabletState = desktopState.reconciled(TestDockables.catalog, TestCapabilities.Tablet)

        assertTrue(tabletState.layout().innerFrame.sides.isEmpty())
        assertTrue(tabletState.layout().contains(Scenes))
    }

    @Test
    fun reconcileRemovesIdentifiersThatAreNotInTheCatalogEverywhere() {
        val removedDockable = DockableId("removed_in_newer_version")
        val storedState = WindowDockState(
            openDockableIds = setOf(Scenes, removedDockable),
            profiles = mapOf(
                landscape to DockProfile(
                    layout = DockLayout(
                        outerFrame = DockFrame(
                            mapOf(DockSide.LEFT to listOf(DockLane("left", 0.25f, listOf(TabGroup("left-group", listOf(removedDockable)))))),
                        ),
                        centerGroup = TabGroup(id = "center", dockableIds = listOf(removedDockable, Scenes), selectedDockableId = removedDockable),
                    ),
                    placementMemory = mapOf(
                        removedDockable to PlacementMemory(DockLocation.Center, "center", null, 0, 0, 0, null),
                    ),
                ),
            ),
        )

        val reconciledState = storedState.reconciled(TestDockables.catalog, desktop)

        assertEquals(setOf(Scenes), reconciledState.openDockableIds)
        LayoutOrientation.entries.forEach { orientation ->
            val profile = reconciledState.profile(orientation)
            assertEquals(listOf(Scenes), profile.layout.placedDockableIds, "Orientation $orientation")
            assertFalse(removedDockable in profile.placementMemory, "No memory of unknown dockables ($orientation)")
        }
        // The stored landscape profile: the lane that only held the unknown dockable collapses.
        assertTrue(reconciledState.layout(landscape).outerFrame.sides.isEmpty())
        assertEquals(Scenes, reconciledState.layout(landscape).centerGroup?.selectedDockableId)
    }

    @Test
    fun openingAnIdentifierThatIsNotInTheCatalogChangesNothing() {
        val state = defaultWindowDockState(TestDockables.catalog, desktop).closingDockable(Scenes)

        assertEquals(state, state.openingDockable(DockableId("unknown"), TestDockables.catalog, desktop))
    }

    @Test
    fun catalogRejectsDuplicateIdentifiersAndResolvesGroupMinimumSizes() {
        val duplicate = Dockable(Scenes, "Duplicate", 0, 0, DockLocation.Center)
        assertFailsWith<IllegalArgumentException> { DockableCatalog(TestDockables.catalog.dockables + duplicate) }

        val group = TabGroup(id = "group", dockableIds = listOf(Scenes, StreamInfo, DockableId("unknown")))
        assertEquals(240, TestDockables.catalog.minimumWidthDp(group))
        assertEquals(200, TestDockables.catalog.minimumHeightDp(group))
    }

    @Test
    fun toggleOpensAndCloses() {
        val closed = defaultWindowDockState(TestDockables.catalog, desktop).togglingDockable(Scenes, TestDockables.catalog, desktop)
        assertFalse(Scenes in closed.openDockableIds)
        assertTrue(Scenes in closed.togglingDockable(Scenes, TestDockables.catalog, desktop).openDockableIds)
    }

    private fun groupIdOf(state: WindowDockState, dockableId: DockableId): String =
        state.layout().allGroups.first { group -> dockableId in group.dockableIds }.id

    @Test
    fun aDropOfAClosedDockableOpensItAtTheTargetInTheCurrentOrientationOnly() {
        val stateWithoutPlayers = defaultWindowDockState(TestDockables.catalog, desktop).closingDockable(Players)
        val operation = DockLayoutOperation.MoveToNewLane(Players, DockFrameLevel.OUTER, DockSide.BOTTOM, 0)

        val result = stateWithoutPlayers.applyingDropOperation(operation, landscape, TestDockables.catalog, desktop)

        val state = assertIs<DockStateTransitionResult.Applied>(result).state
        assertTrue(Players in state.openDockableIds)
        assertEquals(listOf(Players), state.layout(landscape).outerFrame.lanes(DockSide.BOTTOM).single().groups.single().dockableIds)
        assertFalse(Players in state.profile(landscape).placementMemory)
        // The other orientation reopens it where it was before (the RIGHT dock), not at the drop target.
        assertTrue(state.layout(portrait).outerFrame.lanes(DockSide.BOTTOM).isEmpty())
        assertEquals(Players, state.layout(portrait).outerFrame.lanes(DockSide.RIGHT).single().groups.first().dockableIds.single())
        assertFalse(Players in state.profile(portrait).placementMemory)
    }

    @Test
    fun aDropOfAnOpenDockableIsAnOrdinaryMove() {
        val state = defaultWindowDockState(TestDockables.catalog, desktop)
        val operation = DockLayoutOperation.MoveToNewLane(Scenes, DockFrameLevel.OUTER, DockSide.BOTTOM, 0)

        assertEquals(
            state.applyingOperation(operation, landscape, desktop),
            state.applyingDropOperation(operation, landscape, TestDockables.catalog, desktop),
        )
    }

    @Test
    fun aDropIsRejectedForUnknownDockablesTabSelectionAndRejectedTargets() {
        val stateWithoutPlayers = defaultWindowDockState(TestDockables.catalog, desktop).closingDockable(Players)

        val unknown = stateWithoutPlayers.applyingDropOperation(
            DockLayoutOperation.MoveToCenter(DockableId("unknown")), landscape, TestDockables.catalog, desktop,
        )
        val selectTab = stateWithoutPlayers.applyingDropOperation(DockLayoutOperation.SelectTab(Players), landscape, TestDockables.catalog, desktop)
        val unknownGroup = stateWithoutPlayers.applyingDropOperation(
            DockLayoutOperation.MoveToGroup(Players, "missing", 0), landscape, TestDockables.catalog, desktop,
        )

        assertEquals(DockStateTransitionResult.Rejected(DockRejectionReason.UNKNOWN_DOCKABLE), unknown)
        assertEquals(DockStateTransitionResult.Rejected(DockRejectionReason.UNKNOWN_DOCKABLE), selectTab)
        assertIs<DockStateTransitionResult.Rejected>(unknownGroup)
    }
}
