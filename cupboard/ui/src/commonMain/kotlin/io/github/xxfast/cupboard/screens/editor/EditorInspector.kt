package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.GalleryElement
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.ObjectStyle
import io.github.xxfast.cupboard.document.PlaceholderRole
import io.github.xxfast.cupboard.document.PlaybackSettings
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.SlideSizePreset
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.editor.AlignEdge
import io.github.xxfast.cupboard.editor.Axis
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens

/**
 * The 282dp M3 inspector: Format / Animate / Document tabs over their bodies.
 * Clicking the active tab does nothing (close-on-reclick is macOS-only).
 *
 * Each tab is one panel and each panel pins its own chrome: Format pins the
 * segmented control (and the object-style strip on a shape), Animate pins its
 * segments and the Build Order button, and only what sits between them scrolls.
 * That is the whole of what this file does; the three panels live next to it, in
 * `InspectorFormat.kt`, `InspectorAnimate.kt` and `InspectorDocument.kt`, over
 * the shared controls in `InspectorControls.kt`.
 *
 * Which Format segment is showing, which Animate segment, and which sections are
 * disclosed all come off the state and go back through the loop, so none of them
 * is reset by a click on the canvas.
 *
 * The third tab is named for what it now holds. The slide's own formatting moved
 * to Format-with-nothing-selected, so what is left in [InspectorTab.Document] is
 * the deck's: its theme, its size, its playback and its background.
 */
@Composable
fun EditorInspector(
    tab: InspectorTab,
    onSelectTab: (InspectorTab) -> Unit,
    /** The selected slide, which Format falls back to and Animate transitions. */
    slide: Slide,
    onUpdateSlide: (Slide) -> Unit,
    /** An in-flight sample of [slide], for the Animate tab's duration drag. */
    onPreviewSlide: (Slide) -> Unit,
    /** What [slide] plays on its way out, null putting it back on the deck's own. */
    onSetSlideTransition: (slideId: String, transition: SlideTransition?) -> Unit,
    /** The slide's build order, all four of its verbs: the Animate tab's list. */
    onAddBuild: (Build) -> Unit,
    onUpdateBuild: (index: Int, build: Build) -> Unit,
    onRemoveBuild: (index: Int) -> Unit,
    onMoveBuild: (from: Int, to: Int) -> Unit,
    /**
     * Play this slide alone, from its first step: what the Animate tab's Preview
     * asks for. Null on a shell with nowhere to play it, which greys the button.
     */
    onPlayPreview: (() -> Unit)? = null,
    /** A build row picked: the element it animates takes the canvas selection. */
    onSelectElement: (String?) -> Unit,
    /** The deck's layouts: what the Format tab's slide fallback picks out of. */
    layouts: List<Slide>,
    /** The deck's slides, in play order: what a link to a slide picks out of. */
    slides: List<Slide>,
    /**
     * Whether [slide] is one of [layouts], being edited. Format's fallback is
     * then a layout editor: it names the layout and fills it with placeholders
     * instead of putting a slide on one.
     */
    isEditingLayouts: Boolean,
    onApplyLayout: (slideId: String, layoutId: String?) -> Unit,
    onReapplyLayout: (slideId: String) -> Unit,
    onEditSlideLayouts: () -> Unit,
    onExitSlideLayouts: () -> Unit,
    onAddPlaceholder: (PlaceholderRole) -> Unit,
    onRenameSlide: (id: String, title: String) -> Unit,
    /** Every theme the deck can be put on: the built-ins, then the user's. */
    themes: List<Theme>,
    /** The ones of [themes] the user saved, and so the only ones deletable. */
    userThemes: List<Theme>,
    /** The theme the deck is on, by name. */
    themeName: String,
    /** Behind every slide that asks for none of its own; null is the app's dark gradient. */
    documentBackground: SlideBackground?,
    /** The deck's slide shape, in document units, and the preset it is exactly,
     * null for a custom one: between them, what the size picker shows. */
    slideWidth: Float,
    slideHeight: Float,
    slideSizePreset: SlideSizePreset?,
    /** What kind of show the deck is, and the two times that go with its kinds. */
    playback: PlaybackSettings,
    onChangeTheme: (name: String) -> Unit,
    onSaveAsTheme: (name: String) -> Unit,
    onDeleteUserTheme: (name: String) -> Unit,
    onSetDocumentBackground: (SlideBackground?) -> Unit,
    onSetSlideSize: (width: Float, height: Float, scaleContent: Boolean) -> Unit,
    onSetPlayback: (settings: PlaybackSettings) -> Unit,
    /** Where the selection points, over the whole selection at once. */
    onSetElementLinks: (ids: List<String>, target: LinkTarget?) -> Unit,
    /** The deck's saved shape looks: what the Style segment's strip offers. */
    objectStyles: List<ObjectStyle>,
    onApplyObjectStyle: (ids: List<String>, styleId: String) -> Unit,
    onSaveObjectStyle: (shapeId: String, name: String) -> Unit,
    onRenameObjectStyle: (styleId: String, name: String) -> Unit,
    onDeleteObjectStyle: (styleId: String) -> Unit,
    selectedElements: List<Element>,
    onUpdateElements: (List<Element>) -> Unit,
    onPreviewElements: (List<Element>) -> Unit,
    onReorderElements: (List<String>, ZOrderMove) -> Unit,
    onSetElementsLocked: (List<String>, Boolean) -> Unit,
    onFlipElements: (List<String>, FlipAxis) -> Unit,
    onGroupElements: (List<String>) -> Unit,
    onUngroupElements: (String) -> Unit,
    /** The Arrange segment's two menus, the same events the Arrange menu sends. */
    onAlignElements: (AlignEdge) -> Unit,
    onDistributeElements: (Axis) -> Unit,
    /** The Format tab's chrome: which segments the selection offers, which of
     * them is showing, which sections are disclosed, and the two z-order flags
     * the Arrange segment greys its buttons on. All of it the core's. */
    formatSegments: List<FormatSegment>,
    activeFormatSegment: FormatSegment?,
    onSelectFormatSegment: (FormatSegment) -> Unit,
    expandedSections: Set<InspectorSection>,
    onToggleInspectorSection: (InspectorSection) -> Unit,
    animateSegment: AnimateSegment,
    onSelectAnimateSegment: (AnimateSegment) -> Unit,
    canBringForward: Boolean,
    canSendBackward: Boolean,
    /** Point the selected image at other bytes: the image segment's Replace
     * Image. Null on a shell with no file picker, which greys the button. */
    onReplaceImage: ((ImageElement) -> Unit)? = null,
    /** Pick more image files and append them to the selected gallery: the gallery
     * segment's Add Images. Null on a shell with no file picker, which greys it. */
    onAddGalleryImages: ((GalleryElement) -> Unit)? = null,
    /** Write the selected gallery's step builds onto the slide, one per image. */
    onAddElementSteps: (String) -> Unit = {},
    /** The selected code block's versions: which one the canvas shows, and the
     * four verbs of the Code segment's Versions list. All of it the core's. */
    codeVersion: Int = 0,
    onSelectCodeVersion: (Int) -> Unit = {},
    onAddCodeVersion: (String) -> Unit = {},
    onRemoveCodeVersion: (elementId: String, index: Int) -> Unit = { _, _ -> },
    onMoveCodeVersion: (elementId: String, from: Int, to: Int) -> Unit = { _, _, _ -> },
    /** The selected code block's steps: which one the inspector is editing, and
     * the five verbs of the Code segment's Steps list. All of it the core's. */
    codeStep: Int? = null,
    onSelectCodeStep: (Int?) -> Unit = {},
    onAddCodeStep: (String) -> Unit = {},
    onRemoveCodeStep: (elementId: String, index: Int) -> Unit = { _, _ -> },
    onMoveCodeStep: (elementId: String, from: Int, to: Int) -> Unit = { _, _, _ -> },
    onUpdateCodeStep: (elementId: String, index: Int, step: CodeStep) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val tabs: List<Pair<String, InspectorTab>> = listOf(
        "Format" to InspectorTab.Format,
        "Animate" to InspectorTab.Animate,
        "Document" to InspectorTab.Document,
    )
    val selectedIndex: Int = tabs.indexOfFirst { (_, target) -> target == tab }

    Column(
        modifier = modifier
            .width(282.dp)
            .fillMaxHeight()
            .background(tokens.insBg),
    ) {
        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = tokens.insBg,
            contentColor = tokens.text,
            indicator = { tabPositions -> TabIndicator(tabPositions[selectedIndex]) },
            divider = { HorizontalDivider(thickness = 1.dp, color = tokens.div) },
        ) {
            tabs.forEachIndexed { index, (title, target) ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { if (index != selectedIndex) onSelectTab(target) },
                    modifier = Modifier.height(48.dp),
                    selectedContentColor = tokens.text,
                    unselectedContentColor = tokens.dim,
                ) {
                    // The content slot, not `text`: that one pads 16dp a side,
                    // which "Document" doesn't fit in a third of the panel.
                    Text(
                        text = title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        when (tab) {
            InspectorTab.Format -> FormatPanel(
                segments = formatSegments,
                activeSegment = activeFormatSegment,
                onSelectSegment = onSelectFormatSegment,
                expandedSections = expandedSections,
                onToggleSection = onToggleInspectorSection,
                selectedElements = selectedElements,
                onUpdateElements = onUpdateElements,
                onPreviewElements = onPreviewElements,
                onReorderElements = onReorderElements,
                onSetElementsLocked = onSetElementsLocked,
                onFlipElements = onFlipElements,
                onGroupElements = onGroupElements,
                onUngroupElements = onUngroupElements,
                onAlignElements = onAlignElements,
                onDistributeElements = onDistributeElements,
                canBringForward = canBringForward,
                canSendBackward = canSendBackward,
                slides = slides,
                onSetElementLinks = onSetElementLinks,
                objectStyles = objectStyles,
                onApplyObjectStyle = onApplyObjectStyle,
                onSaveObjectStyle = onSaveObjectStyle,
                onRenameObjectStyle = onRenameObjectStyle,
                onDeleteObjectStyle = onDeleteObjectStyle,
                onReplaceImage = onReplaceImage,
                onAddGalleryImages = onAddGalleryImages,
                onAddElementSteps = onAddElementSteps,
                codeVersion = codeVersion,
                onSelectCodeVersion = onSelectCodeVersion,
                onAddCodeVersion = onAddCodeVersion,
                onRemoveCodeVersion = onRemoveCodeVersion,
                onMoveCodeVersion = onMoveCodeVersion,
                codeStep = codeStep,
                onSelectCodeStep = onSelectCodeStep,
                onAddCodeStep = onAddCodeStep,
                onRemoveCodeStep = onRemoveCodeStep,
                onMoveCodeStep = onMoveCodeStep,
                onUpdateCodeStep = onUpdateCodeStep,
                slide = slide,
                layouts = layouts,
                isEditingLayouts = isEditingLayouts,
                onUpdateSlide = onUpdateSlide,
                onApplyLayout = onApplyLayout,
                onReapplyLayout = onReapplyLayout,
                onEditSlideLayouts = onEditSlideLayouts,
                onExitSlideLayouts = onExitSlideLayouts,
                onAddPlaceholder = onAddPlaceholder,
                onRenameSlide = onRenameSlide,
            )

            InspectorTab.Animate -> AnimatePanel(
                slide = slide,
                isEditingLayouts = isEditingLayouts,
                selectedElements = selectedElements,
                animateSegment = animateSegment,
                onSelectAnimateSegment = onSelectAnimateSegment,
                onSelectElement = onSelectElement,
                onSetTransition = onSetSlideTransition,
                onPreview = onPreviewSlide,
                onUpdate = onUpdateSlide,
                onAddBuild = onAddBuild,
                onUpdateBuild = onUpdateBuild,
                onRemoveBuild = onRemoveBuild,
                onMoveBuild = onMoveBuild,
                onPlayPreview = onPlayPreview,
            )

            InspectorTab.Document -> DocumentPanel(
                themes = themes,
                userThemes = userThemes,
                themeName = themeName,
                documentBackground = documentBackground,
                slideWidth = slideWidth,
                slideHeight = slideHeight,
                slideSizePreset = slideSizePreset,
                playback = playback,
                onChangeTheme = onChangeTheme,
                onSaveAsTheme = onSaveAsTheme,
                onDeleteUserTheme = onDeleteUserTheme,
                onSetDocumentBackground = onSetDocumentBackground,
                onSetSlideSize = onSetSlideSize,
                onSetPlayback = onSetPlayback,
            )
        }
    }
}

/** The design's 52x3 rounded indicator, centered under the active tab. */
@Composable
private fun TabIndicator(position: TabPosition) {
    Box(
        Modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.BottomStart)
            .offset(x = position.left + (position.width - 52.dp) / 2)
            .size(width = 52.dp, height = 3.dp)
            .background(
                color = LocalChromeTokens.current.accent,
                shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
            ),
    )
}
