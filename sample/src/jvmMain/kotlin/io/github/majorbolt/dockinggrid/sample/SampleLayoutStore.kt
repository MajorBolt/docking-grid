package io.github.majorbolt.dockinggrid.sample

import io.github.majorbolt.dockinggrid.DockableCatalog
import io.github.majorbolt.dockinggrid.WindowDockState
import io.github.majorbolt.dockinggrid.persistence.DockStateCodec
import io.github.majorbolt.dockinggrid.persistence.DockStateDecodeResult
import java.io.File
import java.io.IOException

/**
 * Keeps the sample's layout in a JSON file. The library only converts the state to and from text
 * ([DockStateCodec]); where it is stored is the application's choice.
 */
internal class SampleLayoutStore(
    private val file: File,
    catalog: DockableCatalog,
) {
    private val codec = DockStateCodec(catalog)

    /** The stored state, or `null` when there is none or it cannot be used; the caller then uses the defaults. */
    fun load(): WindowDockState? {
        if (!file.isFile) return null
        val document = try {
            file.readText()
        } catch (exception: IOException) {
            System.err.println("Cannot read the layout from $file: ${exception.message}")
            return null
        }
        return when (val decodeResult = codec.decode(document)) {
            is DockStateDecodeResult.Decoded -> decodeResult.state
            is DockStateDecodeResult.OutdatedSchema -> null
            is DockStateDecodeResult.Unreadable -> {
                System.err.println("The layout in $file is unreadable and is replaced on the next change: ${decodeResult.cause.message}")
                null
            }
        }
    }

    /** Writes [state]; blocking, so call it off the UI thread. */
    fun save(state: WindowDockState) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(codec.encode(state))
        } catch (exception: IOException) {
            System.err.println("Cannot save the layout to $file: ${exception.message}")
        }
    }

    companion object {
        fun inUserHome(catalog: DockableCatalog): SampleLayoutStore =
            SampleLayoutStore(File(System.getProperty("user.home"), ".docking-grid-sample/layout.json"), catalog)
    }
}
