package com.openconvert.app.domain.work

import java.io.InputStream
import java.io.OutputStream

object BoundedIo {
    const val BUFFER_BYTES = 1024 * 1024

    fun copy(
        input: InputStream,
        output: OutputStream,
        onBytesCopied: (Long) -> Unit = {},
    ): Long {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
            onBytesCopied(total)
        }
        output.flush()
        return total
    }
}
