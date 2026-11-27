package io.github.majorbolt.dockinggrid.material3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.majorbolt.dockinggrid.DockFrameLevel
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockStateTransitionResult
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.WindowDockState
import io.github.majorbolt.dockinggrid.applyingDropOperation
import io.github.majorbolt.dockinggrid.closingDockable
import io.github.majorbolt.dockinggrid.compose.DockDragController
import io.github.majorbolt.dockinggrid.compose.DockHostTestTags
import io.github.majorbolt.dockinggrid.compose.rememberDockDragController
import io.github.majorbolt.dockinggrid.defaultWindowDockState
import io.github.majorbolt.dockinggrid.testing.TestDockables
import io.github.majorbolt.dockinggrid.testing.TestDockables.AudioMixer
import io.github.majorbolt.dockinggrid.testing.TestDockables.Players
import io.github.majorbolt.dockinggrid.testing.TestDockables.Scenes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives a [MaterialDockHost] through a [DockDragController], as an application toolbar would.
 * A 100 dp strip left of the host stands in for the toolbar, so window positions differ from host positions:
 * host x = window x - 100. Density 1; the host is 1600 × 900 dp with the default layout without RIGHT.
 */
@OptIn(ExperimentalTestApi::class)
class ExternalDockDragUiTest {

    private val desktop = DockingCapabilities.Desktop
    private val landscape = LayoutOrientation.LANDSCAPE
    private val stateWithoutRight = defaultWindowDockState(TestDockables.catalog, desktop).closingDockable(Players).closingDockable(AudioMixer)
    private val toolbarWidth = 100f

    private class Recorder(initialState: WindowDockState) {
        var committedState by mutableStateOf(initialState)
        val appliedOperations = mutableListOf<DockLayoutOperation>()
        lateinit var controller: DockDragController
    }

    private fun ComposeUiTest.setDockHostBesideToolbar(initialState: WindowDockState = stateWithoutRight): Recorder {
        val recorder = Recorder(initialState)
        setContent {
            recorder.controller = rememberDockDragController()
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Row {
                        Box(modifier = Modifier.size(width = toolbarWidth.dp, height = 900.dp))
                        Box(modifier = Modifier.size(width = 1600.dp, height = 900.dp)) {
                            MaterialDockHost(
                                profile = recorder.committedState.profile(landscape),
                                catalog = TestDockables.catalog,
                                capabilities = desktop,
                                orientation = landscape,
                                onApplyOperation = { operation ->
                                    recorder.appliedOperations += operation
                                    val result = recorder.committedState.applyingDropOperation(operation, landscape, TestDockables.catalog, desktop)
                                    if (result is DockStateTransitionResult.Applied) recorder.committedState = result.state
                                },
                                onCloseDockable = {},
                                dragController = recorder.controller,
                                content = { dockableId -> Text("Content of ${TestDockables.titleOf(dockableId)}") },
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        return recorder
    }

    private fun windowPosition(hostHorizontalPosition: Float, hostVerticalPosition: Float) =
        Offset(hostHorizontalPosition + toolbarWidth, hostVerticalPosition)

    private fun ComposeUiTest.highlightBounds(): DpRect = onNodeWithTag(DockHostTestTags.HIGHLIGHT).getUnclippedBoundsInRoot()

    @Test
    fun aClosedDockableDraggedToTheRightEdgeIsPreviewedAndOpenedThereOnDrop() = runDesktopComposeUiTest(width = 1700, height = 900) {
        val recorder = setDockHostBesideToolbar()

        runOnIdle {
            assertTrue(recorder.controller.startDrag(Players, windowPosition(-40f, 450f)))
            assertFalse(recorder.controller.isOverDropTarget, "Over the toolbar there is no target")
        }
        waitForIdle()
        onNodeWithTag(DockHostTestTags.HIGHLIGHT).assertDoesNotExist()

        runOnIdle { recorder.controller.updateDrag(windowPosition(1590f, 450f)) }
        waitForIdle()

        // Root coordinates include the 100 dp strip: the predicted RIGHT dock is host x 1200..1600.
        assertEquals(DpRect(1300.dp, 0.dp, 1700.dp, 900.dp), highlightBounds())
        runOnIdle {
            assertTrue(recorder.controller.isDragging)
            assertTrue(recorder.controller.isOverDropTarget)
            assertTrue(recorder.appliedOperations.isEmpty(), "Nothing is committed while dragging")
        }

        runOnIdle { assertTrue(recorder.controller.drop()) }
        waitForIdle()

        assertEquals(listOf<DockLayoutOperation>(DockLayoutOperation.MoveToNewLane(Players, DockFrameLevel.OUTER, DockSide.RIGHT, 0)), recorder.appliedOperations)
        runOnIdle {
            assertTrue(Players in recorder.committedState.openDockableIds)
            assertFalse(recorder.controller.isDragging)
        }
        onNodeWithTag(DockHostTestTags.HIGHLIGHT).assertDoesNotExist()
        onNodeWithText("Content of Players").assertExists()
    }

    @Test
    fun aDropOutsideTheHostSendsNothing() = runDesktopComposeUiTest(width = 1700, height = 900) {
        val recorder = setDockHostBesideToolbar()

        runOnIdle {
            recorder.controller.startDrag(Players, windowPosition(800f, 450f))
            recorder.controller.updateDrag(windowPosition(-50f, 450f))
        }
        waitForIdle()

        runOnIdle { assertFalse(recorder.controller.drop()) }
        waitForIdle()
        assertTrue(recorder.appliedOperations.isEmpty())
        runOnIdle { assertEquals(stateWithoutRight, recorder.committedState) }
    }

    @Test
    fun cancellingRestoresTheCommittedLayout() = runDesktopComposeUiTest(width = 1700, height = 900) {
        val recorder = setDockHostBesideToolbar()
        runOnIdle {
            recorder.controller.startDrag(Players, windowPosition(800f, 450f))
            recorder.controller.updateDrag(windowPosition(1590f, 450f))
        }
        waitForIdle()
        onNodeWithTag(DockHostTestTags.HIGHLIGHT).assertExists()

        runOnIdle { recorder.controller.cancelDrag() }
        waitForIdle()

        onNodeWithTag(DockHostTestTags.HIGHLIGHT).assertDoesNotExist()
        runOnIdle {
            assertFalse(recorder.controller.isDragging)
            assertFalse(recorder.controller.drop(), "A cancelled drag cannot be dropped")
        }
        assertTrue(recorder.appliedOperations.isEmpty())
    }

    @Test
    fun escapeEndsAnExternalDrag() = runDesktopComposeUiTest(width = 1700, height = 900) {
        val recorder = setDockHostBesideToolbar()
        runOnIdle {
            recorder.controller.startDrag(Players, windowPosition(800f, 450f))
            recorder.controller.updateDrag(windowPosition(1590f, 450f))
        }
        waitForIdle()

        onNodeWithTag(DockHostTestTags.HOST).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        runOnIdle { assertFalse(recorder.controller.isDragging) }
        onNodeWithTag(DockHostTestTags.HIGHLIGHT).assertDoesNotExist()
        assertTrue(recorder.appliedOperations.isEmpty())
    }

    @Test
    fun anOpenDockableDraggedFromOutsideMovesLikeAHeaderDrag() = runDesktopComposeUiTest(width = 1700, height = 900) {
        val recorder = setDockHostBesideToolbar()

        runOnIdle {
            recorder.controller.startDrag(Scenes, windowPosition(-40f, 450f))
            recorder.controller.updateDrag(windowPosition(1590f, 450f))
        }
        waitForIdle()
        runOnIdle { assertTrue(recorder.controller.drop()) }
        waitForIdle()

        assertEquals(listOf<DockLayoutOperation>(DockLayoutOperation.MoveToNewLane(Scenes, DockFrameLevel.OUTER, DockSide.RIGHT, 0)), recorder.appliedOperations)
    }

    @Test
    fun aDockableOutsideTheCatalogCannotBeDragged() = runDesktopComposeUiTest(width = 1700, height = 900) {
        val recorder = setDockHostBesideToolbar()

        runOnIdle {
            assertFalse(recorder.controller.startDrag(DockableId("unknown"), windowPosition(800f, 450f)))
            assertFalse(recorder.controller.isDragging)
        }
    }

    @Test
    fun withoutAHostTheControllerDoesNothing() {
        val controller = DockDragController()

        assertFalse(controller.startDrag(Scenes, Offset.Zero))
        assertFalse(controller.isDragging)
        assertFalse(controller.drop())
    }
}
