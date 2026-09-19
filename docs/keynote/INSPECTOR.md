# Keynote inspector, as observed

Surveyed live on Keynote 26 (macOS), 2026-09-19, by driving the app and screenshotting it. Screenshots sit next to this file as `inspector-*.png`. Reference only, not a design spec: `design/` stays the source of truth for our own look.

## Frame

- Three toolbar tabs over the panel: Format, Animate, Document. Clicking the active one closes the panel; the canvas refits (Fit went 40% to 54%) and the toolbar unfolds its overflow items into the freed width. `inspector-toggle.png`
- Under them, a full-width segmented control whose segments depend on the selection:

  | Selection | Segments |
  |---|---|
  | Nothing | none, a centred "Slide" title instead |
  | Shape, text box | Style, Text, Arrange |
  | Image | Style, Image, Arrange |
  | Line | Style, Arrange |
  | Table | Table, Cell, Text, Arrange |
  | Mixed kinds | Style, Arrange |
  | Animate, object selected | Build In, Action, Build Out |
  | Animate, nothing selected | none, a "Transitions" title |
  | Document | Document, Audio |

- The segment is remembered by name across selections. Arrange stays Arrange going shape to table to line. When the new selection has no segment of that name (Text to image, Image to line) it falls back to Style.
- Disclosure state is remembered across selections too: expand Fill/Border/Shadow on a shape, select a text box, they are still expanded. Scroll position resets.
- The style swatch grid (2x3, paged with chevrons and dots, titled "Shape Styles" / "Image Styles" / "Line Styles") is pinned. Only the sections below it scroll.

## Section anatomy

- Collapsible sections are a chevron + bold title + a summary on the right of the header: a swatch for Fill, a line preview for Border, a shadow preview for Shadow, the value popup for Spacing and Bullets. "None" is a grey well with a red diagonal. Mixed is a grey well with three dots.
- Expanded body starts with a kind popup, and the controls under it depend on the kind:
  - Fill: No Fill, Colour Fill, Gradient Fill, Advanced Gradient Fill, Image Fill, Advanced Image Fill. Colour Fill is one colour well (swatch popup + colour wheel button).
  - Border: No Border, Line, Picture Frame. Line is stroke style popup + colour well + width `5 pt` with stepper. `inspector-border_line.png`
  - Shadow: No Shadow, Drop, Contact, Curved. Drop is Blur, Offset, Opacity as slider + field + stepper rows, then an angle dial + `270°` field + colour well. `inspector-shadow_drop2.png`
- Non-collapsible rows: Reflection checkbox, Opacity (slider + `100%` + stepper), Title/Caption checkboxes with a position popup (shapes and images, not text boxes or lines).
- Every numeric field carries its unit inside the field (`pt`, `%`, `°`, `s`) and has a stepper. The stepper arrow greys out at a bound (opacity up-arrow at 100%).
- Typing in a field does nothing until Return or Tab; Tab commits and moves focus to the next field. Steppers commit per click.
- Mixed values across a multi-selection show in grey italics; a mixed checkbox shows a dash. `inspector-multi_arrange.png`

## Format > Style

- Shape / text box: styles, Fill, Border, Shadow, Reflection, Opacity (+ Title/Caption for shapes).
- Image: styles, Border, Shadow, Reflection, Opacity, Title/Caption. No Fill.
- Line: styles, Stroke (as Border's Line, plus two End points popups), Shadow, Reflection, Opacity.
- Mixed: "Multiple Object Types" where the styles go, then only the sections every kind shares, their popups reading "Multiple Border Types". `inspector-multi_style.png`

## Format > Text

- Paragraph style picker on top (big, rendered in the style itself: "Title", "Body").
- Second-level segmented control: Style, Layout.
- Style: Font family popup; weight popup + size `48 pt` with stepper; B / I / U / S segments + an advanced-options gear; Character Styles popup; Text Colour well; horizontal alignment (4: left, centre, right, justify); outdent/indent pair; vertical alignment (3: top, middle, bottom); Spacing disclosure (collapsed shows the line-spacing popup, expanded shows Lines popup + value, Before Paragraph, After Paragraph); Bullets & Lists disclosure. `inspector-text_text.png`, `inspector-text_spacing.png`
- Layout: Columns, Text Inset, Indents, Tabs, Paragraph Borders, Paragraph Background. `inspector-text_layout.png`

## Format > Image

File Info (name + replace button), Edit Mask / Auto Crop, Remove Background, Adjustments (Exposure, Saturation as slider + `0%` + stepper; Enhance, an advanced-adjust button, Reset), Description. `inspector-img_image.png`

## Format > Arrange

- Back / Front and Backwards / Forward as two icon pairs with captions. The impossible ones grey out (frontmost object: Front and Forward off).
- Align and Distribute as menu buttons. Distribute needs 3+ objects.
- Size: Width, Height, Constrain proportions. Position: X, Y. Lines swap both for Start X/Y and End X/Y. `inspector-line_arrange.png`
- Rotate: dial + angle field + stepper, two Flip buttons.
- Lock / Unlock, Group / Ungroup as two button pairs, always present, greyed when not applicable.
- Shapes add Corner Radius above Lock when the shape has one.

## Animate

- Empty state per segment: greyed "No Build In Effect" over a prominent blue "Add an Effect". The effect list is a popover anchored to the button, grouped with headers ("Appear & Move", "Flip, Spin & Scale"; Action: "Basic" Move/Opacity/Rotate/Scale, "Emphasis" Blink/Bounce/Flip/Jiggle/Pop/Pulse), None on top. `inspector-anim_action_list.png`
- Configured state: effect thumbnail + name, Change and Preview buttons, then Duration (slider + `1 s` + stepper), effect-specific options, Order popup, Delivery popup. `inspector-anim_dissolve.png`
- Build Order is pinned to the panel's foot and opens a seperate floating window: numbered rows (thumbnail, object name, effect), then Start (On Click / With / After) + Delay, then Preview. `inspector-buildorder.png`
- A slide with builds gets three small dots under its navigator thumbnail.
- Nothing selected: Transitions. Same Add an Effect pattern, plus Start Transition (On Click / Automatically) + Delay. `inspector-anim_none.png`

## Format with nothing selected, and Document

- Format falls back to the slide: Slide Layout card (thumbnail + name, opens the layout picker), Appearance (Title, Body, Slide Number), Background (Standard / Dynamic, fill kind popup, colour well), Edit Slide Layout pinned at the foot.
- Document > Document: Theme card + Change Theme, Slideshow Settings (auto-play on open, loop, restart if idle + minutes), Presentation Type popup + transition/build delays, Slide Size popup, password.
- Document > Audio: soundtrack.
