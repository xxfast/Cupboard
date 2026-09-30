# Keynote Basic themes, as extracted

Read out of Keynote Creator Studio's own theme files (`Templates/<theme>/Wide.kth`, decoded with keynote-parser) and checked against Keynote's own 1920x1080 slide exports. `cupboard/src/commonMain/kotlin/io/github/xxfast/cupboard/document/BasicLayouts.kt` is these rows as code.

- Slide space: 1920x1080 in every Wide theme file, so frames are used unscaled. The 4:3 masters are their own set, tabled under Standard (4:3) at the end.
- Frames are Keynote's box (x, y, w, h). Text sits inside a 4pt inset; `valign` is where Keynote pins it in the box. Cupboard text boxes have no vertical align, so `BasicLayouts.kt` holds each frame where Keynote's ink actually lands (measured, within 1px), not these boxes.
- Photo frames are the visible (masked) area of the sample image.
- `spacing` is Keynote's relative line spacing (blank is 1.0). Cupboard's `lineHeight` is that times the face's natural line height (ascent + descent + leading): Helvetica Neue 1.193 (Regular) / 1.221 (Medium, Bold), Canela 1.51, Graphik 1.33.
- `tracking` is a fraction of the size; Cupboard's `letterSpacing` is tracking x size.
- Style names are Keynote's paragraph styles; `BasicLayouts.kt` names its looks after them.
- Live Video layouts are listed by Keynote but left out of Cupboard.

## Defaults

| Theme | Background | Text box | Shape |
|---|---|---|---|
| Basic White | #FFFFFF | HelveticaNeue 48, #000000, left | fill #000000, no border, label HelveticaNeue-Medium 32 #FFFFFF |
| Basic Black | #000000 | HelveticaNeue 48, #FFFFFF, left | fill #FFFFFF, no border, label HelveticaNeue-Medium 32 #000000 |
| Classic White | #FFFFFF | CanelaText-Regular 44, #000000, left | fill #000000, no border, label Graphik-Regular 32 #FFFFFF |
| White | #FFFFFF | HelveticaNeue 48, #000000, left | fill #00A2FF, no border, label HelveticaNeue-Medium 32 #FFFFFF |
| Black | #000000 | HelveticaNeue 48, #FFFFFF, left | fill #00A2FF, no border, label HelveticaNeue-Medium 32 #FFFFFF |

## Fonts

Helvetica Neue ships with macOS. Canela, Canela Text, Canela Deck and Graphik are Apple downloadable system fonts (the `com_apple_MobileAsset_Font8` catalogue): present only once something has asked CoreText for them. Where a face is missing, Cupboard draws its generic family (sans for Helvetica Neue and Graphik, serif for the Canelas).

## Basic White

| Layout | Element | Frame | valign | Style | Font | Size | Align | Spacing | Tracking | Colour |
|---|---|---|---|---|---|---|---|---|---|---|
| Title | text: Author and Date | 94.6, 933.8, 1730, 50.2 | top | Heading | HelveticaNeue-Bold | 36 | left |  |  | #000000 |
| Title | title: Presentation Title | 95, 202.8, 1730, 366 | bottom | Title | HelveticaNeue-Bold | 116 | left | 0.8 | -0.02 | #000000 |
| Title | body: Presentation Subtitle | 94.6, 568.8, 1730, 150 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #000000 |
| Title and Photo | photo | 0, 0, 1920, 1080 | | | | | | | | |
| Title and Photo | title: Presentation Title | 95, 561, 1730, 366 | bottom | Title | HelveticaNeue-Bold | 116 | left | 0.8 | -0.02 | #000000 |
| Title and Photo | text: Author and Date | 95.1, 87.1, 1729.8, 50.2 | top | Heading | HelveticaNeue-Bold | 36 | left |  |  | #000000 |
| Title and Photo | body: Presentation Subtitle | 95, 914.2, 1730, 87.9 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #000000 |
| Title and Photo Alt | photo | 960, 100, 860, 880 | | | | | | | | |
| Title and Photo Alt | title: Slide Title | 95, 100, 770, 463.2 | bottom | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #000000 |
| Title and Photo Alt | body: Slide Subtitle | 95, 556, 770, 424 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #000000 |
| Title and Bullets | title: Slide Title | 95, 85, 1730, 112.8 | top | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #000000 |
| Title and Bullets | text: Slide Subtitle | 95, 186.8, 1730, 73.6 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #000000 |
| Title and Bullets | body: Slide bullet text (bullets) | 95, 334.5, 1730, 650.1 | top | Body | HelveticaNeue | 48 | left | 0.9 |  | #000000 |
| Bullets | body: Slide bullet text (bullets) | 95, 334.5, 1730, 650.1 | top | Body | HelveticaNeue | 48 | left | 0.9 |  | #000000 |
| Title, Bullets and Photo | text: Slide Subtitle | 95, 186.8, 770, 73.6 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #000000 |
| Title, Bullets and Photo | body: Slide bullet text (bullets) | 95, 334.5, 770, 650.1 | top | Body | HelveticaNeue | 48 | left | 0.9 |  | #000000 |
| Title, Bullets and Photo | photo | 960, 99.5, 859.6, 881 | | | | | | | | |
| Title, Bullets and Photo | title: Slide Title | 95, 85, 770, 113 | top | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #000000 |
| Title, Bullets and Live Video Small | text: Slide Subtitle | 95, 186.8, 770, 73.6 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #000000 |
| Title, Bullets and Live Video Small | body: Slide bullet text (bullets) | 95, 334.5, 770, 650.1 | top | Body | HelveticaNeue | 48 | left | 0.9 |  | #000000 |
| Title, Bullets and Live Video Small | title: Slide Title | 95, 85, 770, 113 | top | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #000000 |
| Title, Bullets and Live Video Small | video | 1320.8, 480.6, 500, 500 | | | | | | | | |
| Title, Bullets and Live Video Large | text: Slide Subtitle | 95, 186.8, 770, 73.6 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #000000 |
| Title, Bullets and Live Video Large | body: Slide bullet text (bullets) | 95, 334.5, 770, 650.1 | top | Body | HelveticaNeue | 48 | left | 0.9 |  | #000000 |
| Title, Bullets and Live Video Large | title: Slide Title | 95, 85, 770, 113 | top | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #000000 |
| Title, Bullets and Live Video Large | video | 960, 100, 860, 881 | | | | | | | | |
| Section | title: Section Title | 95, 357, 1730, 366 | middle | Section | HelveticaNeue-Medium | 116 | left | 0.8 | -0.02 | #000000 |
| Title Only | title: Slide Title | 95, 85, 1730, 113 | top | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #000000 |
| Title Only | text: Slide Subtitle | 95, 186.8, 1730, 73.6 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #000000 |
| Agenda | title: Agenda Title | 95, 85, 1730, 113 | top | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #000000 |
| Agenda | text: Agenda Subtitle | 95, 186.8, 1730, 73.6 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #000000 |
| Agenda | body: Agenda Topics | 95, 334.5, 1730, 650.1 | top | Agenda | HelveticaNeue | 55 | left |  | -0.01 | #000000 |
| Statement | body: Statement | 95, 387.5, 1730, 305.1 | middle | Statement | HelveticaNeue-Medium | 116 | centre | 0.8 | -0.02 | #000000 |
| Big Fact | body: 100% | 95, 84.7, 1730, 570.2 | bottom | Fact | HelveticaNeue-Bold | 250 | centre | 0.8 | -0.01 | #000000 |
| Big Fact | text: Fact information | 95, 650.6, 1730, 73.6 | top | Subtitle Alt | HelveticaNeue-Bold | 55 | centre |  |  | #000000 |
| Quote | text: Attribution | 191.3, 840.6, 1590.6, 50.2 | top | Attribution | HelveticaNeue-Bold | 36 | left |  |  | #000000 |
| Quote | body: “Notable Quote” | 138.1, 389, 1643.8, 302.1 | top | Quote | HelveticaNeue-Medium | 85 | left | 0.9 | -0.02 | #000000 |
| Photo - 3 Up | photo | 1241, 100, 584.5, 426 | | | | | | | | |
| Photo - 3 Up | photo | 1241, 559, 584.5, 426.4 | | | | | | | | |
| Photo - 3 Up | photo | 95.4, 100, 1115.6, 885.3 | | | | | | | | |
| Photo | photo | 0, 0, 1920, 1080 | | | | | | | | |
| Blank | (empty) | | | | | | | | | |

## Basic Black

| Layout | Element | Frame | valign | Style | Font | Size | Align | Spacing | Tracking | Colour |
|---|---|---|---|---|---|---|---|---|---|---|
| Title | text: Author and Date | 95, 932.2, 1730, 50.2 | bottom | Heading | HelveticaNeue-Bold | 36 | left |  |  | #FFFFFF |
| Title | title: Presentation Title | 95, 202.8, 1730, 366 | bottom | Title | HelveticaNeue-Bold | 116 | left | 0.8 | -0.02 | #FFFFFF |
| Title | body: Presentation Subtitle | 95, 566.7, 1730, 150 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #FFFFFF |
| Title and Photo | photo | 0, 0, 1920, 1080 | | | | | | | | |
| Title and Photo | title: Presentation Title | 95, 561, 1730, 366 | bottom | Title | HelveticaNeue-Bold | 116 | left | 0.8 | -0.02 | #FFFFFF |
| Title and Photo | text: Author and Date | 95.1, 87.1, 1729.8, 50.2 | top | Heading | HelveticaNeue-Bold | 36 | left |  |  | #FFFFFF |
| Title and Photo | body: Presentation Subtitle | 95, 914.2, 1730, 90.1 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #FFFFFF |
| Title and Photo Alt | title: Slide Title | 95, 100, 770, 463.2 | bottom | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #FFFFFF |
| Title and Photo Alt | body: Slide Subtitle | 95, 556, 770, 423.8 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #FFFFFF |
| Title and Photo Alt | photo | 960, 100, 860, 881 | | | | | | | | |
| Title and Bullets | title: Slide Title | 95, 75, 1730, 112.8 | top | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #FFFFFF |
| Title and Bullets | text: Slide Subtitle | 95, 176.8, 1730, 73.6 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #FFFFFF |
| Title and Bullets | body: Slide bullet text (bullets) | 95, 334.5, 1730, 650.1 | top | Body | HelveticaNeue | 48 | left | 0.9 |  | #FFFFFF |
| Bullets | body: Slide bullet text (bullets) | 95, 334.5, 1730, 650.1 | top | Body | HelveticaNeue | 48 | left | 0.9 |  | #FFFFFF |
| Title, Bullets and Photo | title: Slide Title | 95, 75, 770, 113 | top | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #FFFFFF |
| Title, Bullets and Photo | text: Slide Subtitle | 95, 176.8, 770, 73.6 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #FFFFFF |
| Title, Bullets and Photo | body: Slide bullet text (bullets) | 95, 334.5, 770, 650.1 | top | Body | HelveticaNeue | 48 | left | 0.9 |  | #FFFFFF |
| Title, Bullets and Photo | photo | 960, 99.5, 860, 881 | | | | | | | | |
| Title, Bullets and Live Video Small | title: Slide Title | 95, 75, 770, 113 | top | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #FFFFFF |
| Title, Bullets and Live Video Small | text: Slide Subtitle | 95, 176.8, 770, 73.6 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #FFFFFF |
| Title, Bullets and Live Video Small | body: Slide bullet text (bullets) | 95, 334.5, 770, 650.1 | top | Body | HelveticaNeue | 48 | left | 0.9 |  | #FFFFFF |
| Title, Bullets and Live Video Small | video | 1319.1, 481.4, 500, 500 | | | | | | | | |
| Title, Bullets and Live Video Large | title: Slide Title | 95, 75, 770, 113 | top | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #FFFFFF |
| Title, Bullets and Live Video Large | text: Slide Subtitle | 95, 176.8, 770, 73.6 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #FFFFFF |
| Title, Bullets and Live Video Large | body: Slide bullet text (bullets) | 95, 334.5, 770, 650.1 | top | Body | HelveticaNeue | 48 | left | 0.9 |  | #FFFFFF |
| Title, Bullets and Live Video Large | video | 960, 100, 860, 881 | | | | | | | | |
| Section | title: Section Title | 95, 357, 1730, 366 | middle | Section | HelveticaNeue-Medium | 116 | left | 0.8 | -0.02 | #FFFFFF |
| Title Only | title: Slide Title | 95, 75, 1730, 113 | top | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #FFFFFF |
| Title Only | text: Slide Subtitle | 95, 176.8, 1730, 73.6 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #FFFFFF |
| Agenda | title: Agenda Title | 95, 75, 1730, 113 | top | Title Small | HelveticaNeue-Bold | 85 | left | 0.8 | -0.02 | #FFFFFF |
| Agenda | text: Agenda Subtitle | 95, 176.8, 1730, 73.6 | top | Subtitle | HelveticaNeue-Bold | 55 | left |  |  | #FFFFFF |
| Agenda | body: Agenda Topics | 95, 334.5, 1730, 650.1 | top | Agenda | HelveticaNeue | 55 | left |  | -0.01 | #FFFFFF |
| Statement | body: Statement | 95, 387.5, 1730, 305.1 | middle | Statement | HelveticaNeue-Medium | 116 | centre | 0.8 | -0.02 | #FFFFFF |
| Big Fact | text: Fact information | 95, 650.6, 1730, 73.6 | top | Subtitle Alt | HelveticaNeue-Bold | 55 | centre |  |  | #FFFFFF |
| Big Fact | body: 100% | 95, 73.6, 1730, 579.5 | bottom | Fact | HelveticaNeue-Bold | 250 | centre | 0.8 | -0.01 | #FFFFFF |
| Quote | text: Attribution | 195.3, 840.6, 1586.6, 50.2 | top | Attribution | HelveticaNeue-Bold | 36 | left |  |  | #FFFFFF |
| Quote | body: “Notable Quote” | 138.1, 389, 1643.8, 302.1 | middle | Quote | HelveticaNeue-Medium | 85 | left | 0.9 | -0.02 | #FFFFFF |
| Photo - 3 Up | photo | 1245, 557.9, 580, 426 | | | | | | | | |
| Photo - 3 Up | photo | 95.4, 100, 1115.6, 884.1 | | | | | | | | |
| Photo - 3 Up | photo | 1245, 100, 580, 426 | | | | | | | | |
| Photo | photo | 0, 0, 1920, 1080 | | | | | | | | |
| Blank | (empty) | | | | | | | | | |

## Classic White

| Layout | Element | Frame | valign | Style | Font | Size | Align | Spacing | Tracking | Colour |
|---|---|---|---|---|---|---|---|---|---|---|
| Title | text: Author and Date | 96, 943.8, 1728, 47.7 | top | Heading | Graphik-Medium | 30 | centre |  | -0.01 | #000000 |
| Title | title: Presentation Title | 96, 279, 1728, 336 | bottom | Title | Canela-Bold | 128 | centre | 0.8 | -0.01 | #000000 |
| Title | body: Presentation Subtitle | 96, 595.9, 1728, 177.2 | top | Subtitle | Graphik-Semibold | 60 | centre |  | -0.01 | #000000 |
| Title and Photo | photo | 0, 0, 1920, 1080 | | | | | | | | |
| Title and Photo | title: Presentation Title | 96, 279, 1728, 336 | bottom | Title Alt | Canela-Bold | 128 | centre | 0.8 | -0.01 | #FFFFFF |
| Title and Photo | body: Presentation Subtitle | 96, 596, 1728, 177.3 | top | Subtitle Alt | Graphik-Semibold | 60 | centre |  | -0.01 | #FFFFFF |
| Title and Photo | text: Author and Date | 96, 944, 1728, 47.7 | top | Heading Alt | Graphik-Medium | 30 | centre |  | -0.01 | #FFFFFF |
| Title and Photo Alt | title: Slide Title | 95.7, 361, 768.3, 200 | bottom | Title Small | Canela-Bold | 84 | centre | 0.8 | -0.01 | #000000 |
| Title and Photo Alt | photo | 960, 100, 860, 880 | | | | | | | | |
| Title and Photo Alt | body: Slide Subtitle | 96, 552.5, 768, 426.5 | top | Subtitle Small | Graphik-Semibold | 44 | centre |  | -0.01 | #000000 |
| Title and Bullets | title: Slide Title | 96, 61, 1728, 136 | top | Title Small | Canela-Bold | 84 | centre | 0.8 | -0.01 | #000000 |
| Title and Bullets | body: Slide bullet text (bullets) | 96, 316, 1728.2, 668 | top | Body | CanelaText-Regular | 44 | left | 0.9 |  | #000000 |
| Title and Bullets | text: Slide Subtitle | 96, 187.8, 1728, 65.6 | top | Subtitle Small | Graphik-Semibold | 44 | centre |  | -0.01 | #000000 |
| Bullets | body: Slide bullet text (bullets) | 96, 316, 1728, 668.3 | top | Body | CanelaText-Regular | 44 | left | 0.9 |  | #000000 |
| Title, Bullets and Photo | title: Slide Title | 96, 61, 768, 126 | top | Title Small | Canela-Bold | 84 | centre | 0.8 | -0.01 | #000000 |
| Title, Bullets and Photo | photo | 960.1, 100, 860, 880 | | | | | | | | |
| Title, Bullets and Photo | text: Slide Subtitle | 96, 188, 768.3, 65.6 | top | Subtitle Small | Graphik-Semibold | 44 | centre |  | -0.01 | #000000 |
| Title, Bullets and Photo | body: Slide bullet text (bullets) | 96, 316.8, 768.3, 660.2 | top | Body | CanelaText-Regular | 44 | left | 0.9 |  | #000000 |
| Title, Bullets and Live Video Small | title: Slide Title | 96, 61, 768, 126 | top | Title Small | Canela-Bold | 84 | centre | 0.8 | -0.01 | #000000 |
| Title, Bullets and Live Video Small | text: Slide Subtitle | 96, 188, 768.3, 65.6 | top | Subtitle Small | Graphik-Semibold | 44 | centre |  | -0.01 | #000000 |
| Title, Bullets and Live Video Small | body: Slide bullet text (bullets) | 96, 316.8, 768.3, 660.2 | top | Body | CanelaText-Regular | 44 | left | 0.9 |  | #000000 |
| Title, Bullets and Live Video Small | video | 1321, 481, 500, 500 | | | | | | | | |
| Title, Bullets and Live Video Large | title: Slide Title | 96, 61, 768, 126 | top | Title Small | Canela-Bold | 84 | centre | 0.8 | -0.01 | #000000 |
| Title, Bullets and Live Video Large | text: Slide Subtitle | 96, 188, 768.3, 65.6 | top | Subtitle Small | Graphik-Semibold | 44 | centre |  | -0.01 | #000000 |
| Title, Bullets and Live Video Large | body: Slide bullet text (bullets) | 96, 316.8, 768.3, 660.2 | top | Body | CanelaText-Regular | 44 | left | 0.9 |  | #000000 |
| Title, Bullets and Live Video Large | video | 960, 100, 860, 880 | | | | | | | | |
| Section | title: Section Title | 96, 255.3, 1728, 520 | middle | Section | Canela-Bold | 128 | centre | 0.8 |  | #000000 |
| Title Only | title: Slide Title | 96, 61, 1728, 136 | top | Title Small | Canela-Bold | 84 | centre | 0.8 | -0.01 | #000000 |
| Title Only | text: Slide Subtitle | 96, 187.8, 1728, 65.6 | top | Subtitle Small | Graphik-Semibold | 44 | centre |  | -0.01 | #000000 |
| Agenda | title: Agenda Title | 96, 61, 1728, 136 | top | Title Small | Canela-Bold | 84 | centre | 0.8 | -0.01 | #000000 |
| Agenda | body: Agenda Topics | 96, 316, 1728, 660.3 | top | Agenda | CanelaDeck-Regular | 68 | left |  | -0.02 | #000000 |
| Agenda | text: Agenda Subtitle | 96, 188, 1728, 65.6 | top | Subtitle Small | Graphik-Semibold | 44 | centre |  | -0.01 | #000000 |
| Statement | body: Statement | 96, 256, 1728, 520 | middle | Statement | Canela-Regular | 128 | centre | 0.8 |  | #000000 |
| Big Fact | text: Fact information | 96, 666.3, 1728, 65.6 | top | Subtitle Small | Graphik-Semibold | 44 | centre |  | -0.01 | #000000 |
| Big Fact | body: 100% | 96, 331.8, 1728, 336.2 | bottom | Fact | Canela-Bold | 224 | centre | 0.8 |  | #000000 |
| Quote | text: Attribution | 96, 874, 1728, 65.6 | middle | Attribution | Graphik-Semibold | 44 | centre |  | -0.01 | #000000 |
| Quote | body: “Notable Quote” | 96, 329, 1728, 347.8 | middle | Quote | Canela-Bold | 84 | centre | 0.8 |  | #000000 |
| Photo - 3 Up | photo | 1239.8, 553.5, 580, 426 | | | | | | | | |
| Photo - 3 Up | photo | 1239.8, 100, 580, 426 | | | | | | | | |
| Photo - 3 Up | photo | 100.4, 100, 1110.6, 879.4 | | | | | | | | |
| Photo | photo | 100, 100, 1720, 880 | | | | | | | | |
| Blank | (empty) | | | | | | | | | |

## White

| Layout | Element | Frame | valign | Style | Font | Size | Align | Spacing | Tracking | Colour |
|---|---|---|---|---|---|---|---|---|---|---|
| Title | title: Title Text | 140, 181, 1640, 366 | bottom | Title | HelveticaNeue-Medium | 112 | centre |  |  | #000000 |
| Title | body: Body Level One | 140, 557, 1640, 125 | top | Subtitle | HelveticaNeue | 54 | centre |  |  | #000000 |
| Photo - Horizontal | photo | 246.1, 53, 1428, 688 | | | | | | | | |
| Photo - Horizontal | title: Title Text | 50, 749, 1820, 158 | bottom | Title | HelveticaNeue-Medium | 112 | centre |  |  | #000000 |
| Photo - Horizontal | body: Body Level One | 50, 901, 1820, 125 | top | Subtitle | HelveticaNeue | 54 | centre |  |  | #000000 |
| Title - Centre | title: Title Text | 140, 357, 1640, 366 | middle | Title | HelveticaNeue-Medium | 112 | centre |  |  | #000000 |
| Photo - Vertical | photo | 1036.7, 75, 750, 903 | | | | | | | | |
| Photo - Vertical | title: Title Text | 130, 75, 805, 437 | bottom | Title Alt | HelveticaNeue-Medium | 84 | centre |  |  | #000000 |
| Photo - Vertical | body: Body Level One | 130, 514, 805, 451 | top | Subtitle | HelveticaNeue | 54 | centre |  |  | #000000 |
| Title - Top | title: Title Text | 133, 28, 1654, 180 | middle | Title | HelveticaNeue-Medium | 112 | centre |  |  | #000000 |
| Title and Bullets | title: Title Text | 133, 28, 1654, 180 | middle | Title | HelveticaNeue-Medium | 112 | centre |  |  | #000000 |
| Title and Bullets | body: Body Level One (bullets) | 133, 248, 1654, 732 | middle | Body | HelveticaNeue | 48 | left |  |  | #000000 |
| Title, Bullets and Photo | photo | 1037, 248, 750, 732 | | | | | | | | |
| Title, Bullets and Photo | title: Title Text | 133, 28, 1654, 180 | middle | Title | HelveticaNeue-Medium | 112 | centre |  |  | #000000 |
| Title, Bullets and Photo | body: Body Level One (bullets) | 133, 248, 805, 732 | middle | Body Small | HelveticaNeue | 38 | left |  |  | #000000 |
| Title, Bullets and Live Video Small | title: Title Text | 133, 28, 1654, 180 | middle | Title | HelveticaNeue-Medium | 112 | centre |  |  | #000000 |
| Title, Bullets and Live Video Small | body: Body Level One (bullets) | 133, 248, 805, 732 | middle | Body Small | HelveticaNeue | 38 | left |  |  | #000000 |
| Title, Bullets and Live Video Small | video | 1242, 440, 545, 540 | | | | | | | | |
| Title, Bullets and Live Video Large | video | 1037, 248, 750, 732 | | | | | | | | |
| Title, Bullets and Live Video Large | title: Title Text | 133, 28, 1654, 180 | middle | Title | HelveticaNeue-Medium | 112 | centre |  |  | #000000 |
| Title, Bullets and Live Video Large | body: Body Level One (bullets) | 133, 248, 805, 732 | middle | Body Small | HelveticaNeue | 38 | left |  |  | #000000 |
| Bullets | body: Body Level One (bullets) | 133, 140, 1654, 800 | middle | Body | HelveticaNeue | 48 | left |  |  | #000000 |
| Photo - 3 Up | photo | 1241, 555, 583, 437 | | | | | | | | |
| Photo - 3 Up | photo | 1241, 89, 583, 437 | | | | | | | | |
| Photo - 3 Up | photo | 95, 89, 1116, 903 | | | | | | | | |
| Quote | text: –Johnny Appleseed | 188, 705, 1545, 46 | top | Attribution | HelveticaNeue-Italic | 32 | centre |  |  | #000000 |
| Quote | text: “Type a quote here.” | 188, 511, 1545, 65 | middle | Quote | HelveticaNeue-Medium | 48 | centre |  |  | #000000 |
| Photo | photo | 0, 0, 1920, 1080 | | | | | | | | |
| Blank | (empty) | | | | | | | | | |

## Black

| Layout | Element | Frame | valign | Style | Font | Size | Align | Spacing | Tracking | Colour |
|---|---|---|---|---|---|---|---|---|---|---|
| Title | title: Title Text | 140, 181, 1640, 366 | bottom | Title | HelveticaNeue-Medium | 112 | centre |  |  | #FFFFFF |
| Title | body: Body Level One | 140, 557, 1640, 125 | top | Subtitle | HelveticaNeue | 54 | centre |  |  | #FFFFFF |
| Photo - Horizontal | photo | 246.1, 53, 1428, 688 | | | | | | | | |
| Photo - Horizontal | title: Title Text | 50, 749, 1820, 158 | middle | Title | HelveticaNeue-Medium | 112 | centre |  |  | #FFFFFF |
| Photo - Horizontal | body: Body Level One | 50, 901, 1820, 125 | top | Subtitle | HelveticaNeue | 54 | centre |  |  | #FFFFFF |
| Title - Centre | title: Title Text | 140, 357, 1640, 366 | middle | Title | HelveticaNeue-Medium | 112 | centre |  |  | #FFFFFF |
| Photo - Vertical | photo | 1037, 75, 750, 903 | | | | | | | | |
| Photo - Vertical | title: Title Text | 130, 75, 805, 437 | bottom | Title Alt | HelveticaNeue-Medium | 84 | centre |  |  | #FFFFFF |
| Photo - Vertical | body: Body Level One | 130, 514, 805, 451 | top | Subtitle | HelveticaNeue | 54 | centre |  |  | #FFFFFF |
| Title - Top | title: Title Text | 133, 28, 1654, 180 | middle | Title | HelveticaNeue-Medium | 112 | centre |  |  | #FFFFFF |
| Title and Bullets | title: Title Text | 133, 28, 1654, 180 | middle | Title | HelveticaNeue-Medium | 112 | centre |  |  | #FFFFFF |
| Title and Bullets | body: Body Level One (bullets) | 133, 248, 1654, 732 | middle | Body | HelveticaNeue | 48 | left |  |  | #FFFFFF |
| Title, Bullets and Photo | photo | 1037, 248, 750, 732 | | | | | | | | |
| Title, Bullets and Photo | title: Title Text | 133, 28, 1654, 180 | middle | Title | HelveticaNeue-Medium | 112 | centre |  |  | #FFFFFF |
| Title, Bullets and Photo | body: Body Level One (bullets) | 133, 248, 805, 732 | middle | Body Small | HelveticaNeue | 38 | left |  |  | #FFFFFF |
| Title, Bullets and Live Video Small | title: Title Text | 133, 28, 1654, 180 | middle | Title | HelveticaNeue-Medium | 112 | centre |  |  | #FFFFFF |
| Title, Bullets and Live Video Small | body: Body Level One (bullets) | 133, 248, 805, 732 | middle | Body Small | HelveticaNeue | 38 | left |  |  | #FFFFFF |
| Title, Bullets and Live Video Small | video | 1297, 490, 490, 490 | | | | | | | | |
| Title, Bullets and Live Video Large | title: Title Text | 133, 28, 1654, 180 | middle | Title | HelveticaNeue-Medium | 112 | centre |  |  | #FFFFFF |
| Title, Bullets and Live Video Large | body: Body Level One (bullets) | 133, 248, 805, 732 | middle | Body Small | HelveticaNeue | 38 | left |  |  | #FFFFFF |
| Title, Bullets and Live Video Large | video | 1037, 248, 750, 732 | | | | | | | | |
| Bullets | body: Body Level One (bullets) | 133, 140, 1654, 800 | middle | Body | HelveticaNeue | 48 | left |  |  | #FFFFFF |
| Photo - 3 Up | photo | 1241, 541, 583, 437 | | | | | | | | |
| Photo - 3 Up | photo | 1241, 75, 583, 437 | | | | | | | | |
| Photo - 3 Up | photo | 95, 75, 1116, 903 | | | | | | | | |
| Quote | text: –Johnny Appleseed | 188, 705, 1545, 46 | top | Attribution | HelveticaNeue-Italic | 32 | centre |  |  | #FFFFFF |
| Quote | text: “Type a quote here.” | 188, 511, 1545, 65 | middle | Quote | HelveticaNeue-Medium | 48 | centre |  |  | #FFFFFF |
| Photo | photo | 0, 0, 1920, 1080 | | | | | | | | |
| Blank | (empty) | | | | | | | | | |

# Standard (4:3)

Out of each theme's `Templates/<theme>/Standard.kth`. Keynote draws its Standard masters on a 1024x768 slide, and the frames and sizes below are in that space. Cupboard's Standard is 1440x1080, so `BasicStandardLayouts.kt` holds every frame and size times 1440/1024 (1.40625), with frames again moved to where Keynote's ink lands (measured against Keynote's own 1024x768 exports, within 2px at 1440). Faces, tracking, line spacing and colours are as in the Wide tables; only geometry and sizes differ. Layout names, element order and slots match the Wide set one for one.

## Basic White (Standard)

| Layout | Element | Frame | valign | Style | Font | Size | Align |
|---|---|---|---|---|---|---|---|
| Title | text: Author and Date | 55, 681.7, 914, 36.3 | bottom | Heading | HelveticaNeue-Bold | 24 | left |
| Title | title: Presentation Title | 55, 146, 914.1, 260 | bottom | Title | HelveticaNeue-Bold | 82 | left |
| Title | body: Presentation Subtitle | 55, 402, 914, 114.7 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title and Photo | photo | 0, 0, 1024, 768 | | | | | |
| Title and Photo | title: Presentation Title | 55, 408, 914, 260 | bottom | Title | HelveticaNeue-Bold | 82 | left |
| Title and Photo | body: Presentation Subtitle | 55, 664, 914, 54.3 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title and Photo | text: Author and Date | 55, 45, 914, 36.3 | top | Heading | HelveticaNeue-Bold | 24 | left |
| Title and Photo Alt | photo | 512, 54.5, 456.2, 657.8 | | | | | |
| Title and Photo Alt | body: Slide Subtitle | 55, 394, 402, 318.5 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title and Photo Alt | title: Slide Title | 55, 54.5, 402, 345.5 | bottom | Title Small | HelveticaNeue-Bold | 60 | left |
| Title and Bullets | body: Slide bullet text (bullets) | 55, 233, 914, 480 | top | Body | HelveticaNeue | 30 | left |
| Title and Bullets | text: Slide Subtitle | 55, 111.3, 914, 52.9 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title and Bullets | title: Slide Title | 55, 34.7, 914, 80 | top | Title Small | HelveticaNeue-Bold | 60 | left |
| Bullets | body: Slide bullet text (bullets) | 55, 233, 914, 480 | top | Body | HelveticaNeue | 30 | left |
| Title, Bullets and Photo | photo | 512, 55, 456, 658 | | | | | |
| Title, Bullets and Photo | title: Slide Title | 55, 35, 402, 80 | top | Title Small | HelveticaNeue-Bold | 60 | left |
| Title, Bullets and Photo | text: Slide Subtitle | 55, 111.3, 402, 52.9 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title, Bullets and Photo | body: Slide bullet text (bullets) | 55, 274, 402, 440.4 | top | Body | HelveticaNeue | 30 | left |
| Title, Bullets and Live Video Small | title: Slide Title | 55, 35, 402, 80 | top | Title Small | HelveticaNeue-Bold | 60 | left |
| Title, Bullets and Live Video Small | text: Slide Subtitle | 55, 111.3, 402, 52.9 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title, Bullets and Live Video Small | body: Slide bullet text (bullets) | 55, 274, 402, 440.4 | top | Body | HelveticaNeue | 30 | left |
| Title, Bullets and Live Video Small | video | 618, 364, 350, 350 | | | | | |
| Title, Bullets and Live Video Large | title: Slide Title | 55, 35, 402, 80 | top | Title Small | HelveticaNeue-Bold | 60 | left |
| Title, Bullets and Live Video Large | text: Slide Subtitle | 55, 111.3, 402, 52.9 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title, Bullets and Live Video Large | body: Slide bullet text (bullets) | 55, 274, 402, 440.4 | top | Body | HelveticaNeue | 30 | left |
| Title, Bullets and Live Video Large | video | 512, 55, 456, 658 | | | | | |
| Section | title: Section Title | 55, 254, 914, 260 | middle | Section | HelveticaNeue-Medium | 82 | left |
| Title Only | title: Slide Title | 55, 34.7, 914, 80 | top | Title Small | HelveticaNeue-Bold | 60 | left |
| Title Only | text: Slide Subtitle | 55, 111.3, 914, 52.9 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Agenda | title: Agenda Title | 55, 35, 914, 80 | top | Title Small | HelveticaNeue-Bold | 60 | left |
| Agenda | text: Agenda Subtitle | 55, 111, 914, 52.9 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Agenda | body: Agenda Topics | 55, 233, 914, 480 | top | Agenda | HelveticaNeue | 38 | left |
| Statement | body: Statement | 55, 281, 914, 206.1 | middle | Statement | HelveticaNeue-Medium | 82 | centre |
| Big Fact | text: Fact information | 55, 489, 914, 52.9 | top | Subtitle Alt | HelveticaNeue-Bold | 38 | centre |
| Big Fact | body: 100% | 55, 78.7, 914, 410.3 | bottom | Fact | HelveticaNeue-Bold | 176 | centre |
| Quote | body: “Notable Quote” | 58, 293, 908, 183 | middle | Quote | HelveticaNeue-Medium | 60 | left |
| Quote | text: Attribution | 96, 506, 870, 36.3 | top | Attribution | HelveticaNeue-Bold | 24 | left |
| Photo - 3 Up | photo | 55, 55.2, 439, 658.3 | | | | | |
| Photo - 3 Up | photo | 529.5, 55, 440, 312 | | | | | |
| Photo - 3 Up | photo | 529.5, 401.5, 440, 312 | | | | | |
| Photo | photo | 0, 0, 1024, 768 | | | | | |
| Blank | (empty) | | | | | | |

## Basic Black (Standard)

| Layout | Element | Frame | valign | Style | Font | Size | Align |
|---|---|---|---|---|---|---|---|
| Title | text: Author and Date | 55, 679.7, 914, 36.3 | bottom | Heading | HelveticaNeue-Bold | 24 | left |
| Title | title: Presentation Title | 55, 145.9, 914.1, 260 | bottom | Title | HelveticaNeue-Bold | 82 | left |
| Title | body: Presentation Subtitle | 55, 402, 914, 113.5 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title and Photo | photo | 0, 0, 1024, 768 | | | | | |
| Title and Photo | title: Presentation Title | 55, 408, 914, 260 | bottom | Title | HelveticaNeue-Bold | 82 | left |
| Title and Photo | body: Presentation Subtitle | 55, 664, 914, 56.4 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title and Photo | text: Author and Date | 55, 45, 914, 36.3 | top | Heading | HelveticaNeue-Bold | 24 | left |
| Title and Photo Alt | title: Slide Title | 55, 54.5, 402, 345.5 | bottom | Title Small | HelveticaNeue-Bold | 60 | left |
| Title and Photo Alt | body: Slide Subtitle | 55, 394, 402, 318.5 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title and Photo Alt | photo | 512, 55, 456, 658 | | | | | |
| Title and Bullets | title: Slide Title | 55, 34.7, 914, 80 | top | Title Small | HelveticaNeue-Bold | 60 | left |
| Title and Bullets | text: Slide Subtitle | 55, 111.3, 914, 52.9 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title and Bullets | body: Slide bullet text (bullets) | 55, 232.8, 914, 480 | top | Body | HelveticaNeue | 30 | left |
| Bullets | body: Slide bullet text (bullets) | 55, 233, 914, 480 | top | Body | HelveticaNeue | 30 | left |
| Title, Bullets and Photo | title: Slide Title | 55, 35, 402, 80 | top | Title Small | HelveticaNeue-Bold | 60 | left |
| Title, Bullets and Photo | text: Slide Subtitle | 55, 111.3, 402, 52.9 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title, Bullets and Photo | body: Slide bullet text (bullets) | 55, 274, 402, 440 | top | Body | HelveticaNeue | 30 | left |
| Title, Bullets and Photo | photo | 512, 55, 456, 658 | | | | | |
| Title, Bullets and Live Video Small | title: Slide Title | 55, 35, 402, 80 | top | Title Small | HelveticaNeue-Bold | 60 | left |
| Title, Bullets and Live Video Small | text: Slide Subtitle | 55, 111.3, 402, 52.9 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title, Bullets and Live Video Small | body: Slide bullet text (bullets) | 55, 274, 402, 440 | top | Body | HelveticaNeue | 30 | left |
| Title, Bullets and Live Video Small | video | 618.2, 363, 350, 350 | | | | | |
| Title, Bullets and Live Video Large | title: Slide Title | 55, 35, 402, 80 | top | Title Small | HelveticaNeue-Bold | 60 | left |
| Title, Bullets and Live Video Large | text: Slide Subtitle | 55, 111.3, 402, 52.9 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Title, Bullets and Live Video Large | body: Slide bullet text (bullets) | 55, 274, 402, 440 | top | Body | HelveticaNeue | 30 | left |
| Title, Bullets and Live Video Large | video | 512, 55, 456, 658 | | | | | |
| Section | title: Section Title | 55, 254, 914, 260 | middle | Section | HelveticaNeue-Medium | 82 | left |
| Title Only | title: Slide Title | 55, 34.7, 914, 80 | top | Title Small | HelveticaNeue-Bold | 60 | left |
| Title Only | text: Slide Subtitle | 55, 111.3, 914, 52.9 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Agenda | title: Agenda Title | 55, 35, 914, 80 | top | Title Small | HelveticaNeue-Bold | 60 | left |
| Agenda | text: Agenda Subtitle | 55, 111, 914, 52.9 | top | Subtitle | HelveticaNeue-Bold | 38 | left |
| Agenda | body: Agenda Topics | 55, 233, 914, 480 | top | Agenda | HelveticaNeue | 38 | left |
| Statement | body: Statement | 55, 281, 914, 206.1 | middle | Statement | HelveticaNeue-Medium | 82 | centre |
| Big Fact | text: Fact information | 55, 489, 914, 52.9 | top | Subtitle Alt | HelveticaNeue-Bold | 38 | centre |
| Big Fact | body: 100% | 55, 45.1, 914, 443.9 | bottom | Fact | HelveticaNeue-Bold | 176 | centre |
| Quote | body: “Notable Quote” | 58, 293, 908, 183 | middle | Quote | HelveticaNeue-Medium | 60 | left |
| Quote | text: Attribution | 96, 506, 870, 36.3 | top | Attribution | HelveticaNeue-Bold | 24 | left |
| Photo - 3 Up | photo | 530, 401.6, 440, 312 | | | | | |
| Photo - 3 Up | photo | 55, 55, 438.4, 658.5 | | | | | |
| Photo - 3 Up | photo | 530, 55, 440, 312 | | | | | |
| Photo | photo | 0, 0, 1024, 768 | | | | | |
| Blank | (empty) | | | | | | |

## Classic White (Standard)

| Layout | Element | Frame | valign | Style | Font | Size | Align |
|---|---|---|---|---|---|---|---|
| Title | title: Presentation Title | 56, 173, 912, 260 | bottom | Title | Canela-Bold | 82 | centre |
| Title | body: Presentation Subtitle | 56, 420, 912, 114.6 | top | Subtitle | Graphik-Semibold | 38 | centre |
| Title | text: Author and Date | 56, 662.3, 912, 33.8 | top | Heading | Graphik-Medium | 20 | centre |
| Title and Photo | photo | 0, 0, 1024, 768 | | | | | |
| Title and Photo | title: Presentation Title | 56, 173, 912, 260 | bottom | Title Alt | Canela-Bold | 82 | centre |
| Title and Photo | body: Presentation Subtitle | 56, 420, 912, 114.7 | top | Subtitle Alt | Graphik-Semibold | 38 | centre |
| Title and Photo | text: Author and Date | 56, 662, 912, 33.8 | top | Heading Alt | Graphik-Medium | 20 | centre |
| Title and Photo Alt | photo | 512, 60, 452, 624 | | | | | |
| Title and Photo Alt | title: Slide Title | 56, 224.6, 398.3, 164.4 | bottom | Title Small | Canela-Bold | 58 | centre |
| Title and Photo Alt | body: Slide Subtitle | 56, 376, 398.3, 308 | top | Subtitle Small | Graphik-Semibold | 32 | centre |
| Title and Bullets | body: Slide bullet text (bullets) | 56, 236, 912, 476 | top | Body | CanelaText-Regular | 30 | left |
| Title and Bullets | title: Slide Title | 56, 31.3, 912, 92 | top | Title Small | Canela-Bold | 58 | centre |
| Title and Bullets | text: Slide Subtitle | 56, 118.5, 912, 49.7 | top | Subtitle Small | Graphik-Semibold | 32 | centre |
| Bullets | body: Slide bullet text (bullets) | 56, 236, 912, 476 | top | Body | CanelaText-Regular | 30 | left |
| Title, Bullets and Photo | title: Slide Title | 55.7, 29.1, 398, 159.9 | bottom | Title Small | Canela-Bold | 58 | centre |
| Title, Bullets and Photo | body: Slide bullet text (bullets) | 56, 268.7, 398, 414.7 | top | Body | CanelaText-Regular | 30 | left |
| Title, Bullets and Photo | text: Slide Subtitle | 56, 178.7, 398, 49.7 | middle | Subtitle Small | Graphik-Semibold | 32 | centre |
| Title, Bullets and Photo | photo | 512, 60, 452, 624 | | | | | |
| Title, Bullets and Live Video Small | title: Slide Title | 55.7, 29.1, 398, 159.9 | bottom | Title Small | Canela-Bold | 58 | centre |
| Title, Bullets and Live Video Small | body: Slide bullet text (bullets) | 56, 268.7, 398, 414.7 | top | Body | CanelaText-Regular | 30 | left |
| Title, Bullets and Live Video Small | text: Slide Subtitle | 56, 178.7, 398, 49.7 | middle | Subtitle Small | Graphik-Semibold | 32 | centre |
| Title, Bullets and Live Video Small | video | 614, 334, 350, 350 | | | | | |
| Title, Bullets and Live Video Large | title: Slide Title | 55.7, 29.1, 398, 159.9 | bottom | Title Small | Canela-Bold | 58 | centre |
| Title, Bullets and Live Video Large | body: Slide bullet text (bullets) | 56, 268.7, 398, 414.7 | top | Body | CanelaText-Regular | 30 | left |
| Title, Bullets and Live Video Large | text: Slide Subtitle | 56, 178.7, 398, 49.7 | middle | Subtitle Small | Graphik-Semibold | 32 | centre |
| Title, Bullets and Live Video Large | video | 512, 60, 452, 624 | | | | | |
| Section | title: Section Title | 56, 193, 912, 350 | middle | Section | Canela-Bold | 82 | centre |
| Title Only | title: Slide Title | 56, 31, 912, 92 | middle | Title Small | Canela-Bold | 58 | centre |
| Title Only | text: Slide Subtitle | 56, 118.5, 912, 49.7 | top | Subtitle Small | Graphik-Semibold | 32 | centre |
| Agenda | body: Agenda Topics | 56, 236, 912, 476 | top | Agenda | CanelaDeck-Regular | 44 | left |
| Agenda | title: Agenda Title | 56, 31, 912, 92 | top | Title Small | Canela-Bold | 58 | centre |
| Agenda | text: Agenda Subtitle | 56, 118.5, 912, 49.7 | top | Subtitle Small | Graphik-Semibold | 32 | centre |
| Statement | body: Statement | 56, 193, 912, 350 | middle | Statement | Canela-Regular | 82 | centre |
| Big Fact | body: 100% | 56, 151.7, 912, 327.3 | bottom | Fact | Canela-Regular | 158 | centre |
| Big Fact | text: Fact information | 56, 442.9, 912, 49.7 | top | Subtitle Small | Graphik-Semibold | 32 | centre |
| Quote | text: Attribution | 56, 566.3, 912, 49.7 | middle | Attribution | Graphik-Semibold | 32 | centre |
| Quote | body: “Notable Quote” | 56, 216, 912, 285 | middle | Quote | Canela-Bold | 58 | centre |
| Photo - 3 Up | photo | 524.6, 60, 440, 300 | | | | | |
| Photo - 3 Up | photo | 60.4, 60, 439.6, 624.3 | | | | | |
| Photo - 3 Up | photo | 524.6, 384.4, 440, 300 | | | | | |
| Photo | photo | 60, 60, 904, 624 | | | | | |
| Blank | (empty) | | | | | | |

## White (Standard)

| Layout | Element | Frame | valign | Style | Font | Size | Align |
|---|---|---|---|---|---|---|---|
| Title | title: Title Text | 100, 129, 824, 260 | bottom | Title | HelveticaNeue-Medium | 80 | centre |
| Title | body: Body Level One | 100, 397, 824, 89 | top | Subtitle | HelveticaNeue | 37 | centre |
| Photo - Horizontal | photo | 128, 53, 768, 465 | | | | | |
| Photo - Horizontal | title: Title Text | 100, 529, 824, 112 | bottom | Title | HelveticaNeue-Medium | 80 | centre |
| Photo - Horizontal | body: Body Level One | 100, 642, 824, 89 | top | Subtitle | HelveticaNeue | 37 | centre |
| Title - Centre | title: Title Text | 100, 254, 824, 260 | middle | Title | HelveticaNeue-Medium | 80 | centre |
| Photo - Vertical | photo | 529, 50, 420, 647 | | | | | |
| Photo - Vertical | title: Title Text | 75, 50, 420, 314 | bottom | Title Alt | HelveticaNeue-Medium | 60 | centre |
| Photo - Vertical | body: Body Level One | 75, 372, 420, 324 | top | Subtitle | HelveticaNeue | 37 | centre |
| Title - Top | title: Title Text | 75, 20, 874, 170 | middle | Title | HelveticaNeue-Medium | 80 | centre |
| Title and Bullets | title: Title Text | 75, 20, 874, 170 | middle | Title | HelveticaNeue-Medium | 80 | centre |
| Title and Bullets | body: Body Level One (bullets) | 75, 204, 874, 495 | middle | Body | HelveticaNeue | 32 | left |
| Title, Bullets and Photo | photo | 529, 204, 420, 495 | | | | | |
| Title, Bullets and Photo | title: Title Text | 75, 20, 874, 170 | middle | Title | HelveticaNeue-Medium | 80 | centre |
| Title, Bullets and Photo | body: Body Level One (bullets) | 75, 204, 420, 495 | middle | Body Small | HelveticaNeue | 28 | left |
| Title, Bullets and Live Video Small | title: Title Text | 75, 20, 874, 170 | middle | Title | HelveticaNeue-Medium | 80 | centre |
| Title, Bullets and Live Video Small | body: Body Level One (bullets) | 75, 204, 420, 495 | middle | Body Small | HelveticaNeue | 28 | left |
| Title, Bullets and Live Video Small | video | 619, 372, 330, 327 | | | | | |
| Title, Bullets and Live Video Large | video | 529, 204, 420, 495 | | | | | |
| Title, Bullets and Live Video Large | title: Title Text | 75, 20, 874, 170 | middle | Title | HelveticaNeue-Medium | 80 | centre |
| Title, Bullets and Live Video Large | body: Body Level One (bullets) | 75, 204, 420, 495 | middle | Body Small | HelveticaNeue | 28 | left |
| Bullets | body: Body Level One (bullets) | 75, 100, 874, 568 | middle | Body | HelveticaNeue | 32 | left |
| Photo - 3 Up | photo | 529, 401, 420, 297 | | | | | |
| Photo - 3 Up | photo | 529, 70, 420, 297 | | | | | |
| Photo - 3 Up | photo | 75, 70, 420, 628 | | | | | |
| Quote | text: –Johnny Appleseed | 100, 501, 824, 36 | top | Attribution | HelveticaNeue-Italic | 24 | centre |
| Quote | text: “Type a quote here.” | 100, 360, 824, 48 | middle | Quote | HelveticaNeue-Medium | 34 | centre |
| Photo | photo | 0, 0, 1024, 768 | | | | | |
| Blank | (empty) | | | | | | |

## Black (Standard)

| Layout | Element | Frame | valign | Style | Font | Size | Align |
|---|---|---|---|---|---|---|---|
| Title | title: Title Text | 100, 129, 824, 260 | bottom | Title | HelveticaNeue-Medium | 80 | centre |
| Title | body: Body Level One | 100, 396, 824, 89 | top | Subtitle | HelveticaNeue | 37 | centre |
| Photo - Horizontal | photo | 127.8, 53.5, 768.3, 465 | | | | | |
| Photo - Horizontal | title: Title Text | 100, 529, 824, 112 | middle | Title | HelveticaNeue-Medium | 80 | centre |
| Photo - Horizontal | body: Body Level One | 100, 642, 824, 89 | top | Subtitle | HelveticaNeue | 37 | centre |
| Title - Centre | title: Title Text | 100, 254, 824, 260 | middle | Title | HelveticaNeue-Medium | 80 | centre |
| Photo - Vertical | photo | 529, 50.3, 420, 647 | | | | | |
| Photo - Vertical | title: Title Text | 75, 50, 420, 314 | bottom | Title Alt | HelveticaNeue-Medium | 60 | centre |
| Photo - Vertical | body: Body Level One | 75, 372, 420, 324 | top | Subtitle | HelveticaNeue | 37 | centre |
| Title - Top | title: Title Text | 75, 20, 874, 170 | middle | Title | HelveticaNeue-Medium | 80 | centre |
| Title and Bullets | title: Title Text | 75, 20, 874, 170 | middle | Title | HelveticaNeue-Medium | 80 | centre |
| Title and Bullets | body: Body Level One (bullets) | 75, 204, 874, 495 | middle | Body | HelveticaNeue | 32 | left |
| Title, Bullets and Photo | photo | 529, 204, 420, 495 | | | | | |
| Title, Bullets and Photo | title: Title Text | 75, 20, 874, 170 | middle | Title | HelveticaNeue-Medium | 80 | centre |
| Title, Bullets and Photo | body: Body Level One (bullets) | 75, 204, 420, 495 | middle | Body Small | HelveticaNeue | 28 | left |
| Title, Bullets and Live Video Small | title: Title Text | 75, 20, 874, 170 | middle | Title | HelveticaNeue-Medium | 80 | centre |
| Title, Bullets and Live Video Small | body: Body Level One (bullets) | 75, 204, 420, 495 | middle | Body Small | HelveticaNeue | 28 | left |
| Title, Bullets and Live Video Small | video | 619, 369, 330, 330 | | | | | |
| Title, Bullets and Live Video Large | title: Title Text | 75, 20, 874, 170 | middle | Title | HelveticaNeue-Medium | 80 | centre |
| Title, Bullets and Live Video Large | body: Body Level One (bullets) | 75, 204, 420, 495 | middle | Body Small | HelveticaNeue | 28 | left |
| Title, Bullets and Live Video Large | video | 529, 204, 420, 495 | | | | | |
| Bullets | body: Body Level One (bullets) | 75, 100, 874, 568 | middle | Body | HelveticaNeue | 32 | left |
| Photo - 3 Up | photo | 530, 391, 420, 307 | | | | | |
| Photo - 3 Up | photo | 530, 50, 420, 307 | | | | | |
| Photo - 3 Up | photo | 75, 50, 420, 648 | | | | | |
| Quote | text: –Johnny Appleseed | 100, 501, 824, 36 | top | Attribution | HelveticaNeue-Italic | 24 | centre |
| Quote | text: “Type a quote here.” | 100, 363.3, 824, 48 | middle | Quote | HelveticaNeue-Medium | 34 | centre |
| Photo | photo | 0, 0, 1024, 768 | | | | | |
| Blank | (empty) | | | | | | |
