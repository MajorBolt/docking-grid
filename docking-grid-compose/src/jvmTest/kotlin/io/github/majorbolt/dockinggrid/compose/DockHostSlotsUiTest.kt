package io.github.majorbolt.dockinggrid.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockStateTransitionResult
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.WindowDockState
import io.github.majorbolt.dockinggrid.applyingOperation
import io.github.majorbolt.dockinggrid.closingDockable
import io.github.majorbolt.dockinggrid.defaultWindowDockState
import io.github.majorbolt.dockinggrid.testing.TestDockables
import io.github.majorbolt.dockinggrid.testing.TestDockables.AudioMixer
import io.github.majorbolt.dockinggrid.testing.TestDockables.GameControls
import io.github.majorbolt.dockinggrid.testing.TestDockables.Players
import io.github.majorbolt.dockinggrid.testing.TestDockables.Scenes
import io.github.majorbolt.dockinggrid.testing.TestDockables.StreamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Drives [DockHost] with custom chrome in every slot (1600 × 900 dp, density 1), to check that the slot contract
 * holds: custom tabs marked with [DockGroupScope.dockTab] are dragged and measured, a custom header is the drag
 * handle, the divider slot is told about the drag, and the placeholder shows the configured texts.
 */
@OptIn(ExperimentalTestApi::class)
class DockHostSlotsUiTest {

    private val desktop = DockingCapabilities.Desktop
    private val landscape = LayoutOrientation.LANDSCAPE
    private val stateWithoutRight = defaultWindowDockState(TestDockables.catalog, desktop).closingDockable(Players).closingDockable(AudioMixer)
    private val customStrings = DockingStrings(emptyCenter = "Nothing in the center", liftedSlot = "Put it back here")

    private class Recorder(initialState: WindowDockState) {
        var committedState by mutableStateOf(initialState)
        val appliedOperations = mutableListOf<DockLayoutOperation>()
        val dividerStates = mutableListOf<DockDividerState>()
    }

    private fun ComposeUiTest.setCustomDockHost(initialState: WindowDockState): Recorder {
        val recorder = Recorder(initialState)
        setContent {
            val currentRecorder = remember { recorder }
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(modifier = Modifier.size(width = 1600.dp, height = 900.dp)) {
                    DockHost(
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
                        style = DockingStyle(strings = customStrings),
                        tabStrip = {
                            Row(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                                group.dockableIds.forEach { dockableId ->
                                    Box(modifier = Modifier.width(150.dp).height(24.dp).dockTab(dockableId).testTag("tab_${dockableId.value}"))
                                }
                            }
                        },
                        header = {
                            Box(modifier = Modifier.fillMaxWidth().height(30.dp).testTag("header_${group.selectedDockableId.value}"))
                        },
                        divider = { state ->
                            currentRecorder.dividerStates += state
                            Box(modifier = Modifier.size(1.dp))
                        },
                        placeholder = { text -> BasicText(text) },
                        dragLabel = { title -> BasicText("Dragging $title") },
                        content = { dockableId -> BasicText("Content of ${TestDockables.titleOf(dockableId)}") },
                    )
                }
            }
        }
        return recorder
    }

    private fun ComposeUiTest.boundsOfTag(tag: String): DpRect = onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun ComposeUiTest.dragFromTo(start: Offset, positions: List<Offset>, release: Boolean = true) {
        onNodeWithTag(DockHostTestTags.HOST).performMouseInput {
            moveTo(start)
            press()
            positions.forEach { position -> moveTo(position) }
            if (release) release()
        }
        waitForIdle()
    }

    @Test
    fun customTabsMarkedWithDockTabCanBeReorderedByDragging() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val scenesGroupId = stateWithoutRight.profile(landscape).layout.allGroups.first { group -> Scenes in group.dockableIds }.id
        val tabbedState = assertIs<DockStateTransitionResult.Applied>(
            stateWithoutRight.applyingOperation(DockLayoutOperation.MoveToGroup(StreamInfo, scenesGroupId, 1), landscape, desktop),
        ).state
        val recorder = setCustomDockHost(tabbedState)
        val scenesTab = boundsOfTag("tab_${Scenes.value}")
        val streamInfoTab = boundsOfTag("tab_${StreamInfo.value}")
        assertEquals(150.dp, scenesTab.right - scenesTab.left, "The custom tab size is used")
        val pointerHeight = (scenesTab.top.value + scenesTab.bottom.value) / 2f

        dragFromTo(
            start = Offset((scenesTab.left.value + scenesTab.right.value) / 2f, pointerHeight),
            positions = listOf(Offset(streamInfoTab.left.value, pointerHeight), Offset(streamInfoTab.right.value - 4f, pointerHeight)),
        )

        assertEquals(listOf<DockLayoutOperation>(DockLayoutOperation.MoveToGroup(Scenes, scenesGroupId, 1)), recorder.appliedOperations)
        assertEquals(listOf(StreamInfo, Scenes), recorder.committedState.profile(landscape).layout.findGroup(scenesGroupId)?.dockableIds)
    }

    @Test
    fun aCustomHeaderIsTheDragHandleAndTheDragLabelShowsTheTitle() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val recorder = setCustomDockHost(stateWithoutRight)
        val header = boundsOfTag("header_${Scenes.value}")
        val headerCenter = Offset((header.left.value + header.right.value) / 2f, (header.top.value + header.bottom.value) / 2f)

        dragFromTo(headerCenter, listOf(headerCenter + Offset(30f, 20f), Offset(800f, 450f), Offset(1590f, 450f)), release = false)

        onNodeWithText("Dragging Scenes").assertExists()
        assertEquals(DpRect(1200.dp, 0.dp, 1600.dp, 900.dp), boundsOfTag(DockHostTestTags.HIGHLIGHT))

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { release() }
        waitForIdle()
        assertEquals(1, recorder.appliedOperations.size)
    }

    @Test
    fun theDividerSlotIsToldWhileItsDividerIsDragged() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val recorder = setCustomDockHost(stateWithoutRight)
        val leftLaneId = stateWithoutRight.profile(landscape).layout.outerFrame.lanes(DockSide.LEFT).single().id

        dragFromTo(Offset(404f, 300f), listOf(Offset(454f, 300f), Offset(504f, 300f)), release = false)
        assertTrue(recorder.dividerStates.last().isDragged, "Dragged while the pointer is down")
        assertTrue(recorder.dividerStates.last().movesHorizontally)

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { release() }
        waitForIdle()

        assertEquals(false, recorder.dividerStates.last().isDragged)
        assertEquals(listOf<DockLayoutOperation>(DockLayoutOperation.ResizeLane(leftLaneId, 500f / 1600f)), recorder.appliedOperations)
    }

    @Test
    fun placeholdersShowTheConfiguredTexts() = runDesktopComposeUiTest(width = 1600, height = 900) {
        setCustomDockHost(stateWithoutRight.closingDockable(GameControls))

        onNodeWithText(customStrings.emptyCenter).assertExists()
        val header = boundsOfTag("header_${Scenes.value}")
        val headerCenter = Offset((header.left.value + header.right.value) / 2f, (header.top.value + header.bottom.value) / 2f)
        // Barely moved: the pointer stays over the dragged dockable's own slot.
        dragFromTo(headerCenter, listOf(headerCenter + Offset(20f, 20f)), release = false)

        onNodeWithText(customStrings.liftedSlot).assertExists()
    }
}
