package io.github.majorbolt.dockinggrid.material3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.defaultWindowDockState
import io.github.majorbolt.dockinggrid.testing.TestDockables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The options menu of a group: compact entries and the entries an application adds below "Close". */
@OptIn(ExperimentalTestApi::class)
class MaterialDockMenuUiTest {

    @Test
    fun ownEntriesAppearBelowCloseAndReceiveTheSelectedDockable() = runDesktopComposeUiTest(width = 1600, height = 900) {
        val chosenDockables = mutableListOf<DockableId>()
        val closedDockables = mutableListOf<DockableId>()
        val state = defaultWindowDockState(TestDockables.catalog, DockingCapabilities.Desktop)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(modifier = Modifier.size(width = 1600.dp, height = 900.dp)) {
                        MaterialDockHost(
                            profile = state.profile(LayoutOrientation.LANDSCAPE),
                            catalog = TestDockables.catalog,
                            capabilities = DockingCapabilities.Desktop,
                            orientation = LayoutOrientation.LANDSCAPE,
                            onApplyOperation = {},
                            onCloseDockable = { dockableId -> closedDockables += dockableId },
                            dockableMenuItems = { dockableId, dismissMenu ->
                                MaterialDockMenuItem(
                                    text = "Hide",
                                    onClick = {
                                        dismissMenu()
                                        chosenDockables += dockableId
                                    },
                                )
                            },
                            content = { dockableId -> Text("Content of ${TestDockables.titleOf(dockableId)}") },
                        )
                    }
                }
            }
        }

        onAllNodesWithText("⋮")[0].performClick()
        val closeBounds = onNodeWithText("Close").getUnclippedBoundsInRoot()
        val hideBounds = onNodeWithText("Hide").getUnclippedBoundsInRoot()
        assertTrue(hideBounds.top >= closeBounds.bottom, "Own entries come below Close")
        assertEquals(28.dp, hideBounds.bottom - hideBounds.top, "Entries are compact")

        onNodeWithText("Hide").performClick()
        waitForIdle()

        assertEquals(1, chosenDockables.size)
        assertTrue(chosenDockables.single() in TestDockables.catalog)
        assertTrue(closedDockables.isEmpty())
        onNodeWithText("Hide").assertDoesNotExist()
    }
}
