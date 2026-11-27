# Changelog

All notable changes to Docking Grid are documented here. The project follows [Semantic Versioning](https://semver.org).

## Unreleased

- Header styles. **Breaking:** the center-grid members of `DockGroupScope` from the previous entry (lock, reset)
  moved to the new `CenterGridScope`.
  - The center grid has its own header while it holds two or more panels (`DockHost(centerHeader = ...)`,
    `MaterialCenterHeader`, `DockingMetrics.centerHeaderHeightDp`, `DockGeometry.centerHeaderRect`): the arrangement
    button and the options Lock dividers, Reset dividers and Auto-hide header. A single panel keeps the arrangement
    button in its own header.
  - `TabGroup.headerStyle: GroupHeaderStyle`: a vertical header (a strip on the group's outer side, see
    `verticalHeaderSide`, with one icon tab per dockable: `DockHost(verticalHeader = ..., dockableIcon = ...)`,
    `MaterialVerticalDockHeader`) and an auto-hidden header that slides in over the content on hover. Both are
    offered in the group's options menu (`MaterialDockOptionsButton`) and stored with the group
    (`DockLayoutOperation.SetGroupHeaderStyle`).
  - `CenterGrid.autoHidesHeader` (`DockLayoutOperation.SetCenterHeaderAutoHide`); with
    `DockingMetrics.centerHeaderHidesPanelHeaders` (default `true`) it also hides the headers of all panels
    (`isHeaderAutoHidden`, `isHeaderAutoHideForcedByGrid`).
  - While a dockable is dragged, auto-hidden group headers are shown, so tabs can still be dropped on them.
  - `docking-grid-persistence`: the styles are stored with defaults, so existing schema-4 documents still read.
- Center grid: the center can hold one panel, two side by side or stacked, three (one full-length) or a 2×2 grid,
  each panel a tab group. **Breaking:** `DockLayout.centerGroup` is replaced by `DockLayout.center: CenterGrid`
  (`CenterAxis`, `CenterSection`, `CenterPanel`; `CenterGrid.single(group)` for one panel).
  - Dropping near a side of a center panel (`DockingMetrics.centerPanelEdgeShare`) splits it
    (`DropTarget.SplitCenterPanel`, `DockLayoutOperation.SplitCenterPanel`). Where that would exceed 2×2, an outer
    edge of the grid creates an inner dock and an inner edge swaps. The middle of a center panel swaps the dragged
    dockable with the panel's shown one (`DropTarget.Swap`, `DockLayoutOperation.SwapDockables`); tabs are added
    through the header.
  - `DockLayoutOperation.ArrangeCenter(CenterPreset)` rearranges the center in one of eight presets and keeps every
    dockable; panels the preset adds stay empty drop targets (`DropTarget.EmptyCenterPanel`,
    `DockLayoutOperation.MoveToCenterPanel`). A panel that loses its last dockable is removed.
  - Center dividers resize the splits (`DividerTarget.CenterSplit`, `DockLayoutOperation.ResizeCenterSplit`);
    `DockLayoutOperation.ResetCenterSplits` makes them even. `DockingMetrics.centerSplitsResizable = false` locks
    the dividers where they are (`DockDivider.isResizable`).
  - `DockingCapabilities.isCenterGridEnabled` (default `true`) switches the grid off; `reconciled` then merges a
    stored grid into one tab group.
  - `docking-grid-compose`: `DockGroupScope.centerPreset`, `arrangeCenter`, `centerSplitsResizable`,
    `setCenterSplitsResizable`, `hasUnevenCenterSplits` and `resetCenterSplits`; `DockHost(onCenterSplitsResizableChange)`;
    `DockingStrings.emptyCenterPanel`, `centerLayout`, `centerPresetTitles`, `lockCenterDividers` and
    `resetCenterDividers`; test tags for empty panels and dividers.
  - `docking-grid-material3`: the header of a center panel shows the current arrangement as a button that opens the
    presets (`MaterialCenterLayoutButton`, `CenterPresetIcon`); its options menu has "Lock dividers" and "Reset
    dividers". `MaterialDockMenuItem(isChecked = ...)` draws a check mark.
  - `docking-grid-persistence`: schema 4 stores the grid. Documents of schema 3 are reported as `OutdatedSchema`.
- `docking-grid-compose`: `DockDragController` and `rememberDockDragController()` let code outside a `DockHost`
  (for example an application toolbar) drag a dockable over it, with the same preview, highlight, commit and
  cancellation as a header drag. `DockHost` and `MaterialDockHost` take an optional `dragController`.
- `docking-grid-material3`: the options menu of a group has compact, inset entries with a rounded primary-colored
  highlight (`MaterialDockMenuItem`), and
  `MaterialDockHeader(menuItems = ...)` / `MaterialDockHost(dockableMenuItems = ...)` add own entries below "Close".
- `docking-grid-core`: `WindowDockState.applyingDropOperation` commits a drop and opens a dockable that was closed
  at the drop target; the other orientations place it at its remembered or default position.

## 0.1.0 (unreleased)

First version, extracted from the docking system of an existing Compose Multiplatform application.

- `docking-grid-core`: two-level nested frame (outer frame, inner frame, center) with lanes on all four sides, tab
  groups, placement memory for reopening, per-orientation layouts, capability limits, geometry, drop-target
  resolution and resizing.
- `docking-grid-persistence`: `DockStateCodec`, a versioned JSON format (schema 3) that drops unknown identifiers
  and opens dockables that are new since the document was written.
- `docking-grid-compose`: `DockHost` with a live drag preview, target-only highlighting, Escape and focus-loss
  cancellation, divider resizing with platform resize cursors, and a style and slot API with foundation-only
  defaults.
- `docking-grid-material3`: `MaterialDockingDefaults` and `MaterialDockHost`.
- Targets: Android, JVM desktop, iosArm64, iosSimulatorArm64. Explicit API mode.
- Published locally with `maven-publish` under `io.github.majorbolt`; Apache License 2.0.
- Built with Android Gradle Plugin 9.5.2 and Gradle 9.9.1.
