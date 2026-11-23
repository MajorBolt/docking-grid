package io.github.majorbolt.dockinggrid.material3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.majorbolt.dockinggrid.DockFrameLevel
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockStateTransitionResult
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.WindowDockState
import io.github.majorbolt.dockinggrid.applyingOperation
import io.github.majorbolt.dockinggrid.closingDockable
import io.github.majorbolt.dockinggrid.compose.DockHostTestTags
import io.github.majorbolt.dockinggrid.defaultWindowDockState
import io.github.majorbolt.dockinggrid.testing.TestDockables
import io.github.majorbolt.dockinggrid.testing.TestDockables.AudioMixer
import io.github.majorbolt.dockinggrid.testing.TestDockables.Players
import io.github.majorbolt.dockinggrid.testing.TestDockables.Scenes
import io.github.majorbolt.dockinggrid.testing.TestDockables.StreamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Drives the real [MaterialDockHost] with simulated mouse and keyboard input in a 1600 × 900 dp area at density 1.
 * Default layout without RIGHT: LEFT lane 0..400 (Scenes header at y ≈ 2..38), center 408..1600.
 */
@OptIn(ExperimentalTestApi::class)
class MaterialDockHostUiTest {

    private val desktop = DockingCapabilities.Desktop
    private val landscape = LayoutOrientation.LANDSCAPE
    private val stateWithoutRight = defaultWindowDockState(TestDockables.catalog, desktop).closingDockable(Players).closingDockable(AudioMixer)

    private class Recorder(initialState: WindowDockState) {
        var committedState by mutableStateOf(initialState)
        val appliedOperations = mutableListOf<DockLayoutOperation>()
    }

    private fun ComposeUiTest.setDockHost(initialState: WindowDockState): Recorder {
        val recorder = Recorder(initialState)
        setContent {
            val currentRecorder = remember { recorder }
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(modifier = Modifier.size(width = 1600.dp, height = 900.dp)) {
                        MaterialDockHost(
                            profile = currentRecorder.committedState.profile(landscape),
                            catalog = TestDockables.catalog,
                            capabilities = desktop,
                            orientation = landscape,
                            onApplyOperation = { operation ->
                                currentRecorder.appliedOperations += operation
                                val result = currentRecorder.committedState.applyingOperation(operation, landscape, desktop)
                                if (result is DockStateTransitionResult.Applied) currentRecorder.committedState = result.state
                            },
                            onCloseDockable = {},
                            content = { dockableId -> Text("Content of ${TestDockables.titleOf(dockableId)}") },
                        )
                    }
                }
            }
        }
        return recorder
    }

    private fun ComposeUiTest.boundsOfTag(tag: String): DpRect = onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun ComposeUiTest.boundsOfTab(title: String): DpRect =
        onNode(hasText(title) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).getUnclippedBoundsInRoot()

    private fun ComposeUiTest.startDraggingScenesToTheRightEdge() {
        onNodeWithTag(DockHostTestTags.HOST).performMouseInput {
            moveTo(Offset(100f, 20f))
            press()
            moveTo(Offset(130f, 40f))
            moveTo(Offset(800f, 450f))
            moveTo(Offset(1590f, 450f))
        }
        waitForIdle()
    }

    @Test
    fun draggingToAnEmptyRightEdgeHighlightsOnlyThePredictedDockReflowsTheCenterAndCommitsOnRelease() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val recorder = setDockHost(stateWithoutRight)
        val centerGroupId = checkNotNull(stateWithoutRight.profile(landscape).layout.centerGroup).id
        assertEquals(1192.dp, boundsOfTag(DockHostTestTags.group(centerGroupId)).let { bounds -> bounds.right - bounds.left })

        startDraggingScenesToTheRightEdge()

        assertEquals(DpRect(1200.dp, 0.dp, 1600.dp, 900.dp), boundsOfTag(DockHostTestTags.HIGHLIGHT))
        val previewCenterBounds = boundsOfTag(DockHostTestTags.group(centerGroupId))
        assertEquals(784.dp, previewCenterBounds.right - previewCenterBounds.left, "The live preview shrinks the center")
        assertTrue(recorder.appliedOperations.isEmpty(), "Nothing is committed while dragging")

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { release() }
        waitForIdle()

        assertEquals(listOf<DockLayoutOperation>(DockLayoutOperation.MoveToNewLane(Scenes, DockFrameLevel.OUTER, DockSide.RIGHT, 0)), recorder.appliedOperations)
        onNodeWithTag(DockHostTestTags.HIGHLIGHT).assertDoesNotExist()
        assertEquals(784.dp, boundsOfTag(DockHostTestTags.group(centerGroupId)).let { bounds -> bounds.right - bounds.left })
    }

    @Test
    fun escapeCancelsTheDragAndRestoresTheCommittedLayout() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val recorder = setDockHost(stateWithoutRight)
        val centerGroupId = checkNotNull(stateWithoutRight.profile(landscape).layout.centerGroup).id
        startDraggingScenesToTheRightEdge()

        onNodeWithTag(DockHostTestTags.HOST).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { release() }
        waitForIdle()

        onNodeWithTag(DockHostTestTags.HIGHLIGHT).assertDoesNotExist()
        assertTrue(recorder.appliedOperations.isEmpty())
        assertEquals(stateWithoutRight, recorder.committedState)
        assertEquals(1192.dp, boundsOfTag(DockHostTestTags.group(centerGroupId)).let { bounds -> bounds.right - bounds.left })
    }

    @Test
    fun releasingOverNoTargetCommitsNothing() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val recorder = setDockHost(stateWithoutRight)

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput {
            moveTo(Offset(100f, 20f))
            press()
            moveTo(Offset(130f, 40f))
            moveTo(Offset(404f, 450f))
            release()
        }
        waitForIdle()

        assertTrue(recorder.appliedOperations.isEmpty())
    }

    @Test
    fun draggingTheLeftDividerFollowsThePointerExactlyAndCommitsOneResize() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val recorder = setDockHost(stateWithoutRight)
        val leftLaneId = stateWithoutRight.profile(landscape).layout.outerFrame.lanes(DockSide.LEFT).single().id

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput {
            moveTo(Offset(404f, 300f))
            press()
            moveTo(Offset(454f, 300f))
            moveTo(Offset(504f, 300f))
            release()
        }
        waitForIdle()

        assertEquals(listOf<DockLayoutOperation>(DockLayoutOperation.ResizeLane(leftLaneId, 500f / 1600f)), recorder.appliedOperations)
    }

    @Test
    fun draggingATabBehindItsNeighbourReordersTheTabsWithoutASplitPreview() = runDesktopComposeUiTest(width = 1600, height = 900) {
        // Regression B2: Scenes and StreamInfo are tabs of one group at the top-left.
        val scenesGroupId = stateWithoutRight.profile(landscape).layout.allGroups.first { group -> Scenes in group.dockableIds }.id
        val tabbedState = assertIs<DockStateTransitionResult.Applied>(
            stateWithoutRight.applyingOperation(DockLayoutOperation.MoveToGroup(StreamInfo, scenesGroupId, 1), landscape, desktop),
        ).state
        val recorder = setDockHost(tabbedState)
        val groupBoundsBefore = boundsOfTag(DockHostTestTags.group(scenesGroupId))
        val scenesTab = boundsOfTab(TestDockables.titleOf(Scenes))
        val streamInfoTab = boundsOfTab(TestDockables.titleOf(StreamInfo))
        val pointerHeight = (scenesTab.top.value + scenesTab.bottom.value) / 2f

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput {
            moveTo(Offset((scenesTab.left.value + scenesTab.right.value) / 2f, pointerHeight))
            press()
            moveTo(Offset(streamInfoTab.left.value, pointerHeight))
            moveTo(Offset(streamInfoTab.right.value - 4f, pointerHeight))
        }
        waitForIdle()

        assertEquals(groupBoundsBefore, boundsOfTag(DockHostTestTags.HIGHLIGHT), "The whole group is the target, not a split half")
        assertTrue(recorder.appliedOperations.isEmpty())

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { release() }
        waitForIdle()

        // Lifted out, Scenes leaves StreamInfo alone in the group, so the slot behind StreamInfo is index 1.
        assertEquals(listOf<DockLayoutOperation>(DockLayoutOperation.MoveToGroup(Scenes, scenesGroupId, 1)), recorder.appliedOperations)
        assertEquals(
            listOf(StreamInfo, Scenes),
            recorder.committedState.profile(landscape).layout.findGroup(scenesGroupId)?.dockableIds,
        )
    }

    @Test
    fun aWideEdgeLanesWindowCanBeDroppedIntoTheInnerLaneWhereItIsDrawn() = runDesktopComposeUiTest(width = 1600, height = 900) {
        // Regression B3: RIGHT has a wide edge lane (AudioMixer) and a narrower inner lane (Players).
        val defaultState = defaultWindowDockState(TestDockables.catalog, desktop)
        val withTwoRightLanes = assertIs<DockStateTransitionResult.Applied>(
            defaultState.applyingOperation(DockLayoutOperation.MoveToNewLane(AudioMixer, DockFrameLevel.OUTER, DockSide.RIGHT, 0), landscape, desktop),
        ).state
        val edgeLaneId = withTwoRightLanes.profile(landscape).layout.outerFrame.lanes(DockSide.RIGHT).first().id
        val state = assertIs<DockStateTransitionResult.Applied>(
            withTwoRightLanes.applyingOperation(DockLayoutOperation.ResizeLane(edgeLaneId, 0.35f), landscape, desktop),
        ).state
        val layout = state.profile(landscape).layout
        val audioMixerGroupId = layout.allGroups.first { group -> AudioMixer in group.dockableIds }.id
        val playersLaneId = layout.outerFrame.lanes(DockSide.RIGHT)[1].id
        val recorder = setDockHost(state)
        val audioMixerBounds = boundsOfTag(DockHostTestTags.group(audioMixerGroupId))
        val playersGroupId = layout.allGroups.first { group -> Players in group.dockableIds }.id
        val playersBounds = boundsOfTag(DockHostTestTags.group(playersGroupId))

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput {
            moveTo(Offset(audioMixerBounds.left.value + 60f, audioMixerBounds.top.value + 18f))
            press()
            moveTo(Offset(audioMixerBounds.left.value + 90f, audioMixerBounds.top.value + 60f))
        }
        waitForIdle()

        // Regression B6: nothing moves when the drag starts; the lifted slot is a placeholder.
        assertEquals(audioMixerBounds, boundsOfTag(DockHostTestTags.LIFTED_SLOT))
        assertEquals(playersBounds, boundsOfTag(DockHostTestTags.group(playersGroupId)))

        // Aim at the upper half of Players where it is drawn.
        onNodeWithTag(DockHostTestTags.HOST).performMouseInput {
            moveTo(Offset((playersBounds.left.value + playersBounds.right.value) / 2f, playersBounds.top.value + 150f))
        }
        waitForIdle()

        val highlight = boundsOfTag(DockHostTestTags.HIGHLIGHT)
        assertEquals(1200.dp, highlight.left)
        assertEquals(1600.dp, highlight.right)
        assertEquals(0.dp, highlight.top)

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { release() }
        waitForIdle()

        assertEquals(listOf<DockLayoutOperation>(DockLayoutOperation.MoveToNewGroup(AudioMixer, playersLaneId, 0, splitGroupId = playersGroupId)), recorder.appliedOperations)
        val rightLanes = recorder.committedState.profile(landscape).layout.outerFrame.lanes(DockSide.RIGHT)
        assertEquals(listOf(listOf(listOf(AudioMixer), listOf(Players))), rightLanes.map { lane -> lane.groups.map { group -> group.dockableIds } })
    }
}
