package io.github.xxfast.cupboard.canvas

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import io.github.xxfast.cupboard.play.FrameReporter
import kotlin.experimental.ExperimentalNativeApi
import platform.CoreGraphics.CGDisplayCopyDisplayMode
import platform.CoreGraphics.CGDisplayModeGetRefreshRate
import platform.CoreGraphics.CGDisplayModeRelease
import platform.CoreGraphics.CGMainDisplayID
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.writeData
import platform.posix.getenv

/**
 * Where the show's frame reports go: `~/Library/Logs/Cupboard/frames.log`,
 * which Console.app lists and a terminal can `tail -f`. A file rather than the
 * system log, which hides everything NSLog says as private.
 *
 * On in a Debug build (Cupboard Dev) and in any build launched with
 * `CUPBOARD_FRAMES` set, which is how a Release build gets measured: Debug's
 * unoptimised framework misses frames Release doesn't.
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
internal val frameLog: FrameReporter? =
    if (Platform.isDebugBinary || getenv("CUPBOARD_FRAMES") != null) {
        // The main display's rate: 120 on a ProMotion panel, which is what a
        // show running smooth draws at there. 60 where it won't say.
        val refresh: Int = CGDisplayCopyDisplayMode(CGMainDisplayID())
            ?.let { mode -> CGDisplayModeGetRefreshRate(mode).also { CGDisplayModeRelease(mode) } }
            ?.toInt()
            ?.takeIf { it > 0 }
            ?: 60
        FrameReporter(refresh, ::appendFrameLine)
    } else null

private val frameLogPath: String = "${NSHomeDirectory()}/Library/Logs/Cupboard/frames.log"

private val stamp = NSDateFormatter().apply { dateFormat = "HH:mm:ss.SSS" }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun appendFrameLine(line: String) {
    val files: NSFileManager = NSFileManager.defaultManager
    if (!files.fileExistsAtPath(frameLogPath)) {
        files.createDirectoryAtPath(
            frameLogPath.substringBeforeLast('/'),
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        files.createFileAtPath(frameLogPath, contents = null, attributes = null)
    }
    val handle: NSFileHandle = NSFileHandle.fileHandleForWritingAtPath(frameLogPath) ?: return
    val text = "${stamp.stringFromDate(NSDate())} $line\n"
    NSString.create(string = text).dataUsingEncoding(NSUTF8StringEncoding)?.let { data ->
        handle.seekToEndOfFile()
        handle.writeData(data)
    }
    handle.closeFile()
}
