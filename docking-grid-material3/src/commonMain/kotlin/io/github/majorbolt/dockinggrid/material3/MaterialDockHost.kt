package io.github.majorbolt.dockinggrid.material3

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockProfile
import io.github.majorbolt.dockinggrid.DockableCatalog
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.compose.BasicDockDivider
import io.github.majorbolt.dockinggrid.compose.DockDragController
import io.github.majorbolt.dockinggrid.compose.DockDividerState
import io.github.majorbolt.dockinggrid.compose.DockHost
import io.github.majorbolt.dockinggrid.compose.DockingStyle
import io.github.majorbolt.dockinggrid.geometry.DockingMetrics

/**
 * A [DockHost] with Material 3 chrome and a style taken from the current Material theme. Use [DockHost] directly
 * to combine the Material pieces ([MaterialDockTabStrip], [MaterialDockHeader], ...) with own slots.
 *
 * @param dockableMenuItems further entries of a group's options menu, below "Close"; see [MaterialDockHeader].
 * @param onCenterSplitsResizableChange when set, the options menu of a center panel offers "Lock dividers"; see
 *   [DockHost].
 */
@Composable
public fun MaterialDockHost(
    profile: DockProfile,
    catalog: DockableCatalog,
    capabilities: DockingCapabilities,
    orientation: LayoutOrientation,
    onApplyOperation: (DockLayoutOperation) -> Unit,
    onCloseDockable: (DockableId) -> Unit,
    modifier: Modifier = Modifier,
    style: DockingStyle = MaterialDockingDefaults.style(),
    metrics: DockingMetrics = DockingMetrics(),
    divider: @Composable (DockDividerState) -> Unit = { state -> BasicDockDivider(state) },
    dragController: DockDragController? = null,
    dockableMenuItems: @Composable (dockableId: DockableId, dismissMenu: () -> Unit) -> Unit = { _, _ -> },
    onCenterSplitsResizableChange: ((Boolean) -> Unit)? = null,
    content: @Composable (DockableId) -> Unit,
) {
    DockHost(
        profile = profile,
        catalog = catalog,
        capabilities = capabilities,
        orientation = orientation,
        onApplyOperation = onApplyOperation,
        onCloseDockable = onCloseDockable,
        modifier = modifier,
        style = style,
        metrics = metrics,
        tabStrip = { MaterialDockTabStrip() },
        header = { MaterialDockHeader(menuItems = dockableMenuItems) },
        divider = divider,
        placeholder = { text -> MaterialDockPlaceholder(text) },
        dragLabel = { title -> MaterialDockDragLabel(title) },
        dragController = dragController,
        onCenterSplitsResizableChange = onCenterSplitsResizableChange,
        content = content,
    )
}
