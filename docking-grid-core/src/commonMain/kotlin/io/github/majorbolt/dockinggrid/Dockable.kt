package io.github.majorbolt.dockinggrid

import kotlin.jvm.JvmInline

/**
 * Stable identifier of a piece of content that can be docked. It is stored in persisted layouts,
 * so it must not change between app versions.
 */
@JvmInline
public value class DockableId(public val value: String) {
    override fun toString(): String = value
}

/**
 * Description of dockable content: everything the docking engine needs to know about it.
 * The content itself is rendered by the host application.
 *
 * @property minimumWidthDp smallest width the content may be shown with.
 * @property minimumHeightDp smallest height the content may be shown with.
 * @property defaultLocation where the content opens when the user has never placed it.
 */
public data class Dockable(
    val id: DockableId,
    val title: String,
    val minimumWidthDp: Int,
    val minimumHeightDp: Int,
    val defaultLocation: DockLocation,
) {
    init {
        require(minimumWidthDp >= 0 && minimumHeightDp >= 0) { "Minimum sizes of $id must not be negative." }
    }
}

/**
 * All dockables known to the application, in their definition order. The order is used wherever the
 * engine has to process several dockables deterministically, for example when building the default layout.
 *
 * Identifiers that are not in the catalog (for example from a persisted layout of an older app version)
 * are removed by [reconciled].
 */
public data class DockableCatalog(val dockables: List<Dockable>) {

    private val dockablesById: Map<DockableId, Dockable> = dockables.associateBy { dockable -> dockable.id }

    init {
        require(dockablesById.size == dockables.size) { "Dockable identifiers must be unique." }
    }

    val ids: List<DockableId> get() = dockables.map { dockable -> dockable.id }

    public fun find(id: DockableId): Dockable? = dockablesById[id]

    public operator fun contains(id: DockableId): Boolean = id in dockablesById

    /** Minimum width of [id]; unknown identifiers impose no minimum. */
    public fun minimumWidthDp(id: DockableId): Int = dockablesById[id]?.minimumWidthDp ?: 0

    /** Minimum height of [id]; unknown identifiers impose no minimum. */
    public fun minimumHeightDp(id: DockableId): Int = dockablesById[id]?.minimumHeightDp ?: 0

    /** A tab group needs the largest minimum width of its dockables, because any of them may be selected. */
    public fun minimumWidthDp(group: TabGroup): Int = group.dockableIds.maxOfOrNull(::minimumWidthDp) ?: 0

    /** A tab group needs the largest minimum height of its dockables, because any of them may be selected. */
    public fun minimumHeightDp(group: TabGroup): Int = group.dockableIds.maxOfOrNull(::minimumHeightDp) ?: 0
}
