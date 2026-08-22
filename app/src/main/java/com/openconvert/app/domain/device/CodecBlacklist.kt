package com.openconvert.app.domain.device

/**
 * 记录「声明支持但实际编不了」的 MediaCodec。
 * key = manufacturer|model|codecName，大小写不敏感。
 */
class CodecBlacklist(
    private val storage: MutableSet<String> = linkedSetOf(),
) {
    fun record(manufacturer: String, model: String, codecName: String) {
        storage += key(manufacturer, model, codecName)
    }

    fun shouldSkipHardware(manufacturer: String, model: String, codecName: String = ANY_CODEC): Boolean {
        val devicePrefix = "${manufacturer.trim().lowercase()}|${model.trim().lowercase()}|"
        return storage.any { entry ->
            entry.startsWith(devicePrefix) &&
                (codecName == ANY_CODEC || entry == key(manufacturer, model, codecName))
        }
    }

    fun entries(): Set<String> = storage.toSet()

    companion object {
        const val ANY_CODEC = "*"

        fun key(manufacturer: String, model: String, codecName: String): String =
            listOf(manufacturer, model, codecName).joinToString("|") { it.trim().lowercase() }
    }
}
