package io.github.majorbolt.dockinggrid

/** Smallest and largest share of a center split that the model accepts; real minimum sizes come from the geometry. */
internal const val MINIMUM_CENTER_SPLIT_FRACTION = 0.01f
internal const val MAXIMUM_CENTER_SPLIT_FRACTION = 0.99f
internal const val DEFAULT_CENTER_SPLIT_FRACTION = 0.5f

internal const val PANEL_ID_PREFIX = "panel"

/**
 * Whether the panel [panelId] of this grid can be split at [side]: along the grid's axis only while the grid has a
 * single section, across it only while the panel's section has a single panel. That keeps the grid at most 2×2.
 */
public fun CenterGrid.canSplitPanel(panelId: String, side: DockSide): Boolean {
    val section = sections.firstOrNull { section -> section.panels.any { panel -> panel.id == panelId } } ?: return false
    return if (CenterAxis.of(side) == axis) sections.size < 2 else section.panels.size < 2
}

/** The preset this grid is arranged as, or `null` for an empty center. */
public val CenterGrid.matchingPreset: CenterPreset?
    get() {
        val panelCounts = sections.map { section -> section.panels.size }
        return when {
            panelCounts.isEmpty() -> null
            panelCounts == listOf(1) -> CenterPreset.SINGLE
            panelCounts == listOf(1, 1) -> if (axis == CenterAxis.HORIZONTAL) CenterPreset.SIDE_BY_SIDE else CenterPreset.STACKED
            panelCounts == listOf(1, 2) -> if (axis == CenterAxis.HORIZONTAL) CenterPreset.THREE_FULL_LEFT else CenterPreset.THREE_FULL_TOP
            panelCounts == listOf(2, 1) -> if (axis == CenterAxis.HORIZONTAL) CenterPreset.THREE_FULL_RIGHT else CenterPreset.THREE_FULL_BOTTOM
            else -> CenterPreset.GRID_2X2
        }
    }

/** Builds the arrangement of this preset from [panels], given in reading order; [panels] must hold [CenterPreset.panelCount] entries. */
internal fun CenterPreset.arrange(panels: List<CenterPanel>): CenterGrid {
    fun sectionOf(vararg panelIndices: Int) = CenterSection(panels = panelIndices.map { panelIndex -> panels[panelIndex] })
    return when (this) {
        CenterPreset.SINGLE -> CenterGrid(CenterAxis.HORIZONTAL, listOf(sectionOf(0)))
        CenterPreset.SIDE_BY_SIDE -> CenterGrid(CenterAxis.HORIZONTAL, listOf(sectionOf(0), sectionOf(1)))
        CenterPreset.STACKED -> CenterGrid(CenterAxis.VERTICAL, listOf(sectionOf(0), sectionOf(1)))
        CenterPreset.THREE_FULL_LEFT -> CenterGrid(CenterAxis.HORIZONTAL, listOf(sectionOf(0), sectionOf(1, 2)))
        CenterPreset.THREE_FULL_RIGHT -> CenterGrid(CenterAxis.HORIZONTAL, listOf(sectionOf(0, 2), sectionOf(1)))
        CenterPreset.THREE_FULL_TOP -> CenterGrid(CenterAxis.VERTICAL, listOf(sectionOf(0), sectionOf(1, 2)))
        CenterPreset.THREE_FULL_BOTTOM -> CenterGrid(CenterAxis.VERTICAL, listOf(sectionOf(0, 1), sectionOf(2)))
        CenterPreset.GRID_2X2 -> CenterGrid(CenterAxis.HORIZONTAL, listOf(sectionOf(0, 2), sectionOf(1, 3)))
    }
}

/** Builds the arrangement of this preset from [panels] (see above), keeping the grid header's [autoHidesHeader]. */
internal fun CenterPreset.arrange(
    panels: List<CenterPanel>,
    autoHidesHeader: Boolean,
    rememberedHeaderStyles: Map<Int, GroupHeaderStyle> = emptyMap(),
): CenterGrid = arrange(panels).copy(autoHidesHeader = autoHidesHeader, rememberedHeaderStyles = rememberedHeaderStyles)

internal fun DockLayout.splitCenterPanel(
    operation: DockLayoutOperation.SplitCenterPanel,
    capabilities: DockingCapabilities,
    idGenerator: DockIdGenerator,
): DockOperationResult {
    if (!capabilities.isCenterGridEnabled) return DockOperationResult.Rejected(DockRejectionReason.CENTER_LIMIT_REACHED)
    val layoutWithoutDockable = removingDockable(operation.dockableId)
    val grid = layoutWithoutDockable.center
    val sectionIndex = grid.sections.indexOfFirst { section -> section.panels.any { panel -> panel.id == operation.panelId } }
    if (sectionIndex < 0) return DockOperationResult.Rejected(DockRejectionReason.UNKNOWN_TARGET)
    if (!grid.canSplitPanel(operation.panelId, operation.side)) {
        return DockOperationResult.Rejected(DockRejectionReason.CENTER_LIMIT_REACHED)
    }
    val newPanel = CenterPanel(
        id = idGenerator.nextId(PANEL_ID_PREFIX),
        group = TabGroup(id = idGenerator.nextId(GROUP_ID_PREFIX), dockableIds = listOf(operation.dockableId)),
    )
    val insertsBefore = operation.side == DockSide.LEFT || operation.side == DockSide.TOP
    val section = grid.sections[sectionIndex]
    val updatedGrid = if (CenterAxis.of(operation.side) == grid.axis) {
        val insertionIndex = if (insertsBefore) sectionIndex else sectionIndex + 1
        grid.copy(
            sections = grid.sections.toMutableList().apply { add(insertionIndex, CenterSection(panels = listOf(newPanel))) },
            splitFraction = DEFAULT_CENTER_SPLIT_FRACTION,
        )
    } else {
        val panelIndex = section.panels.indexOfFirst { panel -> panel.id == operation.panelId }
        val updatedPanels = section.panels.toMutableList().apply { add(if (insertsBefore) panelIndex else panelIndex + 1, newPanel) }
        grid.copy(
            sections = grid.sections.toMutableList().apply {
                set(sectionIndex, section.copy(panels = updatedPanels, splitFraction = DEFAULT_CENTER_SPLIT_FRACTION))
            },
        )
    }
    return DockOperationResult.Applied(layoutWithoutDockable.copy(center = updatedGrid).normalized())
}

internal fun DockLayout.moveToCenterPanel(
    operation: DockLayoutOperation.MoveToCenterPanel,
    idGenerator: DockIdGenerator,
): DockOperationResult {
    val layoutWithoutDockable = removingDockable(operation.dockableId)
    val panel = layoutWithoutDockable.center.findPanel(operation.panelId)
        ?: return DockOperationResult.Rejected(DockRejectionReason.UNKNOWN_TARGET)
    val existingGroup = panel.group
    if (existingGroup != null) {
        val updatedLayout = layoutWithoutDockable.mapGroups { group ->
            if (group.id != existingGroup.id) return@mapGroups group
            group.copy(dockableIds = group.dockableIds + operation.dockableId, selectedDockableId = operation.dockableId)
        }
        return DockOperationResult.Applied(updatedLayout.normalized())
    }
    val newGroup = TabGroup(
        id = idGenerator.nextId(GROUP_ID_PREFIX),
        dockableIds = listOf(operation.dockableId),
        headerStyle = panel.headerStyle ?: GroupHeaderStyle(),
    )
    val updatedCenter = layoutWithoutDockable.center.mapPanels { centerPanel ->
        if (centerPanel.id == operation.panelId) centerPanel.copy(group = newGroup, headerStyle = null) else centerPanel
    }
    return DockOperationResult.Applied(layoutWithoutDockable.copy(center = updatedCenter).normalized())
}

/**
 * Rearranges the center as [DockLayoutOperation.ArrangeCenter.preset]. The existing groups keep their order (reading
 * order) and fill the new panels; groups beyond the preset's panel count become tabs of its last panel; remaining
 * panels are empty. Existing panel identifiers are reused in reading order. The header style of every position is
 * remembered ([CenterGrid.rememberedHeaderStyles]); an empty panel gets the style remembered for its position.
 */
internal fun DockLayout.arrangeCenter(
    operation: DockLayoutOperation.ArrangeCenter,
    capabilities: DockingCapabilities,
    idGenerator: DockIdGenerator,
): DockOperationResult {
    if (!capabilities.isCenterGridEnabled && operation.preset != CenterPreset.SINGLE) {
        return DockOperationResult.Rejected(DockRejectionReason.CENTER_LIMIT_REACHED)
    }
    val existingPanels = center.panelsInReadingOrder
    val existingGroups = existingPanels.mapNotNull { panel -> panel.group }
    val panelCount = operation.preset.panelCount
    val assignedGroups = (0 until panelCount).map { panelIndex -> existingGroups.getOrNull(panelIndex) }.toMutableList()
    val surplusGroups = existingGroups.drop(panelCount)
    if (surplusGroups.isNotEmpty()) {
        val lastGroup = assignedGroups.last() ?: surplusGroups.first()
        assignedGroups[panelCount - 1] = lastGroup.copy(dockableIds = lastGroup.dockableIds + surplusGroups.flatMap { group -> group.dockableIds })
    }
    val stylesByPosition = center.rememberedHeaderStyles.toMutableMap()
    existingPanels.forEachIndexed { position, panel ->
        val positionStyle = panel.group?.headerStyle ?: panel.headerStyle
        if (positionStyle == null || positionStyle == GroupHeaderStyle()) stylesByPosition.remove(position) else stylesByPosition[position] = positionStyle
    }
    val panels = (0 until panelCount).map { panelIndex ->
        val assignedGroup = assignedGroups[panelIndex]
        CenterPanel(
            id = existingPanels.getOrNull(panelIndex)?.id ?: idGenerator.nextId(PANEL_ID_PREFIX),
            group = assignedGroup,
            headerStyle = if (assignedGroup == null) stylesByPosition[panelIndex] else null,
        )
    }
    return DockOperationResult.Applied(
        copy(center = operation.preset.arrange(panels, center.autoHidesHeader, stylesByPosition)).normalized(),
    )
}

/** This grid with every split even. */
internal fun CenterGrid.withEvenSplits(): CenterGrid = copy(
    splitFraction = DEFAULT_CENTER_SPLIT_FRACTION,
    sections = sections.map { section -> section.copy(splitFraction = DEFAULT_CENTER_SPLIT_FRACTION) },
)

/** `true` when a split of this grid is not even, so [DockLayoutOperation.ResetCenterSplits] would change it. */
public val CenterGrid.hasUnevenSplits: Boolean
    get() = this != withEvenSplits()

internal fun DockLayout.swapDockables(operation: DockLayoutOperation.SwapDockables): DockOperationResult {
    val dockableId = operation.dockableId
    val targetDockableId = operation.targetDockableId
    if (dockableId == targetDockableId || !contains(dockableId) || !contains(targetDockableId)) {
        return DockOperationResult.Rejected(DockRejectionReason.UNKNOWN_DOCKABLE)
    }
    fun DockableId.swapped(): DockableId = when (this) {
        dockableId -> targetDockableId
        targetDockableId -> dockableId
        else -> this
    }
    return DockOperationResult.Applied(
        mapGroups { group ->
            group.copy(dockableIds = group.dockableIds.map { id -> id.swapped() }, selectedDockableId = group.selectedDockableId.swapped())
        }.normalized(),
    )
}

internal fun DockLayout.resizeCenterSplit(operation: DockLayoutOperation.ResizeCenterSplit): DockOperationResult {
    val fraction = operation.splitFraction
    if (!fraction.isFinite() || fraction <= 0f || fraction >= 1f) return DockOperationResult.Rejected(DockRejectionReason.INVALID_SIZE)
    val clampedFraction = fraction.coerceIn(MINIMUM_CENTER_SPLIT_FRACTION, MAXIMUM_CENTER_SPLIT_FRACTION)
    val sectionIndex = operation.sectionIndex
    val updatedCenter = if (sectionIndex == null) {
        if (center.sections.size < 2) return DockOperationResult.Rejected(DockRejectionReason.UNKNOWN_TARGET)
        center.copy(splitFraction = clampedFraction)
    } else {
        val section = center.sections.getOrNull(sectionIndex)
        if (section == null || section.panels.size < 2) return DockOperationResult.Rejected(DockRejectionReason.UNKNOWN_TARGET)
        center.copy(sections = center.sections.toMutableList().apply { set(sectionIndex, section.copy(splitFraction = clampedFraction)) })
    }
    return DockOperationResult.Applied(copy(center = updatedCenter))
}

/** Where [MoveToCenter][DockLayoutOperation.MoveToCenter] goes: the first empty panel, otherwise the first panel. */
internal val CenterGrid.defaultTargetPanel: CenterPanel?
    get() = panelsInReadingOrder.firstOrNull { panel -> panel.group == null } ?: panelsInReadingOrder.firstOrNull()

internal fun CenterGrid.mapPanels(transform: (CenterPanel) -> CenterPanel): CenterGrid =
    copy(sections = sections.map { section -> section.copy(panels = section.panels.map(transform)) })

/**
 * Establishes the grid invariants, with [normalizeGroup] resolving duplicates and selections:
 * - at most two sections of at most two panels, unique panel ids;
 * - a panel whose group lost all dockables is removed, empty sections are removed;
 * - a single section with two panels becomes two single-panel sections along the other axis, so every arrangement
 *   has exactly one representation;
 * - a center whose only panel is empty is the empty center;
 * - split fractions are within the accepted range, and 0.5 where there is nothing to split.
 */
internal fun CenterGrid.normalized(normalizeGroup: (TabGroup) -> TabGroup?): CenterGrid {
    val seenPanelIds = mutableSetOf<String>()
    // Empty sections and panels are dropped before the 2×2 limit applies, so they never displace a real one.
    val normalizedSections = sections.mapNotNull { section ->
        val panels = section.panels.mapNotNull { panel ->
            if (!seenPanelIds.add(panel.id)) return@mapNotNull null
            val group = panel.group ?: return@mapNotNull panel
            normalizeGroup(group)?.let { normalizedGroup -> panel.copy(group = normalizedGroup.copy(sizeFraction = 1f)) }
        }.take(2)
        if (panels.isEmpty()) return@mapNotNull null
        CenterSection(panels = panels, splitFraction = if (panels.size < 2) DEFAULT_CENTER_SPLIT_FRACTION else section.splitFraction.clampedSplitFraction())
    }.take(2)
    val onlySection = normalizedSections.singleOrNull()
    val normalizedGrid = when {
        normalizedSections.isEmpty() -> CenterGrid()
        onlySection != null && onlySection.panels.size == 2 -> CenterGrid(
            axis = axis.crossAxis,
            sections = onlySection.panels.map { panel -> CenterSection(panels = listOf(panel)) },
            splitFraction = onlySection.splitFraction,
        )
        onlySection != null && onlySection.panels.single().group == null -> CenterGrid()
        onlySection != null -> CenterGrid(axis = CenterAxis.HORIZONTAL, sections = normalizedSections)
        else -> CenterGrid(axis = axis, sections = normalizedSections, splitFraction = splitFraction.clampedSplitFraction())
    }
    // The grid header's choice and the remembered panel styles are kept while the grid shrinks and grows again.
    return normalizedGrid.copy(autoHidesHeader = autoHidesHeader, rememberedHeaderStyles = rememberedHeaderStyles)
}

private fun Float.clampedSplitFraction(): Float =
    if (isFinite()) coerceIn(MINIMUM_CENTER_SPLIT_FRACTION, MAXIMUM_CENTER_SPLIT_FRACTION) else DEFAULT_CENTER_SPLIT_FRACTION
