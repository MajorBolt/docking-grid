package io.github.majorbolt.dockinggrid.compose

import android.view.PointerIcon as AndroidPointerIcon
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.input.pointer.PointerIcon

/**
 * Android shows a pointer only with a mouse or trackpad; the platform's double-arrow pointer types are the
 * convention for resizable dividers there.
 */
internal actual fun resizePointerIcon(orientation: Orientation): PointerIcon {
    val pointerIconType = if (orientation == Orientation.Horizontal) {
        AndroidPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW
    } else {
        AndroidPointerIcon.TYPE_VERTICAL_DOUBLE_ARROW
    }
    return PointerIcon(pointerIconType)
}
