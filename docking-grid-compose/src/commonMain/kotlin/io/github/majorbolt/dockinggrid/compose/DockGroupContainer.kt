package io.github.majorbolt.dockinggrid.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.CenterPreset
import io.github.majorbolt.dockinggrid.TabGroup
import io.github.majorbolt.dockinggrid.geometry.DockRect

/**
 * What the tab strip and header slots of a [DockHost] can use to render one tab group.
 *
 * A group is laid out from top to bottom as: the tab strip (only when the group holds more than one dockable), the
 * header row of the selected dockable, and its content. The host calculates drop positions for every layout from
 * this structure and the measured sizes, so custom chrome must keep it.
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
     * The current arrangement of the center when this group is a center panel and the center grid is enabled
     * ([io.github.majorbolt.dockinggrid.DockingCapabilities.isCenterGridEnabled]); `null` otherwise. Chrome can offer
     * [arrangeCenter] while it is not `null`.
     */
    public val centerPreset: CenterPreset? get() = null

    /** Rearranges the center as [preset]; the host commits this as a layout change. Only for center panels. */
    public fun arrangeCenter(preset: CenterPreset) {}

    /** Whether the dividers of the center grid can be dragged ([io.github.majorbolt.dockinggrid.geometry.DockingMetrics.centerSplitsResizable]). */
    public val centerSplitsResizable: Boolean get() = true

    /** `true` when the host lets the chrome lock and unlock the center dividers with [setCenterSplitsResizable]. */
    public val canChangeCenterSplitsResizable: Boolean get() = false

    /** Asks the host to lock (`false`) or unlock the center dividers; see [canChangeCenterSplitsResizable]. */
    public fun setCenterSplitsResizable(resizable: Boolean) {}

    /** `true` when a split of the center grid is not even, so [resetCenterSplits] would change it. */
    public val hasUnevenCenterSplits: Boolean get() = false

    /** Sets every center split back to even; the host commits this as a layout change. Only for center panels. */
    public fun resetCenterSplits() {}

    /**
     * Marks the tab of [dockableId] in a tab strip. Required on every tab: it starts dragging the dockable from the
     * tab and reports the tab's bounds, from which the insertion position of a dropped tab is calculated. A tab
     * without it falls back to [io.github.majorbolt.dockinggrid.geometry.GroupHeaderMetrics.fallbackTabWidthDp].
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

/**
 * Renders a tab group with the given chrome and reports the measured header sizes, from which drop targets of any
 * layout are calculated.
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
    centerSplitsResizable: Boolean,
    onCenterSplitsResizableChange: ((Boolean) -> Unit)?,
    hasUnevenCenterSplits: Boolean,
    onResetCenterSplits: () -> Unit,
    tabStrip: @Composable DockGroupScope.() -> Unit,
    header: @Composable DockGroupScope.() -> Unit,
    content: @Composable (DockableId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalDockingStyle.current
    val density = LocalDensity.current.density
    val currentOnHeaderMeasured by rememberUpdatedState(onHeaderMeasured)
    val measurement = remember { HeaderCoordinates() }
    val selectedDockableId = group.selectedDockableId
    val hasTabStrip = group.dockableIds.size > 1

    fun reportHeader() {
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
        centerSplitsResizable = centerSplitsResizable,
        onCenterSplitsResizableChange = onCenterSplitsResizableChange,
        hasUnevenCenterSplits = hasUnevenCenterSplits,
        onResetCenterSplits = onResetCenterSplits,
        onTabPositioned = { dockableId, coordinates ->
            measurement.tabCoordinates[dockableId] = coordinates
            reportHeader()
        },
    )

    Column(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                measurement.containerCoordinates = coordinates
                reportHeader()
            }
            .padding(style.groupSpacing)
            .clip(style.groupShape)
            .then(
                if(style.groupBorderWidth.value > 0)
                    Modifier.border(style.groupBorderWidth, style.colors.groupBorder, style.groupShape)
                else Modifier
            )
            .background(style.colors.groupBackground),
    ) {
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(style.contentPadding),
        ) {
            content(selectedDockableId)
        }
    }
}

private class GroupScope(
    override val group: TabGroup,
    private val titleProvider: (DockableId) -> String,
    private val onSelect: (DockableId) -> Unit,
    private val onClose: (DockableId) -> Unit,
    private val dragHandleModifier: (DockableId) -> Modifier,
    override val centerPreset: CenterPreset?,
    private val onArrangeCenter: (CenterPreset) -> Unit,
    override val centerSplitsResizable: Boolean,
    private val onCenterSplitsResizableChange: ((Boolean) -> Unit)?,
    override val hasUnevenCenterSplits: Boolean,
    private val onResetCenterSplits: () -> Unit,
    private val onTabPositioned: (DockableId, LayoutCoordinates) -> Unit,
) : DockGroupScope {
    override fun arrangeCenter(preset: CenterPreset) = onArrangeCenter(preset)

    override val canChangeCenterSplitsResizable: Boolean get() = onCenterSplitsResizableChange != null

    override fun setCenterSplitsResizable(resizable: Boolean) {
        onCenterSplitsResizableChange?.invoke(resizable)
    }

    override fun resetCenterSplits() = onResetCenterSplits()

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
