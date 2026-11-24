package io.github.majorbolt.dockinggrid.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockProfile
import io.github.majorbolt.dockinggrid.DockableCatalog
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.geometry.DockRect
import io.github.majorbolt.dockinggrid.geometry.DockSize
import io.github.majorbolt.dockinggrid.geometry.DockingMetrics
import io.github.majorbolt.dockinggrid.geometry.GroupHeaderMetrics
import io.github.majorbolt.dockinggrid.geometry.calculateDockGeometry
import io.github.majorbolt.dockinggrid.withoutDockable

/** Test tags of the dock host, for UI tests of applications that use it. */
public object DockHostTestTags {
    public const val HOST: String = "dock_host"
    public const val HIGHLIGHT: String = "dock_drop_highlight"
    public const val EMPTY_CENTER: String = "dock_empty_center"
    public const val LIFTED_SLOT: String = "dock_lifted_slot"

    public fun group(groupId: String): String = "dock_group_$groupId"
}

/**
 * Renders the dock layout of one window and runs all pointer interactions on it.
 *
 * The committed [profile] is never changed here. During an interaction the transient preview is shown,
 * and a finished interaction emits exactly one operation through [onApplyOperation]. Cancellation
 * (Escape, loss of window focus, gesture cancel, or a change of the committed profile underneath)
 * restores the committed layout. The caller owns the state: it applies the operation (for example with
 * [io.github.majorbolt.dockinggrid.applyingOperation]) and passes the resulting profile back.
 *
 * @param catalog the dockables that may appear in [profile]: their titles and minimum sizes.
 * @param onCloseDockable called when the user closes a dockable through its chrome.
 * @param style colors, shapes and texts; available to the slots as [LocalDockingStyle].
 * @param metrics sizes the layout calculation uses, such as the divider thickness.
 * @param tabStrip the tab strip of a group with more than one dockable. Each tab must use [DockGroupScope.dockTab].
 * @param header the header row of a group's selected dockable; the whole row is its drag handle.
 * @param divider draws a divider inside the area the host reserves for it.
 * @param placeholder the empty center and the slot of a dragged dockable, with the text to show.
 * @param dragLabel follows the pointer while a dockable is dragged, with its title.
 * @param content renders the content of one dockable. Its composition is kept while the dockable moves.
 */
@Composable
public fun DockHost(
    profile: DockProfile,
    catalog: DockableCatalog,
    capabilities: DockingCapabilities,
    orientation: LayoutOrientation,
    onApplyOperation: (DockLayoutOperation) -> Unit,
    onCloseDockable: (DockableId) -> Unit,
    modifier: Modifier = Modifier,
    style: DockingStyle = DockingStyle(),
    metrics: DockingMetrics = DockingMetrics(),
    tabStrip: @Composable DockGroupScope.() -> Unit = { BasicDockTabStrip() },
    header: @Composable DockGroupScope.() -> Unit = { BasicDockHeader() },
    divider: @Composable (DockDividerState) -> Unit = { state -> BasicDockDivider(state) },
    placeholder: @Composable (text: String) -> Unit = { text -> BasicDockPlaceholder(text) },
    dragLabel: @Composable (title: String) -> Unit = { title -> BasicDockDragLabel(title) },
    content: @Composable (DockableId) -> Unit,
) {
    CompositionLocalProvider(LocalDockingStyle provides style) {
        BoxWithConstraints(modifier = modifier) {
            val contentSize = DockSize(widthDp = maxWidth.value, heightDp = maxHeight.value)
            val density = LocalDensity.current.density
            var interaction by remember { mutableStateOf<DockInteraction>(DockInteraction.Idle) }
            val pointerTracking = remember { PointerTracking() }
            // Header sizes do not depend on the rendered layout, so headers of the lifted drag layout can be calculated.
            var headerMetrics by remember { mutableStateOf(GroupHeaderMetrics()) }
            val focusRequester = remember { FocusRequester() }

            val committedGeometry = remember(profile.layout, contentSize, catalog, metrics) {
                calculateDockGeometry(profile.layout, contentSize, catalog, metrics)
            }
            val environment = DockInteractionEnvironment(
                committedProfile = profile,
                committedGeometry = committedGeometry,
                capabilities = capabilities,
                orientation = orientation,
                catalog = catalog,
                metrics = metrics,
                headerMetrics = headerMetrics,
            )
            val currentEnvironment by rememberUpdatedState(environment)
            val currentOnApplyOperation by rememberUpdatedState(onApplyOperation)

            LaunchedEffect(profile) {
                interaction = interaction.afterCommittedProfileChanged(profile)
            }
            val isWindowFocused = LocalWindowInfo.current.isWindowFocused
            LaunchedEffect(isWindowFocused) {
                if (!isWindowFocused) interaction = interaction.cancelled()
            }

            fun finishInteraction() {
                val (operation, nextInteraction) = interaction.finished()
                interaction = nextInteraction
                operation?.let(currentOnApplyOperation)
            }

            val startDockableDragState = rememberUpdatedState { dockableId: DockableId, pointerId: PointerId ->
                val pointerPosition = pointerTracking.lastPositions[pointerId]
                if (pointerPosition != null && interaction !is DockInteraction.ResizingDivider) {
                    pointerTracking.draggingPointer = pointerId
                    interaction = startDockableDrag(
                        dockableId = dockableId,
                        horizontalPosition = pointerPosition.x / density,
                        verticalPosition = pointerPosition.y / density,
                        environment = currentEnvironment,
                    )
                    focusRequester.requestFocus()
                }
            }

            val displayedLayout = interaction.displayedLayout(profile.layout)
            val displayedGeometry = remember(displayedLayout, contentSize, catalog, metrics, committedGeometry) {
                if (displayedLayout == profile.layout) {
                    committedGeometry
                } else {
                    calculateDockGeometry(displayedLayout, contentSize, catalog, metrics)
                }
            }

            val currentContent by rememberUpdatedState(content)
            val movableDockableContents = remember { mutableMapOf<DockableId, @Composable () -> Unit>() }
            fun contentOf(dockableId: DockableId): @Composable () -> Unit =
                movableDockableContents.getOrPut(dockableId) { movableContentOf { currentContent(dockableId) } }
            fun titleOf(dockableId: DockableId): String = catalog.find(dockableId)?.title ?: dockableId.value

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(DockHostTestTags.HOST)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { keyEvent ->
                        val isCancelKey = keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown
                        val isCancellable = interaction is DockInteraction.DraggingDockable || interaction is DockInteraction.ResizingDivider
                        if (isCancelKey && isCancellable) {
                            interaction = interaction.cancelled()
                            pointerTracking.draggingPointer = null
                            true
                        } else {
                            false
                        }
                    }
                    .pointerInput(density) {
                        // The host tracks the drag itself, so the gesture survives disposal of the header it started on.
                        awaitPointerEventScope {
                            while (true) {
                                val pointerEvent = awaitPointerEvent(PointerEventPass.Initial)
                                pointerEvent.changes.forEach { change ->
                                    if (change.pressed) {
                                        pointerTracking.lastPositions[change.id] = change.position
                                    } else {
                                        pointerTracking.lastPositions.remove(change.id)
                                    }
                                }
                                val draggingPointer = pointerTracking.draggingPointer ?: continue
                                val draggingInteraction = interaction as? DockInteraction.DraggingDockable
                                if (draggingInteraction == null) {
                                    pointerTracking.draggingPointer = null
                                    continue
                                }
                                val change = pointerEvent.changes.firstOrNull { pointerChange -> pointerChange.id == draggingPointer } ?: continue
                                change.consume()
                                if (change.pressed) {
                                    interaction = draggingInteraction.movedTo(
                                        horizontalPosition = change.position.x / density,
                                        verticalPosition = change.position.y / density,
                                        environment = currentEnvironment,
                                    )
                                } else {
                                    pointerTracking.draggingPointer = null
                                    finishInteraction()
                                }
                            }
                        }
                    },
            ) {
                val liftedDockableId = interaction.liftedDockableId
                displayedLayout.allGroups.forEach { displayedGroup ->
                    val groupRect = displayedGeometry.groupRects[displayedGroup.id] ?: return@forEach
                    // The lifted dockable's slot keeps its place: its group is shown without it, or as a placeholder.
                    val group = liftedDockableId?.let { dockableId -> displayedGroup.withoutDockable(dockableId) } ?: displayedGroup
                    key(group.id) {
                        if (group.dockableIds.isEmpty()) {
                            Box(modifier = Modifier.placedAt(groupRect).testTag(DockHostTestTags.LIFTED_SLOT), propagateMinConstraints = true) {
                                placeholder(style.strings.liftedSlot)
                            }
                        } else {
                            DockGroupContainer(
                                group = group,
                                titleOf = ::titleOf,
                                onSelectTab = { dockableId -> onApplyOperation(DockLayoutOperation.SelectTab(dockableId)) },
                                onCloseDockable = onCloseDockable,
                                dragHandleModifier = { dockableId -> Modifier.dockableDragHandle(dockableId, startDockableDragState) },
                                onHeaderMeasured = { measurement ->
                                    val updatedMetrics = headerMetrics.updatedWith(measurement)
                                    if (updatedMetrics != headerMetrics) headerMetrics = updatedMetrics
                                },
                                tabStrip = tabStrip,
                                header = header,
                                content = { dockableId -> contentOf(dockableId)() },
                                modifier = Modifier
                                    .placedAt(groupRect)
                                    .testTag(DockHostTestTags.group(group.id)),
                            )
                        }
                    }
                }

                // A lifted dockable is not rendered in a group; keep its content composed (invisible),
                // so that its state survives the drag.
                (interaction as? DockInteraction.DraggingDockable)?.dockableId
                    ?.takeIf { draggedDockableId ->
                        draggedDockableId == liftedDockableId || displayedLayout.findGroupOf(draggedDockableId)?.selectedDockableId != draggedDockableId
                    }
                    ?.let { draggedDockableId ->
                        key(draggedDockableId) {
                            Box(modifier = Modifier.size(0.dp).clipToBounds()) { contentOf(draggedDockableId)() }
                        }
                    }

                if (displayedLayout.centerGroup == null) {
                    Box(modifier = Modifier.placedAt(displayedGeometry.centerRect).testTag(DockHostTestTags.EMPTY_CENTER), propagateMinConstraints = true) {
                        placeholder(style.strings.emptyCenter)
                    }
                }

                displayedGeometry.dividers.forEach { displayedDivider ->
                    key(displayedDivider.target) {
                        DockDividerHandle(
                            movesHorizontally = displayedDivider.movesHorizontally,
                            onResizeStart = {
                                val committedDivider = committedGeometry.dividers.firstOrNull { divider -> divider.target == displayedDivider.target }
                                if (committedDivider != null && interaction == DockInteraction.Idle) {
                                    interaction = startDividerResize(committedDivider, profile)
                                }
                            },
                            onResize = { totalDragDistanceDp ->
                                val resizingInteraction = interaction as? DockInteraction.ResizingDivider
                                if (resizingInteraction != null && resizingInteraction.divider.target == displayedDivider.target) {
                                    interaction = resizingInteraction.draggedTo(totalDragDistanceDp, currentEnvironment)
                                }
                            },
                            onResizeEnd = {
                                if (interaction is DockInteraction.ResizingDivider) finishInteraction()
                            },
                            onResizeCancel = {
                                if (interaction is DockInteraction.ResizingDivider) interaction = DockInteraction.Idle
                            },
                            divider = divider,
                            modifier = Modifier.placedAt(displayedDivider.rect),
                        )
                    }
                }

                interaction.highlightRect?.let { highlightRect ->
                    Box(
                        modifier = Modifier
                            .placedAt(highlightRect)
                            .testTag(DockHostTestTags.HIGHLIGHT)
                            .background(style.colors.dropTargetFill, style.dropTargetShape)
                            .then(
                                if(style.dropTargetBorderWidth.value > 0)
                                Modifier.border(style.dropTargetBorderWidth, style.colors.dropTargetBorder, style.dropTargetShape)
                                else Modifier
                            )
                    )
                }

                (interaction as? DockInteraction.DraggingDockable)?.let { draggingInteraction ->
                    Box(
                        modifier = Modifier.absoluteOffset(
                            x = (draggingInteraction.pointerHorizontalPosition + DRAG_LABEL_OFFSET_DP).dp,
                            y = (draggingInteraction.pointerVerticalPosition + DRAG_LABEL_OFFSET_DP).dp,
                        ),
                    ) {
                        dragLabel(titleOf(draggingInteraction.dockableId))
                    }
                }
            }
        }
    }
}

/** Starts a host-tracked drag once the pointer moved beyond the touch slop on this handle. */
private fun Modifier.dockableDragHandle(
    dockableId: DockableId,
    startDockableDragState: State<(DockableId, PointerId) -> Unit>,
): Modifier = pointerInput(dockableId) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slopChange = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
        if (slopChange != null) startDockableDragState.value(dockableId, slopChange.id)
    }
}

private fun Modifier.placedAt(rect: DockRect): Modifier =
    absoluteOffset(x = rect.left.dp, y = rect.top.dp).size(width = rect.width.dp, height = rect.height.dp)

/** Pointer bookkeeping that must not trigger recomposition. */
private class PointerTracking {
    val lastPositions = mutableMapOf<PointerId, Offset>()
    var draggingPointer: PointerId? = null
}

private const val DRAG_LABEL_OFFSET_DP = 12f

/** Merges a measured header into the metrics; tab widths of all dockables seen so far are kept. */
private fun GroupHeaderMetrics.updatedWith(measurement: HeaderMeasurement): GroupHeaderMetrics = copy(
    headerTopInsetDp = measurement.topInsetDp,
    tabRowHeightDp = measurement.tabRowHeightDp ?: tabRowHeightDp,
    headerRowHeightDp = measurement.headerHeightDp,
    tabStartPaddingDp = measurement.tabStartDp ?: tabStartPaddingDp,
    tabWidthsDp = tabWidthsDp + measurement.tabWidthsDp,
)
