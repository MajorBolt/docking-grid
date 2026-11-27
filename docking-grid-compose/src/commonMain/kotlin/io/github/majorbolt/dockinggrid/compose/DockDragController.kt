package io.github.majorbolt.dockinggrid.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import io.github.majorbolt.dockinggrid.DockableId

/**
 * Drags a dockable over a [DockHost] from outside it, for example from an icon in an application toolbar.
 * Pass the controller to the host, then drive the drag from your own pointer gesture.
 *
 * The drag behaves like a drag of the dockable's header: the host resolves the target under the pointer,
 * shows the live preview and the highlight, and a drop emits exactly one operation through the host's
 * `onApplyOperation`. Positions are window coordinates in pixels (for example from
 * `LayoutCoordinates.localToWindow`); the host converts them itself. Outside the host the preview is cleared,
 * so a drop there sends nothing.
 *
 * The dockable may be closed. Then the emitted operation moves a dockable that is not open; commit it with
 * [io.github.majorbolt.dockinggrid.applyingDropOperation], which opens it at the drop target.
 *
 * The host ends the drag on its own on Escape, loss of window focus or a change of the committed profile;
 * [isDragging] turns `false` then. All functions must be called on the UI thread.
 */
@Stable
public class DockDragController {

    internal var host: ExternalDockDragHost? by mutableStateOf(null)

    /** `true` while a drag started through this controller is running in an attached host. */
    public val isDragging: Boolean get() = host?.isExternalDragActive == true

    /** `true` while the pointer is over a target where a drop would change the layout. */
    public val isOverDropTarget: Boolean get() = host?.isExternalDragOverDropTarget == true

    /**
     * Starts dragging [dockableId] at [windowPosition]. Returns `false` when no host is attached, the host is busy
     * with another interaction, or the dockable is not in the host's catalog.
     */
    public fun startDrag(dockableId: DockableId, windowPosition: Offset): Boolean =
        host?.startExternalDrag(dockableId, windowPosition) ?: false

    /** Moves the running drag to [windowPosition]; ignored when no drag is running. */
    public fun updateDrag(windowPosition: Offset) {
        host?.updateExternalDrag(windowPosition)
    }

    /**
     * Drops at the last position. Returns `true` when an operation was emitted, `false` when there was no running
     * drag or no target that changes the layout (nothing is emitted then).
     */
    public fun drop(): Boolean = host?.dropExternalDrag() ?: false

    /** Cancels the running drag and discards its preview; the committed layout is shown again. */
    public fun cancelDrag() {
        host?.cancelExternalDrag()
    }
}

@Composable
public fun rememberDockDragController(): DockDragController = remember { DockDragController() }

/** The part of a [DockHost] that a [DockDragController] talks to. */
internal interface ExternalDockDragHost {
    val isExternalDragActive: Boolean
    val isExternalDragOverDropTarget: Boolean
    fun startExternalDrag(dockableId: DockableId, windowPosition: Offset): Boolean
    fun updateExternalDrag(windowPosition: Offset)
    fun dropExternalDrag(): Boolean
    fun cancelExternalDrag()
}
