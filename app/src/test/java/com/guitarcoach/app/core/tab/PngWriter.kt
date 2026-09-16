package com.guitarcoach.app.core.tab

import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * 极简 PNG 编码器（test 源集专用）：unit-test 编译的 bootclasspath 是 android.jar（无 AWT），
 * 用 java.util.zip（android.jar 内含）手写 PNG：签名 + IHDR(RGB 8bit) + IDAT(filter 0 扫描线 deflate) + IEND。
 */
object PngWriter {

    fun write(file: File, width: Int, height: Int, rgb: IntArray) {
        require(rgb.size == width * height) { "像素数不匹配" }
        val raw = ByteArray(height * (1 + width * 3))
        var o = 0
        for (y in 0 until height) {
            raw[o++] = 0 // filter: None
            for (x in 0 until width) {
                val p = rgb[y * width + x]
                raw[o++] = ((p shr 16) and 0xFF).toByte()
                raw[o++] = ((p shr 8) and 0xFF).toByte()
                raw[o++] = (p and 0xFF).toByte()
            }
        }
        val deflater = Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val buf = ByteArray(raw.size + 1024)
        val compressed = buf.copyOf(deflater.deflate(buf))
        deflater.end()

        file.outputStream().use { out ->
            out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            val ihdr = ByteArray(13)
            writeInt(ihdr, 0, width)
            writeInt(ihdr, 4, height)
            ihdr[8] = 8 // bit depth
            ihdr[9] = 2 // color type: truecolor RGB
            out.write(chunk("IHDR", ihdr))
            out.write(chunk("IDAT", compressed))
            out.write(chunk("IEND", ByteArray(0)))
        }
    }

    private fun writeInt(b: ByteArray, off: Int, v: Int) {
        b[off] = ((v ushr 24) and 0xFF).toByte()
        b[off + 1] = ((v ushr 16) and 0xFF).toByte()
        b[off + 2] = ((v ushr 8) and 0xFF).toByte()
        b[off + 3] = (v and 0xFF).toByte()
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArray(12 + data.size)
        writeInt(out, 0, data.size)
        for (i in type.indices) out[4 + i] = type[i].code.toByte()
        data.copyInto(out, 8)
        val crc = CRC32()
        crc.update(out, 4, 4 + data.size)
        writeInt(out, 8 + data.size, crc.value.toInt())
        return out
    }
}
