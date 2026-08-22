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
        json.put("password", payload.password)
        json.put("isEncrypt", payload.isEncrypt)
        json.put("compressDpi", payload.compressDpi)
        json.put("compressQuality", payload.compressQuality.toDouble())
        json.put("cropMarginsLeft", payload.cropMarginsLeft.toDouble())
        json.put("cropMarginsTop", payload.cropMarginsTop.toDouble())
        json.put("cropMarginsRight", payload.cropMarginsRight.toDouble())
        json.put("cropMarginsBottom", payload.cropMarginsBottom.toDouble())
        json.put("metadataTitle", payload.metadataTitle)
        json.put("metadataAuthor", payload.metadataAuthor)
        json.put("metadataSubject", payload.metadataSubject)
        json.put("metadataKeywords", payload.metadataKeywords)
        json.put("watermarkText", payload.watermarkText)
        json.put("watermarkOpacity", payload.watermarkOpacity.toDouble())
        json.put("watermarkPosition", payload.watermarkPosition)
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
            password = json.optString("password"),
            isEncrypt = json.optBoolean("isEncrypt", true),
            compressDpi = json.optInt("compressDpi", 200),
            compressQuality = json.optDouble("compressQuality", 0.8).toFloat(),
            cropMarginsLeft = json.optDouble("cropMarginsLeft", 0.0).toFloat(),
            cropMarginsTop = json.optDouble("cropMarginsTop", 0.0).toFloat(),
            cropMarginsRight = json.optDouble("cropMarginsRight", 0.0).toFloat(),
            cropMarginsBottom = json.optDouble("cropMarginsBottom", 0.0).toFloat(),
            metadataTitle = json.optString("metadataTitle"),
            metadataAuthor = json.optString("metadataAuthor"),
            metadataSubject = json.optString("metadataSubject"),
            metadataKeywords = json.optString("metadataKeywords"),
            watermarkText = json.optString("watermarkText"),
            watermarkOpacity = json.optDouble("watermarkOpacity", 0.18).toFloat(),
            watermarkPosition = json.optString("watermarkPosition", "DIAGONAL"),
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
