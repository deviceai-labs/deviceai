package dev.deviceai.models

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
actual fun appendToFile(path: String, data: ByteArray, length: Int) {
    val fileHandle = NSFileHandle.fileHandleForWritingAtPath(path)
    if (fileHandle != null) {
        fileHandle.seekToEndOfFile()
        data.usePinned { pinned ->
            val nsData = NSData.dataWithBytes(pinned.addressOf(0), length.toULong())
            fileHandle.writeData(nsData)
        }
        fileHandle.closeFile()
    } else {
        // File doesn't exist yet, create it
        val fm = NSFileManager.defaultManager
        data.usePinned { pinned ->
            val nsData = NSData.dataWithBytes(pinned.addressOf(0), length.toULong())
            fm.createFileAtPath(path, contents = nsData, attributes = null)
        }
    }
}
