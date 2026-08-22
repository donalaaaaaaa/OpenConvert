package com.openconvert.app.domain.preset

import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import org.json.JSONArray
import org.json.JSONObject

data class PresetPack(
    val version: Int,
    val presets: List<Preset>,
)

/**
 * 自定义预设的 JSON 包。只导入/导出用户项，不覆盖内置预设。
 */
object PresetPackCodec {
    const val FORMAT = "openconvert.presets"
    const val VERSION = 1

    fun encode(presets: List<Preset>): String {
        val array = JSONArray()
        presets.filterNot { it.isBuiltIn }.forEach { preset ->
            array.put(
                JSONObject().apply {
                    put("name", preset.name)
                    put("description", preset.description)
                    put("category", preset.category.name)
                    put("targetFormat", preset.targetFormat.name)
                    put("quality", preset.quality.name)
                    put("resolution", preset.resolution.name)
                    put("stripMetadata", preset.stripMetadata)
                    put("longestEdgePx", preset.longestEdgePx ?: JSONObject.NULL)
                    put("fixedWidthPx", preset.fixedWidthPx ?: JSONObject.NULL)
                    put("fixedHeightPx", preset.fixedHeightPx ?: JSONObject.NULL)
                    put("cropAspect", preset.cropAspect)
                },
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("presets", array)
            .toString()
    }

    fun decode(raw: String): Result<PresetPack> = runCatching {
        val json = JSONObject(raw)
        val format = json.optString("format")
        require(format == FORMAT) { "不是 OpenConvert 预设文件" }
        val version = json.optInt("version", 1)
        require(version in 1..VERSION) { "不支持的预设文件版本 $version" }
        val array = json.optJSONArray("presets") ?: JSONArray()
        val presets = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                parsePreset(item)?.let(::add)
            }
        }
        PresetPack(version = version, presets = presets)
    }

    private fun parsePreset(json: JSONObject): Preset? {
        val name = json.optString("name").trim()
        if (name.isEmpty()) return null
        val category = runCatching { FileCategory.valueOf(json.optString("category")) }.getOrNull()
            ?: return null
        if (category == FileCategory.UNKNOWN) return null
        val target = runCatching { FileFormat.valueOf(json.optString("targetFormat")) }.getOrNull()
            ?: return null
        if (target == FileFormat.UNKNOWN) return null
        return Preset(
            id = "",
            category = category,
            name = name,
            description = json.optString("description"),
            targetFormat = target,
            quality = runCatching { QualityPreset.valueOf(json.optString("quality")) }
                .getOrDefault(QualityPreset.BALANCED),
            resolution = runCatching { ResolutionPreset.valueOf(json.optString("resolution")) }
                .getOrDefault(ResolutionPreset.ORIGINAL),
            stripMetadata = json.optBoolean("stripMetadata", false),
            longestEdgePx = json.optionalPositiveInt("longestEdgePx"),
            fixedWidthPx = json.optionalPositiveInt("fixedWidthPx"),
            fixedHeightPx = json.optionalPositiveInt("fixedHeightPx"),
            cropAspect = json.optString("cropAspect").ifBlank { "free" },
            isBuiltIn = false,
        )
    }

    private fun JSONObject.optionalPositiveInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        val value = optInt(key, -1)
        return value.takeIf { it > 0 }
    }
}
