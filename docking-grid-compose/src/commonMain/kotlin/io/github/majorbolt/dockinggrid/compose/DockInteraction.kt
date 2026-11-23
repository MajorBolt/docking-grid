package io.github.majorbolt.dockinggrid.compose

import io.github.majorbolt.dockinggrid.DockLayout
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockOperationResult
import io.github.majorbolt.dockinggrid.DockProfile
import io.github.majorbolt.dockinggrid.DockableCatalog
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.applyOperation
import io.github.majorbolt.dockinggrid.geometry.DockDivider
import io.github.majorbolt.dockinggrid.geometry.DockGeometry
import io.github.majorbolt.dockinggrid.geometry.DockRect
import io.github.majorbolt.dockinggrid.geometry.DockingMetrics
import io.github.majorbolt.dockinggrid.geometry.DropPreview
import io.github.majorbolt.dockinggrid.geometry.DropTarget
import io.github.majorbolt.dockinggrid.geometry.GroupHeaderMetrics
import io.github.majorbolt.dockinggrid.geometry.LiftedDragBase
import io.github.majorbolt.dockinggrid.geometry.liftedDragBase
import io.github.majorbolt.dockinggrid.geometry.resizeOperationFor
import io.github.majorbolt.dockinggrid.geometry.resolveDropPreview

/** Everything target resolution needs about the committed layout; positions are content dp. */
internal data class DockInteractionEnvironment(
    val committedProfile: DockProfile,
    val committedGeometry: DockGeometry,
    val capabilities: DockingCapabilities,
    val orientation: LayoutOrientation,
    /** Minimum sizes of the dockables, which limit drops and resizing. */
    val catalog: DockableCatalog,
    val metrics: DockingMetrics,
    /** Measured header sizes, from which the header of any group in any layout is calculated. */
    val headerMetrics: GroupHeaderMetrics,
)

/**
 * Transient pointer interaction of the dock host. It never changes the committed layout; a finished
 * interaction yields exactly one [DockLayoutOperation] for the ViewModel.
 */
internal sealed interface DockInteraction {

    data object Idle : DockInteraction

    /**
     * The dragged dockable is lifted out of the layout: its slot stays as a placeholder ([liftedDockableId]),
     * so nothing moves until a target is hovered, and all targets are resolved in [dragBase], where they are drawn.
     */
    data class DraggingDockable(
        val dockableId: DockableId,
        val baseProfile: DockProfile,
        val dragBase: LiftedDragBase,
        val pointerHorizontalPosition: Float,
        val pointerVerticalPosition: Float,
        /** `null` while the pointer is over no valid target. */
        val preview: DropPreview?,
    ) : DockInteraction

    data class ResizingDivider(
        val divider: DockDivider,
        val baseProfile: DockProfile,
        val dragDistanceDp: Float,
        val operation: DockLayoutOperation?,
        val previewLayout: DockLayout,
    ) : DockInteraction

    /**
     * A drop or resize was sent to the ViewModel; its result is shown until the committed profile changes,
     * which prevents a one-frame flash of the old layout.
     */
    data class AwaitingCommit(
        val baseProfile: DockProfile,
        val displayedLayout: DockLayout,
    ) : DockInteraction
}

/** The layout to render: the preview during an interaction, otherwise the committed layout. */
internal fun DockInteraction.displayedLayout(committedLayout: DockLayout): DockLayout = when (this) {
    DockInteraction.Idle -> committedLayout
    is DockInteraction.DraggingDockable -> preview?.takeIf { dropPreview -> dropPreview.target != DropTarget.Origin }?.layout
        ?: committedLayout
    is DockInteraction.ResizingDivider -> previewLayout
    is DockInteraction.AwaitingCommit -> displayedLayout
}

/**
 * The dockable whose slot is rendered as a placeholder: the dragged one, while no target other than its own
 * slot is hovered. `null` otherwise.
 */
internal val DockInteraction.liftedDockableId: DockableId?
    get() = (this as? DockInteraction.DraggingDockable)
        ?.takeIf { dragging -> dragging.preview == null || dragging.preview.target == DropTarget.Origin }
        ?.dockableId

/** The area to highlight: only the resolved target, never the whole UI. */
internal val DockInteraction.highlightRect: DockRect?
    get() = (this as? DockInteraction.DraggingDockable)?.preview?.highlightRect

internal fun startDockableDrag(
    dockableId: DockableId,
    horizontalPosition: Float,
    verticalPosition: Float,
    environment: DockInteractionEnvironment,
): DockInteraction.DraggingDockable = DockInteraction.DraggingDockable(
    dockableId = dockableId,
    baseProfile = environment.committedProfile,
    dragBase = liftedDragBase(
        committedLayout = environment.committedProfile.layout,
        dockableId = dockableId,
        contentSize = environment.committedGeometry.contentSize,
        catalog = environment.catalog,
        metrics = environment.metrics,
    ),
    pointerHorizontalPosition = horizontalPosition,
    pointerVerticalPosition = verticalPosition,
    preview = null,
).movedTo(horizontalPosition, verticalPosition, environment)

internal fun DockInteraction.DraggingDockable.movedTo(
    horizontalPosition: Float,
    verticalPosition: Float,
    environment: DockInteractionEnvironment,
): DockInteraction.DraggingDockable {
    val contentSize = environment.committedGeometry.contentSize
    // The window may be resized during a drag; the lifted geometry follows the content size.
    val currentDragBase = if (dragBase.geometry.contentSize == contentSize) {
        dragBase
    } else {
        liftedDragBase(baseProfile.layout, dockableId, contentSize, environment.catalog, environment.metrics)
    }
    return copy(
        dragBase = currentDragBase,
        pointerHorizontalPosition = horizontalPosition,
        pointerVerticalPosition = verticalPosition,
        preview = resolveDropPreview(
            profile = baseProfile,
            dragBase = currentDragBase,
            horizontalPosition = horizontalPosition,
            verticalPosition = verticalPosition,
            capabilities = environment.capabilities,
            orientation = environment.orientation,
            catalog = environment.catalog,
            metrics = environment.metrics,
            headerMetrics = environment.headerMetrics,
        ),
    )
}

internal fun startDividerResize(divider: DockDivider, profile: DockProfile): DockInteraction.ResizingDivider =
    DockInteraction.ResizingDivider(
        divider = divider,
        baseProfile = profile,
        dragDistanceDp = 0f,
        operation = null,
        previewLayout = profile.layout,
    )

internal fun DockInteraction.ResizingDivider.draggedTo(
    totalDragDistanceDp: Float,
    environment: DockInteractionEnvironment,
): DockInteraction.ResizingDivider {
    val resizeOperation = resizeOperationFor(
        startLayout = baseProfile.layout,
        divider = divider,
        dragDistanceDp = totalDragDistanceDp,
        contentSize = environment.committedGeometry.contentSize,
        catalog = environment.catalog,
        metrics = environment.metrics,
    )
    val resizedLayout = resizeOperation?.let { operation ->
        (baseProfile.applyLayoutOperation(operation, environment) as? DockOperationResult.Applied)?.layout
    }
    return copy(
        dragDistanceDp = totalDragDistanceDp,
        operation = resizeOperation.takeIf { resizedLayout != null },
        previewLayout = resizedLayout ?: baseProfile.layout,
    )
}

/**
 * Finishes the interaction. Returns the operation to send (if any) and the follow-up state.
 * A drop without a valid target, a drop back at the original place or a resize without movement sends nothing.
 */
internal fun DockInteraction.finished(): Pair<DockLayoutOperation?, DockInteraction> = when (this) {
    is DockInteraction.DraggingDockable -> {
        val dropPreview = preview
        val dropOperation = dropPreview?.operation
        if (dropPreview == null || dropOperation == null || !dropPreview.changesLayout) {
            null to DockInteraction.Idle
        } else {
            dropOperation to DockInteraction.AwaitingCommit(baseProfile, dropPreview.layout)
        }
    }
    is DockInteraction.ResizingDivider -> {
        val resizeOperation = operation
        if (resizeOperation == null || previewLayout == baseProfile.layout) {
            null to DockInteraction.Idle
        } else {
            resizeOperation to DockInteraction.AwaitingCommit(baseProfile, previewLayout)
        }
    }
    DockInteraction.Idle, is DockInteraction.AwaitingCommit -> null to this
}

/** Cancels a running drag or resize, discarding its preview; an awaited commit is kept. */
internal fun DockInteraction.cancelled(): DockInteraction = when (this) {
    is DockInteraction.DraggingDockable, is DockInteraction.ResizingDivider -> DockInteraction.Idle
    DockInteraction.Idle, is DockInteraction.AwaitingCommit -> this
}

/**
 * Reacts to a change of the committed profile: an awaited commit is complete; a running interaction
 * is cancelled because its base is stale (for example a dockable was closed by shortcut meanwhile).
 */
internal fun DockInteraction.afterCommittedProfileChanged(committedProfile: DockProfile): DockInteraction {
    val baseProfile = when (this) {
        DockInteraction.Idle -> return this
        is DockInteraction.DraggingDockable -> baseProfile
        is DockInteraction.ResizingDivider -> baseProfile
        is DockInteraction.AwaitingCommit -> baseProfile
    }
    return if (baseProfile == committedProfile) this else DockInteraction.Idle
}

private fun DockProfile.applyLayoutOperation(
    operation: DockLayoutOperation,
    environment: DockInteractionEnvironment,
): DockOperationResult = applyOperation(operation, environment.orientation, environment.capabilities)

