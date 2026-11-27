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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
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
import io.github.majorbolt.dockinggrid.geometry.DividerTarget
import io.github.majorbolt.dockinggrid.hasUnevenSplits
import io.github.majorbolt.dockinggrid.isHeaderAutoHidden
import io.github.majorbolt.dockinggrid.isHeaderAutoHideForcedByGrid
import io.github.majorbolt.dockinggrid.verticalHeaderSide
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import io.github.majorbolt.dockinggrid.matchingPreset
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

    /** An empty center panel created by a center preset. */
    public fun emptyCenterPanel(panelId: String): String = "dock_empty_center_panel_$panelId"

    public fun divider(target: DividerTarget): String = "dock_divider_$target"

    /** The center grid's header (shown with two or more center panels). */
    public const val CENTER_HEADER: String = "dock_center_header"

    /** The sliding header of a group whose header auto-hides. */
    public fun autoHidingHeader(groupId: String): String = "dock_auto_hiding_header_$groupId"
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
 * @param verticalHeader the header strip of a group whose header is vertical, with one tab per dockable marked with
 *   [DockGroupScope.dockTab]; the whole strip is the drag handle of the selected dockable.
 * @param centerHeader the header of the center grid while it holds two or more panels.
 * @param dockableIcon draws the icon of a dockable, used by vertical headers; `null` when the host has no icons.
 * @param divider draws a divider inside the area the host reserves for it.
 * @param placeholder the empty center, an empty center panel and the slot of a dragged dockable, with the text to show.
 * @param dragLabel follows the pointer while a dockable is dragged, with its title.
 * @param dragController lets code outside the host drag dockables over it; see [DockDragController].
 * @param onCenterSplitsResizableChange when set, the center header can lock and unlock the center dividers
 *   ([CenterGridScope.setSplitsResizable]); the caller stores the choice and passes it back in [metrics].
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
    verticalHeader: @Composable DockGroupScope.() -> Unit = { BasicDockVerticalHeader() },
    centerHeader: @Composable CenterGridScope.() -> Unit = { BasicCenterHeader() },
    dockableIcon: (@Composable (DockableId) -> Unit)? = null,
    divider: @Composable (DockDividerState) -> Unit = { state -> BasicDockDivider(state) },
    placeholder: @Composable (text: String) -> Unit = { text -> BasicDockPlaceholder(text) },
    dragLabel: @Composable (title: String) -> Unit = { title -> BasicDockDragLabel(title) },
    dragController: DockDragController? = null,
    onCenterSplitsResizableChange: ((Boolean) -> Unit)? = null,
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

            val currentDensity by rememberUpdatedState(density)
            val hostPlacement = remember { HostPlacement() }

            /** The window position in content dp, or `null` while the host is not placed. */
            fun contentPosition(windowPosition: Offset): Offset? {
                val coordinates = hostPlacement.coordinates?.takeIf { layoutCoordinates -> layoutCoordinates.isAttached } ?: return null
                val localPosition = coordinates.windowToLocal(windowPosition)
                return Offset(localPosition.x / currentDensity, localPosition.y / currentDensity)
            }

            val externalDragHost = remember {
                object : ExternalDockDragHost {
                    private val externalDrag: DockInteraction.DraggingDockable?
                        get() = (interaction as? DockInteraction.DraggingDockable)?.takeIf { dragging -> dragging.isExternal }

                    override val isExternalDragActive: Boolean get() = externalDrag != null

                    override val isExternalDragOverDropTarget: Boolean
                        get() = externalDrag?.preview?.changesLayout == true

                    override fun startExternalDrag(dockableId: DockableId, windowPosition: Offset): Boolean {
                        if (interaction != DockInteraction.Idle || currentEnvironment.catalog.find(dockableId) == null) return false
                        val position = contentPosition(windowPosition) ?: return false
                        interaction = startDockableDrag(
                            dockableId = dockableId,
                            horizontalPosition = position.x,
                            verticalPosition = position.y,
                            environment = currentEnvironment,
                            isExternal = true,
                        )
                        // The host takes the focus, so Escape cancels the drag as it does for header drags.
                        focusRequester.requestFocus()
                        return true
                    }

                    override fun updateExternalDrag(windowPosition: Offset) {
                        val dragging = externalDrag ?: return
                        val position = contentPosition(windowPosition) ?: return
                        interaction = dragging.movedTo(position.x, position.y, currentEnvironment)
                    }

                    override fun dropExternalDrag(): Boolean {
                        if (externalDrag == null) return false
                        val (operation, nextInteraction) = interaction.finished()
                        interaction = nextInteraction
                        operation?.let(currentOnApplyOperation)
                        return operation != null
                    }

                    override fun cancelExternalDrag() {
                        if (externalDrag != null) interaction = interaction.cancelled()
                    }
                }
            }
            DisposableEffect(dragController, externalDragHost) {
                dragController?.host = externalDragHost
                onDispose {
                    // A drag that is still running ends with the host; the controller then reports no drag.
                    if (dragController?.host === externalDragHost) dragController.host = null
                }
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
            // An auto-hidden grid header pushes the panels down while it is revealed. Interactions work on the committed
            // geometry, in which it is hidden, so it folds away while one runs.
            var isCenterHeaderRevealRequested by remember { mutableStateOf(false) }
            val revealsCenterHeader = isCenterHeaderRevealRequested && interaction == DockInteraction.Idle &&
                displayedLayout.center.hasHeader && displayedLayout.center.autoHidesHeader
            val centerHeaderRevealFraction by animateFloatAsState(
                targetValue = if (revealsCenterHeader) 1f else 0f,
                animationSpec = tween(durationMillis = AUTO_HIDE_ANIMATION_MILLIS),
            )
            val displayedGeometry = remember(displayedLayout, contentSize, catalog, metrics, committedGeometry, centerHeaderRevealFraction) {
                if (displayedLayout == profile.layout && centerHeaderRevealFraction == 0f) {
                    committedGeometry
                } else {
                    calculateDockGeometry(displayedLayout, contentSize, catalog, metrics, centerHeaderRevealFraction)
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
                    .onPlaced { coordinates -> hostPlacement.coordinates = coordinates }
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
                                // With two or more panels the grid header offers the arrangements.
                                centerPreset = profile.layout.center.matchingPreset?.takeIf {
                                    capabilities.isCenterGridEnabled && !displayedLayout.center.hasHeader &&
                                        displayedLayout.center.findPanelOf(group.id) != null
                                },
                                onArrangeCenter = { preset -> onApplyOperation(DockLayoutOperation.ArrangeCenter(preset)) },
                                headerPresentation = HeaderPresentation(
                                    side = if (group.headerStyle.isVertical) displayedLayout.verticalHeaderSide(group.id) else null,
                                    isAutoHidden = displayedLayout.isHeaderAutoHidden(group.id, metrics.centerHeaderHidesPanelHeaders),
                                    isAutoHideForcedByGrid = displayedLayout.isHeaderAutoHideForcedByGrid(group.id, metrics.centerHeaderHidesPanelHeaders),
                                    // Headers are drop targets for tabs, so a drag shows them all.
                                    isForcedVisible = interaction is DockInteraction.DraggingDockable,
                                ),
                                onHeaderStyleChange = { headerStyle -> onApplyOperation(DockLayoutOperation.SetGroupHeaderStyle(group.id, headerStyle)) },
                                dockableIcon = dockableIcon,
                                tabStrip = tabStrip,
                                header = header,
                                verticalHeader = verticalHeader,
                                content = { dockableId -> contentOf(dockableId)() },
                                modifier = Modifier
                                    .placedAt(groupRect)
                                    .testTag(DockHostTestTags.group(group.id)),
                            )
                        }
                    }
                }

                // A lifted dockable is not rendered in a group; keep its content composed (invisible),
                // so that its state survives the drag. A closed dockable dragged in from outside has no state yet.
                (interaction as? DockInteraction.DraggingDockable)?.dockableId
                    ?.takeIf { draggedDockableId -> profile.layout.contains(draggedDockableId) }
                    ?.takeIf { draggedDockableId ->
                        draggedDockableId == liftedDockableId || displayedLayout.findGroupOf(draggedDockableId)?.selectedDockableId != draggedDockableId
                    }
                    ?.let { draggedDockableId ->
                        key(draggedDockableId) {
                            Box(modifier = Modifier.size(0.dp).clipToBounds()) { contentOf(draggedDockableId)() }
                        }
                    }

                if (displayedLayout.center.isEmpty) {
                    Box(modifier = Modifier.placedAt(displayedGeometry.centerRect).testTag(DockHostTestTags.EMPTY_CENTER), propagateMinConstraints = true) {
                        placeholder(style.strings.emptyCenter)
                    }
                }
                displayedLayout.center.panels.filter { panel -> panel.group == null }.forEach { emptyPanel ->
                    val panelRect = displayedGeometry.centerPanelRects[emptyPanel.id] ?: return@forEach
                    key(emptyPanel.id) {
                        Box(
                            modifier = Modifier.placedAt(panelRect).testTag(DockHostTestTags.emptyCenterPanel(emptyPanel.id)),
                            propagateMinConstraints = true,
                        ) {
                            placeholder(style.strings.emptyCenterPanel)
                        }
                    }
                }

                val centerHeaderRect = displayedGeometry.centerHeaderRect
                val centerPreset = displayedLayout.center.matchingPreset
                if (centerHeaderRect != null && centerPreset != null && capabilities.isCenterGridEnabled) {
                    val centerScope = HostCenterGridScope(
                        preset = centerPreset,
                        onArrangeCenter = { preset -> onApplyOperation(DockLayoutOperation.ArrangeCenter(preset)) },
                        splitsResizable = metrics.centerSplitsResizable,
                        onSplitsResizableChange = onCenterSplitsResizableChange,
                        hasUnevenSplits = profile.layout.center.hasUnevenSplits,
                        onResetSplits = { onApplyOperation(DockLayoutOperation.ResetCenterSplits) },
                        autoHidesHeader = displayedLayout.center.autoHidesHeader,
                        onAutoHidesHeaderChange = { autoHides -> onApplyOperation(DockLayoutOperation.SetCenterHeaderAutoHide(autoHides)) },
                    )
                    if (displayedLayout.center.autoHidesHeader) {
                        // Only the top edge of the grid reveals it, so the panels' own headers below stay reachable. It is
                        // no drop target, so a drag does not reveal it.
                        RevealingCenterHeader(
                            rect = centerHeaderRect,
                            revealFraction = centerHeaderRevealFraction,
                            onRevealRequestChange = { isRequested -> isCenterHeaderRevealRequested = isRequested },
                        ) {
                            centerScope.centerHeader()
                        }
                    } else {
                        Box(modifier = Modifier.placedAt(centerHeaderRect).testTag(DockHostTestTags.CENTER_HEADER)) {
                            centerScope.centerHeader()
                        }
                    }
                }

                displayedGeometry.dividers.forEach { displayedDivider ->
                    key(displayedDivider.target) {
                        DockDividerHandle(
                            movesHorizontally = displayedDivider.movesHorizontally,
                            isResizable = displayedDivider.isResizable,
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
                            modifier = Modifier.placedAt(displayedDivider.rect).testTag(DockHostTestTags.divider(displayedDivider.target)),
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

                // An external drag shows the label only inside the host; outside it, the drag source draws its own feedback.
                (interaction as? DockInteraction.DraggingDockable)?.takeIf { dragging ->
                    !dragging.isExternal || DockRect(0f, 0f, contentSize.widthDp, contentSize.heightDp)
                        .contains(dragging.pointerHorizontalPosition, dragging.pointerVerticalPosition)
                }?.let { draggingInteraction ->
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

/**
 * An auto-hidden center grid header: a hover zone along the top edge of the grid that asks to reveal it, and the
 * header itself, uncovered from the top as far as [revealFraction] (the host moves the panels down by as much).
 */
@Composable
private fun RevealingCenterHeader(
    rect: DockRect,
    revealFraction: Float,
    onRevealRequestChange: (Boolean) -> Unit,
    header: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val holds = remember { HeaderVisibilityHolds() }
    val isRequested = isHovered || holds.count > 0
    val currentOnRevealRequestChange by rememberUpdatedState(onRevealRequestChange)
    LaunchedEffect(isRequested) { currentOnRevealRequestChange(isRequested) }
    DisposableEffect(Unit) { onDispose { currentOnRevealRequestChange(false) } }
    val shownHeight = rect.height * revealFraction
    Box(
        modifier = Modifier
            .absoluteOffset(x = rect.left.dp, y = rect.top.dp)
            .width(rect.width.dp)
            .height(maxOf(CENTER_HEADER_HOVER_ZONE.value, shownHeight).dp)
            .hoverable(interactionSource)
            .clipToBounds()
            .testTag(DockHostTestTags.CENTER_HEADER),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rect.height.dp)
                .absoluteOffset(y = (shownHeight - rect.height).dp),
        ) {
            CompositionLocalProvider(LocalHeaderVisibilityHolds provides holds) { header() }
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

/** Where the host is placed, for converting window positions of external drags; not observed by composition. */
private class HostPlacement {
    var coordinates: LayoutCoordinates? = null
}

/** Pointer bookkeeping that must not trigger recomposition. */
private class PointerTracking {
    val lastPositions = mutableMapOf<PointerId, Offset>()
    var draggingPointer: PointerId? = null
}

private const val DRAG_LABEL_OFFSET_DP = 12f

/** The top edge of the center grid that shows its auto-hidden header; below it the panels' headers take over. */
private val CENTER_HEADER_HOVER_ZONE = 6.dp

/** Merges a measured header into the metrics; tab widths of all dockables seen so far are kept. */
private fun GroupHeaderMetrics.updatedWith(measurement: HeaderMeasurement): GroupHeaderMetrics = copy(
    headerTopInsetDp = measurement.topInsetDp,
    tabRowHeightDp = measurement.tabRowHeightDp ?: tabRowHeightDp,
    headerRowHeightDp = measurement.headerHeightDp,
    tabStartPaddingDp = measurement.tabStartDp ?: tabStartPaddingDp,
    tabWidthsDp = tabWidthsDp + measurement.tabWidthsDp,
)
