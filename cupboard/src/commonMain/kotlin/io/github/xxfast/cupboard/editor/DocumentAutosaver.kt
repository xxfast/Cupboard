package io.github.xxfast.cupboard.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.kstore.KStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Persists this store's document to [store], debounced by [debounce].
 *
 * Interim persistence: one whole [Document] as a single JSON file, no history,
 * no assets on the side. The real `.cupboard` bundle format comes later, and
 * this goes away with it.
 *
 * Rides [EditorStore.subscribe], so [EditorStore] itself stays free of
 * coroutines and IO. Every change starts the debounce again, which means a run
 * of keystrokes costs one write. Selection-only changes notify too and so also
 * trigger a write of an unchanged document; that's fine, writes are cheap and
 * debounced, and filtering would mean diffing the document on every intent.
 *
 * [scope] should outlive the editor. The returned function unsubscribes and
 * drops any write still waiting out its debounce, so the last edit before a
 * stop may not reach disk.
 */
fun EditorStore.autosaveTo(
    store: KStore<Document>,
    scope: CoroutineScope,
    debounce: Duration = 500.milliseconds,
): () -> Unit {
    var pending: Job? = null

    val unsubscribe: () -> Unit = subscribe {
        pending?.cancel()
        pending = scope.launch {
            delay(debounce)
            store.set(document)
        }
    }

    return {
        unsubscribe()
        pending?.cancel()
        pending = null
    }
}
