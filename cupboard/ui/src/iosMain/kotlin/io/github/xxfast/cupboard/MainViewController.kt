package io.github.xxfast.cupboard

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import io.github.xxfast.cupboard.document.inMemoryDocumentStore
import io.github.xxfast.cupboard.document.showcaseDocument
import io.github.xxfast.cupboard.screens.editor.EditorScreen
import io.github.xxfast.cupboard.screens.editor.EditorViewModel

/**
 * Preview shell: the showcase deck in an in-memory store. iOS is a later phase,
 * so there is no document file here yet, nothing edited on this target survives.
 */
fun MainViewController() = ComposeUIViewController {
    val viewModel = remember { EditorViewModel(showcaseDocument(), inMemoryDocumentStore()) }
    EditorScreen(viewModel)
}