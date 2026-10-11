package io.github.xxfast.cupboard.document

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemPathSeparator
import platform.windows.CancelIoEx
import platform.windows.CloseHandle
import platform.windows.CreateEventW
import platform.windows.CreateFileW
import platform.windows.ERROR_IO_PENDING
import platform.windows.ERROR_NOTIFY_ENUM_DIR
import platform.windows.FALSE
import platform.windows.FILE_FLAG_BACKUP_SEMANTICS
import platform.windows.FILE_FLAG_OVERLAPPED
import platform.windows.FILE_LIST_DIRECTORY
import platform.windows.FILE_NOTIFY_CHANGE_DIR_NAME
import platform.windows.FILE_NOTIFY_CHANGE_FILE_NAME
import platform.windows.FILE_NOTIFY_CHANGE_LAST_WRITE
import platform.windows.FILE_NOTIFY_CHANGE_SIZE
import platform.windows.FILE_NOTIFY_INFORMATION
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GetLastError
import platform.windows.GetOverlappedResult
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.OPEN_EXISTING
import platform.windows.OVERLAPPED
import platform.windows.ReadDirectoryChangesW
import platform.windows.ResetEvent
import platform.windows.TRUE
import platform.windows.WAIT_OBJECT_0
import platform.windows.WAIT_TIMEOUT
import platform.windows.WaitForSingleObject

/**
 * Over `ReadDirectoryChangesW`, overlapped, watching the whole subtree: that
 * covers `assets/` without registering it, so an `assets/` made after the
 * watch starts is seen with nothing extra to do.
 *
 * Waited on a quarter second at a time rather than with `INFINITE`, for the
 * same reason the JVM actual polls: a blocked wait is a thread cancellation
 * cannot reach, and a closed editor must not leave one behind.
 *
 * Compile-verified only. Nothing has run it until the WinUI shell does (see
 * `winuiApp/README.md`), so every call here is meant to be checked line by line
 * against the Win32 docs.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun fileChanges(directory: Path): Flow<Path> = flow {
    // FILE_FLAG_BACKUP_SEMANTICS is what lets CreateFileW open a directory at
    // all. Sharing everything, delete included, so the watch never stops a
    // tool or a checkout from rewriting or removing the bundle under us.
    val folder: HANDLE? = CreateFileW(
        directory.toString(),
        FILE_LIST_DIRECTORY.toUInt(),
        (FILE_SHARE_READ or FILE_SHARE_WRITE or FILE_SHARE_DELETE).toUInt(),
        null,
        OPEN_EXISTING.toUInt(),
        (FILE_FLAG_BACKUP_SEMANTICS or FILE_FLAG_OVERLAPPED).toUInt(),
        null,
    )
    if (folder == INVALID_HANDLE_VALUE) {
        throw IOException("Cannot watch $directory: error ${GetLastError()}")
    }

    // Manual-reset, as GetOverlappedResult expects of an OVERLAPPED's event.
    val event: HANDLE? = CreateEventW(null, TRUE, FALSE, null)

    try {
        if (event == null) throw IOException("Cannot watch $directory: error ${GetLastError()}")

        // The buffer and the OVERLAPPED belong to the kernel while a read is
        // outstanding, so both live in this scope, and the scope does not end
        // until the `finally` below has seen the read settle. Allocated as
        // DWORDs because ReadDirectoryChangesW wants a DWORD-aligned buffer, and
        // a ByteArray's payload makes no promise about that.
        memScoped {
            val overlapped: OVERLAPPED = alloc<OVERLAPPED>()
            overlapped.hEvent = event
            val transferred: UIntVar = alloc<UIntVar>()
            val buffer: CPointer<ByteVar> = allocArray<UIntVar>(BUFFER_BYTES / 4)
                .reinterpret<ByteVar>()
            var pending = false

            try {
                while (true) {
                    ResetEvent(event)
                    val queued: Int = ReadDirectoryChangesW(
                        folder,
                        buffer,
                        BUFFER_BYTES.toUInt(),
                        TRUE,
                        NOTIFY_FILTER,
                        null,
                        overlapped.ptr,
                        null,
                    )

                    // A failed read here is the bundle gone from under the
                    // handle. Same as a JVM key that will not reset: nothing more
                    // comes from this folder, and the editor keeps what it has
                    // rather than having its loop torn down by a throw.
                    val issued: Boolean =
                        queued != FALSE || GetLastError() == ERROR_IO_PENDING.toUInt()
                    if (!issued) return@memScoped
                    pending = true

                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val waited: UInt = WaitForSingleObject(event, POLL_MILLIS)
                        if (waited == WAIT_OBJECT_0) break
                        if (waited != WAIT_TIMEOUT.toUInt()) return@memScoped
                    }

                    val read: Int =
                        GetOverlappedResult(folder, overlapped.ptr, transferred.ptr, FALSE)
                    pending = false

                    // Nothing back means the kernel's own buffer overflowed and
                    // the names are lost: all that is left to say is that
                    // something changed, somewhere in the bundle, assets included.
                    val overflowed: Boolean =
                        read == FALSE && GetLastError() == ERROR_NOTIFY_ENUM_DIR.toUInt()
                    if (read == FALSE && !overflowed) return@memScoped

                    if (overflowed || transferred.value == 0u) {
                        emit(directory)
                        emit(CupboardBundle.assetsDirectory(directory))
                        continue
                    }

                    // Read out whole before anything is emitted: the next read
                    // reuses the buffer, and the kernel holds what happens in
                    // between until it is issued.
                    val changed: MutableList<Path> = mutableListOf()
                    var offset = 0
                    while (true) {
                        val info: FILE_NOTIFY_INFORMATION = (buffer + offset)!!
                            .reinterpret<FILE_NOTIFY_INFORMATION>()
                            .pointed

                        // UTF-16, counted in bytes, with no terminator to stop at.
                        val units: Int = (info.FileNameLength / 2u).toInt()
                        val name: String = CharArray(units) { index -> Char(info.FileName[index]) }
                            .concatToString()

                        // Relative, backslash-separated. That is already the
                        // separator on Windows, and watchBundle matches `assets/`
                        // by prefix on SystemPathSeparator, so make it so if not.
                        changed += Path(directory, name.replace('\\', SystemPathSeparator))

                        if (info.NextEntryOffset == 0u) break
                        offset += info.NextEntryOffset.toInt()
                    }

                    for (path in changed) emit(path)
                }
            } finally {
                // The kernel still writes into the buffer and the OVERLAPPED
                // until the read settles, so cancel it and wait that out before
                // memScoped frees either. Cancelled reads settle promptly.
                if (pending) {
                    CancelIoEx(folder, overlapped.ptr)
                    GetOverlappedResult(folder, overlapped.ptr, transferred.ptr, TRUE)
                }
            }
        }
    } finally {
        if (event != null) CloseHandle(event)
        CloseHandle(folder)
    }
}.flowOn(Dispatchers.IO)

/** The documented ceiling for a watch over the network, and plenty for a bundle. */
private const val BUFFER_BYTES: Int = 64 * 1024

private const val POLL_MILLIS: UInt = 250u

private val NOTIFY_FILTER: UInt = (
    FILE_NOTIFY_CHANGE_FILE_NAME or
        FILE_NOTIFY_CHANGE_DIR_NAME or
        FILE_NOTIFY_CHANGE_SIZE or
        FILE_NOTIFY_CHANGE_LAST_WRITE
    ).toUInt()
