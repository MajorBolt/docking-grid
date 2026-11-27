# Changelog

All notable changes to Docking Grid are documented here. The project follows [Semantic Versioning](https://semver.org).

## Unreleased

- `docking-grid-compose`: `DockDragController` and `rememberDockDragController()` let code outside a `DockHost`
  (for example an application toolbar) drag a dockable over it, with the same preview, highlight, commit and
  cancellation as a header drag. `DockHost` and `MaterialDockHost` take an optional `dragController`.
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
