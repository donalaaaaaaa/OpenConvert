package com.openconvert.app.domain.model

import org.json.JSONArray
import org.json.JSONObject

object ConversionPayloadCodec {
    fun encode(payload: ConversionPayload): String {
        val json = JSONObject()
        json.put("sourceUris", JSONArray(payload.sourceUris))
        json.put("sourceNames", JSONArray(payload.sourceNames))
        json.put("pageRanges", payload.pageRanges)
        json.put("pages", JSONArray(payload.pages))
        json.put("outputTreeUri", payload.outputTreeUri ?: JSONObject.NULL)
        json.put("outputUris", JSONArray(payload.outputUris))
        json.put("rotateDegrees", payload.rotateDegrees)
        json.put("batchId", payload.batchId ?: JSONObject.NULL)
        json.put("cropAspect", payload.cropAspect)
        json.put("flip", payload.flip)
        json.put("stripMetadata", payload.stripMetadata)
        // 预设尺寸约束（§8.1）必须过 Room：Worker 在另一个进程周期里执行，
        // 丢了这几个字段「最长边 1920」就会静默失效。
        json.put("presetId", payload.presetId ?: JSONObject.NULL)
        json.put("longestEdgePx", payload.longestEdgePx ?: JSONObject.NULL)
        json.put("fixedWidthPx", payload.fixedWidthPx ?: JSONObject.NULL)
        json.put("fixedHeightPx", payload.fixedHeightPx ?: JSONObject.NULL)
        return json.toString()
    }

    fun decode(raw: String?): ConversionPayload {
        if (raw.isNullOrBlank()) return ConversionPayload()
        val json = JSONObject(raw)
        return ConversionPayload(
            sourceUris = json.optJSONArray("sourceUris").toStringList(),
            sourceNames = json.optJSONArray("sourceNames").toStringList(),
            pageRanges = json.optString("pageRanges"),
            pages = json.optJSONArray("pages").toIntList(),
            outputTreeUri = json.optionalString("outputTreeUri"),
            outputUris = json.optJSONArray("outputUris").toStringList(),
            rotateDegrees = json.optInt("rotateDegrees"),
            batchId = json.optionalString("batchId"),
            cropAspect = json.optString("cropAspect", "free"),
            flip = json.optInt("flip"),
            stripMetadata = json.optBoolean("stripMetadata"),
            presetId = json.optionalString("presetId"),
            longestEdgePx = json.optionalInt("longestEdgePx"),
            fixedWidthPx = json.optionalInt("fixedWidthPx"),
            fixedHeightPx = json.optionalInt("fixedHeightPx"),
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }

    private fun JSONArray?.toIntList(): List<Int> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) add(optInt(index))
        }
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optionalInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key).takeIf { it > 0 }
    }
}
