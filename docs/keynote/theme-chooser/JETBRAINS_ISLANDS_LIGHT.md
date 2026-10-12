# JetBrains Islands Light, as extracted

Lifted out of the "Kotlin to C#" deck (`Kotlin to C#.key`, 47 slides on Keynote's Dynamic Colour theme, every content slide on the Big Fact master), decoded with keynote-parser and checked against Keynote's own 1920x1080 slide exports. `cupboard/src/commonMain/kotlin/io/github/xxfast/cupboard/document/IslandsLayouts.kt` is these rows as code. Named after the [JetBrains Islands theme](https://blog.jetbrains.com/platform/2025/12/meet-the-islands-theme-the-new-default-look-for-jetbrains-ides) the deck's look borrows: panels as separate rounded "islands" on a soft backdrop.

- Slide space: 1920x1080, frames unscaled. Text frames are Keynote's box, or where its ink lands for the auto-sized boxes (Keynote stored those at zero size).
- Every text run the deck set itself is JetBrains Mono; the title and statement cards used Keynote's master placeholders (Helvetica Neue). The theme sets everything in JetBrains Mono. The face is not an Apple system font: where it is missing, Cupboard draws its generic monospace, and Keynote on this machine drew Helvetica.
- `lineHeight` here is Keynote's relative spacing times JetBrains Mono's natural line height (1.32): 1.0 gives 1.32, 0.9 gives 1.19, 0.8 gives 1.06.
- The deck's title and statement backgrounds were a Dynamic Colour motion background; its exported frames sample at #D94064 to #DF4065, so the theme uses the flat #DC4065.

## Defaults

| Field | Value | From |
|---|---|---|
| Background | gradient #FFFFFF to #D5D5D5, top-left to bottom-right | the Notes and Photos slides |
| Text | JetBrains Mono 42, #202020, left, spacing 1.0 | the island body text |
| Body ink | #383838 | the dimmer code runs |
| Shape | fill #FFFFFF, no border | an island |
| Code | IntelliJLight (the IDE's own Light scheme, which Islands Light sets) | the deck's IDE-light syntax colours |
| Accent | #3574F0 | the JetBrains IDE's own accent; the deck has no one accent of its own |

## Backgrounds

| Name | Value | Slides |
|---|---|---|
| Grey | #FFFFFF to #D5D5D5 | Notes, Photos (4) |
| Lavender | #FFFFFF to #ABA3ED | every Kotlin editor slide (20) |
| Sky | #FFFFFF to #56C1FF | every C# editor slide (25) |
| Pink | #DC4065 | Title, Statement (motion background) |
| White | #FFFFFF | Big Fact (2) |

## Shape styles

| Style | Fill | Stroke | Radius | Shadow |
|---|---|---|---|---|
| Island | #FFFFFF | none | 34 | black 11%, blur 22, 2 down |
| Tab | #E3EAFD | #A7BDF3 x5 | 24 | none |
| Success | #FFFFFF | #1DB100 x4 | 20 | black 42%, blur 11, 2 down |
| Warning | #FFFFFF | #FEAE00 x4 | 20 | same |
| Error | #FFFFFF | #EE220C x4 | 20 | same |

## Layouts

| Layout | Element | Frame | Font | Size | Spacing | Colour |
|---|---|---|---|---|---|---|
| Title | title: Presentation Title | 99, 423.8, 1722, 142 | JetBrainsMono-Bold | 116 | 0.8 | #FFFFFF |
| Title | body: Presentation Subtitle | 98.6, 572.8, 1722, 142 | JetBrainsMono-Bold | 55 | 0.9 | #000000 |
| Title | island | 75, 893, 1770, 457 | | | | |
| Title | body: Author and Date | 239, 967, 1230, 50 | JetBrainsMono-Bold | 36 | 0.9 | #000000 |
| Statement | body: Statement | 97, 468.5, 1722, 142 | JetBrainsMono-Medium | 116 | 0.8 | #FFFFFF, centre |
| Statement | island | 79, 896, 1770, 457 | | | | |
| Statement | body: Footnote | 145, 960, 1629, 50 | JetBrainsMono-Bold | 40 | 0.9 | #000000 |
| Big Fact | body: 100% | 98, 480, 1722, 190 | JetBrainsMono-Bold | 150 | 0.8 | #7C51E6, centre |
| Notes | title: Notes.md | 190, 150, 1500, 60 | JetBrainsMono-Bold | 42 | 0.9 | #000000 |
| Notes | island | 82, 223, 1311, 757 | | | | |
| Notes | body: Notes | 128, 269, 1219, 665 | JetBrainsMono | 41 | 1.0 | #202020 |
| Kotlin Editor, C# Editor | text: Project / C# Project | 177, 150, 420, 60 | JetBrainsMono-Bold | 42 | 0.9 | #000000 |
| Kotlin Editor, C# Editor | island | 89, 249, 518, 698 | | | | |
| Kotlin Editor, C# Editor | media: Project tree | 112, 272, 472, 652 | | | | |
| Kotlin Editor, C# Editor | tab chip | 666, 124, 513, 87 | | | | |
| Kotlin Editor, C# Editor | title: File.kt / File.cs | 690, 140, 470, 55 | JetBrainsMono | 42 | 0.9 | #000000 |
| Kotlin Editor, C# Editor | island | 662, 239, 1186, 717 | | | | |
| Kotlin Editor, C# Editor | code | 708, 285, 1094, 625 | JetBrains Mono | 42 | | IntelliJLight |
| ... and Run | tab chip | 41, 132, 438, 91 | | | | |
| ... and Run | title: File.kt / File.cs | 65, 150, 400, 55 | JetBrainsMono | 42 | 0.9 | #000000 |
| ... and Run | island | 60, 249, 1142, 698 | | | | |
| ... and Run | code | 106, 295, 1050, 606 | JetBrains Mono | 42 | | IntelliJLight |
| ... and Run | text: ⏵ Run | 1247, 150, 300, 60 | JetBrainsMono-Bold | 42 | 0.9 | #000000 |
| ... and Run | island | 1234, 249, 628, 698 | | | | |
| ... and Run | body: Output | 1280, 295, 536, 606 | JetBrainsMono | 42 | 1.0 | #202020 |
| Photos | title: Photos.jpg | 190, 150, 1500, 60 | JetBrainsMono-Bold | 42 | 0.9 | #000000 |
| Photos | photo | 96, 255, 842, 629 | | | | |
| Photos | photo | 971, 255, 856, 629 | | | | |
| Photos | island | 92, 916, 1735, 457 | | | | |
| Photos | body: Caption | 207, 955, 1459, 76 | JetBrainsMono | 30 | 0.9 | #000000 |
| Blank | (empty) | | | | | |

Left out: the small file-type icons beside each heading and tab (SVGs in the deck), the Kodee animation on the title card, the IDE screenshots the project trees were made of (a picture placeholder stands in), and the per-word colouring of the Big Fact line (#7C51E6 Kotlin, #FEAE00 C, #0076BA C#), which is a slide's own business.
