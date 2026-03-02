package dev.deviceai.models

/** Platform-specific file append used during streaming downloads. */
expect fun appendToFile(path: String, data: ByteArray, length: Int)
