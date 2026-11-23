package io.github.majorbolt.dockinggrid.compose

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.input.pointer.PointerIcon

/**
 * iPadOS has no resize pointer shapes in Compose Multiplatform; the platform convention is the default
 * pointer, never a hand.
 */
internal actual fun resizePointerIcon(orientation: Orientation): PointerIcon = PointerIcon.Default
