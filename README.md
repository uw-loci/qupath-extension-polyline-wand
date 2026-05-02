# QuPath Polyline Wand and Brush

A QuPath extension that adds a brush/wand-style editor for **line** and **polyline** annotations. QuPath's built-in brush and wand operate on areas only; this extension adds the same kind of fluid editing to lines.

Default keyboard chord: **Shift+P**.

## Use cases

- Tracing the leading edge of a tumor along a long polyline, then needing to push a section outward to include tissue you originally passed through.
- Drawing a long polyline for a vein and overshooting at the endpoint with a jerky hand movement -- erase backwards from the endpoint instead of restarting.
- Smoothing out a noisy polyline.
- Cutting a polyline at a specific point to break it into two annotations.

## Three brush engines, one tool

The toolbar exposes a single **Polyline Wand** tool. Right-click the toolbar button to switch between three engines at runtime; each tackles the "push the line around" problem differently.

- **Direct vertex push** (default) -- per-frame brush displaces affected vertices with a configurable falloff (cosine / linear / gaussian). Local densification keeps sparse segments responsive. End-of-stroke runs a vertex compactor and self-intersection loop remover, so the line collapses cleanly when pushed over itself. Most reactive: the brush can start anywhere and pulls the line toward it whenever the line enters the brush footprint.
- **Area proxy + skeletonize** -- buffers the polyline to a thin area, brush stamps union/difference into that area via JTS `OverlayNGRobust`, and on release OpenCV `ximgproc.thinning` (Zhang-Suen) recovers a polyline. All heavy work runs on a daemon thread (`polyline-wand-area-proxy`) so the UI stays responsive even on long polylines. Best for bold reshapes and gap bridging.
- **Arc-length displacement field** -- locks an active arc-length window of 2x brush radius at press; per-frame work touches only K vertices in that window. Cosine kernel + perpendicular-velocity damping for the most tactile feel. Self-intersection guard refuses any per-vertex move that would create a local crossing.

## Other features

- **Local region editing.** At mouse-press, only the polyline section within ~3x brush radius of the cursor is editable. The head and tail are spliced back bit-exact at commit, so untouched segments never get re-shaped -- no collateral smoothing or skeletonization round-trip on parts of the line you didn't touch. Bounds work on long polylines too: the engine sees ~50 vertices instead of 10,000. Adjustable via the "Local region multiplier" preference (default 3.0; set to 0 to disable).
- **Scissors / cut-at-click mode.** Pick **Scissors (cut at click)** from the right-click **Mode** submenu. The toolbar icon swaps from paint-brush to scissors. A click on the selected polyline finds the closest point on it (vertex or interpolated along a segment), splits the polyline into two `PolylineROI` annotations at that point, removes the original, and selects the first half. Both new pieces inherit the original's path class, name, and color.
- **Zoom-aware brush.** With the default *Radius follows zoom* preference on, brush radius is interpreted in **screen pixels** so the on-screen size stays constant and zoom-out automatically makes the brush cover a larger region of the image (matching QuPath's built-in brush). Turn it off to lock the brush to image pixels instead.
- **Cursor matches felt effect.** The solid cursor circle is drawn at the radius where the cosine falloff still has significant strength (default 75% of the brush radius); a faint dashed outer ring shows the true maximum reach where the per-vertex weight tapers to zero. Erase-from-end and area-proxy use a hard radius and draw a single solid circle.
- **Auto endpoint erase.** If the stroke begins near an endpoint of a polyline, the brush switches to erase-from-end mode (the line shortens from that end). Hold Shift to override and edit at an endpoint normally.
- **LineROI promotion.** Editing a 2-point `LineROI` for the first time densifies it into a `PolylineROI` (default 32 vertices) so the engines have interior vertices to work with.
- **Throttled commits.** Mid-drag `setROI` calls are throttled to ~30 Hz with `isChanging=true`, so the undo stack stays clean (one entry per stroke).
- **Brush size.** Adjust via the right-click toolbar menu ("Set brush radius...") or the Preferences pane. The tool does not bind any scroll-wheel shortcut so all of QuPath's normal zoom/pan gestures stay untouched.

## Right-click toolbar menu

Right-click the toolbar button to bring up:

```
Engine >                    (radio: Direct vertex push / Area proxy + skeletonize / Displacement field)
Mode >                      (radio: Auto / Push / Smooth / Erase from end / Scissors)
Engine settings >           (engine-specific submenu, swaps based on the active engine)
Set brush radius...
Reset Polyline Wand preferences
```

The **Engine settings** submenu rebuilds itself based on the active engine and exposes only the controls relevant to that engine.

## Preferences

All preferences live under the **Polyline Wand** category in QuPath's Preferences pane, with engine-specific entries grouped under nested sub-categories:

- *Polyline Wand* -- engine choice, default mode, brush radius, radius-follows-zoom toggle, cursor effective scale, throttle, endpoint erase, line conversion, end-stroke simplify, cursor color, local region multiplier
- *Polyline Wand: Direct vertex push* -- falloff profile, radial bias, densify, max insertions, velocity damping
- *Polyline Wand: Area proxy* -- buffer width fraction, mask max dim, simplify tolerance, disconnection policy, anchor endpoints, close gaps before thinning, overlay alpha
- *Polyline Wand: Displacement field* -- kernel type, sigma fraction, displacement strength, velocity damping, densify divisor, Catmull-Rom densify, self-intersection guard

Reset everything from the right-click toolbar menu.

## Installation

1. Download the latest `qupath-extension-polyline-wand-X.Y.Z-all.jar` from [Releases](https://github.com/uw-loci/qupath-extension-polyline-wand/releases).
2. Drop it into your QuPath `extensions/` folder.
3. Restart QuPath.

Or wait for the `uw-loci/qupath-catalog-mikenelson` catalog to publish the new release (auto-dispatch fires on every GitHub release).

## Build from source

Requires JDK 21 (Gradle 8.12 cannot run on JDK 25). Standard QuPath extension build:

```
./gradlew shadowJar
```

Output: `build/libs/qupath-extension-polyline-wand-X.Y.Z-all.jar` (the `-all` jar bundles all dependencies).

If your default JDK is newer than 21:

```
./gradlew shadowJar -Dorg.gradle.java.home=/path/to/jdk21
```

## Architecture sketch

For contributors who want to extend or tune the engines:

- `PolylineWandExtension` -- entry, installs prefs, tool, key chord, mode-aware icon, context menu.
- `PolylineWandPathTool` + `PolylineWandEventHandler` -- mouse / scroll / key dispatch; LocalRegion extraction; commit pipeline.
- `LocalRegion` -- splits a polyline into immutable head, editable body, immutable tail; `splice()` puts them back together.
- `BrushEngine` interface (`engine/`) -- one stroke per engine instance; `beginStroke` / `applyDrag` / `endStroke` / `cancel` / `previewSnapshot`.
- `engine/direct/` -- direct vertex push engine + uniform grid spatial index + falloff kernel.
- `engine/proxy/` -- area proxy session + brush stamper + raster Zhang-Suen skeletonizer + skeleton tracer.
- `engine/field/` -- arc-length curve + active-range computation + displacement kernel + self-intersection guard.
- `CommitThrottler` -- AnimationTimer-based ~30 Hz mid-drag setROI throttle that splices through `LocalRegion` so untouched segments stay literally identical.
- `PolylineCompactor` and `LoopRemover` -- shared end-of-stroke cleanup.

ASCII-only in logs/strings/comments per project convention.

## License

GPL-3.0 (matching the wizard wand extension and QuPath itself).
