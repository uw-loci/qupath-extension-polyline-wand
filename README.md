# QuPath Polyline Wand and Brush

A QuPath extension that adds a brush/wand-style editor for **line** and **polyline** annotations. QuPath's built-in brush and wand operate on areas only; this extension adds the same kind of fluid editing for the line family.

## Use cases

- Tracing the leading edge of a tumor along a long polyline, then needing to push a section outward to include tissue you originally passed through.
- Drawing a long polyline for a vein and overshooting at the endpoint with a jerky hand movement -- erase backwards from the endpoint instead of restarting.
- Smoothing out a noisy polyline.

## Three brush engines, one tool

The toolbar exposes a single **Polyline Wand** tool. The right-click context menu lets you switch between three engines at runtime.

- **Direct vertex push** (default): per-frame brush displaces affected vertices using a configurable falloff. Optional local densification, end-of-stroke simplification. Most reactive: brush can start anywhere; the line is pulled toward it when in range.
- **Area proxy + skeletonize**: buffers the polyline to a thin area, brush stamps union/difference into that area, on release OpenCV `ximgproc.thinning` (Zhang-Suen) recovers a polyline. Best for bold reshapes and gap bridging.
- **Displacement field**: arc-length parameterized displacement over a locked active window. Cosine kernel + velocity damping for the most tactile feel. Vertices outside the window are guaranteed untouched.

## Installation

Default chord: **Shift+P**. The extension auto-promotes a `LineROI` to a `PolylineROI` (densified to 32 vertices by default) on first edit.

Drop the shadow JAR (`qupath-extension-polyline-wand-X.Y.Z-all.jar`) into your QuPath install's extensions folder, or wait for the catalog to publish the new release.

## Settings

All preferences live under the "Polyline Wand" category in the QuPath Preferences pane, with engine-specific entries grouped under nested sub-categories. Reset everything from the right-click toolbar menu.

## Build

```
./gradlew shadowJar
```

Output: `build/libs/qupath-extension-polyline-wand-X.Y.Z-all.jar`.
