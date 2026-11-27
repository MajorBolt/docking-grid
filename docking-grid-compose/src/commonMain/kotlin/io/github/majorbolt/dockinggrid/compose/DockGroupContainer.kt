package io.github.majorbolt.dockinggrid.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.majorbolt.dockinggrid.CenterPreset
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.GroupHeaderStyle
import io.github.majorbolt.dockinggrid.TabGroup
import io.github.majorbolt.dockinggrid.geometry.DockRect

/**
 * What the tab strip and header slots of a [DockHost] can use to render one tab group.
 *
 * A group is laid out from top to bottom as: the tab strip (only when the group holds more than one dockable), the
 * header row of the selected dockable, and its content. With a vertical header ([headerSide] not `null`) the
 * vertical header slot is a strip beside the content instead, with one icon tab per dockable. The host calculates
 * drop positions for every layout from this structure and the measured sizes, so custom chrome must keep it.
 */
@Stable
public interface DockGroupScope {
    /** The group to render. During a drag the dragged dockable is missing from its source group. */
    public val group: TabGroup

    public fun titleOf(dockableId: DockableId): String

    /** Makes [dockableId] the visible tab; the host commits this as a layout change. */
    public fun select(dockableId: DockableId)

    public fun close(dockableId: DockableId)

    /**
     * The arrangement of the center when this group is the only center panel and the center grid is enabled; `null`
     * otherwise (with two or more panels the grid header offers the arrangements). Chrome can offer [arrangeCenter]
     * while it is not `null`.
     */
    public val centerPreset: CenterPreset? get() = null

    /** Rearranges the center as [preset]; the host commits this as a layout change. Only for center panels. */
    public fun arrangeCenter(preset: CenterPreset) {}

    /** How this group's header is shown; see [setHeaderStyle]. */
    public val headerStyle: GroupHeaderStyle get() = group.headerStyle

    /** Changes how this group's header is shown; the host commits this as a layout change. */
    public fun setHeaderStyle(headerStyle: GroupHeaderStyle) {}

    /** `true` while the center grid's auto-hide hides this group's header regardless of [headerStyle]. */
    public val isHeaderAutoHideForcedByGrid: Boolean get() = false

    /**
     * `true` while the header lies over the content (it auto-hides and slides in on hover). Chrome should then leave
     * its background transparent and give only its texts, icons and buttons a background, so the content shows
     * around them.
     */
    public val isHeaderOverlay: Boolean get() = false

    /** The side of a vertical header, or `null` for a header row at the top. */
    public val headerSide: DockSide? get() = null

    /** Draws the icon of [dockableId] when the host has one ([DockHost]'s `dockableIcon`); `false` otherwise. */
    public val hasDockableIcons: Boolean get() = false

    @Composable
    public fun DockableIcon(dockableId: DockableId) {}

    /**
     * Marks the tab of [dockableId] in a tab strip or a vertical header. Required on every tab: it starts dragging
     * the dockable from the tab and reports the tab's bounds, from which the insertion position of a dropped tab is
     * calculated. A tab without it falls back to [io.github.majorbolt.dockinggrid.geometry.GroupHeaderMetrics.fallbackTabWidthDp].
     *
     * The header row needs no marker; the host makes it the drag handle of the selected dockable.
     */
    public fun Modifier.dockTab(dockableId: DockableId): Modifier
}

/**
 * Sizes of a rendered group header in dp, relative to the group container's origin.
 *
 * @property topInsetDp space between the container top and the first header row.
 * @property tabRowHeightDp height of the tab row, `null` for a group with a single dockable (no tab row).
 * @property headerHeightDp height of the selected dockable's header row.
 * @property tabStartDp left edge of the first tab, `null` without a tab row.
 * @property tabWidthsDp width of each rendered tab.
 */
internal data class HeaderMeasurement(
    val topInsetDp: Float,
    val tabRowHeightDp: Float?,
    val headerHeightDp: Float,
    val tabStartDp: Float?,
    val tabWidthsDp: Map<DockableId, Float>,
)

/** How a group container shows its header; derived by the host from the layout. */
internal data class HeaderPresentation(
    /** `null` for a header row at the top, otherwise the side of the vertical header strip. */
    val side: DockSide?,
    val isAutoHidden: Boolean,
    val isAutoHideForcedByGrid: Boolean,
    /** An auto-hidden header is shown regardless of the pointer, for example while a dockable is dragged. */
    val isForcedVisible: Boolean,
)

/**
 * Renders a tab group with the given chrome and reports the measured header sizes, from which drop targets of any
 * layout are calculated. Only a header row that is always shown is measured: a vertical header uses the fixed
 * sizes of [io.github.majorbolt.dockinggrid.geometry.GroupHeaderMetrics], and an auto-hidden one moves while it slides.
 */
@Composable
internal fun DockGroupContainer(
    group: TabGroup,
    titleOf: (DockableId) -> String,
    onSelectTab: (DockableId) -> Unit,
    onCloseDockable: (DockableId) -> Unit,
    dragHandleModifier: (DockableId) -> Modifier,
    onHeaderMeasured: (HeaderMeasurement) -> Unit,
    centerPreset: CenterPreset?,
    onArrangeCenter: (CenterPreset) -> Unit,
    headerPresentation: HeaderPresentation,
    onHeaderStyleChange: (GroupHeaderStyle) -> Unit,
    dockableIcon: (@Composable (DockableId) -> Unit)?,
    tabStrip: @Composable DockGroupScope.() -> Unit,
    header: @Composable DockGroupScope.() -> Unit,
    verticalHeader: @Composable DockGroupScope.() -> Unit,
    content: @Composable (DockableId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalDockingStyle.current
    val density = LocalDensity.current.density
    val currentOnHeaderMeasured by rememberUpdatedState(onHeaderMeasured)
    val measurement = remember { HeaderCoordinates() }
    val selectedDockableId = group.selectedDockableId
    val hasTabStrip = group.dockableIds.size > 1
    val isMeasured = headerPresentation.side == null && !headerPresentation.isAutoHidden

    fun reportHeader() {
        if (!isMeasured) return
        val containerCoordinates = measurement.containerCoordinates?.takeIf { coordinates -> coordinates.isAttached } ?: return
        fun boundsOf(coordinates: LayoutCoordinates?): DockRect? {
            if (coordinates == null || !coordinates.isAttached) return null
            val bounds = containerCoordinates.localBoundingBoxOf(coordinates, clipBounds = false)
            return DockRect(bounds.left / density, bounds.top / density, bounds.width / density, bounds.height / density)
        }
        val headerBounds = boundsOf(measurement.headerCoordinates) ?: return
        val tabBounds = if (hasTabStrip) {
            group.dockableIds.map { dockableId -> dockableId to (boundsOf(measurement.tabCoordinates[dockableId]) ?: return) }
        } else {
            emptyList()
        }
        val tabRowTop = tabBounds.minOfOrNull { (_, bounds) -> bounds.top }
        val tabRowBottom = tabBounds.maxOfOrNull { (_, bounds) -> bounds.bottom }
        currentOnHeaderMeasured(
            HeaderMeasurement(
                topInsetDp = tabRowTop ?: headerBounds.top,
                tabRowHeightDp = if (tabRowTop != null && tabRowBottom != null) tabRowBottom - tabRowTop else null,
                headerHeightDp = headerBounds.height,
                tabStartDp = tabBounds.firstOrNull()?.second?.left,
                tabWidthsDp = tabBounds.associate { (dockableId, bounds) -> dockableId to bounds.width },
            ),
        )
    }

    val scope = GroupScope(
        group = group,
        titleProvider = titleOf,
        onSelect = onSelectTab,
        onClose = onCloseDockable,
        dragHandleModifier = dragHandleModifier,
        centerPreset = centerPreset,
        onArrangeCenter = onArrangeCenter,
        onHeaderStyleChange = onHeaderStyleChange,
        isHeaderAutoHideForcedByGrid = headerPresentation.isAutoHideForcedByGrid,
        isHeaderOverlay = headerPresentation.isAutoHidden,
        headerSide = headerPresentation.side,
        dockableIcon = dockableIcon,
        onTabPositioned = { dockableId, coordinates ->
            measurement.tabCoordinates[dockableId] = coordinates
            reportHeader()
        },
    )

    /** The header row (with the tab strip above it), the drag handle of the selected dockable. */
    @Composable
    fun HorizontalHeader() {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (hasTabStrip) {
                Box(modifier = Modifier.fillMaxWidth()) { scope.tabStrip() }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(dragHandleModifier(selectedDockableId))
                    .onGloballyPositioned { coordinates ->
                        measurement.headerCoordinates = coordinates
                        reportHeader()
                    },
            ) {
                scope.header()
            }
        }
    }

    @Composable
    fun VerticalHeader() {
        Box(modifier = Modifier.fillMaxHeight().then(dragHandleModifier(selectedDockableId))) {
            scope.verticalHeader()
        }
    }

    @Composable
    fun Content(modifier: Modifier) {
        Box(modifier = modifier.padding(style.contentPadding)) {
            content(selectedDockableId)
        }
    }

    val side = headerPresentation.side
    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                measurement.containerCoordinates = coordinates
                reportHeader()
            }
            .padding(style.groupSpacing)
            .clip(style.groupShape)
            .then(
                if (style.groupBorderWidth.value > 0) {
                    Modifier.border(style.groupBorderWidth, style.colors.groupBorder, style.groupShape)
                } else {
                    Modifier
                },
            )
            .background(style.colors.groupBackground),
    ) {
        when {
            headerPresentation.isAutoHidden -> {
                Content(Modifier.fillMaxSize())
                AutoHidingHeader(
                    side = side,
                    isForcedVisible = headerPresentation.isForcedVisible,
                    modifier = Modifier
                        .align(
                            when (side) {
                                null -> Alignment.TopCenter
                                DockSide.RIGHT -> Alignment.CenterEnd
                                else -> Alignment.CenterStart
                            },
                        )
                        .testTag(DockHostTestTags.autoHidingHeader(group.id)),
                ) {
                    if (side == null) HorizontalHeader() else VerticalHeader()
                }
            }
            side == null -> Column(modifier = Modifier.fillMaxSize()) {
                HorizontalHeader()
                Content(Modifier.weight(1f).fillMaxWidth())
            }
            else -> Row(modifier = Modifier.fillMaxSize()) {
                if (side != DockSide.RIGHT) VerticalHeader()
                Content(Modifier.weight(1f).fillMaxHeight())
                if (side == DockSide.RIGHT) VerticalHeader()
            }
        }
    }
}

/** How many parts of an auto-hidden header want it to stay shown, for example an open menu; see [KeepHeaderVisible]. */
internal class HeaderVisibilityHolds {
    var count by mutableIntStateOf(0)
}

internal val LocalHeaderVisibilityHolds = staticCompositionLocalOf<HeaderVisibilityHolds?> { null }

/**
 * Keeps the enclosing auto-hidden header shown while [isHeld], although the pointer leaves it. Call it from chrome
 * that opens a popup from a header (a menu, a picker): the popup lies outside the header, so without it the header
 * would slide out and take the open popup along. Has no effect outside an auto-hidden header.
 */
@Composable
public fun KeepHeaderVisible(isHeld: Boolean) {
    val holds = LocalHeaderVisibilityHolds.current ?: return
    DisposableEffect(holds, isHeld) {
        if (isHeld) holds.count++
        onDispose { if (isHeld) holds.count-- }
    }
}

/**
 * A header that lies over the content: hidden except for a thin hover zone along its edge, it slides in while the
 * pointer is over the zone or the header, while [isForcedVisible], and while a part of it holds it ([KeepHeaderVisible]).
 */
@Composable
internal fun AutoHidingHeader(
    side: DockSide?,
    isForcedVisible: Boolean,
    modifier: Modifier = Modifier,
    hoverZone: Dp = AUTO_HIDE_HOVER_ZONE,
    header: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val holds = remember { HeaderVisibilityHolds() }
    val isVisible = isHovered || isForcedVisible || holds.count > 0
    val animation = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = AUTO_HIDE_ANIMATION_MILLIS)
    Box(
        modifier = modifier
            .then(if (side == null) Modifier.fillMaxWidth().heightIn(min = hoverZone) else Modifier.fillMaxHeight().widthIn(min = hoverZone))
            .hoverable(interactionSource),
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = when (side) {
                null -> slideInVertically(animation) { height -> -height }
                DockSide.RIGHT -> slideInHorizontally(animation) { width -> width }
                else -> slideInHorizontally(animation) { width -> -width }
            },
            exit = when (side) {
                null -> slideOutVertically(animation) { height -> -height }
                DockSide.RIGHT -> slideOutHorizontally(animation) { width -> width }
                else -> slideOutHorizontally(animation) { width -> -width }
            },
        ) {
            CompositionLocalProvider(LocalHeaderVisibilityHolds provides holds) {
                header()
            }
        }
    }
}

/** How close to an auto-hidden header's edge the pointer must come to show it. */
internal val AUTO_HIDE_HOVER_ZONE: Dp = 12.dp
internal const val AUTO_HIDE_ANIMATION_MILLIS = 150

private class GroupScope(
    override val group: TabGroup,
    private val titleProvider: (DockableId) -> String,
    private val onSelect: (DockableId) -> Unit,
    private val onClose: (DockableId) -> Unit,
    private val dragHandleModifier: (DockableId) -> Modifier,
    override val centerPreset: CenterPreset?,
    private val onArrangeCenter: (CenterPreset) -> Unit,
    private val onHeaderStyleChange: (GroupHeaderStyle) -> Unit,
    override val isHeaderAutoHideForcedByGrid: Boolean,
    override val isHeaderOverlay: Boolean,
    override val headerSide: DockSide?,
    private val dockableIcon: (@Composable (DockableId) -> Unit)?,
    private val onTabPositioned: (DockableId, LayoutCoordinates) -> Unit,
) : DockGroupScope {
    override fun arrangeCenter(preset: CenterPreset) = onArrangeCenter(preset)

    override fun setHeaderStyle(headerStyle: GroupHeaderStyle) = onHeaderStyleChange(headerStyle)

    override val hasDockableIcons: Boolean get() = dockableIcon != null

    @Composable
    override fun DockableIcon(dockableId: DockableId) {
        dockableIcon?.invoke(dockableId)
    }

    override fun titleOf(dockableId: DockableId): String = titleProvider(dockableId)

    override fun select(dockableId: DockableId) = onSelect(dockableId)

    override fun close(dockableId: DockableId) = onClose(dockableId)

    override fun Modifier.dockTab(dockableId: DockableId): Modifier = this
        .then(dragHandleModifier(dockableId))
        .onGloballyPositioned { coordinates -> onTabPositioned(dockableId, coordinates) }
}

/** Layout coordinates gathered during placement; not state, because they never drive recomposition. */
private class HeaderCoordinates {
    var containerCoordinates: LayoutCoordinates? = null
    var headerCoordinates: LayoutCoordinates? = null
    val tabCoordinates = mutableMapOf<DockableId, LayoutCoordinates>()
}
