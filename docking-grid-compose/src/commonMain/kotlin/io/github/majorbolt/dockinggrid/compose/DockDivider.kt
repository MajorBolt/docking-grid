package io.github.majorbolt.dockinggrid.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity

/**
 * What a divider slot needs to draw a divider. The [DockHost] owns its size, gesture and resize cursor.
 *
 * @property movesHorizontally `true` for a vertical divider line that is moved left and right.
 * @property isDragged `true` while the user drags this divider.
 */
@Immutable
public data class DockDividerState(
    val movesHorizontally: Boolean,
    val isDragged: Boolean,
)

/** The default divider: a line in [DockingColors.divider] that fills the whole divider area. */
@Composable
public fun BasicDockDivider(state: DockDividerState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(LocalDockingStyle.current.colors.divider))
}

/**
 * Draggable divider area that draws [divider] inside. Reports the total pointer distance in dp since the press, so
 * the caller can derive the new size from the size at gesture start without accumulating drift; the divider follows
 * the pointer exactly.
 */
@Composable
internal fun DockDividerHandle(
    movesHorizontally: Boolean,
    onResizeStart: () -> Unit,
    onResize: (totalDragDistanceDp: Float) -> Unit,
    onResizeEnd: () -> Unit,
    onResizeCancel: () -> Unit,
    divider: @Composable (DockDividerState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val orientation = if (movesHorizontally) Orientation.Horizontal else Orientation.Vertical
    val resizePointerIcon = remember(orientation) { resizePointerIcon(orientation) }
    val density = LocalDensity.current.density
    val currentOnResizeStart by rememberUpdatedState(onResizeStart)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnResizeEnd by rememberUpdatedState(onResizeEnd)
    val currentOnResizeCancel by rememberUpdatedState(onResizeCancel)
    var isDragged by remember { mutableStateOf(false) }

    val coordinatesHolder = remember { CoordinatesHolder() }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates -> coordinatesHolder.coordinates = coordinates }
            .pointerHoverIcon(resizePointerIcon)
            .pointerInput(movesHorizontally, density) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val slopChange = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        ?: return@awaitEachGesture
                    // Measured in root coordinates from the press position: the divider itself moves with the resize,
                    // so local positions and positionChange() would under-report the pointer movement, and
                    // detectDragGestures would additionally drop the touch-slop distance.
                    val pressPositionInRoot = coordinatesHolder.toRoot(down.position)
                    fun reportDistanceTo(localPosition: Offset) {
                        val distancePixels = (coordinatesHolder.toRoot(localPosition) - pressPositionInRoot).alongAxis(movesHorizontally)
                        currentOnResize(distancePixels / density)
                    }
                    isDragged = true
                    try {
                        currentOnResizeStart()
                        reportDistanceTo(slopChange.position)
                        val completed = drag(slopChange.id) { change ->
                            change.consume()
                            reportDistanceTo(change.position)
                        }
                        if (completed) currentOnResizeEnd() else currentOnResizeCancel()
                    } finally {
                        isDragged = false
                    }
                }
            },
    ) {
        divider(DockDividerState(movesHorizontally = movesHorizontally, isDragged = isDragged))
    }
}

private fun Offset.alongAxis(horizontal: Boolean): Float = if (horizontal) x else y

/** Latest layout coordinates of the divider; not state, because it never drives recomposition. */
private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null

    fun toRoot(localPosition: Offset): Offset {
        val currentCoordinates = coordinates?.takeIf { layoutCoordinates -> layoutCoordinates.isAttached } ?: return localPosition
        return currentCoordinates.localToRoot(localPosition)
    }
}
