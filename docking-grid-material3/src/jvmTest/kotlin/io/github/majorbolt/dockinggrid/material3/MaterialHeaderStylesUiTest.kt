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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockStateTransitionResult
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.GroupHeaderStyle
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.WindowDockState
import io.github.majorbolt.dockinggrid.applyingOperation
import io.github.majorbolt.dockinggrid.closingDockable
import io.github.majorbolt.dockinggrid.compose.DockHostTestTags
import io.github.majorbolt.dockinggrid.defaultWindowDockState
import io.github.majorbolt.dockinggrid.geometry.DockingMetrics
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
 * Header styles in the real [MaterialDockHost], 1600 × 900 dp at density 1. Default layout without RIGHT: LEFT lane
 * 0..400 with Scenes (0..446) above StreamInfo, center 408..1600 with GameControls.
 */
@OptIn(ExperimentalTestApi::class)
class MaterialHeaderStylesUiTest {

    private val desktop = DockingCapabilities.Desktop
    private val landscape = LayoutOrientation.LANDSCAPE
    private val stateWithoutRight = defaultWindowDockState(TestDockables.catalog, desktop).closingDockable(Players).closingDockable(AudioMixer)

    private class Recorder(initialState: WindowDockState) {
        var committedState by mutableStateOf(initialState)
        val appliedOperations = mutableListOf<DockLayoutOperation>()
    }

    private fun ComposeUiTest.setDockHost(initialState: WindowDockState, centerHeaderHidesPanelHeaders: Boolean = true): Recorder {
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
                            metrics = DockingMetrics(centerHeaderHidesPanelHeaders = centerHeaderHidesPanelHeaders),
                            content = { dockableId -> Text("Content of ${TestDockables.titleOf(dockableId)}") },
                        )
                    }
                }
            }
        }
        return recorder
    }

    private fun WindowDockState.applyOrFail(operation: DockLayoutOperation): WindowDockState =
        assertIs<DockStateTransitionResult.Applied>(applyingOperation(operation, landscape, desktop)).state

    private fun WindowDockState.groupIdOf(dockableId: io.github.majorbolt.dockinggrid.DockableId): String =
        profile(landscape).layout.findGroupOf(dockableId)!!.id

    private fun WindowDockState.withHeaderStyle(dockableId: io.github.majorbolt.dockinggrid.DockableId, style: GroupHeaderStyle) =
        applyOrFail(DockLayoutOperation.SetGroupHeaderStyle(groupIdOf(dockableId), style))

    @Test
    fun theOptionsMenuSwitchesToAVerticalHeaderWithIconTabs() = runDesktopComposeUiTest(width = 1600, height = 900) {
        // Scenes and StreamInfo as tabs of one LEFT group.
        val withTabs = stateWithoutRight.applyOrFail(DockLayoutOperation.MoveToGroup(StreamInfo, stateWithoutRight.groupIdOf(Scenes), 1))
        val recorder = setDockHost(withTabs)
        val groupId = withTabs.groupIdOf(Scenes)

        onNodeWithTag(MaterialDockTestTags.options(groupId)).performClick()
        waitForIdle()
        onNodeWithTag(MaterialDockTestTags.VERTICAL_HEADER).performClick()
        waitForIdle()

        assertEquals(DockLayoutOperation.SetGroupHeaderStyle(groupId, GroupHeaderStyle(isVertical = true)), recorder.appliedOperations.last())
        val scenesTab = onNodeWithTag(MaterialDockTestTags.verticalTab(Scenes)).getUnclippedBoundsInRoot()
        val streamInfoTab = onNodeWithTag(MaterialDockTestTags.verticalTab(StreamInfo)).getUnclippedBoundsInRoot()
        assertTrue(scenesTab.right <= 40.dp, "The strip lies at the left edge of the LEFT lane")
        assertEquals(scenesTab.bottom, streamInfoTab.top, "The tabs are stacked")

        onNodeWithTag(MaterialDockTestTags.verticalTab(StreamInfo)).performClick()
        waitForIdle()
        assertEquals(DockLayoutOperation.SelectTab(StreamInfo), recorder.appliedOperations.last())
    }

    @Test
    fun theTabsOfAGroupAreShownInItsHeaderRow() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val withTabs = stateWithoutRight.applyOrFail(DockLayoutOperation.MoveToGroup(StreamInfo, stateWithoutRight.groupIdOf(Scenes), 1))
        setDockHost(withTabs)

        val scenesTab = onNode(hasText("Scenes") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).getUnclippedBoundsInRoot()
        val streamInfoTab = onNode(hasText("Stream Info") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).getUnclippedBoundsInRoot()
        assertTrue(scenesTab.bottom <= 40.dp, "In the 36 dp header row, no tab row above it")
        assertEquals(scenesTab.top, streamInfoTab.top, "Side by side")
        assertEquals(1, onAllNodesWithText("Stream Info").fetchSemanticsNodes().size, "The title is not repeated beside the tabs")
    }

    @Test
    fun anAutoHiddenHeaderSlidesInWhileHovered() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val state = stateWithoutRight.withHeaderStyle(Scenes, GroupHeaderStyle(autoHides = true))
        setDockHost(state)
        val headerTag = DockHostTestTags.autoHidingHeader(state.groupIdOf(Scenes))
        fun headerHeight() = onNodeWithTag(headerTag).getUnclippedBoundsInRoot().let { bounds -> bounds.bottom - bounds.top }

        assertEquals(28.dp, headerHeight(), "Hidden: only the hover zone")

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { moveTo(Offset(200f, 6f)) }
        waitForIdle()
        assertTrue(headerHeight() >= 36.dp, "Shown while hovered")

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { moveTo(Offset(200f, 300f)) }
        waitForIdle()
        assertEquals(28.dp, headerHeight(), "Hidden again")
    }

    @Test
    fun onlyAnAutoHiddenHeaderLiesOverTheContent() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val state = stateWithoutRight.withHeaderStyle(Scenes, GroupHeaderStyle(autoHides = true))
        val overlayByGroup = mutableMapOf<String, Boolean>()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(modifier = Modifier.size(width = 1600.dp, height = 900.dp)) {
                        io.github.majorbolt.dockinggrid.compose.DockHost(
                            profile = state.profile(landscape),
                            catalog = TestDockables.catalog,
                            capabilities = desktop,
                            orientation = landscape,
                            onApplyOperation = {},
                            onCloseDockable = {},
                            header = {
                                overlayByGroup[group.id] = isHeaderOverlay
                                MaterialDockHeader()
                            },
                            content = { dockableId -> Text("Content of ${TestDockables.titleOf(dockableId)}") },
                        )
                    }
                }
            }
        }
        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { moveTo(Offset(200f, 6f)) }
        waitForIdle()

        assertEquals(true, overlayByGroup[state.groupIdOf(Scenes)])
        assertEquals(false, overlayByGroup[state.groupIdOf(GameControls)])
    }

    @Test
    fun anOpenMenuKeepsAnAutoHiddenHeaderShown() = runDesktopComposeUiTest(width = 1600, height = 900) {
        // Regression: the header slid out as soon as the pointer moved to its menu, and took the open menu along.
        val state = stateWithoutRight.withHeaderStyle(Scenes, GroupHeaderStyle(autoHides = true))
        val recorder = setDockHost(state)
        val groupId = state.groupIdOf(Scenes)
        val headerTag = DockHostTestTags.autoHidingHeader(groupId)
        fun headerHeight() = onNodeWithTag(headerTag).getUnclippedBoundsInRoot().let { bounds -> bounds.bottom - bounds.top }

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { moveTo(Offset(200f, 6f)) }
        waitForIdle()
        onNodeWithTag(MaterialDockTestTags.options(groupId)).performClick()
        waitForIdle()
        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { moveTo(Offset(200f, 300f)) }
        waitForIdle()

        assertTrue(headerHeight() >= 36.dp, "Still shown while its menu is open")
        onNodeWithTag(MaterialDockTestTags.VERTICAL_HEADER).performClick()
        waitForIdle()
        assertEquals(DockLayoutOperation.SetGroupHeaderStyle(groupId, GroupHeaderStyle(isVertical = true, autoHides = true)), recorder.appliedOperations.last())
        // Now a vertical strip: hidden again once the menu is closed, only its 28 dp wide hover zone remains.
        val strip = onNodeWithTag(headerTag).getUnclippedBoundsInRoot()
        assertEquals(28.dp, strip.right - strip.left, "Hidden again once the menu is closed and the pointer is away")
    }

    @Test
    fun theGridsAutoHideHidesThePanelHeadersUnlessTheyAreIndependent() {
        fun sideBySideWithHiddenGridHeader(): WindowDockState {
            val centerPanelId = stateWithoutRight.profile(landscape).layout.center.panels.single().id
            return stateWithoutRight
                .applyOrFail(DockLayoutOperation.SplitCenterPanel(Scenes, centerPanelId, DockSide.RIGHT))
                .applyOrFail(DockLayoutOperation.SetCenterHeaderAutoHide(true))
        }

        runDesktopComposeUiTest(width = 1600, height = 900) {
            val state = sideBySideWithHiddenGridHeader()
            setDockHost(state, centerHeaderHidesPanelHeaders = true)
            onNodeWithTag(DockHostTestTags.autoHidingHeader(state.groupIdOf(GameControls))).assertExists()
            onNodeWithTag(DockHostTestTags.CENTER_HEADER).assertExists()
        }
        runDesktopComposeUiTest(width = 1600, height = 900) {
            val state = sideBySideWithHiddenGridHeader()
            setDockHost(state, centerHeaderHidesPanelHeaders = false)
            onNodeWithTag(DockHostTestTags.autoHidingHeader(state.groupIdOf(GameControls))).assertDoesNotExist()
        }
    }

    @Test
    fun aRevealedGridHeaderPushesThePanelsDownInsteadOfCoveringTheirHeaders() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val centerPanelId = stateWithoutRight.profile(landscape).layout.center.panels.single().id
        val state = stateWithoutRight
            .applyOrFail(DockLayoutOperation.SplitCenterPanel(Scenes, centerPanelId, DockSide.RIGHT))
            .applyOrFail(DockLayoutOperation.SetCenterHeaderAutoHide(true))
        // The grid header covers every panel header by default; here the panel headers stay, to see them move.
        setDockHost(state, centerHeaderHidesPanelHeaders = false)
        val groupTag = DockHostTestTags.group(state.groupIdOf(GameControls))
        fun panelTop() = onNodeWithTag(groupTag).getUnclippedBoundsInRoot().top

        assertEquals(0.dp, panelTop(), "Hidden: the panels start at the top")

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { moveTo(Offset(700f, 3f)) }
        waitForIdle()
        assertEquals(28.dp, panelTop(), "Revealed: the panels lie below the grid header")
        val header = onNodeWithTag(DockHostTestTags.CENTER_HEADER).getUnclippedBoundsInRoot()
        assertEquals(28.dp, header.bottom - header.top)

        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { moveTo(Offset(700f, 400f)) }
        waitForIdle()
        assertEquals(0.dp, panelTop(), "Hidden again")
    }

    @Test
    fun aDragShowsHiddenHeadersSoATabCanBeDroppedOnThem() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val state = stateWithoutRight.withHeaderStyle(Scenes, GroupHeaderStyle(autoHides = true))
        val recorder = setDockHost(state)
        val scenesGroupId = state.groupIdOf(Scenes)

        // Drag GameControls by its header (center 408..1600, header from y 2) onto the hidden header of Scenes.
        onNodeWithTag(DockHostTestTags.HOST).performMouseInput {
            moveTo(Offset(600f, 20f))
            press()
            moveTo(Offset(630f, 40f))
            moveTo(Offset(200f, 24f))
        }
        waitForIdle()
        onNodeWithTag(DockHostTestTags.HOST).performMouseInput { release() }
        waitForIdle()

        val operation = assertIs<DockLayoutOperation.MoveToGroup>(recorder.appliedOperations.single())
        assertEquals(scenesGroupId, operation.targetGroupId)
    }
}
