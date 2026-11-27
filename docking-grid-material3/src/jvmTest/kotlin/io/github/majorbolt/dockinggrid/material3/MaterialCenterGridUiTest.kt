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
    }

    private fun ComposeUiTest.setDockHost(initialState: WindowDockState, metrics: DockingMetrics = DockingMetrics()): Recorder {
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
                            metrics = metrics,
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
        assertEquals(454.dp, highlight.top, "The lower half of the center, below the new divider")

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { release() }
        waitForIdle()

        val center = recorder.committedState.profile(landscape).layout.center
        assertEquals(CenterPreset.STACKED, center.matchingPreset)
        assertEquals(listOf(listOf(GameControls), listOf(Scenes)), center.panels.map { panel -> panel.group?.dockableIds.orEmpty() })
    }

    @Test
    fun aPresetFromTheOptionsMenuCreatesEmptyPanels() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val recorder = setDockHost(stateWithoutRight)

        // The options button of the center panel's header is the one on the right of the window.
        val optionsButtons = onAllNodesWithText("⋮").fetchSemanticsNodes()
        val centerOptionsIndex = optionsButtons.indexOfFirst { node -> node.boundsInRoot.left > 1000f }
        onAllNodesWithText("⋮")[centerOptionsIndex].performClick()
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
    fun withoutResizableCenterSplitsTheDividerDoesNothing() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val resizedState = assertIs<DockStateTransitionResult.Applied>(
            sideBySideState().applyingOperation(DockLayoutOperation.ResizeCenterSplit(null, 0.25f), landscape, desktop),
        ).state
        val recorder = setDockHost(resizedState, DockingMetrics(centerSplitsResizable = false))
        val dividerTag = DockHostTestTags.divider(DividerTarget.CenterSplit(null))
        assertEquals(1000.dp, onNodeWithTag(dividerTag).getUnclippedBoundsInRoot().left, "Evenly split despite the stored 0.25")

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput {
            moveTo(Offset(1004f, 450f))
            press()
            moveTo(Offset(1104f, 450f))
            release()
        }
        waitForIdle()

        assertTrue(recorder.appliedOperations.none { operation -> operation is DockLayoutOperation.ResizeCenterSplit })
        assertEquals(1000.dp, onNodeWithTag(dividerTag).getUnclippedBoundsInRoot().left)
        assertEquals(0.25f, recorder.committedState.profile(landscape).layout.center.splitFraction, "The stored split is kept")
    }
}
