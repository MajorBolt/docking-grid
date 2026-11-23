package io.github.majorbolt.dockinggrid.compose

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

internal actual fun resizePointerIcon(orientation: Orientation): PointerIcon {
    val cursorType = if (orientation == Orientation.Horizontal) {
        Cursor.E_RESIZE_CURSOR
    } else {
        Cursor.S_RESIZE_CURSOR
    }
    return PointerIcon(Cursor.getPredefinedCursor(cursorType))
}
