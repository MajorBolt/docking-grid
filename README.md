# Docking Grid

A docking layout for Compose Multiplatform: tool panes ("dockables") in docks on the four sides and in the center,
with tab groups, nested docks, a live preview while dragging, resizable dividers and a persistable layout.
It is built for desktop applications (Windows, Linux, macOS) and also runs on Android and iOS.

The library manages the layout only. What a dockable shows, how dockables are opened and closed (menus, toolbars,
shortcuts) and where the layout is stored are up to the application.

## Modules

| Artifact | Contents | Depends on |
|---|---|---|
| `docking-grid-core` | Layout model, operations, geometry, drop-target resolution and resizing. No UI. | Kotlin standard library |
| `docking-grid-persistence` | `DockStateCodec`: the layout as a versioned JSON document. | `docking-grid-core`, kotlinx-serialization |
| `docking-grid-compose` | `DockHost` with a style and slot API, using Compose foundation only. | `docking-grid-core`, Compose foundation |
| `docking-grid-material3` | Material 3 defaults and the ready-made `MaterialDockHost`. | `docking-grid-compose`, Material 3 |

Targets: Android, JVM (desktop), iosArm64 and iosSimulatorArm64.

## Setup

The library is not on a public repository yet. Two options:

**From source**, as a Gradle composite build. In the application's `settings.gradle.kts`:

```kotlin
includeBuild("path/to/docking-grid")
```

The included build uses its own versions from `gradle/libs.versions.toml`. Keep Kotlin, the Android Gradle plugin
and Compose Multiplatform there at the versions of the including build.

**From Maven Local**, after `./gradlew publishToMavenLocal` in this repository, with `mavenLocal()` in the repositories.

Either way, depend on the coordinates; with the composite build, Gradle substitutes the local modules:

```kotlin
commonMain.dependencies {
    implementation("io.github.majorbolt:docking-grid-material3:0.1.0-SNAPSHOT")
    implementation("io.github.majorbolt:docking-grid-persistence:0.1.0-SNAPSHOT") // only to store layouts
}
```

Building the library needs an Android SDK: set `ANDROID_HOME`, or put `sdk.dir=…` into `local.properties` in this
repository's root. Run the tests with `./gradlew jvmTest`.

## Usage

### 1. Describe the dockables

```kotlin
val catalog = DockableCatalog(
    listOf(
        Dockable(DockableId("project"), "Project", minimumWidthDp = 200, minimumHeightDp = 160,
            defaultLocation = DockLocation.Side(DockFrameLevel.OUTER, DockSide.LEFT)),
        Dockable(DockableId("editor"), "Editor", 320, 200, DockLocation.Center),
    ),
)
```

The identifiers are stored in saved layouts, so keep them stable between versions.

### 2. Own the state and show it

The application owns a `WindowDockState`. The host never changes it: it reports each finished drag, drop or resize as
one `DockLayoutOperation`, and the application applies it.

```kotlin
var dockState by remember { mutableStateOf(defaultWindowDockState(catalog, DockingCapabilities.Desktop)) }
val orientation = LayoutOrientation.LANDSCAPE

MaterialDockHost(
    profile = dockState.profile(orientation),
    catalog = catalog,
    capabilities = DockingCapabilities.Desktop,
    orientation = orientation,
    onApplyOperation = { operation ->
        val result = dockState.applyingOperation(operation, orientation, DockingCapabilities.Desktop)
        if (result is DockStateTransitionResult.Applied) dockState = result.state
    },
    onCloseDockable = { dockableId -> dockState = dockState.closingDockable(dockableId) },
) { dockableId ->
    // The content of one dockable. Its state survives moving it.
}
```

There is one layout per orientation (`LANDSCAPE`, `PORTRAIT`); the set of open dockables is shared.

### 3. Open and close dockables from your own UI

The library has no toolbar or menu. Connect your own UI to the state:

```kotlin
dockState = dockState.togglingDockable(dockableId, catalog, DockingCapabilities.Desktop) // or openingDockable
val isOpen = dockableId in dockState.openDockableIds
```

A reopened dockable returns to where it was closed, if that place still exists; otherwise to its default location.

### Drag from your own UI

To let users drag a dockable from outside the host, for example from a toolbar icon, pass a `DockDragController` to
the host and drive it from your own pointer gesture. Positions are window coordinates in pixels. The host shows the
same live preview and highlight as for a header drag, and a drop emits one operation. The dockable may be closed;
commit drops with `applyingDropOperation`, which also opens a closed dockable at the drop target:

```kotlin
val dragController = rememberDockDragController()

MaterialDockHost(
    // ...
    onApplyOperation = { operation ->
        val result = dockState.applyingDropOperation(operation, orientation, catalog, DockingCapabilities.Desktop)
        if (result is DockStateTransitionResult.Applied) dockState = result.state
    },
    dragController = dragController,
) { dockableId -> /* ... */ }

// In your gesture, with positions from LayoutCoordinates.localToWindow:
dragController.startDrag(dockableId, windowPosition)
dragController.updateDrag(windowPosition)
val dropped: Boolean = dragController.drop()   // or dragController.cancelDrag()
```

Escape, loss of window focus and a change of the committed profile end the drag in the host;
`dragController.isDragging` then turns `false`. `isOverDropTarget` tells whether a drop would change the layout.

### 4. Store the layout

```kotlin
val codec = DockStateCodec(catalog)
val document: String = codec.encode(dockState)            // write it wherever you like

val restored = when (val result = codec.decode(document)) {
    is DockStateDecodeResult.Decoded -> result.state
    is DockStateDecodeResult.OutdatedSchema, is DockStateDecodeResult.Unreadable -> null
} ?: defaultWindowDockState(catalog, capabilities)
dockState = restored.reconciled(catalog, capabilities)    // always reconcile what you load
```

`reconciled` repairs a loaded state: it drops identifiers that are not in the catalog and places open dockables
that are missing. Dockables added in a newer app version open at their default location.

## Customizing

### Style

`DockingStyle` holds the colors (drop-target highlight, dividers, groups, headers, placeholders, drag label), the
shapes and spacing, and the texts. `MaterialDockingDefaults.style()` derives one from the Material theme:

```kotlin
style = MaterialDockingDefaults.style(
    colors = MaterialDockingDefaults.colors().copy(dropTargetFill = accent.copy(alpha = 0.2f), dropTargetBorder = accent),
    strings = DockingStrings(emptyCenter = "Drag a panel here", close = "Close panel"),
)
```

Sizes that the layout calculation depends on, such as the divider thickness, are part of `DockingMetrics`.

### Slots

`DockHost` (in `docking-grid-compose`) takes slots for the `tabStrip`, the `header`, the `divider`, the `placeholder`
and the `dragLabel`. The defaults use Compose foundation only; `docking-grid-material3` provides Material versions
(`MaterialDockTabStrip`, `MaterialDockHeader`, …), which `MaterialDockHost` combines.

A group is always laid out as: tab strip (only with more than one dockable), header row, content. Drop positions
are calculated from this structure, so custom chrome must keep it:

- Mark every tab with `Modifier.dockTab(dockableId)` from `DockGroupScope`. It starts dragging from the tab and
  measures it, so dropped tabs are inserted where the pointer is.
- The header row needs no marker; the host makes the whole row the drag handle of the selected dockable.
- The divider slot only draws. The host owns the gesture and the resize cursor and passes a `DockDividerState`
  with the orientation and whether it is being dragged.

```kotlin
DockHost(
    /* … */
    tabStrip = {
        Row {
            group.dockableIds.forEach { dockableId ->
                Text(titleOf(dockableId), Modifier.dockTab(dockableId).clickable { select(dockableId) })
            }
        }
    },
    divider = { state -> Box(Modifier.fillMaxSize().background(if (state.isDragged) accent else lineColor)) },
) { dockableId -> /* content */ }
```

### Center grid

The center is a grid of up to 2×2 panels, each a tab group: one panel, two side by side or stacked, three with one
full-length panel, or four. Dropping a dockable on a side of a center panel splits it; a panel that loses its last
dockable disappears. Dropping in the middle of a center panel swaps the two dockables; dropping on its header adds
a tab. `DockLayoutOperation.ArrangeCenter(CenterPreset.GRID_2X2)` (and the other presets) rearranges the center
without closing anything; panels it adds stay empty until something is dropped in. In Material chrome, the header of
a center panel shows the current arrangement as a button that opens the presets, and its options menu can lock and
reset the dividers.

```kotlin
onApplyOperation(DockLayoutOperation.ArrangeCenter(CenterPreset.GRID_2X2))   // for example from your own menu
MaterialDockHost(
    /* … */,
    metrics = DockingMetrics(centerSplitsResizable = dividersUnlocked),      // false: the dividers stay where they are
    onCenterSplitsResizableChange = { unlocked -> dividersUnlocked = unlocked },  // offers "Lock dividers"
)
```

Set `DockingCapabilities(isCenterGridEnabled = false)` to keep a single center group.

### Capabilities

`DockingCapabilities` limits the structure, for example for a tablet: whether docks may be created inside the
center, how many lanes per side and how many groups per lane. Only `DockingCapabilities.Desktop` (no limits) is
built in; define other profiles in your application.

## Sample

`./gradlew :sample:run` opens a desktop window with five dummy dockables, a custom divider and highlight
color, a Window menu that opens and closes them, a Center menu with the center presets and a switch for resizable
center splits, and a layout stored in `~/.docking-grid-sample/layout.json`.

## Platform notes

- Desktop is the primary target. Resize cursors use the platform cursors on desktop and Android (mouse); iPadOS has
  no resize pointer in Compose Multiplatform, so the default pointer is shown.
- The iOS libraries compile on any host, but iOS apps and the iOS tests need macOS.

## License

Copyright 2026 Marian Kondziella

Licensed under the Apache License, Version 2.0; see [LICENSE](LICENSE).
