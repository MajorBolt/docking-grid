package io.github.majorbolt.dockinggrid.persistence

import kotlinx.serialization.Serializable

/**
 * Only the version of a stored dock state. It is read before the content, so that documents of an older schema are
 * recognized as outdated even when their content no longer matches the current DTOs.
 */
@Serializable
internal data class DockStateVersionDto(
    val schemaVersion: Int,
)
