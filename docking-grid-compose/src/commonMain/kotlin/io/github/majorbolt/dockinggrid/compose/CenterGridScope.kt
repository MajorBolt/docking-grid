package io.github.majorbolt.dockinggrid.compose

import androidx.compose.runtime.Stable
import io.github.majorbolt.dockinggrid.CenterPreset

/**
 * What the center header slot of a [DockHost] can use. The center grid has its own header while it holds two or
 * more panels; it acts on the whole grid.
 */
@Stable
public interface CenterGridScope {
    /** The current arrangement of the center. */
    public val preset: CenterPreset

    /** Rearranges the center as [preset]; the host commits this as a layout change. */
    public fun arrangeCenter(preset: CenterPreset)

    /** Whether the dividers of the grid can be dragged ([io.github.majorbolt.dockinggrid.geometry.DockingMetrics.centerSplitsResizable]). */
    public val splitsResizable: Boolean

    /** `true` when the host lets the chrome lock and unlock the dividers with [setSplitsResizable]. */
    public val canChangeSplitsResizable: Boolean

    /** Asks the host to lock (`false`) or unlock the dividers; see [canChangeSplitsResizable]. */
    public fun setSplitsResizable(resizable: Boolean)

    /** `true` when a split is not even, so [resetSplits] would change it. */
    public val hasUnevenSplits: Boolean

    /** Sets every split back to even; the host commits this as a layout change. */
    public fun resetSplits()

    /** Whether the grid header is hidden until hovered; see [io.github.majorbolt.dockinggrid.isHeaderAutoHidden]. */
    public val autoHidesHeader: Boolean

    /** Hides the grid header until hovered, or shows it again; the host commits this as a layout change. */
    public fun setAutoHidesHeader(autoHides: Boolean)
}

internal class HostCenterGridScope(
    override val preset: CenterPreset,
    private val onArrangeCenter: (CenterPreset) -> Unit,
    override val splitsResizable: Boolean,
    private val onSplitsResizableChange: ((Boolean) -> Unit)?,
    override val hasUnevenSplits: Boolean,
    private val onResetSplits: () -> Unit,
    override val autoHidesHeader: Boolean,
    private val onAutoHidesHeaderChange: (Boolean) -> Unit,
) : CenterGridScope {
    override fun arrangeCenter(preset: CenterPreset) = onArrangeCenter(preset)

    override val canChangeSplitsResizable: Boolean get() = onSplitsResizableChange != null

    override fun setSplitsResizable(resizable: Boolean) {
        onSplitsResizableChange?.invoke(resizable)
    }

    override fun resetSplits() = onResetSplits()

    override fun setAutoHidesHeader(autoHides: Boolean) = onAutoHidesHeaderChange(autoHides)
}
