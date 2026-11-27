# Changelog

All notable changes to Docking Grid are documented here. The project follows [Semantic Versioning](https://semver.org).

## Unreleased

- Center grid: the center can hold one panel, two side by side or stacked, three (one full-length) or a 2×2 grid,
  each panel a tab group. **Breaking:** `DockLayout.centerGroup` is replaced by `DockLayout.center: CenterGrid`
  (`CenterAxis`, `CenterSection`, `CenterPanel`; `CenterGrid.single(group)` for one panel).
  - Dropping on a side triangle of a center panel splits it (`DropTarget.SplitCenterPanel`,
    `DockLayoutOperation.SplitCenterPanel`); where that would exceed 2×2, the triangle creates an inner dock as before.
  - `DockLayoutOperation.ArrangeCenter(CenterPreset)` rearranges the center in one of eight presets and keeps every
    dockable; panels the preset adds stay empty drop targets (`DropTarget.EmptyCenterPanel`,
    `DockLayoutOperation.MoveToCenterPanel`). A panel that loses its last dockable is removed.
  - Center dividers resize the splits (`DividerTarget.CenterSplit`, `DockLayoutOperation.ResizeCenterSplit`).
    `DockingMetrics.centerSplitsResizable = false` shows every split evenly and makes the dividers drawn only
    (`DockDivider.isResizable`).
  - `DockingCapabilities.isCenterGridEnabled` (default `true`) switches the grid off; `reconciled` then merges a
    stored grid into one tab group.
  - `docking-grid-compose`: `DockGroupScope.centerPreset` and `arrangeCenter`, `DockingStrings.emptyCenterPanel`,
    `centerLayout` and `centerPresetTitles`, test tags for empty panels and dividers.
  - `docking-grid-material3`: the options menu of a center panel offers the presets
    (`MaterialCenterLayoutMenuSection`).
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
