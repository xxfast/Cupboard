# Keynote lines, as observed

Surveyed live on Keynote 15.4 (macOS), 2026-10-13, by driving the app in a scratch "White" deck (1024x768 slide) at 100% zoom, measuring with AppleScript (`start point`, `end point` of each `line`) and screenshotting mid-gesture. Lines only: straight, curved, connection, pen-drawn. Reference only, like `INSPECTOR.md` and `ALIGNMENT.md`.

## Where lines come from

`lines-library.png`, `lines-insert-menu.png`

- Toolbar Shape button: a popover with a 3x4 grid of tiles and three buttons under it, Draw with Pen, Generate Shape, Browse Content Hub. No category list or "Basic" section in this version: the grid is the whole library.
- Two of the twelve tiles are lines: "Line" (a straight diagonal, row 2 column 3) and "Connection Line" (a quarter curve, row 3 column 1). Names come from hover tooltips; the AX tree gives every tile as an unnamed `button` with help "Perform press to insert".
- Hovering the Connection Line tile animates it into its selected look: square endpoint handles and the green control dot.
- Insert > Line: Line, Line with Arrowhead, Line with Two Arrowheads, Straight Connection Line, Curved Connection Line, Angled Connection Line, a separator, Draw with Pen. Only Draw with Pen has a shortcut: Opt+Shift+Cmd+P.
- Insert > Shape has no line items.

## Creating and dragging a straight line

`lines-selection.png`, `lines-drag-readout.png`

- Insert > Line > Line drops a 100x100 diagonal rising left to right, centred on the slide: start (462, 434), end (562, 334).
- Selected, a line shows only two handles: ~5pt white squares with a dark outline, one on each endpoint. No bounding box, no rotate handle, no midpoint handle.
- Dragging an endpoint moves that end alone, exactly under the pointer; the other end stays put. The other handle hides during the drag.
- The readout pill while dragging an endpoint is angle and length, not position: `22°  l: 215 pt`. Below and to the right of the pointer. The angle is 0 to 360, counterclockwise from pointing right with y up: 90° is straight up, 225° is down-left.
- Shift snaps the angle to multiples of 45°. Length follows the pointer's distance from the fixed end, so the end lands on the snapped ray, not on the pointer's projection. Measured: pointer at 14° and 20° gave 0°, 30° and 36.9° gave 45°, 70° gave 90°; a 250pt pointer distance gave a 250pt line.
- Option mirrors the drag about the line's midpoint: end moved +100 in x, start moved -100, midpoint fixed.
- Cmd: no difference from a plain drag that could be seen here.
- Dragging the stroke moves the whole line. Readout switches to position, the top-left of the line's bounding box: `x: 401 pt  y: 283 pt`. Both handles hide while moving.
- No zero-length line. Dragging the end exactly onto the start leaves it one point off (`225°  l: 1 pt`). Setting `end point` to the `start point` from AppleScript does the same: it lands at (401, 463), 1pt at 135°.
- AppleScript reports a straight line as `position` = bounding-box top-left, `width` = length, `height` = 0, `rotation` = the angle above, all integers.

## Curved lines

`lines-connection-library.png`, `lines-curve-handle.png`, `lines-curve-endpoint.png`

- There is no curved plain line. The library's curve is a connection line (it reads "Connection Line"), unattached when inserted with nothing selected. A plain Line has no curve handle: bending one takes Make Editable (see below).
- The library Connection Line lands with the same endpoints as Line, (462, 434) to (562, 334), bulging up-left: vertical tangent leaving the start, horizontal arriving at the end. A green round handle sits on the curve itself.
- Dragging the green handle bends the curve so it passes through the pointer. It can cross the chord and bulge the other way. No readout pill; the endpoint handles hide during the drag.
- The geometry is one quadratic Bézier whose point at t = 0.5 is the green handle, so the control point is `2 * handle - (start + end) / 2`. Measured on the default curve: handle at (482.3, 354.2), so control (452.5, 324.4); the stroke's pixels sit 0.55pt on average from that quadratic, against 1.55pt from a circular arc through the same three points. Connection ends also attach exactly where this model predicts (next section). Keynote never shows the control point itself.
- Insert > Line > Curved Connection Line with nothing selected gives exactly the library tile.
- Dragging an endpoint of a curved line keeps the bulge's depth in points and stretches the rest: chord 141 to 224pt, the handle's perpendicular distance from the chord stayed 35pt while its offset along the chord scaled 1.58x with the chord.
- AppleScript reports a connection line (curved or not) as its bounding box: `width` and `height` of the box, `rotation` 0.
- Format > Shapes and Lines on a connection line: only Toggle Line Type is enabled. Make Editable is greyed.

## Connection lines

`lines-connection-straight.png`, `lines-connection-attach.png`, `lines-connection-bent.png`, `lines-connection-angled.png`

- Select two objects, Insert > Line > Straight Connection Line (or Curved, or Angled). Both times the start attached to the later-created box, whichever was clicked first; the exact ordering rule is unverified.
- With nothing selected, or with a line among the two selected objects, the connection kinds insert a free connection line at the default spot, (462, 434) to (562, 334), attached to nothing.
- Each end attaches where the centre-to-centre line crosses that object's box edge, with no gap. Two 120x80 boxes: ends at (583, 180) and (737, 450), what that rule gives to the point.
- Moving a connected object re-routes the line live, same rule.
- Selected: square handles on both ends and the green handle at the midpoint. No dedicated gap or offset handles.
- Dragging an end off its object detaches that end: it stays where it was dropped and no longer follows the object. The other end stays attached.
- Dragging a free end over an object gives the object a blue glow. On release the end attaches at the edge point the rule gives, not where it was dropped.
- Dragging the midpoint of a straight connection line bends it into the curve above. The ends then slide along the box edges so each end's tangent aims at its object's centre: they sit where the line from each centre to the quadratic control crosses the edge (predicted (470, 180) and (700, 463), measured (467, 180) and (700, 464)).
- Format > Shapes and Lines > Toggle Line Type flips between the curve and an angled (elbow) route, and back. Toggling back restores the previous bend.
- Angled: horizontal, vertical, horizontal, square corners. The ends attach at edge midpoints (the top box's right-centre, the bottom box's left-centre). The green handle sits on the middle run, which always passes through it.
- Dragging the angled handle re-routes. With the handle between the boxes horizontally: horizontal, vertical, horizontal, leaving and entering through the facing side midpoints. Drag it into the top box's x span and the route turns vertical, horizontal, vertical, leaving the bottom-centre of the top box and entering the top-centre of the other, with the horizontal run at the handle's y. Sliding the handle along its own run changes nothing but the handle's place. `lines-connection-angled-handle.png`
- Insert > Line > Angled Connection Line between the same two boxes came out with the handle at (462, 434) (the default line's start point), outside both boxes, and a four-segment route that bends through it. Dragging the handle between the boxes tidied it to three segments. `lines-connection-angled-default.png`
- Connection lines don't attach to lines. Dragging a connector's end onto a plain line gives no glow, drops the end where released, and moving the line leaves it behind. Moving a connected shape from AppleScript does re-route its connectors, so this isn't a scripting artefact.

## Format > Style

`lines-inspector-style.png`

- Two segments only: Style, Arrange. Same for straight and connection lines.
- Top: a "Line Styles" swatch grid, 2x3, two pages (dots under the title, chevrons either side). Then Stroke, Shadow, Reflection, Opacity. No Fill, no Title/Caption.

### Line Styles

`lines-style-presets.png`, `lines-style-saved.png`

- Page 1 is the theme's six (White theme): black solid 2pt, green solid 2pt, red chalk 6pt, black chalk 6pt, light grey solid 2pt, black round dots 3pt.
- Applying a preset sets stroke style, colour and width, and clears both endpoints back to None.
- Page 2: a "+" tile then five empty slots. "+" saves the selected line's look, endpoints included (the saved swatch showed the double arrow), and the "+" moves to the next slot.
- Right-click a saved swatch: Apply Line Style, Redefine Style from Selection, Delete Style.

### Stroke

`lines-stroke-styles.png`, `lines-stroke-styles-applied.png`

- Header summary on the right: a preview of the current stroke. A light colour previews on a black well.
- Kind popup: No Stroke, Line. No Picture Frame for lines.
- No Stroke hides everything below it, endpoints included. The line still draws a faint grey guide in the editor (darkest pixel 209/255 when deselected) and still clicks to select.
- Line: stroke style popup, colour well (swatch + colour wheel button), width field with stepper, then "End points:" with two popups.
- Stroke style popup, 10 entries, all image-only in the AX tree: solid, square dots, short dash, long dash, round dots, then five brush strokes (chalk, tapered brush, flat calligraphic nib, dry brush, ink with blobbed ends). No custom dash editor anywhere.
- Changing the stroke style keeps the width.
- Width: two decimals allowed (2.75 sticks). Clamps to 0.25pt minimum and 100pt maximum (0 and 0.1 become 0.25, 200 and 1000 become 100). The stepper moves by whole points and snaps: 2.5 up gives 3; 0.5 down gives the 0.25 floor.

### End points

`lines-endpoints.png`

- Two popups. The left is the start, the right the end. The left one draws its icons mirrored, pointing left.
- 11 entries each, image-only in AX: None, filled arrow, filled circle, filled diamond, open arrow (two strokes, no fill), swept arrow (concave back), filled square, open square, open circle, reversed arrow (pointing back along the line), bar (perpendicular tick).
- The tip of a head sits on the endpoint; the stroke stops short under it.
- Heads scale with width, not proportionally. Filled arrow, measured on a horizontal line: 6pt wide at 1pt, 13pt at 3pt, 38pt wide and 38pt long at 10pt. Roughly 3.5x the width plus 3pt. At 0.25pt the heads are effectively invisible.

### Shadow, Reflection, Opacity

`lines-shadow.png`, `lines-reflection.png`

- Shadow popup: No Shadow, Drop Shadow, Contact Shadow, Curved Shadow. All four are enabled for a line.
  - Drop: Blur `5 pt`, Offset `2 pt`, Opacity `50%`, Angle dial `270°`, colour well (black).
  - Contact: Blur `28 pt`, Offset `0 pt`, Opacity `75%`, Perspective `10°`, colour well.
  - Curved: an Inward to Outwards slider, Offset `1 pt`, Opacity `100%`, Angle `270°`, colour well.
- Reflection: checkbox; ticking it reveals a slider + `50%` field + stepper. Drawn as a faint mirrored copy hanging under the line. AppleScript reads `reflection showing` true, `reflection value` 50.
- Opacity: slider + `100%` + stepper, as for every object.

## Format > Arrange

`lines-arrange.png`, `lines-arrange-connection.png`, `lines-connection-offset.png`, `lines-connection-types.png`

- Straight line: Back / Front / Backwards / Forward, Align (Distribute greyed with one object), Start X / Y, End X / Y, Rotate (dial + Angle field + stepper, two Flip buttons), Lock / Unlock, Group / Ungroup. No Width, Height, Constrain or Position.
- Start / End fields move that endpoint alone; their steppers move 1pt. They show whole points (`100.4` displays `100 pt`). Values past the slide are kept (End X 2000 stuck). Negative values and 0 clamp to 1pt. Typing the start's coordinates into the end leaves it 1pt away, as with dragging.
- Angle is the start-to-end direction, same convention as the drag readout. Typing one rotates the line about its midpoint, length kept: 90 makes it vertical with the end on top. It normalises to 0 to 360 (-45 shows `315°`, 400 shows `40°`) and shows one decimal (45.55 shows `45.6°`). There's no separate "rotation" on top of the endpoints: the angle is the endpoints.
- Flip horizontal mirrors the line inside its bounding box (swaps the two x values), flip vertical swaps the y values. 26.6° flips to 153.4° and 206.6°.
- Connection line, attached at both ends: Start / End fields greyed, Rotate greyed (empty Angle, flips disabled). It gains a Connection section between Rotate and Lock:
  - A segmented control: Straight, Curve, Corner. It switches the route in place; Curve reuses the current green handle. Toggle Line Type in the Format menu only flips between Curve and Corner.
  - Offset, Start and End fields (`0 pt` each, with steppers). This is the gap between the object's edge and the line's end: Start 20 left the start 20pt clear of its box, and an angled route re-flowed around the new end. Not available by dragging on the canvas (dragging an end off its object detaches it instead).

## Animate

`lines-build-list.png`, `lines-build-linedraw.png`, `lines-build-linedraw-frames.png`, `lines-build-linedraw-noends.png`

- Build In for a line: None, Recent Effects, then "Line Draw" in a group of its own with no header, then the usual Appear & Move, Flip, Spin & Scale and Special Effects groups. Line Draw is offered for straight and connection lines, not for a box.
- Build Out has Line Draw at the top too (an un-draw). Action has nothing line-specific: Basic (Move, Opacity, Rotate, Scale), Emphasis (Blink, Bounce, Flip, Jiggle, Pop, Pulse).
- Line Draw options, under "Duration & Direction":
  - Duration slider + field + stepper, default `2 s`. Accepts 0 to 60 s (100 clamps to 60).
  - Direction popup: Start to End, End to Start, Middle to Ends.
  - "Include end points" checkbox, on by default.
  - Then the usual Acceleration (None, Ease In, Ease Out, Ease In and Out; default Ease In and Out), Order, Delivery (All at Once, By Paragraph, By Paragraph Group, By Highlighted Paragraph, all enabled even for a line).
- Previewed on a 6pt line with filled arrows at both ends, Start to End over 3 s: the stroke grows from the start. With Include end points on, the end's arrowhead rides the growing tip the whole way. With it off, the tip is a bare stroke end and the end arrowhead is still absent at ~2.3 s. The start arrowhead shows from the first frame either way.

## Make Editable and point editing

`lines-edit-hint.png`, `lines-edit-addpoint.png`, `lines-edit-pointtypes.png`, `lines-edit-bezier.png`, `lines-edit-bezier-modifiers.png`, `lines-edit-doubleclick.png`, `lines-edit-done.png`, `lines-edit-done-inspector.png`

- Format > Shapes and Lines > Make Editable is enabled for a straight line, greyed for any connection line. There's no menu or toolbar route for curving a connection line other than its green handle.
- Entering edit mode turns the endpoint handles into red-outlined squares. A hint toast appears below the canvas: "Double-click points to toggle between straight and curved lines. Drag a midpoint to divide a line and add a new point."
- Adding a point: drag from the middle of a segment. The new point lands under the pointer and starts smooth: a filled red circle, the path curving through it. Hovering the other segments shows a small white circle at their midpoints. Clicking a segment without dragging adds nothing.
- With a point selected, Format > Shapes and Lines enables Make Sharp Point, Make Smooth Point, Make Bézier Point. Three point kinds:
  - Sharp: square marker, straight segments meeting at a corner.
  - Smooth: round marker, curve computed automatically, no handles.
  - Bézier: round marker with two control handles (small white circles) joined through the point by a thin grey line.
- Double-clicking a point toggles it between smooth and sharp.
- Dragging a Bézier handle keeps the point smooth: the opposite handle swings to stay collinear but keeps its own length (46.5pt before and after). Option-drag makes the opposite handle mirror exactly (same length). Cmd-drag moves one handle alone and breaks the tangent into a corner.
- Delete removes the selected point. With the extra point gone, the path is straight again and AppleScript reports it as a `line` again.
- Escape (or clicking empty canvas) leaves edit mode. A three-point path comes back as a `shape`, not a line: a rotated bounding box with eight resize handles, and the inspector gains a Text tab and Fill (No Fill) while keeping Line Styles, Stroke and End points. Endpoint arrowheads follow the path's end tangents.

## Draw with Pen

`lines-pen-drawing.png`, `lines-pen-closed.png`

- Insert > Line > Draw with Pen (Opt+Shift+Cmd+P), then click on the canvas.
- Click places a sharp point (square). Click-drag places a smooth point (hollow circle) whose tangent follows the drag direction; no handles show while drawing. The latest point is a filled red square.
- Escape finishes and keeps the path open. A click on empty canvas also finishes without adding a point. Return and a double-click don't end point editing (the double-click places the last point; the points stay up until a click away or Escape).
- Clicking the first point closes the path.
- What comes out: a two-point pen path is a `line` to AppleScript. Longer open paths are `shape`s with the line inspector (Line Styles, Fill: No Fill, Stroke with End points). A closed path is a plain shape: Shape Styles, Fill (No Fill), Border, no End points.

## Context menu

`lines-context-menu.png`

- Right-click a straight line: Bring to Front, Send to Back; Align Objects, Distribute Objects (submenus); Cut, Copy, Paste, Duplicate, Lock; Add Link (submenu); Reapply Layout to Object (greyed), Make Editable, Break Apart (greyed), Save to My Shapes (greyed).
- A connection line gets the same list with Make Editable greyed too. Nothing line-specific: no arrowhead, line type or connection items.

## Keyboard, duplicate, copy style

- Arrow keys nudge 1pt, Shift+arrow 10pt, both endpoints together.
- Option+arrow duplicates the line and nudges the copy 1pt, selecting the copy.
- Cmd+arrow does nothing to a line.
- Cmd+D duplicates with a +10, +10 offset and selects the copy.
- Format > Copy Style / Paste Style carries stroke width and both endpoints: a 6pt double-arrow path's style pasted onto a 1pt bare line gave it 6pt and both arrows. Pasted onto a box, the same style set the box's border to 6pt black and its fill to No Fill (the source had no fill).

## Hit testing

- Measured at 100% zoom on a 1pt horizontal line, clicking at growing offsets. Above the line: 6pt selects, 7pt doesn't. Below: 5pt selects, 6pt doesn't. Past the end: 5pt selects, 6pt doesn't; before the start, 6pt selects, 7pt doesn't. Call it about 6 screen points around the stroke, end caps included, independent of the stroke's own width at this size. Other zoom levels unverified.

## Other line behaviour

`lines-endpoint-snap.png`, `lines-cursors.png`, `lines-locked.png`, `lines-readout-mismatch.png`

- Snapping while dragging an endpoint goes through the line's bounding box, not the endpoint. Relative-spacing arrows (yellow) appeared between the line and its neighbours, and the end landed 5pt off the pointer (pointer at (509, 381), end at (509, 386)). An endpoint 3pt from the slide's centre, or from a box's centre, didn't snap to it.
- Cmd held during an endpoint drag turns snapping off: the same drag with Cmd ended exactly under the pointer.
- Cursor over an endpoint handle: a double-headed resize arrow, along the line on a 45° line (whether it follows other angles is unverified). Over the stroke: the plain arrow, no move cursor.
- A locked line still selects; its endpoints show grey × marks and dragging them does nothing.
- The drag readout's angle didn't always agree with where the line ended up. Early drags matched (22°, 36°, 45°), later ones read 3 to 6° high: `24°` for a line that ended at 18.4°, `50°` and `48°` for two 45° lines, `44°` for 38.3°. The length matched within ~6pt. The cause is unverified; the AppleScript geometry is the truth.
- AppleScript quirk, for anyone measuring the same way: reading `position`, `width`, `height` or `rotation` of a line selects that line. `start point` and `end point` don't.

## Unverified

- Hit tolerance at zoom levels other than 100%.
- Whether endpoints snap to other objects' edges (edge guides are off by default and weren't turned on).
- The exact rule for which object a connection line's start attaches to.
- Whether Line Draw is offered for open pen paths.
- Whether the end arrowhead pops in at the very end of a Line Draw with Include end points off.

## What Cupboard does differently

- Cupboard's line is `ShapeKind.Line`, a shape whose stroke runs corner to corner across its frame (round cap, default 320x120 at 3pt). Keynote's line is two points: no frame, no box handles, only an endpoint handle at each end. A diagonal is all Cupboard can draw directly; the other diagonal needs a flip. Keynote's Start / End X / Y, angle-and-length readout, and 45° Shift snap all come from storing endpoints.
- Cupboard's selection is a frame with resize handles. Keynote shows only the two endpoint handles, and Option-drag mirrors about the midpoint.
- Stroke: Cupboard has colour and width. Keynote adds 10 stroke styles (4 dash/dot patterns, 5 brushes), a 0.25 to 100pt width range, and themed Line Styles presets with user-saved slots.
- Endpoints: Cupboard has `startArrow` / `endArrow` booleans. Keynote has 10 endpoint styles per end that scale with width (about 3.5x + 3pt).
- No curves in Cupboard. Keynote's curve is one quadratic through a draggable on-curve handle, plus full point editing (sharp, smooth, Bézier) via Make Editable, and a pen tool. Cupboard's `ShapeKind.Path` (imported SVGs, broken-apart shapes) is the nearest thing, but has no editing UI.
- No connection lines in Cupboard. Keynote's attach to boxes, follow them, come in Straight / Curve / Corner, and carry Start / End offsets. The closest Cupboard has is `DiagramElement`: edges laid out from mermaid-style text, solid, dotted or thick, arrow on or off, sharing the line's arrowhead. They're one object, not lines you can grab.
- No Line Draw build in Cupboard; Keynote has it for build in and out, with direction and an option to carry the end's arrowhead along the tip.
- Cupboard's line carries a label (`ShapeLabel`); a Keynote line has no Text tab until it's been edited into a path.
- Same in both: shadow, opacity, rotation and lock exist. Cupboard's `ShapeShadow` is a drop shadow only; Keynote also offers Contact and Curved shadows, and Reflection, on lines.
