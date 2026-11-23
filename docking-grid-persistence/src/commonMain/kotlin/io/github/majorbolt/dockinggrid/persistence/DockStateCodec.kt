package io.github.majorbolt.dockinggrid.persistence

import io.github.majorbolt.dockinggrid.DockableCatalog
import io.github.majorbolt.dockinggrid.WindowDockState
import kotlinx.serialization.json.Json

/**
 * Converts a [WindowDockState] to and from a versioned JSON document. Where the document is stored is up to the
 * host application.
 *
 * Compatibility rules:
 * - Unknown fields are ignored, so documents written by a newer version can be read as far as they are understood.
 * - Identifiers that are not in [catalog] are dropped while decoding. Dockables that the document lists neither as
 *   open nor as closed (for example ones added in a newer app version) count as open.
 * - A document with an older [SCHEMA_VERSION] is not migrated; it is reported as [DockStateDecodeResult.OutdatedSchema].
 *   The version is read before the content, so this holds even when the old content no longer matches the format.
 *
 * A decoded state must still be repaired with [io.github.majorbolt.dockinggrid.reconciled] before use.
 */
public class DockStateCodec(private val catalog: DockableCatalog) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    public fun encode(state: WindowDockState): String =
        json.encodeToString(WindowDockStateDto.serializer(), state.toDto(catalog))

    public fun decode(document: String): DockStateDecodeResult {
        // SerializationException is an IllegalArgumentException.
        val storedSchemaVersion = try {
            // The version is checked before the content: an older document may no longer match the current DTOs.
            json.decodeFromString(DockStateVersionDto.serializer(), document).schemaVersion
        } catch (exception: IllegalArgumentException) {
            return DockStateDecodeResult.Unreadable(exception)
        }
        if (storedSchemaVersion < SCHEMA_VERSION) return DockStateDecodeResult.OutdatedSchema(storedSchemaVersion)
        val stateDto = try {
            json.decodeFromString(WindowDockStateDto.serializer(), document)
        } catch (exception: IllegalArgumentException) {
            return DockStateDecodeResult.Unreadable(exception)
        }
        return DockStateDecodeResult.Decoded(stateDto.toDomain(catalog), stateDto.schemaVersion)
    }

    public companion object {
        /** Version written by [encode]. */
        public const val SCHEMA_VERSION: Int = DOCK_STATE_SCHEMA_VERSION
    }
}

public sealed interface DockStateDecodeResult {

    /**
     * @property storedSchemaVersion version the document was written with; greater than
     * [DockStateCodec.SCHEMA_VERSION] when a newer version wrote it.
     */
    public data class Decoded(val state: WindowDockState, val storedSchemaVersion: Int) : DockStateDecodeResult {
        public val isNewerSchema: Boolean get() = storedSchemaVersion > DockStateCodec.SCHEMA_VERSION
    }

    /** The document is older than [DockStateCodec.SCHEMA_VERSION] and is not migrated; use the defaults. */
    public data class OutdatedSchema(val storedSchemaVersion: Int) : DockStateDecodeResult

    /** The document is not valid JSON or does not match the format. */
    public data class Unreadable(val cause: IllegalArgumentException) : DockStateDecodeResult
}
