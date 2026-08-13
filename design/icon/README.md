# Cupboard app icon

Construction: 128-unit grid. Carcass 100x100 at (14,14), radius 20, 8-unit stroke; shelf on the
horizontal centre axis (y64); board 54x30 (16:9) at (26,26) with the play triangle knocked out;
mug bottom-right — 36-unit rim at y79, base arc r19, annular handle (outer r10 / inner r5.5)
centred at (93,92).

Gradient: #FFC24B -> #FF7A59 (55%) -> #F0357B, 135 degrees. Ink: #3C2A4D on light, #FFFFFF on dark.

| File | Use |
|---|---|
| cupboard-icon.svg | Mark on transparent, plum ink (light backgrounds) |
| cupboard-icon-dark.svg | Mark on transparent, white ink (dark backgrounds) |
| cupboard-icon-mono.svg | Single-colour silhouette (currentColor) for tray / menu bar |
| cupboard-tile-light.svg | 1024px app tile, light (r28 squircle-ish corner) |
| cupboard-tile-dark.svg | 1024px app tile, dark |
| cupboard-icon-grid.svg | Construction grid + mark on separate layers (#grid / #mark) |

For .icns / .ico / PNG sets, render the tiles at 16/32/64/128/256/512/1024 and let each platform's
packaging tool mask the corners.
