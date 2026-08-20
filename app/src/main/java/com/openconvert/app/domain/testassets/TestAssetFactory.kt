package com.openconvert.app.domain.testassets

import java.io.File

/**
 * 稳定性测试文件库生成器（计划书 §4.2）。
 *
 * 纯 JVM 代码，无 Android 依赖，因此单测与 instrumented 测试都能用。
 * 生成的文件全部是确定性字节序列——同一个 seed 每次产出完全一致，
 * 便于失败复现。
 *
 * 每种格式提供计划书 §4.2 要求的变体：
 * - [standard]      合法可解析
 * - [empty]         0 字节
 * - [truncated]     只有文件头，内容被截断
 * - [corrupted]     文件头正确但内容是随机字节
 * - [wrongExtension] 内容与扩展名不一致（考验 magic number 识别）
 */
object TestAssetFactory {

    /** PNG 8 字节签名。 */
    val PNG_MAGIC = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /** JPEG SOI + APP0。 */
    val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

    val PDF_MAGIC = "%PDF-1.7\n".toByteArray()

    val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    val GZIP_MAGIC = byteArrayOf(0x1F, 0x8B.toByte(), 0x08)

    val FLAC_MAGIC = "fLaC".toByteArray()

    val MP3_MAGIC = "ID3".toByteArray()

    /** RIFF....WAVE */
    fun wavMagic(): ByteArray =
        "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WAVE".toByteArray()

    /** RIFF....WEBP */
    fun webpMagic(): ByteArray =
        "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray()

    /** ....ftypisom */
    fun mp4Magic(): ByteArray = byteArrayOf(0, 0, 0, 0x18) + "ftypisom".toByteArray()

    /** Matroska EBML 头。 */
    val MKV_MAGIC = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())

    /**
     * 确定性伪随机字节（线性同余，不依赖 java.util.Random 的实现细节）。
     * 同一 seed + size 永远产出同一序列。
     */
    fun deterministicBytes(size: Int, seed: Long = 42L): ByteArray {
        var state = seed
        return ByteArray(size) {
            state = state * 6364136223846793005L + 1442695040888963407L
            (state ushr 33).toByte()
        }
    }

    /** 空文件（0 字节）。 */
    fun empty(dir: File, name: String): File =
        File(dir, name).apply { parentFile?.mkdirs(); writeBytes(ByteArray(0)) }

    /** 只有魔数、没有实际内容的截断文件。 */
    fun truncated(dir: File, name: String, magic: ByteArray): File =
        File(dir, name).apply { parentFile?.mkdirs(); writeBytes(magic) }

    /** 魔数正确但主体是垃圾字节——解码器一定会失败。 */
    fun corrupted(dir: File, name: String, magic: ByteArray, bodyBytes: Int = 4096): File =
        File(dir, name).apply {
            parentFile?.mkdirs()
            writeBytes(magic + deterministicBytes(bodyBytes, seed = name.hashCode().toLong()))
        }

    /**
     * 内容与扩展名不一致：用 [actualMagic] 的内容，存成 [name] 的扩展名。
     * 用于验证 FileTypeDetector 以 magic number 为准。
     */
    fun wrongExtension(dir: File, name: String, actualMagic: ByteArray): File =
        File(dir, name).apply {
            parentFile?.mkdirs()
            writeBytes(actualMagic + deterministicBytes(512, seed = 7L))
        }

    /** 指定体积的稀疏文件，用于大文件流式测试（不实际占用物理块）。 */
    fun sparse(dir: File, name: String, sizeBytes: Long): File =
        File(dir, name).apply {
            parentFile?.mkdirs()
            java.io.RandomAccessFile(this, "rw").use { it.setLength(sizeBytes) }
        }
}
