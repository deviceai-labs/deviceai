package dev.deviceai.models

import java.io.File
import java.io.FileOutputStream

actual fun appendToFile(path: String, data: ByteArray, length: Int) {
    FileOutputStream(File(path), true).use { fos ->
        fos.write(data, 0, length)
    }
}
