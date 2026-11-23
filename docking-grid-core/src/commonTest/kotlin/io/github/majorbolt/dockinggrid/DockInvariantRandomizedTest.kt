package io.github.majorbolt.dockinggrid

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Applies long, seeded random sequences of operations and checks the model invariants after every step.
 * A failure message contains the seed and step so it can be reproduced deterministically.
 */
class DockInvariantRandomizedTest {

    @Test
    fun invariantsHoldOnDesktop() = runRandomSequences(DockingCapabilities.Desktop)

    @Test
    fun invariantsAndLimitsHoldOnTablet() = runRandomSequences(TestCapabilities.Tablet)

    private fun runRandomSequences(capabilities: DockingCapabilities) {
        repeat(SEQUENCE_COUNT) { sequenceIndex ->
            val seed = 1_000 + sequenceIndex
            val random = Random(seed)
            var state = defaultWindowDockState(TestDockables.catalog, capabilities)
            repeat(STEPS_PER_SEQUENCE) { stepIndex ->
                val orientation = LayoutOrientation.entries.random(random)
                val description = "seed=$seed step=$stepIndex"
                state = nextState(state, orientation, capabilities, random)
                assertInvariants(state, capabilities, description)
            }
        }
    }

    private fun nextState(
        state: WindowDockState,
        orientation: LayoutOrientation,
        capabilities: DockingCapabilities,
        random: Random,
    ): WindowDockState {
        val dockableId = TestDockables.ALL.random(random)
        if (random.nextInt(10) == 0) return state.togglingDockable(dockableId, TestDockables.catalog, capabilities)

        val operation = randomOperation(state.profile(orientation).layout, state, dockableId, random) ?: return state
        return when (val result = state.applyingOperation(operation, orientation, capabilities)) {
            is DockStateTransitionResult.Applied -> result.state
            is DockStateTransitionResult.Rejected -> state
        }
    }

    private fun randomOperation(
        layout: DockLayout,
        state: WindowDockState,
        dockableId: DockableId,
        random: Random,
    ): DockLayoutOperation? {
        val lanes = layout.outerFrame.allLanes + layout.innerFrame.allLanes
        val groups = layout.allGroups
        return when (random.nextInt(7)) {
            0 -> groups.randomOrNull(random)?.let { group ->
                DockLayoutOperation.MoveToGroup(dockableId, group.id, random.nextInt(group.dockableIds.size + 2))
            }
            1 -> lanes.randomOrNull(random)?.let { lane ->
                DockLayoutOperation.MoveToNewGroup(dockableId, lane.id, random.nextInt(lane.groups.size + 2))
            }
            2 -> DockLayoutOperation.MoveToNewLane(
                dockableId = dockableId,
                frameLevel = DockFrameLevel.entries.random(random),
                side = DockSide.entries.random(random),
                laneIndex = random.nextInt(4),
            )
            3 -> DockLayoutOperation.MoveToCenter(dockableId)
            4 -> lanes.randomOrNull(random)?.let { lane ->
                DockLayoutOperation.ResizeLane(lane.id, random.nextFloat() * 1.2f)
            }
            5 -> lanes.randomOrNull(random)?.let { lane ->
                DockLayoutOperation.ResizeGroups(lane.id, lane.groups.map { random.nextFloat() + 0.01f })
            }
            else -> state.openDockableIds.randomOrNull(random)?.let { openDockableId -> DockLayoutOperation.SelectTab(openDockableId) }
        }
    }

    private fun assertInvariants(state: WindowDockState, capabilities: DockingCapabilities, description: String) {
        assertEquals(LayoutOrientation.entries.toSet(), state.profiles.keys, "All profiles exist ($description)")
        state.profiles.forEach { (orientation, profile) ->
            val layout = profile.layout
            val context = "$description orientation=$orientation"
            val placed = layout.placedDockableIds
            assertEquals(placed.size, placed.toSet().size, "No duplicates ($context)")
            assertEquals(state.openDockableIds, placed.toSet(), "Exactly the open windows are placed ($context)")
            assertEquals(layout, layout.normalized(), "Layout is normalized ($context)")

            layout.allGroups.forEach { group ->
                assertTrue(group.dockableIds.isNotEmpty(), "No empty group ($context)")
                assertTrue(group.selectedDockableId in group.dockableIds, "Selection valid ($context)")
            }
            DockFrameLevel.entries.forEach { frameLevel ->
                DockSide.entries.forEach { side ->
                    val lanes = layout.frame(frameLevel).lanes(side)
                    if (!capabilities.isFrameAllowed(frameLevel)) {
                        assertTrue(lanes.isEmpty(), "Frame $frameLevel not allowed ($context)")
                    }
                    capabilities.maximumLanes(orientation, side)?.let { maximum ->
                        assertTrue(lanes.size <= maximum, "Lane limit on $side ($context)")
                    }
                    lanes.forEach { lane ->
                        assertTrue(lane.groups.isNotEmpty(), "No empty lane ($context)")
                        capabilities.maximumGroupsPerLane?.let { maximum ->
                            assertTrue(lane.groups.size <= maximum, "Group limit ($context)")
                        }
                        assertTrue(lane.extentFraction in MINIMUM_EXTENT_FRACTION..MAXIMUM_EXTENT_FRACTION, "Extent range ($context)")
                        val fractionSum = lane.groups.sumOf { group -> group.sizeFraction.toDouble() }
                        if (abs(fractionSum - 1.0) > 0.0001) fail("Group fractions sum to $fractionSum ($context)")
                    }
                }
            }
        }
    }

    private companion object {
        const val SEQUENCE_COUNT = 40
        const val STEPS_PER_SEQUENCE = 250
    }
}
