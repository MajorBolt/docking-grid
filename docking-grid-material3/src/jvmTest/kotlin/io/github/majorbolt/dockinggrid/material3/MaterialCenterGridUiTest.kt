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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.majorbolt.dockinggrid.CenterPreset
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
import io.github.majorbolt.dockinggrid.geometry.DividerTarget
import io.github.majorbolt.dockinggrid.geometry.DockingMetrics
import io.github.majorbolt.dockinggrid.matchingPreset
import io.github.majorbolt.dockinggrid.testing.TestDockables
import io.github.majorbolt.dockinggrid.testing.TestDockables.AudioMixer
import io.github.majorbolt.dockinggrid.testing.TestDockables.GameControls
import io.github.majorbolt.dockinggrid.testing.TestDockables.Players
import io.github.majorbolt.dockinggrid.testing.TestDockables.Scenes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The center grid in the real [MaterialDockHost], 1600 × 900 dp at density 1. Default layout without RIGHT: LEFT lane
 * 0..400 (Scenes header at y ≈ 2..38), center 408..1600 with GameControls.
 */
@OptIn(ExperimentalTestApi::class)
class MaterialCenterGridUiTest {

    private val desktop = DockingCapabilities.Desktop
    private val landscape = LayoutOrientation.LANDSCAPE
    private val stateWithoutRight = defaultWindowDockState(TestDockables.catalog, desktop).closingDockable(Players).closingDockable(AudioMixer)

    private class Recorder(initialState: WindowDockState) {
        var committedState by mutableStateOf(initialState)
        val appliedOperations = mutableListOf<DockLayoutOperation>()
        var centerSplitsResizable by mutableStateOf(true)
    }

    private fun ComposeUiTest.setDockHost(initialState: WindowDockState, centerSplitsResizable: Boolean = true): Recorder {
        val recorder = Recorder(initialState)
        recorder.centerSplitsResizable = centerSplitsResizable
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
                            metrics = DockingMetrics(centerSplitsResizable = currentRecorder.centerSplitsResizable),
                            onCenterSplitsResizableChange = { resizable -> currentRecorder.centerSplitsResizable = resizable },
                            content = { dockableId -> Text("Content of ${TestDockables.titleOf(dockableId)}") },
                        )
                    }
                }
            }
        }
        return recorder
    }

    /** GameControls on the left and Scenes on the right of the center: the divider lies at x 1000..1008. */
    private fun sideBySideState(): WindowDockState {
        val centerPanelId = stateWithoutRight.profile(landscape).layout.center.panels.single().id
        val result = stateWithoutRight.applyingOperation(DockLayoutOperation.SplitCenterPanel(Scenes, centerPanelId, DockSide.RIGHT), landscape, desktop)
        return assertIs<DockStateTransitionResult.Applied>(result).state
    }

    @Test
    fun droppingOnTheBottomOfTheCenterPanelSplitsIt() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val recorder = setDockHost(stateWithoutRight)

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput {
            moveTo(Offset(100f, 20f))
            press()
            moveTo(Offset(130f, 40f))
            moveTo(Offset(1004f, 800f))
        }
        waitForIdle()
        val highlight = onNodeWithTag(DockHostTestTags.HIGHLIGHT).getUnclippedBoundsInRoot()
        assertEquals(408.dp, highlight.left)
        // Below the grid header (0..28): 28 + (900 − 28 − 8) / 2 + 8 = 468.
        assertEquals(468.dp, highlight.top, "The lower half of the center, below the new divider")

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { release() }
        waitForIdle()

        val center = recorder.committedState.profile(landscape).layout.center
        assertEquals(CenterPreset.STACKED, center.matchingPreset)
        assertEquals(listOf(listOf(GameControls), listOf(Scenes)), center.panels.map { panel -> panel.group?.dockableIds.orEmpty() })
    }

    @Test
    fun aPresetFromTheLayoutButtonCreatesEmptyPanels() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val recorder = setDockHost(stateWithoutRight)

        // Only the center panel's header has the layout button.
        onNodeWithTag(MaterialCenterLayoutTestTags.LAYOUT_BUTTON).performClick()
        waitForIdle()
        onNodeWithTag(MaterialCenterLayoutTestTags.preset(CenterPreset.GRID_2X2)).performClick()
        waitForIdle()

        assertEquals<List<DockLayoutOperation>>(listOf(DockLayoutOperation.ArrangeCenter(CenterPreset.GRID_2X2)), recorder.appliedOperations)
        val center = recorder.committedState.profile(landscape).layout.center
        assertEquals(CenterPreset.GRID_2X2, center.matchingPreset)
        center.panels.filter { panel -> panel.group == null }.forEach { emptyPanel ->
            onNodeWithTag(DockHostTestTags.emptyCenterPanel(emptyPanel.id)).assertExists()
        }
        assertEquals(3, center.panels.count { panel -> panel.group == null })
    }

    @Test
    fun theCenterDividerResizesTheSplit() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val recorder = setDockHost(sideBySideState())
        val dividerTag = DockHostTestTags.divider(DividerTarget.CenterSplit(null))
        assertEquals(1000.dp, onNodeWithTag(dividerTag).getUnclippedBoundsInRoot().left)

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput {
            moveTo(Offset(1004f, 450f))
            press()
            moveTo(Offset(1054f, 450f))
            moveTo(Offset(1104f, 450f))
            release()
        }
        waitForIdle()

        val operation = assertIs<DockLayoutOperation.ResizeCenterSplit>(recorder.appliedOperations.single())
        assertNull(operation.sectionIndex)
        assertEquals(1100.dp, onNodeWithTag(dividerTag).getUnclippedBoundsInRoot().left)
    }

    @Test
    fun lockedCenterDividersStayWhereTheyAre() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val resizedState = assertIs<DockStateTransitionResult.Applied>(
            sideBySideState().applyingOperation(DockLayoutOperation.ResizeCenterSplit(null, 0.25f), landscape, desktop),
        ).state
        val recorder = setDockHost(resizedState, centerSplitsResizable = false)
        val dividerTag = DockHostTestTags.divider(DividerTarget.CenterSplit(null))
        // 0.25 of the center would be 296 wide, but GameControls needs 320: 408 + 320 = 728, not the even 1000.
        assertEquals(728.dp, onNodeWithTag(dividerTag).getUnclippedBoundsInRoot().left, "The stored split is shown")

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput {
            moveTo(Offset(732f, 450f))
            press()
            moveTo(Offset(832f, 450f))
            release()
        }
        waitForIdle()

        assertTrue(recorder.appliedOperations.none { operation -> operation is DockLayoutOperation.ResizeCenterSplit })
        assertEquals(728.dp, onNodeWithTag(dividerTag).getUnclippedBoundsInRoot().left)
    }

    @Test
    fun theCenterHeaderLocksAndResetsTheDividers() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val resizedState = assertIs<DockStateTransitionResult.Applied>(
            sideBySideState().applyingOperation(DockLayoutOperation.ResizeCenterSplit(null, 0.25f), landscape, desktop),
        ).state
        val recorder = setDockHost(resizedState)

        onNodeWithTag(MaterialCenterLayoutTestTags.CENTER_OPTIONS).performClick()
        waitForIdle()
        onNodeWithTag(MaterialCenterLayoutTestTags.LOCK_DIVIDERS).performClick()
        waitForIdle()
        assertFalse(recorder.centerSplitsResizable)

        onNodeWithTag(MaterialCenterLayoutTestTags.CENTER_OPTIONS).performClick()
        waitForIdle()
        onNodeWithTag(MaterialCenterLayoutTestTags.RESET_DIVIDERS).performClick()
        waitForIdle()
        assertEquals<List<DockLayoutOperation>>(listOf(DockLayoutOperation.ResetCenterSplits), recorder.appliedOperations)
        assertEquals(0.5f, recorder.committedState.profile(landscape).layout.center.splitFraction)
        assertFalse(recorder.centerSplitsResizable, "Resetting does not unlock")
    }

    @Test
    fun theCenterHeaderAppearsWithTwoPanelsAndTakesTheLayoutButtonAlong() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val recorder = setDockHost(stateWithoutRight)
        onNodeWithTag(DockHostTestTags.CENTER_HEADER).assertDoesNotExist()
        assertEquals(1, onAllNodesWithTag(MaterialCenterLayoutTestTags.LAYOUT_BUTTON).fetchSemanticsNodes().size, "In the only panel's header")

        recorder.committedState = sideBySideState()
        waitForIdle()

        val headerBounds = onNodeWithTag(DockHostTestTags.CENTER_HEADER).getUnclippedBoundsInRoot()
        assertEquals(408.dp, headerBounds.left)
        assertEquals(28.dp, headerBounds.bottom - headerBounds.top)
        val layoutButtons = onAllNodesWithTag(MaterialCenterLayoutTestTags.LAYOUT_BUTTON).fetchSemanticsNodes()
        assertEquals(1, layoutButtons.size, "Only the center header has it")
        assertTrue(layoutButtons.single().boundsInRoot.bottom <= 28f)
    }

    @Test
    fun droppingInTheMiddleOfAnotherCenterPanelSwapsThem() = runDesktopComposeUiTest(width = 1600, height = 900) {
        // GameControls 408..1000, Scenes 1008..1600, below the grid header (0..28); drag GameControls by its header
        // (from y 28) into the middle of Scenes.
        val recorder = setDockHost(sideBySideState())

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput {
            moveTo(Offset(500f, 46f))
            press()
            moveTo(Offset(530f, 70f))
            moveTo(Offset(1304f, 450f))
        }
        waitForIdle()
        val highlight = onNodeWithTag(DockHostTestTags.HIGHLIGHT).getUnclippedBoundsInRoot()
        assertEquals(1008.dp, highlight.left, "The grid keeps its arrangement; only the target panel is highlighted")
        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { release() }
        waitForIdle()

        assertEquals<List<DockLayoutOperation>>(listOf(DockLayoutOperation.SwapDockables(GameControls, Scenes)), recorder.appliedOperations)
        val panels = recorder.committedState.profile(landscape).layout.center.panels
        assertEquals(listOf(listOf(Scenes), listOf(GameControls)), panels.map { panel -> panel.group?.dockableIds.orEmpty() })
    }
}
