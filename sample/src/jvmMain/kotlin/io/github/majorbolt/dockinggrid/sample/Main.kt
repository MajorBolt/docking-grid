package io.github.majorbolt.dockinggrid.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.majorbolt.dockinggrid.CenterPreset
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockStateTransitionResult
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.WindowDockState
import io.github.majorbolt.dockinggrid.applyingOperation
import io.github.majorbolt.dockinggrid.closingDockable
import io.github.majorbolt.dockinggrid.compose.DockDividerState
import io.github.majorbolt.dockinggrid.compose.titleOf
import io.github.majorbolt.dockinggrid.defaultWindowDockState
import io.github.majorbolt.dockinggrid.geometry.DockingMetrics
import io.github.majorbolt.dockinggrid.matchingPreset
import io.github.majorbolt.dockinggrid.material3.MaterialDockHost
import io.github.majorbolt.dockinggrid.material3.MaterialDockingDefaults
import io.github.majorbolt.dockinggrid.reconciled
import io.github.majorbolt.dockinggrid.togglingDockable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CAPABILITIES = DockingCapabilities.Desktop
private val ACCENT = Color(0xFFFF8A00)

fun main() {
    val catalog = SampleDockables.catalog
    val store = SampleLayoutStore.inUserHome(catalog)
    // Read once before the window opens; a missing or unusable file gives the default layout.
    val initialState = (store.load() ?: defaultWindowDockState(catalog, CAPABILITIES)).reconciled(catalog, CAPABILITIES)

    application {
        val windowState = rememberWindowState(size = DpSize(1400.dp, 900.dp))
        var dockState by remember { mutableStateOf(initialState) }
        var hasChanged by remember { mutableStateOf(false) }
        var centerSplitsResizable by remember { mutableStateOf(true) }
        var orientation by remember { mutableStateOf(LayoutOrientation.LANDSCAPE) }
        val style = MaterialDockingDefaults.style(
            colors = MaterialDockingDefaults.colors().copy(dropTargetFill = ACCENT.copy(alpha = 0.20f), dropTargetBorder = ACCENT),
        )
        fun commit(updatedState: WindowDockState) {
            if (updatedState != dockState) {
                dockState = updatedState
                hasChanged = true
            }
        }
        LaunchedEffect(dockState) {
            if (hasChanged) withContext(Dispatchers.IO) { store.save(dockState) }
        }

        Window(onCloseRequest = ::exitApplication, title = "Docking Grid sample", state = windowState) {
            // The library has no toolbar on purpose: the host decides how dockables are opened and closed.
            MenuBar {
                Menu("Window") {
                    catalog.dockables.forEach { dockable ->
                        CheckboxItem(
                            text = dockable.title,
                            checked = dockable.id in dockState.openDockableIds,
                            onCheckedChange = { commit(dockState.togglingDockable(dockable.id, catalog, CAPABILITIES)) },
                        )
                    }
                    Separator()
                    Item("Reset layout", onClick = { commit(defaultWindowDockState(catalog, CAPABILITIES)) })
                }
                Menu("Center") {
                    val currentPreset = dockState.profile(orientation).layout.center.matchingPreset
                    CenterPreset.entries.forEach { preset ->
                        CheckboxItem(
                            text = style.strings.titleOf(preset),
                            checked = preset == currentPreset,
                            onCheckedChange = {
                                val result = dockState.applyingOperation(DockLayoutOperation.ArrangeCenter(preset), orientation, CAPABILITIES)
                                if (result is DockStateTransitionResult.Applied) commit(result.state)
                            },
                        )
                    }
                    Separator()
                    CheckboxItem(
                        text = "Resizable center splits",
                        checked = centerSplitsResizable,
                        onCheckedChange = { checked -> centerSplitsResizable = checked },
                    )
                }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        orientation = if (maxWidth >= maxHeight) LayoutOrientation.LANDSCAPE else LayoutOrientation.PORTRAIT
                        MaterialDockHost(
                            profile = dockState.profile(orientation),
                            catalog = catalog,
                            capabilities = CAPABILITIES,
                            orientation = orientation,
                            onApplyOperation = { operation ->
                                val result = dockState.applyingOperation(operation, orientation, CAPABILITIES)
                                if (result is DockStateTransitionResult.Applied) commit(result.state)
                            },
                            onCloseDockable = { dockableId -> commit(dockState.closingDockable(dockableId)) },
                            style = style,
                            metrics = DockingMetrics(centerSplitsResizable = centerSplitsResizable),
                            onCenterSplitsResizableChange = { resizable -> centerSplitsResizable = resizable },
                            divider = { state -> ThinDivider(state) },
                            modifier = Modifier.fillMaxSize(),
                        ) { dockableId ->
                            SampleContent(dockableId, title = catalog.find(dockableId)?.title.orEmpty())
                        }
                    }
                }
            }
        }
    }
}

/** A custom divider: a thin line in the middle of the divider area that thickens and turns orange while dragged. */
@Composable
private fun ThinDivider(state: DockDividerState) {
    val lineThickness = if (state.isDragged) 3.dp else 1.dp
    val lineColor = if (state.isDragged) ACCENT else MaterialTheme.colorScheme.outlineVariant
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val lineModifier = if (state.movesHorizontally) {
            Modifier.fillMaxHeight().width(lineThickness)
        } else {
            Modifier.fillMaxWidth().height(lineThickness)
        }
        Box(modifier = lineModifier.background(lineColor))
    }
}

/** Dummy content with its own state, which survives moving the dockable to another place. */
@Composable
private fun SampleContent(dockableId: DockableId, title: String) {
    var clickCount by rememberSaveable { mutableIntStateOf(0) }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = "Drag the header or a tab to move it.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { clickCount++ }) { Text("Clicked $clickCount times") }
        Text(text = "id: ${dockableId.value}", style = MaterialTheme.typography.labelSmall)
    }
}
