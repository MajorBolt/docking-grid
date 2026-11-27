package io.github.majorbolt.dockinggrid

import io.github.majorbolt.dockinggrid.TestDockables.AudioMixer
import io.github.majorbolt.dockinggrid.TestDockables.GameControls
import io.github.majorbolt.dockinggrid.TestDockables.Players
import io.github.majorbolt.dockinggrid.TestDockables.Scenes
import io.github.majorbolt.dockinggrid.TestDockables.StreamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DockLayoutOperationsTest {

    private val landscape = LayoutOrientation.LANDSCAPE

    private fun group(identifier: String, dockableIds: List<DockableId>, sizeFraction: Float = 1f) =
        TabGroup(id = identifier, dockableIds = dockableIds, sizeFraction = sizeFraction)

    private fun group(identifier: String, dockableId: DockableId, sizeFraction: Float = 1f) =
        group(identifier, listOf(dockableId), sizeFraction)

    private fun lane(identifier: String, extentFraction: Float, vararg groups: TabGroup) =
        DockLane(id = identifier, extentFraction = extentFraction, groups = groups.toList())

    /** LEFT: [edge lane: Scenes | StreamInfo], [inner lane: Players]; CENTER: GameControls; RIGHT: AudioMixer. */
    private val twoLeftLanesLayout = DockLayout(
        outerFrame = DockFrame(
            sides = mapOf(
                DockSide.LEFT to listOf(
                    lane("left-edge", 0.2f, group("scenes-group", Scenes, sizeFraction = 0.5f), group("stream-group", StreamInfo, sizeFraction = 0.5f)),
                    lane("left-inner", 0.15f, group("players-group", Players)),
                ),
                DockSide.RIGHT to listOf(lane("right-edge", 0.3f, group("mixer-group", AudioMixer))),
            ),
        ),
        center = CenterGrid.single(group("center-group", GameControls)),
    )

    private fun DockLayout.applyOrFail(
        operation: DockLayoutOperation,
        capabilities: DockingCapabilities = DockingCapabilities.Desktop,
    ): DockLayout {
        val result = applyOperation(operation, landscape, capabilities)
        return assertIs<DockOperationResult.Applied>(result, "Expected $operation to be applied").layout
    }

    private fun DockLayout.groupOrFail(groupId: String): TabGroup = assertNotNull(findGroup(groupId), "Missing group $groupId")

    @Test
    fun movingTheOnlyWindowOutOfTheEdgeLaneCollapsesItAndTheInnerLaneMovesToTheEdgeKeepingItsWidth() {
        val result = twoLeftLanesLayout
            .applyOrFail(DockLayoutOperation.MoveToCenter(Scenes))
            .applyOrFail(DockLayoutOperation.MoveToCenter(StreamInfo))

        val leftLanes = result.outerFrame.lanes(DockSide.LEFT)
        assertEquals(listOf("left-inner"), leftLanes.map { lane -> lane.id })
        assertEquals(0.15f, leftLanes.single().extentFraction)
        assertEquals(0.3f, result.outerFrame.lanes(DockSide.RIGHT).single().extentFraction)
    }

    @Test
    fun newLaneOnTheOuterSideIsInsertedAtTheEdgeAndExistingLanesKeepTheirExtent() {
        val result = twoLeftLanesLayout.applyOrFail(
            DockLayoutOperation.MoveToNewLane(AudioMixer, DockFrameLevel.OUTER, DockSide.LEFT, laneIndex = 0),
        )

        val leftLanes = result.outerFrame.lanes(DockSide.LEFT)
        assertEquals(listOf(AudioMixer), leftLanes[0].groups.single().dockableIds)
        assertEquals(listOf("left-edge", "left-inner"), leftLanes.drop(1).map { lane -> lane.id })
        assertEquals(listOf(0.2f, 0.15f), leftLanes.drop(1).map { lane -> lane.extentFraction })
        assertEquals(DockingDefaults.laneExtentFraction(DockSide.LEFT), leftLanes[0].extentFraction)
        assertTrue(result.outerFrame.lanes(DockSide.RIGHT).isEmpty(), "Source side must collapse")
    }

    @Test
    fun newLaneOnTheInnerSideIsInsertedNextToTheCenter() {
        val result = twoLeftLanesLayout.applyOrFail(
            DockLayoutOperation.MoveToNewLane(AudioMixer, DockFrameLevel.OUTER, DockSide.LEFT, laneIndex = 2),
        )

        val leftLanes = result.outerFrame.lanes(DockSide.LEFT)
        assertEquals(listOf("left-edge", "left-inner"), leftLanes.take(2).map { lane -> lane.id })
        assertEquals(listOf(AudioMixer), leftLanes[2].groups.single().dockableIds)
    }

    @Test
    fun splittingAGroupHalvesOnlyThatGroup() {
        // Regression B7: AudioMixer splits StreamInfo (lower group); Scenes keeps its half.
        val result = twoLeftLanesLayout.applyOrFail(
            DockLayoutOperation.MoveToNewGroup(AudioMixer, "left-edge", groupIndex = 1, splitGroupId = "stream-group"),
        )

        val edgeLane = result.outerFrame.lanes(DockSide.LEFT).first()
        assertEquals(listOf(Scenes, AudioMixer, StreamInfo), edgeLane.groups.map { group -> group.dockableIds.single() })
        assertEquals(listOf(0.5f, 0.25f, 0.25f), edgeLane.groups.map { group -> group.sizeFraction })
    }

    @Test
    fun withoutASplitGroupTheGroupBeforeTheInsertionPointIsSplit() {
        val afterScenes = twoLeftLanesLayout.applyOrFail(DockLayoutOperation.MoveToNewGroup(AudioMixer, "left-edge", groupIndex = 1))
        assertEquals(listOf(0.25f, 0.25f, 0.5f), afterScenes.outerFrame.lanes(DockSide.LEFT).first().groups.map { group -> group.sizeFraction })

        val atTheStart = twoLeftLanesLayout.applyOrFail(DockLayoutOperation.MoveToNewGroup(AudioMixer, "left-edge", groupIndex = 0))
        assertEquals(listOf(0.25f, 0.25f, 0.5f), atTheStart.outerFrame.lanes(DockSide.LEFT).first().groups.map { group -> group.sizeFraction })
    }

    @Test
    fun splittingAndMovingTheNewWindowOutAgainRestoresTheFractionsExactly() {
        val split = threeGroupLaneLayout.applyOrFail(
            DockLayoutOperation.MoveToNewGroup(AudioMixer, "left", groupIndex = 2, splitGroupId = "b"),
        )
        val restored = split.applyOrFail(DockLayoutOperation.MoveToCenter(AudioMixer))

        val originalFractions = threeGroupLaneLayout.groupFractions("left")
        val restoredFractions = restored.groupFractions("left")
        assertEquals(originalFractions.keys, restoredFractions.keys)
        originalFractions.forEach { (groupId, fraction) -> assertEquals(fraction, restoredFractions.getValue(groupId), 0.000001f, groupId) }
    }

    @Test
    fun movingTheLastWindowOfAGroupRedistributesItsShareToTheRemainingGroups() {
        val result = twoLeftLanesLayout.applyOrFail(DockLayoutOperation.MoveToCenter(StreamInfo))

        val edgeLane = result.outerFrame.lanes(DockSide.LEFT).first()
        assertEquals(listOf("scenes-group"), edgeLane.groups.map { group -> group.id })
        assertEquals(1f, edgeLane.groups.single().sizeFraction, 0.0001f)
    }

    /** LEFT: one lane with A (Scenes), B (StreamInfo) and C (Players), each a third; RIGHT: AudioMixer. */
    private val threeGroupLaneLayout = DockLayout(
        outerFrame = DockFrame(
            sides = mapOf(
                DockSide.LEFT to listOf(
                    lane(
                        "left",
                        0.25f,
                        group("a", Scenes, sizeFraction = 1f / 3f),
                        group("b", StreamInfo, sizeFraction = 1f / 3f),
                        group("c", Players, sizeFraction = 1f / 3f),
                    ),
                ),
                DockSide.RIGHT to listOf(lane("right", 0.25f, group("mixer-group", AudioMixer))),
            ),
        ),
        center = CenterGrid.single(group("center-group", GameControls)),
    )

    private fun DockLayout.groupFractions(laneId: String): Map<String, Float> {
        val location = assertNotNull(findLane(laneId))
        return frame(location.frameLevel).lanes(location.side)[location.laneIndex].groups.associate { group -> group.id to group.sizeFraction }
    }

    @Test
    fun mergingAGroupIntoItsNeighbourGivesItTheWholeShareAndLeavesOthersUntouched() {
        // Regression B5: A keeps its third, the merged tab group (B, C) gets two thirds.
        val result = threeGroupLaneLayout.applyOrFail(DockLayoutOperation.MoveToGroup(Players, "b", tabIndex = 1))

        val fractions = result.groupFractions("left")
        assertEquals(setOf("a", "b"), fractions.keys)
        assertEquals(1f / 3f, fractions.getValue("a"), 0.0001f)
        assertEquals(2f / 3f, fractions.getValue("b"), 0.0001f)
        assertEquals(listOf(StreamInfo, Players), result.groupOrFail("b").dockableIds)
    }

    @Test
    fun mergingIntoANonAdjacentGroupOfTheSameLaneAlsoKeepsTheOthers() {
        val result = threeGroupLaneLayout.applyOrFail(DockLayoutOperation.MoveToGroup(Players, "a", tabIndex = 1))

        val fractions = result.groupFractions("left")
        assertEquals(2f / 3f, fractions.getValue("a"), 0.0001f)
        assertEquals(1f / 3f, fractions.getValue("b"), 0.0001f)
    }

    @Test
    fun movingAGroupsLastWindowIntoAnotherLaneGivesItsShareToTheNeighbour() {
        // Regression B7: C (last) leaves the lane; B above it takes C's share, A stays untouched.
        val result = threeGroupLaneLayout.applyOrFail(DockLayoutOperation.MoveToGroup(Players, "mixer-group", tabIndex = 1))

        val fractions = result.groupFractions("left")
        assertEquals(1f / 3f, fractions.getValue("a"), 0.0001f)
        assertEquals(2f / 3f, fractions.getValue("b"), 0.0001f)
    }

    @Test
    fun closingAMiddleGroupGivesItsShareToTheGroupAbove() {
        // Closing uses removingDockable; B (middle) goes, A above it takes B's share, C stays untouched.
        val result = threeGroupLaneLayout.removingDockable(StreamInfo)

        val fractions = result.groupFractions("left")
        assertEquals(2f / 3f, fractions.getValue("a"), 0.0001f)
        assertEquals(1f / 3f, fractions.getValue("c"), 0.0001f)
    }

    @Test
    fun theFirstGroupsShareGoesToTheGroupAfterIt() {
        val result = threeGroupLaneLayout.applyOrFail(DockLayoutOperation.MoveToGroup(Scenes, "mixer-group", tabIndex = 1))

        val fractions = result.groupFractions("left")
        assertEquals(2f / 3f, fractions.getValue("b"), 0.0001f)
        assertEquals(1f / 3f, fractions.getValue("c"), 0.0001f)
    }

    @Test
    fun addingToAGroupSelectsTheMovedWindowAndTheSourceSelectsItsNeighbour() {
        val layout = twoLeftLanesLayout
            .applyOrFail(DockLayoutOperation.MoveToGroup(Players, "scenes-group", tabIndex = 1))
            .applyOrFail(DockLayoutOperation.MoveToGroup(AudioMixer, "scenes-group", tabIndex = 2))
        val scenesGroup = layout.groupOrFail("scenes-group")
        assertEquals(listOf(Scenes, Players, AudioMixer), scenesGroup.dockableIds)
        assertEquals(AudioMixer, scenesGroup.selectedDockableId)

        val afterMovingSelectedAway = layout.applyOrFail(DockLayoutOperation.MoveToCenter(AudioMixer))
        assertEquals(Players, afterMovingSelectedAway.groupOrFail("scenes-group").selectedDockableId)
    }

    @Test
    fun reorderingInsideTheSameGroupMovesForwardAndBackward() {
        val threeTabs = twoLeftLanesLayout
            .applyOrFail(DockLayoutOperation.MoveToGroup(Players, "scenes-group", tabIndex = 1))
            .applyOrFail(DockLayoutOperation.MoveToGroup(AudioMixer, "scenes-group", tabIndex = 2))

        // The tab index refers to the strip without the moved tab: [Players, AudioMixer] has the slots 0..2.
        val movedForward = threeTabs.applyOrFail(DockLayoutOperation.MoveToGroup(Scenes, "scenes-group", tabIndex = 2))
        assertEquals(listOf(Players, AudioMixer, Scenes), movedForward.groupOrFail("scenes-group").dockableIds)

        val movedBackward = threeTabs.applyOrFail(DockLayoutOperation.MoveToGroup(AudioMixer, "scenes-group", tabIndex = 0))
        assertEquals(listOf(AudioMixer, Scenes, Players), movedBackward.groupOrFail("scenes-group").dockableIds)
    }

    @Test
    fun laneIndicesReferToTheLayoutWithoutTheMovedWindow() {
        // LEFT: [edge: Players], [inner: Scenes]. Lifting Players collapses the edge lane, so index 1 is the
        // inner side of the Scenes lane: Players ends up between Scenes and the center (regression B3).
        val layout = DockLayout(
            outerFrame = DockFrame(
                sides = mapOf(
                    DockSide.LEFT to listOf(
                        lane("left-edge", 0.3f, group("players-group", Players)),
                        lane("left-inner", 0.15f, group("scenes-group", Scenes)),
                    ),
                ),
            ),
            center = CenterGrid.single(group("center-group", GameControls)),
        )

        val result = layout.applyOrFail(DockLayoutOperation.MoveToNewLane(Players, DockFrameLevel.OUTER, DockSide.LEFT, laneIndex = 1))

        val leftLanes = result.outerFrame.lanes(DockSide.LEFT)
        assertEquals(listOf(listOf(Scenes), listOf(Players)), leftLanes.map { lane -> lane.groups.single().dockableIds })
        assertEquals(0.15f, leftLanes.first().extentFraction, "The remaining lane keeps its width")
    }

    @Test
    fun theMovedWindowsOwnCollapsedContainerIsNoTarget() {
        val result = twoLeftLanesLayout.applyOperation(
            DockLayoutOperation.MoveToNewGroup(Players, "left-inner", groupIndex = 0),
            landscape,
            DockingCapabilities.Desktop,
        )

        assertEquals(DockOperationResult.Rejected(DockRejectionReason.UNKNOWN_TARGET), result)
    }

    @Test
    fun movingTheOnlyCenterWindowOutLeavesThePlaceholderAndMovingInCreatesTheCenterGroup() {
        val emptyCenter = twoLeftLanesLayout.applyOrFail(DockLayoutOperation.MoveToGroup(GameControls, "mixer-group", tabIndex = 1))
        assertNull(emptyCenter.center.groups.singleOrNull())

        val refilledCenter = emptyCenter.applyOrFail(DockLayoutOperation.MoveToCenter(Scenes))
        assertEquals(listOf(Scenes), refilledCenter.center.groups.singleOrNull()?.dockableIds)
    }

    @Test
    fun innerFrameLanesAreSupportedOnDesktop() {
        val result = twoLeftLanesLayout.applyOrFail(
            DockLayoutOperation.MoveToNewLane(Players, DockFrameLevel.INNER, DockSide.TOP, laneIndex = 0),
        )

        assertEquals(listOf(Players), result.innerFrame.lanes(DockSide.TOP).single().groups.single().dockableIds)
        assertTrue(result.outerFrame.lanes(DockSide.LEFT).none { lane -> lane.id == "left-inner" })
    }

    @Test
    fun tabletRejectsInnerFrameAndLimitsLanesAndGroups() {
        val tablet = TestCapabilities.Tablet

        val innerFrameResult = twoLeftLanesLayout.applyOperation(
            DockLayoutOperation.MoveToNewLane(AudioMixer, DockFrameLevel.INNER, DockSide.TOP, 0),
            landscape,
            tablet,
        )
        assertEquals(DockOperationResult.Rejected(DockRejectionReason.FRAME_NOT_ALLOWED), innerFrameResult)

        val singleLeftLane = DockLayout(
            outerFrame = DockFrame(mapOf(DockSide.LEFT to listOf(lane("left", 0.2f, group("a", Scenes), group("b", StreamInfo))))),
            center = CenterGrid.single(group("center", GameControls)),
        )
        val laneResult = singleLeftLane.applyOperation(
            DockLayoutOperation.MoveToNewLane(GameControls, DockFrameLevel.OUTER, DockSide.LEFT, 1),
            landscape,
            tablet,
        )
        assertEquals(DockOperationResult.Rejected(DockRejectionReason.LANE_LIMIT_REACHED), laneResult)

        val groupResult = singleLeftLane.applyOperation(
            DockLayoutOperation.MoveToNewGroup(GameControls, "left", 2),
            landscape,
            tablet,
        )
        assertEquals(DockOperationResult.Rejected(DockRejectionReason.GROUP_LIMIT_REACHED), groupResult)

        // Re-arranging inside a full lane does not count the moving window twice.
        val swapInFullLane = singleLeftLane.applyOperation(DockLayoutOperation.MoveToNewGroup(Scenes, "left", 2), landscape, tablet)
        assertIs<DockOperationResult.Applied>(swapInFullLane)

        // Portrait allows two TOP lanes on tablets.
        val twoTopLanes = singleLeftLane
            .applyOperation(DockLayoutOperation.MoveToNewLane(Scenes, DockFrameLevel.OUTER, DockSide.TOP, 0), LayoutOrientation.PORTRAIT, tablet)
            .let { result -> assertIs<DockOperationResult.Applied>(result).layout }
            .applyOperation(DockLayoutOperation.MoveToNewLane(StreamInfo, DockFrameLevel.OUTER, DockSide.TOP, 1), LayoutOrientation.PORTRAIT, tablet)
        assertIs<DockOperationResult.Applied>(twoTopLanes)
    }

    @Test
    fun applyingTheSameOperationTwiceYieldsIdenticalLayouts() {
        val operation = DockLayoutOperation.MoveToNewLane(Players, DockFrameLevel.OUTER, DockSide.BOTTOM, 0)

        assertEquals(twoLeftLanesLayout.applyOrFail(operation), twoLeftLanesLayout.applyOrFail(operation))
    }

    @Test
    fun unknownTargetsAndInvalidSizesAreRejected() {
        assertEquals(
            DockOperationResult.Rejected(DockRejectionReason.UNKNOWN_TARGET),
            twoLeftLanesLayout.applyOperation(DockLayoutOperation.MoveToGroup(Scenes, "missing", 0), landscape, DockingCapabilities.Desktop),
        )
        assertEquals(
            DockOperationResult.Rejected(DockRejectionReason.INVALID_SIZE),
            twoLeftLanesLayout.applyOperation(DockLayoutOperation.ResizeGroups("left-edge", listOf(1f)), landscape, DockingCapabilities.Desktop),
        )
        assertEquals(
            DockOperationResult.Rejected(DockRejectionReason.INVALID_SIZE),
            twoLeftLanesLayout.applyOperation(DockLayoutOperation.ResizeLane("left-edge", Float.NaN), landscape, DockingCapabilities.Desktop),
        )
    }

    @Test
    fun resizingALaneChangesOnlyThatLane() {
        val result = twoLeftLanesLayout.applyOrFail(DockLayoutOperation.ResizeLane("left-inner", 0.3f))

        assertEquals(listOf(0.2f, 0.3f), result.outerFrame.lanes(DockSide.LEFT).map { lane -> lane.extentFraction })
        assertEquals(0.3f, result.outerFrame.lanes(DockSide.RIGHT).single().extentFraction)
    }

    @Test
    fun resizingGroupsNormalizesTheGivenFractions() {
        val result = twoLeftLanesLayout.applyOrFail(DockLayoutOperation.ResizeGroups("left-edge", listOf(3f, 1f)))

        assertEquals(listOf(0.75f, 0.25f), result.outerFrame.lanes(DockSide.LEFT).first().groups.map { group -> group.sizeFraction })
    }

    @Test
    fun normalizationRemovesDuplicatesAndFixesInvalidSelection() {
        val brokenLayout = DockLayout(
            outerFrame = DockFrame(mapOf(DockSide.LEFT to listOf(lane("left", 0.2f, group("a", listOf(Scenes, Players)))))),
            center = CenterGrid.single(TabGroup(id = "center", dockableIds = listOf(Scenes, GameControls), selectedDockableId = AudioMixer)),
        )

        val normalizedLayout = brokenLayout.normalized()

        assertEquals(listOf(GameControls), normalizedLayout.center.groups.singleOrNull()?.dockableIds)
        assertEquals(GameControls, normalizedLayout.center.groups.singleOrNull()?.selectedDockableId)
        assertEquals(listOf(Scenes, Players), normalizedLayout.placedDockableIds.take(2))
    }
}
