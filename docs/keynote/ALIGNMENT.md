# Keynote alignment guides, as observed

Surveyed live on Keynote 26 (macOS), 2026-10-12, from a screen recording of three 308pt shapes (square, circle, triangle) on a 1920x1080 slide, then by driving the app at 54% zoom and screenshotting mid-drag. Reference only, like `INSPECTOR.md`.

## Settings (Keynote > Settings > Rulers), the defaults

`alignment-settings.png`

- Guide colour: yellow. Sampled off the canvas as rgb(225, 192, 35).
- Show guides at object centre: on.
- Show guides at object edges: off.
- Show guides for relative sizing: on.
- Show guides for relative spacing: on.
- Show size and position when moving objects: on.
- Haptic feedback on snap: on.

## Guides

- A solid 1pt line across the whole slide, edge to edge, at the aligned coordinate. No overhang, no label.
- With the defaults, a guide appears when the dragged box's centre meets another object's centre or the slide's centre, on either axis. Both axes can show at once (an object dropped on the slide centre gets a cross).
- Nothing at the slide's edges: an object dragged to x 0 shows no guide.
- A multi-selection is one box: three shapes dragged together snap their bounding box's centre to the slide centre.

## Snap distance

Measured by dragging the square down by N screen pixels at 54% zoom with a wide x offset (so only y could snap): 6px snapped, 7px didn't. Call it six screen points, whatever the zoom.

## Relative spacing

`alignment-spacing.png`

- When the dragged object's gap to a neighbour equals that neighbour's gap to the next object along, or when it sits midway between two neighbours, both gaps get a double-headed arrow with a tick at each end. The object snaps to make the gaps equal.
- Only objects beside each other count: a neighbour has to share some vertical run (for horizontal spacing) with the dragged object.
- Each arrow sits at the middle of the vertical run the two objects bounding that gap share. Square 40pt higher than the circle: its arrow sits 20pt above the circle's centre line, the circle-triangle arrow stays on it.
- Gaps of 93 and 94 both read as equal: Keynote's snap lands them exactly equal, so there is no tolerance to speak of once snapped.

## Relative sizing

`alignment-sizing.png`

- While resizing, when the width (or height) comes to match another object's, every matching object including the one being resized gets a double-headed arrow with end ticks spanning its width, about 10 screen points above its top (heights: beside its left). The size snaps to the match.

## Readout

- A dark rounded pill (about rgb(27, 27, 27), slightly translucent) with white ~13pt text, up and to the right of the pointer: left edge ~8pt right of the pointer tip, bottom edge ~20pt above it.
- Moving: `x: 404 pt  y: 386 pt`, the top-left of the dragged box, integers. Resizing: `w: 308 pt  h: 308 pt`.
- Shown only while the button is down. Gone on release.

## Keyboard

- Arrow keys nudge 1pt, Shift+arrow 10pt. (Already Cupboard's.)

## What Cupboard does differently

- Cupboard's snap switches are Center (slide centre), Edges (slide margins), Objects (object edges and centres) and Guides. Keynote's object-edge guides are off by default; Cupboard's Objects switch covers edges and centres together, so Cupboard shows edge guides Keynote wouldn't out of the box.
- Cupboard snaps to the slide's 5% margins. Keynote has no such lines.
